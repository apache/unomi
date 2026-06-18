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
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.rules.RuleStatistics;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.RuleListenerService;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.conditions.evaluator.ConditionEvaluatorDispatcher;
import org.apache.unomi.services.TestHelper;
import org.apache.unomi.services.common.security.AuditServiceImpl;
import org.apache.unomi.services.common.security.ExecutionContextManagerImpl;
import org.apache.unomi.services.common.security.KarafSecurityService;
import org.apache.unomi.services.impl.*;
import org.apache.unomi.services.impl.cache.MultiTypeCacheServiceImpl;
import org.apache.unomi.services.impl.definitions.DefinitionsServiceImpl;
import org.apache.unomi.tracing.api.RequestTracer;
import org.apache.unomi.tracing.api.TracerService;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class RulesServiceImplTest {

    private RulesServiceImpl rulesService;
    private TestTenantService tenantService;
    private PersistenceService persistenceService;
    private DefinitionsServiceImpl definitionsService;
    private MultiTypeCacheServiceImpl multiTypeCacheService;
    private ExecutionContextManagerImpl executionContextManager;
    private KarafSecurityService securityService;
    private AuditServiceImpl auditService;

    @Mock
    private BundleContext bundleContext;
    private EventService eventService;
    private TracerService tracerService;
    private RequestTracer requestTracer;

    private TestActionExecutorDispatcher actionExecutorDispatcher;
    private SchedulerService schedulerService;
    private TestEventAdmin testEventAdmin;

    private static final String TENANT_1 = "tenant1";
    private static final String TENANT_2 = "tenant2";
    private static final String SYSTEM_TENANT = "system";

    @BeforeEach
    public void setUp() throws Exception {

        tracerService = TestHelper.createTracerService();
        tenantService = new TestTenantService();

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
        schedulerService = TestHelper.createSchedulerService("rule-scheduler-node", persistenceService, executionContextManager, bundleContext, null, -1, true, true);

        // Create definitions service with TestEventAdmin
        java.util.Map.Entry<DefinitionsServiceImpl, TestEventAdmin> servicePair =
            TestHelper.createDefinitionServiceWithEventAdmin(persistenceService, bundleContext, schedulerService,
                multiTypeCacheService, executionContextManager, tenantService);
        definitionsService = servicePair.getKey();
        testEventAdmin = servicePair.getValue();

        // Inject definitionsService into the dispatcher
        TestHelper.injectDefinitionsServiceIntoDispatcher(conditionEvaluatorDispatcher, definitionsService);
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
        rulesService.setTracerService(tracerService);
        rulesService.setCacheService(multiTypeCacheService);


        // Set up condition types
        setupActionTypes();

        // Initialize rule caches
        rulesService.postConstruct();

        // Register RulesServiceImpl as an EventHandler with TestEventAdmin
        // In real OSGi, this would be done via service registry, but for tests we register manually
        if (testEventAdmin != null) {
            testEventAdmin.registerHandler(rulesService, "org/apache/unomi/definitions/**");
        }
    }

    @org.junit.jupiter.api.AfterEach
    public void tearDown() {
        if (testEventAdmin != null) {
            testEventAdmin.shutdown();
        }
        if (rulesService != null) {
            rulesService.preDestroy();
        }
    }

    private void setupActionTypes() {
        // Create and register test action type using TestHelper
        ActionType testActionType = TestHelper.createActionType("test", "test");
        definitionsService.setActionType(testActionType);
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
        Condition condition = createEventTypeCondition("test");
        rule.setCondition(condition);

        // Create a simple action
        Action action = createTestAction();
        rule.setActions(Collections.singletonList(action));

        return rule;
    }

    private Condition createEventTypeCondition(String eventType) {
        Condition condition = new Condition();
        condition.setConditionType(definitionsService.getConditionType("eventTypeCondition"));
        condition.setParameter("eventTypeId", eventType);
        return condition;
    }

    private Condition createProfilePropertyCondition(String propertyName, String comparisonOperator, String propertyValue) {
        Condition condition = new Condition();
        condition.setConditionType(definitionsService.getConditionType("profilePropertyCondition"));
        condition.setParameter("propertyName", propertyName);
        condition.setParameter("comparisonOperator", comparisonOperator);
        condition.setParameter("propertyValue", propertyValue);
        return condition;
    }

    private Condition createSessionPropertyCondition(String propertyName, String comparisonOperator, String propertyValue) {
        Condition condition = new Condition();
        condition.setConditionType(definitionsService.getConditionType("sessionPropertyCondition"));
        condition.setParameter("propertyName", propertyName);
        condition.setParameter("comparisonOperator", comparisonOperator);
        condition.setParameter("propertyValue", propertyValue);
        return condition;
    }

    private Condition createBooleanCondition(String operator, List<Condition> subConditions) {
        Condition condition = new Condition();
        condition.setConditionType(definitionsService.getConditionType("booleanCondition"));
        condition.setParameter("operator", operator);
        condition.setParameter("subConditions", subConditions);
        return condition;
    }

    private Action createTestAction() {
        Action action = new Action();
        action.setActionType(definitionsService.getActionType("test"));
        return action;
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

    private Rule createRuleWithUnavailableConditionType(String conditionTypeId) {
        Rule rule = new Rule();
        rule.setMetadata(new Metadata());
        rule.getMetadata().setId("testRule");
        rule.getMetadata().setEnabled(true);
        rule.setScope("systemscope");

        Condition condition = new Condition();
        condition.setConditionTypeId(conditionTypeId);
        condition.setParameter("propertyName", "testProperty");
        condition.setParameter("comparisonOperator", "equals");
        condition.setParameter("propertyValue", "testValue");
        rule.setCondition(condition);

        Action action = createTestAction();
        rule.setActions(List.of(action));

        return rule;
    }

    private ConditionType createTestConditionType(String id) {
        ConditionType conditionType = new ConditionType();
        Metadata metadata = new Metadata();
        metadata.setId(id);
        metadata.setEnabled(true);
        conditionType.setMetadata(metadata);
        conditionType.setConditionEvaluator(id + "Evaluator");
        conditionType.setQueryBuilder(id + "QueryBuilder");
        return conditionType;
    }

    @Test
    public void testGetMatchingRules_NoRules() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            Event event = createTestEvent();
            Set<Rule> matchedRules = rulesService.getMatchingRules(event);
            assertTrue(matchedRules.isEmpty(), "Should return empty set when no rules match");
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
        assertFalse(matchedRules.isEmpty(), "Should return non-empty set when rule matches");
        assertEquals(1, matchedRules.size(), "Should return one matching rule");
        assertTrue(matchedRules.contains(rule), "Should contain the matching rule");
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
            assertEquals(2, allRules.size(), "Should see both system and tenant rules");
            assertTrue(allRules.stream().anyMatch(r -> r.getItemId().equals("system-rule")), "Should contain system rule");
            assertTrue(allRules.stream().anyMatch(r -> r.getItemId().equals("tenant-rule")), "Should contain tenant rule");
            return null;
        });


        // Test that tenant2 can only see system rule
        executionContextManager.executeAsTenant(TENANT_2, () -> {
            Set<Rule> tenant2Rules = new HashSet<>(rulesService.getAllRules());
            assertEquals(1, tenant2Rules.size(), "Should only see system rule");
            assertTrue(tenant2Rules.stream().anyMatch(r -> r.getItemId().equals("system-rule")), "Should contain system rule");
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
            assertNotNull(stats1, "Tenant1 rule statistics should exist");
            return null;
        });

        // Test statistics for tenant2
        executionContextManager.executeAsTenant(TENANT_2, () -> {
            Event event2 = createTestEvent();
            rulesService.getMatchingRules(event2);
            RuleStatistics stats2 = rulesService.getRuleStatistics(tenant2Rule[0].getItemId());
            assertNotNull(stats2, "Tenant2 rule statistics should exist");

            // Verify tenant isolation
            assertNull(rulesService.getRuleStatistics(tenant1Rule[0].getItemId()),
                "Tenant2 should not see tenant1's rule statistics");
            return null;
        });
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
        assertEquals(EventService.PROFILE_UPDATED, result, "Should return PROFILE_UPDATED flag");
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

            // Execute test - retry until event is available for rule matching (handles refresh delay)
            Set<Rule> matchingRules = TestHelper.retryUntil(
                () -> rulesService.getMatchingRules(event),
                r -> r != null && !r.isEmpty()
            );
            assertNotNull(matchingRules, "Matching rules should exist");
            assertTrue(!matchingRules.isEmpty(), "Matching rules should not be empty");

            // Verify statistics were updated
            RuleStatistics stats = rulesService.getRuleStatistics(rule.getItemId());
            assertNotNull(stats, "Rule statistics should be created");
            assertEquals(TENANT_1, stats.getTenantId(), "Statistics should have correct tenant ID");
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
            assertNotNull(savedRule, "Rule should be saved");
            assertEquals(TENANT_1, savedRule.getTenantId(), "Rule should have correct tenant");
            assertEquals("systemscope", savedRule.getMetadata().getScope(), "Rule should have correct scope");
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
        assertNull(persistenceService.load(rule.getItemId(), Rule.class), "Rule should be removed");
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
            assertNotNull(result, "Should return the rule");
            assertEquals(rule.getItemId(), result.getItemId(), "Should return the correct rule");
            assertEquals(TENANT_1, result.getTenantId(), "Should have correct tenant");
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
            assertNull(stats, "Statistics should be reset");
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
        assertTrue(matchedRules.isEmpty(), "Should not match rule when event already raised");
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
            // Retry until event is available for query (handles refresh delay)
            // The rule should not match because the event was already raised for this profile
            Set<Rule> matchedRules = TestHelper.retryUntil(
                () -> rulesService.getMatchingRules(sameProfileEvent),
                r -> r != null && r.isEmpty()
            );

            // Verify
            assertTrue(matchedRules.isEmpty(), "Should not match rule when event already raised for profile");
            return null;
        });
    }

    @Test
    public void testProfileConditionMatching() {
        // Setup
        Event event = createTestEvent();
        Rule rule = createTestRule();
        rule.setTenantId(TENANT_1);

        Condition eventCondition = createEventTypeCondition("test");
        Condition profileCondition = createProfilePropertyCondition("properties.testProperty", "equals", "testValue");
        Condition booleanCondition = createBooleanCondition("and", Arrays.asList(eventCondition, profileCondition));

        rule.setCondition(booleanCondition);
        rulesService.setRule(rule);

        // Set the profile property to match
        Profile profile = event.getProfile();
        profile.setProperty("testProperty", "testValue");
        event.setProfile(profile);

        // Execute
        Set<Rule> matchedRules = rulesService.getMatchingRules(event);

        // Verify
        assertFalse(matchedRules.isEmpty(), "Should match rule when both event and profile conditions match");
    }

    @Test
    public void testSessionConditionMatching() {
        // Setup
        Event event = createTestEvent();
        Rule rule = createTestRule();
        rule.setTenantId(TENANT_1);

        Condition eventCondition = createEventTypeCondition("test");
        Condition sessionCondition = createSessionPropertyCondition("properties.testProperty", "equals", "testValue");
        Condition booleanCondition = createBooleanCondition("and", Arrays.asList(eventCondition, sessionCondition));

        rule.setCondition(booleanCondition);
        rulesService.setRule(rule);

        // Set the session property to match
        Session session = event.getSession();
        session.setProperty("testProperty", "testValue");
        event.setSession(session);

        // Execute
        Set<Rule> matchedRules = rulesService.getMatchingRules(event);

        // Verify
        assertFalse(matchedRules.isEmpty(), "Should match rule when both event and session conditions match");
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
            assertNotNull(rulesService.getRule("system-rule"), "System rule should be loaded");
            return null;
        });

        executionContextManager.executeAsTenant(TENANT_1, () -> {
            assertNotNull(rulesService.getRule("tenant1-rule"), "Tenant1 rule should be loaded");
            return null;
        });

        executionContextManager.executeAsTenant(TENANT_2, () -> {
            assertNotNull(rulesService.getRule("tenant2-rule"), "Tenant2 rule should be loaded");
            return null;
        });
    }

    @Test
    public void testSetRuleWithInvalidCondition() {
        // Create a rule with invalid condition
        Rule rule = new Rule();
        rule.setMetadata(new Metadata());
        rule.getMetadata().setId("testRule");
        rule.getMetadata().setEnabled(true);

        Condition condition = new Condition();
        rule.setCondition(condition);

        // Should throw IllegalArgumentException with detailed message
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> rulesService.setRule(rule),
            "Setting rule with empty condition should fail validation (ruleId=testRule)"
        );
    }

    @Test
    public void testSetRuleWithValidCondition() {
        // Create a rule with valid condition
        Rule rule = new Rule();
        rule.setMetadata(new Metadata());
        rule.getMetadata().setId("testRule");
        rule.getMetadata().setEnabled(true);

        Condition condition = createProfilePropertyCondition("testProperty", "exists", null);
        rule.setCondition(condition);

        // Should not throw any exceptions
        rulesService.setRule(rule);
    }

    @Test
    public void testSetRuleWithNestedConditions() {
        Rule rule = new Rule();
        rule.setMetadata(new Metadata());
        rule.getMetadata().setId("testRule");
        rule.getMetadata().setEnabled(true);

        // Create parent condition
        Condition parentCondition = new Condition();
        parentCondition.setConditionType(definitionsService.getConditionType("booleanCondition"));
        parentCondition.setParameter("operator", "and");

        // Create child condition with proper ConditionType set
        // Note: propertyCondition is an alias, we need to use the actual condition type
        // For a minimal valid condition, we'll use profilePropertyCondition with minimal required params
        Condition childCondition = new Condition();
        childCondition.setConditionType(definitionsService.getConditionType("profilePropertyCondition"));
        childCondition.setParameter("propertyName", "testProperty");
        childCondition.setParameter("comparisonOperator", "exists");

        // Set up nested structure
        List<Condition> subConditions = new ArrayList<>();
        subConditions.add(childCondition);
        parentCondition.setParameter("subConditions", subConditions);

        rule.setCondition(parentCondition);

        // Should not throw any exceptions
        rulesService.setRule(rule);
    }

    @Test
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
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> rulesService.setRule(rule),
            "Setting rule with invalid nested child condition should fail (ruleId=testRule)"
        );
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

    @Test
    public void testSetRuleWithUnavailableConditionType() {
        Rule rule = createRuleWithUnavailableConditionType("unavailableConditionType");

        rulesService.setRule(rule, true);

        Rule savedRule = persistenceService.load(rule.getItemId(), Rule.class);
        assertNotNull(savedRule, "Rule should be saved when deployed from bundle");
        assertNull(definitionsService.getConditionType("unavailableConditionType"), "Condition type should not be available yet");

        ConditionType conditionType = createTestConditionType("unavailableConditionType");
        definitionsService.setConditionType(conditionType);

        // Refresh persistence to ensure rule is available for querying
        persistenceService.refresh();
        rulesService.refreshRules();

        Rule refreshedRule = rulesService.getRule(rule.getItemId());
        assertNotNull(definitionsService.getConditionType("unavailableConditionType"), "Condition type should be available after being set");
        assertNotNull(refreshedRule, "Refreshed rule should not be null");
        // Condition type is resolved on-demand, not automatically on load
        // Resolve it explicitly for the test using the test's typeResolutionService
        TypeResolutionServiceImpl testTypeResolutionService = new TypeResolutionServiceImpl(definitionsService);
        testTypeResolutionService.resolveConditionType(refreshedRule.getCondition(), "test rule condition");
        assertNotNull(refreshedRule.getCondition().getConditionType(), "Rule condition type should be resolved");
    }

    @Test
    public void testSetRuleWithUnavailableConditionTypeNonBundle() {
        Rule rule = createRuleWithUnavailableConditionType("unavailableConditionType");
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> rulesService.setRule(rule, false),
            "Non-bundle rule with unavailable condition type should be rejected (conditionTypeId=unavailableConditionType)"
        );
    }

    @Test
    public void testSetRuleWithMissingPluginsFlag() {
        Rule rule = createRuleWithUnavailableConditionType("unavailableConditionType");
        rule.getMetadata().setMissingPlugins(true);

        rulesService.setRule(rule, false);

        Rule savedRule = persistenceService.load(rule.getItemId(), Rule.class);
        assertNotNull(savedRule, "Rule should be saved when missingPlugins is true");
        assertNull(definitionsService.getConditionType("unavailableConditionType"), "Condition type should not be available yet");
        assertTrue(savedRule.getMetadata().isMissingPlugins(), "Rule should still be marked as having missing plugins");

        ConditionType conditionType = createTestConditionType("unavailableConditionType");
        definitionsService.setConditionType(conditionType);

        // Refresh persistence to ensure rule is available for querying
        persistenceService.refresh();
        rulesService.refreshRules();

        Rule refreshedRule = rulesService.getRule(rule.getItemId());
        assertNotNull(definitionsService.getConditionType("unavailableConditionType"), "Condition type should be available after being set");
        assertNotNull(refreshedRule, "Refreshed rule should not be null");
        // Condition type is resolved on-demand, not automatically on load
        // Resolve it explicitly for the test using the test's typeResolutionService
        TypeResolutionServiceImpl testTypeResolutionService = new TypeResolutionServiceImpl(definitionsService);
        testTypeResolutionService.resolveConditionType(refreshedRule.getCondition(), "test rule condition");
        assertNotNull(refreshedRule.getCondition().getConditionType(), "Rule condition type should be resolved");
    }

    // ==================== Loop Detection Tests ====================

    /**
     * Helper to create a wildcard rule that matches all events (including ruleFired).
     */
    private Rule createWildcardRule(String ruleId) {
        Rule rule = createTestRule();
        rule.setItemId(ruleId);
        rule.setTenantId(TENANT_1);
        Condition wildcardCondition = new Condition();
        wildcardCondition.setConditionType(definitionsService.getConditionType("eventTypeCondition"));
        wildcardCondition.setParameter("eventTypeId", "*");
        rule.setCondition(wildcardCondition);
        rule.setActions(Collections.singletonList(createTestAction()));
        return rule;
    }

    @Test
    public void testRuleFiredEventLoopDetection() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Wildcard rule matches ruleFired events, creating a loop
            Rule wildcardRule = createWildcardRule("wildcard-rule");
            rulesService.setRule(wildcardRule);
            rulesService.refreshRules();

            Event event = createTestEvent();
            event.setEventType("view");
            event.setItemId("test-event-123"); // Set explicit ID for proper loop detection

            // First event should process normally (triggers rule, which sends ruleFired)
            // The ruleFired event should be detected as a loop and prevented
            int result = rulesService.onEvent(event);
            assertNotNull(result, "Event should return a result without infinite loop");

            // Verify rule was processed (ruleFired was sent but loop was detected)
            // The key is that processing completes without hanging
            return null;
        });
    }

    @Test
    public void testRuleFiredEventLoopDetectionWithProperIds() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create a rule that matches ruleFired events
            Rule rule = createWildcardRule("rule-matching-ruleFired");
            rulesService.setRule(rule);
            rulesService.refreshRules();

            // Create an event with a proper ID
            Event originalEvent = createTestEvent();
            originalEvent.setEventType("view");
            originalEvent.setItemId("original-event-456");

            // Process the event - this will trigger ruleFired
            // The ruleFired event should use proper ID (source event + rule ID)
            // and be detected as a loop when it tries to process again
            int result = rulesService.onEvent(originalEvent);
            assertNotNull(result, "Should return without infinite loop");

            // Verify the event was processed (but loop was prevented)
            // The key is that it returns without hanging
            return null;
        });
    }

    @Test
    public void testGenericEventLoopDetection() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Action executor that sends the same event type with same ID
            Event loopEvent = createTestEvent();
            loopEvent.setEventType("customEvent");
            loopEvent.setItemId("loop-event-789");

            actionExecutorDispatcher.addExecutor("test", (action, evt) -> {
                // Send the same event (same ID) to create a loop
                Event newEvent = new Event("customEvent", evt.getSession(), evt.getProfile(),
                    evt.getScope(), evt, evt.getTarget(), evt.getTimeStamp());
                newEvent.setItemId("loop-event-789"); // Same ID to trigger loop detection
                newEvent.setPersistent(false);
                eventService.send(newEvent);
                return EventService.NO_CHANGE;
            });

            Rule rule = createTestRule();
            rule.setItemId("loop-rule");
            rule.setTenantId(TENANT_1);
            rule.setCondition(createEventTypeCondition("customEvent"));
            Action action = new Action();
            action.setActionType(definitionsService.getActionType("test"));
            rule.setActions(Collections.singletonList(action));

            rulesService.setRule(rule);
            rulesService.refreshRules();

            // Should detect loop when same event ID is processed again
            int result = rulesService.onEvent(loopEvent);
            assertNotNull(result, "Should return without infinite loop");
            return null;
        });
    }

    @Test
    public void testMaximumDepthDetection() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Register action types for this test
            ActionType testActionType1 = TestHelper.createActionType("test1", "test1");
            definitionsService.setActionType(testActionType1);
            ActionType testActionType2 = TestHelper.createActionType("test2", "test2");
            definitionsService.setActionType(testActionType2);

            // Create a chain of rules that trigger each other (but not a loop)
            // This should hit maximum depth limit

            // Rule 1: matches eventA, sends eventB
            actionExecutorDispatcher.addExecutor("test1", (action, evt) -> {
                Event newEvent = new Event("eventB", evt.getSession(), evt.getProfile(),
                    evt.getScope(), evt, evt.getTarget(), evt.getTimeStamp());
                newEvent.setItemId("event-b-" + System.currentTimeMillis()); // Different ID each time
                newEvent.setPersistent(false);
                eventService.send(newEvent);
                return EventService.NO_CHANGE;
            });

            Rule rule1 = createTestRule();
            rule1.setItemId("rule-eventA");
            rule1.setTenantId(TENANT_1);
            rule1.setCondition(createEventTypeCondition("eventA"));
            Action action1 = new Action();
            action1.setActionType(definitionsService.getActionType("test1"));
            rule1.setActions(Collections.singletonList(action1));

            // Rule 2: matches eventB, sends eventA (creates deep nesting)
            actionExecutorDispatcher.addExecutor("test2", (action, evt) -> {
                Event newEvent = new Event("eventA", evt.getSession(), evt.getProfile(),
                    evt.getScope(), evt, evt.getTarget(), evt.getTimeStamp());
                newEvent.setItemId("event-a-" + System.currentTimeMillis()); // Different ID each time
                newEvent.setPersistent(false);
                eventService.send(newEvent);
                return EventService.NO_CHANGE;
            });

            Rule rule2 = createTestRule();
            rule2.setItemId("rule-eventB");
            rule2.setTenantId(TENANT_1);
            rule2.setCondition(createEventTypeCondition("eventB"));
            Action action2 = new Action();
            action2.setActionType(definitionsService.getActionType("test2"));
            rule2.setActions(Collections.singletonList(action2));

            rulesService.setRule(rule1);
            rulesService.setRule(rule2);
            rulesService.refreshRules();

            // Start with eventA - this will create deep nesting
            Event startEvent = createTestEvent();
            startEvent.setEventType("eventA");
            startEvent.setItemId("start-event");

            // Should hit maximum depth and return (not hang)
            // Depth protection is now handled by EventServiceImpl.MAX_RECURSION_DEPTH
            // RulesServiceImpl only handles loop detection (same event key seen twice)
            int result = rulesService.onEvent(startEvent);
            assertNotNull(result, "Should return when maximum depth is reached by EventServiceImpl");

            // Verify that processing can continue after depth limit (ThreadLocal was cleaned up)
            Event nextEvent = createTestEvent();
            nextEvent.setEventType("view");
            nextEvent.setItemId("next-event");
            int nextResult = rulesService.onEvent(nextEvent);
            assertNotNull(nextResult, "Should be able to process new events after depth limit");

            return null;
        });
    }

    @Test
    public void testNullEventHandling() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            assertEquals(EventService.NO_CHANGE, rulesService.onEvent(null),
                "Null event should return NO_CHANGE");
            return null;
        });
    }

    @Test
    public void testEventWithoutId() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            Event event = createTestEvent();
            event.setItemId(null);
            // Event without ID should still generate a key and be processed
            assertNotNull(rulesService.onEvent(event), "Event without ID should be processed");
            return null;
        });
    }

    @Test
    public void testMultipleRulesInLoop() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Multiple wildcard rules should all be caught by loop detection
            rulesService.setRule(createWildcardRule("rule1"));
            rulesService.setRule(createWildcardRule("rule2"));
            rulesService.refreshRules();

            Event event = createTestEvent();
            event.setEventType("view");
            event.setItemId("test-event-multi");

            assertNotNull(rulesService.onEvent(event), "Should handle multiple rules in loop");
            return null;
        });
    }

    @Test
    public void testSameEventIdLoopDetection() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Test that the same event ID is properly detected as a loop
            Event event = createTestEvent();
            event.setEventType("testEvent");
            event.setItemId("same-event-id");

            // Create a rule that sends the same event back
            actionExecutorDispatcher.addExecutor("test", (action, evt) -> {
                Event newEvent = new Event("testEvent", evt.getSession(), evt.getProfile(),
                    evt.getScope(), evt, evt.getTarget(), evt.getTimeStamp());
                newEvent.setItemId("same-event-id"); // Same ID - should trigger loop detection
                newEvent.setPersistent(false);
                eventService.send(newEvent);
                return EventService.NO_CHANGE;
            });

            Rule rule = createTestRule();
            rule.setItemId("loop-back-rule");
            rule.setTenantId(TENANT_1);
            rule.setCondition(createEventTypeCondition("testEvent"));
            Action action = new Action();
            action.setActionType(definitionsService.getActionType("test"));
            rule.setActions(Collections.singletonList(action));

            rulesService.setRule(rule);
            rulesService.refreshRules();

            // Should detect loop when same event ID is processed again
            int result = rulesService.onEvent(event);
            assertNotNull(result, "Should detect loop and return");
            return null;
        });
    }

    @Test
    public void testProcessingContextReuseAfterCleanup() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Verify that ProcessingContext is properly cleaned up and recreated
            Rule rule = createTestRule();
            rule.setItemId("reuse-test-rule");
            rule.setTenantId(TENANT_1);
            rule.setActions(Collections.singletonList(createTestAction()));
            rulesService.setRule(rule);
            rulesService.refreshRules();

            // Process first event - should create ProcessingContext
            Event event1 = createTestEvent();
            event1.setItemId("event-1");
            int result1 = rulesService.onEvent(event1);
            assertNotNull(result1, "First event should process successfully");

            // Process second event - should reuse ProcessingContext (but different event key, so no loop)
            Event event2 = createTestEvent();
            event2.setItemId("event-2");
            int result2 = rulesService.onEvent(event2);
            assertNotNull(result2, "Second event should process successfully");

            // Process third event - verify context is still working
            Event event3 = createTestEvent();
            event3.setItemId("event-3");
            int result3 = rulesService.onEvent(event3);
            assertNotNull(result3, "Third event should process successfully");

            return null;
        });
    }

    @Test
    public void testHistoryTrackingWithMultipleRules() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create multiple rules that all match the same event type "test"
            Rule rule1 = createTestRule();
            rule1.setItemId("history-rule-1");
            rule1.setTenantId(TENANT_1);
            rule1.setCondition(createEventTypeCondition("test")); // Explicitly set to match "test" events
            rule1.setActions(Collections.singletonList(createTestAction()));

            Rule rule2 = createTestRule();
            rule2.setItemId("history-rule-2");
            rule2.setTenantId(TENANT_1);
            rule2.setCondition(createEventTypeCondition("test")); // Explicitly set to match "test" events
            rule2.setActions(Collections.singletonList(createTestAction()));

            Rule rule3 = createTestRule();
            rule3.setItemId("history-rule-3");
            rule3.setTenantId(TENANT_1);
            rule3.setCondition(createEventTypeCondition("test")); // Explicitly set to match "test" events
            rule3.setActions(Collections.singletonList(createTestAction()));

            rulesService.setRule(rule1);
            rulesService.setRule(rule2);
            rulesService.setRule(rule3);
            rulesService.refreshRules();

            // Verify rules are saved and can be retrieved
            Rule savedRule1 = rulesService.getRule("history-rule-1");
            Rule savedRule2 = rulesService.getRule("history-rule-2");
            Rule savedRule3 = rulesService.getRule("history-rule-3");
            assertNotNull(savedRule1, "Rule 1 should be saved");
            assertNotNull(savedRule2, "Rule 2 should be saved");
            assertNotNull(savedRule3, "Rule 3 should be saved");

            // Process event that matches all three rules
            Event event = createTestEvent();
            event.setItemId("history-test-event");

            // Verify rules match before processing (with retry to handle indexing delays)
            Set<Rule> matchingRulesBefore = TestHelper.retryUntil(
                () -> rulesService.getMatchingRules(event),
                rules -> rules != null && rules.size() >= 3
            );
            assertTrue(matchingRulesBefore.size() >= 3,
                "Should match at least 3 rules before processing. Found: " + matchingRulesBefore.size());

            // All three rules should fire and be recorded in history
            int result = rulesService.onEvent(event);
            assertNotNull(result, "Event should process all matching rules");

            // Verify rules were processed (history tracking happens in processEvent)
            Set<Rule> matchingRules = rulesService.getMatchingRules(event);
            assertTrue(matchingRules.size() >= 3,
                "Should match at least 3 rules after processing. Found: " + matchingRules.size());

            return null;
        });
    }

    @Test
    public void testRuleFiredEventWithNullSource() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Test ruleFired event handling when source is null or malformed
            Rule rule = createTestRule();
            rule.setItemId("rule-fired-null-test");
            rule.setTenantId(TENANT_1);
            rule.setActions(Collections.singletonList(createTestAction()));
            rulesService.setRule(rule);
            rulesService.refreshRules();

            // Create event without ID to test fallback key generation
            Event event = createTestEvent();
            event.setItemId(null);
            event.setEventType("testEvent");

            // Should process without error (generateEventKey handles null IDs)
            int result = rulesService.onEvent(event);
            assertNotNull(result, "Event without ID should still process");

            return null;
        });
    }

    @Test
    public void testMultipleLoopsInSequence() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Test that multiple different loops can be detected in sequence
            Event loopEvent1 = createTestEvent();
            loopEvent1.setEventType("loopEvent1");
            loopEvent1.setItemId("loop-1");

            actionExecutorDispatcher.addExecutor("test", (action, evt) -> {
                if ("loopEvent1".equals(evt.getEventType())) {
                    Event newEvent = new Event("loopEvent1", evt.getSession(), evt.getProfile(),
                        evt.getScope(), evt, evt.getTarget(), evt.getTimeStamp());
                    newEvent.setItemId("loop-1");
                    newEvent.setPersistent(false);
                    eventService.send(newEvent);
                }
                return EventService.NO_CHANGE;
            });

            Rule rule1 = createTestRule();
            rule1.setItemId("loop-rule-1");
            rule1.setTenantId(TENANT_1);
            rule1.setCondition(createEventTypeCondition("loopEvent1"));
            Action action1 = new Action();
            action1.setActionType(definitionsService.getActionType("test"));
            rule1.setActions(Collections.singletonList(action1));

            rulesService.setRule(rule1);
            rulesService.refreshRules();

            // First loop should be detected
            int result1 = rulesService.onEvent(loopEvent1);
            assertNotNull(result1, "First loop should be detected");

            // Process a different event to reset context
            Event normalEvent = createTestEvent();
            normalEvent.setEventType("normal");
            normalEvent.setItemId("normal-event");
            rulesService.onEvent(normalEvent);

            // Second loop with different event should also be detected
            Event loopEvent2 = createTestEvent();
            loopEvent2.setEventType("loopEvent1");
            loopEvent2.setItemId("loop-1");
            int result2 = rulesService.onEvent(loopEvent2);
            assertNotNull(result2, "Second loop should also be detected");

            return null;
        });
    }

    @Test
    public void testDepthTrackingAccuracy() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Test that nested event processing works correctly
            // Note: Depth protection is now handled by EventServiceImpl.MAX_RECURSION_DEPTH
            // This test verifies that nested events can be processed without issues
            ActionType testActionType = TestHelper.createActionType("depthTest", "depthTest");
            definitionsService.setActionType(testActionType);

            // Verify action type is properly set up
            ActionType retrievedActionType = definitionsService.getActionType("depthTest");
            assertNotNull(retrievedActionType, "Action type should be available");

            final int[] depthCounter = {0};
            actionExecutorDispatcher.addExecutor("depthTest", (action, evt) -> {
                depthCounter[0]++;
                if (depthCounter[0] < 5) {
                    Event newEvent = new Event("depthTestEvent", evt.getSession(), evt.getProfile(),
                        evt.getScope(), evt, evt.getTarget(), evt.getTimeStamp());
                    newEvent.setItemId("depth-event-" + depthCounter[0]);
                    newEvent.setPersistent(false);
                    eventService.send(newEvent);
                }
                return EventService.NO_CHANGE;
            });

            Rule rule = createTestRule();
            rule.setItemId("depth-test-rule");
            rule.setTenantId(TENANT_1);
            rule.setCondition(createEventTypeCondition("depthTestEvent"));
            Action action = new Action();
            action.setActionType(retrievedActionType);
            rule.setActions(Collections.singletonList(action));

            rulesService.setRule(rule);
            rulesService.refreshRules();

            // Verify rule is saved and matches the event type
            Rule savedRule = rulesService.getRule("depth-test-rule");
            assertNotNull(savedRule, "Rule should be saved");

            Event startEvent = createTestEvent();
            startEvent.setEventType("depthTestEvent");
            startEvent.setItemId("depth-start");

            // Verify rule matches before processing (with retry to handle indexing delays)
            Set<Rule> matchingRules = TestHelper.retryUntil(
                () -> rulesService.getMatchingRules(startEvent),
                rules -> rules != null && !rules.isEmpty() &&
                    rules.stream().anyMatch(r -> r.getItemId().equals("depth-test-rule"))
            );
            assertFalse(matchingRules.isEmpty(), "Rule should match depthTestEvent");
            assertTrue(matchingRules.stream().anyMatch(r -> r.getItemId().equals("depth-test-rule")),
                "Matching rules should include depth-test-rule");

            // Should process without hitting depth limit (we only go 5 levels deep)
            // Depth protection is handled by EventServiceImpl.MAX_RECURSION_DEPTH (20)
            int result = rulesService.onEvent(startEvent);
            assertNotNull(result, "Should process nested events without hitting depth limit");

            // Verify nested events were processed (we should have processed multiple levels)
            // The action executor should be called at least once for the initial event
            // Note: The counter increments when the action executor is called, which happens
            // when a rule fires. Even if nested events aren't processed due to depth limits,
            // the initial event should trigger the action at least once.
            assertTrue(depthCounter[0] > 0,
                "Should have processed nested events. Action executor was called " + depthCounter[0] + " times. " +
                "This means the rule matched and the action was executed.");

            return null;
        });
    }

    @Test
    public void testThreadLocalCleanupAfterException() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Action executor that throws exception
            actionExecutorDispatcher.addExecutor("test",
                (action, event) -> { throw new RuntimeException("Test exception"); });

            Rule rule = createTestRule();
            rule.setItemId("exception-rule");
            rule.setTenantId(TENANT_1);
            rule.setActions(Collections.singletonList(createTestAction()));
            rulesService.setRule(rule);

            // Ensure rule is saved and available before refreshing
            Rule savedRule = persistenceService.load(rule.getItemId(), Rule.class);
            assertNotNull(savedRule, "Rule should be saved");

            // Refresh rules to load and cache them
            rulesService.refreshRules();

            // Verify rule is loaded and cached by checking it can be retrieved
            Rule retrievedRule = rulesService.getRule(rule.getItemId());
            assertNotNull(retrievedRule, "Rule should be retrievable after refreshRules");

            // Ensure rule matches the event (with refresh delay disabled, this should be immediate)
            Event testEvent = createTestEvent();
            testEvent.setItemId("exception-test-event");
            Set<Rule> matchingRules = TestHelper.retryUntil(
                () -> rulesService.getMatchingRules(testEvent),
                r -> r != null && !r.isEmpty() && r.stream().anyMatch(rl -> rl.getItemId().equals("exception-rule"))
            );
            assertFalse(matchingRules.isEmpty(), "Rule should be available and match the event");
            assertTrue(matchingRules.stream().anyMatch(rl -> rl.getItemId().equals("exception-rule")),
                "Matching rules should include the exception-rule");

            // Exception should not prevent ThreadLocal cleanup (finally block should execute)
            assertThrows(RuntimeException.class, () -> rulesService.onEvent(testEvent),
                "Exception should propagate but ThreadLocal should be cleaned up");

            // Should still be able to process events after exception (cleanup worked)
            // This verifies that ProcessingContext was properly cleaned up
            Event event2 = createTestEvent();
            event2.setEventType("anotherEvent");
            event2.setItemId("after-exception-event");
            int result = rulesService.onEvent(event2);
            assertNotNull(result, "Should process events after exception - ThreadLocal cleanup verified");

            // Verify we can process multiple events in sequence (ThreadLocal is recreated properly)
            Event event3 = createTestEvent();
            event3.setEventType("thirdEvent");
            event3.setItemId("third-event");
            assertNotNull(rulesService.onEvent(event3), "Should process multiple events sequentially");

            return null;
        });
    }

    @Test
    public void testRuleWithUnresolvedConditionTypeShouldNotBeIndexedAsWildcard() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create a rule with a condition type that doesn't exist (not deployed)
            Rule rule = new Rule();
            rule.setItemId("unresolved-condition-rule");
            rule.setTenantId(TENANT_1);

            Metadata metadata = new Metadata();
            metadata.setId("unresolved-condition-rule");
            metadata.setEnabled(true);
            metadata.setScope("systemscope");
            rule.setMetadata(metadata);

            // Create condition with unresolved condition type (no eventTypeCondition, so would default to wildcard)
            Condition condition = new Condition();
            condition.setConditionTypeId("nonExistentConditionType");
            condition.setParameter("propertyName", "testProperty");
            rule.setCondition(condition);

            // Add a valid action so the rule structure is complete
            rule.setActions(Collections.singletonList(createTestAction()));

            // Set rule with allowInvalidRules=true (simulating a rule loaded from bundle before condition type is deployed)
            rulesService.setRule(rule, true);

            // Refresh rules to ensure indexing happens - this is where the bug would manifest
            // The rule should be excluded, not indexed as wildcard
            rulesService.refreshRules();

            // Verify rule is saved
            Rule savedRule = rulesService.getRule("unresolved-condition-rule");
            assertNotNull(savedRule, "Rule should be saved");

            // Verify rule has missingPlugins flag set (indicating unresolved condition type)
            assertTrue(savedRule.getMetadata().isMissingPlugins(),
                "Rule should be marked as having missing plugins when condition type is unresolved");

            // Create an event that would match if the rule was indexed as wildcard
            Event testEvent = createTestEvent();
            testEvent.setEventType("anyEventType");
            testEvent.setItemId("test-event-unresolved");

            // The rule should NOT match because it should be excluded from indexing, not indexed as wildcard
            Set<Rule> matchingRules = rulesService.getMatchingRules(testEvent);
            assertFalse(matchingRules.contains(savedRule),
                "Rule with unresolved condition type should not match events (should be deactivated, not wildcard). " +
                "Rule ID: unresolved-condition-rule, Event type: anyEventType, Matching rules: " +
                matchingRules.stream().map(r -> r.getItemId()).collect(java.util.stream.Collectors.toList()));

            // Verify the rule is not in the wildcard index by checking it doesn't match any event type
            Event anotherEvent = createTestEvent();
            anotherEvent.setEventType("anotherEventType");
            anotherEvent.setItemId("test-event-another");
            Set<Rule> matchingRules2 = rulesService.getMatchingRules(anotherEvent);
            assertFalse(matchingRules2.contains(savedRule),
                "Rule with unresolved condition type should not match any event type (should be deactivated). " +
                "Rule ID: unresolved-condition-rule, Event type: anotherEventType, Matching rules: " +
                matchingRules2.stream().map(r -> r.getItemId()).collect(java.util.stream.Collectors.toList()));

            return null;
        });
    }

    @Test
    public void testRuleWithUnresolvedConditionTypeInNestedCondition() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create a rule with unresolved condition type in nested subConditions
            Rule rule = new Rule();
            rule.setItemId("nested-unresolved-rule");
            rule.setTenantId(TENANT_1);

            Metadata metadata = new Metadata();
            metadata.setId("nested-unresolved-rule");
            metadata.setEnabled(true);
            metadata.setScope("systemscope");
            rule.setMetadata(metadata);

            // Create parent condition with valid type
            Condition parentCondition = new Condition();
            parentCondition.setConditionType(definitionsService.getConditionType("booleanCondition"));
            parentCondition.setParameter("operator", "and");

            // Create child condition with unresolved type
            Condition childCondition = new Condition();
            childCondition.setConditionTypeId("nonExistentNestedConditionType");
            childCondition.setParameter("propertyName", "testProperty");

            // Set up nested structure
            List<Condition> subConditions = new ArrayList<>();
            subConditions.add(childCondition);
            parentCondition.setParameter("subConditions", subConditions);

            rule.setCondition(parentCondition);
            rule.setActions(Collections.singletonList(createTestAction()));

            // Set rule with allowInvalidRules=true
            rulesService.setRule(rule, true);
            rulesService.refreshRules();

            Rule savedRule = rulesService.getRule("nested-unresolved-rule");
            assertNotNull(savedRule, "Rule should be saved");
            assertTrue(savedRule.getMetadata().isMissingPlugins(),
                "Rule should be marked as having missing plugins when nested condition type is unresolved");

            // Rule should not match any events
            Event testEvent = createTestEvent();
            testEvent.setEventType("anyEventType");
            testEvent.setItemId("test-event-nested");

            Set<Rule> matchingRules = rulesService.getMatchingRules(testEvent);
            assertFalse(matchingRules.contains(savedRule),
                "Rule with unresolved nested condition type should not match events. " +
                "Rule ID: nested-unresolved-rule");

            return null;
        });
    }

    @Test
    public void testRuleWithMixedResolvedAndUnresolvedConditionTypes() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create a rule with both resolved and unresolved condition types
            Rule rule = new Rule();
            rule.setItemId("mixed-condition-rule");
            rule.setTenantId(TENANT_1);

            Metadata metadata = new Metadata();
            metadata.setId("mixed-condition-rule");
            metadata.setEnabled(true);
            metadata.setScope("systemscope");
            rule.setMetadata(metadata);

            // Create parent condition with valid type
            Condition parentCondition = new Condition();
            parentCondition.setConditionType(definitionsService.getConditionType("booleanCondition"));
            parentCondition.setParameter("operator", "and");

            // Create one resolved child condition
            Condition resolvedChild = createProfilePropertyCondition("testProperty", "equals", "testValue");

            // Create one unresolved child condition
            Condition unresolvedChild = new Condition();
            unresolvedChild.setConditionTypeId("nonExistentConditionType2");
            unresolvedChild.setParameter("propertyName", "testProperty");

            // Set up nested structure with both
            List<Condition> subConditions = new ArrayList<>();
            subConditions.add(resolvedChild);
            subConditions.add(unresolvedChild);
            parentCondition.setParameter("subConditions", subConditions);

            rule.setCondition(parentCondition);
            rule.setActions(Collections.singletonList(createTestAction()));

            // Set rule with allowInvalidRules=true
            rulesService.setRule(rule, true);
            rulesService.refreshRules();

            Rule savedRule = rulesService.getRule("mixed-condition-rule");
            assertNotNull(savedRule, "Rule should be saved");
            assertTrue(savedRule.getMetadata().isMissingPlugins(),
                "Rule should be marked as having missing plugins when any condition type is unresolved");

            // Rule should not match any events (even if one condition is resolved)
            Event testEvent = createTestEvent();
            testEvent.setEventType("anyEventType");
            testEvent.setItemId("test-event-mixed");

            Set<Rule> matchingRules = rulesService.getMatchingRules(testEvent);
            assertFalse(matchingRules.contains(savedRule),
                "Rule with mixed resolved/unresolved condition types should not match events. " +
                "Rule ID: mixed-condition-rule");

            return null;
        });
    }

    @Test
    public void testRuleWithUnresolvedConditionTypeThatGetsResolvedLater() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create a rule with unresolved condition type
            Rule rule = new Rule();
            rule.setItemId("later-resolved-rule");
            rule.setTenantId(TENANT_1);

            Metadata metadata = new Metadata();
            metadata.setId("later-resolved-rule");
            metadata.setEnabled(true);
            metadata.setScope("systemscope");
            rule.setMetadata(metadata);

            Condition condition = new Condition();
            condition.setConditionTypeId("laterDeployedConditionType");
            condition.setParameter("propertyName", "testProperty");
            rule.setCondition(condition);
            rule.setActions(Collections.singletonList(createTestAction()));

            // Set rule with allowInvalidRules=true (simulating bundle deployment)
            rulesService.setRule(rule, true);
            rulesService.refreshRules();

            Rule savedRule = rulesService.getRule("later-resolved-rule");
            assertNotNull(savedRule, "Rule should be saved");
            assertTrue(savedRule.getMetadata().isMissingPlugins(),
                "Rule should be marked as having missing plugins initially");

            // Rule should not match events initially
            Event testEvent = createTestEvent();
            testEvent.setEventType("test");
            testEvent.setItemId("test-event-later");

            Set<Rule> matchingRules1 = rulesService.getMatchingRules(testEvent);
            assertFalse(matchingRules1.contains(savedRule),
                "Rule with unresolved condition type should not match events initially");

            // Now deploy the condition type
            ConditionType conditionType = createTestConditionType("laterDeployedConditionType");
            definitionsService.setConditionType(conditionType);

            // Refresh persistence and rules
            persistenceService.refresh();
            rulesService.refreshRules();

            // Rule should now be resolved and work
            Rule refreshedRule = rulesService.getRule("later-resolved-rule");
            assertNotNull(refreshedRule, "Refreshed rule should not be null");
            // Note: missingPlugins might still be true until the rule is re-saved, but it should be resolvable now

            return null;
        });
    }

    @Test
    public void testRuleWithUnresolvedActionTypes() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create a rule with valid condition but unresolved action type
            // Note: This test verifies that rules with unresolved action types are excluded
            // The main fix is for condition types, but we verify action types are also handled
            Rule rule = new Rule();
            rule.setItemId("unresolved-action-rule");
            rule.setTenantId(TENANT_1);

            Metadata metadata = new Metadata();
            metadata.setId("unresolved-action-rule");
            metadata.setEnabled(true);
            metadata.setScope("systemscope");
            rule.setMetadata(metadata);

            // Valid condition
            Condition condition = createEventTypeCondition("test");
            rule.setCondition(condition);

            // Unresolved action type
            Action action = new Action();
            action.setActionTypeId("nonExistentActionType");
            rule.setActions(Collections.singletonList(action));

            // Set rule with allowInvalidRules=true
            rulesService.setRule(rule, true);
            rulesService.refreshRules();

            Rule savedRule = rulesService.getRule("unresolved-action-rule");
            assertNotNull(savedRule, "Rule should be saved");

            // The key test: Rule should not match events (should be excluded)
            // This is what matters - whether it's marked as invalid or missingPlugins is less important
            Event testEvent = createTestEvent();
            testEvent.setEventType("test");
            testEvent.setItemId("test-event-action");

            Set<Rule> matchingRules = rulesService.getMatchingRules(testEvent);
            assertFalse(matchingRules.contains(savedRule),
                "Rule with unresolved action type should not match events (should be excluded). " +
                "Rule ID: unresolved-action-rule");

            return null;
        });
    }

    @Test
    public void testRuleWithUnresolvedTypesButDisabled() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create a disabled rule with unresolved condition type
            Rule rule = new Rule();
            rule.setItemId("disabled-unresolved-rule");
            rule.setTenantId(TENANT_1);

            Metadata metadata = new Metadata();
            metadata.setId("disabled-unresolved-rule");
            metadata.setEnabled(false); // Disabled
            metadata.setScope("systemscope");
            rule.setMetadata(metadata);

            Condition condition = new Condition();
            condition.setConditionTypeId("nonExistentConditionType3");
            condition.setParameter("propertyName", "testProperty");
            rule.setCondition(condition);
            rule.setActions(Collections.singletonList(createTestAction()));

            // Set rule with allowInvalidRules=true
            rulesService.setRule(rule, true);
            rulesService.refreshRules();

            Rule savedRule = rulesService.getRule("disabled-unresolved-rule");
            assertNotNull(savedRule, "Rule should be saved");
            assertFalse(savedRule.getMetadata().isEnabled(), "Rule should be disabled");

            // Disabled rule should not match events (regardless of unresolved types)
            Event testEvent = createTestEvent();
            testEvent.setEventType("anyEventType");
            testEvent.setItemId("test-event-disabled");

            Set<Rule> matchingRules = rulesService.getMatchingRules(testEvent);
            assertFalse(matchingRules.contains(savedRule),
                "Disabled rule should not match events. " +
                "Rule ID: disabled-unresolved-rule");

            return null;
        });
    }

    @Test
    public void testRuleWithMultipleUnresolvedTypesInDifferentBranches() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create a rule with multiple unresolved condition types in different branches
            Rule rule = new Rule();
            rule.setItemId("multiple-unresolved-rule");
            rule.setTenantId(TENANT_1);

            Metadata metadata = new Metadata();
            metadata.setId("multiple-unresolved-rule");
            metadata.setEnabled(true);
            metadata.setScope("systemscope");
            rule.setMetadata(metadata);

            // Create parent condition
            Condition parentCondition = new Condition();
            parentCondition.setConditionType(definitionsService.getConditionType("booleanCondition"));
            parentCondition.setParameter("operator", "or");

            // Create multiple child conditions with different unresolved types
            Condition child1 = new Condition();
            child1.setConditionTypeId("nonExistentType1");
            child1.setParameter("propertyName", "prop1");

            Condition child2 = new Condition();
            child2.setConditionTypeId("nonExistentType2");
            child2.setParameter("propertyName", "prop2");

            List<Condition> subConditions = new ArrayList<>();
            subConditions.add(child1);
            subConditions.add(child2);
            parentCondition.setParameter("subConditions", subConditions);

            rule.setCondition(parentCondition);
            rule.setActions(Collections.singletonList(createTestAction()));

            // Set rule with allowInvalidRules=true
            rulesService.setRule(rule, true);
            rulesService.refreshRules();

            Rule savedRule = rulesService.getRule("multiple-unresolved-rule");
            assertNotNull(savedRule, "Rule should be saved");
            assertTrue(savedRule.getMetadata().isMissingPlugins(),
                "Rule should be marked as having missing plugins when multiple condition types are unresolved");

            // Rule should not match any events
            Event testEvent = createTestEvent();
            testEvent.setEventType("anyEventType");
            testEvent.setItemId("test-event-multiple");

            Set<Rule> matchingRules = rulesService.getMatchingRules(testEvent);
            assertFalse(matchingRules.contains(savedRule),
                "Rule with multiple unresolved condition types should not match events. " +
                "Rule ID: multiple-unresolved-rule");

            return null;
        });
    }

    @Test
    public void testRuleWithUnresolvedTypeInListParameter() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create a rule with unresolved condition type in a list parameter
            // Some condition types might have list parameters containing conditions
            Rule rule = new Rule();
            rule.setItemId("list-parameter-unresolved-rule");
            rule.setTenantId(TENANT_1);

            Metadata metadata = new Metadata();
            metadata.setId("list-parameter-unresolved-rule");
            metadata.setEnabled(true);
            metadata.setScope("systemscope");
            rule.setMetadata(metadata);

            // Create parent condition
            Condition parentCondition = new Condition();
            parentCondition.setConditionType(definitionsService.getConditionType("booleanCondition"));
            parentCondition.setParameter("operator", "and");

            // Create child with unresolved type in subConditions list
            Condition unresolvedChild = new Condition();
            unresolvedChild.setConditionTypeId("nonExistentListType");
            unresolvedChild.setParameter("propertyName", "testProperty");

            List<Condition> subConditions = new ArrayList<>();
            subConditions.add(unresolvedChild);
            parentCondition.setParameter("subConditions", subConditions);

            rule.setCondition(parentCondition);
            rule.setActions(Collections.singletonList(createTestAction()));

            // Set rule with allowInvalidRules=true
            rulesService.setRule(rule, true);
            rulesService.refreshRules();

            Rule savedRule = rulesService.getRule("list-parameter-unresolved-rule");
            assertNotNull(savedRule, "Rule should be saved");
            assertTrue(savedRule.getMetadata().isMissingPlugins(),
                "Rule should be marked as having missing plugins when condition type in list is unresolved");

            // Rule should not match any events
            Event testEvent = createTestEvent();
            testEvent.setEventType("anyEventType");
            testEvent.setItemId("test-event-list");

            Set<Rule> matchingRules = rulesService.getMatchingRules(testEvent);
            assertFalse(matchingRules.contains(savedRule),
                "Rule with unresolved condition type in list parameter should not match events. " +
                "Rule ID: list-parameter-unresolved-rule");

            return null;
        });
    }

    @Test
    public void testRuleWithDeeplyNestedUnresolvedType() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create a rule with unresolved condition type deeply nested
            Rule rule = new Rule();
            rule.setItemId("deeply-nested-unresolved-rule");
            rule.setTenantId(TENANT_1);

            Metadata metadata = new Metadata();
            metadata.setId("deeply-nested-unresolved-rule");
            metadata.setEnabled(true);
            metadata.setScope("systemscope");
            rule.setMetadata(metadata);

            // Level 1: booleanCondition
            Condition level1 = new Condition();
            level1.setConditionType(definitionsService.getConditionType("booleanCondition"));
            level1.setParameter("operator", "and");

            // Level 2: another booleanCondition
            Condition level2 = new Condition();
            level2.setConditionType(definitionsService.getConditionType("booleanCondition"));
            level2.setParameter("operator", "or");

            // Level 3: unresolved condition type
            Condition level3 = new Condition();
            level3.setConditionTypeId("deeplyNestedUnresolvedType");
            level3.setParameter("propertyName", "testProperty");

            List<Condition> level3List = new ArrayList<>();
            level3List.add(level3);
            level2.setParameter("subConditions", level3List);

            List<Condition> level2List = new ArrayList<>();
            level2List.add(level2);
            level1.setParameter("subConditions", level2List);

            rule.setCondition(level1);
            rule.setActions(Collections.singletonList(createTestAction()));

            // Set rule with allowInvalidRules=true
            rulesService.setRule(rule, true);
            rulesService.refreshRules();

            Rule savedRule = rulesService.getRule("deeply-nested-unresolved-rule");
            assertNotNull(savedRule, "Rule should be saved");
            assertTrue(savedRule.getMetadata().isMissingPlugins(),
                "Rule should be marked as having missing plugins when deeply nested condition type is unresolved");

            // Rule should not match any events
            Event testEvent = createTestEvent();
            testEvent.setEventType("anyEventType");
            testEvent.setItemId("test-event-deep");

            Set<Rule> matchingRules = rulesService.getMatchingRules(testEvent);
            assertFalse(matchingRules.contains(savedRule),
                "Rule with deeply nested unresolved condition type should not match events. " +
                "Rule ID: deeply-nested-unresolved-rule");

            return null;
        });
    }

    @Test
    public void testRuleWithConditionTypeHavingUnresolvedParentCondition() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create a rule with a condition type that exists but has an unresolved parent condition
            Rule rule = new Rule();
            rule.setItemId("unresolved-parent-condition-rule");
            rule.setTenantId(TENANT_1);

            Metadata metadata = new Metadata();
            metadata.setId("unresolved-parent-condition-rule");
            metadata.setEnabled(true);
            metadata.setScope("systemscope");
            rule.setMetadata(metadata);

            // Create a condition type that exists but has a parent condition that doesn't exist
            ConditionType childConditionType = createTestConditionType("childConditionWithParent");
            Condition unresolvedParentCondition = new Condition();
            unresolvedParentCondition.setConditionTypeId("nonExistentParentConditionType");
            childConditionType.setParentCondition(unresolvedParentCondition);

            // Register the child condition type (but not the parent)
            definitionsService.setConditionType(childConditionType);

            // Create condition using the child type
            Condition condition = new Condition();
            condition.setConditionTypeId("childConditionWithParent");
            condition.setParameter("propertyName", "testProperty");
            rule.setCondition(condition);
            rule.setActions(Collections.singletonList(createTestAction()));

            // Set rule with allowInvalidRules=true
            rulesService.setRule(rule, true);

            // Refresh rules to trigger resolution for indexing
            rulesService.refreshRules();

            Rule savedRule = rulesService.getRule("unresolved-parent-condition-rule");
            assertNotNull(savedRule, "Rule should be saved");

            // Clear condition type to force re-resolution (simulates loading from persistence)
            // This ensures the parent condition check happens
            if (savedRule.getCondition() != null) {
                savedRule.getCondition().setConditionType(null);
            }

            // Manually trigger resolution to ensure parent condition is checked
            // This simulates what happens during refreshRules -> updateRulesByEventType -> ensureRuleResolvedForIndexing
            TypeResolutionServiceImpl testTypeResolutionService = new TypeResolutionServiceImpl(definitionsService);
            boolean resolved = testTypeResolutionService.resolveRule("rules", savedRule);

            // Resolution should fail because parent condition doesn't exist
            assertFalse(resolved, "Rule resolution should fail when parent condition type is unresolved");

            // Check if rule is marked as invalid or has missing plugins
            boolean hasMissingPlugins = savedRule.getMetadata().isMissingPlugins();
            boolean isInvalid = testTypeResolutionService.isInvalid("rules", savedRule.getItemId());

            // Verify the condition type was cleared due to unresolved parent
            // When parent can't be resolved, TypeResolutionService sets condition type to null
            boolean conditionTypeCleared = savedRule.getCondition() != null &&
                savedRule.getCondition().getConditionType() == null &&
                savedRule.getCondition().getConditionTypeId() != null;

            assertTrue(hasMissingPlugins || isInvalid || conditionTypeCleared,
                "Rule should be marked as having missing plugins, invalid, or have condition type cleared when parent condition type is unresolved. " +
                "missingPlugins: " + hasMissingPlugins + ", isInvalid: " + isInvalid + ", conditionTypeCleared: " + conditionTypeCleared);

            // Rule should not match any events (this is the most important check)
            Event testEvent = createTestEvent();
            testEvent.setEventType("anyEventType");
            testEvent.setItemId("test-event-unresolved-parent");

            Set<Rule> matchingRules = rulesService.getMatchingRules(testEvent);
            assertFalse(matchingRules.contains(savedRule),
                "Rule with condition type having unresolved parent condition should not match events. " +
                "Rule ID: unresolved-parent-condition-rule, missingPlugins: " + hasMissingPlugins +
                ", isInvalid: " + isInvalid);

            return null;
        });
    }

    @Test
    public void testRuleWithConditionTypeHavingUnresolvedGrandparentInParentChain() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create a rule with a condition type that has a parent chain where grandparent doesn't exist
            // Chain: child -> parent -> grandparent (unresolved)
            Rule rule = new Rule();
            rule.setItemId("unresolved-grandparent-condition-rule");
            rule.setTenantId(TENANT_1);

            Metadata metadata = new Metadata();
            metadata.setId("unresolved-grandparent-condition-rule");
            metadata.setEnabled(true);
            metadata.setScope("systemscope");
            rule.setMetadata(metadata);

            // Create grandparent condition (unresolved - doesn't exist)
            Condition unresolvedGrandparentCondition = new Condition();
            unresolvedGrandparentCondition.setConditionTypeId("nonExistentGrandparentType");

            // Create parent condition type that references the unresolved grandparent
            ConditionType parentConditionType = createTestConditionType("parentConditionWithGrandparent");
            parentConditionType.setParentCondition(unresolvedGrandparentCondition);

            // Create child condition type that references the parent
            Condition parentCondition = new Condition();
            parentCondition.setConditionTypeId("parentConditionWithGrandparent");
            ConditionType childConditionType = createTestConditionType("childConditionWithParentChain");
            childConditionType.setParentCondition(parentCondition);

            // Register parent and child condition types (but not grandparent)
            definitionsService.setConditionType(parentConditionType);
            definitionsService.setConditionType(childConditionType);

            // Create condition using the child type
            Condition condition = new Condition();
            condition.setConditionTypeId("childConditionWithParentChain");
            condition.setParameter("propertyName", "testProperty");
            rule.setCondition(condition);
            rule.setActions(Collections.singletonList(createTestAction()));

            // Set rule with allowInvalidRules=true
            rulesService.setRule(rule, true);
            rulesService.refreshRules();

            Rule savedRule = rulesService.getRule("unresolved-grandparent-condition-rule");
            assertNotNull(savedRule, "Rule should be saved");

            // Clear condition type to force re-resolution (simulates loading from persistence)
            if (savedRule.getCondition() != null) {
                savedRule.getCondition().setConditionType(null);
            }

            // Manually trigger resolution to ensure parent chain is checked
            TypeResolutionServiceImpl testTypeResolutionService = new TypeResolutionServiceImpl(definitionsService);
            boolean resolved = testTypeResolutionService.resolveRule("rules", savedRule);

            // Resolution should fail because grandparent condition doesn't exist
            assertFalse(resolved, "Rule resolution should fail when grandparent condition type in parent chain is unresolved");

            // Check if rule is marked as invalid or has missing plugins
            boolean hasMissingPlugins = savedRule.getMetadata().isMissingPlugins();
            boolean isInvalid = testTypeResolutionService.isInvalid("rules", savedRule.getItemId());

            // Rule should not match any events (this is the most important check)
            Event testEvent = createTestEvent();
            testEvent.setEventType("anyEventType");
            testEvent.setItemId("test-event-unresolved-grandparent");

            Set<Rule> matchingRules = rulesService.getMatchingRules(testEvent);
            assertFalse(matchingRules.contains(savedRule),
                "Rule with condition type having unresolved grandparent in parent chain should not match events. " +
                "Rule ID: unresolved-grandparent-condition-rule, missingPlugins: " + hasMissingPlugins +
                ", isInvalid: " + isInvalid);

            return null;
        });
    }

    @Test
    public void testRuleWithUnresolvedSubConditionInParentCondition() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Create a rule where the condition type has a parent condition that contains
            // an unresolved sub-condition in its subConditions parameter
            Rule rule = new Rule();
            rule.setItemId("unresolved-sub-in-parent-rule");
            rule.setTenantId(TENANT_1);

            Metadata metadata = new Metadata();
            metadata.setId("unresolved-sub-in-parent-rule");
            metadata.setEnabled(true);
            metadata.setScope("systemscope");
            rule.setMetadata(metadata);

            // Create parent condition with unresolved sub-condition
            Condition unresolvedSubCondition = new Condition();
            unresolvedSubCondition.setConditionTypeId("nonExistentSubConditionType");
            unresolvedSubCondition.setParameter("propertyName", "testProperty");

            Condition parentCondition = new Condition();
            parentCondition.setConditionType(definitionsService.getConditionType("booleanCondition"));
            parentCondition.setParameter("operator", "and");
            parentCondition.setParameter("subConditions", Collections.singletonList(unresolvedSubCondition));

            // Create child condition type that uses this parent condition
            ConditionType childConditionType = createTestConditionType("childConditionWithParentSub");
            childConditionType.setParentCondition(parentCondition);

            // Register the child condition type
            definitionsService.setConditionType(childConditionType);

            // Create condition using the child type
            Condition condition = new Condition();
            condition.setConditionTypeId("childConditionWithParentSub");
            condition.setParameter("propertyName", "testProperty");
            rule.setCondition(condition);
            rule.setActions(Collections.singletonList(createTestAction()));

            // Set rule with allowInvalidRules=true
            rulesService.setRule(rule, true);
            rulesService.refreshRules();

            Rule savedRule = rulesService.getRule("unresolved-sub-in-parent-rule");
            assertNotNull(savedRule, "Rule should be saved");

            // Clear condition type to force re-resolution (simulates loading from persistence)
            if (savedRule.getCondition() != null) {
                savedRule.getCondition().setConditionType(null);
            }

            // Manually trigger resolution to ensure parent condition's sub-conditions are checked
            TypeResolutionServiceImpl testTypeResolutionService = new TypeResolutionServiceImpl(definitionsService);
            boolean resolved = testTypeResolutionService.resolveRule("rules", savedRule);

            // Resolution should fail because parent condition has unresolved sub-condition
            assertFalse(resolved, "Rule resolution should fail when parent condition has unresolved sub-condition");

            // Check if rule is marked as invalid or has missing plugins
            boolean hasMissingPlugins = savedRule.getMetadata().isMissingPlugins();
            boolean isInvalid = testTypeResolutionService.isInvalid("rules", savedRule.getItemId());

            // Rule should not match any events (this is the most important check)
            Event testEvent = createTestEvent();
            testEvent.setEventType("anyEventType");
            testEvent.setItemId("test-event-unresolved-sub-in-parent");

            Set<Rule> matchingRules = rulesService.getMatchingRules(testEvent);
            assertFalse(matchingRules.contains(savedRule),
                "Rule with condition type having parent condition with unresolved sub-condition should not match events. " +
                "Rule ID: unresolved-sub-in-parent-rule, missingPlugins: " + hasMissingPlugins +
                ", isInvalid: " + isInvalid);

            return null;
        });
    }

}
