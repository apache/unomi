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
package org.apache.unomi.services.impl;

import org.apache.unomi.api.ExecutionContext;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.rules.RuleStatistics;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.RulesService;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.api.tasks.TaskExecutor;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.conditions.evaluator.ConditionEvaluatorDispatcher;
import org.apache.unomi.services.TestHelper;
import org.apache.unomi.services.common.security.ExecutionContextManagerImpl;
import org.apache.unomi.services.common.security.KarafSecurityService;
import org.apache.unomi.services.impl.cache.MultiTypeCacheServiceImpl;
import org.apache.unomi.services.impl.definitions.DefinitionsServiceImpl;
import org.apache.unomi.services.impl.profiles.ProfileServiceImpl;
import org.apache.unomi.services.impl.rules.RulesServiceImpl;
import org.apache.unomi.services.impl.scheduler.SchedulerServiceImpl;
import org.apache.unomi.services.impl.segments.SegmentServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.osgi.framework.Bundle;

import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * UNOMI-1000: background jobs must visit every tenant (not only {@code system}).
 * <p>
 * These tests fail when purge / segment recalculation / rule-statistics refresh run
 * solely under {@code executeAsSystem}, where persistence only ever sees
 * {@code tenantId=system}. They also cover what running per tenant adds:
 * one tenant's failure is reported without stopping the others, and system-owned
 * statistics stay in the system tenant.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class BackgroundTenantTasksTest {

    private static final String TENANT_A = "tenant1";
    private static final String TENANT_B = "tenant2";

    private TestTenantService tenantService;
    private PersistenceService persistenceService;
    private DefinitionsServiceImpl definitionsService;
    private TestBundleContext bundleContext;
    private ExecutionContextManagerImpl executionContextManager;
    private MultiTypeCacheServiceImpl multiTypeCacheService;
    private KarafSecurityService securityService;
    private SchedulerServiceImpl schedulerService;

    @BeforeEach
    public void setUp() {
        bundleContext = new TestBundleContext();
        Bundle systemBundle = mock(Bundle.class);
        when(systemBundle.getBundleContext()).thenReturn(bundleContext);
        when(systemBundle.getBundleId()).thenReturn(0L);
        when(systemBundle.getSymbolicName()).thenReturn("org.apache.unomi.predefined");
        bundleContext.addBundle(systemBundle);

        tenantService = new TestTenantService();
        TestHelper.setupCommonTestData(tenantService);

        securityService = TestHelper.createSecurityService();
        executionContextManager = TestHelper.createExecutionContextManager(securityService);
        multiTypeCacheService = new MultiTypeCacheServiceImpl();

        ConditionEvaluatorDispatcher dispatcher = TestConditionEvaluators.createDispatcher();
        persistenceService = new InMemoryPersistenceServiceImpl(executionContextManager, dispatcher);
        schedulerService = TestHelper.createSchedulerService(
                "background-tenant-tasks-node", persistenceService, executionContextManager,
                bundleContext, null, -1, true, true);

        definitionsService = TestHelper.createDefinitionService(
                persistenceService, bundleContext, schedulerService, multiTypeCacheService,
                executionContextManager, tenantService);
        TestHelper.injectDefinitionsServiceIntoDispatcher(dispatcher, definitionsService);
        TestConditionEvaluators.getConditionTypes().forEach((k, v) -> definitionsService.setConditionType(v));
    }

    @AfterEach
    public void tearDown() throws Exception {
        TestHelper.tearDown(schedulerService, multiTypeCacheService, persistenceService, tenantService,
                TENANT_A, TENANT_B, TenantService.SYSTEM_TENANT);
        TestHelper.cleanupReferences(tenantService, securityService, executionContextManager,
                persistenceService, definitionsService, bundleContext, schedulerService, multiTypeCacheService);
    }

    @Test
    public void profilePurgeTaskPurgesEveryTenantAndRestoresContext() throws Exception {
        AtomicReference<TaskExecutor> purgeExecutor = new AtomicReference<>();
        ExecutionContextManagerImpl spiedContext = spy(executionContextManager);
        newProfileService(spiedContext, capturingScheduler("profile-purge", purgeExecutor));
        saveOldProfile(TENANT_A, "a-old");
        saveOldProfile(TENANT_B, "b-old");

        ExecutionContext before = spiedContext.getCurrentContext();
        String beforeTenant = before != null ? before.getTenantId() : null;

        assertNull(runExecutor(purgeExecutor.get()), "purge task should complete");
        persistenceService.refresh();

        assertNull(loadProfile(TENANT_A, "a-old"), "purge must remove tenant1's old profile");
        assertNull(loadProfile(TENANT_B, "b-old"), "purge must remove tenant2's old profile");

        ExecutionContext after = spiedContext.getCurrentContext();
        String afterTenant = after != null ? after.getTenantId() : null;
        assertEquals(beforeTenant, afterTenant, "tenant context must be restored after the purge task");
    }

    @Test
    public void profilePurgeTaskPurgesSessionsAndEventsOfEveryTenant() throws Exception {
        AtomicReference<TaskExecutor> purgeExecutor = new AtomicReference<>();
        ProfileServiceImpl profileService = new ProfileServiceImpl();
        profileService.setPurgeSessionExistTime(30);
        profileService.setPurgeEventExistTime(30);
        initProfileService(profileService, executionContextManager, capturingScheduler("profile-purge", purgeExecutor));
        for (String tenantId : new String[]{TENANT_A, TENANT_B}) {
            executionContextManager.executeAsTenant(tenantId, () -> {
                persistenceService.save(session("old-session", new Date(0)));
                persistenceService.save(session("recent-session", new Date()));
                persistenceService.save(event("old-event", new Date(0)));
                persistenceService.save(event("recent-event", new Date()));
            });
        }
        persistenceService.refresh();

        assertNull(runExecutor(purgeExecutor.get()), "purge task should complete");
        persistenceService.refresh();

        for (String tenantId : new String[]{TENANT_A, TENANT_B}) {
            executionContextManager.executeAsTenant(tenantId, () -> {
                assertNull(persistenceService.load("old-session", Session.class), "old session of " + tenantId + " must be purged");
                assertNotNull(persistenceService.load("recent-session", Session.class), "recent session of " + tenantId + " must be kept");
                assertNull(persistenceService.load("old-event", Event.class), "old event of " + tenantId + " must be purged");
                assertNotNull(persistenceService.load("recent-event", Event.class), "recent event of " + tenantId + " must be kept");
            });
        }
    }

    @Test
    public void profilePurgeFailureInOneTenantIsReportedAndDoesNotStopTheOthers() throws Exception {
        AtomicReference<TaskExecutor> purgeExecutor = new AtomicReference<>();
        ExecutionContextManagerImpl spiedContext = spy(executionContextManager);
        newProfileService(spiedContext, capturingScheduler("profile-purge", purgeExecutor));
        failTenant(spiedContext, TENANT_A);
        saveOldProfile(TENANT_A, "a-old");
        saveOldProfile(TENANT_B, "b-old");

        ExecutionContext before = spiedContext.getCurrentContext();
        String beforeTenant = before != null ? before.getTenantId() : null;

        String failure = runExecutor(purgeExecutor.get());
        persistenceService.refresh();

        assertNotNull(failure, "a tenant failure must fail the task instead of being reported as success");
        assertTrue(failure.contains(TENANT_A), "failure must name the tenant: " + failure);
        assertFalse(failure.contains(TENANT_B), "failure must not name a tenant that succeeded: " + failure);
        assertNotNull(loadProfile(TENANT_A, "a-old"), "the failing tenant keeps its data");
        assertNull(loadProfile(TENANT_B, "b-old"), "the other tenant must still be purged");

        ExecutionContext after = spiedContext.getCurrentContext();
        String afterTenant = after != null ? after.getTenantId() : null;
        assertEquals(beforeTenant, afterTenant, "tenant context must be restored after a tenant failure");
    }

    @Test
    public void profilePurgeInterruptedBeforeItsTenantsIsNotReportedAsDone() throws Exception {
        AtomicReference<TaskExecutor> purgeExecutor = new AtomicReference<>();
        newProfileService(executionContextManager, capturingScheduler("profile-purge", purgeExecutor));
        saveOldProfile(TENANT_A, "a-old");

        String failure;
        Thread.currentThread().interrupt();
        try {
            failure = runExecutor(purgeExecutor.get());
        } finally {
            Thread.interrupted();
        }

        assertNotNull(failure, "tenants skipped by an interruption must not be reported as purged");
        for (String tenantId : new String[]{TENANT_A, TENANT_B, TenantService.SYSTEM_TENANT}) {
            assertTrue(failure.contains(tenantId), "every skipped tenant must be named: " + failure);
        }
        assertNotNull(loadProfile(TENANT_A, "a-old"), "an interrupted purge must not have run");
    }

    @Test
    public void segmentDateRecalculationTaskRunsUnderEveryTenant() throws Exception {
        Set<String> visitedTenants = ConcurrentHashMap.newKeySet();
        ExecutionContextManagerImpl spiedContext = spy(executionContextManager);
        trackTenantVisits(spiedContext, visitedTenants);
        AtomicReference<TaskExecutor> segmentExecutor = new AtomicReference<>();
        newSegmentService(spiedContext, capturingScheduler("segment-date-recalculation", segmentExecutor));
        // starting the service already visits every tenant to load its cache
        visitedTenants.clear();

        assertNull(runExecutor(segmentExecutor.get()), "segment recalculation should complete");

        assertEquals(Set.of(TENANT_A, TENANT_B, TenantService.SYSTEM_TENANT), visitedTenants);
    }

    @Test
    public void segmentDateRecalculationTaskUpdatesProfilesOfEveryTenant() throws Exception {
        AtomicReference<TaskExecutor> segmentExecutor = new AtomicReference<>();
        SegmentServiceImpl segmentService = newSegmentService(executionContextManager,
                capturingScheduler("segment-date-recalculation", segmentExecutor));
        saveInactiveProfilesAndTheirSegments(segmentService);
        assertFalse(loadProfile(TENANT_A, "inactive-profile").getSegments().contains("inactive-" + TENANT_A),
                "the profile must not be in the segment before the task runs");

        assertNull(runExecutor(segmentExecutor.get()), "segment recalculation should complete");
        persistenceService.refresh();

        assertEquals(Set.of("inactive-" + TENANT_A), loadProfile(TENANT_A, "inactive-profile").getSegments(),
                "tenant1's profile must enter tenant1's date-relative segment only");
        assertEquals(Set.of("inactive-" + TENANT_B), loadProfile(TENANT_B, "inactive-profile").getSegments(),
                "tenant2's profile must enter tenant2's date-relative segment only");
    }

    @Test
    public void segmentDateRecalculationTaskAppliesSystemSegmentsToTenantProfiles() throws Exception {
        AtomicReference<TaskExecutor> segmentExecutor = new AtomicReference<>();
        SegmentServiceImpl segmentService = newSegmentService(executionContextManager,
                capturingScheduler("segment-date-recalculation", segmentExecutor));
        // a date-relative segment owned by the system tenant applies to the profiles of every tenant
        executionContextManager.executeAsSystem(() -> segmentService.setSegmentDefinition(inactiveSinceSegment("inactive-everywhere")));
        executionContextManager.executeAsTenant(TENANT_A, () -> {
            Profile inactive = new Profile("inactive-profile");
            inactive.setProperty("lastVisit", new Date(0));
            persistenceService.save(inactive);
            Profile active = new Profile("active-profile");
            active.setProperty("lastVisit", new Date());
            persistenceService.save(active);
        });
        persistenceService.refresh();

        assertNull(runExecutor(segmentExecutor.get()), "segment recalculation should complete");
        persistenceService.refresh();

        assertEquals(Set.of("inactive-everywhere"), loadProfile(TENANT_A, "inactive-profile").getSegments(),
                "a tenant profile must enter the system tenant's date-relative segment");
        assertTrue(loadProfile(TENANT_A, "active-profile").getSegments().isEmpty(),
                "a profile that does not match must stay out of it");
    }

    @Test
    public void segmentDateRecalculationFailureInOneTenantIsReportedAndDoesNotStopTheOthers() throws Exception {
        ExecutionContextManagerImpl spiedContext = spy(executionContextManager);
        AtomicReference<TaskExecutor> segmentExecutor = new AtomicReference<>();
        SegmentServiceImpl segmentService = newSegmentService(spiedContext,
                capturingScheduler("segment-date-recalculation", segmentExecutor));
        saveInactiveProfilesAndTheirSegments(segmentService);
        failTenant(spiedContext, TENANT_B);

        String failure = runExecutor(segmentExecutor.get());
        persistenceService.refresh();

        assertNotNull(failure, "a tenant failure must fail the task instead of being reported as success");
        assertTrue(failure.contains(TENANT_B), "failure must name the tenant: " + failure);
        assertFalse(failure.contains(TENANT_A), "failure must not name a tenant that succeeded: " + failure);
        assertEquals(Set.of("inactive-" + TENANT_A), loadProfile(TENANT_A, "inactive-profile").getSegments(),
                "the other tenant must still be recalculated");
        assertTrue(loadProfile(TENANT_B, "inactive-profile").getSegments().isEmpty(),
                "the failing tenant's profiles are left as they were");
    }

    @Test
    public void ruleStatisticsRefreshPersistsEachTenantsOwnStatistics() throws Exception {
        AtomicReference<TaskExecutor> statsExecutor = new AtomicReference<>();
        RulesServiceImpl rulesService = newRulesService(capturingScheduler("rules-statistics-refresh", statsExecutor));
        saveRuleStatistics(TENANT_A, "a-rule", 10);
        // getRuleStatistics() hands out the live in-memory statistics the rule engine increments
        RuleStatistics liveStats = executionContextManager.executeAsTenant(TENANT_A, () -> rulesService.getRuleStatistics("a-rule"));
        liveStats.setLocalExecutionCount(4);

        assertNull(runExecutor(statsExecutor.get()), "statistics refresh should complete");
        persistenceService.refresh();

        assertEquals(14L, loadRuleStatistics(TENANT_A, "a-rule").getExecutionCount(),
                "tenant1's pending executions must be flushed to tenant1's statistics");
        assertNull(loadRuleStatistics(TENANT_B, "a-rule"), "tenant2 must not receive tenant1's statistics");
    }

    @Test
    public void ruleStatisticsRefreshKeepsSystemStatisticsInTheSystemTenant() throws Exception {
        AtomicReference<TaskExecutor> statsExecutor = new AtomicReference<>();
        RulesServiceImpl rulesService = newRulesService(capturingScheduler("rules-statistics-refresh", statsExecutor));
        saveRuleStatistics(TenantService.SYSTEM_TENANT, "system-rule", 5);
        RuleStatistics liveStats = executionContextManager.executeAsSystem(() -> rulesService.getRuleStatistics("system-rule"));

        // Several ticks: a tenant run must never pick up the system statistics a previous tick exposed to it.
        for (int tick = 1; tick <= 3; tick++) {
            liveStats.setLocalExecutionCount(liveStats.getLocalExecutionCount() + 1);
            assertNull(runExecutor(statsExecutor.get()), "statistics refresh should complete");
            persistenceService.refresh();

            assertEquals(5L + tick, loadRuleStatistics(TenantService.SYSTEM_TENANT, "system-rule").getExecutionCount(),
                    "system rule executions must be flushed to the system statistics on tick " + tick);
            assertNull(loadRuleStatistics(TENANT_A, "system-rule"), "tenant1 must not get a copy of system statistics");
            assertNull(loadRuleStatistics(TENANT_B, "system-rule"), "tenant2 must not get a copy of system statistics");
            assertEquals(TenantService.SYSTEM_TENANT, liveStats.getTenantId(), "system statistics must stay in the system tenant");
        }
    }

    /**
     * The tests above rely on the in-memory persistence to keep tenants apart the way the real one does:
     * the system context sees the system tenant only, which is why the tasks have to switch tenant.
     */
    @Test
    public void systemContextPersistenceStillOnlySeesSystemTenant() {
        executionContextManager.executeAsTenant(TENANT_A, () -> {
            Profile profile = new Profile("tenant-a-only");
            persistenceService.save(profile);
            return null;
        });
        persistenceService.refresh();

        executionContextManager.executeAsSystem(() -> {
            assertNull(persistenceService.load("tenant-a-only", Profile.class),
                    "system context must not load another tenant's profile");
            assertEquals(0L, persistenceService.getAllItemsCount(Profile.ITEM_TYPE),
                    "system context must not count another tenant's profiles");
            return null;
        });

        executionContextManager.executeAsTenant(TENANT_A, () -> {
            assertNotNull(persistenceService.load("tenant-a-only", Profile.class));
            return null;
        });
    }

    private ProfileServiceImpl newProfileService(ExecutionContextManagerImpl contextManager, SchedulerServiceImpl scheduler) {
        ProfileServiceImpl profileService = new ProfileServiceImpl();
        profileService.setPurgeProfileExistTime(30);
        return initProfileService(profileService, contextManager, scheduler);
    }

    private ProfileServiceImpl initProfileService(ProfileServiceImpl profileService, ExecutionContextManagerImpl contextManager,
                                                  SchedulerServiceImpl scheduler) {
        profileService.setBundleContext(bundleContext);
        profileService.setPersistenceService(persistenceService);
        profileService.setDefinitionsService(definitionsService);
        profileService.setContextManager(contextManager);
        profileService.setSchedulerService(scheduler);
        profileService.setCacheService(multiTypeCacheService);
        profileService.setTenantService(tenantService);
        profileService.setSegmentService(mock(SegmentService.class));
        profileService.setPurgeProfileInterval(1);
        profileService.postConstruct();
        return profileService;
    }

    private SegmentServiceImpl newSegmentService(ExecutionContextManagerImpl contextManager, SchedulerServiceImpl scheduler) {
        RulesService rulesService = mock(RulesService.class);
        when(rulesService.getAllRules()).thenReturn(Collections.emptyList());

        SegmentServiceImpl segmentService = new SegmentServiceImpl();
        segmentService.setBundleContext(bundleContext);
        segmentService.setPersistenceService(persistenceService);
        segmentService.setDefinitionsService(definitionsService);
        segmentService.setContextManager(contextManager);
        segmentService.setSchedulerService(scheduler);
        segmentService.setCacheService(multiTypeCacheService);
        segmentService.setTenantService(tenantService);
        segmentService.setRulesService(rulesService);
        segmentService.setEventService(mock(EventService.class));
        segmentService.setTracerService(TestHelper.createTracerService());
        segmentService.postConstruct();
        return segmentService;
    }

    private RulesServiceImpl newRulesService(SchedulerServiceImpl scheduler) {
        RulesServiceImpl rulesService = new RulesServiceImpl();
        rulesService.setBundleContext(bundleContext);
        rulesService.setPersistenceService(persistenceService);
        rulesService.setDefinitionsService(definitionsService);
        rulesService.setContextManager(executionContextManager);
        rulesService.setSchedulerService(scheduler);
        rulesService.setCacheService(multiTypeCacheService);
        rulesService.setTenantService(tenantService);
        rulesService.setEventService(mock(EventService.class));
        rulesService.setActionExecutorDispatcher(mock(org.apache.unomi.services.actions.ActionExecutorDispatcher.class));
        rulesService.setTracerService(TestHelper.createTracerService());
        rulesService.setRulesRefreshInterval(60000);
        rulesService.setRulesStatisticsRefreshInterval(60000);
        rulesService.postConstruct();
        return rulesService;
    }

    /**
     * Gives each tenant a date-relative segment, then a profile matching it. The profile is saved after the
     * segment was defined, straight to persistence: only the scheduled recalculation can put it in the
     * segment, as when time makes a profile match.
     */
    private void saveInactiveProfilesAndTheirSegments(SegmentServiceImpl segmentService) {
        for (String tenantId : new String[]{TENANT_A, TENANT_B}) {
            executionContextManager.executeAsTenant(tenantId, () -> {
                segmentService.setSegmentDefinition(inactiveSinceSegment("inactive-" + tenantId));
                Profile inactive = new Profile("inactive-profile");
                inactive.setProperty("lastVisit", new Date(0));
                persistenceService.save(inactive);
            });
        }
        persistenceService.refresh();
    }

    /** A segment of the profiles whose last visit is more than 30 days old: its membership changes as time passes. */
    private Segment inactiveSinceSegment(String segmentId) {
        Metadata metadata = new Metadata();
        metadata.setId(segmentId);
        metadata.setName(segmentId);
        metadata.setScope("systemscope");
        metadata.setEnabled(true);

        Condition condition = new Condition(definitionsService.getConditionType("profilePropertyCondition"));
        condition.setParameter("propertyName", "properties.lastVisit");
        condition.setParameter("comparisonOperator", "lessThanOrEqualTo");
        condition.setParameter("propertyValueDateExpr", "now-30d");

        Segment segment = new Segment();
        segment.setItemId(segmentId);
        segment.setMetadata(metadata);
        segment.setCondition(condition);
        return segment;
    }

    private static Session session(String sessionId, Date timeStamp) {
        Session session = new Session(sessionId, new Profile("profile-of-" + sessionId), timeStamp, "scope");
        // the in-memory persistence purges on the creation date
        session.setCreationDate(timeStamp);
        return session;
    }

    private static Event event(String eventId, Date timeStamp) {
        Event event = new Event();
        event.setItemId(eventId);
        event.setEventType("view");
        event.setProfileId("profile-of-" + eventId);
        event.setTimeStamp(timeStamp);
        event.setCreationDate(timeStamp);
        return event;
    }

    private void saveOldProfile(String tenantId, String profileId) {
        executionContextManager.executeAsTenant(tenantId, () -> {
            Profile old = new Profile(profileId);
            old.setProperty("firstVisit", new Date(0));
            persistenceService.save(old);
        });
        persistenceService.refresh();
    }

    private Profile loadProfile(String tenantId, String profileId) {
        return executionContextManager.executeAsTenant(tenantId, () -> persistenceService.load(profileId, Profile.class));
    }

    private void saveRuleStatistics(String tenantId, String ruleId, long executionCount) {
        executionContextManager.executeAsTenant(tenantId, () -> {
            RuleStatistics statistics = new RuleStatistics(ruleId);
            statistics.setExecutionCount(executionCount);
            persistenceService.save(statistics);
        });
        persistenceService.refresh();
    }

    private RuleStatistics loadRuleStatistics(String tenantId, String ruleId) {
        return executionContextManager.executeAsTenant(tenantId, () -> persistenceService.load(ruleId, RuleStatistics.class));
    }

    @SuppressWarnings("unchecked")
    private static void trackTenantVisits(ExecutionContextManagerImpl spiedContext, Set<String> visitedTenants) {
        doAnswer(invocation -> {
            visitedTenants.add(invocation.getArgument(0));
            return invocation.callRealMethod();
        }).when(spiedContext).executeAsTenant(any(String.class), any(Supplier.class));
    }

    /** Makes every later switch into the given tenant fail, as if that tenant's work had thrown. */
    @SuppressWarnings("unchecked")
    private static void failTenant(ExecutionContextManagerImpl spiedContext, String tenantId) {
        doThrow(new IllegalStateException("simulated failure for " + tenantId))
                .when(spiedContext).executeAsTenant(eq(tenantId), any(Supplier.class));
    }

    /**
     * Returns a scheduler that hands the executor registered for the given task type to the test instead of
     * registering it: only the test runs that task, the scheduler's own threads cannot run it concurrently.
     */
    private SchedulerServiceImpl capturingScheduler(String taskType, AtomicReference<TaskExecutor> target) {
        SchedulerServiceImpl spiedScheduler = spy(schedulerService);
        doAnswer(invocation -> {
            TaskExecutor executor = invocation.getArgument(0);
            if (taskType.equals(executor.getTaskType())) {
                target.set(executor);
                return null;
            }
            return invocation.callRealMethod();
        }).when(spiedScheduler).registerTaskExecutor(any(TaskExecutor.class));
        return spiedScheduler;
    }

    /** Runs the executor to its end and returns the failure it reported, or {@code null} when it completed. */
    private String runExecutor(TaskExecutor executor) throws Exception {
        assertNotNull(executor, "the task executor must be registered");
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        AtomicReference<String> failure = new AtomicReference<>();
        executor.execute(mock(ScheduledTask.class), new TaskExecutor.TaskStatusCallback() {
            @Override
            public void updateStep(String step, Map<String, Object> details) {
            }

            @Override
            public void checkpoint(Map<String, Object> checkpointData) {
            }

            @Override
            public void updateStatusDetails(Map<String, Object> details) {
            }

            @Override
            public void complete() {
                done.set(true);
            }

            @Override
            public void fail(String error) {
                failure.set(error);
                done.set(true);
            }
        });
        assertTrue(done.get(), "task should report its outcome before returning");
        return failure.get();
    }
}
