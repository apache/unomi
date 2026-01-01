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
 * limitations under the License
 */
package org.apache.unomi.itests;

import org.apache.unomi.api.*;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.RulesService;
import org.apache.unomi.api.utils.ConditionBuilder;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Integration tests for the Unomi rule service.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class RuleServiceIT extends BaseIT {

    private final static Logger LOGGER = LoggerFactory.getLogger(RuleServiceIT.class);

    private final static String TEST_RULE_ID = "test-rule-id";
    public static final String TEST_SCOPE = "test-scope";

    @Before
    public void setUp() {
        TestUtils.removeAllProfiles(definitionsService, persistenceService);
    }

    /**
     * Creates a default action for test rules. Uses setPropertyAction as a simple, always-available action.
     * 
     * @return a default action for test rules
     */
    private Action createDefaultAction() {
        Action action = new Action(definitionsService.getActionType("setPropertyAction"));
        action.setParameter("propertyName", "testProperty");
        action.setParameter("propertyValue", "testValue");
        return action;
    }

    /**
     * Creates a rule with a default action. This ensures all rules have actions, which is required in newer versions.
     * 
     * @param metadata the rule metadata
     * @param condition the rule condition (may be null)
     * @return a rule with default action
     */
    private Rule createRuleWithDefaultAction(Metadata metadata, Condition condition) {
        return createRuleWithActions(metadata, condition, Collections.singletonList(createDefaultAction()));
    }

    /**
     * Creates a rule with specified actions. If actions is null or empty, a default action is added.
     * 
     * @param metadata the rule metadata
     * @param condition the rule condition (may be null)
     * @param actions the list of actions (if null or empty, a default action is added)
     * @return a rule with actions
     */
    private Rule createRuleWithActions(Metadata metadata, Condition condition, List<Action> actions) {
        Rule rule = new Rule(metadata);
        rule.setCondition(condition);
        
        // Ensure rule always has at least one action (required in newer versions)
        if (actions == null || actions.isEmpty()) {
            rule.setActions(Collections.singletonList(createDefaultAction()));
        } else {
            rule.setActions(actions);
        }
        
        return rule;
    }

    @Test
    public void testRuleWithNullActions() throws InterruptedException {
        Metadata metadata = new Metadata(TEST_RULE_ID);
        metadata.setName(TEST_RULE_ID + "_name");
        metadata.setDescription(TEST_RULE_ID + "_description");
        metadata.setScope(TEST_SCOPE);
        Rule nullRule = new Rule(metadata);
        nullRule.setCondition(null);
        nullRule.setActions(null);
        createAndWaitForRule(nullRule);
        assertNull("Expected rule actions to be null", nullRule.getActions());
        assertNull("Expected rule condition to be null", nullRule.getCondition());
        assertEquals("Invalid rule name", TEST_RULE_ID + "_name", nullRule.getMetadata().getName());
        rulesService.removeRule(TEST_RULE_ID);
        refreshPersistence(Rule.class);
        rulesService.refreshRules();
    }

    @Test
    public void getAllRulesShouldReturnAllRulesAvailable() throws InterruptedException {
        String ruleIDBase = "moreThan50RuleTest";
        refreshPersistence(Rule.class); // refresh the persistence to ensure that the rules are all properly indexed by the persistence service
        rulesService.refreshRules();
        int originalRulesNumber = rulesService.getAllRules().size();
        LOGGER.info("Original number of rules: {}", originalRulesNumber);

        // Create a simple condition instead of null
        Condition defaultCondition = new Condition(definitionsService.getConditionType("matchAllCondition"));

        int successfullyCreatedRules = 0;
        for (int i = 0; i < 60; i++) {
            String ruleID = ruleIDBase + "_" + i;
            Metadata metadata = new Metadata(ruleID);
            metadata.setName(ruleID);
            metadata.setDescription(ruleID);
            metadata.setScope(TEST_SCOPE);
            // Use helper method to ensure rule always has actions
            Rule rule = createRuleWithDefaultAction(metadata, defaultCondition);
            
            try {
                createAndWaitForRule(rule);
                successfullyCreatedRules++;
                LOGGER.debug("Successfully created rule: {}", ruleID);
            } catch (Exception e) {
                LOGGER.error("Failed to create rule: {}", ruleID, e);
            }
        }
        
        LOGGER.info("Successfully created {} out of 60 rules", successfullyCreatedRules);
        
        // Wait a bit more to ensure all rules are indexed
        Thread.sleep(1000);
        refreshPersistence(Rule.class);
        rulesService.refreshRules();
        
        int finalRulesNumber = rulesService.getAllRules().size();
        LOGGER.info("Final number of rules: {} (expected: {})", finalRulesNumber, originalRulesNumber + 60);
        
        assertEquals("Expected getAllRules to be able to retrieve all the rules available in the system", originalRulesNumber + 60, finalRulesNumber);
        
        // cleanup
        for (int i = 0; i < 60; i++) {
            String ruleID = ruleIDBase + "_" + i;
            rulesService.removeRule(ruleID);
        }
        refreshPersistence(Rule.class);
        rulesService.refreshRules();
    }

    @Test
    public void testRuleEventTypeOptimization() throws InterruptedException {
        ConditionBuilder builder = definitionsService.getConditionBuilder();
        Rule simpleEventTypeRule = createRuleWithDefaultAction(
            new Metadata(TEST_SCOPE, "simple-event-type-rule", "Simple event type rule", "A rule with a simple condition to match an event type"),
            builder.condition("eventTypeCondition").parameter("eventTypeId", "view").build()
        );
        createAndWaitForRule(simpleEventTypeRule);
        Rule complexEventTypeRule = createRuleWithDefaultAction(
            new Metadata(TEST_SCOPE, "complex-event-type-rule", "Complex event type rule", "A rule with a complex condition to match multiple event types with negations"),
            builder.not(
                    builder.or(
                            builder.condition("eventTypeCondition").parameter( "eventTypeId", "view"),
                            builder.condition("eventTypeCondition").parameter("eventTypeId", "form")
                    )
            ).build()
        );
        createAndWaitForRule(complexEventTypeRule);
        Rule noEventTypeRule = createRuleWithDefaultAction(
            new Metadata(TEST_SCOPE, "no-event-type-rule", "No event type rule", "A rule with a simple condition but no event type matching"),
            builder.condition("eventPropertyCondition")
                .parameter("propertyName", "target.properties.pageInfo.language")
                .parameter("comparisonOperator", "equals")
                .parameter("propertyValue", "en")
                .build()
        );
        createAndWaitForRule(noEventTypeRule);

        Profile profile = new Profile(UUID.randomUUID().toString());
        Session session = new Session(UUID.randomUUID().toString(), profile, new Date(), TEST_SCOPE);
        Event viewEvent = generateViewEvent(session, profile);
        Set<Rule> matchingRules = rulesService.getMatchingRules(viewEvent);

        assertTrue("Simple rule should be matched", matchingRules.contains(simpleEventTypeRule));
        assertFalse("Complex rule should NOT be matched", matchingRules.contains(complexEventTypeRule));
        assertTrue("No event type rule should be matched", matchingRules.contains(noEventTypeRule));

        Event loginEvent = new Event(UUID.randomUUID().toString(), "login", session, profile, TEST_SCOPE, null, null, new Date());
        matchingRules = rulesService.getMatchingRules(loginEvent);
        assertTrue("Complex rule should be matched", matchingRules.contains(complexEventTypeRule));
        assertFalse("Simple rule should NOT be matched", matchingRules.contains(simpleEventTypeRule));

        rulesService.removeRule(simpleEventTypeRule.getItemId());
        rulesService.removeRule(complexEventTypeRule.getItemId());
        rulesService.removeRule(noEventTypeRule.getItemId());
        refreshPersistence(Rule.class);
        rulesService.refreshRules();
    }

    @Test
    public void testRuleOptimizationPerf() throws NoSuchFieldException, IllegalAccessException, IOException, InterruptedException {
        Profile profile = new Profile(UUID.randomUUID().toString());
        Session session = new Session(UUID.randomUUID().toString(), profile, new Date(), TEST_SCOPE);

        updateConfiguration(null, "org.apache.unomi.services", "rules.optimizationActivated", "false");
        rulesService = getService(RulesService.class);
        eventService = getService(EventService.class);

        LOGGER.info("Running unoptimized rules performance test...");
        long unoptimizedRunTime = runEventTest(profile, session);

        updateConfiguration(null, "org.apache.unomi.services", "rules.optimizationActivated", "true");
        rulesService = getService(RulesService.class);
        eventService = getService(EventService.class);

        LOGGER.info("Running optimized rules performance test...");
        long optimizedRunTime = runEventTest(profile, session);

        double improvementRatio = ((double) unoptimizedRunTime) / ((double) optimizedRunTime);
        LOGGER.info("Unoptimized run time = {}ms, optimized run time = {}ms. Improvement={}x", unoptimizedRunTime, optimizedRunTime, improvementRatio);

        String searchEngine = System.getProperty("org.apache.unomi.itests.searchEngine", "elasticsearch");
        // we check with a ratio of 0.7 because the test can sometimes fail due to the fact that the sample size is small and can be affected by
        // environmental issues such as CPU or I/O load, JVM warmup, garbage collection, etc.
        // The optimization may not always show improvement in a single test run, but should not be significantly worse
        if ("opensearch".equals(searchEngine)) {
            // OpenSearch may have different performance characteristics
            assertTrue("Optimized run time should not be significantly worse (ratio: " + improvementRatio + ")",
            improvementRatio > 0.7);
        } else {
            assertTrue("Optimized run time should not be significantly worse (ratio: " + improvementRatio + ")",
            improvementRatio > 0.7);
        }
    }

    private long runEventTest(Profile profile, Session session) {
        LOGGER.info("eventService={}", eventService);
        Event viewEvent = generateViewEvent(session, profile);
        int loopCount = 0;
        long startTime = System.currentTimeMillis();
        while (loopCount < 500) {
            eventService.send(viewEvent);
            viewEvent = generateViewEvent(session, profile);
            loopCount++;
        }
        return System.currentTimeMillis() - startTime;
    }

    private Event generateViewEvent(Session session, Profile profile) {
        CustomItem sourceItem = new CustomItem();
        sourceItem.setScope(TEST_SCOPE);

        CustomItem targetItem = new CustomItem();
        targetItem.setScope(TEST_SCOPE);
        Map<String,Object> targetProperties = new HashMap<>();

        Map<String,Object> pageInfo = new HashMap<>();
        pageInfo.put("language", "en");
        pageInfo.put("destinationURL", "https://www.acme.com/test-page.html");
        pageInfo.put("referringURL", "https://unomi.apache.org");
        pageInfo.put("pageID", "ITEM_ID_PAGE");
        pageInfo.put("pagePath", "/test-page.html");
        pageInfo.put("pageName", "Test page");

        targetProperties.put("pageInfo", pageInfo);

        targetItem.setProperties(targetProperties);
        return new Event(UUID.randomUUID().toString(), "view", session, profile, TEST_SCOPE, sourceItem, targetItem, new Date());
    }

    @Test
    public void testGetTrackedConditions() throws InterruptedException, IOException {
        // Add custom condition with parameter
        try {
            ConditionType conditionType = CustomObjectMapper.getObjectMapper().readValue(
                    new File("data/tmp/testClickEventCondition.json").toURI().toURL(), ConditionType.class);
            definitionsService.setConditionType(conditionType);
            refreshPersistence(Rule.class);
            rulesService.refreshRules();
            // Test tracked parameter
            // Add rule that has a trackParameter condition that matches
            ConditionBuilder builder = new ConditionBuilder(definitionsService);
            Condition trackedCondition = builder.condition("clickEventCondition").build();
            trackedCondition.setParameter("path", "/test-page.html");
            trackedCondition.setParameter("referrer", "https://unomi.apache.org");
            trackedCondition.getConditionType().getMetadata().getSystemTags().add("trackedCondition");
            Rule trackParameterRule = createRuleWithDefaultAction(
                new Metadata(TEST_SCOPE, "tracked-parameter-rule", "Tracked parameter rule", "A rule with tracked parameter"),
                trackedCondition
            );
            createAndWaitForRule(trackParameterRule);
            // Add rule that has a trackParameter condition that does not match
            Condition unTrackedCondition = builder.condition("clickEventCondition").build();
            unTrackedCondition.setParameter("path", "/test-page.html");
            unTrackedCondition.setParameter("referrer", "https://localhost");
            unTrackedCondition.getConditionType().getMetadata().getSystemTags().add("trackedCondition");
            Rule unTrackParameterRule = createRuleWithDefaultAction(
                new Metadata(TEST_SCOPE, "not-tracked-parameter-rule", "Not Tracked parameter rule", "A rule that has a parameter not tracked"),
                unTrackedCondition
            );
            createAndWaitForRule(unTrackParameterRule);
            // Check that the given event return the tracked condition
            Profile profile = new Profile(UUID.randomUUID().toString());
            Session session = new Session(UUID.randomUUID().toString(), profile, new Date(), TEST_SCOPE);
            Event viewEvent = generateViewEvent(session, profile);
            Set<Condition> trackedConditions = rulesService.getTrackedConditions(viewEvent.getTarget());
            Assert.assertTrue(trackedConditions.contains(trackedCondition));
            Assert.assertFalse(trackedConditions.contains(unTrackedCondition));
        } finally {
            // Clean up test data
            rulesService.removeRule("tracked-parameter-rule");
            rulesService.removeRule("not-tracked-parameter-rule");
            definitionsService.removeConditionType("clickEventCondition");
        }
    }

    @Override
    public void updateServices() throws InterruptedException {
        super.updateServices();
        rulesService = getService(RulesService.class);
        eventService = getService(EventService.class);
    }
}
