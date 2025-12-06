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
package org.apache.unomi.services.impl.events;

import org.apache.unomi.api.*;
import org.apache.unomi.api.actions.ActionPostExecutor;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.ConditionValidationService;
import org.apache.unomi.api.services.EventListenerService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.conditions.evaluator.ConditionEvaluatorDispatcher;
import org.apache.unomi.services.TestHelper;
import org.apache.unomi.services.common.security.ExecutionContextManagerImpl;
import org.apache.unomi.services.common.security.KarafSecurityService;
import org.apache.unomi.services.impl.*;
import org.apache.unomi.services.impl.cache.MultiTypeCacheServiceImpl;
import org.apache.unomi.services.impl.definitions.DefinitionsServiceImpl;
import org.apache.unomi.services.common.security.AuditServiceImpl;
import org.apache.unomi.tracing.api.TracerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EventServiceImplTest {

    private EventServiceImpl eventService;
    private TestTenantService tenantService;
    private PersistenceService persistenceService;
    private DefinitionsServiceImpl definitionsService;
    private MultiTypeCacheServiceImpl multiTypeCacheService;
    private ExecutionContextManagerImpl executionContextManager;
    private KarafSecurityService securityService;
    private AuditServiceImpl auditService;

    @Mock
    private BundleContext bundleContext;
    private org.apache.unomi.api.services.SchedulerService schedulerService;
    @Mock
    private EventListenerService eventListener;
    @Mock
    private ServiceReference<EventListenerService> eventListenerReference;
    private ConditionValidationService conditionValidationService;
    private TracerService tracerService;

    private static final String TENANT_1 = "tenant1";
    private static final String TENANT_2 = "tenant2";
    private static final String SYSTEM_TENANT = "system";

    @BeforeEach
    public void setUp() {
        
        tenantService = new TestTenantService();

        // Initialize ConditionValidationService using TestHelper
        this.conditionValidationService = TestHelper.createConditionValidationService();

        // Create tenants using TestHelper
        TestHelper.setupCommonTestData(tenantService);

        // Set up condition evaluator dispatcher
        ConditionEvaluatorDispatcher conditionEvaluatorDispatcher = TestConditionEvaluators.createDispatcher();

        // Set up bundle context using TestHelper
        bundleContext = TestHelper.createMockBundleContext();

        securityService = TestHelper.createSecurityService();
        executionContextManager = TestHelper.createExecutionContextManager(securityService);
        multiTypeCacheService = new MultiTypeCacheServiceImpl();

        // Set up persistence service
        persistenceService = new InMemoryPersistenceServiceImpl(executionContextManager, conditionEvaluatorDispatcher);

        // Create scheduler service using TestHelper
        schedulerService = TestHelper.createSchedulerService("event-service-scheduler-node", persistenceService, executionContextManager, bundleContext, null, -1, true, true);

        // Set up definitions service
        definitionsService = TestHelper.createDefinitionService(persistenceService, bundleContext, schedulerService, multiTypeCacheService, executionContextManager, tenantService, conditionValidationService);

        TestConditionEvaluators.getConditionTypes().forEach((key, value) -> definitionsService.setConditionType(value));

        // Set up event service using TestHelper
        tracerService = TestHelper.createTracerService();
        eventService = TestHelper.createEventService(persistenceService, bundleContext, definitionsService, tenantService, tracerService);

        // Set up event listener mock
        when(bundleContext.getService(eventListenerReference)).thenReturn(eventListener);
        when(eventListener.canHandle(any(Event.class))).thenReturn(true);
        eventService.bind(eventListenerReference);
    }

    @AfterEach
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
            tenantService, securityService, executionContextManager, eventService,
            persistenceService, definitionsService, schedulerService, conditionValidationService,
            multiTypeCacheService, auditService, bundleContext, eventListener,
            eventListenerReference, tracerService
        );
    }

    // ========= Event Creation and Basic Operations Tests =========

    @Test
    public void testSend_BasicEvent() {
        // Create test event
        Event event = createTestEvent();
        event.setPersistent(true);

        // Test
        int result = eventService.send(event);

        // Verify
        assertEquals(EventService.NO_CHANGE, result, "Basic event should not change profile/session state (eventType=test)");
        assertNotNull(persistenceService.load(event.getItemId(), Event.class), "Event should be persisted (eventId=" + event.getItemId() + ")");
        verify(eventListener, times(1)).onEvent(event);
    }

    @Test
    public void testSend_WithProfileUpdate() {
        // Create test event
        Event event = createTestEvent();
        event.setPersistent(true);

        // Setup event listener to trigger profile update
        when(eventListener.canHandle(any(Event.class))).thenReturn(true);
        when(eventListener.onEvent(any(Event.class))).thenReturn(EventService.PROFILE_UPDATED);

        // Test
        int result = eventService.send(event);

        // Verify
        assertEquals(EventService.PROFILE_UPDATED, result & EventService.PROFILE_UPDATED, "Profile update flag should be set after listener triggers update");
        verify(eventListener, times(11)).onEvent(any(Event.class)); // Original event + profileUpdated event recursive
    }

    @Test
    public void testSend_WithPostExecutor() {
        // Create test event
        Event event = createTestEvent();
        event.setPersistent(true);

        // Add post executor
        ActionPostExecutor postExecutor = mock(ActionPostExecutor.class);
        when(postExecutor.execute()).thenReturn(true);
        event.getActionPostExecutors().add(postExecutor);

        // Test
        int result = eventService.send(event);

        // Verify
        verify(postExecutor, times(1)).execute();
        assertEquals(EventService.NO_CHANGE, result, "Post executor should not alter result flags (eventType=test)");
    }

    @Test
    public void testSend_MaxRecursionDepth() {
        // Create test event that triggers profile update
        Event event = createTestEvent();
        event.setPersistent(true);
        when(eventListener.canHandle(any(Event.class))).thenReturn(true);
        when(eventListener.onEvent(any(Event.class))).thenReturn(EventService.PROFILE_UPDATED);

        // Test
        int result = eventService.send(event);

        // Verify that after max recursion is reached, we get NO_CHANGE
        verify(eventListener, times(11)).onEvent(any(Event.class)); // 10 is max recursion depth
        assertEquals(EventService.PROFILE_UPDATED | EventService.SESSION_UPDATED, result, "Result flags should reflect last recursion state");
    }

    @Test
    public void testGetEvent() {
        // Create and save test event
        Event event = createTestEvent();
        persistenceService.save(event);

        // Test
        Event result = eventService.getEvent(event.getItemId());

        // Verify
        assertNotNull(result, "Loaded event should exist (eventId=" + event.getItemId() + ")");
        assertEquals(event.getItemId(), result.getItemId(), "Loaded event id should match requested id");
    }

    @Test
    public void testDeleteEvent() {
        // Create and save test event
        Event event = createTestEvent();
        event.setItemId("testEventId");
        persistenceService.save(event);

        // Test
        eventService.deleteEvent("testEventId");

        // Verify
        assertNull(persistenceService.load("testEventId", Event.class), "Deleted event should not be found (eventId=testEventId)");
    }

    // ========= Event Property and Type Management Tests =========

    @Test
    public void testGetEventProperties() {
        // Create test event properties directly in persistence service
        Map<String, Map<String, Object>> mapping = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> fieldProperties = new HashMap<>();
        fieldProperties.put("type", "string");
        properties.put("properties", fieldProperties);
        mapping.put("testProperty", properties);

        Map<String, Object> nestedProperties = new HashMap<>();
        Map<String, Map<String, Object>> subProperties = new HashMap<>();
        Map<String, Object> subProperty = new HashMap<>();
        subProperty.put("type", "string");
        subProperties.put("subProp", subProperty);
        nestedProperties.put("properties", subProperties);
        mapping.put("nestedProperty", nestedProperties);

        // Set properties mapping directly
        persistenceService.setPropertyMapping(new PropertyType(new Metadata("testProperty")), Event.ITEM_TYPE);
        persistenceService.setPropertyMapping(new PropertyType(new Metadata("nestedProperty")), Event.ITEM_TYPE);

        // Test
        List<EventProperty> result = eventService.getEventProperties();

        // Verify
        assertNotNull(result, "Event properties should be discoverable from mappings");
        assertTrue(result.stream().anyMatch(p -> p.getId().equals("properties.testProperty")), "Flat property should be present (properties.testProperty)");
        assertTrue(result.stream().anyMatch(p -> p.getId().equals("properties.nestedProperty")), "Nested property should be present (properties.nestedProperty)");
    }

    @Test
    public void testGetEventProperties_InvalidMapping() {
        // Setup invalid property mapping
        Map<String, Map<String, Object>> invalidMapping = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        properties.put("type", "invalidType");
        invalidMapping.put("invalidProperty", properties);
        persistenceService.setPropertyMapping(new PropertyType(new Metadata("invalidProperty")), Event.ITEM_TYPE);

        // Test
        List<EventProperty> result = eventService.getEventProperties();

        // Verify invalid mapping is handled
        assertNotNull(result, "Event properties lookup should not fail on invalid mapping");
        assertTrue(result.stream().anyMatch(p -> p.getId().equals("properties.invalidProperty")), "Invalid property mapping should still list id (properties.invalidProperty)");
    }

    @Test
    public void testGetEventTypeIds() {
        // Setup predefined event types
        Set<String> predefinedTypes = new HashSet<>(Arrays.asList("type1", "type2"));
        eventService.setPredefinedEventTypeIds(predefinedTypes);

        // Create and save some events with different types
        Event event1 = createTestEvent();
        event1.setEventType("type3");
        Event event2 = createTestEvent();
        event2.setEventType("type4");
        persistenceService.save(event1);
        persistenceService.save(event2);

        // Test - retry until events are available for aggregation (handles refresh delay)
        Set<String> result = TestHelper.retryUntil(
            () -> eventService.getEventTypeIds(),
            r -> r != null && r.size() >= 4 && r.containsAll(Arrays.asList("type1", "type2", "type3", "type4"))
        );

        // Verify
        assertNotNull(result, "Event type ids should include predefined and persisted types");
        assertEquals(4, result.size(), "All four event types should be returned (type1,type2,type3,type4)");
        assertTrue(result.containsAll(Arrays.asList("type1", "type2", "type3", "type4")), "Returned set should contain expected types");
    }

    // ========= Event Search and Query Tests =========

    @Test
    public void testSearchEvents_WithCondition() {
        // Create and save test event
        Event event = createTestEvent();
        persistenceService.save(event);

        // Create search condition
        Condition condition = new Condition();
        condition.setConditionType(definitionsService.getConditionType("eventPropertyCondition"));
        condition.setParameter("propertyName", "eventType");
        condition.setParameter("comparisonOperator", "equals");
        condition.setParameter("propertyValue", "test");

        // Test - retry until event is available (handles refresh delay)
        PartialList<Event> result = TestHelper.retryQueryUntilAvailable(
            () -> eventService.searchEvents(condition, 0, 10),
            1
        );

        // Verify
        assertNotNull(result, "Search should return results for matching condition");
        assertEquals(1, result.size(), "Exactly one event should match condition");
        assertEquals(event.getItemId(), result.get(0).getItemId(), "Returned event id should match saved event");
    }

    @Test
    public void testSearchEvents_BySessionId() {
        // Create test event
        Event event = createTestEvent();
        event.getSession().setItemId("test-session");
        event.setSessionId("test-session");
        persistenceService.save(event);

        // Test - retry until event is available (handles refresh delay)
        PartialList<Event> result = TestHelper.retryQueryUntilAvailable(
            () -> eventService.searchEvents("test-session", new String[]{"test"}, "", 0, 10, "timeStamp"),
            1
        );

        // Verify
        assertNotNull(result, "Search by session should return results (sessionId=test-session)");
        assertEquals(1, result.size(), "Exactly one event should match session id");
        assertEquals(event.getItemId(), result.get(0).getItemId(), "Returned event id should match saved event");
    }

    @Test
    public void testSearch_WithQuery() {
        // Create test event
        Event event = createTestEvent();
        persistenceService.save(event);

        // Create query
        Query query = new Query();
        query.setText("match");
        query.setLimit(10);

        // Test
        PartialList<Event> result = eventService.search(query);

        // Verify
        assertNotNull(result, "Search should return a PartialList even when no text matches");
        assertTrue(result.getList().isEmpty(), "No events should match full-text 'match'");
    }

    @Test
    public void testSearch_WithScrollQuery() {
        // Create and save test event
        Event event = createTestEvent();
        persistenceService.save(event);

        // Create scroll query
        Query query = new Query();
        query.setScrollTimeValidity("1000");

        // Test - retry until event is available (handles refresh delay)
        PartialList<Event> result = TestHelper.retryQueryUntilAvailable(
            () -> eventService.search(query),
            1
        );

        // Verify
        assertNotNull(result, "Scroll search should initialize a cursor and return results");
        assertEquals(1, result.size(), "Exactly one event should be returned by scroll search");
        assertEquals(event.getItemId(), result.get(0).getItemId(), "Returned event id should match saved event");
    }

    @Test
    public void testSearch_WithFullTextAndCondition() {
        // Create and save test event
        Event event = createTestEvent();
        persistenceService.save(event);

        // Create query with condition and text
        Query query = new Query();
        query.setText("test");
        Condition condition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        condition.setParameter("propertyName", "eventType");
        condition.setParameter("comparisonOperator", "equals");
        condition.setParameter("propertyValue", "test");
        query.setCondition(condition);

        // Test - retry until event is available (handles refresh delay)
        PartialList<Event> result = TestHelper.retryQueryUntilAvailable(
            () -> eventService.search(query),
            1
        );

        // Verify
        assertNotNull(result, "Combined full-text and condition search should return results");
        assertEquals(1, result.size(), "Exactly one event should match combined criteria");
        assertEquals(event.getItemId(), result.get(0).getItemId(), "Returned event id should match saved event");
    }

    @Test
    public void testSearch_EdgeCases() {
        // Test with null query
        Query nullQuery = new Query();
        PartialList<Event> result = eventService.search(nullQuery);
        assertNotNull(result, "Null query should yield an empty PartialList, not null");

        // Test with empty condition
        Query emptyConditionQuery = new Query();
        emptyConditionQuery.setCondition(new Condition());
        result = eventService.search(emptyConditionQuery);
        assertNotNull(result, "Empty condition should yield an empty PartialList, not null");

        // Test with invalid scroll identifier
        Query invalidScrollQuery = new Query();
        invalidScrollQuery.setScrollIdentifier("invalid");
        result = eventService.search(invalidScrollQuery);
        assertNotNull(result, "Invalid scroll id should return an empty PartialList, not null");
    }

    // ========= Event Duplicate Detection Tests =========

    @Test
    public void testHasEventAlreadyBeenRaised() {
        // Create and save test event
        Event event = createTestEvent();
        event.setItemId("test-event");
        event.setSessionId("test-session");
        event.setProfileId("test-profile");
        persistenceService.save(event);

        // Test
        boolean result = eventService.hasEventAlreadyBeenRaised(event);

        // Verify
        assertTrue(result, "Duplicate check should return true for already saved event (eventId=test-event)");
    }

    @Test
    public void testHasEventAlreadyBeenRaised_WithSession() {
        // Create and save test event
        Event event = createTestEvent();
        TestItem target = new TestItem();
        target.setItemId("targetId");
        target.setItemType("targetType");
        event.setTarget(target);
        persistenceService.save(event);

        // Test with session parameter - retry until event is available for query (handles refresh delay)
        boolean result = TestHelper.retryUntil(
            () -> eventService.hasEventAlreadyBeenRaised(event, true),
            r -> r == true
        );

        // Verify
        assertTrue(result, "Duplicate check should respect session scoping when requested");
    }

    // ========= Profile Event Management Tests =========

    @Test
    public void testRemoveProfileEvents() {
        // Create and save test events
        Event event1 = createTestEvent();
        event1.setProfileId("test-profile");
        event1.getProfile().setItemId("test-profile");
        Event event2 = createTestEvent();
        event2.setProfileId("test-profile");
        event2.getProfile().setItemId("test-profile");
        persistenceService.save(event1);
        persistenceService.save(event2);

        // Test
        eventService.removeProfileEvents("test-profile");

        // Verify
        assertNull(persistenceService.load(event1.getItemId(), Event.class), "Profile events should be removed (profileId=test-profile)");
        assertNull(persistenceService.load(event2.getItemId(), Event.class), "Profile events should be removed (profileId=test-profile)");
    }

    // ========= Event Service Lifecycle Tests =========

    @Test
    public void testBindUnbind() {
        // Create mock service reference and event listener
        ServiceReference<EventListenerService> serviceReference = mock(ServiceReference.class);
        EventListenerService eventListener = mock(EventListenerService.class);
        when(bundleContext.getService(serviceReference)).thenReturn(eventListener);

        // Test bind
        eventService.bind(serviceReference);

        // Create and send test event to verify listener was bound
        Event event = createTestEvent();
        when(eventListener.canHandle(event)).thenReturn(true);
        eventService.send(event);
        verify(eventListener).onEvent(event);

        // Test unbind
        eventService.unbind(serviceReference);

        // Create and send another event to verify listener was unbound
        Event event2 = createTestEvent();
        eventService.send(event2);
        verify(eventListener, times(1)).onEvent(any(Event.class)); // Should still be 1 from before
    }

    // ========= Tenant Event Authorization Tests =========

    @Test
    public void testIsEventAllowedForTenant_RestrictedEvent() {
        // Setup restricted event types
        Set<String> restrictedTypes = new HashSet<>(Arrays.asList("restricted"));
        eventService.setRestrictedEventTypeIds(restrictedTypes);

        // Setup tenant with restricted event types and authorized IPs
        Tenant tenant = new Tenant();
        tenant.setItemId(TENANT_1);
        tenant.setRestrictedEventTypes(new HashSet<>(Arrays.asList("restricted")));
        tenant.setAuthorizedIPs(new HashSet<>(Arrays.asList("127.0.0.1")));
        tenantService.saveTenant(tenant);

        // Create test event
        Event event = createTestEvent();
        event.setEventType("restricted");

        // Test
        boolean result = eventService.isEventAllowedForTenant(event, TENANT_1, "127.0.0.1");

        // Verify
        assertTrue(result);
    }

    @Test
    public void testEventAllowed() {
        String tenantId = "test_tenant";
        String allowedSourceIP = "127.0.0.1";
        String unallowedSourceIP = "127.0.0.2";

        // Create test tenant with restricted event types and authorized IPs
        Tenant tenant = new Tenant();
        tenant.setItemId(tenantId);
        Set<String> restrictedTypes = new HashSet<>(Arrays.asList("test1", "test2", "test4"));
        tenant.setRestrictedEventTypes(restrictedTypes);
        Set<String> ips = new HashSet<>(Arrays.asList(allowedSourceIP));
        tenant.setAuthorizedIPs(ips);
        tenantService.saveTenant(tenant);

        // Test with initial restrictions
        // Restricted events should be checked against IP
        assertTrue(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, new Date()), tenantId, allowedSourceIP), "Restricted event should be allowed from authorized IPv4 (tenant=" + tenantId + ")");
        assertFalse(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, new Date()), tenantId, unallowedSourceIP), "Restricted event should be blocked from unauthorized IPv4 (tenant=" + tenantId + ")");

        assertTrue(eventService.isEventAllowedForTenant(new Event("test2", null, new Profile(), null, null, null, new Date()), tenantId, allowedSourceIP), "Restricted event should be allowed from authorized IPv4 (tenant=" + tenantId + ")");
        assertFalse(eventService.isEventAllowedForTenant(new Event("test2", null, new Profile(), null, null, null, new Date()), tenantId, unallowedSourceIP), "Restricted event should be blocked from unauthorized IPv4 (tenant=" + tenantId + ")");

        // Unrestricted events should be allowed regardless of IP
        assertTrue(eventService.isEventAllowedForTenant(new Event("test3", null, new Profile(), null, null, null, new Date()), tenantId, allowedSourceIP), "Unrestricted event should be allowed regardless of IP");

        assertTrue(eventService.isEventAllowedForTenant(new Event("test4", null, new Profile(), null, null, null, new Date()), tenantId, allowedSourceIP), "Restricted event should be allowed from authorized IPv4 (tenant=" + tenantId + ")");

        // Update tenant restrictions to only restrict test4
        restrictedTypes = new HashSet<>(Arrays.asList("test4"));
        tenant.setRestrictedEventTypes(restrictedTypes);
        tenantService.saveTenant(tenant);

        // Test with updated restrictions
        // test4 should be IP checked
        assertTrue(eventService.isEventAllowedForTenant(new Event("test4", null, new Profile(), null, null, null, new Date()), tenantId, allowedSourceIP), "Restricted event should be allowed from authorized IPv4 after update");
        assertFalse(eventService.isEventAllowedForTenant(new Event("test4", null, new Profile(), null, null, null, new Date()), tenantId, unallowedSourceIP), "Restricted event should be blocked from unauthorized IPv4 after update");

        // All other events should be allowed, regardless of IP since they are not restricted
        assertTrue(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, new Date()), tenantId, unallowedSourceIP), "Unrestricted event should be allowed (IPv4, tenant=" + tenantId + ")");
        assertTrue(eventService.isEventAllowedForTenant(new Event("test2", null, new Profile(), null, null, null, new Date()), tenantId, unallowedSourceIP), "Unrestricted event should be allowed (IPv4, tenant=" + tenantId + ")");
        assertTrue(eventService.isEventAllowedForTenant(new Event("test3", null, new Profile(), null, null, null, new Date()), tenantId, unallowedSourceIP), "Unrestricted event should be allowed (IPv4, tenant=" + tenantId + ")");
    }

    @Test
    public void testIsEventAllowedForTenant_TenantAndGlobalRestrictions() {
        // Setup global restricted event types
        Set<String> globalRestrictedTypes = new HashSet<>(Arrays.asList("globalRestricted1", "globalRestricted2"));
        eventService.setRestrictedEventTypeIds(globalRestrictedTypes);

        // Setup tenant with its own restricted event types
        Tenant tenant = new Tenant();
        tenant.setItemId(TENANT_1);
        tenant.setRestrictedEventTypes(new HashSet<>(Arrays.asList("tenantRestricted1", "tenantRestricted2")));
        tenant.setAuthorizedIPs(new HashSet<>(Arrays.asList("127.0.0.1")));
        tenantService.saveTenant(tenant);

        // Test cases
        // 1. Event restricted by tenant - should check IP
        Event tenantRestrictedEvent = createTestEvent();
        tenantRestrictedEvent.setEventType("tenantRestricted1");
        assertTrue(eventService.isEventAllowedForTenant(tenantRestrictedEvent, TENANT_1, "127.0.0.1"), "Tenant-restricted event should be allowed from authorized IP");
        assertFalse(eventService.isEventAllowedForTenant(tenantRestrictedEvent, TENANT_1, "192.168.1.1"), "Tenant-restricted event should be blocked from unauthorized IP");

        // 2. Event restricted globally - should check IP
        Event globalRestrictedEvent = createTestEvent();
        globalRestrictedEvent.setEventType("globalRestricted1");
        assertTrue(eventService.isEventAllowedForTenant(globalRestrictedEvent, TENANT_1, "127.0.0.1"), "Globally restricted event should be allowed from authorized IP");
        assertFalse(eventService.isEventAllowedForTenant(globalRestrictedEvent, TENANT_1, "192.168.1.1"), "Globally restricted event should be blocked from unauthorized IP");

        // 3. Event not restricted by either - should be accepted without IP check
        Event unrestrictedEvent = createTestEvent();
        unrestrictedEvent.setEventType("unrestricted");
        assertTrue(eventService.isEventAllowedForTenant(unrestrictedEvent, TENANT_1, "127.0.0.1"), "Unrestricted event should be allowed regardless of IP");
        assertTrue(eventService.isEventAllowedForTenant(unrestrictedEvent, TENANT_1, "192.168.1.1"), "Unrestricted event should be allowed regardless of IP");

        // 4. Test with another tenant that doesn't have any restrictions
        Tenant tenant2 = new Tenant();
        tenant2.setItemId(TENANT_2);
        tenant2.setRestrictedEventTypes(new HashSet<>());
        tenant2.setAuthorizedIPs(new HashSet<>(Arrays.asList("127.0.0.1")));
        tenantService.saveTenant(tenant2);

        // Should still check IP for global-restricted events even if tenant doesn't have local restrictions
        assertTrue(eventService.isEventAllowedForTenant(globalRestrictedEvent, TENANT_2, "127.0.0.1"), "Global restriction applies across tenants (allowed IP)");
        assertFalse(eventService.isEventAllowedForTenant(globalRestrictedEvent, TENANT_2, "192.168.1.1"), "Global restriction applies across tenants (unauthorized IP)");
    }

    @Test
    public void testIsEventAllowedForTenant_NoTenantRestrictions() {
        // Setup global restricted event types
        Set<String> globalRestrictedTypes = new HashSet<>(Arrays.asList("globalRestricted"));
        eventService.setRestrictedEventTypeIds(globalRestrictedTypes);

        // Setup tenant with no event type restrictions
        Tenant tenant = new Tenant();
        tenant.setItemId(TENANT_1);
        tenant.setAuthorizedIPs(new HashSet<>(Arrays.asList("127.0.0.1")));
        tenantService.saveTenant(tenant);

        // 1. Event restricted globally - should check IP
        Event globalRestrictedEvent = createTestEvent();
        globalRestrictedEvent.setEventType("globalRestricted");
        assertTrue(eventService.isEventAllowedForTenant(globalRestrictedEvent, TENANT_1, "127.0.0.1")); // Allowed IP
        assertFalse(eventService.isEventAllowedForTenant(globalRestrictedEvent, TENANT_1, "192.168.1.1")); // Unauthorized IP

        // 2. Event not restricted - should be accepted without IP check
        Event unrestrictedEvent = createTestEvent();
        unrestrictedEvent.setEventType("unrestricted");
        assertTrue(eventService.isEventAllowedForTenant(unrestrictedEvent, TENANT_1, "127.0.0.1")); // Any IP should work
        assertTrue(eventService.isEventAllowedForTenant(unrestrictedEvent, TENANT_1, "192.168.1.1")); // Any IP should work
    }

    // ========= IP Authorization Tests =========

    @Test
    public void testIPv6EventAllowed() {
        String tenantId = "test_tenant";

        // Create test tenant with IPv6 restrictions and event types
        Tenant tenant = new Tenant();
        tenant.setItemId(tenantId);
        Set<String> restrictedTypes = new HashSet<>(Arrays.asList("test1", "test2", "test4"));
        tenant.setRestrictedEventTypes(restrictedTypes);
        Set<String> ips = new HashSet<>(Arrays.asList(
            "2001:db8::/32",                  // IPv6 CIDR block
            "::1",                            // IPv6 localhost
            "2001:db8::1",                    // Single IPv6 address
            "2001:db8:3:4:5:6:7:8"           // Full IPv6 address
        ));
        tenant.setAuthorizedIPs(ips);
        tenantService.saveTenant(tenant);

        // Test IPv6 addresses with square brackets (as returned by HttpServletRequest.getRemoteAddr)
        assertTrue(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, new Date()),
            tenantId, "[2001:db8::1]"), "IPv6 in brackets should be accepted when authorized (tenant=" + tenantId + ")");
        assertTrue(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, new Date()),
            tenantId, "[2001:db8:1:2:3:4:5:6]"), "IPv6 full form in brackets should be accepted when authorized");
        assertTrue(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, new Date()),
            tenantId, "[::1]"), "IPv6 localhost in brackets should be accepted");

        // Test IPv6 addresses without square brackets
        assertTrue(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, new Date()),
            tenantId, "2001:db8::1"), "IPv6 without brackets should be accepted when authorized");
        assertTrue(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, new Date()),
            tenantId, "2001:db8:3:4:5:6:7:8"), "Full IPv6 should be accepted when authorized");
        assertTrue(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, new Date()),
            tenantId, "::1"), "IPv6 localhost should be accepted");

        // Test unauthorized IPv6 addresses
        assertFalse(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, new Date()),
            tenantId, "[2001:db9::1]"), "IPv6 with different prefix should be rejected (brackets)");
        assertFalse(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, new Date()),
            tenantId, "2001:db9::1"), "IPv6 with different prefix should be rejected");
    }

    @Test
    public void testMixedIPv4AndIPv6EventAllowed() {
        String tenantId = "test_tenant";

        // Create test tenant with mixed IPv4 and IPv6 addresses and restricted event types
        Tenant tenant = new Tenant();
        tenant.setItemId(tenantId);
        Set<String> restrictedTypes = new HashSet<>(Arrays.asList("test1"));
        tenant.setRestrictedEventTypes(restrictedTypes);
        Set<String> ips = new HashSet<>(Arrays.asList(
            "127.0.0.1",                      // IPv4 localhost
            "192.168.1.0/24",                 // IPv4 CIDR block
            "2001:db8::/32",                  // IPv6 CIDR block
            "::1"                             // IPv6 localhost
        ));
        tenant.setAuthorizedIPs(ips);
        tenantService.saveTenant(tenant);

        // Test IPv4 addresses for restricted events
        assertTrue(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, new Date()),
            tenantId, "127.0.0.1"), "IPv4 localhost should be accepted for restricted event");
        assertTrue(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, new Date()),
            tenantId, "192.168.1.100"), "IPv4 in allowed CIDR should be accepted for restricted event");

        // Test IPv6 addresses with and without brackets for restricted events
        assertTrue(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, new Date()),
            tenantId, "[::1]"), "IPv6 localhost in brackets should be accepted for restricted event");
        assertTrue(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, new Date()),
            tenantId, "::1"), "IPv6 localhost should be accepted for restricted event");
        assertTrue(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, new Date()),
            tenantId, "[2001:db8::1]"), "IPv6 address in allowed CIDR (brackets) should be accepted");
        assertTrue(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, new Date()),
            tenantId, "2001:db8::1"), "IPv6 address in allowed CIDR should be accepted");

        // Test unauthorized IPs
        assertFalse(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, new Date()),
            tenantId, "192.168.2.1"), "IPv4 outside allowed range should be rejected for restricted event");
        assertFalse(eventService.isEventAllowedForTenant(new Event("test1", null, new Profile(), null, null, null, new Date()),
            tenantId, "[2001:db9::1]"), "IPv6 outside allowed CIDR should be rejected for restricted event");
    }

    // ========= Critical Edge Cases Tests =========

    @Test
    public void testTenantStateChange() {
        // Setup initial tenant state
        Tenant tenant = new Tenant();
        tenant.setItemId(TENANT_1);
        tenant.setRestrictedEventTypes(new HashSet<>(Arrays.asList("restricted")));
        tenant.setAuthorizedIPs(new HashSet<>(Arrays.asList("127.0.0.1")));
        tenantService.saveTenant(tenant);

        // Create test event
        Event event = createTestEvent();
        event.setEventType("restricted");

        // Verify initial state
        assertTrue(eventService.isEventAllowedForTenant(event, TENANT_1, "127.0.0.1"), "Restricted event should be allowed initially (tenant=" + TENANT_1 + ")");

        // Simulate tenant being disabled/deleted
        tenantService.deleteTenant(TENANT_1);

        // Verify behavior with missing tenant
        assertFalse(eventService.isEventAllowedForTenant(event, TENANT_1, "127.0.0.1"), "Event should be rejected when tenant is deleted (tenant=" + TENANT_1 + ")");
    }

    @Test
    public void testMalformedEventProperties() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create event with malformed properties
            Event event = createTestEvent();
            Map<String, Object> properties = new HashMap<>();
            properties.put("validKey", "validValue");
            properties.put("nullKey", null);
            properties.put("nestedNull", Collections.singletonMap("key", null));
            event.setProperties(properties);
            event.setPersistent(true);

            // Test
            int result = eventService.send(event);

            // Verify the event is handled gracefully
            assertEquals(EventService.NO_CHANGE, result, "Malformed properties should not break event processing");
            Event savedEvent = persistenceService.load(event.getItemId(), Event.class);
            assertNotNull(savedEvent, "Event should be persisted despite malformed properties (eventId=" + event.getItemId() + ")");
            assertNotNull(savedEvent.getProperties().get("validKey"), "Valid property should be preserved in persistence (key=validKey)");
        });
    }

    // ========= Helper Classes =========

    public static class TestItem extends Item {
        public static final String ITEM_TYPE = "testItem";
        private static final long serialVersionUID = 1L;
    }

    // ========= Helper Methods =========

    private Event createTestEvent() {
        Date timeStamp = new Date();
        Event event = new Event();
        event.setTimeStamp(timeStamp);
        event.setEventType("test");
        event.setScope("testScope");
        event.setItemId(UUID.randomUUID().toString());
        event.setActionPostExecutors(new ArrayList<>());
        event.setAttributes(Collections.emptyMap());

        Profile profile = new Profile(executionContextManager.getCurrentContext().getTenantId());
        profile.setItemId("test-profile");
        event.setProfile(profile);
        event.setProfileId(profile.getItemId());

        Session session = new Session("test-session", profile, timeStamp, "test");
        event.setSession(session);
        event.setSessionId(session.getItemId());

        return event;
    }
}
