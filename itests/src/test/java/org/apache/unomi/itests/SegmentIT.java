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
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.segments.Scoring;
import org.apache.unomi.api.segments.ScoringElement;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.api.exceptions.BadSegmentConditionException;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.After;
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
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class SegmentIT extends BaseIT {
    private final static Logger LOGGER = LoggerFactory.getLogger(SegmentIT.class);
    private final static String SEGMENT_ID = "test-segment-id-2";

    @Inject @Filter(timeout = 600000)
    protected SegmentService segmentService;

    @Inject @Filter(timeout = 600000)
    protected ProfileService profileService;

    @Inject @Filter(timeout = 600000)
    protected EventService eventService;

    @Inject @Filter(timeout = 600000)
    protected PersistenceService persistenceService;

    @Before
    public void setUp() throws InterruptedException {
        removeItems(Segment.class);
        removeItems(Scoring.class);
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
        pastEventEventCondition.setParameter("eventTypeId", "test-event-type");
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
        pastEventEventCondition.setParameter("eventTypeId", "test-event-type");
        segmentCondition.setParameter("eventCondition", pastEventEventCondition);
        segment.setCondition(segmentCondition);
        segmentService.setSegmentDefinition(segment);

        segmentService.removeSegmentDefinition(SEGMENT_ID, false);
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
        Event testEvent = new Event("test-event-type", null, profile, null, null, profile, Date.from(localDate.atStartOfDay(defaultZoneId).toInstant()));
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
        pastEventEventCondition.setParameter("eventTypeId", "test-event-type");
        segmentCondition.setParameter("eventCondition", pastEventEventCondition);
        segment.setCondition(segmentCondition);
        segmentService.setSegmentDefinition(segment);

        // insure the profile that did the past event condition is correctly engaged in the segment.
        keepTrying("Profile should be engaged in the segment",
                () -> profileService.load("test_profile_id"),
                updatedProfile -> updatedProfile.getSegments().contains("past-event-segment-test"),
                1000, 20);
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
        pastEventEventCondition.setParameter("eventTypeId", "test-event-type");
        segmentCondition.setParameter("eventCondition", pastEventEventCondition);
        segment.setCondition(segmentCondition);
        segmentService.setSegmentDefinition(segment);
        Thread.sleep(5000);

        // Persist the event (do not send it into the system so that it will not be processed by the rules)
        ZoneId defaultZoneId = ZoneId.systemDefault();
        LocalDate localDate = LocalDate.now().minusDays(3);
        Event testEvent = new Event("test-event-type", null, profile, null, null, profile, Date.from(localDate.atStartOfDay(defaultZoneId).toInstant()));
        testEvent.setPersistent(true);
        persistenceService.save(testEvent, null, true);
        persistenceService.refreshIndex(Event.class, testEvent.getTimeStamp()); // wait for event to be fully persisted and indexed

        // insure the profile is not yet engaged since we directly saved the event in ES
        profile = profileService.load("test_profile_id");
        Assert.assertFalse("Profile should not be engaged in the segment", profile.getSegments().contains("past-event-segment-test"));

        // now recalculate the past event conditions
        segmentService.recalculatePastEventConditions();
        persistenceService.refreshIndex(Profile.class, null);
        keepTrying("Profile should be engaged in the segment",
                () -> profileService.load("test_profile_id"),
                updatedProfile -> updatedProfile.getSegments().contains("past-event-segment-test"),
                1000, 20);

        // update the event to a date out of the past event condition
        removeItems(Event.class);
        localDate = LocalDate.now().minusDays(15);
        testEvent = new Event("test-event-type", null, profile, null, null, profile, Date.from(localDate.atStartOfDay(defaultZoneId).toInstant()));
        persistenceService.save(testEvent);
        persistenceService.refreshIndex(Event.class, testEvent.getTimeStamp()); // wait for event to be fully persisted and indexed

        // now recalculate the past event conditions
        segmentService.recalculatePastEventConditions();
        persistenceService.refreshIndex(Profile.class, null);
        keepTrying("Profile should not be engaged in the segment anymore",
                () -> profileService.load("test_profile_id"),
                updatedProfile -> !updatedProfile.getSegments().contains("past-event-segment-test"),
                1000, 20);
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
        Event testEvent = new Event("test-event-type", null, profile, null, null, profile, Date.from(localDate.atStartOfDay(defaultZoneId).toInstant()));
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
        pastEventEventCondition.setParameter("eventTypeId", "test-event-type");
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
        keepTrying("Profile should be engaged in the scoring with a score of 50",
                () -> profileService.load("test_profile_id"),
                updatedProfile -> updatedProfile.getScores() != null &&
                        updatedProfile.getScores().containsKey("past-event-scoring-test") &&
                        updatedProfile.getScores().get("past-event-scoring-test") == 50,
                1000, 20);
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
        pastEventEventCondition.setParameter("eventTypeId", "test-event-type");
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
        Event testEvent = new Event("test-event-type", null, profile, null, null, profile, Date.from(localDate.atStartOfDay(defaultZoneId).toInstant()));
        testEvent.setPersistent(true);
        persistenceService.save(testEvent, null, true);
        persistenceService.refreshIndex(Event.class, testEvent.getTimeStamp()); // wait for event to be fully persisted and indexed

        // insure the profile is not yet engaged since we directly saved the event in ES
        profile = profileService.load("test_profile_id");
        Assert.assertTrue("Profile should not be engaged in the scoring", profile.getScores() == null || !profile.getScores().containsKey("past-event-scoring-test"));

        // now recalculate the past event conditions
        segmentService.recalculatePastEventConditions();
        persistenceService.refreshIndex(Profile.class, null);
        keepTrying("Profile should be engaged in the scoring with a score of 50",
                () -> profileService.load("test_profile_id"),
                updatedProfile -> updatedProfile.getScores() != null &&
                        updatedProfile.getScores().containsKey("past-event-scoring-test") &&
                        updatedProfile.getScores().get("past-event-scoring-test") == 50,
                1000, 20);

        // update the event to a date out of the past event condition
        removeItems(Event.class);
        localDate = LocalDate.now().minusDays(15);
        testEvent = new Event("test-event-type", null, profile, null, null, profile, Date.from(localDate.atStartOfDay(defaultZoneId).toInstant()));
        persistenceService.save(testEvent);
        persistenceService.refreshIndex(Event.class, testEvent.getTimeStamp()); // wait for event to be fully persisted and indexed

        // now recalculate the past event conditions
        segmentService.recalculatePastEventConditions();
        persistenceService.refreshIndex(Profile.class, null);
        keepTrying("Profile should not be engaged in the scoring anymore",
                () -> profileService.load("test_profile_id"),
                updatedProfile -> !updatedProfile.getScores().containsKey("past-event-scoring-test"),
                1000, 20);
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

        pastEventCondition.setParameter("fromDate","2000-07-15T07:00:00Z");
        pastEventCondition.setParameter("toDate","2001-01-15T07:00:00Z");;
        Condition pastEventEventCondition = new Condition(definitionsService.getConditionType("eventTypeCondition"));
        pastEventEventCondition.setParameter("eventTypeId", "test-event-type");
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

        // Send 2 events that match the scoring plan.
        Event testEvent = new Event("test-event-type", null, profile, null, null, profile, timestampEventInRange);
        testEvent.setPersistent(true);
        eventService.send(testEvent);
        refreshPersistence();
        // 2nd event
        testEvent = new Event("test-event-type", null, testEvent.getProfile(), null, null, testEvent.getProfile(), timestampEventInRange);
        eventService.send(testEvent);
        refreshPersistence();

        // insure the profile is engaged;
        try {
            Assert.assertTrue("Profile should have 2 events in the scoring",  (Long) ((Map) testEvent.getProfile().getSystemProperties().get("pastEvents")).get(pastEventCondition.getParameterValues().get("generatedPropertyKey")) == 2);
        } catch (Exception e) {
            Assert.fail("Unable to read past event because " + e.getMessage());
        }
        profileService.save(testEvent.getProfile());
        refreshPersistence();
        // recalculate event conditions
        segmentService.recalculatePastEventConditions();
        // insure the profile is still engaged after recalculate;
        keepTrying("Profile should have 2 events in the scoring",
                () -> profileService.load("test_profile_id"),
                updatedProfile -> {
                    try {
                        return (Integer) ((Map) updatedProfile.getSystemProperties().get("pastEvents")).get(pastEventCondition.getParameterValues().get("generatedPropertyKey")) == 2;
                    } catch (Exception e) {
                        // Do nothing, unable to read value
                    };
                    return false;
                },
                1000, 20);


        // Add one more event
        testEvent = new Event("test-event-type", null, testEvent.getProfile(), null, null, testEvent.getProfile(), timestampEventInRange);
        eventService.send(testEvent);

        // As 3 events have match, the profile should not be part of the scoring plan.
        try {
            Assert.assertTrue("Profile should have no scoring", testEvent.getProfile().getScores().get("past-event-scoring-test") == 0);
        } catch (Exception e) {
            Assert.fail("Unable to read past event because " + e.getMessage());
        }
        profileService.save(testEvent.getProfile());
        refreshPersistence();
        // now recalculate the past event conditions
        segmentService.recalculatePastEventConditions();
        persistenceService.refreshIndex(Profile.class, null);
        // As 3 events have match, the profile should not be part of the scoring plan.
        keepTrying("Profile should not be part of the scoring anymore",
                () -> profileService.load("test_profile_id"),
                updatedProfile -> {
                    try {
                        return updatedProfile.getScores().get("past-event-scoring-test") == 0;
                    } catch (Exception e) {
                        // Do nothing, unable to read value
                    };
                    return false;
                },
                1000, 20);
    }

    @Test
    public void testLinkedItems() throws Exception {

        // create the past event condition
        Condition pastEventCondition = new Condition(definitionsService.getConditionType("pastEventCondition"));
        pastEventCondition.setParameter("minimumEventCount", 1);
        pastEventCondition.setParameter("maximumEventCount", 2);

        pastEventCondition.setParameter("fromDate", "2000-07-15T07:00:00Z");
        pastEventCondition.setParameter("toDate", "2001-01-15T07:00:00Z");
        ;
        Condition pastEventEventCondition = new Condition(definitionsService.getConditionType("eventTypeCondition"));
        pastEventEventCondition.setParameter("eventTypeId", "test-event-type");
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
        refreshPersistence();
        // Check linkedItems
        List<Rule> rules = persistenceService.getAllItems(Rule.class);
        Rule scoringRule = rules.stream().filter(rule -> rule.getItemId().equals(pastEventCondition.getParameter("generatedPropertyKey"))).findFirst().get();
        Assert.assertEquals("Scoring linked Item should be one", 1, scoringRule.getLinkedItems().size());

        // save the scoring once again
        segmentService.setScoringDefinition(scoring);
        refreshPersistence();
        // Check linkedItems
        rules = persistenceService.getAllItems(Rule.class);
        scoringRule = rules.stream().filter(rule -> rule.getItemId().equals(pastEventCondition.getParameter("generatedPropertyKey"))).findFirst().get();
        Assert.assertEquals("Scoring linked Item should be one", 1, scoringRule.getLinkedItems().size());

        // Remove scoring
        segmentService.removeSegmentDefinition(scoring.getItemId(), true);
        refreshPersistence();
        // Check linkedItems
        rules = persistenceService.getAllItems(Rule.class);
        boolean isRule = rules.stream().anyMatch(rule -> rule.getItemId().equals(pastEventCondition.getParameter("generatedPropertyKey")));
        Assert.assertFalse("Rule is properly removed", isRule);
    }
}