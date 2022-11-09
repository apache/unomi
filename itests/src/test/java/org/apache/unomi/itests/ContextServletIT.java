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
 * See the License for the specific language gtestCreateEventWithPropertiesValidation_Successoverning permissions and
 * limitations under the License
 */

package org.apache.unomi.itests;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.unomi.api.*;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.segments.Scoring;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.services.ScopeService;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.schema.api.SchemaService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Created by Ron Barabash on 5/4/2020.
 */

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ContextServletIT extends BaseIT {
    private final static String CONTEXT_URL = "/cxs/context.json";

    private final static String THIRD_PARTY_HEADER_NAME = "X-Unomi-Peer";
    private final static String TEST_EVENT_TYPE = "testEventType";
    private final static String TEST_EVENT_TYPE_SCHEMA = "schemas/events/test-event-type.json";
    private final static String FLOAT_PROPERTY_EVENT_TYPE = "floatPropertyType";
    private final static String FLOAT_PROPERTY_EVENT_TYPE_SCHEMA = "schemas/events/float-property-type.json";
    private final static String TEST_PROFILE_ID = "test-profile-id";

    private final static String SEGMENT_ID = "test-segment-id";
    private final static int SEGMENT_NUMBER_OF_DAYS = 30;

    private static final int DEFAULT_TRYING_TIMEOUT = 2000;
    private static final int DEFAULT_TRYING_TRIES = 30;
    public static final String TEST_SCOPE = "test-scope";

    private Profile profile;

    @Before
    public void setUp() throws InterruptedException {

        //Create a past-event segment
        Metadata segmentMetadata = new Metadata(SEGMENT_ID);
        Segment segment = new Segment(segmentMetadata);
        Condition segmentCondition = new Condition(definitionsService.getConditionType("pastEventCondition"));
        segmentCondition.setParameter("minimumEventCount", 2);
        segmentCondition.setParameter("numberOfDays", SEGMENT_NUMBER_OF_DAYS);
        Condition pastEventEventCondition = new Condition(definitionsService.getConditionType("eventTypeCondition"));
        pastEventEventCondition.setParameter("eventTypeId", TEST_EVENT_TYPE);
        segmentCondition.setParameter("eventCondition", pastEventEventCondition);
        segment.setCondition(segmentCondition);
        segmentService.setSegmentDefinition(segment);

        profile = new Profile(TEST_PROFILE_ID);
        profileService.save(profile);

        keepTrying("Profile " + TEST_PROFILE_ID + " not found in the required time", () -> profileService.load(TEST_PROFILE_ID),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // create schemas required for tests
        schemaService.saveSchema(resourceAsString(TEST_EVENT_TYPE_SCHEMA));
        schemaService.saveSchema(resourceAsString(FLOAT_PROPERTY_EVENT_TYPE_SCHEMA));
        keepTrying("Couldn't find json schemas",
                () -> schemaService.getInstalledJsonSchemaIds(),
                (schemaIds) -> (schemaIds.contains("https://unomi.apache.org/schemas/json/events/floatPropertyType/1-0-0") &&
                        schemaIds.contains("https://unomi.apache.org/schemas/json/events/testEventType/1-0-0")),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        TestUtils.createScope(TEST_SCOPE, "Test scope", scopeService);
        keepTrying("Scope test-scope not found in the required time", () -> scopeService.getScope(TEST_SCOPE),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @After
    public void tearDown() throws InterruptedException {
        TestUtils.removeAllEvents(definitionsService, persistenceService);
        TestUtils.removeAllSessions(definitionsService, persistenceService);
        TestUtils.removeAllProfiles(definitionsService, persistenceService);
        profileService.delete(profile.getItemId(), false);
        segmentService.removeSegmentDefinition(SEGMENT_ID, false);

        // cleanup schemas
        schemaService.deleteSchema("https://unomi.apache.org/schemas/json/events/testEventType/1-0-0");
        schemaService.deleteSchema("https://unomi.apache.org/schemas/json/events/floatPropertyType/1-0-0");
        keepTrying("Should not find json schemas anymore",
                () -> schemaService.getInstalledJsonSchemaIds(),
                (schemaIds) -> (!schemaIds.contains("https://unomi.apache.org/schemas/json/events/floatPropertyType/1-0-0") &&
                        !schemaIds.contains("https://unomi.apache.org/schemas/json/events/testEventType/1-0-0")),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        scopeService.delete(TEST_SCOPE);
    }

    @Test
    public void testUpdateEventFromContextAuthorizedThirdParty_Success() throws Exception {
        //Arrange
        String eventId = "test-event-id-" + System.currentTimeMillis();
        String sessionId = "test-session-id";
        String scope = TEST_SCOPE;
        String eventTypeOriginal = "test-event-type-original";
        Profile profile = new Profile(TEST_PROFILE_ID);
        Session session = new Session(sessionId, profile, new Date(), scope);
        Event event = new Event(eventId, eventTypeOriginal, session, profile, scope, null, null, new Date());
        profileService.save(profile);

        keepTrying("Profile " + TEST_PROFILE_ID + " not found in the required time", () -> profileService.load(TEST_PROFILE_ID),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        this.eventService.send(event);

        keepTrying("Event " + eventId + " not updated in the required time", () -> this.eventService.getEvent(eventId),
                savedEvent -> Objects.nonNull(savedEvent) && eventTypeOriginal.equals(savedEvent.getEventType()), DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);

        event.setEventType(TEST_EVENT_TYPE); //change the event so we can see the update effect

        //Act
        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setSessionId(session.getItemId());
        contextRequest.setEvents(Arrays.asList(event));
        HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.addHeader(THIRD_PARTY_HEADER_NAME, UNOMI_KEY);
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.executeContextJSONRequest(request, sessionId);

        event = keepTrying("Event " + eventId + " not updated in the required time", () -> eventService.getEvent(eventId),
                savedEvent -> Objects.nonNull(savedEvent) && TEST_EVENT_TYPE.equals(savedEvent.getEventType()), DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);
        assertEquals(2, event.getVersion().longValue());
    }

    @Test
    public void testCallingContextWithSessionCreation() throws Exception {
        //Arrange
        String eventId = "test-event-id-" + System.currentTimeMillis();
        String sessionId = "test-session-id";
        Profile profile = new Profile(TEST_PROFILE_ID);
        profileService.save(profile);

        keepTrying("Profile " + TEST_PROFILE_ID + " not found in the required time", () -> profileService.load(TEST_PROFILE_ID),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        //Act
        Event event = new Event(TEST_EVENT_TYPE, null, profile, TEST_SCOPE, null, null, new Date());

        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setSessionId(sessionId);
        contextRequest.setEvents(Collections.singletonList(event));
        HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.addHeader(THIRD_PARTY_HEADER_NAME, UNOMI_KEY);
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.executeContextJSONRequest(request, sessionId);

        Session session = keepTrying("Session with the id " + sessionId + " not saved in the required time",
                () -> profileService.loadSession(sessionId,
                        null), Objects::nonNull, DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);

        assertEquals(TEST_EVENT_TYPE, session.getOriginEventTypes().get(0));
        assertFalse(session.getOriginEventIds().isEmpty());
    }

    @Test
    public void testUpdateEventFromContextUnAuthorizedThirdParty_Fail() throws Exception {
        //Arrange
        String eventId = "test-event-id-" + System.currentTimeMillis();
        String sessionId = "test-session-id";
        String scope = TEST_SCOPE;
        String eventTypeOriginal = "test-event-type-original";
        String eventTypeUpdated = TEST_EVENT_TYPE;
        Profile profile = new Profile(TEST_PROFILE_ID);
        Session session = new Session(sessionId, profile, new Date(), scope);
        Event event = new Event(eventId, eventTypeOriginal, session, profile, scope, null, null, new Date());
        profileService.save(profile);

        keepTrying("Profile " + TEST_PROFILE_ID + " not found in the required time", () -> profileService.load(TEST_PROFILE_ID),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        this.eventService.send(event);

        keepTrying("Event " + eventId + " not saved in the required time", () -> this.eventService.getEvent(eventId),
                savedEvent -> Objects.nonNull(savedEvent) && eventTypeOriginal.equals(savedEvent.getEventType()), DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);

        event.setEventType(eventTypeUpdated); //change the event so we can see the update effect

        //Act
        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setSessionId(session.getItemId());
        contextRequest.setEvents(Arrays.asList(event));
        HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.executeContextJSONRequest(request, sessionId);

        // Check event type did not changed
        event = shouldBeTrueUntilEnd("Event type should not have changed", () -> eventService.getEvent(eventId),
                (savedEvent) -> eventTypeOriginal.equals(savedEvent.getEventType()), DEFAULT_TRYING_TIMEOUT, 10);
        assertEquals(1, event.getVersion().longValue());
    }

    @Test
    public void testUpdateEventFromContextAuthorizedThirdPartyNoItemID_Fail() throws Exception {
        //Arrange
        String eventId = "test-event-id-" + System.currentTimeMillis();
        String sessionId = "test-session-id";
        String scope = TEST_SCOPE;
        String eventTypeOriginal = "test-event-type-original";
        String eventTypeUpdated = TEST_EVENT_TYPE;
        Session session = new Session(sessionId, profile, new Date(), scope);
        Event event = new Event(eventId, eventTypeOriginal, session, profile, scope, null, null, new Date());
        this.eventService.send(event);

        keepTrying("Event " + eventId + " not saved in the required time", () -> this.eventService.getEvent(eventId),
                savedEvent -> Objects.nonNull(savedEvent) && eventTypeOriginal.equals(savedEvent.getEventType()), DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);

        event.setEventType(eventTypeUpdated); //change the event so we can see the update effect

        //Act
        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setSessionId(session.getItemId());
        contextRequest.setEvents(Arrays.asList(event));
        HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.executeContextJSONRequest(request, sessionId);

        // Check event type did not changed
        event = shouldBeTrueUntilEnd("Event type should not have changed", () -> eventService.getEvent(eventId),
                (savedEvent) -> eventTypeOriginal.equals(savedEvent.getEventType()), DEFAULT_TRYING_TIMEOUT, 10);

        assertEquals(1, event.getVersion().longValue());
    }

    @Test
    public void testCreateEventsWithNoTimestampParam_profileAddedToSegment() throws Exception {
        //Arrange
        String sessionId = "test-session-id";
        String scope = TEST_SCOPE;
        Event event = new Event();
        event.setEventType(TEST_EVENT_TYPE);
        event.setScope(scope);

        //Act
        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setSessionId(sessionId);
        contextRequest.setRequireSegments(true);
        contextRequest.setEvents(Arrays.asList(event));
        HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        String cookieHeaderValue = TestUtils.executeContextJSONRequest(request, sessionId).getCookieHeaderValue();

        refreshPersistence();

        //Add the context-profile-id cookie to the second event
        request.addHeader("Cookie", cookieHeaderValue);
        ContextResponse response = (TestUtils.executeContextJSONRequest(request, sessionId)).getContextResponse(); //second event

        refreshPersistence();

        //Assert
        assertEquals(1, response.getProfileSegments().size());
        assertThat(response.getProfileSegments(), hasItem(SEGMENT_ID));

    }

    @Test
    public void testCreateEventWithTimestampParam_pastEvent_profileIsNotAddedToSegment() throws Exception {
        //Arrange
        String sessionId = "test-session-id";
        String scope = TEST_SCOPE;
        Event event = new Event();
        event.setEventType(TEST_EVENT_TYPE);
        event.setScope(scope);
        String regularURI = getFullUrl(CONTEXT_URL);
        long oldTimestamp = LocalDateTime.now(ZoneId.of("UTC")).minusDays(SEGMENT_NUMBER_OF_DAYS + 1).toInstant(ZoneOffset.UTC)
                .toEpochMilli();
        String customTimestampURI = regularURI + "?timestamp=" + oldTimestamp;

        //Act
        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setSessionId(sessionId);
        contextRequest.setRequireSegments(true);
        contextRequest.setEvents(Arrays.asList(event));
        HttpPost request = new HttpPost(regularURI);
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        //The first event is with a default timestamp (now)
        String cookieHeaderValue = TestUtils.executeContextJSONRequest(request, sessionId).getCookieHeaderValue();
        //The second event is with a customized timestamp
        request.setURI(URI.create(customTimestampURI));
        request.addHeader("Cookie", cookieHeaderValue);
        ContextResponse response = (TestUtils.executeContextJSONRequest(request, sessionId)).getContextResponse(); //second event

        shouldBeTrueUntilEnd("Profile " + response.getProfileId() + " not found in the required time",
                () -> profileService.load(response.getProfileId()),
                (savedProfile) -> Objects.nonNull(savedProfile) && !savedProfile.getSegments().contains(SEGMENT_ID), DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);
    }

    @Test
    public void testCreateEventWithTimestampParam_futureEvent_profileIsNotAddedToSegment() throws Exception {
        //Arrange
        String sessionId = "test-session-id";
        String scope = TEST_SCOPE;
        Event event = new Event();
        event.setEventType(TEST_EVENT_TYPE);
        event.setScope(scope);
        String regularURI = getFullUrl(CONTEXT_URL);
        long futureTimestamp = LocalDateTime.now(ZoneId.of("UTC")).plusDays(1).toInstant(ZoneOffset.UTC).toEpochMilli();
        String customTimestampURI = regularURI + "?timestamp=" + futureTimestamp;

        //Act
        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setSessionId(sessionId);
        contextRequest.setRequireSegments(true);
        contextRequest.setEvents(Arrays.asList(event));
        HttpPost request = new HttpPost(regularURI);
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        //The first event is with a default timestamp (now)
        String cookieHeaderValue = TestUtils.executeContextJSONRequest(request, sessionId).getCookieHeaderValue();

        //The second event is with a customized timestamp
        request.setURI(URI.create(customTimestampURI));
        request.addHeader("Cookie", cookieHeaderValue);
        ContextResponse response = TestUtils.executeContextJSONRequest(request, sessionId).getContextResponse(); //second event

        shouldBeTrueUntilEnd("Profile " + response.getProfileId() + " not found in the required time",
                () -> profileService.load(response.getProfileId()),
                (savedProfile) -> Objects.nonNull(savedProfile) && !savedProfile.getSegments().contains(SEGMENT_ID), DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);
    }

    @Test
    public void testCreateEventWithProfileId_Success() throws Exception {
        //Arrange
        String eventId = "test-event-id-" + System.currentTimeMillis();
        String eventType = "test-event-type";
        Event event = new Event();
        event.setEventType(eventType);
        event.setItemId(eventId);

        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setProfileId(TEST_PROFILE_ID);
        contextRequest.setEvents(Arrays.asList(event));

        //Act
        HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.addHeader(THIRD_PARTY_HEADER_NAME, UNOMI_KEY);
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.executeContextJSONRequest(request);

        keepTrying("Profile " + TEST_PROFILE_ID + " not found in the required time", () -> profileService.load(TEST_PROFILE_ID),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @Test
    public void testCreateEventWithPropertiesValidation_Success() throws Exception {
        //Arrange
        String eventId = "valid-event-id-" + System.currentTimeMillis();
        String profileId = "valid-profile-id";
        String eventType = FLOAT_PROPERTY_EVENT_TYPE;
        Event event = new Event();
        event.setEventType(eventType);
        event.setItemId(eventId);
        Map<String, Object> props = new HashMap<>();
        props.put("floatProperty", 3.14159);
        event.setProperties(props);

        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setProfileId(profileId);
        contextRequest.setEvents(Arrays.asList(event));

        //Act
        HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.addHeader(THIRD_PARTY_HEADER_NAME, UNOMI_KEY);
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.executeContextJSONRequest(request);

        //Assert
        event = keepTrying("Event not found", () -> eventService.getEvent(eventId), Objects::nonNull, DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);
        assertEquals(eventType, event.getEventType());
        assertEquals(3.14159, event.getProperty("floatProperty"));
    }

    @Test
    public void testCreateEventWithPropertyValueValidation_Failure() throws Exception {
        //Arrange
        String eventId = "invalid-event-value-id-" + System.currentTimeMillis();
        String profileId = "invalid-profile-id";
        String eventType = FLOAT_PROPERTY_EVENT_TYPE;
        Event event = new Event();
        event.setEventType(eventType);
        event.setItemId(eventId);
        Map<String, Object> props = new HashMap<>();
        props.put("floatProperty", "Invalid value");
        event.setProperties(props);

        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setProfileId(profileId);
        contextRequest.setEvents(Arrays.asList(event));

        //Act
        HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.addHeader(THIRD_PARTY_HEADER_NAME, UNOMI_KEY);
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.executeContextJSONRequest(request);

        //Assert
        shouldBeTrueUntilEnd("Event should be null", () -> eventService.getEvent(eventId), Objects::isNull, DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);
    }

    @Test
    public void testCreateEventWithPropertyNameValidation_Failure() throws Exception {
        //Arrange
        String eventId = "invalid-event-prop-id-" + System.currentTimeMillis();
        String profileId = "invalid-profile-id";
        Event event = new Event();
        event.setEventType(FLOAT_PROPERTY_EVENT_TYPE);
        event.setItemId(eventId);
        Map<String, Object> props = new HashMap<>();
        props.put("ffloatProperty", 3.14159);
        event.setProperties(props);

        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setProfileId(profileId);
        contextRequest.setEvents(Arrays.asList(event));

        //Act
        HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.addHeader(THIRD_PARTY_HEADER_NAME, UNOMI_KEY);
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.executeContextJSONRequest(request);

        //Assert
        shouldBeTrueUntilEnd("Event should be null", () -> eventService.getEvent(eventId), Objects::isNull, DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);
    }

    @Test
    public void testOGNLVulnerability() throws Exception {

        File vulnFile = new File("target/vuln-file.txt");
        if (vulnFile.exists()) {
            vulnFile.delete();
        }
        String vulnFileCanonicalPath = vulnFile.getCanonicalPath();
        vulnFileCanonicalPath = vulnFileCanonicalPath.replace("\\", "\\\\"); // this is required for Windows support

        Map<String, String> parameters = new HashMap<>();
        parameters.put("VULN_FILE_PATH", vulnFileCanonicalPath);
        HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.setEntity(
                new StringEntity(getValidatedBundleJSON("security/ognl-payload-1.json", parameters), ContentType.APPLICATION_JSON));
        TestUtils.executeContextJSONRequest(request);

        shouldBeTrueUntilEnd("Vulnerability successfully executed ! File created at " + vulnFileCanonicalPath, vulnFile::exists,
                exists -> exists == Boolean.FALSE, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @Test
    public void testMVELVulnerability() throws Exception {

        File vulnFile = new File("target/vuln-file.txt");
        if (vulnFile.exists()) {
            vulnFile.delete();
        }
        String vulnFileCanonicalPath = vulnFile.getCanonicalPath();
        vulnFileCanonicalPath = vulnFileCanonicalPath.replace("\\", "\\\\"); // this is required for Windows support

        Map<String, String> parameters = new HashMap<>();
        parameters.put("VULN_FILE_PATH", vulnFileCanonicalPath);
        HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.setEntity(
                new StringEntity(getValidatedBundleJSON("security/mvel-payload-1.json", parameters), ContentType.APPLICATION_JSON));
        TestUtils.executeContextJSONRequest(request);

        shouldBeTrueUntilEnd("Vulnerability successfully executed ! File created at " + vulnFileCanonicalPath, vulnFile::exists,
                exists -> exists == Boolean.FALSE, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @Test
    public void testPersonalization() throws Exception {

        Map<String, String> parameters = new HashMap<>();
        HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.setEntity(new StringEntity(getValidatedBundleJSON("personalization.json", parameters), ContentType.APPLICATION_JSON));
        TestUtils.RequestResponse response = TestUtils.executeContextJSONRequest(request);
        assertEquals("Invalid response code", 200, response.getStatusCode());
    }

    @Test
    public void testScorePersonalizationStrategy_Interests() throws Exception {
        // Test request before adding interests to current profile.
        HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.setEntity(new StringEntity(getValidatedBundleJSON("personalization-score-interests.json", null), ContentType.APPLICATION_JSON));
        TestUtils.RequestResponse response = TestUtils.executeContextJSONRequest(request);
        ContextResponse contextResponse = response.getContextResponse();
        List<String> variants = contextResponse.getPersonalizations().get("perso-by-interest");
        assertEquals("Invalid response code", 200, response.getStatusCode());
        assertEquals("Perso should be empty, profile is empty", 0, variants.size());
        variants = contextResponse.getPersonalizationResults().get("perso-by-interest").getContentIds();
        assertEquals("Perso should be empty, profile is empty", 0, variants.size());

        // set profile for matching
        Profile profile = profileService.load(TEST_PROFILE_ID);
        profile.setProperty("age", 30);
        profileService.save(profile);
        keepTrying("Profile " + TEST_PROFILE_ID + " not found in the required time", () -> profileService.load(TEST_PROFILE_ID),
                savedProfile -> (savedProfile != null && savedProfile.getProperty("age").equals(30)), DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // check results of the perso now
        request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.setEntity(new StringEntity(getValidatedBundleJSON("personalization-score-interests.json", null), ContentType.APPLICATION_JSON));
        response = TestUtils.executeContextJSONRequest(request);
        contextResponse = response.getContextResponse();
        variants = contextResponse.getPersonalizations().get("perso-by-interest");
        assertEquals("Invalid response code", 200, response.getStatusCode());
        assertEquals("Perso should contains the good number of variants", 1, variants.size());
        assertEquals("Variant is not the expected one", "matching-fishing-interests-custom-score-100-variant-expected-score-120", variants.get(0));
        variants = contextResponse.getPersonalizationResults().get("perso-by-interest").getContentIds();
        assertEquals("Perso should contains the good number of variants", 1, variants.size());
        assertEquals("Variant is not the expected one", "matching-fishing-interests-custom-score-100-variant-expected-score-120", variants.get(0));

        // modify profile to add interests
        profile = profileService.load(TEST_PROFILE_ID);
        List<Map<String, Object>> interests = new ArrayList<>();
        Map<String, Object> interest1 = new HashMap<>();
        interest1.put("key", "cars");
        interest1.put("value", 50);
        interests.add(interest1);
        Map<String, Object> interest2 = new HashMap<>();
        interest2.put("key", "football");
        interest2.put("value", 40);
        interests.add(interest2);
        Map<String, Object> interest3 = new HashMap<>();
        interest3.put("key", "tennis");
        interest3.put("value", 30);
        interests.add(interest3);
        Map<String, Object> interest4 = new HashMap<>();
        interest4.put("key", "fishing");
        interest4.put("value", 20);
        interests.add(interest4);
        profile.setProperty("interests", interests);
        profileService.save(profile);
        keepTrying("Profile " + TEST_PROFILE_ID + " not found in the required time", () -> profileService.load(TEST_PROFILE_ID),
                savedProfile -> (savedProfile != null && savedProfile.getProperty("interests") != null), DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // re test now that profiles has interests
        request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.setEntity(new StringEntity(getValidatedBundleJSON("personalization-score-interests.json", null), ContentType.APPLICATION_JSON));
        response = TestUtils.executeContextJSONRequest(request);
        contextResponse = response.getContextResponse();
        variants = contextResponse.getPersonalizations().get("perso-by-interest");
        assertEquals("Invalid response code", 200, response.getStatusCode());
        assertEquals("Perso should contains the good number of variants", 7, variants.size());
        assertEquals("Variant is not the expected one", "matching-fishing-interests-custom-score-100-variant-expected-score-120", variants.get(0));
        assertEquals("Variant is not the expected one", "matching-football-cars-interests-variant-expected-score-91", variants.get(1));
        assertEquals("Variant is not the expected one", "not-matching-football-cars-interests-variant-expected-score-90", variants.get(2));
        assertEquals("Variant is not the expected one", "not-matching-tennis-fishing-interests-variant-expected-score-50", variants.get(3));
        assertEquals("Variant is not the expected one", "matching-football-interests-variant-expected-score-51", variants.get(4));
        assertEquals("Variant is not the expected one", "matching-tennis-interests-variant-expected-score-31", variants.get(5));
        assertEquals("Variant is not the expected one", "not-matching-tennis-interests-custom-score-100-variant-expected-score-30", variants.get(6));
        variants = contextResponse.getPersonalizationResults().get("perso-by-interest").getContentIds();
        assertEquals("Perso should contains the good number of variants", 7, variants.size());
        assertEquals("Variant is not the expected one", "matching-fishing-interests-custom-score-100-variant-expected-score-120", variants.get(0));
        assertEquals("Variant is not the expected one", "matching-football-cars-interests-variant-expected-score-91", variants.get(1));
        assertEquals("Variant is not the expected one", "not-matching-football-cars-interests-variant-expected-score-90", variants.get(2));
        assertEquals("Variant is not the expected one", "not-matching-tennis-fishing-interests-variant-expected-score-50", variants.get(3));
        assertEquals("Variant is not the expected one", "matching-football-interests-variant-expected-score-51", variants.get(4));
        assertEquals("Variant is not the expected one", "matching-tennis-interests-variant-expected-score-31", variants.get(5));
        assertEquals("Variant is not the expected one", "not-matching-tennis-interests-custom-score-100-variant-expected-score-30", variants.get(6));
    }

    @Test
    public void testRequireScoring() throws Exception {

        Map<String, String> parameters = new HashMap<>();
        String scoringSource = getValidatedBundleJSON("score1.json", parameters);
        Scoring scoring = CustomObjectMapper.getObjectMapper().readValue(scoringSource, Scoring.class);
        segmentService.setScoringDefinition(scoring);

        keepTrying("Profile does not contains scores in the required time", () -> profileService.load(TEST_PROFILE_ID), storedProfile ->
                storedProfile.getScores() != null && storedProfile.getScores().get("score1") != null, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // first let's make sure everything works without the requireScoring parameter
        parameters = new HashMap<>();
        HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.setEntity(new StringEntity(getValidatedBundleJSON("withoutRequireScores.json", parameters), ContentType.APPLICATION_JSON));
        TestUtils.RequestResponse response = TestUtils.executeContextJSONRequest(request);
        assertEquals("Invalid response code", 200, response.getStatusCode());

        assertNotNull("Context response should not be null", response.getContextResponse());
        Map<String, Integer> scores = response.getContextResponse().getProfileScores();
        assertNull("Context response should not contain scores", scores);

        // now let's test adding it.
        parameters = new HashMap<>();
        request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.setEntity(new StringEntity(getValidatedBundleJSON("withRequireScores.json", parameters), ContentType.APPLICATION_JSON));
        response = TestUtils.executeContextJSONRequest(request);
        assertEquals("Invalid response code", 200, response.getStatusCode());

        assertNotNull("Context response should not be null", response.getContextResponse());
        scores = response.getContextResponse().getProfileScores();
        assertNotNull("Context response should contain scores", scores);
        assertNotNull("score1 not found in profile scores", scores.get("score1"));
        assertEquals("score1 does not have expected value", 1, scores.get("score1").intValue());

        segmentService.removeScoringDefinition(scoring.getItemId(), false);
    }
}
