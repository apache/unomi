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
import org.apache.unomi.api.services.ConditionValidationService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.RuleListenerService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.conditions.evaluator.ConditionEvaluatorDispatcher;
import org.apache.unomi.services.TestHelper;
import org.apache.unomi.services.common.security.ExecutionContextManagerImpl;
import org.apache.unomi.services.common.security.KarafSecurityService;
import org.apache.unomi.services.impl.*;
import org.apache.unomi.services.impl.cache.MultiTypeCacheServiceImpl;
import org.apache.unomi.services.impl.definitions.DefinitionsServiceImpl;
import org.apache.unomi.services.common.security.AuditServiceImpl;
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

    @BeforeEach
    public void setUp() throws Exception {
        
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
        schedulerService = TestHelper.createSchedulerService("rule-scheduler-node", persistenceService, executionContextManager, bundleContext, null, -1, true, true);

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
        rulesService.setCacheService(multiTypeCacheService);
        
        // Create and inject ResolverService
        ResolverServiceImpl resolverService = new ResolverServiceImpl();
        resolverService.setDefinitionsService(definitionsService);
        rulesService.setResolverService(resolverService);

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
        assertNotNull(definitionsService.getConditionType("unavailableConditionType"), "Rule should have a condition type after refresh");
        assertNotNull(refreshedRule, "Refreshed rule should not be null");
        assertNotNull(refreshedRule.getCondition().getConditionType(), "Rule condition type should not be null");
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
        assertNotNull(definitionsService.getConditionType("unavailableConditionType"), "Rule should have a condition type after refresh");
        assertNotNull(refreshedRule, "Refreshed rule should not be null");
        assertNotNull(refreshedRule.getCondition().getConditionType(), "Rule condition type should not be null");
    }
}
