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
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.exceptions.BadSegmentConditionException;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.segments.Scoring;
import org.apache.unomi.api.segments.ScoringElement;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.services.EventService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class SegmentIT extends BaseIT {

    private final static Logger LOGGER = LoggerFactory.getLogger(SegmentIT.class);
    private final static String SEGMENT_ID = "test-segment-id-2";

    private final static String TEST_EVENT_TYPE = "testEventType";
    private final static String TEST_EVENT_TYPE_SCHEMA = "schemas/events/test-event-type.json";

    @Before
    public void setUp() throws InterruptedException {
        removeItems(Segment.class);
        removeItems(Scoring.class);

        // create schemas required for tests
        schemaService.saveSchema(resourceAsString(TEST_EVENT_TYPE_SCHEMA));
        keepTrying("Couldn't find json schemas",
                () -> schemaService.getInstalledJsonSchemaIds(),
                (schemaIds) -> (schemaIds.contains("https://unomi.apache.org/schemas/json/events/testEventType/1-0-0")),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

    }

    @After
    public void tearDown() throws InterruptedException {
        removeItems(Segment.class);
        removeItems(Scoring.class);
        removeItems(Profile.class);
        removeItems(Event.class);
    }

    @Test
    public void testSegments() {
        Assert.assertNotNull("Segment service should be available", segmentService);
        List<Metadata> segmentMetadatas = segmentService.getSegmentMetadatas(0, 50, null).getList();
        Assert.assertEquals("Segment metadata list should be empty", 0, segmentMetadatas.size());
        LOGGER.info("Retrieved " + segmentMetadatas.size() + " segment metadata entries");
    }

    @Test(expected = BadSegmentConditionException.class)
    public void testSegmentWithNullCondition() {
        Metadata segmentMetadata = new Metadata(SEGMENT_ID);
        Segment segment = new Segment();
        segment.setMetadata(segmentMetadata);
        segment.setCondition(null);

        segmentService.setSegmentDefinition(segment);
    }

    @Test
    public void testSegmentWithNullConditionButDisabled() {
        Metadata segmentMetadata = new Metadata(SEGMENT_ID);
        segmentMetadata.setEnabled(false);
        Segment segment = new Segment();
        segment.setMetadata(segmentMetadata);
        segment.setCondition(null);

        segmentService.setSegmentDefinition(segment);
        segmentService.removeSegmentDefinition(SEGMENT_ID, false);
    }

    @Test(expected = BadSegmentConditionException.class)
    public void testSegmentWithInValidCondition() {
        Metadata segmentMetadata = new Metadata(SEGMENT_ID);
        Segment segment = new Segment();
        segment.setMetadata(segmentMetadata);
        Condition condition = new Condition();
        condition.setParameter("param", "param value");
        condition.setConditionTypeId("fakeConditionId");
        segment.setCondition(condition);

        segmentService.setSegmentDefinition(segment);
    }

    @Test(expected = BadSegmentConditionException.class)
    public void testSegmentWithInvalidConditionParameterTypes() {
        Metadata segmentMetadata = new Metadata(SEGMENT_ID);
        Segment segment = new Segment(segmentMetadata);
        Condition segmentCondition = new Condition(definitionsService.getConditionType("pastEventCondition"));
        segmentCondition.setParameter("minimumEventCount", "2");
        segmentCondition.setParameter("numberOfDays", "10");
        Condition pastEventEventCondition = new Condition(definitionsService.getConditionType("eventTypeCondition"));
        pastEventEventCondition.setParameter("eventTypeId", "testEventType");
        segmentCondition.setParameter("eventCondition", pastEventEventCondition);
        segment.setCondition(segmentCondition);
        segmentService.setSegmentDefinition(segment);
    }

    @Test
    public void testSegmentWithValidCondition() {
        Metadata segmentMetadata = new Metadata(SEGMENT_ID);
        Segment segment = new Segment(segmentMetadata);
        Condition segmentCondition = new Condition(definitionsService.getConditionType("pastEventCondition"));
        segmentCondition.setParameter("minimumEventCount", 2);
        segmentCondition.setParameter("numberOfDays", 10);
        Condition pastEventEventCondition = new Condition(definitionsService.getConditionType("eventTypeCondition"));
        pastEventEventCondition.setParameter("eventTypeId", "testEventType");
        segmentCondition.setParameter("eventCondition", pastEventEventCondition);
        segment.setCondition(segmentCondition);
        segmentService.setSegmentDefinition(segment);

        segmentService.removeSegmentDefinition(SEGMENT_ID, false);
    }

    @Test
    public void testProfileEngagedSegmentAddedRemoved() throws InterruptedException {
        Condition segmentSearchCondition = new Condition();
        segmentSearchCondition.setConditionType(definitionsService.getConditionType("profilePropertyCondition"));
        segmentSearchCondition.setParameter("propertyName", "segments");
        segmentSearchCondition.setParameter("comparisonOperator", "equals");
        segmentSearchCondition.setParameter("propertyValue", "add-delete-segment-test");

        // create Profile
        Profile profile = new Profile();
        profile.setItemId("test_profile_id");
        profile.setProperty("age", 42);
        profileService.save(profile);
        persistenceService.refreshIndex(Profile.class, null);

        keepTrying("Profile should not be engaged in the segment yet", () -> persistenceService.query(segmentSearchCondition, null, Profile.class),
                profiles -> profiles.size() == 0, 1000, 20);

        // create the segment
        Metadata segmentMetadata = new Metadata("add-delete-segment-test");
        Segment segment = new Segment(segmentMetadata);
        Condition segmentCondition = new Condition(definitionsService.getConditionType("profilePropertyCondition"));
        segmentCondition.setParameter("propertyName", "properties.age");
        segmentCondition.setParameter("comparisonOperator", "exists");
        segment.setCondition(segmentCondition);
        segmentService.setSegmentDefinition(segment);

        // insure the profile that did the past event condition is correctly engaged in the segment.
        keepTrying("Profile should be engaged in the segment", () -> persistenceService.query(segmentSearchCondition, null, Profile.class),
                profiles -> profiles.size() == 1, 1000, 20);

        // delete the segment
        segmentService.removeSegmentDefinition("add-delete-segment-test", false);

        // insure the profile is not engaged anymore after segment deleted
        keepTrying("Profile should not be engaged in the segment anymore after the segment have been deleted", () -> persistenceService.query(segmentSearchCondition, null, Profile.class),
                profiles -> profiles.size() == 0, 1000, 20);
    }

    @Test
    public void testSegmentWithPastEventCondition() throws InterruptedException {
        // create Profile
        Profile profile = new Profile();
        profile.setItemId("test_profile_id");
        profileService.save(profile);
        persistenceService.refreshIndex(Profile.class, null); // wait for profile to be full persisted and index

        // send event for profile from a previous date (today -3 days)
        ZoneId defaultZoneId = ZoneId.systemDefault();
        LocalDate localDate = LocalDate.now().minusDays(3);
        Event testEvent = new Event("testEventType", null, profile, null, null, profile,
                Date.from(localDate.atStartOfDay(defaultZoneId).toInstant()));
        testEvent.setPersistent(true);
        int changes = eventService.send(testEvent);
        if ((changes & EventService.PROFILE_UPDATED) == EventService.PROFILE_UPDATED) {
            profileService.save(profile);
            persistenceService.refreshIndex(Profile.class, null);
        }
        persistenceService.refreshIndex(Event.class, testEvent.getTimeStamp()); // wait for event to be fully persisted and indexed

        // create the segment
        Metadata segmentMetadata = new Metadata("past-event-segment-test");
        Segment segment = new Segment(segmentMetadata);
        Condition segmentCondition = new Condition(definitionsService.getConditionType("pastEventCondition"));
        segmentCondition.setParameter("numberOfDays", 10);
        Condition pastEventEventCondition = new Condition(definitionsService.getConditionType("eventTypeCondition"));
        pastEventEventCondition.setParameter("eventTypeId", "testEventType");
        segmentCondition.setParameter("eventCondition", pastEventEventCondition);
        segment.setCondition(segmentCondition);
        segmentService.setSegmentDefinition(segment);

        // insure the profile that did the past event condition is correctly engaged in the segment.
        keepTrying("Profile should be engaged in the segment", () -> profileService.load("test_profile_id"),
                updatedProfile -> updatedProfile.getSegments().contains("past-event-segment-test"), 1000, 20);
    }

    @Test
    public void testSegmentWithNegativePastEventCondition() throws InterruptedException {
        // create Profile
        Profile profile = new Profile();
        profile.setItemId("test_profile_id");
        profileService.save(profile);
        persistenceService.refreshIndex(Profile.class, null); // wait for profile to be full persisted and index

        // create the negative past event condition segment
        Metadata segmentMetadata = new Metadata("negative-past-event-segment-test");
        Segment segment = new Segment(segmentMetadata);
        Condition segmentCondition = new Condition(definitionsService.getConditionType("pastEventCondition"));
        segmentCondition.setParameter("numberOfDays", 10);
        Condition pastEventEventCondition = new Condition(definitionsService.getConditionType("eventTypeCondition"));
        pastEventEventCondition.setParameter("eventTypeId", "negative-testEventType");
        segmentCondition.setParameter("eventCondition", pastEventEventCondition);
        segmentCondition.setParameter("operator", "eventsNotOccurred");
        segment.setCondition(segmentCondition);
        segmentService.setSegmentDefinition(segment);

        // insure that profile is correctly engaged in sement since there is no events yet.
        keepTrying("Profile should be engaged in the segment, there is no event for the past condition yet",
                () -> profileService.load("test_profile_id"),
                updatedProfile -> updatedProfile.getSegments().contains("negative-past-event-segment-test"), 1000, 20);

        // we load the profile so that we are sure that it contains the segments
        profile = profileService.load("test_profile_id");

        // send event for profile from a previous date (today -3 days)
        ZoneId defaultZoneId = ZoneId.systemDefault();
        LocalDate localDate = LocalDate.now().minusDays(3);
        Event testEvent = new Event("negative-testEventType", null, profile, null, null, profile,
                Date.from(localDate.atStartOfDay(defaultZoneId).toInstant()));
        testEvent.setPersistent(true);

        // wait for segment auto generated rule to be available
        keepTrying("The segment auto generated rule should be available to handle the test event",
                () -> rulesService.getMatchingRules(testEvent),
                rules -> rules.size() > 0, 1000, 20);

        // send the event
        int changes = eventService.send(testEvent);
        if ((changes & EventService.PROFILE_UPDATED) == EventService.PROFILE_UPDATED) {
            profileService.save(profile);
            persistenceService.refreshIndex(Profile.class, null);
        }
        persistenceService.refreshIndex(Event.class, testEvent.getTimeStamp()); // wait for event to be fully persisted and indexed

        // now Profile should be out of the segment since one event have been done and the past event is only valid for no events occurrences
        keepTrying("Profile should not be engaged in the segment anymore, it have a least one event now",
                () -> profileService.load("test_profile_id"),
                updatedProfile -> !updatedProfile.getSegments().contains("negative-past-event-segment-test"), 1000, 20);
    }

    @Test
    public void testSegmentPastEventRecalculation() throws Exception {
        // create Profile
        Profile profile = new Profile();
        profile.setItemId("test_profile_id");
        profileService.save(profile);
        persistenceService.refreshIndex(Profile.class, null); // wait for profile to be full persisted and index

        // create the segment
        Metadata segmentMetadata = new Metadata("past-event-segment-test");
        Segment segment = new Segment(segmentMetadata);
        Condition segmentCondition = new Condition(definitionsService.getConditionType("pastEventCondition"));
        segmentCondition.setParameter("numberOfDays", 10);
        Condition pastEventEventCondition = new Condition(definitionsService.getConditionType("eventTypeCondition"));
        pastEventEventCondition.setParameter("eventTypeId", "testEventType");
        segmentCondition.setParameter("eventCondition", pastEventEventCondition);
        segment.setCondition(segmentCondition);
        segmentService.setSegmentDefinition(segment);
        Thread.sleep(5000);

        profile = profileService.load("test_profile_id");
        // Persist the event (do not send it into the system so that it will not be processed by the rules)
        ZoneId defaultZoneId = ZoneId.systemDefault();
        LocalDate localDate = LocalDate.now().minusDays(3);
        Event testEvent = new Event("testEventType", null, profile, null, null, profile,
                Date.from(localDate.atStartOfDay(defaultZoneId).toInstant()));
        testEvent.setPersistent(true);
        persistenceService.save(testEvent, null, true);
        persistenceService.refreshIndex(Event.class, testEvent.getTimeStamp()); // wait for event to be fully persisted and indexed

        // insure the profile is not yet engaged since we directly saved the event in ES
        profile = profileService.load("test_profile_id");
        Assert.assertFalse("Profile should not be engaged in the segment", profile.getSegments().contains("past-event-segment-test"));

        // now recalculate the past event conditions
        segmentService.recalculatePastEventConditions();
        persistenceService.refreshIndex(Profile.class, null);
        keepTrying("Profile should be engaged in the segment", () -> profileService.load("test_profile_id"),
                updatedProfile -> updatedProfile.getSegments().contains("past-event-segment-test"), 1000, 20);

        // update the event to a date out of the past event condition
        removeItems(Event.class);
        localDate = LocalDate.now().minusDays(15);
        testEvent = new Event("testEventType", null, profile, null, null, profile,
                Date.from(localDate.atStartOfDay(defaultZoneId).toInstant()));
        persistenceService.save(testEvent);
        persistenceService.refreshIndex(Event.class, testEvent.getTimeStamp()); // wait for event to be fully persisted and indexed

        // now recalculate the past event conditions
        segmentService.recalculatePastEventConditions();
        persistenceService.refreshIndex(Profile.class, null);
        keepTrying("Profile should not be engaged in the segment anymore", () -> profileService.load("test_profile_id"),
                updatedProfile -> !updatedProfile.getSegments().contains("past-event-segment-test"), 1000, 20);
    }

    @Test
    public void testScoringWithPastEventCondition() throws InterruptedException {
        // create Profile
        Profile profile = new Profile();
        profile.setItemId("test_profile_id");
        profileService.save(profile);
        persistenceService.refreshIndex(Profile.class, null); // wait for profile to be full persisted and index

        // send event for profile from a previous date (today -3 days)
        ZoneId defaultZoneId = ZoneId.systemDefault();
        LocalDate localDate = LocalDate.now().minusDays(3);
        Event testEvent = new Event("testEventType", null, profile, null, null, profile,
                Date.from(localDate.atStartOfDay(defaultZoneId).toInstant()));
        testEvent.setPersistent(true);
        int changes = eventService.send(testEvent);
        if ((changes & EventService.PROFILE_UPDATED) == EventService.PROFILE_UPDATED) {
            profileService.save(profile);
            persistenceService.refreshIndex(Profile.class, null);
        }
        persistenceService.refreshIndex(Event.class, testEvent.getTimeStamp()); // wait for event to be fully persisted and indexed

        // create the past event condition
        Condition pastEventCondition = new Condition(definitionsService.getConditionType("pastEventCondition"));
        pastEventCondition.setParameter("numberOfDays", 10);
        Condition pastEventEventCondition = new Condition(definitionsService.getConditionType("eventTypeCondition"));
        pastEventEventCondition.setParameter("eventTypeId", "testEventType");
        pastEventCondition.setParameter("eventCondition", pastEventEventCondition);

        // create the scoring plan
        Metadata scoringMetadata = new Metadata("past-event-scoring-test");
        Scoring scoring = new Scoring(scoringMetadata);
        List<ScoringElement> scoringElements = new ArrayList<>();
        ScoringElement scoringElement = new ScoringElement();
        scoringElement.setCondition(pastEventCondition);
        scoringElement.setValue(50);
        scoringElements.add(scoringElement);
        scoring.setElements(scoringElements);
        segmentService.setScoringDefinition(scoring);

        // insure the profile that did the past event condition is correctly engaged in the scoring plan.
        keepTrying("Profile should be engaged in the scoring with a score of 50", () -> profileService.load("test_profile_id"),
                updatedProfile -> updatedProfile.getScores() != null && updatedProfile.getScores().containsKey("past-event-scoring-test")
                        && updatedProfile.getScores().get("past-event-scoring-test") == 50, 1000, 20);
    }

    @Test
    public void testScoringPastEventRecalculation() throws Exception {
        // create Profile
        Profile profile = new Profile();
        profile.setItemId("test_profile_id");
        profileService.save(profile);
        persistenceService.refreshIndex(Profile.class, null); // wait for profile to be full persisted and index

        // create the past event condition
        Condition pastEventCondition = new Condition(definitionsService.getConditionType("pastEventCondition"));
        pastEventCondition.setParameter("numberOfDays", 10);
        Condition pastEventEventCondition = new Condition(definitionsService.getConditionType("eventTypeCondition"));
        pastEventEventCondition.setParameter("eventTypeId", "testEventType");
        pastEventCondition.setParameter("eventCondition", pastEventEventCondition);

        // create the scoring
        Metadata scoringMetadata = new Metadata("past-event-scoring-test");
        Scoring scoring = new Scoring(scoringMetadata);
        List<ScoringElement> scoringElements = new ArrayList<>();
        ScoringElement scoringElement = new ScoringElement();
        scoringElement.setCondition(pastEventCondition);
        scoringElement.setValue(50);
        scoringElements.add(scoringElement);
        scoring.setElements(scoringElements);
        segmentService.setScoringDefinition(scoring);
        Thread.sleep(5000);

        // Persist the event (do not send it into the system so that it will not be processed by the rules)
        ZoneId defaultZoneId = ZoneId.systemDefault();
        LocalDate localDate = LocalDate.now().minusDays(3);
        Event testEvent = new Event("testEventType", null, profile, null, null, profile,
                Date.from(localDate.atStartOfDay(defaultZoneId).toInstant()));
        testEvent.setPersistent(true);
        persistenceService.save(testEvent, null, true);
        persistenceService.refreshIndex(Event.class, testEvent.getTimeStamp()); // wait for event to be fully persisted and indexed

        // insure the profile is not yet engaged since we directly saved the event in ES
        profile = profileService.load("test_profile_id");
        Assert.assertTrue("Profile should not be engaged in the scoring",
                profile.getScores() == null || !profile.getScores().containsKey("past-event-scoring-test"));

        // now recalculate the past event conditions
        segmentService.recalculatePastEventConditions();
        persistenceService.refreshIndex(Profile.class, null);
        keepTrying("Profile should be engaged in the scoring with a score of 50", () -> profileService.load("test_profile_id"),
                updatedProfile -> updatedProfile.getScores() != null && updatedProfile.getScores().containsKey("past-event-scoring-test")
                        && updatedProfile.getScores().get("past-event-scoring-test") == 50, 1000, 20);

        // update the event to a date out of the past event condition
        removeItems(Event.class);
        localDate = LocalDate.now().minusDays(15);
        testEvent = new Event("testEventType", null, profile, null, null, profile,
                Date.from(localDate.atStartOfDay(defaultZoneId).toInstant()));
        persistenceService.save(testEvent);
        persistenceService.refreshIndex(Event.class, testEvent.getTimeStamp()); // wait for event to be fully persisted and indexed

        // now recalculate the past event conditions
        segmentService.recalculatePastEventConditions();
        persistenceService.refreshIndex(Profile.class, null);
        keepTrying("Profile should not be engaged in the scoring anymore", () -> profileService.load("test_profile_id"),
                updatedProfile -> !updatedProfile.getScores().containsKey("past-event-scoring-test"), 1000, 20);
    }

    @Test
    public void testScoringPastEventRecalculationMaximumEventCount() throws Exception {
        // create Profile
        Profile profile = new Profile();
        profile.setItemId("test_profile_id");
        profileService.save(profile);
        persistenceService.refreshIndex(Profile.class, null); // wait for profile to be full persisted and index

        // create the past event condition
        Condition pastEventCondition = new Condition(definitionsService.getConditionType("pastEventCondition"));
        pastEventCondition.setParameter("numberOfDays", 10);
        Condition pastEventEventCondition = new Condition(definitionsService.getConditionType("eventTypeCondition"));
        pastEventEventCondition.setParameter("eventTypeId", "testEventType-max");
        pastEventCondition.setParameter("eventCondition", pastEventEventCondition);
        pastEventCondition.setParameter("maximumEventCount", 1);

        // create the scoring
        Metadata scoringMetadata = new Metadata("past-event-scoring-test-max");
        Scoring scoring = new Scoring(scoringMetadata);
        List<ScoringElement> scoringElements = new ArrayList<>();
        ScoringElement scoringElement = new ScoringElement();
        scoringElement.setCondition(pastEventCondition);
        scoringElement.setValue(50);
        scoringElements.add(scoringElement);
        scoring.setElements(scoringElements);
        segmentService.setScoringDefinition(scoring);
        Thread.sleep(5000);

        // Persist the event (do not send it into the system so that it will not be processed by the rules)
        ZoneId defaultZoneId = ZoneId.systemDefault();
        LocalDate localDate = LocalDate.now().minusDays(3);
        Event testEvent = new Event("testEventType-max", null, profile, null, null, profile,
                Date.from(localDate.atStartOfDay(defaultZoneId).toInstant()));
        testEvent.setPersistent(true);
        persistenceService.save(testEvent, null, true);
        persistenceService.refreshIndex(Event.class, testEvent.getTimeStamp()); // wait for event to be fully persisted and indexed

        // insure the profile is not yet engaged since we directly saved the event in ES
        profile = profileService.load("test_profile_id");
        Assert.assertTrue("Profile should not be engaged in the scoring",
                profile.getScores() == null || !profile.getScores().containsKey("past-event-scoring-test-max"));

        // now recalculate the past event conditions
        segmentService.recalculatePastEventConditions();
        persistenceService.refreshIndex(Profile.class, null);
        keepTrying("Profile should be engaged in the scoring with a score of 50", () -> profileService.load("test_profile_id"),
                updatedProfile -> updatedProfile.getScores() != null && updatedProfile.getScores()
                        .containsKey("past-event-scoring-test-max") && updatedProfile.getScores().get("past-event-scoring-test-max") == 50,
                1000, 20);

        // Persist the 2 event (do not send it into the system so that it will not be processed by the rules)
        defaultZoneId = ZoneId.systemDefault();
        localDate = LocalDate.now().minusDays(3);
        testEvent = new Event("testEventType-max", null, profile, null, null, profile,
                Date.from(localDate.atStartOfDay(defaultZoneId).toInstant()));
        testEvent.setPersistent(true);
        persistenceService.save(testEvent, null, true);
        persistenceService.refreshIndex(Event.class, testEvent.getTimeStamp()); // wait for event to be fully persisted and indexed

        // now recalculate the past event conditions
        segmentService.recalculatePastEventConditions();
        persistenceService.refreshIndex(Profile.class, null);
        keepTrying("Profile should not be engaged in the scoring anymore", () -> profileService.load("test_profile_id"),
                updatedProfile -> !updatedProfile.getScores().containsKey("past-event-scoring-test-max"), 1000, 20);
    }

    @Test
    public void testScoringRecalculation() throws Exception {
        // create Profile
        Profile profile = new Profile();
        profile.setItemId("test_profile_id");
        profileService.save(profile);
        persistenceService.refreshIndex(Profile.class, null); // wait for profile to be full persisted and index

        Date timestampEventInRange = new SimpleDateFormat("yyyy-MM-dd").parse("2000-10-30");
        // create the past event condition
        Condition pastEventCondition = new Condition(definitionsService.getConditionType("pastEventCondition"));
        pastEventCondition.setParameter("minimumEventCount", 1);
        pastEventCondition.setParameter("maximumEventCount", 2);

        pastEventCondition.setParameter("fromDate", "2000-07-15T07:00:00Z");
        pastEventCondition.setParameter("toDate", "2001-01-15T07:00:00Z");
        ;
        Condition pastEventEventCondition = new Condition(definitionsService.getConditionType("eventTypeCondition"));
        pastEventEventCondition.setParameter("eventTypeId", "testEventType");
        pastEventCondition.setParameter("eventCondition", pastEventEventCondition);

        // create the scoring
        Metadata scoringMetadata = new Metadata("past-event-scoring-test");
        Scoring scoring = new Scoring(scoringMetadata);
        List<ScoringElement> scoringElements = new ArrayList<>();
        ScoringElement scoringElement = new ScoringElement();
        scoringElement.setCondition(pastEventCondition);
        scoringElement.setValue(50);
        scoringElements.add(scoringElement);
        scoring.setElements(scoringElements);
        segmentService.setScoringDefinition(scoring);
        refreshPersistence(Segment.class);

        // Send 2 events that match the scoring plan.
        profile = profileService.load("test_profile_id");
        Event testEvent = new Event("testEventType", null, profile, null, null, profile, timestampEventInRange);
        testEvent.setPersistent(true);
        eventService.send(testEvent);
        refreshPersistence(Event.class);
        // 2nd event
        testEvent = new Event("testEventType", null, testEvent.getProfile(), null, null, testEvent.getProfile(), timestampEventInRange);
        eventService.send(testEvent);
        refreshPersistence(Event.class, Profile.class);

        // insure the profile is engaged;
        try {
            Map<String, Object> pastEvent = ((List<Map<String, Object>>)testEvent.getProfile().getSystemProperties().get("pastEvents")).stream().filter(profilePastEvent -> profilePastEvent.get("key").equals(pastEventCondition.getParameterValues().get("generatedPropertyKey"))).findFirst().get();
            Assert.assertEquals("Profile should have 2 events in the scoring", 2, (long) pastEvent.get("count"));
            Assert.assertTrue("Profile is engaged", testEvent.getProfile().getScores().containsKey("past-event-scoring-test")
                    && testEvent.getProfile().getScores().get("past-event-scoring-test") == 50);
        } catch (Exception e) {
            Assert.fail("Unable to read past event because " + e.getMessage());
        }
        profileService.save(testEvent.getProfile());
        refreshPersistence(Profile.class);
        // recalculate event conditions
        segmentService.recalculatePastEventConditions();
        // insure the profile is still engaged after recalculate;
        keepTrying("Profile should have 2 events in the scoring", () -> profileService.load("test_profile_id"), updatedProfile -> {
            try {
                Map<String, Object> pastEvent = ((List<Map<String, Object>>)updatedProfile.getSystemProperties().get("pastEvents")).stream().filter(profilePastEvent -> profilePastEvent.get("key").equals(pastEventCondition.getParameterValues().get("generatedPropertyKey"))).findFirst().get();

                boolean eventCounted = (Integer) pastEvent.get("count") == 2;
                boolean profileEngaged = updatedProfile.getScores().containsKey("past-event-scoring-test")
                        && updatedProfile.getScores().get("past-event-scoring-test") == 50;
                return eventCounted && profileEngaged;
            } catch (Exception e) {
                // Do nothing, unable to read value
            }
            return false;
        }, 1000, 20);

        // Add one more event
        testEvent = new Event("testEventType", null, testEvent.getProfile(), null, null, testEvent.getProfile(), timestampEventInRange);
        eventService.send(testEvent);

        // As 3 events have match, the profile should not be part of the scoring plan.
        try {
            Assert.assertTrue("Profile should have no scoring", testEvent.getProfile().getScores().get("past-event-scoring-test") == 0);
        } catch (Exception e) {
            Assert.fail("Unable to read past event because " + e.getMessage());
        }
        profileService.save(testEvent.getProfile());
        refreshPersistence(Profile.class);
        // now recalculate the past event conditions
        segmentService.recalculatePastEventConditions();
        refreshPersistence(Profile.class);
        // As 3 events have match, the profile should not be part of the scoring plan.
        keepTrying("Profile should not be part of the scoring anymore", () -> profileService.load("test_profile_id"), updatedProfile -> {
            try {
                return (updatedProfile.getScores().get("past-event-scoring-test") == null) ||
                        (updatedProfile.getScores().get("past-event-scoring-test") == 0);
            } catch (Exception e) {
                // Do nothing, unable to read value
            }
            ;
            return false;
        }, 1000, 20);
    }

    @Test
    public void testLinkedItems() throws Exception {

        // create the past event condition
        Condition pastEventCondition = new Condition(definitionsService.getConditionType("pastEventCondition"));
        pastEventCondition.setParameter("minimumEventCount", 1);
        pastEventCondition.setParameter("maximumEventCount", 2);

        pastEventCondition.setParameter("fromDate", "2000-07-15T07:00:00Z");
        pastEventCondition.setParameter("toDate", "2001-01-15T07:00:00Z");
        Condition pastEventEventCondition = new Condition(definitionsService.getConditionType("eventTypeCondition"));
        pastEventEventCondition.setParameter("eventTypeId", "testEventType");
        pastEventCondition.setParameter("eventCondition", pastEventEventCondition);

        // create the scoring
        Metadata scoringMetadata = new Metadata("past-event-scoring-test");
        Scoring scoring = new Scoring(scoringMetadata);
        List<ScoringElement> scoringElements = new ArrayList<>();
        ScoringElement scoringElement = new ScoringElement();
        scoringElement.setCondition(pastEventCondition);
        scoringElement.setValue(50);
        scoringElements.add(scoringElement);
        scoring.setElements(scoringElements);
        segmentService.setScoringDefinition(scoring);
        refreshPersistence(Segment.class);
        // Check linkedItems
        List<Rule> rules = persistenceService.getAllItems(Rule.class);
        Rule scoringRule = rules.stream().filter(rule -> rule.getItemId().equals(pastEventCondition.getParameter("generatedPropertyKey")))
                .findFirst().get();
        Assert.assertEquals("Scoring linked Item should be one", 1, scoringRule.getLinkedItems().size());

        // save the scoring once again
        segmentService.setScoringDefinition(scoring);
        refreshPersistence(Segment.class);
        // Check linkedItems
        rules = persistenceService.getAllItems(Rule.class);
        scoringRule = rules.stream().filter(rule -> rule.getItemId().equals(pastEventCondition.getParameter("generatedPropertyKey")))
                .findFirst().get();
        Assert.assertEquals("Scoring linked Item should be one", 1, scoringRule.getLinkedItems().size());

        // Remove scoring
        segmentService.removeSegmentDefinition(scoring.getItemId(), true);
        refreshPersistence(Segment.class);
        // Check linkedItems
        rules = persistenceService.getAllItems(Rule.class);
        boolean isRule = rules.stream().anyMatch(rule -> rule.getItemId().equals(pastEventCondition.getParameter("generatedPropertyKey")));
        Assert.assertFalse("Rule is properly removed", isRule);
    }

    @Test
    public void testSegmentWithRelativeDateExpressions() throws Exception {
        // create Profile
        Profile profile = new Profile();
        profile.setItemId("test_profile_id");
        profileService.save(profile);
        persistenceService.refreshIndex(Profile.class, null); // wait for profile to be full persisted and index

        // create the conditions
        Condition booleanCondition = new Condition(definitionsService.getConditionType("booleanCondition"));
        List<Condition> subConditions = new ArrayList<>();
        Condition dateExpCondition = new Condition(definitionsService.getConditionType("profilePropertyCondition"));
        dateExpCondition.setParameter("propertyName", "properties.lastVisit");
        dateExpCondition.setParameter("comparisonOperator", "greaterThanOrEqualTo");
        dateExpCondition.setParameter("propertyValueDateExpr", "now-5d");
        subConditions.add(dateExpCondition);
        Condition otherCondition = new Condition(definitionsService.getConditionType("profilePropertyCondition"));
        otherCondition.setParameter("propertyName", "properties.address");
        otherCondition.setParameter("comparisonOperator", "notEquals");
        otherCondition.setParameter("propertyValueDateExpr", "test");
        subConditions.add(otherCondition);
        booleanCondition.setParameter("operator", "and");
        booleanCondition.setParameter("subConditions", subConditions);

        // create segment and scoring
        Metadata segmentMetadata = new Metadata("relative-date-segment-test");
        Segment segment = new Segment(segmentMetadata);
        segment.setCondition(booleanCondition);
        segmentService.setSegmentDefinition(segment);
        Metadata scoringMetadata = new Metadata("relative-date-scoring-test");
        Scoring scoring = new Scoring(scoringMetadata);
        ScoringElement scoringElement = new ScoringElement();
        scoringElement.setCondition(booleanCondition);
        scoringElement.setValue(5);
        scoring.setElements(Collections.singletonList(scoringElement));
        segmentService.setScoringDefinition(scoring);
        Thread.sleep(5000);

        // insure the profile is not yet engaged since we directly saved the profile in ES
        profile = profileService.load("test_profile_id");
        Assert.assertFalse("Profile should not be engaged in the segment", profile.getSegments().contains("relative-date-segment-test"));
        Assert.assertTrue("Profile should not be engaged in the scoring",
                profile.getScores() == null || !profile.getScores().containsKey("relative-date-scoring-test"));

        // Update the profile last visit to match the segment ans the scoring
        ZoneId defaultZoneId = ZoneId.systemDefault();
        LocalDate localDate = LocalDate.now().minusDays(3);
        profile.setProperty("lastVisit", Date.from(localDate.atStartOfDay(defaultZoneId).toInstant()));
        profileService.save(profile);
        persistenceService.refreshIndex(Profile.class, null); // wait for profile to be full persisted and index

        // insure the profile is not yet engaged since we directly saved the profile in ES
        profile = profileService.load("test_profile_id");
        Assert.assertFalse("Profile should not be engaged in the segment", profile.getSegments().contains("relative-date-segment-test"));
        Assert.assertTrue("Profile should not be engaged in the scoring",
                profile.getScores() == null || profile.getScores().containsKey("relative-date-scoring-test"));

        // now force the recalculation of the date relative segments/scorings
        segmentService.recalculatePastEventConditions();
        persistenceService.refreshIndex(Profile.class, null);
        keepTrying("Profile should be engaged in the segment and scoring", () -> profileService.load("test_profile_id"),
                updatedProfile -> updatedProfile.getSegments().contains("relative-date-segment-test") && updatedProfile.getScores() != null
                        && updatedProfile.getScores().get("relative-date-scoring-test") == 5, 1000, 20);

        // update the profile to a date out of date expression
        localDate = LocalDate.now().minusDays(15);
        profile.setProperty("lastVisit", Date.from(localDate.atStartOfDay(defaultZoneId).toInstant()));
        profileService.save(profile);
        persistenceService.refreshIndex(Profile.class, null); // wait for profile to be full persisted and index

        // now force the recalculation of the date relative segments/scorings
        segmentService.recalculatePastEventConditions();
        persistenceService.refreshIndex(Profile.class, null);
        keepTrying("Profile should not be engaged in the segment and scoring anymore", () -> profileService.load("test_profile_id"),
                updatedProfile -> !updatedProfile.getSegments().contains("relative-date-segment-test") && (
                        updatedProfile.getScores() == null || !updatedProfile.getScores().containsKey("relative-date-scoring-test")), 1000,
                20);
    }
}
