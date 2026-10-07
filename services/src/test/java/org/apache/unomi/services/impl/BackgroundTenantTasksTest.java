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
import org.apache.unomi.api.Profile;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * UNOMI-1000: background jobs must visit every tenant (not only {@code system}).
 * <p>
 * These tests fail on the 4.0.0 RC behaviour where purge / segment recalculation /
 * rule-statistics refresh ran solely under {@code executeAsSystem}, so persistence
 * only ever saw {@code tenantId=system}.
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
    public void profilePurgeTaskRunsUnderEveryTenantAndRestoresContext() throws Exception {
        Set<String> visitedTenants = ConcurrentHashMap.newKeySet();
        ExecutionContextManagerImpl spiedContext = spy(executionContextManager);
        trackTenantVisits(spiedContext, visitedTenants);

        AtomicReference<TaskExecutor> purgeExecutor = new AtomicReference<>();
        SchedulerServiceImpl spiedScheduler = spy(schedulerService);
        captureExecutor(spiedScheduler, "profile-purge", purgeExecutor);

        ProfileServiceImpl profileService = new ProfileServiceImpl();
        profileService.setBundleContext(bundleContext);
        profileService.setPersistenceService(persistenceService);
        profileService.setDefinitionsService(definitionsService);
        profileService.setContextManager(spiedContext);
        profileService.setSchedulerService(spiedScheduler);
        profileService.setCacheService(multiTypeCacheService);
        profileService.setTenantService(tenantService);
        profileService.setSegmentService(mock(SegmentService.class));
        profileService.setPurgeProfileExistTime(30);
        profileService.setPurgeProfileInterval(1);
        profileService.postConstruct();

        assertNotNull(purgeExecutor.get(), "profile-purge executor must be registered");

        ExecutionContext before = spiedContext.getCurrentContext();
        String beforeTenant = before != null ? before.getTenantId() : null;

        runExecutor(purgeExecutor.get());

        assertTrue(visitedTenants.contains(TENANT_A), "purge must visit tenant1, visited=" + visitedTenants);
        assertTrue(visitedTenants.contains(TENANT_B), "purge must visit tenant2, visited=" + visitedTenants);
        assertTrue(visitedTenants.contains(TenantService.SYSTEM_TENANT),
                "purge must also visit system, visited=" + visitedTenants);

        ExecutionContext after = spiedContext.getCurrentContext();
        String afterTenant = after != null ? after.getTenantId() : null;
        assertEquals(beforeTenant, afterTenant, "tenant context must be restored after the purge task");
    }

    @Test
    public void segmentDateRecalculationTaskRunsUnderEveryTenant() throws Exception {
        Set<String> visitedTenants = ConcurrentHashMap.newKeySet();
        ExecutionContextManagerImpl spiedContext = spy(executionContextManager);
        trackTenantVisits(spiedContext, visitedTenants);

        AtomicReference<TaskExecutor> segmentExecutor = new AtomicReference<>();
        SchedulerServiceImpl spiedScheduler = spy(schedulerService);
        captureExecutor(spiedScheduler, "segment-date-recalculation", segmentExecutor);

        RulesService rulesService = mock(RulesService.class);
        when(rulesService.getAllRules()).thenReturn(Collections.emptyList());

        SegmentServiceImpl segmentService = new SegmentServiceImpl();
        segmentService.setBundleContext(bundleContext);
        segmentService.setPersistenceService(persistenceService);
        segmentService.setDefinitionsService(definitionsService);
        segmentService.setContextManager(spiedContext);
        segmentService.setSchedulerService(spiedScheduler);
        segmentService.setCacheService(multiTypeCacheService);
        segmentService.setTenantService(tenantService);
        segmentService.setRulesService(rulesService);
        segmentService.setEventService(mock(EventService.class));
        segmentService.setTracerService(TestHelper.createTracerService());
        segmentService.postConstruct();

        assertNotNull(segmentExecutor.get(), "segment-date-recalculation executor must be registered");
        runExecutor(segmentExecutor.get());

        assertTrue(visitedTenants.contains(TENANT_A), "segment recalc must visit tenant1, visited=" + visitedTenants);
        assertTrue(visitedTenants.contains(TENANT_B), "segment recalc must visit tenant2, visited=" + visitedTenants);
    }

    @Test
    public void ruleStatisticsRefreshVisitsEveryTenant() throws Exception {
        Set<String> visitedTenants = ConcurrentHashMap.newKeySet();
        ExecutionContextManagerImpl spiedContext = spy(executionContextManager);
        trackTenantVisits(spiedContext, visitedTenants);

        AtomicReference<TaskExecutor> statsExecutor = new AtomicReference<>();
        SchedulerServiceImpl spiedScheduler = spy(schedulerService);
        captureExecutor(spiedScheduler, "rules-statistics-refresh", statsExecutor);

        RulesServiceImpl rulesService = new RulesServiceImpl();
        rulesService.setBundleContext(bundleContext);
        rulesService.setPersistenceService(persistenceService);
        rulesService.setDefinitionsService(definitionsService);
        rulesService.setContextManager(spiedContext);
        rulesService.setSchedulerService(spiedScheduler);
        rulesService.setCacheService(multiTypeCacheService);
        rulesService.setTenantService(tenantService);
        rulesService.setEventService(mock(EventService.class));
        rulesService.setActionExecutorDispatcher(mock(org.apache.unomi.services.actions.ActionExecutorDispatcher.class));
        rulesService.setTracerService(TestHelper.createTracerService());
        rulesService.setRulesRefreshInterval(60000);
        rulesService.setRulesStatisticsRefreshInterval(60000);
        rulesService.postConstruct();

        assertNotNull(statsExecutor.get(), "rules-statistics-refresh executor must be registered");
        runExecutor(statsExecutor.get());

        assertTrue(visitedTenants.contains(TENANT_A), "stats refresh must visit tenant1, visited=" + visitedTenants);
        assertTrue(visitedTenants.contains(TENANT_B), "stats refresh must visit tenant2, visited=" + visitedTenants);
        assertTrue(visitedTenants.contains(TenantService.SYSTEM_TENANT),
                "stats refresh must visit system, visited=" + visitedTenants);
    }

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

    @Test
    public void purgeForOneTenantDoesNotRemoveAnotherTenantsProfiles() {
        ProfileServiceImpl profileService = new ProfileServiceImpl();
        profileService.setBundleContext(bundleContext);
        profileService.setPersistenceService(persistenceService);
        profileService.setDefinitionsService(definitionsService);
        profileService.setContextManager(executionContextManager);
        profileService.setSchedulerService(schedulerService);
        profileService.setCacheService(multiTypeCacheService);
        profileService.setTenantService(tenantService);
        profileService.setSegmentService(mock(SegmentService.class));
        profileService.postConstruct();

        executionContextManager.executeAsTenant(TENANT_A, () -> {
            Profile old = new Profile("a-old");
            old.setProperty("firstVisit", new java.util.Date(0));
            persistenceService.save(old);
            return null;
        });
        executionContextManager.executeAsTenant(TENANT_B, () -> {
            Profile old = new Profile("b-old");
            old.setProperty("firstVisit", new java.util.Date(0));
            persistenceService.save(old);
            return null;
        });
        persistenceService.refresh();

        executionContextManager.executeAsTenant(TENANT_A, () -> {
            profileService.purgeProfiles(0, 1);
            return null;
        });
        persistenceService.refresh();

        executionContextManager.executeAsTenant(TENANT_A, () ->
                assertNull(persistenceService.load("a-old", Profile.class)));
        executionContextManager.executeAsTenant(TENANT_B, () ->
                assertNotNull(persistenceService.load("b-old", Profile.class),
                        "tenant B profiles must stay when purge runs only for tenant A"));
    }

    @SuppressWarnings("unchecked")
    private static void trackTenantVisits(ExecutionContextManagerImpl spiedContext, Set<String> visitedTenants) {
        doAnswer(invocation -> {
            visitedTenants.add(invocation.getArgument(0));
            return invocation.callRealMethod();
        }).when(spiedContext).executeAsTenant(any(String.class), any(Supplier.class));
    }

    private static void captureExecutor(SchedulerServiceImpl spiedScheduler, String taskType,
                                        AtomicReference<TaskExecutor> target) {
        doAnswer(invocation -> {
            TaskExecutor executor = invocation.getArgument(0);
            if (taskType.equals(executor.getTaskType())) {
                target.set(executor);
            }
            return invocation.callRealMethod();
        }).when(spiedScheduler).registerTaskExecutor(any(TaskExecutor.class));
    }

    private static void runExecutor(TaskExecutor executor) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
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
                done.countDown();
            }

            @Override
            public void fail(String error) {
                failure.set(error);
                done.countDown();
            }
        });
        assertTrue(done.await(30, TimeUnit.SECONDS), "task should finish");
        assertNull(failure.get(), "task should not fail: " + failure.get());
    }
}
