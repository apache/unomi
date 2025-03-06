/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.unomi.services.impl.rules;

import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.rules.RuleStatistics;
import org.apache.unomi.api.services.*;
import org.apache.unomi.api.services.ConditionValidationService.ValidationError;
import org.apache.unomi.api.services.ConditionValidationService.ValidationErrorType;
import org.apache.unomi.api.services.cache.CacheableTypeConfig;
import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.api.utils.ParserHelper;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.services.actions.ActionExecutorDispatcher;
import org.apache.unomi.services.impl.cache.AbstractMultiTypeCachingService;
import org.apache.unomi.tracing.api.RequestTracer;
import org.apache.unomi.tracing.api.TracerService;
import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.apache.unomi.api.tenants.TenantService.SYSTEM_TENANT;

public class RulesServiceImpl extends AbstractMultiTypeCachingService implements RulesService, EventListenerService {

    public static final String TRACKED_PARAMETER = "trackedConditionParameters";
    private static final Logger LOGGER = LoggerFactory.getLogger(RulesServiceImpl.class.getName());

    private DefinitionsService definitionsService;
    private EventService eventService;
    private ActionExecutorDispatcher actionExecutorDispatcher;
    private ConditionValidationService conditionValidationService;
    private TracerService tracerService;

    private final Set<String> invalidRulesId = Collections.synchronizedSet(new HashSet<>());

    private Integer rulesRefreshInterval = 1000;
    private Integer rulesStatisticsRefreshInterval = 10000;

    private final List<RuleListenerService> ruleListeners = new CopyOnWriteArrayList<>();

    private final Object cacheLock = new Object();
    private final Map<String, Map<String, Set<Rule>>> rulesByEventTypeByTenant = new ConcurrentHashMap<>();
    private final Map<String, Map<String, RuleStatistics>> ruleStatisticsByTenant = new ConcurrentHashMap<>();
    private volatile Boolean optimizedRulesActivated = true;

    private ScheduledTask statisticsRefreshTask;

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    public void setActionExecutorDispatcher(ActionExecutorDispatcher actionExecutorDispatcher) {
        this.actionExecutorDispatcher = actionExecutorDispatcher;
    }

    public void setRulesRefreshInterval(Integer rulesRefreshInterval) {
        this.rulesRefreshInterval = rulesRefreshInterval;
    }

    public void setRulesStatisticsRefreshInterval(Integer rulesStatisticsRefreshInterval) {
        this.rulesStatisticsRefreshInterval = rulesStatisticsRefreshInterval;
    }

    public void setOptimizedRulesActivated(Boolean optimizedRulesActivated) {
        this.optimizedRulesActivated = optimizedRulesActivated;
    }

    public void setConditionValidationService(ConditionValidationService conditionValidationService) {
        this.conditionValidationService = conditionValidationService;
    }

    public void setTracerService(TracerService tracerService) {
        this.tracerService = tracerService;
    }

    /**
     * Creates a base configuration builder with common settings for cacheable types
     *
     * @param <T> the type of the cacheable item
     * @param type the class of the cacheable item
     * @param itemType the item type identifier
     * @param metaInfPath the path for predefined items
     * @return a builder with common settings applied
     */
    private <T extends Serializable> CacheableTypeConfig.Builder<T> createBaseBuilder(
            Class<T> type,
            String itemType,
            String metaInfPath) {
        return CacheableTypeConfig.<T>builder(type, itemType, metaInfPath)
            .withInheritFromSystemTenant(true)
            .withRequiresRefresh(true)
            .withRefreshInterval(rulesRefreshInterval);
    }

    @Override
    protected Set<CacheableTypeConfig<?>> getTypeConfigs() {
        Set<CacheableTypeConfig<?>> configs = new HashSet<>();

        // Configure Rule type
        configs.add(createBaseBuilder(Rule.class, Rule.ITEM_TYPE, "rules")
                .withIdExtractor(r -> r.getItemId())
                .withBundleItemProcessor((bundleContext, rule) -> {
                    // Bundle item processor is called before post processor when loading predefined types
                    setRule(rule);
                })
                .withPostProcessor(rule -> {
                    // post processor is called when loading predefined types or when reloading from persistence
                    boolean isValid = ParserHelper.resolveConditionType(definitionsService, rule.getCondition(), "rule " + rule.getItemId());
                    isValid = isValid && ParserHelper.resolveActionTypes(definitionsService, rule, invalidRulesId.contains(rule.getItemId()));
                    if (!isValid) {
                        invalidRulesId.add(rule.getItemId());
                    } else {
                        invalidRulesId.remove(rule.getItemId());
                    }
                    // Update rule by event type cache
                    String tenantId = rule.getTenantId();
                    Map<String, Set<Rule>> tenantEventTypeRules = getRulesByEventTypeForTenant(tenantId);
                    updateRulesByEventType(tenantEventTypeRules, rule);
                })
                .build());

        return configs;
    }

    @Override
    public void postConstruct() {
        super.postConstruct();

        // Initialize statistics refresh task (separate from rule refresh task)
        statisticsRefreshTask = schedulerService.newTask("rules-statistics-refresh")
            .nonPersistent()
            .withPeriod(rulesStatisticsRefreshInterval, TimeUnit.MILLISECONDS)
            .withFixedDelay()
            .withSimpleExecutor(() -> contextManager.executeAsSystem(() -> syncRuleStatistics()))
            .schedule();

        LOGGER.info("Rule service initialized.");
    }

    @Override
    public void preDestroy() {
        super.preDestroy();
        if (statisticsRefreshTask != null) {
            schedulerService.cancelTask(statisticsRefreshTask.getItemId());
        }
        LOGGER.info("Rule service shutdown.");
    }

    @Override
    public void bundleChanged(BundleEvent event) {
        // Let the parent class handle the basic bundle lifecycle
        super.bundleChanged(event);
    }

    @Override
    protected void processBundleStartup(BundleContext bundleContext) {
        // Additional processing specific to RulesService
        super.processBundleStartup(bundleContext);
    }

    @Override
    protected void processBundleStop(Bundle bundle) {
        // Additional processing specific to RulesService
        super.processBundleStop(bundle);
    }

    public void refreshRules() {
        try {
            // Get all tenants and ensure system tenant is included
            Set<String> tenants = new HashSet<>();
            for (Tenant tenant : tenantService.getAllTenants()) {
                tenants.add(tenant.getItemId());
            }
            tenants.add(SYSTEM_TENANT);

            synchronized (cacheLock) {
                for (String tenantId : tenants) {
                    // Set current tenant for querying
                    contextManager.executeAsTenant(tenantId, () -> {
                        // Query rules for current tenant
                        List<Rule> rules = persistenceService.query("tenantId", tenantId, "priority", Rule.class);

                        // Update tenant event type rules cache
                        Map<String, Set<Rule>> tenantEventTypeRules = getRulesByEventTypeForTenant(tenantId);
                        tenantEventTypeRules.clear();

                        for (Rule rule : rules) {
                            // validate rule
                            boolean isValid = ParserHelper.resolveConditionType(definitionsService, rule.getCondition(), "rule " + rule.getItemId());
                            isValid = isValid && ParserHelper.resolveActionTypes(definitionsService, rule, invalidRulesId.contains(rule.getItemId()));
                            if (!isValid) {
                                invalidRulesId.add(rule.getItemId());
                            } else {
                                invalidRulesId.remove(rule.getItemId());
                            }
                            // Update cache service
                            cacheService.put(Rule.ITEM_TYPE, rule.getItemId(), tenantId, rule);
                            // Update event type index
                            updateRulesByEventType(tenantEventTypeRules, rule);
                        }
                    });
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Error loading rules from persistence back-end", t);
        }
    }

    public Set<Rule> getMatchingRules(Event event) {
        Set<Rule> matchedRules = new LinkedHashSet<>();
        String currentTenant = contextManager.getCurrentContext().getTenantId();

        Boolean hasEventAlreadyBeenRaised = null;
        Boolean hasEventAlreadyBeenRaisedForSession = null;
        Boolean hasEventAlreadyBeenRaisedForProfile = null;

        // Get rules for current tenant and event type
        Set<Rule> eventTypeRules = new HashSet<>();
        Map<String, Set<Rule>> tenantRules = getRulesByEventTypeForTenant(currentTenant);

        if (optimizedRulesActivated) {
            Set<Rule> typeRules = tenantRules.get(event.getEventType());
            if (typeRules != null) {
                eventTypeRules.addAll(typeRules);
            }
            Set<Rule> allEventRules = tenantRules.get("*");
            if (allEventRules != null) {
                eventTypeRules.addAll(allEventRules);
            }

            // If not in system tenant, also get inherited rules
            if (!SYSTEM_TENANT.equals(currentTenant)) {
                Map<String, Set<Rule>> systemRules = getRulesByEventTypeForTenant(SYSTEM_TENANT);
                Set<Rule> systemTypeRules = systemRules.get(event.getEventType());
                if (systemTypeRules != null) {
                    eventTypeRules.addAll(systemTypeRules);
                }
                Set<Rule> systemAllEventRules = systemRules.get("*");
                if (systemAllEventRules != null) {
                    eventTypeRules.addAll(systemAllEventRules);
                }
            }

            if (eventTypeRules.isEmpty()) {
                return matchedRules;
            }
        } else {
            // Get all rules from current tenant and system tenant if needed
            eventTypeRules.addAll(getAllItems(Rule.class, true));
        }

        // Rest of the existing matching logic
        for (Rule rule : eventTypeRules) {
            if (!rule.getMetadata().isEnabled()) {
                continue;
            }
            RuleStatistics ruleStatistics = getLocalRuleStatistics(rule);
            long ruleConditionStartTime = System.currentTimeMillis();
            String scope = rule.getMetadata().getScope();
            if (scope == null) {
                LOGGER.warn("No scope defined for rule " + rule.getItemId());
            } else if (scope.equals(Metadata.SYSTEM_SCOPE) || scope.equals(event.getScope())) {
                Condition eventCondition = definitionsService.extractConditionBySystemTag(rule.getCondition(), "eventCondition");

                if (eventCondition == null) {
                    updateRuleStatistics(ruleStatistics, ruleConditionStartTime);
                    continue;
                }

                fireEvaluate(rule, event);

                if (!persistenceService.testMatch(eventCondition, event)) {
                    updateRuleStatistics(ruleStatistics, ruleConditionStartTime);
                    continue;
                }

                Condition sourceCondition = definitionsService.extractConditionBySystemTag(rule.getCondition(), "sourceEventCondition");
                if (sourceCondition != null && !persistenceService.testMatch(sourceCondition, event.getSource())) {
                    updateRuleStatistics(ruleStatistics, ruleConditionStartTime);
                    continue;
                }
                if (rule.isRaiseEventOnlyOnce()) {
                    hasEventAlreadyBeenRaised = hasEventAlreadyBeenRaised != null ? hasEventAlreadyBeenRaised : eventService.hasEventAlreadyBeenRaised(event);
                    if (hasEventAlreadyBeenRaised) {
                        updateRuleStatistics(ruleStatistics, ruleConditionStartTime);
                        fireAlreadyRaised(RuleListenerService.AlreadyRaisedFor.EVENT, rule, event);
                        continue;
                    }
                } else if (rule.isRaiseEventOnlyOnceForProfile()) {
                    hasEventAlreadyBeenRaisedForProfile = hasEventAlreadyBeenRaisedForProfile != null ? hasEventAlreadyBeenRaisedForProfile : eventService.hasEventAlreadyBeenRaised(event, false);
                    if (hasEventAlreadyBeenRaisedForProfile) {
                        updateRuleStatistics(ruleStatistics, ruleConditionStartTime);
                        fireAlreadyRaised(RuleListenerService.AlreadyRaisedFor.PROFILE, rule, event);
                        continue;
                    }
                } else if (rule.isRaiseEventOnlyOnceForSession()) {
                    hasEventAlreadyBeenRaisedForSession = hasEventAlreadyBeenRaisedForSession != null ? hasEventAlreadyBeenRaisedForSession : eventService.hasEventAlreadyBeenRaised(event, true);
                    if (hasEventAlreadyBeenRaisedForSession) {
                        updateRuleStatistics(ruleStatistics, ruleConditionStartTime);
                        fireAlreadyRaised(RuleListenerService.AlreadyRaisedFor.SESSION, rule, event);
                        continue;
                    }
                }

                Condition profileCondition = definitionsService.extractConditionBySystemTag(rule.getCondition(), "profileCondition");
                if (profileCondition != null && !persistenceService.testMatch(profileCondition, event.getProfile())) {
                    updateRuleStatistics(ruleStatistics, ruleConditionStartTime);
                    continue;
                }
                Condition sessionCondition = definitionsService.extractConditionBySystemTag(rule.getCondition(), "sessionCondition");
                if (sessionCondition != null && !persistenceService.testMatch(sessionCondition, event.getSession())) {
                    updateRuleStatistics(ruleStatistics, ruleConditionStartTime);
                    continue;
                }
                matchedRules.add(rule);
            }
        }

        return matchedRules;
    }

    private RuleStatistics getLocalRuleStatistics(Rule rule) {
        String tenantId = rule.getTenantId();
        String ruleId = rule.getItemId();
        Map<String, RuleStatistics> tenantStats = getRuleStatisticsForTenant(tenantId);
        RuleStatistics ruleStatistics = tenantStats.get(ruleId);
        if (ruleStatistics == null) {
            ruleStatistics = new RuleStatistics(ruleId);
            ruleStatistics.setTenantId(tenantId);
            tenantStats.put(ruleId, ruleStatistics);
        }
        return ruleStatistics;
    }

    private void updateRuleStatistics(RuleStatistics ruleStatistics, long ruleConditionStartTime) {
        long totalRuleConditionTime = System.currentTimeMillis() - ruleConditionStartTime;
        synchronized (ruleStatistics) {
            ruleStatistics.setLocalConditionsTime(ruleStatistics.getLocalConditionsTime() + totalRuleConditionTime);
            getRuleStatisticsForTenant(ruleStatistics.getTenantId())
                .put(ruleStatistics.getItemId(), ruleStatistics);
        }
    }

    public List<Rule> getAllRules() {
        return new ArrayList<>(getAllItems(Rule.class, true));
    }

    public boolean canHandle(Event event) {
        return true;
    }

    public int onEvent(Event event) {
        Set<Rule> rules = getMatchingRules(event);
        int changes = EventService.NO_CHANGE;

        for (Rule rule : rules) {
            LOGGER.debug("Fired rule {} for {} - {}", rule.getMetadata().getId(), event.getEventType(), event.getItemId());
            fireExecuteActions(rule, event);

            long actionsStartTime = System.currentTimeMillis();
            for (Action action : rule.getActions()) {
                changes |= actionExecutorDispatcher.execute(action, event);
            }
            long totalActionsTime = System.currentTimeMillis() - actionsStartTime;

            Event ruleFired = new Event("ruleFired", event.getSession(), event.getProfile(),
                event.getScope(), event, rule, event.getTimeStamp());
            ruleFired.getAttributes().putAll(event.getAttributes());
            ruleFired.setPersistent(false);
            changes |= eventService.send(ruleFired);

            RuleStatistics ruleStatistics = getLocalRuleStatistics(rule);
            synchronized (ruleStatistics) {
                ruleStatistics.setLocalExecutionCount(ruleStatistics.getLocalExecutionCount() + 1);
                ruleStatistics.setLocalActionsTime(ruleStatistics.getLocalActionsTime() + totalActionsTime);
                getRuleStatisticsForTenant(rule.getTenantId()).put(ruleStatistics.getItemId(), ruleStatistics);
            }
        }
        return changes;
    }

    @Override
    public RuleStatistics getRuleStatistics(String ruleId) {
        String currentTenant = contextManager.getCurrentContext().getTenantId();

        // Check current tenant statistics
        Map<String, RuleStatistics> tenantStats = getRuleStatisticsForTenant(currentTenant);
        RuleStatistics stats = tenantStats.get(ruleId);

        // If not found and not in system tenant, check system tenant statistics
        if (stats == null && !SYSTEM_TENANT.equals(currentTenant)) {
            Map<String, RuleStatistics> systemStats = getRuleStatisticsForTenant(SYSTEM_TENANT);
            stats = systemStats.get(ruleId);
        }

        // If still not found, try loading from persistence
        if (stats == null) {
            stats = loadWithInheritance(ruleId, RuleStatistics.class);
            if (stats != null) {
                getRuleStatisticsForTenant(stats.getTenantId()).put(ruleId, stats);
            }
        }

        return stats;
    }

    public Map<String, RuleStatistics> getAllRuleStatistics() {
        String currentTenant = contextManager.getCurrentContext().getTenantId();

        Map<String, RuleStatistics> result = new ConcurrentHashMap<>(getRuleStatisticsForTenant(currentTenant));

        // If not in system tenant, also get inherited statistics
        if (!SYSTEM_TENANT.equals(currentTenant)) {
            Map<String, RuleStatistics> systemStats = getRuleStatisticsForTenant(SYSTEM_TENANT);
            for (Map.Entry<String, RuleStatistics> entry : systemStats.entrySet()) {
                if (!result.containsKey(entry.getKey())) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        }

        return result;
    }

    @Override
    public void resetAllRuleStatistics() {
        String currentTenant = contextManager.getCurrentContext().getTenantId();

        Condition matchAllCondition = new Condition(definitionsService.getConditionType("matchAllCondition"));

        // Remove from persistence
        persistenceService.removeByQuery(matchAllCondition, RuleStatistics.class);

        // Clear tenant cache
        getRuleStatisticsForTenant(currentTenant).clear();

        // If not in system tenant, also clear system tenant cache
        if (!SYSTEM_TENANT.equals(currentTenant)) {
            getRuleStatisticsForTenant(SYSTEM_TENANT).clear();
        }
    }

    public Set<Metadata> getRuleMetadatas() {
        Collection<Rule> rules = getAllItems(Rule.class, true);
        Set<Metadata> metadatas = new HashSet<>();
        for (Rule rule : rules) {
            metadatas.add(rule.getMetadata());
        }
        return metadatas;
    }

    public PartialList<Metadata> getRuleMetadatas(Query query) {
        if (query.isForceRefresh()) {
            persistenceService.refreshIndex(Rule.class);
        }
        definitionsService.resolveConditionType(query.getCondition());
        List<Metadata> descriptions = new LinkedList<>();
        PartialList<Rule> rules = persistenceService.query(query.getCondition(), query.getSortby(), Rule.class, query.getOffset(), query.getLimit());
        for (Rule definition : rules.getList()) {
            descriptions.add(definition.getMetadata());
        }
        return new PartialList<>(descriptions, rules.getOffset(), rules.getPageSize(), rules.getTotalSize(), rules.getTotalSizeRelation());
    }

    public PartialList<Rule> getRuleDetails(Query query) {
        if (query.isForceRefresh()) {
            persistenceService.refreshIndex(Rule.class);
        }
        definitionsService.resolveConditionType(query.getCondition());
        PartialList<Rule> rules = persistenceService.query(query.getCondition(), query.getSortby(), Rule.class, query.getOffset(), query.getLimit());
        List<Rule> details = new LinkedList<>();
        details.addAll(rules.getList());
        return new PartialList<>(details, rules.getOffset(), rules.getPageSize(), rules.getTotalSize(), rules.getTotalSizeRelation());
    }

    @Override
    public Rule getRule(String ruleId) {
        return getItem(ruleId, Rule.class);
    }

    @Override
    public void setRule(Rule rule) {
        if (rule == null) {
            return;
        }

        String tenantId = contextManager.getCurrentContext().getTenantId();

        if (rule.getMetadata().getScope() == null) {
            rule.getMetadata().setScope("systemscope");
        }

        if (rule.getTenantId() == null) {
            rule.setTenantId(tenantId);
        }

        Condition condition = rule.getCondition();
        if (condition != null) {
            if (rule.getMetadata().isEnabled() && !rule.getMetadata().isMissingPlugins()) {
                ParserHelper.resolveConditionType(definitionsService, condition, "rule " + rule.getItemId());
                ParserHelper.resolveActionTypes(definitionsService, rule, invalidRulesId.contains(rule.getItemId()));
                // Check rule's condition validity, throws an exception if not set properly.
                definitionsService.extractConditionBySystemTag(condition, "eventCondition");
            }
        }

        if (rule.getCondition() != null) {
            // Start validation operation in tracer
            if (tracerService != null) {
                RequestTracer tracer = tracerService.getCurrentTracer();
                if (tracer != null && tracer.isEnabled()) {
                    tracer.startOperation("rule-condition-validation", "Validating rule condition: " + rule.getItemId(), rule.getCondition());
                }
            }

            List<ValidationError> validationErrors = conditionValidationService.validate(rule.getCondition());

            // Add validation info to tracer
            if (tracerService != null) {
                RequestTracer tracer = tracerService.getCurrentTracer();
                if (tracer != null && tracer.isEnabled()) {
                    tracer.addValidationInfo(validationErrors, "rule-condition-validation");
                    tracer.endOperation(!validationErrors.isEmpty(), String.format("Rule validation completed with %d errors", validationErrors.size()));
                }
            }

            // Separate errors and warnings
            List<ValidationError> errors = validationErrors.stream()
                .filter(error -> error.getType() != ValidationErrorType.MISSING_RECOMMENDED_PARAMETER)
                .collect(Collectors.toList());

            List<ValidationError> warnings = validationErrors.stream()
                .filter(error -> error.getType() == ValidationErrorType.MISSING_RECOMMENDED_PARAMETER)
                .collect(Collectors.toList());

            // Log warnings but don't block the operation
            if (!warnings.isEmpty()) {
                StringBuilder warningMessage = new StringBuilder("Rule condition has warnings:");
                for (ValidationError warning : warnings) {
                    warningMessage.append("\n- ").append(warning.getMessage());
                }
                LOGGER.warn(warningMessage.toString());
            }

            // Only throw exception for actual errors
            if (!errors.isEmpty()) {
                StringBuilder errorMessage = new StringBuilder("Invalid rule condition:");
                for (ValidationError error : errors) {
                    errorMessage.append("\n- ").append(error.getMessage());
                }
                throw new IllegalArgumentException(errorMessage.toString());
            }
        }

        // Save the rule using the parent class method
        saveItem(rule, Rule::getItemId, Rule.ITEM_TYPE);
        Map<String, Set<Rule>> tenantEventTypeRules = getRulesByEventTypeForTenant(tenantId);
        updateRulesByEventType(tenantEventTypeRules, rule);
    }

    public void removeRule(String ruleId) {
        removeItem(ruleId, Rule.class, Rule.ITEM_TYPE);
    }

    private void syncRuleStatistics() {
        List<RuleStatistics> allPersistedRuleStatisticsList = persistenceService.getAllItems(RuleStatistics.class);
        Map<String, RuleStatistics> allPersistedRuleStatistics = new HashMap<>();
        for (RuleStatistics ruleStatistics : allPersistedRuleStatisticsList) {
            allPersistedRuleStatistics.put(ruleStatistics.getItemId(), ruleStatistics);
        }

        String currentTenant = contextManager.getCurrentContext().getTenantId();

        Map<String, RuleStatistics> tenantStats = getRuleStatisticsForTenant(currentTenant);

        // Sync tenant statistics
        for (RuleStatistics ruleStatistics : tenantStats.values()) {
            boolean mustPersist = false;
            if (allPersistedRuleStatistics.containsKey(ruleStatistics.getItemId())) {
                RuleStatistics persistedRuleStatistics = allPersistedRuleStatistics.get(ruleStatistics.getItemId());
                synchronized (ruleStatistics) {
                    ruleStatistics.setExecutionCount(persistedRuleStatistics.getExecutionCount() + ruleStatistics.getLocalExecutionCount());
                    if (ruleStatistics.getLocalExecutionCount() > 0) {
                        ruleStatistics.setLocalExecutionCount(0);
                        mustPersist = true;
                    }
                    ruleStatistics.setConditionsTime(persistedRuleStatistics.getConditionsTime() + ruleStatistics.getLocalConditionsTime());
                    if (ruleStatistics.getLocalConditionsTime() > 0) {
                        ruleStatistics.setLocalConditionsTime(0);
                        mustPersist = true;
                    }
                    ruleStatistics.setActionsTime(persistedRuleStatistics.getActionsTime() + ruleStatistics.getLocalActionsTime());
                    if (ruleStatistics.getLocalActionsTime() > 0) {
                        ruleStatistics.setLocalActionsTime(0);
                        mustPersist = true;
                    }
                    ruleStatistics.setLastSyncDate(new Date());
                }
            } else {
                synchronized (ruleStatistics) {
                    ruleStatistics.setExecutionCount(ruleStatistics.getExecutionCount() + ruleStatistics.getLocalExecutionCount());
                    if (ruleStatistics.getLocalExecutionCount() > 0) {
                        ruleStatistics.setLocalExecutionCount(0);
                        mustPersist = true;
                    }
                    ruleStatistics.setConditionsTime(ruleStatistics.getConditionsTime() + ruleStatistics.getLocalConditionsTime());
                    if (ruleStatistics.getLocalConditionsTime() > 0) {
                        ruleStatistics.setLocalConditionsTime(0);
                        mustPersist = true;
                    }
                    ruleStatistics.setActionsTime(ruleStatistics.getActionsTime() + ruleStatistics.getLocalActionsTime());
                    if (ruleStatistics.getLocalActionsTime() > 0) {
                        ruleStatistics.setLocalActionsTime(0);
                        mustPersist = true;
                    }
                    ruleStatistics.setLastSyncDate(new Date());
                }
            }
            if (mustPersist) {
                persistenceService.save(ruleStatistics, null, true);
            }
        }

        // Also sync system tenant statistics if needed
        if (!SYSTEM_TENANT.equals(currentTenant)) {
            Map<String, RuleStatistics> systemStats = getRuleStatisticsForTenant(SYSTEM_TENANT);
            for (RuleStatistics ruleStatistics : systemStats.values()) {
                if (!tenantStats.containsKey(ruleStatistics.getItemId())) {
                    tenantStats.put(ruleStatistics.getItemId(), ruleStatistics);
                }
            }
        }
    }

    public void bind(ServiceReference<RuleListenerService> serviceReference) {
        RuleListenerService ruleListenerService = bundleContext.getService(serviceReference);
        ruleListeners.add(ruleListenerService);
    }

    public void unbind(ServiceReference<RuleListenerService> serviceReference) {
        if (serviceReference != null) {
            RuleListenerService ruleListenerService = bundleContext.getService(serviceReference);
            ruleListeners.remove(ruleListenerService);
        }
    }

    public void fireEvaluate(Rule rule, Event event) {
        for (RuleListenerService ruleListenerService : ruleListeners) {
            ruleListenerService.onEvaluate(rule, event);
        }
    }

    public void fireAlreadyRaised(RuleListenerService.AlreadyRaisedFor alreadyRaisedFor, Rule rule, Event event) {
        for (RuleListenerService ruleListenerService : ruleListeners) {
            ruleListenerService.onAlreadyRaised(alreadyRaisedFor, rule, event);
        }
    }

    public void fireExecuteActions(Rule rule, Event event) {
        for (RuleListenerService ruleListenerService : ruleListeners) {
            ruleListenerService.onExecuteActions(rule, event);
        }
    }

    private void updateRulesByEventType(Map<String, Set<Rule>> rulesByEventType, Rule rule) {
        Set<String> eventTypeIds = ParserHelper.resolveConditionEventTypes(rule.getCondition());
        if (eventTypeIds.isEmpty()) {
            eventTypeIds = Collections.singleton("*");
        }
        synchronized (rulesByEventType) {
            // First remove the rule from all existing event type sets to handle updates
            for (Set<Rule> rules : rulesByEventType.values()) {
                rules.remove(rule);
            }
            // Then add the rule to the appropriate event type sets
            for (String eventTypeId : eventTypeIds) {
                Set<Rule> rules = rulesByEventType.computeIfAbsent(eventTypeId,
                    k -> Collections.synchronizedSet(new HashSet<>()));
                rules.add(rule);
            }
        }
    }

    private Map<String, Set<Rule>> getRulesByEventTypeForTenant(String tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }
        synchronized (cacheLock) {
            return rulesByEventTypeByTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
        }
    }

    private Map<String, RuleStatistics> getRuleStatisticsForTenant(String tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }
        synchronized (cacheLock) {
            return ruleStatisticsByTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
        }
    }

    public Set<Condition> getTrackedConditions(Item source) {
        Set<Condition> trackedConditions = new HashSet<>();
        Collection<Rule> rules = getAllItems(Rule.class, true);

        for (Rule r : rules) {
            if (!r.getMetadata().isEnabled()) {
                continue;
            }
            Condition ruleCondition = r.getCondition();
            Condition trackedCondition = definitionsService.extractConditionBySystemTag(ruleCondition, "trackedCondition");
            if (trackedCondition != null) {
                Condition evalCondition = definitionsService.extractConditionBySystemTag(ruleCondition, "sourceEventCondition");
                if (evalCondition != null) {
                    if (persistenceService.testMatch(evalCondition, source)) {
                        trackedConditions.add(trackedCondition);
                    }
                } else if (
                        trackedCondition.getConditionType() != null &&
                                trackedCondition.getConditionType().getParameters() != null && !trackedCondition.getConditionType()
                                .getParameters().isEmpty()
                ) {
                    // lookup for track parameters
                    Map<String, Object> trackedParameters = new HashMap<>();
                    trackedCondition.getConditionType().getParameters().forEach(parameter -> {
                        try {
                            if (TRACKED_PARAMETER.equals(parameter.getId())) {
                                Arrays.stream(StringUtils.split(parameter.getDefaultValue().toString(), ",")).forEach(trackedParameter -> {
                                    String[] param = StringUtils.split(StringUtils.trim(trackedParameter), ":");
                                    trackedParameters.put(StringUtils.trim(param[1]), trackedCondition.getParameter(StringUtils.trim(param[0])));
                                });
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Unable to parse tracked parameter from {} for condition type {}", parameter, trackedCondition.getConditionType().getItemId());
                        }
                    });
                    if (!trackedParameters.isEmpty()) {
                        evalCondition = new Condition(definitionsService.getConditionType("booleanCondition"));
                        evalCondition.setParameter("operator", "and");
                        ArrayList<Condition> conditions = new ArrayList<>();
                        trackedParameters.forEach((key, value) -> {
                            Condition propCondition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
                            propCondition.setParameter("comparisonOperator", "equals");
                            propCondition.setParameter("propertyName", key);
                            propCondition.setParameter("propertyValue", value);
                            conditions.add(propCondition);
                        });
                        evalCondition.setParameter("subConditions", conditions);
                        if (persistenceService.testMatch(evalCondition, source)) {
                            trackedConditions.add(trackedCondition);
                        }
                    } else {
                        trackedConditions.add(trackedCondition);
                    }
                }
            }
        }
        return trackedConditions;
    }
}
