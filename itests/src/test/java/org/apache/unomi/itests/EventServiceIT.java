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
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;

import javax.inject.Inject;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An integration test for the event service
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class EventServiceIT extends BaseIT {

    private final static String TEST_PROFILE_ID = "test-profile-id";

    @Inject
    @Filter(timeout = 600000)
    protected EventService eventService;

    @Inject
    @Filter(timeout = 600000)
    protected ProfileService profileService;

    @After
    public void tearDown() {
        TestUtils.removeAllEvents(definitionsService, persistenceService);
        TestUtils.removeAllSessions(definitionsService, persistenceService);
        TestUtils.removeAllProfiles(definitionsService, persistenceService);
    }

    @Test
    public void test_EventExistenceWithProfileId() throws InterruptedException {
        String eventId = "test-event-id-" + System.currentTimeMillis();
        String profileId = "test-profile-id";
        String eventType = "test-type";
        Profile profile = new Profile(profileId);
        Event event = new Event(eventId, eventType, null, profile, null, null, null, new Date());
        profileService.save(profile);
        keepTrying("Profile with id profileId not found in the required time", () -> profileService.load(profileId), Objects::nonNull,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        eventService.send(event);
        keepTrying("Event has not been raised", () -> eventService.hasEventAlreadyBeenRaised(event), raised -> raised == Boolean.TRUE,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @Test
    public void test_PastEventWithDateRange() throws InterruptedException, ParseException {
        String eventId = "past-event-id" + System.currentTimeMillis();
        String profileId = "past-event-profile-id" + System.currentTimeMillis();
        String eventType = "past-event-with-date-range-type";
        Profile profile = new Profile(profileId);
        Date timestamp = new SimpleDateFormat("yyyy-MM-dd").parse("2000-06-30");
        Event event = new Event(eventId, eventType, null, profile, null, null, null, timestamp);

        profileService.save(profile);
        eventService.send(event);

        keepTrying("Profile with id profileId not found in the required time", () -> profileService.load(profileId), Objects::nonNull,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        keepTrying("Event has not been raised", () -> eventService.getEvent(eventId), Objects::nonNull, DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);

        Condition eventTypeCondition = new Condition(definitionsService.getConditionType("eventTypeCondition"));
        eventTypeCondition.setParameter("eventTypeId", eventType);

        Condition pastEventCondition = new Condition(definitionsService.getConditionType("pastEventCondition"));
        pastEventCondition.setParameter("minimumEventCount", 1);
        pastEventCondition.setParameter("fromDate", "1999-01-15T07:00:00Z");
        pastEventCondition.setParameter("toDate", "2001-01-15T07:00:00Z");

        pastEventCondition.setParameter("eventCondition", eventTypeCondition);

        Query query = new Query();
        query.setCondition(pastEventCondition);

        PartialList<Profile> profiles = profileService.search(query, Profile.class);
        Assert.assertEquals(1, profiles.getList().size());
        Assert.assertEquals(profiles.getList().get(0).getItemId(), profileId);

    }

    @Test
    public void test_PastEventNotInRange_NoProfilesShouldReturn() throws InterruptedException, ParseException {
        String eventId = "past-event-id" + System.currentTimeMillis();
        String profileId = "past-event-profile-id" + System.currentTimeMillis();
        String eventType = "past-event-with-date-range-type";
        Profile profile = new Profile(profileId);
        Date timestamp = new SimpleDateFormat("yyyy-MM-dd").parse("2000-06-30");
        Event event = new Event(eventId, eventType, null, profile, null, null, null, timestamp);

        profileService.save(profile);
        eventService.send(event);

        keepTrying("Profile with id profileId not found in the required time", () -> profileService.load(profileId), Objects::nonNull,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        keepTrying("Event has not been raised", () -> eventService.getEvent(eventId), Objects::nonNull, DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);

        Condition eventTypeCondition = new Condition(definitionsService.getConditionType("eventTypeCondition"));
        eventTypeCondition.setParameter("eventTypeId", eventType);

        Condition pastEventCondition = new Condition(definitionsService.getConditionType("pastEventCondition"));
        pastEventCondition.setParameter("minimumEventCount", 1);
        pastEventCondition.setParameter("fromDate", "2000-07-15T07:00:00Z");
        pastEventCondition.setParameter("toDate", "2001-01-15T07:00:00Z");

        pastEventCondition.setParameter("eventCondition", eventTypeCondition);

        Query query = new Query();
        query.setCondition(pastEventCondition);

        PartialList<Profile> profiles = profileService.search(query, Profile.class);
        Assert.assertEquals(0, profiles.getList().size());
    }

    @Test
    public void test_EventGetNestedProperty() {
        String nestedProperty = "outerProperty.innerProperty";
        String testValue = "test-value";
        String eventId = "test-event-id-" + System.currentTimeMillis();
        String profileId = "test-profile-id";
        String eventType = "test-type";
        Profile profile = new Profile(profileId);
        Event event = new Event(eventId, eventType, null, profile, null, null, null, new Date());
        final Map<String, String> innerProperty = new HashMap<>();
        innerProperty.put("innerProperty", testValue);
        event.setProperty("outerProperty", innerProperty);
        String value = (String) event.getNestedProperty(nestedProperty);
        Assert.assertEquals(testValue, value);
    }

}
