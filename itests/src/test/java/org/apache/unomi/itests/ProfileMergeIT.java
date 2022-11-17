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

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.services.RulesService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;

import javax.inject.Inject;
import java.util.*;

/**
 * Integration test for MergeProfilesOnPropertyAction
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ProfileMergeIT extends BaseIT {
    public static final String PERSONALIZATION_STRATEGY_STATUS = "personalizationStrategyStatus";
    public static final String PERSONALIZATION_STRATEGY_STATUS_ID = "personalizationId";
    public static final String PERSONALIZATION_STRATEGY_STATUS_IN_CTRL_GROUP = "inControlGroup";
    public static final String PERSONALIZATION_STRATEGY_STATUS_DATE = "timeStamp";

    @Inject @Filter(timeout = 600000)
    protected EventService eventService;
    @Inject @Filter(timeout = 600000)
    protected RulesService rulesService;
    @Inject @Filter(timeout = 600000)
    protected DefinitionsService definitionsService;
    @Inject @Filter(timeout = 600000)
    protected ProfileService profileService;

    private final static String TEST_EVENT_TYPE = "mergeProfileTestEventType";
    private final static String TEST_RULE_ID = "mergeOnPropertyTest";
    private final static String TEST_PROFILE_ID = "mergeOnPropertyTestProfileId";

    @After
    public void after() {
        // cleanup created data
        rulesService.removeRule(TEST_RULE_ID);
    }

    @Test
    public void testProfileMergeOnPropertyAction_dont_forceEventProfileAsMaster() throws InterruptedException {
        createAndWaitForRule(createMergeOnPropertyRule(false));

        // A new profile should be created.
        Assert.assertNotEquals(sendEvent().getProfile().getItemId(), TEST_PROFILE_ID);
    }

    @Test
    public void testProfileMergeOnPropertyAction_forceEventProfileAsMaster() throws InterruptedException {
        createAndWaitForRule(createMergeOnPropertyRule(true));

        // No new profile should be created, instead the profile of the event should be used.
        Assert.assertEquals(sendEvent().getProfile().getItemId(), TEST_PROFILE_ID);
    }

    @Test
    public void testPersonalizationStrategyStatusMerge() {
        // create some statuses for the tests:
        Map<String, Object> perso1true = buildPersonalizationStrategyStatus("perso-test-1", true);
        Map<String, Object> perso1false = buildPersonalizationStrategyStatus("perso-test-1", false);
        Map<String, Object> perso2false = buildPersonalizationStrategyStatus("perso-test-2", false);
        Map<String, Object> perso3true = buildPersonalizationStrategyStatus("perso-test-3", true);

        // create a single master profile we will keep along all the tests:
        Profile profileMaster = new Profile("master-profile");

        // merge test 1: Master do not have statuses, but slave have statuses
        // master have: NULL
        // slave have:  perso-test-1 -> true
        //              perso-test-2 -> false
        // Expected:    perso-test-1 -> true
        //              perso-test-2 -> false
        Profile profileSlave = new Profile("slave-profile");
        profileSlave.setSystemProperty(PERSONALIZATION_STRATEGY_STATUS, new ArrayList<>(Arrays.asList(perso1true, perso2false)));
        profileMaster = profileService.mergeProfiles(profileMaster, Collections.singletonList(profileSlave));
        assertPersonalizationStrategyStatus(profileMaster,  Arrays.asList(perso1true, perso2false));

        // merge test 2: Master do not have statuses, but slave have statuses
        // master have: perso-test-1 -> true
        //              perso-test-2 -> false
        // slave have:  NULL
        // Expected:    perso-test-1 -> true
        //              perso-test-2 -> false
        profileSlave = new Profile("slave-profile");
        profileMaster = profileService.mergeProfiles(profileMaster, Collections.singletonList(profileSlave));
        assertPersonalizationStrategyStatus(profileMaster, Arrays.asList(perso1true, perso2false));

        // merge test 3: both master and slave have strategy statuses and some are conflicting
        // master have: perso-test-1 -> true
        //              perso-test-2 -> false
        // slave have:  perso-test-1 -> false (conflicting)
        //              perso-test-3 -> true
        // Expected:    perso-test-1 -> true
        //              perso-test-2 -> false
        //              perso-test-3 -> true
        profileSlave = new Profile("slave-profile");
        profileSlave.setSystemProperty(PERSONALIZATION_STRATEGY_STATUS, new ArrayList<>(Arrays.asList(perso1false, perso3true)));
        profileMaster = profileService.mergeProfiles(profileMaster, Collections.singletonList(profileSlave));
        assertPersonalizationStrategyStatus(profileMaster,  Arrays.asList(perso1true, perso2false, perso3true));
    }

    private Map<String, Object> buildPersonalizationStrategyStatus(String persoId, boolean inControlGroup) {
        Map<String, Object> personalizationStrategyStatus = new HashMap<>();
        personalizationStrategyStatus.put(PERSONALIZATION_STRATEGY_STATUS_ID, persoId);
        personalizationStrategyStatus.put(PERSONALIZATION_STRATEGY_STATUS_DATE, new Date());
        personalizationStrategyStatus.put(PERSONALIZATION_STRATEGY_STATUS_IN_CTRL_GROUP, inControlGroup);
        return personalizationStrategyStatus;
    }

    private void assertPersonalizationStrategyStatus(Profile profile, List<Map<String, Object>> expectedStatuses) {
        List<Map<String, Object>> strategyStatuses = (List<Map<String, Object>>) profile.getSystemProperties().get(PERSONALIZATION_STRATEGY_STATUS);
        Assert.assertEquals("We didn't get the good number of expected personalization statuses on the given profile: " + profile.getItemId(),
                expectedStatuses.size(), strategyStatuses.size());
        for (Map<String, Object> expectedStatus : expectedStatuses) {
            Optional<Map<String, Object>> foundStatusOp = strategyStatuses
                    .stream()
                    .filter(strategyStatus -> strategyStatus.get(PERSONALIZATION_STRATEGY_STATUS_ID)
                            .equals(expectedStatus.get(PERSONALIZATION_STRATEGY_STATUS_ID)))
                    .findFirst();
            if (foundStatusOp.isPresent()) {
                Map<String, Object> foundStatus = foundStatusOp.get();
                Assert.assertEquals(expectedStatus.get(PERSONALIZATION_STRATEGY_STATUS_DATE), foundStatus.get(PERSONALIZATION_STRATEGY_STATUS_DATE));
                Assert.assertEquals(expectedStatus.get(PERSONALIZATION_STRATEGY_STATUS_IN_CTRL_GROUP), foundStatus.get(PERSONALIZATION_STRATEGY_STATUS_IN_CTRL_GROUP));
            } else {
                Assert.fail("We didn't found the expected personalization: " + expectedStatus.get(PERSONALIZATION_STRATEGY_STATUS_ID) + " status on the given profile: " + profile.getItemId());
            }
        }
    }
    private Event sendEvent() {
        Profile profile = new Profile();
        profile.setProperties(new HashMap<>());
        profile.setItemId(TEST_PROFILE_ID);
        profile.setProperty("j:nodename", "michel");
        profile.getSystemProperties().put("mergeIdentifier", "jose");
        Event testEvent = new Event(TEST_EVENT_TYPE, null, profile, null, null, profile, new Date());
        eventService.send(testEvent);
        return testEvent;
    }

    private Rule createMergeOnPropertyRule(boolean forceEventProfileAsMaster) throws InterruptedException {
        Rule mergeOnPropertyTestRule = new Rule();
        mergeOnPropertyTestRule.setMetadata(new Metadata(null, TEST_RULE_ID, TEST_RULE_ID, "Test rule for testing MergeProfilesOnPropertyAction"));

        Condition condition = new Condition(definitionsService.getConditionType("eventTypeCondition"));
        condition.setParameter("eventTypeId", TEST_EVENT_TYPE);
        mergeOnPropertyTestRule.setCondition(condition);

        final Action mergeProfilesOnPropertyAction = new Action( definitionsService.getActionType( "mergeProfilesOnPropertyAction"));
        mergeProfilesOnPropertyAction.setParameter("mergeProfilePropertyValue", "eventProperty::target.properties(j:nodename)");
        mergeProfilesOnPropertyAction.setParameter("mergeProfilePropertyName", "mergeIdentifier");
        mergeProfilesOnPropertyAction.setParameter("forceEventProfileAsMaster", forceEventProfileAsMaster);
        mergeOnPropertyTestRule.setActions(Collections.singletonList(mergeProfilesOnPropertyAction));

        return mergeOnPropertyTestRule;
    }
}
