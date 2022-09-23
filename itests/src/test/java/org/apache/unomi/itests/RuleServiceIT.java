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
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.RulesService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
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
        refreshPersistence();
        rulesService.refreshRules();
    }

    @Test
    public void getAllRulesShouldReturnAllRulesAvailable() throws InterruptedException {
        String ruleIDBase = "moreThan50RuleTest";
        int originalRulesNumber = rulesService.getAllRules().size();
        for (int i = 0; i < 60; i++) {
            String ruleID = ruleIDBase + "_" + i;
            Metadata metadata = new Metadata(ruleID);
            metadata.setName(ruleID);
            metadata.setDescription(ruleID);
            metadata.setScope(TEST_SCOPE);
            Rule nullRule = new Rule(metadata);
            nullRule.setCondition(null);
            nullRule.setActions(null);
            createAndWaitForRule(nullRule);
        }
        assertEquals("Expected getAllRules to be able to retrieve all the rules available in the system", originalRulesNumber + 60, rulesService.getAllRules().size());
        // cleanup
        for (int i = 0; i < 60; i++) {
            String ruleID = ruleIDBase + "_" + i;
            rulesService.removeRule(ruleID);
        }
        refreshPersistence();
        rulesService.refreshRules();
    }

    @Test
    public void testRuleEventTypeOptimization() throws InterruptedException {
        ConditionBuilder builder = new ConditionBuilder(definitionsService);
        Rule simpleEventTypeRule = new Rule(new Metadata(TEST_SCOPE, "simple-event-type-rule", "Simple event type rule", "A rule with a simple condition to match an event type"));
        simpleEventTypeRule.setCondition(builder.condition("eventTypeCondition").parameter("eventTypeId", "view").build());
        createAndWaitForRule(simpleEventTypeRule);
        Rule complexEventTypeRule = new Rule(new Metadata(TEST_SCOPE, "complex-event-type-rule", "Complex event type rule", "A rule with a complex condition to match multiple event types with negations"));
        complexEventTypeRule.setCondition(
                builder.not(
                        builder.or(
                                builder.condition("eventTypeCondition").parameter( "eventTypeId", "view"),
                                builder.condition("eventTypeCondition").parameter("eventTypeId", "form")
                        )
                ).build()
        );
        createAndWaitForRule(complexEventTypeRule);
        Rule noEventTypeRule = new Rule(new Metadata(TEST_SCOPE, "no-event-type-rule", "No event type rule", "A rule with a simple condition but no event type matching"));
        noEventTypeRule.setCondition(builder.condition("eventPropertyCondition")
                .parameter("propertyName", "target.properties.pageInfo.language")
                .parameter("comparisonOperator", "equals")
                .parameter("propertyValue", "en")
                .build());
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
        refreshPersistence();
        rulesService.refreshRules();
    }

    @Test
    public void testRuleOptimizationPerf() throws NoSuchFieldException, IllegalAccessException, IOException, InterruptedException {
        Profile profile = new Profile(UUID.randomUUID().toString());
        Session session = new Session(UUID.randomUUID().toString(), profile, new Date(), TEST_SCOPE);

        updateConfiguration(RulesService.class.getName(), "org.apache.unomi.services", "rules.optimizationActivated", "false");
        rulesService = getService(RulesService.class);
        eventService = getService(EventService.class);

        LOGGER.info("Running unoptimized rules performance test...");
        long unoptimizedRunTime = runEventTest(profile, session);

        updateConfiguration(RulesService.class.getName(), "org.apache.unomi.services", "rules.optimizationActivated", "true");
        rulesService = getService(RulesService.class);
        eventService = getService(EventService.class);

        LOGGER.info("Running optimized rules performance test...");
        long optimizedRunTime = runEventTest(profile, session);

        double improvementRatio = ((double) unoptimizedRunTime) / ((double) optimizedRunTime);
        LOGGER.info("Unoptimized run time = {}ms, optimized run time = {}ms. Improvement={}x", unoptimizedRunTime, optimizedRunTime, improvementRatio);
        // we check with a ratio of 0.9 because the test can sometimes fail due to the fact that the sample size is small and can be affected by
        // environmental issues such as CPU or I/O load.
        assertTrue("Optimized run time should be smaller than unoptimized", improvementRatio > 0.9);
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
            refreshPersistence();
            rulesService.refreshRules();
            // Test tracked parameter
            // Add rule that has a trackParameter condition that matches
            ConditionBuilder builder = new ConditionBuilder(definitionsService);
            Rule trackParameterRule = new Rule(new Metadata(TEST_SCOPE, "tracked-parameter-rule", "Tracked parameter rule", "A rule with tracked parameter"));
            Condition trackedCondition = builder.condition("clickEventCondition").build();
            trackedCondition.setParameter("path", "/test-page.html");
            trackedCondition.setParameter("referrer", "https://unomi.apache.org");
            trackedCondition.getConditionType().getMetadata().getSystemTags().add("trackedCondition");
            trackParameterRule.setCondition(trackedCondition);
            createAndWaitForRule(trackParameterRule);
            // Add rule that has a trackParameter condition that does not match
            Rule unTrackParameterRule = new Rule(new Metadata(TEST_SCOPE, "not-tracked-parameter-rule", "Not Tracked parameter rule", "A rule that has a parameter not tracked"));
            Condition unTrackedCondition = builder.condition("clickEventCondition").build();
            unTrackedCondition.setParameter("path", "/test-page.html");
            unTrackedCondition.setParameter("referrer", "https://localhost");
            unTrackedCondition.getConditionType().getMetadata().getSystemTags().add("trackedCondition");
            unTrackParameterRule.setCondition(unTrackedCondition);
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
