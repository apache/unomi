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

import org.apache.unomi.api.*;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.rules.RuleStatistics;
import org.apache.unomi.api.services.ConditionValidationService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.RuleListenerService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluatorDispatcher;
import org.apache.unomi.services.TestHelper;
import org.apache.unomi.services.impl.*;
import org.apache.unomi.services.impl.cache.MultiTypeCacheServiceImpl;
import org.apache.unomi.services.impl.definitions.DefinitionsServiceImpl;
import org.apache.unomi.services.impl.tenants.AuditServiceImpl;
import org.apache.unomi.tracing.api.RequestTracer;
import org.apache.unomi.tracing.api.TracerService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;


public class RulesServiceImplTest {

    private RulesServiceImpl rulesService;
    private TestTenantService tenantService;
    private PersistenceService persistenceService;
    private DefinitionsServiceImpl definitionsService;
    private MultiTypeCacheServiceImpl multiTypeCacheService;
    private ExecutionContextManagerImpl executionContextManager;
    private KarafSecurityService securityService;
    private AuditServiceImpl auditService;
    private ConditionValidationService conditionValidationService;

    @Mock
    private BundleContext bundleContext;
    private EventService eventService;
    private TracerService tracerService;
    private RequestTracer requestTracer;

    private TestActionExecutorDispatcher actionExecutorDispatcher;
    private org.apache.unomi.api.services.SchedulerService schedulerService;

    private static final String TENANT_1 = "tenant1";
    private static final String TENANT_2 = "tenant2";
    private static final String SYSTEM_TENANT = "system";

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        tracerService = TestHelper.createTracerService();
        tenantService = new TestTenantService();

        // Initialize ConditionValidationService using TestHelper
        this.conditionValidationService = TestHelper.createConditionValidationService();

        // Create tenants using TestHelper
        TestHelper.setupCommonTestData(tenantService);

        securityService = TestHelper.createSecurityService();
        executionContextManager = TestHelper.createExecutionContextManager(securityService);

        // Set up condition evaluator dispatcher
        ConditionEvaluatorDispatcher conditionEvaluatorDispatcher = TestConditionEvaluators.createDispatcher();

        // Set up bundle context using TestHelper
        bundleContext = TestHelper.createMockBundleContext();

        multiTypeCacheService = new MultiTypeCacheServiceImpl();

        persistenceService = new InMemoryPersistenceServiceImpl(executionContextManager, conditionEvaluatorDispatcher);

        // Create scheduler service using TestHelper
        schedulerService = TestHelper.createSchedulerService(persistenceService, executionContextManager, bundleContext);

        definitionsService = TestHelper.createDefinitionService(persistenceService, bundleContext, schedulerService, multiTypeCacheService, executionContextManager, tenantService, conditionValidationService);
        TestConditionEvaluators.getConditionTypes().forEach((key, value) -> definitionsService.setConditionType(value));

        eventService = TestHelper.createEventService(persistenceService, bundleContext, definitionsService, tenantService, tracerService);

        rulesService = new RulesServiceImpl();

        // Set up action executor dispatcher
        actionExecutorDispatcher = new TestActionExecutorDispatcher(definitionsService, persistenceService);
        actionExecutorDispatcher.setDefaultReturnValue(EventService.PROFILE_UPDATED);

        // Set up tracing
        TestRequestTracer tracer = new TestRequestTracer(true);
        actionExecutorDispatcher.setTracer(tracer);

        rulesService.setBundleContext(bundleContext);
        rulesService.setPersistenceService(persistenceService);
        rulesService.setDefinitionsService(definitionsService);
        rulesService.setEventService(eventService);
        rulesService.setActionExecutorDispatcher(actionExecutorDispatcher);
        rulesService.setTenantService(tenantService);
        rulesService.setSchedulerService(schedulerService);
        rulesService.setContextManager(executionContextManager);
        rulesService.setConditionValidationService(conditionValidationService);
        rulesService.setTracerService(tracerService);

        // Set up condition types
        setupActionTypes();

        // Initialize rule caches
        rulesService.postConstruct();
    }

    private void setupActionTypes() {
        // Create and register test action type using TestHelper
        ActionType testActionType = TestHelper.createActionType("test", "test");
        definitionsService.setActionType(testActionType);
    }

    private Event createTestEvent() {
        Event event = new Event();
        event.setEventType("test");
        String currentTenant = executionContextManager.getCurrentContext().getTenantId();
        Profile profile = new Profile(currentTenant);
        profile.setProperty("testProperty", "testValue"); // Add test property for profile condition
        event.setProfile(profile);
        event.setProfileId(profile.getItemId());
        Session session = new Session();
        session.setItemId(currentTenant);
        session.setProfile(profile);
        session.setTenantId(currentTenant);
        session.setProperty("testProperty", "testValue"); // Add test property for session condition
        event.setSession(session);
        event.setSessionId(currentTenant);
        event.setTenantId(currentTenant);
        event.setAttributes(new HashMap<>());
        event.setActionPostExecutors(new ArrayList<>());
        Item target = new CustomItem();
        target.setItemId("targetItemId");
        event.setTarget(target);
        return event;
    }

    @Test
    public void testGetMatchingRules_NoRules() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            Event event = createTestEvent();
            Set<Rule> matchedRules = rulesService.getMatchingRules(event);
            assertTrue("Should return empty set when no rules match", matchedRules.isEmpty());
            return null;
        });
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
        executionContextManager.executeAsSystem(() -> {
            Rule systemRule = createTestRule();
            systemRule.setItemId("system-rule");
            rulesService.setRule(systemRule);
            return null;
        });

        // Create a rule in tenant1
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            Rule tenantRule = createTestRule();
            tenantRule.setItemId("tenant-rule");
            rulesService.setRule(tenantRule);

            // Test that tenant1 can see both rules
            Set<Rule> allRules = new HashSet<>(rulesService.getAllRules());
            assertEquals("Should see both system and tenant rules", 2, allRules.size());
            assertTrue("Should contain system rule", allRules.stream().anyMatch(r -> r.getItemId().equals("system-rule")));
            assertTrue("Should contain tenant rule", allRules.stream().anyMatch(r -> r.getItemId().equals("tenant-rule")));
            return null;
        });


        // Test that tenant2 can only see system rule
        executionContextManager.executeAsTenant(TENANT_2, () -> {
            Set<Rule> tenant2Rules = new HashSet<>(rulesService.getAllRules());
            assertEquals("Should only see system rule", 1, tenant2Rules.size());
            assertTrue("Should contain system rule", tenant2Rules.stream().anyMatch(r -> r.getItemId().equals("system-rule")));
            return null;
        });
    }

    @Test
    public void testRuleStatisticsAreTenantSpecific() {
        // Setup rules in different tenants
        final Rule[] tenant1Rule = new Rule[1];
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            tenant1Rule[0] = createTestRule();
            tenant1Rule[0].setItemId("tenant1-rule");
            tenant1Rule[0].setTenantId(TENANT_1);
            rulesService.setRule(tenant1Rule[0]);
            return null;
        });

        final Rule[] tenant2Rule = new Rule[1];
        executionContextManager.executeAsTenant(TENANT_2, () -> {
            tenant2Rule[0] = createTestRule();
            tenant2Rule[0].setItemId("tenant2-rule");
            tenant2Rule[0].setTenantId(TENANT_2);
            rulesService.setRule(tenant2Rule[0]);
            return null;
        });

        // Test statistics for tenant1
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            Event event1 = createTestEvent();
            rulesService.getMatchingRules(event1);
            RuleStatistics stats1 = rulesService.getRuleStatistics(tenant1Rule[0].getItemId());
            assertNotNull("Tenant1 rule statistics should exist", stats1);
            return null;
        });

        // Test statistics for tenant2
        executionContextManager.executeAsTenant(TENANT_2, () -> {
            Event event2 = createTestEvent();
            rulesService.getMatchingRules(event2);
            RuleStatistics stats2 = rulesService.getRuleStatistics(tenant2Rule[0].getItemId());
            assertNotNull("Tenant2 rule statistics should exist", stats2);

            // Verify tenant isolation
            assertNull("Tenant2 should not see tenant1's rule statistics",
                rulesService.getRuleStatistics(tenant1Rule[0].getItemId()));
            return null;
        });
    }

    private Rule createTestRule() {
        Rule rule = new Rule();
        rule.setItemId("test-rule");
        rule.setTenantId(executionContextManager.getCurrentContext().getTenantId());

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
        executionContextManager.executeAsTenant(TENANT_1, () -> {
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
            return null;
        });
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
        executionContextManager.executeAsTenant(TENANT_1, () -> {
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
            return null;
        });
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
        executionContextManager.executeAsTenant(TENANT_1, () -> {
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
            return null;
        });
    }

    @Test
    public void testGetRuleStatistics() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            Rule rule = createTestRule();
            rule.setTenantId(TENANT_1);
            rulesService.setRule(rule);

            // Execute
            rulesService.resetAllRuleStatistics();
            // Execute test
            rulesService.refreshRules();

            // Verify
            RuleStatistics stats = rulesService.getRuleStatistics(rule.getItemId());
            assertNull("Statistics should be reset", stats);
            return null;
        });
    }

    @Test
    public void testEventRaisedOnlyOnce() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create and send initial event
            Event event = createTestEvent();
            eventService.send(event);

            // Create rule that should only fire once
            Rule rule = createTestRule();
            rule.setRaiseEventOnlyOnce(true);
            rulesService.setRule(rule);

            // Create new event with same ID to test if rule matches
            Event sameEvent = createTestEvent();
            sameEvent.setItemId(event.getItemId());
            Set<Rule> matchedRules = rulesService.getMatchingRules(sameEvent);

            // Verify
            assertTrue("Should not match rule when event already raised", matchedRules.isEmpty());
            return null;
        });
    }

    @Test
    public void testEventRaisedOnlyOnceForProfile() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create and send initial event
            Event event = createTestEvent();
            eventService.send(event);

            // Create rule that should only fire once per profile
            Rule rule = createTestRule();
            rule.setRaiseEventOnlyOnceForProfile(true);
            rulesService.setRule(rule);

            // Create new event with same profile to test if rule matches
            Event sameProfileEvent = createTestEvent();
            sameProfileEvent.setProfile(event.getProfile());
            sameProfileEvent.setTarget(event.getTarget());
            Set<Rule> matchedRules = rulesService.getMatchingRules(sameProfileEvent);

            // Verify
            assertTrue("Should not match rule when event already raised for profile", matchedRules.isEmpty());
            return null;
        });
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
        executionContextManager.executeAsSystem(() -> {
            Rule systemRule = createTestRule();
            systemRule.setItemId("system-rule");
            rulesService.setRule(systemRule);
            return null;
        });

        executionContextManager.executeAsTenant(TENANT_1, () -> {
            Rule tenant1Rule = createTestRule();
            tenant1Rule.setItemId("tenant1-rule");
            rulesService.setRule(tenant1Rule);
            return null;
        });

        executionContextManager.executeAsTenant(TENANT_2, () -> {
            Rule tenant2Rule = createTestRule();
            tenant2Rule.setItemId("tenant2-rule");
            rulesService.setRule(tenant2Rule);
            return null;
        });

        // Execute refresh
        rulesService.refreshRules();

        // Verify rules are loaded for all tenants
        executionContextManager.executeAsSystem(() -> {
            assertNotNull("System rule should be loaded", rulesService.getRule("system-rule"));
            return null;
        });

        executionContextManager.executeAsTenant(TENANT_1, () -> {
            assertNotNull("Tenant1 rule should be loaded", rulesService.getRule("tenant1-rule"));
            return null;
        });

        executionContextManager.executeAsTenant(TENANT_2, () -> {
            assertNotNull("Tenant2 rule should be loaded", rulesService.getRule("tenant2-rule"));
            return null;
        });
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetRuleWithInvalidCondition() {
        // Create a rule with invalid condition
        Rule rule = new Rule();
        rule.setMetadata(new Metadata());
        rule.getMetadata().setId("testRule");
        rule.getMetadata().setEnabled(true);

        Condition condition = new Condition();
        rule.setCondition(condition);

        // Should throw IllegalArgumentException with detailed message
        rulesService.setRule(rule);
    }

    @Test
    public void testSetRuleWithValidCondition() {
        // Create a rule with valid condition
        Rule rule = new Rule();
        rule.setMetadata(new Metadata());
        rule.getMetadata().setId("testRule");
        rule.getMetadata().setEnabled(true);

        Condition condition = new Condition();
        condition.setConditionType(definitionsService.getConditionType("profilePropertyCondition"));
        condition.setParameter("propertyName", "testProperty");
        condition.setParameter("comparisonOperator", "exists");
        rule.setCondition(condition);

        // Should not throw any exceptions
        rulesService.setRule(rule);

    }

    @Test
    public void testSetRuleWithNestedConditions() {
        // Create a rule with nested conditions
        Rule rule = new Rule();
        rule.setMetadata(new Metadata());
        rule.getMetadata().setId("testRule");
        rule.getMetadata().setEnabled(true);

        // Create parent condition
        Condition parentCondition = new Condition();
        parentCondition.setConditionTypeId("booleanCondition");
        parentCondition.setParameter("operator", "and");

        // Create child condition
        Condition childCondition = new Condition();
        childCondition.setConditionTypeId("propertyCondition");

        // Set up nested structure
        List<Condition> subConditions = new ArrayList<>();
        subConditions.add(childCondition);
        parentCondition.setParameter("subConditions", subConditions);

        rule.setCondition(parentCondition);

        // Should not throw any exceptions
        rulesService.setRule(rule);

    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetRuleWithInvalidNestedCondition() {
        // Create a rule with nested conditions where child is invalid
        Rule rule = new Rule();
        rule.setMetadata(new Metadata());
        rule.getMetadata().setId("testRule");
        rule.getMetadata().setEnabled(true);

        // Create parent condition
        Condition parentCondition = new Condition();
        parentCondition.setConditionTypeId("booleanCondition");

        // Create invalid child condition
        Condition childCondition = new Condition();
        childCondition.setConditionTypeId("propertyCondition");

        // Set up nested structure
        List<Condition> subConditions = new ArrayList<>();
        subConditions.add(childCondition);
        parentCondition.setParameter("subConditions", subConditions);

        rule.setCondition(parentCondition);

        // Should throw IllegalArgumentException with detailed message
        rulesService.setRule(rule);
    }

    @Test
    public void testRuleExecutionWithValidCondition() {
        // Create a rule with valid condition
        Rule rule = new Rule();
        rule.setMetadata(new Metadata());
        rule.getMetadata().setId("testRule");
        rule.getMetadata().setEnabled(true);
        rule.setScope("systemscope");

        Condition condition = new Condition();
        rule.setCondition(condition);

        // Create test event
        Event event = new Event();
        event.setEventType("testEvent");
        event.setScope("systemscope");
        event.setProfile(new Profile("testProfile"));

        // Add test action
        Action action = new Action();
        ActionType actionType = new ActionType();
        actionType.setActionExecutor("test");
        action.setActionType(actionType);
        rule.setActions(List.of(action));

        // Execute rule
        rulesService.onEvent(event);

    }
}
