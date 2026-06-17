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
import org.apache.unomi.api.services.ConditionValidationService.ValidationError;
import org.apache.unomi.api.services.ConditionValidationService.ValidationErrorType;
import org.apache.unomi.api.services.*;
import org.apache.unomi.api.services.cache.CacheableTypeConfig;
import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.utils.ParserHelper;
import org.apache.unomi.persistence.spi.config.ConfigurationUpdateHelper;
import org.apache.unomi.services.actions.ActionExecutorDispatcher;
import org.apache.unomi.services.common.cache.AbstractMultiTypeCachingService;
import org.apache.unomi.tracing.api.RequestTracer;
import org.apache.unomi.tracing.api.TracerService;
import org.osgi.framework.*;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.apache.unomi.api.tenants.TenantService.SYSTEM_TENANT;

public class RulesServiceImpl extends AbstractMultiTypeCachingService implements RulesService, EventListenerService, ManagedService, EventHandler {

    public static final String TRACKED_PARAMETER = "trackedConditionParameters";
    private static final Logger LOGGER = LoggerFactory.getLogger(RulesServiceImpl.class.getName());

    private DefinitionsService definitionsService;
    private EventService eventService;
    private ActionExecutorDispatcher actionExecutorDispatcher;
    private TracerService tracerService;

    private Integer rulesRefreshInterval = 1000;
    private Integer rulesStatisticsRefreshInterval = 10000;

    private final List<RuleListenerService> ruleListeners = new CopyOnWriteArrayList<>();

    private final Object cacheLock = new Object();
    private final Map<String, Map<String, Set<Rule>>> rulesByEventTypeByTenant = new ConcurrentHashMap<>();
    private final Map<String, Map<String, RuleStatistics>> ruleStatisticsByTenant = new ConcurrentHashMap<>();
    private volatile Boolean optimizedRulesActivated = true;

    private ScheduledTask statisticsRefreshTask;
    private ServiceRegistration<EventHandler> eventHandlerRegistration;

    /**
     * ThreadLocal to track event processing context for loop detection.
     * Note: Depth protection is handled by EventServiceImpl.MAX_RECURSION_DEPTH to avoid duplication.
     */
    private static final ThreadLocal<ProcessingContext> PROCESSING_CONTEXT = ThreadLocal.withInitial(ProcessingContext::new);

    /**
     * Context object that holds event processing state for the current thread.
     */
    private static class ProcessingContext {
        final Set<String> processingEvents = new HashSet<>();
        final Set<String> reportedLoops = new HashSet<>();
    }

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

    public void setTracerService(TracerService tracerService) {
        this.tracerService = tracerService;
    }

    /**
     * Helper method to get TypeResolutionService from DefinitionsService.
     * Returns null if DefinitionsService is not available or doesn't have TypeResolutionService.
     */
    private TypeResolutionService getTypeResolutionService() {
        return definitionsService != null ? definitionsService.getTypeResolutionService() : null;
    }

    @Override
    public void updated(Dictionary<String, ?> properties) {
        Map<String, ConfigurationUpdateHelper.PropertyMapping> propertyMappings = new HashMap<>();

        // Boolean properties
        propertyMappings.put("rules.optimizationActivated", ConfigurationUpdateHelper.booleanProperty(this::setOptimizedRulesActivated));

        // Integer properties
        propertyMappings.put("rules.refresh.interval", ConfigurationUpdateHelper.integerProperty(this::setRulesRefreshInterval));
        propertyMappings.put("rules.statistics.refresh.interval", ConfigurationUpdateHelper.integerProperty(this::setRulesStatisticsRefreshInterval));

        ConfigurationUpdateHelper.processConfigurationUpdates(properties, LOGGER, "Rules service", propertyMappings);
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
                    setRule(rule, true);
                })
                .withPostProcessor(rule -> {
                    // Only ensure rule is resolved (for initial load and updates)
                    // Re-evaluation of invalid rules happens via OSGi Event Admin when types change
                    ensureRuleResolved(rule);

                    // Update rule by event type cache (only indexes valid, enabled rules)
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
        if (eventHandlerRegistration != null) {
            eventHandlerRegistration.unregister();
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
                            // Only ensure rule is resolved (for refresh from persistence)
                            // Re-evaluation of invalid rules happens via OSGi Event Admin when types change
                            ensureRuleResolved(rule);

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

                boolean matchResult = persistenceService.testMatch(eventCondition, event);
                if (!matchResult) {
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
        if (event == null) {
            return EventService.NO_CHANGE;
        }

        ProcessingContext context = PROCESSING_CONTEXT.get();

        // Generate proper event key for loop detection
        String eventKey = generateEventKey(event);

        // Check if this event is already being processed (loop detection)
        // Note: Depth protection is handled by EventServiceImpl.MAX_RECURSION_DEPTH
        if (context.processingEvents.contains(eventKey)) {
            if (context.reportedLoops.contains(eventKey)) {
                String eventId = event.getItemId() != null ? event.getItemId() : "new";
                LOGGER.warn("Loop detected again: event {} (type: {}) is already being processed. Skipping to prevent infinite loop.",
                    eventId, event.getEventType());
                return EventService.NO_CHANGE;
            }
            context.reportedLoops.add(eventKey);
            logLoopDetected(event);
            return EventService.NO_CHANGE;
        }

        // Add event to processing set
        context.processingEvents.add(eventKey);
        LOGGER.debug("Processing event {} (type: {})",
            event.getItemId() != null ? event.getItemId() : "new", event.getEventType());
        try {
            return processEvent(event, context);
        } finally {
            // Always cleanup (even if exception occurs)
            context.processingEvents.remove(eventKey);

            // Clean up ThreadLocal if processing is complete
            if (context.processingEvents.isEmpty()) {
                LOGGER.debug("Event processing complete, cleaning up ThreadLocal context");
                PROCESSING_CONTEXT.remove();
            }
        }
    }

    /**
     * Generates a unique key for an event to track it in the processing chain.
     * Uses event ID if available, otherwise creates a stable identifier.
     */
    private String generateEventKey(Event event) {
        String eventType = event.getEventType();
        if (eventType == null) {
            eventType = "unknown";
        }
        String eventId = event.getItemId();
        if (eventId != null && !eventId.isEmpty()) {
            return eventType + ":" + eventId;
        }
        // Fallback: use event type and identity hash for events without ID
        return eventType + ":hash:" + System.identityHashCode(event);
    }

    /**
     * Logs when a loop is detected with diagnostic information.
     */
    private void logLoopDetected(Event event) {
        String eventId = event.getItemId() != null ? event.getItemId() : "new";
        String eventType = event.getEventType();
        String cause = "ruleFired".equals(eventType)
            ? "Rule(s) matching 'ruleFired' events (likely wildcard '*')"
            : "Rule(s) matching '" + eventType + "' events send the same event type";
        String fix = "ruleFired".equals(eventType)
            ? "Exclude 'ruleFired' from wildcard rules or use specific event types"
            : "Change rule actions to send different event types or make rules more specific";

        LOGGER.error("Loop detected for event {} (type: {}). {}. Fix: {}.",
            eventId, eventType, cause, fix);
    }

    private int processEvent(Event event, ProcessingContext context) {
        Set<Rule> rules = getMatchingRules(event);
        int changes = EventService.NO_CHANGE;

        String eventId = event.getItemId();
        if (eventId == null || eventId.isEmpty()) {
            eventId = "new";
        }

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

    @Override
    public Map<String, RuleStatistics> getAllRuleStatistics() {
        String currentTenant = contextManager.getCurrentContext().getTenantId();

        Map<String, RuleStatistics> result = new ConcurrentHashMap<>(getRuleStatisticsForTenant(currentTenant));

        // If not in system tenant, also get inherited statistics
        if (!SYSTEM_TENANT.equals(currentTenant)) {
            Map<String, RuleStatistics> systemStats = getRuleStatisticsForTenant(SYSTEM_TENANT);
            result.putAll(systemStats);
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
        if (query.getCondition() != null) {
            definitionsService.getConditionValidationService().validate(query.getCondition());
        }
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
        if (query.getCondition() != null) {
            definitionsService.getConditionValidationService().validate(query.getCondition());
        }
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
        setRule(rule, false);
    }

    protected void setRule(Rule rule, boolean allowInvalidRules) {
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

        // Attempt to resolve rule first to update missingPlugins flag
        // This must happen before checking effectiveAllowInvalidRules
        if (rule.getCondition() != null) {
            try {
                ensureRuleResolved(rule);
            } catch (Exception e) {
                // Resolution failure shouldn't prevent rule from being saved
                // The rule will be marked as invalid and excluded from indexing
                LOGGER.debug("Failed to resolve rule {} during setRule, will be marked as invalid: {}",
                    rule.getItemId(), e.getMessage());
            }
        }

        // If missingPlugins is true, treat as if allowInvalidRules is true
        boolean effectiveAllowInvalidRules = allowInvalidRules || (rule.getMetadata() != null && rule.getMetadata().isMissingPlugins());

        Condition condition = rule.getCondition();
        if (condition != null) {
            // Only validate eventCondition for enabled rules (disabled rules don't need to be executable)
            if (rule.getMetadata().isEnabled()) {
                try {
                    // Check rule's condition validity, throws an exception if not set properly.
                    definitionsService.extractConditionBySystemTag(condition, "eventCondition");
                } catch (Exception e) {
                    if (!effectiveAllowInvalidRules) {
                        throw e;
                    } else {
                        LOGGER.warn("Invalid rule condition for rule {} : ", rule, e);
                        TypeResolutionService typeResolutionService = getTypeResolutionService();
                        if (typeResolutionService != null) {
                            typeResolutionService.markInvalid("rules", rule.getItemId(),
                                "Missing eventCondition: " + e.getMessage());
                        }
                    }
                }
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

            // Validate condition (skips parameters with references/scripts)
            // Validation service will auto-resolve types if needed
            List<ValidationError> validationErrors = definitionsService.getConditionValidationService().validate(rule.getCondition());

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
                if (!effectiveAllowInvalidRules) {
                    throw new IllegalArgumentException(errorMessage.toString());
                } else {
                    LOGGER.warn("Invalid rule condition for rule {} : {}", rule, errorMessage.toString());
                    TypeResolutionService typeResolutionService = getTypeResolutionService();
                    if (typeResolutionService != null) {
                        typeResolutionService.markInvalid("rules", rule.getItemId(),
                            "Condition validation errors: " + errorMessage.toString());
                    }
                }
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

    /**
     * Checks if a rule should be excluded from event type indexing.
     * Rules are excluded if they are disabled, have missing plugins, or are marked as invalid.
     *
     * Note: This method assumes ensureRuleResolved() has been called first to ensure
     * the rule's resolution status is up-to-date. The flags checked here are set by
     * resolveRule() which is called by ensureRuleResolved().
     *
     * @param rule the rule to check
     * @return true if the rule should be excluded, false otherwise
     */
    private boolean shouldExcludeRuleFromEventTypeIndex(Rule rule) {
        if (rule == null) {
            return true;
        }

        // Exclude disabled rules
        if (rule.getMetadata() == null || !rule.getMetadata().isEnabled()) {
            return true;
        }

        // Check if rule has missing plugins or is invalid (set by resolveRule)
        boolean hasMissingPlugins = rule.getMetadata().isMissingPlugins();
        TypeResolutionService typeResolutionService = getTypeResolutionService();
        boolean isInvalid = typeResolutionService != null && typeResolutionService.isInvalid("rules", rule.getItemId());

        if (hasMissingPlugins || isInvalid) {
            String ruleName = getRuleName(rule);
            String ruleId = rule.getItemId();
            String reason = hasMissingPlugins ? "missing plugins" : "invalid rule";
            LOGGER.debug("Excluding rule '{}' (id: {}) from event type index due to: {}", ruleName, ruleId, reason);
            return true;
        }

        return false;
    }

    /**
     * Gets a human-readable name for a rule, falling back to "unnamed" if not available.
     *
     * @param rule the rule
     * @return the rule name or "unnamed"
     */
    private String getRuleName(Rule rule) {
        return rule.getMetadata() != null && rule.getMetadata().getName() != null
            ? rule.getMetadata().getName()
            : "unnamed";
    }

    /**
     * Removes a rule from all event type sets in the given map.
     * This is used when a rule should be excluded from indexing.
     * Uses copy of keys to avoid synchronization during iteration.
     *
     * @param rulesByEventType the map of event types to rule sets (ConcurrentHashMap)
     * @param rule the rule to remove
     */
    private void removeRuleFromEventTypeIndex(Map<String, Set<Rule>> rulesByEventType, Rule rule) {
        // Copy keys to avoid concurrent modification during iteration
        // Since rulesByEventType is a ConcurrentHashMap, we can safely iterate over a copy of keys
        Set<String> eventTypeIds = new HashSet<>(rulesByEventType.keySet());
        for (String eventTypeId : eventTypeIds) {
            Set<Rule> rules = rulesByEventType.get(eventTypeId);
            if (rules != null) {
                rules.remove(rule);
            }
        }
    }

    /**
     * Resolves event types from a rule's condition and logs warnings for wildcard usage.
     * Only logs warnings for enabled rules that are actually being indexed.
     *
     * This method relies on ensureRuleResolvedForIndexing() having been called first, which will
     * mark the rule as invalid/missingPlugins if there are unresolved condition types.
     * If eventTypeIds is empty and the rule has unresolved types, this indicates the
     * rule should be excluded rather than defaulting to wildcard.
     *
     * @param rule the rule (should have been resolved via ensureRuleResolvedForIndexing() first)
     * @return the set of event type IDs, which may include "*" for wildcard matching, or empty set if condition has unresolved types
     */
    private Set<String> resolveEventTypesWithWarnings(Rule rule) {
        Set<String> eventTypeIds = ParserHelper.resolveConditionEventTypes(rule.getCondition(), definitionsService);
        boolean hasWildcard = eventTypeIds.contains("*");
        boolean defaultingToWildcard = false;

        // Before defaulting to wildcard when eventTypeIds is empty, check if rule has unresolved types
        // This relies on ensureRuleResolvedForIndexing() having been called, which marks the rule appropriately
        // We check for unresolved types by looking at the rule's resolution status (missingPlugins or invalid)
        // This avoids duplicating the resolution logic - we rely on TypeResolutionService infrastructure
        if (eventTypeIds.isEmpty()) {
            // Check if rule has unresolved types by checking resolution status
            // Note: shouldExcludeRuleFromEventTypeIndex() also checks disabled, so we need to check specifically
            boolean hasMissingPlugins = rule.getMetadata() != null && rule.getMetadata().isMissingPlugins();
            TypeResolutionService typeResolutionService = getTypeResolutionService();
            boolean isInvalid = typeResolutionService != null && typeResolutionService.isInvalid("rules", rule.getItemId());
            boolean hasUnresolvedTypes = hasMissingPlugins || isInvalid;

            if (hasUnresolvedTypes) {
                // Rule has unresolved types - return empty set to exclude rule
                String ruleName = getRuleName(rule);
                String ruleId = rule.getItemId();
                LOGGER.debug("Rule '{}' (id: {}) has unresolved condition types - excluding from event type index instead of defaulting to wildcard",
                    ruleName, ruleId);
                return Collections.emptySet();
            }
            // No unresolved types - safe to default to wildcard
            eventTypeIds = Collections.singleton("*");
            defaultingToWildcard = true;
        }

        // Only log warning for enabled rules that are actually being indexed
        // Disabled rules or invalid rules won't be indexed, so no need to warn
        if ((hasWildcard || defaultingToWildcard) &&
            rule.getMetadata() != null &&
            rule.getMetadata().isEnabled() &&
            !shouldExcludeRuleFromEventTypeIndex(rule)) {
            String ruleName = getRuleName(rule);
            String ruleId = rule.getItemId();
            String reason = defaultingToWildcard
                ? "no eventTypeCondition found in rule condition"
                : "rule condition contains negated eventTypeCondition or wildcard";
            LOGGER.debug("Rule '{}' (id: {}) uses wildcard event type matching (*). This can cause event loops if the rule triggers events that match its own conditions. Reason: {}. Consider using specific event types instead.",
                ruleName, ruleId, reason);
        }

        return eventTypeIds;
    }

    /**
     * Adds a rule to the appropriate event type sets in the index.
     * Uses copy-and-swap pattern to avoid synchronization on the map.
     *
     * @param rulesByEventType the map of event types to rule sets (ConcurrentHashMap)
     * @param rule the rule to add
     * @param eventTypeIds the set of event type IDs to index the rule under
     */
    private void addRuleToEventTypeIndex(Map<String, Set<Rule>> rulesByEventType, Rule rule, Set<String> eventTypeIds) {
        // First remove the rule from all existing event type sets to handle updates
        // Copy keys to avoid concurrent modification during iteration
        Set<String> existingEventTypes = new HashSet<>(rulesByEventType.keySet());
        for (String eventTypeId : existingEventTypes) {
            Set<Rule> rules = rulesByEventType.get(eventTypeId);
            if (rules != null) {
                rules.remove(rule);
            }
        }

        // Then add the rule to the appropriate event type sets
        // Since rulesByEventType is a ConcurrentHashMap, computeIfAbsent is thread-safe
        for (String eventTypeId : eventTypeIds) {
            Set<Rule> rules = rulesByEventType.computeIfAbsent(eventTypeId,
                k -> ConcurrentHashMap.newKeySet());
            rules.add(rule);
        }
    }

    /**
     * Ensures a rule is resolved (conditions and actions). This is idempotent - if the rule
     * is already resolved, it returns immediately. If the rule was previously invalid or
     * had missing plugins, it attempts to resolve it again (useful when new types are deployed).
     *
     * @param rule the rule to ensure is resolved
     * @return true if the rule is now valid, false if it's still invalid
     */
    private boolean ensureRuleResolved(Rule rule) {
        if (rule == null) {
            return false;
        }

        TypeResolutionService typeResolutionService = getTypeResolutionService();
        if (typeResolutionService == null) {
            return false;
        }

        // Check if rule needs resolution (invalid or missing plugins)
        boolean wasInvalid = typeResolutionService.isInvalid("rules", rule.getItemId());
        boolean hadMissingPlugins = rule.getMetadata() != null && rule.getMetadata().isMissingPlugins();

        // If rule is already valid, no need to resolve
        if (!wasInvalid && !hadMissingPlugins) {
            return true;
        }

        // Attempt to resolve the rule (conditions + actions)
        // This will update missingPlugins flag and invalid status if resolution succeeds
        return typeResolutionService.resolveRule("rules", rule);
    }

    /**
     * Ensures a rule is resolved for indexing purposes. This always attempts resolution
     * to detect unresolved types, even if the rule wasn't previously marked as invalid.
     * This is safe for indexing because it doesn't affect validation behavior.
     *
     * @param rule the rule to ensure is resolved
     * @return true if the rule is now valid, false if it's still invalid
     */
    private boolean ensureRuleResolvedForIndexing(Rule rule) {
        if (rule == null) {
            return false;
        }

        TypeResolutionService typeResolutionService = getTypeResolutionService();
        if (typeResolutionService == null) {
            return false;
        }

        // Always attempt to resolve the rule to ensure resolution status is up-to-date
        // This is idempotent - resolveRule() efficiently checks if types are already resolved
        // and only performs actual resolution if needed. This ensures newly loaded rules
        // are properly checked for unresolved types before indexing.
        return typeResolutionService.resolveRule("rules", rule);
    }

    /**
     * Re-evaluates rule resolution and saves the rule if it becomes valid.
     * This is called when rules are refreshed, allowing rules that were marked as invalid
     * to be re-evaluated when new types are deployed.
     *
     * @param rule the rule to re-evaluate
     * @return true if the rule was resolved (or was already valid), false if still invalid
     */
    private boolean reEvaluateRuleResolution(Rule rule) {
        if (rule == null) {
            return false;
        }

        // Check if rule was previously invalid or had missing plugins
        TypeResolutionService typeResolutionService = getTypeResolutionService();
        if (typeResolutionService == null) {
            return false;
        }
        boolean wasInvalid = typeResolutionService.isInvalid("rules", rule.getItemId());
        boolean hadMissingPlugins = rule.getMetadata() != null && rule.getMetadata().isMissingPlugins();

        // Ensure rule is resolved (idempotent - only resolves if needed)
        boolean resolved = ensureRuleResolved(rule);

        // Only log and save if rule transitioned from invalid to valid
        if (resolved && (wasInvalid || hadMissingPlugins)) {
            // Rule is now resolved - save it to update the missingPlugins flag in persistence
            try {
                // Ensure we're in the correct tenant context before saving
                String ruleTenantId = rule.getTenantId();
                String currentTenantId = contextManager.getCurrentContext().getTenantId();

                if (ruleTenantId != null && !ruleTenantId.equals(currentTenantId)) {
                    // Need to switch tenant context
                    contextManager.executeAsTenant(ruleTenantId, () -> {
                        saveItem(rule, Rule::getItemId, Rule.ITEM_TYPE);
                        return null;
                    });
                } else {
                    // Already in correct tenant context (or rule has no tenant)
                    saveItem(rule, Rule::getItemId, Rule.ITEM_TYPE);
                }

                String ruleName = getRuleName(rule);
                String ruleId = rule.getItemId();
                LOGGER.debug("Rule '{}' (id: {}) is now valid - previously missing condition/action types have been deployed",
                    ruleName, ruleId);
            } catch (Exception e) {
                LOGGER.warn("Failed to save rule {} after successful re-resolution", rule.getItemId(), e);
            }
        }

        return resolved;
    }

    private void updateRulesByEventType(Map<String, Set<Rule>> rulesByEventType, Rule rule) {
        // Ensure rule is resolved for indexing purposes (always attempts resolution to detect unresolved types)
        // This is safe for indexing - it doesn't affect validation behavior in setRule()
        ensureRuleResolvedForIndexing(rule);

        // Check if rule should be excluded from event type indexing (disabled, invalid, or missing plugins)
        if (shouldExcludeRuleFromEventTypeIndex(rule)) {
            removeRuleFromEventTypeIndex(rulesByEventType, rule);
            return;
        }

        // Resolve event types and add rule to index
        // Note: resolveEventTypesWithWarnings will check for unresolved types and return empty set
        // if found, which will effectively exclude the rule from indexing
        Set<String> eventTypeIds = resolveEventTypesWithWarnings(rule);

        // If eventTypeIds is empty (due to unresolved types), exclude the rule
        if (eventTypeIds.isEmpty()) {
            removeRuleFromEventTypeIndex(rulesByEventType, rule);
            return;
        }

        addRuleToEventTypeIndex(rulesByEventType, rule, eventTypeIds);
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
                } else {
                    // Resolve condition type if needed before accessing it
                    if (trackedCondition.getConditionType() == null) {
                        TypeResolutionService typeResolutionService = getTypeResolutionService();
                        if (typeResolutionService != null) {
                            typeResolutionService.resolveConditionType(trackedCondition, "tracked conditions");
                        }
                    }
                    if (trackedCondition.getConditionType() != null &&
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
        }
        return trackedConditions;
    }

    /**
     * Handles OSGi Event Admin events for condition/action type changes.
     * This method is called when condition types or action types are added, updated, or removed.
     * It triggers re-evaluation of all invalid rules to check if they can now be resolved.
     *
     * @param event the OSGi event containing type change information
     */
    @Override
    public void handleEvent(org.osgi.service.event.Event event) {
        String topic = event.getTopic();
        String typeId = (String) event.getProperty("typeId");
        String tenantId = (String) event.getProperty("tenantId");

        if (typeId == null) {
            LOGGER.warn("Received type change event without typeId: {}", topic);
            return;
        }

        LOGGER.debug("Received type change event: {} for type {} (tenant: {})", topic, typeId, tenantId);

        // Re-evaluate all invalid rules across all tenants
        // This works in cluster environments because events are published when types are saved to persistence
        contextManager.executeAsSystem(() -> {
            try {
                // Get all tenants
                Set<String> tenants = new HashSet<>();
                for (Tenant tenant : tenantService.getAllTenants()) {
                    tenants.add(tenant.getItemId());
                }
                tenants.add(SYSTEM_TENANT);

                for (String tId : tenants) {
                    contextManager.executeAsTenant(tId, () -> {
                        // Get all rules for this tenant
                        List<Rule> rules = persistenceService.query("tenantId", tId, "priority", Rule.class);

                        for (Rule rule : rules) {
                            // Only re-evaluate rules that were previously invalid or had missing plugins
                            TypeResolutionService typeResolutionService = getTypeResolutionService();
                            if (typeResolutionService == null) {
                                continue;
                            }
                            boolean wasInvalid = typeResolutionService.isInvalid("rules", rule.getItemId());
                            boolean hadMissingPlugins = rule.getMetadata() != null && rule.getMetadata().isMissingPlugins();

                            if (wasInvalid || hadMissingPlugins) {
                                // Re-evaluate this rule
                                boolean resolved = reEvaluateRuleResolution(rule);

                                if (resolved) {
                                    // Rule is now resolved - update cache and event type index
                                    cacheService.put(Rule.ITEM_TYPE, rule.getItemId(), tId, rule);
                                    Map<String, Set<Rule>> tenantEventTypeRules = getRulesByEventTypeForTenant(tId);
                                    updateRulesByEventType(tenantEventTypeRules, rule);
                                }
                            }
                        }
                        return null;
                    });
                }

                LOGGER.debug("Re-evaluated rules after type change: {} (type: {})", typeId, topic);
            } catch (Exception e) {
                LOGGER.error("Error re-evaluating rules after type change event: {}", topic, e);
            }
            return null;
        });
    }
}
