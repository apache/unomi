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
package org.apache.unomi.services.impl.segments;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.segments.SegmentsAndScores;
import org.apache.unomi.api.services.ConditionValidationService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.cache.CacheableTypeConfig;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluatorDispatcher;
import org.apache.unomi.services.TestHelper;
import org.apache.unomi.services.impl.*;
import org.apache.unomi.services.impl.cache.MultiTypeCacheServiceImpl;
import org.apache.unomi.services.impl.definitions.DefinitionsServiceImpl;
import org.apache.unomi.services.impl.events.EventServiceImpl;
import org.apache.unomi.services.impl.rules.RulesServiceImpl;
import org.apache.unomi.services.impl.rules.TestActionExecutorDispatcher;
import org.apache.unomi.services.impl.rules.TestEvaluateProfileSegmentsAction;
import org.apache.unomi.tracing.api.RequestTracer;
import org.apache.unomi.tracing.api.TracerService;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.osgi.framework.BundleContext;

import java.io.IOException;
import java.net.URL;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class SegmentServiceImplTest {

    private SegmentServiceImpl segmentService;
    private EventServiceImpl eventService;
    private TestTenantService tenantService;
    private PersistenceService persistenceService;
    private DefinitionsServiceImpl definitionsService;
    private MultiTypeCacheServiceImpl multiTypeCacheService;
    private ExecutionContextManagerImpl executionContextManager;
    private KarafSecurityService securityService;
    private RulesServiceImpl rulesService;
    private TestActionExecutorDispatcher actionExecutorDispatcher;
    private ConditionValidationService conditionValidationService;

    private BundleContext bundleContext;
    private TracerService tracerService;
    private RequestTracer requestTracer;

    private org.apache.unomi.api.services.SchedulerService schedulerService;

    private static final String TENANT_1 = "tenant1";
    private static final String TENANT_2 = "tenant2";
    private static final String SYSTEM_TENANT = "system";

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        tenantService = new TestTenantService();
        securityService = TestHelper.createSecurityService();
        executionContextManager = TestHelper.createExecutionContextManager(securityService);
        conditionValidationService = TestHelper.createConditionValidationService();
        TracerService tracerService = TestHelper.createTracerService();

        // Create tenants using TestHelper
        TestHelper.setupCommonTestData(tenantService);

        ConditionEvaluatorDispatcher conditionEvaluatorDispatcher = TestConditionEvaluators.createDispatcher();

        // Set up bundle context using TestHelper
        bundleContext = TestHelper.createMockBundleContext();

        persistenceService = new InMemoryPersistenceServiceImpl(executionContextManager, conditionEvaluatorDispatcher);

        // Create scheduler service using TestHelper
        schedulerService = TestHelper.createSchedulerService("segment-service-scheduler-node", persistenceService, executionContextManager, bundleContext, null, -1, true, true);

        multiTypeCacheService = new MultiTypeCacheServiceImpl();

        definitionsService = TestHelper.createDefinitionService(persistenceService, bundleContext, schedulerService, multiTypeCacheService, executionContextManager, tenantService, conditionValidationService);

        TestConditionEvaluators.getConditionTypes().forEach((key, value) -> definitionsService.setConditionType(value));

        // Set up event service using TestHelper
        eventService = TestHelper.createEventService(persistenceService, bundleContext, definitionsService, tenantService, tracerService);
        TestConditionEvaluators.setEventService(eventService);

        // Set up action executor dispatcher
        actionExecutorDispatcher = new TestActionExecutorDispatcher(definitionsService, persistenceService);
        actionExecutorDispatcher.setDefaultReturnValue(EventService.PROFILE_UPDATED);
        actionExecutorDispatcher.setTracer(requestTracer);

        // Set up rules service using TestHelper
        rulesService = TestHelper.createRulesService(persistenceService, bundleContext, schedulerService, definitionsService, eventService, executionContextManager, tenantService, conditionValidationService, multiTypeCacheService);
        rulesService.setTracerService(tracerService);

        // Set up segment service
        segmentService = new SegmentServiceImpl();
        segmentService.setBundleContext(bundleContext);
        segmentService.setPersistenceService(persistenceService);
        segmentService.setDefinitionsService(definitionsService);
        segmentService.setRulesService(rulesService);
        segmentService.setEventService(eventService);
        segmentService.setContextManager(executionContextManager);
        segmentService.setSchedulerService(schedulerService);
        segmentService.setCacheService(multiTypeCacheService);
        segmentService.setTenantService(tenantService);
        segmentService.setConditionValidationService(conditionValidationService);
        segmentService.setTracerService(tracerService);

        // Register TestEvaluateProfileSegmentsAction
        actionExecutorDispatcher.addExecutor("evaluateProfileSegments", new TestEvaluateProfileSegmentsAction(segmentService));

        // Set up action types
        TestHelper.setupSegmentActionTypes(definitionsService);

        // Initialize services
        segmentService.postConstruct();

        // Initialize rule caches
        rulesService.postConstruct();

        // Create and deploy the system rule for segment evaluation
        executionContextManager.executeAsSystem(() -> {
            Rule segmentEvaluationRule = new Rule();
            segmentEvaluationRule.setItemId("evaluateProfileSegments");
            segmentEvaluationRule.setTenantId(SYSTEM_TENANT);

            Metadata metadata = new Metadata();
            metadata.setId("evaluateProfileSegments");
            metadata.setName("Evaluate segments");
            metadata.setDescription("Evaluate segments when a profile is modified");
            metadata.setReadOnly(true);
            segmentEvaluationRule.setMetadata(metadata);

            // Create profile updated condition
            Condition condition = new Condition();
            condition.setConditionType(definitionsService.getConditionType("profileUpdatedEventCondition"));
            condition.setConditionTypeId("profileUpdatedEventCondition");
            segmentEvaluationRule.setCondition(condition);

            // Create evaluate segments action
            Action action = new Action();
            action.setActionType(definitionsService.getActionType("evaluateProfileSegmentsAction"));
            action.setActionTypeId("evaluateProfileSegmentsAction");
            segmentEvaluationRule.setActions(Collections.singletonList(action));

            rulesService.setRule(segmentEvaluationRule);
            return null;
        });
    }

    @After
    public void tearDown() throws Exception {
        // Use the common tearDown method from TestHelper
        org.apache.unomi.services.TestHelper.tearDown(
            schedulerService,
            multiTypeCacheService,
            persistenceService,
            tenantService,
            TENANT_1, TENANT_2, SYSTEM_TENANT
        );

        // Clean up references using the helper method
        org.apache.unomi.services.TestHelper.cleanupReferences(
            tenantService, securityService, executionContextManager, segmentService,
            eventService, persistenceService, definitionsService, schedulerService,
            conditionValidationService, multiTypeCacheService, bundleContext,
            rulesService, actionExecutorDispatcher, tracerService, requestTracer
        );
    }

    private Segment createTestSegment(String segmentId, String name) {
        Segment segment = new Segment();
        segment.setItemId(segmentId);
        segment.setTenantId(executionContextManager.getCurrentContext().getTenantId());

        Metadata metadata = new Metadata();
        metadata.setId(segmentId);
        metadata.setName(name);
        metadata.setScope("systemscope");
        metadata.setEnabled(true);
        segment.setMetadata(metadata);

        // Create a simple condition
        Condition condition = new Condition();
        condition.setConditionType(definitionsService.getConditionType("profilePropertyCondition"));
        condition.setConditionTypeId("profilePropertyCondition");
        condition.setParameter("propertyName", "properties.testProperty");
        condition.setParameter("comparisonOperator", "equals");
        condition.setParameter("propertyValue", "testValue");
        segment.setCondition(condition);

        return segment;
    }

    private Segment createPastEventSegment(String segmentId, String name, String eventType, int numberOfDays) {
        Segment segment = new Segment();
        segment.setItemId(segmentId);
        segment.setTenantId(executionContextManager.getCurrentContext().getTenantId());

        Metadata metadata = new Metadata();
        metadata.setId(segmentId);
        metadata.setName(name);
        metadata.setScope("systemscope");
        metadata.setEnabled(true);
        segment.setMetadata(metadata);

        // Create event condition
        Condition eventCondition = new Condition();
        eventCondition.setConditionType(definitionsService.getConditionType("eventTypeCondition"));
        eventCondition.setConditionTypeId("eventTypeCondition");
        eventCondition.setParameter("eventTypeId", eventType);

        // Create past event condition
        Condition pastEventCondition = new Condition();
        pastEventCondition.setConditionType(definitionsService.getConditionType("pastEventCondition"));
        pastEventCondition.setConditionTypeId("pastEventCondition");
        pastEventCondition.setParameter("eventCondition", eventCondition);
        if (numberOfDays > 0) {
            pastEventCondition.setParameter("numberOfDays", numberOfDays);
        }
        pastEventCondition.setParameter("operator", "true");
        segment.setCondition(pastEventCondition);

        return segment;
    }

    private Profile createTestProfile() {
        String currentTenant = executionContextManager.getCurrentContext().getTenantId();
        Profile profile = new Profile(currentTenant);
        profile.setProperty("testProperty", "testValue");
        return profile;
    }

    private Event createTestEvent(Profile profile, String eventType) {
        Event event = new Event();
        event.setEventType(eventType);
        event.setProfile(profile);
        event.setProfileId(profile.getItemId());
        event.setTenantId(profile.getTenantId());
        event.setTimeStamp(new Date());
        event.setActionPostExecutors(new ArrayList<>());
        event.setAttributes(Collections.<String, Object>emptyMap());
        event.setPersistent(true);
        return event;
    }

    private void addEventToProfile(Profile profile, Event event) {
        Map<String, Object> systemProperties = profile.getSystemProperties();
        @SuppressWarnings("unchecked")
        List<Event> pastEvents = (List<Event>) systemProperties.get("pastEvents");
        if (pastEvents == null) {
            pastEvents = new ArrayList<>();
            systemProperties.put("pastEvents", pastEvents);
        }
        pastEvents.add(event);
        persistenceService.save(profile);
    }

    @Test
    public void testSetAndGetSegmentDefinition() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create and set segment
            Segment segment = createTestSegment("test-segment", "Test Segment");
            segmentService.setSegmentDefinition(segment);

            // Get and verify
            Segment retrieved = segmentService.getSegmentDefinition("test-segment");
            assertNotNull("Should retrieve segment", retrieved);
            assertEquals("Should have correct ID", "test-segment", retrieved.getItemId());
            assertEquals("Should have correct name", "Test Segment", retrieved.getMetadata().getName());
            assertEquals("Should have correct tenant", TENANT_1, retrieved.getTenantId());
            return null;
        });
    }

    @Test
    public void testIsProfileInSegment() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create and set segment
            Segment segment = createTestSegment("test-segment", "Test Segment");
            segmentService.setSegmentDefinition(segment);

            // Create matching profile
            Profile profile = createTestProfile();

            // Test matching
            Boolean isInSegment = segmentService.isProfileInSegment(profile, "test-segment");
            assertTrue("Profile should match segment", isInSegment);

            // Test non-matching
            profile.setProperty("testProperty", "differentValue");
            isInSegment = segmentService.isProfileInSegment(profile, "test-segment");
            assertFalse("Profile should not match segment", isInSegment);
            return null;
        });
    }

    @Test
    public void testGetSegmentsAndScoresForProfile() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create and set multiple segments
            Segment segment1 = createTestSegment("segment1", "Segment 1");
            Segment segment2 = createTestSegment("segment2", "Segment 2");
            segmentService.setSegmentDefinition(segment1);
            segmentService.setSegmentDefinition(segment2);

            // Force a reload and an update of the caches
            CacheableTypeConfig<Segment> segmentConfig = CacheableTypeConfig.<Segment>builder(
                Segment.class,
                Segment.ITEM_TYPE,
                "segments")
                .withInheritFromSystemTenant(true)
                .withRequiresRefresh(true)
                .withRefreshInterval(1000L)
                .withIdExtractor(s -> s.getMetadata().getId())
                .build();
            multiTypeCacheService.refreshTypeCache(segmentConfig);

            // Create profile that matches both segments
            Profile profile = createTestProfile();

            // Get segments and verify
            SegmentsAndScores segmentsAndScores = segmentService.getSegmentsAndScoresForProfile(profile);
            assertNotNull("Should return segments and scores", segmentsAndScores);
            assertTrue("Should contain segment1", segmentsAndScores.getSegments().contains("segment1"));
            assertTrue("Should contain segment2", segmentsAndScores.getSegments().contains("segment2"));
            return null;
        });
    }

    @Test
    public void testGetSegmentsAndScoresForProfileTenantInheritance() {
        // Create segments in system tenant
        executionContextManager.executeAsSystem(() -> {
            Segment systemSegment = createTestSegment("system-segment", "System Segment");
            segmentService.setSegmentDefinition(systemSegment);
            return null;
        });

        // Create segments in tenant1
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create tenant segment
            Segment tenantSegment = createTestSegment("tenant-segment", "Tenant Segment");
            segmentService.setSegmentDefinition(tenantSegment);

            // Create profile that matches both segments
            Profile profile = createTestProfile();

            // Get segments and verify
            SegmentsAndScores segmentsAndScores = segmentService.getSegmentsAndScoresForProfile(profile);
            assertNotNull("Should return segments and scores", segmentsAndScores);
            assertTrue("Should contain system segment", segmentsAndScores.getSegments().contains("system-segment"));
            assertTrue("Should contain tenant segment", segmentsAndScores.getSegments().contains("tenant-segment"));

            // Create tenant segment with same ID as system segment to test override
            Segment overrideSegment = createTestSegment("system-segment", "Override Segment");
            // Change condition to match the profile but with a different value
            overrideSegment.getCondition().setParameter("propertyValue", "testValue");
            segmentService.setSegmentDefinition(overrideSegment);

            // Update profile to match only tenant segment condition
            profile.setProperty("testProperty", "testValue");

            // Verify tenant segment overrides system segment
            segmentsAndScores = segmentService.getSegmentsAndScoresForProfile(profile);
            assertTrue("Should contain tenant segment", segmentsAndScores.getSegments().contains("system-segment"));
            assertEquals("Should only contain one instance of the segment", 1,
                segmentsAndScores.getSegments().stream().filter(s -> s.equals("system-segment")).count());

            return null;
        });

        // Verify tenant2 only sees system segment
        executionContextManager.executeAsTenant(TENANT_2, () -> {
            Profile profile = createTestProfile();
            SegmentsAndScores segmentsAndScores = segmentService.getSegmentsAndScoresForProfile(profile);
            assertTrue("Should contain system segment", segmentsAndScores.getSegments().contains("system-segment"));
            assertFalse("Should not contain tenant1 segment", segmentsAndScores.getSegments().contains("tenant-segment"));
            return null;
        });
    }

    @Test
    public void testSegmentInheritanceFromSystemTenant() {
        // Create a segment in system tenant
        executionContextManager.executeAsSystem(() -> {
            Segment systemSegment = createTestSegment("system-segment", "System Segment");
            segmentService.setSegmentDefinition(systemSegment);
            return null;
        });

        // Create a segment in tenant1
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            Segment tenantSegment = createTestSegment("tenant-segment", "Tenant Segment");
            segmentService.setSegmentDefinition(tenantSegment);

            // Test that tenant1 can see both segments
            Profile profile = createTestProfile();
            SegmentsAndScores segmentsAndScores = segmentService.getSegmentsAndScoresForProfile(profile);
            assertTrue("Should contain system segment", segmentsAndScores.getSegments().contains("system-segment"));
            assertTrue("Should contain tenant segment", segmentsAndScores.getSegments().contains("tenant-segment"));
            return null;
        });

        // Test that tenant2 can only see system segment
        executionContextManager.executeAsTenant(TENANT_2, () -> {
            Profile profile = createTestProfile();
            SegmentsAndScores segmentsAndScores = segmentService.getSegmentsAndScoresForProfile(profile);
            assertTrue("Should contain system segment", segmentsAndScores.getSegments().contains("system-segment"));
            assertFalse("Should not contain tenant1 segment", segmentsAndScores.getSegments().contains("tenant-segment"));
            return null;
        });
    }

    @Test
    public void testRemoveSegmentDefinition() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create and set segment
            Segment segment = createTestSegment("test-segment", "Test Segment");
            segmentService.setSegmentDefinition(segment);

            // Remove segment
            segmentService.removeSegmentDefinition("test-segment", true);

            // Verify removal
            Segment removed = segmentService.getSegmentDefinition("test-segment");
            assertNull("Segment should be removed", removed);

            // Verify profile is no longer in segment
            Profile profile = createTestProfile();
            Boolean isInSegment = segmentService.isProfileInSegment(profile, "test-segment");
            assertFalse("Profile should not be in removed segment", isInSegment);
            return null;
        });
    }

    @Test
    public void testPastEventSegment() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create profile
            Profile profile = createTestProfile();
            persistenceService.save(profile);

            // Create past event segment (last 30 days)
            Segment segment = createPastEventSegment("past-event-segment", "Past Event Segment", "test-event", 30);
            segmentService.setSegmentDefinition(segment);

            // Create and save some events
            Event event1 = createTestEvent(profile, "test-event");
            Event event2 = createTestEvent(profile, "test-event");
            eventService.send(event1);
            eventService.send(event2);

            // Force recalculation of past event conditions
            segmentService.recalculatePastEventConditions();

            // Verify profile is in segment
            assertTrue("Profile should be in past event segment", profile.getSegments().contains("past-event-segment"));

            return null;
        });
    }

    @Test
    public void testPastEventSegmentWithDateRange() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create profile
            Profile profile = createTestProfile();
            persistenceService.save(profile);

            // Create past event segment with date range
            Segment segment = createPastEventSegment("date-range-segment", "Date Range Segment", "test-event", 0);
            Condition condition = segment.getCondition();
            condition.setParameter("fromDate", "2024-01-01T00:00:00Z");
            condition.setParameter("toDate", "2024-12-31T23:59:59Z");
            condition.setParameter("operator", "true");
            segmentService.setSegmentDefinition(segment);

            // Create and save event within date range
            Event event = createTestEvent(profile, "test-event");
            Calendar cal = Calendar.getInstance();
            cal.set(2024, Calendar.JUNE, 1);
            event.setTimeStamp(cal.getTime());
            eventService.send(event);

            // Force recalculation of past event conditions
            segmentService.recalculatePastEventConditions();

            assertTrue("Profile should be in date range segment", profile.getSegments().contains("date-range-segment"));

            return null;
        });
    }

    @Test
    public void testPastEventSegmentOutsideDateRange() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create profile
            Profile profile = createTestProfile();
            persistenceService.save(profile);

            // Create past event segment with date range
            Segment segment = createPastEventSegment("date-range-segment", "Date Range Segment", "test-event", 0);
            Condition condition = segment.getCondition();
            condition.setParameter("fromDate", "2024-01-01T00:00:00Z");
            condition.setParameter("toDate", "2024-12-31T23:59:59Z");
            segmentService.setSegmentDefinition(segment);

            // Create and save event outside date range
            Event event = createTestEvent(profile, "test-event");
            Calendar cal = Calendar.getInstance();
            cal.set(2023, Calendar.DECEMBER, 31); // Event before date range
            event.setTimeStamp(cal.getTime());
            eventService.send(event);

            assertFalse("Profile should not be in date range segment", profile.getSegments().contains("date-range-segment"));

            return null;
        });
    }

    @Test
    public void testPastEventSegmentWithDifferentEventType() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create profile
            Profile profile = createTestProfile();
            persistenceService.save(profile);

            // Create past event segment for specific event type
            Segment segment = createPastEventSegment("past-event-segment", "Past Event Segment", "target-event", 30);
            segmentService.setSegmentDefinition(segment);

            // Create and save event with different type
            Event event = createTestEvent(profile, "different-event");
            eventService.send(event);

            // Verify profile is not in segment
            Boolean isInSegment = segmentService.isProfileInSegment(profile, "past-event-segment");
            assertFalse("Profile should not be in segment due to different event type", isInSegment);

            return null;
        });
    }

    @Test
    public void testSystemTenantInheritance() {
        // Create a segment in system tenant
        executionContextManager.executeAsSystem(() -> {
            Segment systemSegment = createTestSegment("system-segment", "System Segment");
            segmentService.setSegmentDefinition(systemSegment);
            return null;
        });

        // Test that tenant1 can see the system segment
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            Segment segment = segmentService.getSegmentDefinition("system-segment");
            assertNotNull("Tenant should see system segment", segment);
            assertEquals("Should have correct ID", "system-segment", segment.getItemId());
            assertEquals("Should have correct name", "System Segment", segment.getMetadata().getName());
            return null;
        });

        // Test that tenant2 can also see the system segment
        executionContextManager.executeAsTenant(TENANT_2, () -> {
            Segment segment = segmentService.getSegmentDefinition("system-segment");
            assertNotNull("Tenant should see system segment", segment);
            assertEquals("Should have correct ID", "system-segment", segment.getItemId());
            assertEquals("Should have correct name", "System Segment", segment.getMetadata().getName());
            return null;
        });
    }

    @Test
    public void testTenantIsolation() {
        // Create a segment in tenant1
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            Segment tenant1Segment = createTestSegment("tenant1-segment", "Tenant 1 Segment");
            segmentService.setSegmentDefinition(tenant1Segment);
            return null;
        });

        // Test that tenant2 cannot see tenant1's segment
        executionContextManager.executeAsTenant(TENANT_2, () -> {
            Segment segment = segmentService.getSegmentDefinition("tenant1-segment");
            assertNull("Tenant2 should not see tenant1's segment", segment);
            return null;
        });
    }

    @Test
    public void testCacheRefresh() {
        // Create a segment in tenant1
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            Segment segment = createTestSegment("test-segment", "Test Segment");
            segmentService.setSegmentDefinition(segment);

            // Verify it's in the cache
            Map<String, Segment> cache = multiTypeCacheService.getTenantCache(TENANT_1, Segment.class);
            assertNotNull("Cache should exist", cache);
            assertNotNull("Segment should be in cache", cache.get("test-segment"));

            // Update segment directly in persistence
            segment.getMetadata().setName("Updated Name");
            persistenceService.save(segment);

            // Force cache refresh
            segmentService.postConstruct();

            // Verify cache was updated
            cache = multiTypeCacheService.getTenantCache(TENANT_1, Segment.class);
            assertEquals("Cache should have updated name", "Updated Name",
                cache.get("test-segment").getMetadata().getName());

            return null;
        });
    }

    @Test
    public void testPredefinedSegmentLoading() {
        // Mock bundle entries
        URL mockUrl = getClass().getResource("/test-segments/test-segment.json");
        when(bundleContext.getBundle().findEntries(eq("META-INF/cxs/segments"), eq("*.json"), eq(true)))
            .thenReturn(Collections.enumeration(Collections.singletonList(mockUrl)));

        // Initialize service
        segmentService.postConstruct();

        // Verify predefined segment was loaded
        executionContextManager.executeAsSystem(() -> {
            Segment segment = segmentService.getSegmentDefinition("test-segment");
            assertNotNull("Predefined segment should be loaded", segment);
            assertEquals("Should have correct name", "Test Segment", segment.getMetadata().getName());
            return null;
        });
    }

    @Test
    public void testGetSegmentMetadatasTenantInheritance() {
        // Create segments in system tenant
        executionContextManager.executeAsSystem(() -> {
            Segment systemSegment1 = createTestSegment("system-segment-1", "System Segment 1");
            Segment systemSegment2 = createTestSegment("system-segment-2", "System Segment 2");
            segmentService.setSegmentDefinition(systemSegment1);
            segmentService.setSegmentDefinition(systemSegment2);
            return null;
        });

        // Create segments in tenant1
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            Segment tenant1Segment = createTestSegment("tenant1-segment", "Tenant 1 Segment");
            segmentService.setSegmentDefinition(tenant1Segment);

            // Verify tenant1 can see both system and tenant1 segments
            PartialList<Metadata> metadatas = segmentService.getSegmentMetadatas(0, 10, null);
            assertEquals("Should see all segments", 3, metadatas.getList().size());
            assertTrue("Should contain system segment 1",
                metadatas.getList().stream().anyMatch(m -> m.getId().equals("system-segment-1")));
            assertTrue("Should contain system segment 2",
                metadatas.getList().stream().anyMatch(m -> m.getId().equals("system-segment-2")));
            assertTrue("Should contain tenant segment",
                metadatas.getList().stream().anyMatch(m -> m.getId().equals("tenant1-segment")));
            return null;
        });

        // Verify tenant2 only sees system segments
        executionContextManager.executeAsTenant(TENANT_2, () -> {
            PartialList<Metadata> metadatas = segmentService.getSegmentMetadatas(0, 10, null);
            assertEquals("Should only see system segments", 2, metadatas.getList().size());
            assertTrue("Should contain system segment 1",
                metadatas.getList().stream().anyMatch(m -> m.getId().equals("system-segment-1")));
            assertTrue("Should contain system segment 2",
                metadatas.getList().stream().anyMatch(m -> m.getId().equals("system-segment-2")));
            assertFalse("Should not contain tenant1 segment",
                metadatas.getList().stream().anyMatch(m -> m.getId().equals("tenant1-segment")));
            return null;
        });
    }

    @Test
    public void testSegmentUpdateAcrossTenants() {
        // Create segment in system tenant
        executionContextManager.executeAsSystem(() -> {
            Segment systemSegment = createTestSegment("system-segment", "System Segment");
            segmentService.setSegmentDefinition(systemSegment);
            return null;
        });

        // Verify tenant1 sees original name
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            Segment segment = segmentService.getSegmentDefinition("system-segment");
            assertEquals("Should see original name", "System Segment", segment.getMetadata().getName());
            return null;
        });

        // Update segment in system tenant
        executionContextManager.executeAsSystem(() -> {
            Segment systemSegment = segmentService.getSegmentDefinition("system-segment");
            systemSegment.getMetadata().setName("Updated System Segment");
            segmentService.setSegmentDefinition(systemSegment);
            return null;
        });

        // Verify tenant1 sees updated name
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            Segment segment = segmentService.getSegmentDefinition("system-segment");
            assertEquals("Should see updated name", "Updated System Segment", segment.getMetadata().getName());
            return null;
        });
    }

    @Test
    public void testSegmentDeletionAcrossTenants() {
        // Create segment in system tenant
        executionContextManager.executeAsSystem(() -> {
            Segment systemSegment = createTestSegment("system-segment", "System Segment");
            segmentService.setSegmentDefinition(systemSegment);
            return null;
        });

        // Create profile in tenant1 that matches the segment
        final String[] profileId = new String[1];
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            Profile profile = createTestProfile();
            persistenceService.save(profile);
            profileId[0] = profile.getItemId();
            assertTrue("Profile should be in system segment",
                segmentService.isProfileInSegment(profile, "system-segment"));
            return null;
        });

        // Delete segment in system tenant
        executionContextManager.executeAsSystem(() -> {
            segmentService.removeSegmentDefinition("system-segment", true);
            return null;
        });

        // Verify segment is removed from tenant1 and profile
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            assertNull("Segment should be removed",
                segmentService.getSegmentDefinition("system-segment"));
            Profile profile = persistenceService.load(profileId[0], Profile.class);
            assertFalse("Profile should not be in removed segment",
                profile.getSegments().contains("system-segment"));
            return null;
        });
    }

    @Test
    public void testCustomEventConditionTypeWithBooleanParent() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create profile
            Profile profile = createTestProfile();
            persistenceService.save(profile);

            // Register custom condition type with boolean parent condition
            ConditionType customEventConditionType = new ConditionType();
            customEventConditionType.setItemId("customEventCondition");
            customEventConditionType.setConditionEvaluator("eventTypeConditionEvaluator"); // Required for proper evaluation

            // Create simple boolean parent condition
            Condition booleanParent = new Condition(definitionsService.getConditionType("booleanCondition"));
            booleanParent.setParameter("operator", "and");

            // Add two simple event conditions
            Condition eventTypeCondition = new Condition(definitionsService.getConditionType("eventTypeCondition"));
            eventTypeCondition.setParameter("eventTypeId", "view");

            Condition propertyCondition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
            propertyCondition.setParameter("propertyName", "testProperty");
            propertyCondition.setParameter("comparisonOperator", "equals");
            propertyCondition.setParameter("propertyValue", "parameter::value");

            booleanParent.setParameter("subConditions", Arrays.asList(eventTypeCondition, propertyCondition));
            customEventConditionType.setParentCondition(booleanParent);

            // Set metadata with eventCondition tag
            customEventConditionType.setMetadata(new Metadata(null, "customEventCondition", "Custom Event Condition", ""));
            customEventConditionType.getMetadata().setSystemTags(new HashSet<>(Arrays.asList("eventCondition")));
            customEventConditionType.getMetadata().setEnabled(true);

            definitionsService.setConditionType(customEventConditionType);

            // Create segment
            Segment segment = createTestSegment("test-segment", "Test Segment");

            // Create past event condition using the custom condition
            Condition pastEventCondition = new Condition(definitionsService.getConditionType("pastEventCondition"));
            Condition customCondition = new Condition(customEventConditionType);
            customCondition.setParameter("value", "test");

            pastEventCondition.setParameter("eventCondition", customCondition);
            pastEventCondition.setParameter("numberOfDays", 30);
            segment.setCondition(pastEventCondition);

            segmentService.setSegmentDefinition(segment);

            // Send matching event
            Event event = createTestEvent(profile, "view");
            event.setProperty("testProperty", "test");
            eventService.send(event);

            // Force recalculation
            segmentService.recalculatePastEventConditions();

            // Verify profile is in segment
            assertTrue("Profile should be in segment", profile.getSegments().contains("test-segment"));

            return null;
        });
    }

    @Test
    public void testMultiplePastEventConditionsWithBoolean() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create profile
            Profile profile = createTestProfile();
            persistenceService.save(profile);

            // Create segment with boolean condition combining two past event conditions
            Segment segment = new Segment();
            segment.setItemId("test-segment");
            segment.setTenantId(executionContextManager.getCurrentContext().getTenantId());

            Metadata metadata = new Metadata();
            metadata.setId("test-segment");
            metadata.setName("Test Segment");
            metadata.setScope("systemscope");
            metadata.setEnabled(true);
            segment.setMetadata(metadata);

            // Create first past event condition (view events)
            Condition eventCondition1 = new Condition(definitionsService.getConditionType("eventTypeCondition"));
            eventCondition1.setParameter("eventTypeId", "view");
            // Ensure event condition type is properly tagged
            eventCondition1.getConditionType().getMetadata().setSystemTags(Collections.singleton("eventCondition"));

            Condition pastEventCondition1 = new Condition(definitionsService.getConditionType("pastEventCondition"));
            pastEventCondition1.setParameter("eventCondition", eventCondition1);
            pastEventCondition1.setParameter("numberOfDays", 30);
            pastEventCondition1.setParameter("minimumCount", 1);
            pastEventCondition1.setParameter("operator", "true");

            // Create second past event condition (login events)
            Condition eventCondition2 = new Condition(definitionsService.getConditionType("eventTypeCondition"));
            eventCondition2.setParameter("eventTypeId", "login");
            // Ensure event condition type is properly tagged
            eventCondition2.getConditionType().getMetadata().setSystemTags(Collections.singleton("eventCondition"));

            Condition pastEventCondition2 = new Condition(definitionsService.getConditionType("pastEventCondition"));
            pastEventCondition2.setParameter("eventCondition", eventCondition2);
            pastEventCondition2.setParameter("numberOfDays", 30);
            pastEventCondition2.setParameter("minimumCount", 1);
            pastEventCondition2.setParameter("operator", "true");

            // Combine with boolean condition
            Condition booleanCondition = new Condition(definitionsService.getConditionType("booleanCondition"));
            booleanCondition.setParameter("operator", "and");
            booleanCondition.setParameter("subConditions", Arrays.asList(pastEventCondition1, pastEventCondition2));

            segment.setCondition(booleanCondition);
            segmentService.setSegmentDefinition(segment);

            // Send first matching event (view)
            Event viewEvent = createTestEvent(profile, "view");
            viewEvent.setPersistent(true);
            eventService.send(viewEvent);
            persistenceService.save(viewEvent);

            // Force event indexing and recalculation
            persistenceService.refresh();
            segmentService.recalculatePastEventConditions();

            // Reload profile and verify intermediate state
            profile = persistenceService.load(profile.getItemId(), Profile.class);
            assertFalse("Profile should not be in segment yet", profile.getSegments().contains("test-segment"));

            // Send second matching event (login)
            Event loginEvent = createTestEvent(profile, "login");
            loginEvent.setPersistent(true);
            eventService.send(loginEvent);
            persistenceService.save(loginEvent);

            // Force event indexing and recalculation
            persistenceService.refresh();
            segmentService.recalculatePastEventConditions();

            // Trigger profile update to force segment evaluation
            Event profileUpdatedEvent = new Event("profileUpdated", null, profile, null, null, profile, new Date());
            profileUpdatedEvent.setPersistent(false);
            eventService.send(profileUpdatedEvent);

            // Reload profile and verify final state
            profile = persistenceService.load(profile.getItemId(), Profile.class);
            assertTrue("Profile should be in segment", profile.getSegments().contains("test-segment"));

            return null;
        });
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetSegmentWithInvalidCondition() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create a segment with invalid condition
            Segment segment = new Segment();
            segment.setItemId("testSegment");
            segment.setMetadata(new Metadata());
            segment.getMetadata().setId("testSegment");
            segment.getMetadata().setEnabled(true);

            // Create an invalid condition (missing required parameters)
            Condition condition = new Condition();
            condition.setConditionTypeId("profilePropertyCondition");

            // Get the condition type from definitions service
            ConditionType conditionType = definitionsService.getConditionType("profilePropertyCondition");
            assertNotNull("Condition type should exist", conditionType);
            condition.setConditionType(conditionType);

            // Don't set required parameters
            segment.setCondition(condition);

            // Should throw IllegalArgumentException since condition is missing required parameters
            segmentService.setSegmentDefinition(segment);
            return null;
        });
    }

    @Test
    public void testSetSegmentWithValidCondition() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create a segment with valid condition
            Segment segment = new Segment();
            Metadata metadata = new Metadata();
            metadata.setId("testSegment");
            metadata.setEnabled(true);
            segment.setMetadata(metadata);

            // Create a valid condition
            Condition condition = new Condition();
            condition.setConditionTypeId("profilePropertyCondition");

            // Get the condition type from definitions service
            ConditionType conditionType = definitionsService.getConditionType("profilePropertyCondition");
            assertNotNull("Condition type should exist", conditionType);
            condition.setConditionType(conditionType);

            // Set all required parameters
            condition.setParameter("propertyName", "properties.testProperty");
            condition.setParameter("comparisonOperator", "equals");
            condition.setParameter("propertyValue", "testValue");
            segment.setCondition(condition);

            // Should not throw any exceptions
            segmentService.setSegmentDefinition(segment);

            // Verify segment was saved
            Segment savedSegment = segmentService.getSegmentDefinition("testSegment");
            assertNotNull("Segment should be saved", savedSegment);
            assertEquals("Should have correct condition type", "profilePropertyCondition",
                savedSegment.getCondition().getConditionTypeId());
            return null;
        });
    }

    @Test
    public void testSetSegmentWithNestedConditions() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create a segment with nested conditions
            Segment segment = new Segment();
            // We have to create the metadata before setting it because the metadata id will be used to set the itemId of the segment
            Metadata metadata = new Metadata();
            metadata.setId("testSegment");
            metadata.setEnabled(true);
            segment.setMetadata(metadata);

            // Create parent condition
            Condition parentCondition = new Condition();
            parentCondition.setConditionTypeId("booleanCondition");

            // Get the condition type from definitions service
            ConditionType booleanConditionType = definitionsService.getConditionType("booleanCondition");
            assertNotNull("Boolean condition type should exist", booleanConditionType);
            parentCondition.setConditionType(booleanConditionType);
            parentCondition.setParameter("operator", "and");

            // Create child condition
            Condition childCondition = new Condition();
            childCondition.setConditionTypeId("profilePropertyCondition");

            // Get the condition type from definitions service
            ConditionType profilePropertyConditionType = definitionsService.getConditionType("profilePropertyCondition");
            assertNotNull("Profile property condition type should exist", profilePropertyConditionType);
            childCondition.setConditionType(profilePropertyConditionType);

            childCondition.setParameter("propertyName", "properties.testProperty");
            childCondition.setParameter("comparisonOperator", "equals");
            childCondition.setParameter("propertyValue", "testValue");

            // Set up nested structure
            List<Condition> subConditions = new ArrayList<>();
            subConditions.add(childCondition);
            parentCondition.setParameter("subConditions", subConditions);

            segment.setCondition(parentCondition);

            // Should not throw any exceptions
            segmentService.setSegmentDefinition(segment);

            // Verify segment was saved with nested conditions
            Segment savedSegment = segmentService.getSegmentDefinition("testSegment");
            assertNotNull("Segment should be saved", savedSegment);
            assertEquals("Should have boolean condition type", "booleanCondition",
                savedSegment.getCondition().getConditionTypeId());
            List<Condition> savedSubConditions = (List<Condition>) savedSegment.getCondition().getParameter("subConditions");
            assertNotNull("Should have sub conditions", savedSubConditions);
            assertEquals("Should have one sub condition", 1, savedSubConditions.size());
            assertEquals("Sub condition should have correct type", "profilePropertyCondition",
                    savedSubConditions.get(0).getConditionTypeId());
            return null;
        });
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetSegmentWithInvalidNestedCondition() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create a segment with nested conditions where child is invalid
            Segment segment = new Segment();
            segment.setItemId("testSegment");
            segment.setMetadata(new Metadata());
            segment.getMetadata().setId("testSegment");
            segment.getMetadata().setEnabled(true);

            // Create parent condition
            Condition parentCondition = new Condition();
            parentCondition.setConditionTypeId("booleanCondition");

            // Get the condition type from definitions service
            ConditionType booleanConditionType = definitionsService.getConditionType("booleanCondition");
            assertNotNull("Boolean condition type should exist", booleanConditionType);
            parentCondition.setConditionType(booleanConditionType);
            parentCondition.setParameter("operator", "and");

            // Create invalid child condition (missing required parameters)
            Condition childCondition = new Condition();
            childCondition.setConditionTypeId("profilePropertyCondition");

            // Get the condition type from definitions service
            ConditionType profilePropertyConditionType = definitionsService.getConditionType("profilePropertyCondition");
            assertNotNull("Profile property condition type should exist", profilePropertyConditionType);
            childCondition.setConditionType(profilePropertyConditionType);

            // Don't set required parameters

            // Set up nested structure
            List<Condition> subConditions = new ArrayList<>();
            subConditions.add(childCondition);
            parentCondition.setParameter("subConditions", subConditions);

            segment.setCondition(parentCondition);

            // Should throw IllegalArgumentException since child condition is missing required parameters
            segmentService.setSegmentDefinition(segment);
            return null;
        });
    }
}
