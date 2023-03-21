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
import org.apache.unomi.api.rules.Rule;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

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

    private final static String TEST_EVENT_TYPE = "mergeProfileTestEventType";
    private final static String TEST_RULE_ID = "mergeOnPropertyTest";
    private final static String TEST_PROFILE_ID = "mergeOnPropertyTestProfileId";

    @After
    public void after() throws InterruptedException {
        // cleanup created data
        rulesService.removeRule(TEST_RULE_ID);
        removeItems(Profile.class, ProfileAlias.class, Event.class, Session.class);
    }

    @Test
    public void testProfileMergeOnPropertyAction_dont_forceEventProfileAsMaster() throws InterruptedException {
        createAndWaitForRule(createMergeOnPropertyRule(false, "j:nodename"));

        // A new profile should be created.
        Assert.assertNotEquals(sendEvent().getProfile().getItemId(), TEST_PROFILE_ID);
    }

    @Test
    public void testProfileMergeOnPropertyAction_forceEventProfileAsMaster() throws InterruptedException {
        createAndWaitForRule(createMergeOnPropertyRule(true, "j:nodename"));

        // No new profile should be created, instead the profile of the event should be used.
        Assert.assertEquals(sendEvent().getProfile().getItemId(), TEST_PROFILE_ID);
    }

    @Test
    public void testProfileMergeOnPropertyAction_simpleMergeAndCheckAlias() throws InterruptedException {
        // create rule
        createAndWaitForRule(createMergeOnPropertyRule(false, "email"));

        // create master profile
        Profile masterProfile = new Profile();
        masterProfile.setItemId("masterProfileID");
        masterProfile.setProperty("email", "username@domain.com");
        masterProfile.setSystemProperty("mergeIdentifier", "username@domain.com");
        profileService.save(masterProfile);

        // create event profile
        Profile eventProfile = new Profile();
        eventProfile.setItemId("eventProfileID");
        eventProfile.setProperty("email", "username@domain.com");
        profileService.save(eventProfile);

        keepTrying("Profile with id masterProfileID not found in the required time", () -> profileService.load("masterProfileID"),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        keepTrying("Profile with id eventProfileID not found in the required time", () -> profileService.load("eventProfileID"),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        Event event = new Event(TEST_EVENT_TYPE, null, eventProfile, null, null, eventProfile, new Date());

        eventService.send(event);

        Assert.assertNotNull(event.getProfile());

        keepTrying("Profile with id masterProfileID not found in the required time",
                () -> persistenceService.getAllItems(ProfileAlias.class), (profileAliases) -> !profileAliases.isEmpty(),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        waitForNullValue("Profile with id eventProfileID not removed in the required time",
                () -> persistenceService.load("eventProfileID", Profile.class),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        keepTrying("Profile with id eventProfileID should still be accessible due to alias",
                () -> profileService.load("eventProfileID"), Objects::nonNull,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        List<ProfileAlias> aliases = persistenceService.query("profileID", masterProfile.getItemId(), null, ProfileAlias.class);

        Assert.assertFalse(aliases.isEmpty());
        Assert.assertEquals(masterProfile.getItemId(), aliases.get(0).getProfileID());
        Assert.assertEquals(eventProfile.getItemId(), aliases.get(0).getItemId());
        Assert.assertEquals("defaultClientId", aliases.get(0).getClientID());
    }


    /**
     * User switch case, this case can happen when a person (user A) is using the same browser session of a previous logged user (user B).
     * user A will be using user B profile, but when user A is going to login by send a merge event, then we will detect that the mergeIdentifier is not the same
     * In this case we will just switch user A profile to:
     * - a new one, if it's the first time we encounter his own mergeIdentifier (TESTED in this scenario)
     * - a previous one, if we already have a profile in DB with the same mergeIdentifier.
     */
    @Test
    public void testProfileMergeOnPropertyAction_sessionReassigned_newProfile() throws InterruptedException {
        // create rule
        createAndWaitForRule(createMergeOnPropertyRule(false, "email"));

        // create master profile
        Profile masterProfile = new Profile();
        masterProfile.setItemId("masterProfileID");
        masterProfile.setProperty("email", "master@domain.com");
        masterProfile.setSystemProperty("mergeIdentifier", "master@domain.com");
        profileService.save(masterProfile);

        keepTrying("Profile with id masterProfileID not found in the required time", () -> profileService.load("masterProfileID"),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // create event profile
        Profile eventProfile = new Profile();
        eventProfile.setItemId("eventProfileID");
        eventProfile.setProperty("email", "event@domain.com");

        Session simpleSession = new Session("simpleSession", eventProfile, new Date(), null);
        Event event = new Event(TEST_EVENT_TYPE, simpleSession, masterProfile, null, null, eventProfile, new Date());
        eventService.send(event);

        // Session should have been reassign and a new profile should have been created ! (We call this user switch case)
        Assert.assertNotNull(event.getProfile());
        Assert.assertNotEquals("eventProfileID", event.getProfile().getItemId());
        Assert.assertNotEquals("eventProfileID", event.getProfileId());
        Assert.assertNotEquals("eventProfileID", event.getSession().getProfile().getItemId());
        Assert.assertNotEquals("eventProfileID", event.getSession().getProfileId());

        Assert.assertNotEquals("masterProfileID", event.getProfile().getItemId());
        Assert.assertNotEquals("masterProfileID", event.getProfileId());
        Assert.assertNotEquals("masterProfileID", event.getSession().getProfile().getItemId());
        Assert.assertNotEquals("masterProfileID", event.getSession().getProfileId());

        Assert.assertEquals(event.getSession().getProfileId(), event.getProfileId());
        Assert.assertEquals("event@domain.com", event.getProfile().getSystemProperties().get("mergeIdentifier"));
    }

    /**
     * User switch case, this case can happen when a person (user A) is using the same browser session of a previous logged user (user B).
     * user A will be using user B profile, but when user A is going to login by send a merge event, then we will detect that the mergeIdentifier is not the same
     * In this case we will just switch user A profile to:
     * - a new one, if it's the first time we encounter his own mergeIdentifier
     * - a previous one, if we already have a profile in DB with the same mergeIdentifier. (TESTED in this scenario)
     */
    @Test
    public void testProfileMergeOnPropertyAction_sessionReassigned_existingProfile() throws InterruptedException {
        // create rule
        createAndWaitForRule(createMergeOnPropertyRule(false, "email"));

        // create master profile
        Profile masterProfile = new Profile();
        masterProfile.setItemId("masterProfileID");
        masterProfile.setProperty("email", "master@domain.com");
        masterProfile.setSystemProperty("mergeIdentifier", "master@domain.com");
        profileService.save(masterProfile);

        // create a previous existing profile with same mergeIdentifier
        Profile previousProfile = new Profile();
        previousProfile.setItemId("previousProfileID");
        previousProfile.setProperty("email", "event@domain.com");
        previousProfile.setSystemProperty("mergeIdentifier", "event@domain.com");
        profileService.save(previousProfile);

        keepTrying("Profile with id masterProfileID not found in the required time", () -> profileService.load("masterProfileID"),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        keepTrying("Profile with id previousProfileID not found in the required time", () -> profileService.load("previousProfileID"),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // create event profile
        Profile eventProfile = new Profile();
        eventProfile.setItemId("eventProfileID");
        eventProfile.setProperty("email", "event@domain.com");

        Session simpleSession = new Session("simpleSession", eventProfile, new Date(), null);
        Event event = new Event(TEST_EVENT_TYPE, simpleSession, masterProfile, null, null, eventProfile, new Date());
        eventService.send(event);

        // Session should have been reassign and the previous existing profile for mergeIdentifier: event@domain.com should have been reuse
        // Session should have been reassign and a new profile should have been created ! (We call this user switch case)
        Assert.assertNotNull(event.getProfile());
        Assert.assertEquals("previousProfileID", event.getProfile().getItemId());
        Assert.assertEquals("previousProfileID", event.getProfileId());
        Assert.assertEquals("previousProfileID", event.getSession().getProfile().getItemId());
        Assert.assertEquals("previousProfileID", event.getSession().getProfileId());

        Assert.assertEquals(event.getSession().getProfileId(), event.getProfileId());
        Assert.assertEquals("event@domain.com", event.getProfile().getSystemProperties().get("mergeIdentifier"));
    }

    /**
     * In case of merge, existing sessions/events from previous profileId should be rewritten to use the new master profileId
     */
    @Test
    public void testProfileMergeOnPropertyAction_rewriteExistingSessionsEvents() throws InterruptedException {
        Condition matchAll = new Condition(definitionsService.getConditionType("matchAllCondition"));
        // create rule
        createAndWaitForRule(createMergeOnPropertyRule(false, "email"));

        // create master profile
        Profile masterProfile = new Profile();
        masterProfile.setItemId("masterProfileID");
        masterProfile.setProperty("email", "username@domain.com");
        masterProfile.setSystemProperty("mergeIdentifier", "username@domain.com");
        profileService.save(masterProfile);

        Profile eventProfile = new Profile();
        eventProfile.setItemId("eventProfileID");
        eventProfile.setProperty("email", "username@domain.com");
        profileService.save(eventProfile);

        // create 5 past sessions and 5 past events.
        List<Session> sessionsToBeRewritten = new ArrayList<>();
        List<Event> eventsToBeRewritten = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Session sessionToBeRewritten = new Session("simpleSession_"+ i, eventProfile, new Date(), null);
            sessionsToBeRewritten.add(sessionToBeRewritten);
            Event eventToBeRewritten = new Event("view", sessionToBeRewritten, eventProfile, null, null, null, new Date());
            eventsToBeRewritten.add(eventToBeRewritten);


            persistenceService.save(sessionToBeRewritten);
            persistenceService.save(eventToBeRewritten);
        }
        for (Session session : sessionsToBeRewritten) {
            keepTrying("Wait for session: " + session.getItemId() + " to be indexed",
                    () -> persistenceService.query("itemId", session.getItemId(), null, Session.class),
                    (list) -> list.size() == 1, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        }
        for (Event event : eventsToBeRewritten) {
            keepTrying("Wait for event: " + event.getItemId() + " to be indexed",
                    () -> persistenceService.query("itemId", event.getItemId(), null, Event.class),
                    (list) -> list.size() == 1, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        }
        keepTrying("Profile with id masterProfileID not found in the required time", () -> profileService.load("masterProfileID"),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        keepTrying("Profile with id eventProfileID not found in the required time", () -> profileService.load("eventProfileID"),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // Trigger the merge
        Session simpleSession = new Session("simpleSession", eventProfile, new Date(), null);
        Event mergeEvent = new Event(TEST_EVENT_TYPE, simpleSession, eventProfile, null, null, eventProfile, new Date());
        eventService.send(mergeEvent);

        // Check that master profile is now used:
        Assert.assertNotNull(mergeEvent.getProfile());
        Assert.assertEquals("masterProfileID", mergeEvent.getProfile().getItemId());
        Assert.assertEquals("masterProfileID", mergeEvent.getProfileId());
        Assert.assertEquals("masterProfileID", mergeEvent.getSession().getProfile().getItemId());
        Assert.assertEquals("masterProfileID", mergeEvent.getSession().getProfileId());
        Assert.assertEquals(mergeEvent.getSession().getProfileId(), mergeEvent.getProfileId());
        Assert.assertEquals("username@domain.com", mergeEvent.getProfile().getSystemProperties().get("mergeIdentifier"));

        // Check events are correctly rewritten
        for (Event event : eventsToBeRewritten) {
            keepTrying("Wait for event: " + event.getItemId() + " profileId to be rewritten for masterProfileID",
                    () -> persistenceService.load(event.getItemId(), Event.class),
                    (loadedEvent) -> loadedEvent.getProfileId().equals("masterProfileID"), DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        }

        // Check sessions are correctly rewritten
        Condition sessionProfileIDRewrittenCondition = new Condition(definitionsService.getConditionType("sessionPropertyCondition"));
        sessionProfileIDRewrittenCondition.setParameter("propertyName","profileId");
        sessionProfileIDRewrittenCondition.setParameter("comparisonOperator","equals");
        sessionProfileIDRewrittenCondition.setParameter("propertyValue","masterProfileID");
        keepTrying("Wait for sessions profileId to be rewritten to masterProfileID",
                () -> persistenceService.queryCount(sessionProfileIDRewrittenCondition, Session.ITEM_TYPE),
                (count) -> count == 5, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        for (Session session : sessionsToBeRewritten) {
            keepTrying("Wait for session: " + session.getItemId() + " profileId to be rewritten for masterProfileID",
                    () -> persistenceService.load(session.getItemId(), Session.class),
                    (loadedSession) -> loadedSession.getProfileId().equals("masterProfileID"), DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        }
    }

    /**
     * If master profile is flagged as anonymous profile, then after the merge all past sessions/events should be anonymized
     */
    @Test
    public void testProfileMergeOnPropertyAction_rewriteExistingSessionsEventsAnonymous() throws InterruptedException {
        Condition matchAll = new Condition(definitionsService.getConditionType("matchAllCondition"));
        // create rule
        createAndWaitForRule(createMergeOnPropertyRule(false, "email"));

        // create master profile
        Profile masterProfile = new Profile();
        masterProfile.setItemId("masterProfileID");
        masterProfile.setProperty("email", "username@domain.com");
        masterProfile.setSystemProperty("mergeIdentifier", "username@domain.com");
        profileService.save(masterProfile);
        privacyService.setRequireAnonymousBrowsing(masterProfile.getItemId(), true, null);

        Profile eventProfile = new Profile();
        eventProfile.setItemId("eventProfileID");
        eventProfile.setProperty("email", "username@domain.com");
        profileService.save(eventProfile);

        // create 5 sessions and 5 events for master profile.
        List<Session> sessionsToBeRewritten = new ArrayList<>();
        List<Event> eventsToBeRewritten = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Session sessionToBeRewritten = new Session("simpleSession_"+ i, eventProfile, new Date(), null);
            sessionsToBeRewritten.add(sessionToBeRewritten);
            Event eventToBeRewritten = new Event("view", sessionToBeRewritten, eventProfile, null, null, null, new Date());
            eventsToBeRewritten.add(eventToBeRewritten);

            persistenceService.save(sessionToBeRewritten);
            persistenceService.save(eventToBeRewritten);
        }
        for (Session session : sessionsToBeRewritten) {
            keepTrying("Wait for session: " + session.getItemId() + " to be indexed",
                    () -> persistenceService.query("itemId", session.getItemId(), null, Session.class),
                    (list) -> list.size() == 1, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        }
        for (Event event : eventsToBeRewritten) {
            keepTrying("Wait for event: " + event.getItemId() + " to be indexed",
                    () -> persistenceService.query("itemId", event.getItemId(), null, Event.class),
                    (list) -> list.size() == 1, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        }
        keepTrying("Profile with id masterProfileID (should required anonymous browsing) not found in the required time",
                () -> profileService.load("masterProfileID"),
                profile -> profile != null && privacyService.isRequireAnonymousBrowsing(profile), DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        keepTrying("Profile with id eventProfileID not found in the required time", () -> profileService.load("eventProfileID"),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // Trigger the merge
        Session simpleSession = new Session("simpleSession", eventProfile, new Date(), null);
        Event mergeEvent = new Event(TEST_EVENT_TYPE, simpleSession, eventProfile, null, null, eventProfile, new Date());
        eventService.send(mergeEvent);

        // Check that master profile is now used, but anonymous browsing is respected:
        Assert.assertNotNull(mergeEvent.getProfile());
        Assert.assertEquals("masterProfileID", mergeEvent.getProfile().getItemId()); // We still have profile in the event
        Assert.assertNull(mergeEvent.getProfileId()); // But profileId prop is null due to anonymous browsing
        Assert.assertNull(mergeEvent.getSession().getProfile().getItemId()); // Same for the event session
        Assert.assertNull(mergeEvent.getSession().getProfileId());
        Assert.assertEquals(mergeEvent.getSession().getProfileId(), mergeEvent.getProfileId());
        Assert.assertEquals("username@domain.com", mergeEvent.getProfile().getSystemProperties().get("mergeIdentifier"));

        // Check events are correctly rewritten (Anonymous !)
        for (Event event : eventsToBeRewritten) {
            keepTrying("Wait for event: " + event.getItemId() + " profileId to be rewritten for NULL due to anonymous browsing",
                    () -> persistenceService.load(event.getItemId(), Event.class),
                    (loadedEvent) -> loadedEvent.getProfileId() == null, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        }

        // Check sessions are correctly rewritten (Anonymous !)
        for (Session session : sessionsToBeRewritten) {
            keepTrying("Wait for session: " + session.getItemId() + " profileId to be rewritten for NULL due to anonymous browsing",
                    () -> persistenceService.load(session.getItemId(), Session.class),
                    (loadedSession) -> loadedSession.getProfileId() == null, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        }
    }

    /**
     * Personalization strategy have a specific handling during the merge of two profiles
     * This test is here to ensure this specific behavior is correctly working.
     */
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

    private Rule createMergeOnPropertyRule(boolean forceEventProfileAsMaster, String eventProperty) throws InterruptedException {
        Rule mergeOnPropertyTestRule = new Rule();
        mergeOnPropertyTestRule
                .setMetadata(new Metadata(null, TEST_RULE_ID, TEST_RULE_ID, "Test rule for testing MergeProfilesOnPropertyAction"));

        Condition condition = new Condition(definitionsService.getConditionType("eventTypeCondition"));
        condition.setParameter("eventTypeId", TEST_EVENT_TYPE);
        mergeOnPropertyTestRule.setCondition(condition);

        final Action mergeProfilesOnPropertyAction = new Action(definitionsService.getActionType("mergeProfilesOnPropertyAction"));
        mergeProfilesOnPropertyAction.setParameter("mergeProfilePropertyValue", "eventProperty::target.properties(" + eventProperty + ")");
        mergeProfilesOnPropertyAction.setParameter("mergeProfilePropertyName", "mergeIdentifier");
        mergeProfilesOnPropertyAction.setParameter("forceEventProfileAsMaster", forceEventProfileAsMaster);
        mergeOnPropertyTestRule.setActions(Collections.singletonList(mergeProfilesOnPropertyAction));

        return mergeOnPropertyTestRule;
    }
}
