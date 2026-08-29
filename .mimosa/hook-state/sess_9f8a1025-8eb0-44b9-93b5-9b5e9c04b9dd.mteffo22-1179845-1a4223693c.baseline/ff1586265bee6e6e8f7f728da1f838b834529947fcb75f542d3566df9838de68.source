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
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.tenants.TenantEventPurgeResult;
import org.apache.unomi.api.tenants.TenantUsage;
import org.apache.unomi.api.tenants.TenantUsageService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.conditions.evaluator.ConditionEvaluatorDispatcher;
import org.apache.unomi.services.TestHelper;
import org.apache.unomi.services.common.security.ExecutionContextManagerImpl;
import org.apache.unomi.services.common.security.KarafSecurityService;
import org.apache.unomi.services.impl.InMemoryPersistenceServiceImpl;
import org.apache.unomi.services.impl.TestConditionEvaluators;
import org.apache.unomi.services.impl.TestTenantService;
import org.apache.unomi.services.impl.cache.MultiTypeCacheServiceImpl;
import org.apache.unomi.services.impl.definitions.DefinitionsServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link TenantUsageServiceImpl} against real {@link InMemoryPersistenceServiceImpl} and
 * {@link ExecutionContextManagerImpl} implementations instead of Mockito mocks.
 *
 * <p>{@link TenantUsageServiceImplTest} mocks {@code ExecutionContextManager.executeAsTenant(...)}
 * to simply invoke the given supplier, which makes it unable to catch a regression where a query
 * runs under the wrong tenant context (the exact bug fixed for UNOMI-958, where profileCount/eventCount
 * were silently scoped to the "system" tenant during the background refresh and always read 0). Using
 * real persistence and a real execution-context manager here means these tests fail the same way
 * production would if that scoping regressed.</p>
 */
class TenantUsageServiceImplTenantIsolationTest {

    private static final String TENANT_A = "usage-isolation-tenant-a";
    private static final String TENANT_B = "usage-isolation-tenant-b";

    private TestTenantService tenantService;
    private PersistenceService persistenceService;
    private DefinitionsServiceImpl definitionsService;
    private ExecutionContextManagerImpl executionContextManager;
    private KarafSecurityService securityService;
    private MultiTypeCacheServiceImpl multiTypeCacheService;
    private BundleContext bundleContext;
    private SchedulerService schedulerService;
    private TenantUsageServiceImpl tenantUsageService;

    @BeforeEach
    void setUp() {
        tenantService = new TestTenantService();
        tenantService.createTenant(TENANT_A, Collections.emptyMap());
        tenantService.createTenant(TENANT_B, Collections.emptyMap());

        ConditionEvaluatorDispatcher conditionEvaluatorDispatcher = TestConditionEvaluators.createDispatcher();
        bundleContext = TestHelper.createMockBundleContext();
        securityService = TestHelper.createSecurityService();
        executionContextManager = TestHelper.createExecutionContextManager(securityService);
        multiTypeCacheService = new MultiTypeCacheServiceImpl();
        persistenceService = new InMemoryPersistenceServiceImpl(executionContextManager, conditionEvaluatorDispatcher);
        schedulerService = TestHelper.createSchedulerService("usage-isolation-scheduler-node", persistenceService,
                executionContextManager, bundleContext, null, -1, true, true);
        definitionsService = TestHelper.createDefinitionService(persistenceService, bundleContext, schedulerService,
                multiTypeCacheService, executionContextManager, tenantService);
        TestHelper.injectDefinitionsServiceIntoDispatcher(conditionEvaluatorDispatcher, definitionsService);
        TestConditionEvaluators.getConditionTypes().forEach((key, value) -> definitionsService.setConditionType(value));

        tenantUsageService = new TenantUsageServiceImpl();
        tenantUsageService.setPersistenceService(persistenceService);
        tenantUsageService.setDefinitionsService(definitionsService);
        tenantUsageService.setTenantService(tenantService);
        tenantUsageService.setContextManager(executionContextManager);
    }

    @AfterEach
    void tearDown() throws Exception {
        TestHelper.tearDown(schedulerService, multiTypeCacheService, persistenceService, tenantService,
                TENANT_A, TENANT_B, "system");
        TestHelper.cleanupReferences(tenantService, securityService, executionContextManager, tenantUsageService,
                persistenceService, definitionsService, schedulerService, multiTypeCacheService, bundleContext);
    }

    @Test
    void profileAndEventCountsStayIsolatedPerTenantEvenWhenRefreshedUnderSystemContext() {
        seedTenant(TENANT_A, 2, 3);
        seedTenant(TENANT_B, 5, 1);
        persistenceService.refresh();

        // The scheduled background collector refreshes usage from inside executeAsSystem(), not
        // from inside the target tenant's own context. That's exactly the call shape that used to
        // leave profileCount/eventCount scoped to "system" and reading 0 for every real tenant.
        TenantUsage usageA = executionContextManager.executeAsSystem(() ->
                tenantUsageService.getUsage(TENANT_A, TenantUsageService.DEFAULT_PERIOD));
        TenantUsage usageB = executionContextManager.executeAsSystem(() ->
                tenantUsageService.getUsage(TENANT_B, TenantUsageService.DEFAULT_PERIOD));

        assertNotNull(usageA);
        assertEquals(2L, usageA.getProfileCount());
        assertEquals(3L, usageA.getEventCount());

        assertNotNull(usageB);
        assertEquals(5L, usageB.getProfileCount());
        assertEquals(1L, usageB.getEventCount());
    }

    @Test
    void purgeEventsOlderThanOnlyDeletesTheTargetTenantsMatchingEvents() {
        Date oldTimestamp = Date.from(Instant.now().minus(100, ChronoUnit.DAYS));
        Date recentTimestamp = new Date();

        executionContextManager.executeAsTenant(TENANT_A, () -> {
            persistenceService.save(eventAt("tenant-a-old-event", oldTimestamp));
            persistenceService.save(eventAt("tenant-a-recent-event", recentTimestamp));
        });
        executionContextManager.executeAsTenant(TENANT_B, () ->
                persistenceService.save(eventAt("tenant-b-old-event", oldTimestamp)));

        TenantEventPurgeResult result = tenantUsageService.purgeEventsOlderThan(TENANT_A, 90);

        assertNotNull(result);
        assertTrue(result.isPurgeRequested());
        executionContextManager.executeAsTenant(TENANT_A, () -> {
            assertNull(persistenceService.load("tenant-a-old-event", Event.class));
            assertNotNull(persistenceService.load("tenant-a-recent-event", Event.class));
        });
        executionContextManager.executeAsTenant(TENANT_B, () ->
                assertNotNull(persistenceService.load("tenant-b-old-event", Event.class)));
    }

    private void seedTenant(String tenantId, int profileCount, int eventCount) {
        executionContextManager.executeAsTenant(tenantId, () -> {
            for (int i = 0; i < profileCount; i++) {
                Profile profile = new Profile();
                profile.setItemId(tenantId + "-profile-" + i);
                persistenceService.save(profile);
            }
            for (int i = 0; i < eventCount; i++) {
                persistenceService.save(eventAt(tenantId + "-event-" + i, new Date()));
            }
        });
    }

    private Event eventAt(String itemId, Date timestamp) {
        Event event = new Event();
        event.setItemId(itemId);
        event.setEventType("pageView");
        event.setTimeStamp(timestamp);
        return event;
    }
}
