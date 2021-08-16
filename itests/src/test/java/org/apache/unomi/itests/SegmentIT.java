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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

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
    }

    @After
    public void tearDown() throws InterruptedException {
        removeItems(Segment.class);
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
        eventService.send(testEvent);
        persistenceService.refreshIndex(Event.class, new Date()); // wait for event to be fully persisted and indexed

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
        Thread.sleep(5000);
        profile = profileService.load("test_profile_id");
        Assert.assertTrue("Profile should be engaged in the segment", profile.getSegments().contains("past-event-segment-test"));
    }
}
