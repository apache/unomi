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

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.rules.RuleStatistics;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.RuleListenerService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluatorDispatcher;
import org.apache.unomi.services.impl.InMemoryPersistenceServiceImpl;
import org.apache.unomi.services.impl.TestActionExecutorDispatcher;
import org.apache.unomi.services.impl.TestConditionEvaluators;
import org.apache.unomi.services.impl.TestTenantService;
import org.apache.unomi.services.impl.definitions.DefinitionsServiceImpl;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class RulesServiceImplTest {

    private RulesServiceImpl rulesService;
    private TestTenantService tenantService;
    private PersistenceService persistenceService;
    private DefinitionsServiceImpl definitionsService;

    @Mock
    private BundleContext bundleContext;
    @Mock
    private EventService eventService;
    private TestActionExecutorDispatcher actionExecutorDispatcher;
    @Mock
    private org.apache.unomi.api.services.SchedulerService schedulerService;

    private static final String TENANT_1 = "tenant1";
    private static final String TENANT_2 = "tenant2";
    private static final String SYSTEM_TENANT = "system";

    @Before
    public void setUp() {

        MockitoAnnotations.initMocks(this);
        tenantService = new TestTenantService();
        // Register condition evaluators and condition types in system tenant.
        tenantService.setCurrentTenant(SYSTEM_TENANT);

        // Create tenants
        tenantService.createTenant(SYSTEM_TENANT, Collections.singletonMap("description", "System tenant"));
        tenantService.createTenant(TENANT_1, Collections.singletonMap("description", "Tenant 1"));
        tenantService.createTenant(TENANT_2, Collections.singletonMap("description", "Tenant 2"));

        ConditionEvaluatorDispatcher conditionEvaluatorDispatcher = TestConditionEvaluators.createDispatcher();

        persistenceService = new InMemoryPersistenceServiceImpl(tenantService, conditionEvaluatorDispatcher);
        definitionsService = new DefinitionsServiceImpl();
        definitionsService.setPersistenceService(persistenceService);
        definitionsService.setTenantService(tenantService);

        TestConditionEvaluators.getConditionTypes().forEach((key, value) -> definitionsService.setConditionType(value));

        rulesService = new RulesServiceImpl();

        // Mock bundle context
        Bundle bundle = mock(Bundle.class);
        when(bundleContext.getBundle()).thenReturn(bundle);
        when(bundle.getBundleContext()).thenReturn(bundleContext);
        when(bundle.findEntries(eq("META-INF/cxs/rules"), eq("*.json"), eq(true))).thenReturn(null);
        when(bundleContext.getBundles()).thenReturn(new Bundle[0]);

        // Mock scheduler service
        when(schedulerService.getScheduleExecutorService()).thenReturn(java.util.concurrent.Executors.newSingleThreadScheduledExecutor());

        // Set up action executor dispatcher
        actionExecutorDispatcher = new TestActionExecutorDispatcher();
        actionExecutorDispatcher.setDefaultReturnValue(EventService.PROFILE_UPDATED);

        rulesService.setBundleContext(bundleContext);
        rulesService.setPersistenceService(persistenceService);
        rulesService.setDefinitionsService(definitionsService);
        rulesService.setEventService(eventService);
        rulesService.setActionExecutorDispatcher(actionExecutorDispatcher);
        rulesService.setTenantService(tenantService);
        rulesService.setSchedulerService(schedulerService);

        // Set up condition types
        setupActionTypes();

        // Initialize rule caches
        rulesService.postConstruct();

        // Set default tenant
        tenantService.setCurrentTenant(TENANT_1);

    }

    private void setupActionTypes() {
        // Create and register action type
        ActionType testActionType = new ActionType();
        testActionType.setItemId("test");
        Metadata actionMetadata = new Metadata();
        actionMetadata.setId("test");
        actionMetadata.setEnabled(true);
        testActionType.setMetadata(actionMetadata);
        definitionsService.setActionType(testActionType);

    }

    private Event createTestEvent() {
        Event event = new Event();
        event.setEventType("test");
        String currentTenant = tenantService.getCurrentTenantId();
        Profile profile = new Profile(currentTenant);
        profile.setProperty("testProperty", "testValue"); // Add test property for profile condition
        event.setProfile(profile);
        Session session = new Session();
        session.setProfile(profile);
        session.setTenantId(currentTenant);
        session.setProperty("testProperty", "testValue"); // Add test property for session condition
        event.setSession(session);
        event.setTenantId(currentTenant);
        event.setAttributes(new HashMap<>());
        return event;
    }

    @Test
    public void testGetMatchingRules_NoRules() {
        tenantService.setCurrentTenant(TENANT_1);
        Event event = createTestEvent();
        Set<Rule> matchedRules = rulesService.getMatchingRules(event);
        assertTrue("Should return empty set when no rules match", matchedRules.isEmpty());
    }

    @Test
    public void testGetMatchingRules_WithMatchingRule() {
        // Setup test data
        Event event = createTestEvent();
        Rule rule = createTestRule();
        rule.setTenantId(TENANT_1);
        rulesService.setRule(rule);

        // Execute test
        Set<Rule> matchedRules = rulesService.getMatchingRules(event);

        // Verify results
        assertFalse("Should return non-empty set when rule matches", matchedRules.isEmpty());
        assertEquals("Should return one matching rule", 1, matchedRules.size());
        assertTrue("Should contain the matching rule", matchedRules.contains(rule));
    }

    @Test
    public void testRuleInheritanceFromSystemTenant() {
        // Create a rule in system tenant
        tenantService.setCurrentTenant(SYSTEM_TENANT);
        Rule systemRule = createTestRule();
        systemRule.setItemId("system-rule");
        rulesService.setRule(systemRule);

        // Create a rule in tenant1
        tenantService.setCurrentTenant(TENANT_1);
        Rule tenantRule = createTestRule();
        tenantRule.setItemId("tenant-rule");
        rulesService.setRule(tenantRule);

        // Test that tenant1 can see both rules
        Set<Rule> allRules = new HashSet<>(rulesService.getAllRules());
        assertEquals("Should see both system and tenant rules", 2, allRules.size());
        assertTrue("Should contain system rule", allRules.stream().anyMatch(r -> r.getItemId().equals("system-rule")));
        assertTrue("Should contain tenant rule", allRules.stream().anyMatch(r -> r.getItemId().equals("tenant-rule")));

        // Test that tenant2 can only see system rule
        tenantService.setCurrentTenant(TENANT_2);
        allRules = new HashSet<>(rulesService.getAllRules());
        assertEquals("Should only see system rule", 1, allRules.size());
        assertTrue("Should contain system rule", allRules.stream().anyMatch(r -> r.getItemId().equals("system-rule")));
    }

    @Test
    public void testRuleStatisticsAreTenantSpecific() {
        // Setup rules in different tenants
        tenantService.setCurrentTenant(TENANT_1);
        Rule tenant1Rule = createTestRule();
        tenant1Rule.setItemId("tenant1-rule");
        tenant1Rule.setTenantId(TENANT_1);
        rulesService.setRule(tenant1Rule);

        tenantService.setCurrentTenant(TENANT_2);
        Rule tenant2Rule = createTestRule();
        tenant2Rule.setItemId("tenant2-rule");
        tenant2Rule.setTenantId(TENANT_2);
        rulesService.setRule(tenant2Rule);

        // Test statistics for tenant1
        tenantService.setCurrentTenant(TENANT_1);
        Event event1 = createTestEvent();
        rulesService.getMatchingRules(event1);
        RuleStatistics stats1 = rulesService.getRuleStatistics(tenant1Rule.getItemId());
        assertNotNull("Tenant1 rule statistics should exist", stats1);

        // Test statistics for tenant2
        tenantService.setCurrentTenant(TENANT_2);
        Event event2 = createTestEvent();
        rulesService.getMatchingRules(event2);
        RuleStatistics stats2 = rulesService.getRuleStatistics(tenant2Rule.getItemId());
        assertNotNull("Tenant2 rule statistics should exist", stats2);

        // Verify tenant isolation
        assertNull("Tenant2 should not see tenant1's rule statistics",
            rulesService.getRuleStatistics(tenant1Rule.getItemId()));
    }

    private Rule createTestRule() {
        Rule rule = new Rule();
        rule.setItemId("test-rule");
        rule.setTenantId(tenantService.getCurrentTenantId());

        Metadata metadata = new Metadata();
        metadata.setId("test-rule");
        metadata.setScope("systemscope");
        metadata.setEnabled(true);
        rule.setMetadata(metadata);

        // Create a simple condition
        Condition condition = new Condition();
        condition.setConditionType(definitionsService.getConditionType("eventTypeCondition"));
        condition.setParameter("eventTypeId", "test");
        rule.setCondition(condition);

        // Create a simple action
        Action action = new Action();
        action.setActionType(definitionsService.getActionType("test"));
        rule.setActions(Collections.singletonList(action));

        return rule;
    }

    @Test
    public void testOnEvent_ExecutesActions() {
        // Setup test data
        Event event = createTestEvent();
        Rule rule = createTestRule();
        rule.setTenantId(TENANT_1);
        rulesService.setRule(rule);

        // Execute test
        int result = rulesService.onEvent(event);

        // Verify results
        assertEquals("Should return PROFILE_UPDATED flag", EventService.PROFILE_UPDATED, result);
    }

    @Test
    public void testRuleStatistics() {
        // Setup test data
        Event event = createTestEvent();
        Rule rule = createTestRule();
        rule.setTenantId(TENANT_1);
        rulesService.setRule(rule);

        rulesService.refreshRules();

        // Execute test
        Set<Rule> matchingRules = rulesService.getMatchingRules(event);
        assertNotNull("Rule statistics should exist", matchingRules);
        assertTrue("Rule statistics should exist", !matchingRules.isEmpty());

        // Verify statistics were updated
        RuleStatistics stats = rulesService.getRuleStatistics(rule.getItemId());
        assertNotNull("Rule statistics should be created", stats);
        assertEquals("Statistics should have correct tenant ID", TENANT_1, stats.getTenantId());
    }

    @Test
    public void testRuleListener() {
        // Setup test data
        Event event = createTestEvent();
        Rule rule = createTestRule();
        rulesService.setRule(rule);

        // Create and register mock listener
        RuleListenerService listener = mock(RuleListenerService.class);
        ServiceReference<RuleListenerService> serviceRef = mock(ServiceReference.class);
        when(bundleContext.getService(serviceRef)).thenReturn(listener);

        rulesService.bind(serviceRef);

        // Execute test
        rulesService.fireEvaluate(rule, event);

        // Verify listener was called
        verify(listener, times(1)).onEvaluate(eq(rule), eq(event));
    }

    @Test
    public void testSetRule() {
        // Setup
        Rule rule = createTestRule();
        rule.setTenantId(TENANT_1);

        // Execute
        rulesService.setRule(rule);

        // Verify
        Rule savedRule = persistenceService.load(rule.getItemId(), Rule.class);
        assertNotNull("Rule should be saved", savedRule);
        assertEquals("Rule should have correct tenant", TENANT_1, savedRule.getTenantId());
        assertEquals("Rule should have correct scope", "systemscope", savedRule.getMetadata().getScope());
    }

    @Test
    public void testRemoveRule() {
        // Setup
        Rule rule = createTestRule();
        rule.setTenantId(TENANT_1);
        rulesService.setRule(rule);

        // Execute
        rulesService.removeRule(rule.getItemId());

        // Verify
        assertNull("Rule should be removed", persistenceService.load(rule.getItemId(), Rule.class));
    }

    @Test
    public void testGetRule() {
        // Setup
        Rule rule = createTestRule();
        rule.setItemId("test-rule-id");
        rule.setTenantId(TENANT_1);
        rulesService.setRule(rule);

        // Execute
        Rule result = rulesService.getRule(rule.getItemId());

        // Verify
        assertNotNull("Should return the rule", result);
        assertEquals("Should return the correct rule", rule.getItemId(), result.getItemId());
        assertEquals("Should have correct tenant", TENANT_1, result.getTenantId());
    }

    @Test
    public void testGetRuleStatistics() {
        // Setup
        tenantService.setCurrentTenant(TENANT_1);
        Rule rule = createTestRule();
        rule.setTenantId(TENANT_1);
        rulesService.setRule(rule);

        // Execute
        rulesService.resetAllRuleStatistics();

        // Verify
        RuleStatistics stats = rulesService.getRuleStatistics(rule.getItemId());
        assertNull("Statistics should be reset", stats);
    }

    @Test
    public void testEventRaisedOnlyOnce() {
        // Setup
        tenantService.setCurrentTenant(TENANT_1);
        Event event = createTestEvent();
        Rule rule = createTestRule();
        rule.setRaiseEventOnlyOnce(true);
        rulesService.setRule(rule);

        when(eventService.hasEventAlreadyBeenRaised(any(Event.class))).thenReturn(true);

        // Execute
        Set<Rule> matchedRules = rulesService.getMatchingRules(event);

        // Verify
        assertTrue("Should not match rule when event already raised", matchedRules.isEmpty());
    }

    @Test
    public void testEventRaisedOnlyOnceForProfile() {
        // Setup
        tenantService.setCurrentTenant(TENANT_1);
        Event event = createTestEvent();
        Rule rule = createTestRule();
        rule.setRaiseEventOnlyOnceForProfile(true);
        rulesService.setRule(rule);

        when(eventService.hasEventAlreadyBeenRaised(any(Event.class), eq(false))).thenReturn(true);

        // Execute
        Set<Rule> matchedRules = rulesService.getMatchingRules(event);

        // Verify
        assertTrue("Should not match rule when event already raised for profile", matchedRules.isEmpty());
    }

    @Test
    public void testProfileConditionMatching() {
        // Setup
        Event event = createTestEvent();
        Rule rule = createTestRule();
        rule.setTenantId(TENANT_1);

        // Create event condition
        Condition eventCondition = new Condition();
        eventCondition.setConditionType(definitionsService.getConditionType("eventTypeCondition"));
        eventCondition.setParameter("eventTypeId", "test");

        // Create profile condition
        Condition profileCondition = new Condition();
        profileCondition.setConditionType(definitionsService.getConditionType("profilePropertyCondition"));
        profileCondition.setParameter("propertyName", "properties.testProperty");
        profileCondition.setParameter("comparisonOperator", "equals");
        profileCondition.setParameter("propertyValue", "testValue");

        // Combine conditions with AND
        Condition booleanCondition = new Condition();
        booleanCondition.setConditionType(definitionsService.getConditionType("booleanCondition"));
        booleanCondition.setParameter("operator", "and");
        booleanCondition.setParameter("subConditions", Arrays.asList(eventCondition, profileCondition));

        rule.setCondition(booleanCondition);
        rulesService.setRule(rule);

        // Set the profile property to match
        Profile profile = event.getProfile();
        profile.setProperty("testProperty", "testValue");
        event.setProfile(profile);

        // Execute
        Set<Rule> matchedRules = rulesService.getMatchingRules(event);

        // Verify
        assertFalse("Should match rule when both event and profile conditions match", matchedRules.isEmpty());
    }

    @Test
    public void testSessionConditionMatching() {
        // Setup
        Event event = createTestEvent();
        Rule rule = createTestRule();
        rule.setTenantId(TENANT_1);

        // Create event condition
        Condition eventCondition = new Condition();
        eventCondition.setConditionType(definitionsService.getConditionType("eventTypeCondition"));
        eventCondition.setParameter("eventTypeId", "test");

        // Create session condition
        Condition sessionCondition = new Condition();
        sessionCondition.setConditionType(definitionsService.getConditionType("sessionPropertyCondition"));
        sessionCondition.setParameter("propertyName", "properties.testProperty");
        sessionCondition.setParameter("comparisonOperator", "equals");
        sessionCondition.setParameter("propertyValue", "testValue");

        // Combine conditions with AND
        Condition booleanCondition = new Condition();
        booleanCondition.setConditionType(definitionsService.getConditionType("booleanCondition"));
        booleanCondition.setParameter("operator", "and");
        booleanCondition.setParameter("subConditions", Arrays.asList(eventCondition, sessionCondition));

        rule.setCondition(booleanCondition);
        rulesService.setRule(rule);

        // Set the session property to match
        Session session = event.getSession();
        session.setProperty("testProperty", "testValue");
        event.setSession(session);

        // Execute
        Set<Rule> matchedRules = rulesService.getMatchingRules(event);

        // Verify
        assertFalse("Should match rule when both event and session conditions match", matchedRules.isEmpty());
    }

    @Test
    public void testRefreshRulesHandlesAllTenants() {
        // Setup rules in different tenants
        tenantService.setCurrentTenant(SYSTEM_TENANT);
        Rule systemRule = createTestRule();
        systemRule.setItemId("system-rule");
        rulesService.setRule(systemRule);

        tenantService.setCurrentTenant(TENANT_1);
        Rule tenant1Rule = createTestRule();
        tenant1Rule.setItemId("tenant1-rule");
        rulesService.setRule(tenant1Rule);

        tenantService.setCurrentTenant(TENANT_2);
        Rule tenant2Rule = createTestRule();
        tenant2Rule.setItemId("tenant2-rule");
        rulesService.setRule(tenant2Rule);

        // Execute refresh
        rulesService.refreshRules();

        // Verify rules are loaded for all tenants
        tenantService.setCurrentTenant(SYSTEM_TENANT);
        assertNotNull("System rule should be loaded", rulesService.getRule("system-rule"));

        tenantService.setCurrentTenant(TENANT_1);
        assertNotNull("Tenant1 rule should be loaded", rulesService.getRule("tenant1-rule"));

        tenantService.setCurrentTenant(TENANT_2);
        assertNotNull("Tenant2 rule should be loaded", rulesService.getRule("tenant2-rule"));
    }
}
