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
package org.apache.unomi.services.impl.tenants;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Scope;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantEventPurgeResult;
import org.apache.unomi.api.tenants.TenantScopeUsage;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.api.tenants.TenantUsage;
import org.apache.unomi.api.tenants.TenantUsageService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.aggregate.TermsAggregate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Default implementation of {@link TenantUsageService}.
 */
public class TenantUsageServiceImpl implements TenantUsageService {

    private static final Logger logger = LoggerFactory.getLogger(TenantUsageServiceImpl.class);
    private static final Pattern MONTH_PERIOD = Pattern.compile("^\\d{4}-\\d{2}$");

    private PersistenceService persistenceService;
    private DefinitionsService definitionsService;
    private TenantService tenantService;
    private ExecutionContextManager contextManager;

    private final Map<String, UsageSnapshot> usageCache = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> restRequestCounts = new ConcurrentHashMap<>();
    private ScheduledExecutorService executor;
    private volatile boolean shutdownNow = false;

    /**
     * Sets the persistence service via Blueprint dependency injection.
     *
     * @param persistenceService the persistence service
     */
    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    /**
     * Sets the tenant service.
     *
     * @param tenantService the tenant service
     */
    public void setTenantService(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    /**
     * Sets the definitions service.
     *
     * @param definitionsService the definitions service
     */
    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    /**
     * Sets the execution context manager.
     *
     * @param contextManager the execution context manager
     */
    public void setContextManager(ExecutionContextManager contextManager) {
        this.contextManager = contextManager;
    }

    /**
     * Blueprint activate hook; starts usage metrics collection.
     */
    public void activate() {
        shutdownNow = false;
        startMetricsCollection();
    }

    /**
     * Blueprint deactivate hook; stops usage metrics collection.
     */
    public void deactivate() {
        shutdownNow = true;
        stopMetricsCollection();
    }

    @Override
    public TenantUsage getUsage(String tenantId, String period) {
        UsagePeriod usagePeriod = resolvePeriod(period);
        Tenant tenant = tenantService.getTenant(tenantId);
        if (tenant == null) {
            return null;
        }
        String cacheKey = cacheKey(tenantId, usagePeriod.getLabel());
        UsageSnapshot snapshot = usageCache.get(cacheKey);
        if (snapshot == null) {
            refreshTenantUsage(tenantId, usagePeriod);
            snapshot = usageCache.get(cacheKey);
        }
        if (snapshot == null) {
            return null;
        }
        return toDto(tenant, usagePeriod, snapshot);
    }

    @Override
    public void recordRestRequest(String tenantId) {
        if (tenantId != null && !tenantId.isEmpty()) {
            restRequestCounts.computeIfAbsent(tenantId, id -> new AtomicLong()).incrementAndGet();
        }
    }

    @Override
    public TenantEventPurgeResult purgeEventsOlderThan(String tenantId, int retentionDays) {
        if (tenantService.getTenant(tenantId) == null) {
            return null;
        }
        if (retentionDays < MIN_EVENT_RETENTION_DAYS) {
            throw new IllegalArgumentException(
                    "retentionDays must be at least " + MIN_EVENT_RETENTION_DAYS + " (requested " + retentionDays + ")");
        }
        if (contextManager == null || definitionsService == null || persistenceService == null) {
            throw new IllegalStateException("Tenant usage service is not fully initialized");
        }

        return contextManager.executeAsSystem(() -> contextManager.executeAsTenant(tenantId, () -> {
            ConditionType eventPropertyConditionType = definitionsService.getConditionType("eventPropertyCondition");
            if (eventPropertyConditionType == null) {
                throw new IllegalStateException("eventPropertyCondition type is not available");
            }

            Condition ageCondition = newCondition(eventPropertyConditionType);
            ageCondition.setParameter("propertyName", "timeStamp");
            ageCondition.setParameter("comparisonOperator", "lessThanOrEqualTo");
            ageCondition.setParameter("propertyValueDateExpr", "now-" + retentionDays + "d");

            long matched = persistenceService.queryCount(ageCondition, Event.ITEM_TYPE);
            boolean purgeRequested = persistenceService.removeByQuery(ageCondition, Event.class);

            TenantEventPurgeResult result = new TenantEventPurgeResult();
            result.setTenantId(tenantId);
            result.setRetentionDays(retentionDays);
            result.setEventsMatched(matched);
            result.setPurgeRequested(purgeRequested);
            result.setRequestedAt(System.currentTimeMillis());
            return result;
        }));
    }

    private void startMetricsCollection() {
        executor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "Tenant-Usage-Collector");
            t.setDaemon(true);
            return t;
        });

        executor.scheduleAtFixedRate(() -> {
            try {
                if (shutdownNow) {
                    return;
                }

                if (contextManager == null) {
                    logger.warn("Context manager not available, skipping usage collection");
                    return;
                }

                contextManager.executeAsSystem(() -> {
                    try {
                        if (!shutdownNow && tenantService != null && persistenceService != null) {
                            refreshAllTenants();
                        }
                    } catch (Exception e) {
                        logger.error("Error updating tenant usage", e);
                    }
                });
            } catch (Exception e) {
                logger.error("Error executing tenant usage update as system subject", e);
            }
        }, 0, 5, TimeUnit.MINUTES);
    }

    private void refreshAllTenants() {
        if (shutdownNow || definitionsService == null) {
            return;
        }

        ConditionType eventPropertyConditionType = definitionsService.getConditionType("eventPropertyCondition");
        ConditionType booleanConditionType = definitionsService.getConditionType("booleanCondition");

        if (eventPropertyConditionType == null || booleanConditionType == null) {
            logger.debug("Required condition types not available, skipping usage update");
            return;
        }

        UsagePeriod currentMonth = resolvePeriod(DEFAULT_PERIOD);

        List<Tenant> tenants;
        try {
            tenants = tenantService.getAllTenants();
        } catch (Exception e) {
            logger.error("Error listing tenants for usage refresh", e);
            return;
        }
        for (Tenant tenant : tenants) {
            if (shutdownNow) {
                return;
            }
            try {
                refreshTenantUsage(tenant.getItemId(), currentMonth, eventPropertyConditionType, booleanConditionType);
            } catch (Exception e) {
                logger.error("Error refreshing usage for tenant {}", tenant.getItemId(), e);
            }
        }
    }

    private void refreshTenantUsage(String tenantId, UsagePeriod usagePeriod) {
        if (definitionsService == null) {
            logger.warn("Definitions service not available, skipping usage refresh for tenant {}", tenantId);
            return;
        }
        ConditionType eventPropertyConditionType = definitionsService.getConditionType("eventPropertyCondition");
        ConditionType booleanConditionType = definitionsService.getConditionType("booleanCondition");
        if (eventPropertyConditionType == null || booleanConditionType == null) {
            logger.debug("Required condition types not available, skipping usage update for tenant {}", tenantId);
            return;
        }
        refreshTenantUsage(tenantId, usagePeriod, eventPropertyConditionType, booleanConditionType);
    }

    private void refreshTenantUsage(String tenantId, UsagePeriod usagePeriod,
                                    ConditionType eventPropertyConditionType,
                                    ConditionType booleanConditionType) {
        if (shutdownNow || persistenceService == null || contextManager == null) {
            return;
        }

        UsageSnapshot snapshot = new UsageSnapshot();
        snapshot.profileCount = persistenceService.getAllItemsCount(Profile.ITEM_TYPE, tenantId);
        // queryCount() scopes by the calling thread's execution context, not by any tenantId
        // parameter inside the Condition, so the count must run under the target tenant's context
        // (background refreshes run as "system", which would otherwise always match zero events).
        snapshot.eventCount = contextManager.executeAsTenant(tenantId, () ->
                countEventsInPeriod(tenantId, eventPropertyConditionType, booleanConditionType,
                        usagePeriod.getStartMillis(), usagePeriod.getEndMillis()));
        snapshot.scopeCount = countCommercialScopes(tenantId);
        snapshot.segmentCount = persistenceService.getAllItemsCount(Segment.ITEM_TYPE, tenantId);
        snapshot.ruleCount = persistenceService.getAllItemsCount(Rule.ITEM_TYPE, tenantId);
        snapshot.storageDocumentCount = persistenceService.calculateStorageSize(tenantId);
        snapshot.scopeUsages = loadScopeUsages(tenantId);
        snapshot.collectedAt = System.currentTimeMillis();
        usageCache.put(cacheKey(tenantId, usagePeriod.getLabel()), snapshot);
    }

    private List<TenantScopeUsage> loadScopeUsages(String tenantId) {
        if (contextManager == null) {
            return Collections.emptyList();
        }

        Map<String, Long> segmentsByScope = contextManager.executeAsTenant(tenantId, () ->
                persistenceService.aggregateWithOptimizedQuery(null, new TermsAggregate("metadata.scope"),
                        Segment.ITEM_TYPE));
        Map<String, Long> rulesByScope = contextManager.executeAsTenant(tenantId, () ->
                persistenceService.aggregateWithOptimizedQuery(null, new TermsAggregate("metadata.scope"),
                        Rule.ITEM_TYPE));

        Set<String> scopeIds = new TreeSet<>();
        if (segmentsByScope != null) {
            scopeIds.addAll(segmentsByScope.keySet());
        }
        if (rulesByScope != null) {
            scopeIds.addAll(rulesByScope.keySet());
        }
        scopeIds.remove("_filtered");
        scopeIds.remove(Metadata.SYSTEM_SCOPE);
        scopeIds.removeIf(id -> id == null || id.isEmpty());

        List<TenantScopeUsage> scopeUsages = new ArrayList<>();
        for (String scopeId : scopeIds) {
            TenantScopeUsage scopeUsage = new TenantScopeUsage();
            scopeUsage.setScopeId(scopeId);
            scopeUsage.setSegmentCount(segmentsByScope != null ? segmentsByScope.getOrDefault(scopeId, 0L) : 0L);
            scopeUsage.setRuleCount(rulesByScope != null ? rulesByScope.getOrDefault(scopeId, 0L) : 0L);
            scopeUsages.add(scopeUsage);
        }
        return scopeUsages;
    }

    private long countCommercialScopes(String tenantId) {
        long total = persistenceService.getAllItemsCount(Scope.ITEM_TYPE, tenantId);
        if (contextManager == null) {
            return total;
        }
        Scope systemScope = contextManager.executeAsTenant(tenantId,
                () -> persistenceService.load(Metadata.SYSTEM_SCOPE, Scope.class));
        if (systemScope != null) {
            return Math.max(0L, total - 1L);
        }
        return total;
    }

    private long countEventsInPeriod(String tenantId, ConditionType eventPropertyConditionType,
                                     ConditionType booleanConditionType, long periodStartMillis, long periodEndMillis) {
        Condition andCondition = newCondition(booleanConditionType);
        andCondition.setParameter("operator", "and");

        List<Condition> subConditions = new ArrayList<>();
        subConditions.add(tenantEqualsCondition(tenantId, eventPropertyConditionType));

        Condition startCondition = newCondition(eventPropertyConditionType);
        startCondition.setParameter("propertyName", "timeStamp");
        startCondition.setParameter("comparisonOperator", "greaterThanOrEqualTo");
        startCondition.setParameter("propertyValueDate", toIsoDateTime(periodStartMillis));
        subConditions.add(startCondition);

        Condition endCondition = newCondition(eventPropertyConditionType);
        endCondition.setParameter("propertyName", "timeStamp");
        endCondition.setParameter("comparisonOperator", "lessThan");
        endCondition.setParameter("propertyValueDate", toIsoDateTime(periodEndMillis));
        subConditions.add(endCondition);

        andCondition.setParameter("subConditions", subConditions);
        return persistenceService.queryCount(andCondition, Event.ITEM_TYPE);
    }

    private Condition tenantEqualsCondition(String tenantId, ConditionType conditionType) {
        Condition condition = newCondition(conditionType);
        condition.setParameter("propertyName", "tenantId");
        condition.setParameter("comparisonOperator", "equals");
        condition.setParameter("propertyValue", tenantId);
        return condition;
    }

    private Condition newCondition(ConditionType conditionType) {
        Condition condition = new Condition();
        condition.setConditionTypeId(conditionType.getItemId());
        condition.setConditionType(conditionType);
        return condition;
    }

    private long currentRestRequestCount(String tenantId) {
        AtomicLong counter = restRequestCounts.get(tenantId);
        return counter != null ? counter.get() : 0L;
    }

    private TenantUsage toDto(Tenant tenant, UsagePeriod usagePeriod, UsageSnapshot snapshot) {
        TenantUsage usage = new TenantUsage();
        usage.setTenantId(tenant.getItemId());
        usage.setPeriod(usagePeriod.getLabel());
        usage.setPeriodStart(usagePeriod.getStartMillis());
        usage.setPeriodEnd(usagePeriod.getEndMillis());
        usage.setProfileCount(snapshot.profileCount);
        usage.setEventCount(snapshot.eventCount);
        usage.setScopeCount(snapshot.scopeCount);
        usage.setSegmentCount(snapshot.segmentCount);
        usage.setRuleCount(snapshot.ruleCount);
        usage.setStorageDocumentCount(snapshot.storageDocumentCount);
        usage.setActiveApiKeyCount(tenant.getActiveApiKeys().size());
        usage.setScopeUsages(snapshot.scopeUsages);
        usage.setRestRequestCount(currentRestRequestCount(tenant.getItemId()));
        usage.setCollectedAt(snapshot.collectedAt);
        return usage;
    }

    static UsagePeriod resolvePeriod(String period) {
        String effectivePeriod = (period == null || period.trim().isEmpty()) ? DEFAULT_PERIOD : period.trim();
        if (DEFAULT_PERIOD.equals(effectivePeriod) || "24h".equals(effectivePeriod)) {
            return forYearMonth(YearMonth.now(ZoneOffset.UTC));
        }
        if (MONTH_PERIOD.matcher(effectivePeriod).matches()) {
            try {
                return forYearMonth(YearMonth.parse(effectivePeriod));
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Unsupported usage period: " + effectivePeriod, e);
            }
        }
        throw new IllegalArgumentException("Unsupported usage period: " + effectivePeriod);
    }

    private static UsagePeriod forYearMonth(YearMonth yearMonth) {
        Instant start = yearMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = yearMonth.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return new UsagePeriod(yearMonth.toString(), start.toEpochMilli(), end.toEpochMilli());
    }

    private static String toIsoDateTime(long epochMillis) {
        Calendar calendar = GregorianCalendar.from(Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC));
        return DatatypeConverter.printDateTime(calendar);
    }

    private static String cacheKey(String tenantId, String periodLabel) {
        return tenantId + ":" + periodLabel;
    }

    private void stopMetricsCollection() {
        if (executor != null) {
            try {
                executor.shutdownNow();
                if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                    logger.warn("Tenant usage executor did not terminate in time");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while shutting down the tenant usage executor");
            } finally {
                executor = null;
            }
        }
    }

    static final class UsagePeriod {
        private final String label;
        private final long startMillis;
        private final long endMillis;

        UsagePeriod(String label, long startMillis, long endMillis) {
            this.label = label;
            this.startMillis = startMillis;
            this.endMillis = endMillis;
        }

        String getLabel() {
            return label;
        }

        long getStartMillis() {
            return startMillis;
        }

        long getEndMillis() {
            return endMillis;
        }
    }

    private static final class UsageSnapshot {
        private long profileCount;
        private long eventCount;
        private long scopeCount;
        private long segmentCount;
        private long ruleCount;
        private long storageDocumentCount;
        private List<TenantScopeUsage> scopeUsages = Collections.emptyList();
        private long collectedAt;
    }
}
