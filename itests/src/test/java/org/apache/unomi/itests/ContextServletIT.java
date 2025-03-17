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

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.unomi.api.*;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.segments.Scoring;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.tenants.ApiKey;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.itests.TestUtils.RequestResponse;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import javax.inject.Inject;
import java.io.File;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.junit.Assert.*;

/**
 * Created by Ron Barabash on 5/4/2020.
 */

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ContextServletIT extends BaseIT {
    private final static String CONTEXT_URL = "/cxs/context.json";

    private final static String UNOMI_API_KEY_HTTP_HEADER_KEY = "X-Unomi-Api-Key";
    private final static String TEST_EVENT_TYPE = "testEventType";
    private final static String TEST_EVENT_TYPE_SCHEMA = "schemas/events/test-event-type.json";
    private final static String FLOAT_PROPERTY_EVENT_TYPE = "floatPropertyType";
    private final static String FLOAT_PROPERTY_EVENT_TYPE_SCHEMA = "schemas/events/float-property-type.json";
    private final static String TEST_SESSION_ID = "dummy-session";
    private final static String TEST_PROFILE_ID = "test-profile-id";
    private final static String TEST_PROFILE_FIRST_NAME = "contextServletIT_profile";

    private final static String SEGMENT_ID = "test-segment-id";
    private final static int SEGMENT_NUMBER_OF_DAYS = 30;

    private static final int DEFAULT_TRYING_TIMEOUT = 2000;
    private static final int DEFAULT_TRYING_TRIES = 60;
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
        profile.setProperty("firstName", TEST_PROFILE_FIRST_NAME);
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
        removeItems(Session.class);
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
        request.addHeader(UNOMI_API_KEY_HTTP_HEADER_KEY, testPublicKey.getKey());
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
        request.addHeader(UNOMI_API_KEY_HTTP_HEADER_KEY, testPublicKey.getKey());
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.executeContextJSONRequest(request, sessionId);

        Session session = keepTrying("Session with the id " + sessionId + " not saved in the required time",
                () -> profileService.loadSession(sessionId), Objects::nonNull, DEFAULT_TRYING_TIMEOUT,
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
                (savedEvent) -> eventTypeOriginal.equals(savedEvent.getEventType()), DEFAULT_TRYING_TIMEOUT, DEFAULT_SHOULDBETRUE_TRIES);
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
                (savedEvent) -> eventTypeOriginal.equals(savedEvent.getEventType()), DEFAULT_TRYING_TIMEOUT, DEFAULT_SHOULDBETRUE_TRIES);

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

        refreshPersistence(Event.class);

        //Add the context-profile-id cookie to the second event
        request.addHeader("Cookie", cookieHeaderValue);
        ContextResponse response = (TestUtils.executeContextJSONRequest(request, sessionId)).getContextResponse(); //second event

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
                DEFAULT_SHOULDBETRUE_TRIES);
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
                DEFAULT_SHOULDBETRUE_TRIES);
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
        request.addHeader(UNOMI_API_KEY_HTTP_HEADER_KEY, testPublicKey.getKey());
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
        request.addHeader(UNOMI_API_KEY_HTTP_HEADER_KEY, testPublicKey.getKey());
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
        request.addHeader(UNOMI_API_KEY_HTTP_HEADER_KEY, testPublicKey.getKey());
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.executeContextJSONRequest(request);

        //Assert
        shouldBeTrueUntilEnd("Event should be null", () -> eventService.getEvent(eventId), Objects::isNull, DEFAULT_TRYING_TIMEOUT,
                DEFAULT_SHOULDBETRUE_TRIES);
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
        request.addHeader(UNOMI_API_KEY_HTTP_HEADER_KEY, testPublicKey.getKey());
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.executeContextJSONRequest(request);

        //Assert
        shouldBeTrueUntilEnd("Event should be null", () -> eventService.getEvent(eventId), Objects::isNull, DEFAULT_TRYING_TIMEOUT,
                DEFAULT_SHOULDBETRUE_TRIES);
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
        RequestResponse response = TestUtils.executeContextJSONRequest(request, TEST_SESSION_ID);

        shouldBeTrueUntilEnd("Vulnerability successfully executed ! File created at " + vulnFileCanonicalPath, vulnFile::exists,
                exists -> exists == Boolean.FALSE, DEFAULT_TRYING_TIMEOUT, DEFAULT_SHOULDBETRUE_TRIES);
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
        RequestResponse response = TestUtils.executeContextJSONRequest(request, TEST_SESSION_ID);

        shouldBeTrueUntilEnd("Vulnerability successfully executed ! File created at " + vulnFileCanonicalPath, vulnFile::exists,
                exists -> exists == Boolean.FALSE, DEFAULT_TRYING_TIMEOUT, DEFAULT_SHOULDBETRUE_TRIES);
    }

    @Test
    public void testPersonalization() throws Exception {
        Map<String, String> parameters = new HashMap<>();
        HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.setEntity(new StringEntity(getValidatedBundleJSON("personalization.json", parameters), ContentType.APPLICATION_JSON));
        RequestResponse response = TestUtils.executeContextJSONRequest(request, TEST_SESSION_ID);
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

    @Test
    public void test_no_ControlGroup() throws Exception {
        performPersonalizationWithControlGroup(
                null,
                Collections.singletonList("no-condition"),
                false,
                false,
                null,
                null);
    }

    @Test
    public void test_in_ControlGroup_profile_stored() throws Exception {
        performPersonalizationWithControlGroup(
                generateControlGroupConfig("false", "100.0"),
                Arrays.asList("first-name-missing", "no-condition"),
                true,
                true,
                true,
                null);

        performPersonalizationWithControlGroup(
                generateControlGroupConfig("false", "0.0"),
                Arrays.asList("first-name-missing", "no-condition"),
                true,
                true,
                true,
                null);
    }

    @Test
    public void test_in_ControlGroup_session_stored() throws Exception {
        performPersonalizationWithControlGroup(
                generateControlGroupConfig("true", "100.0"),
                Arrays.asList("first-name-missing", "no-condition"),
                true,
                true,
                null,
                true);

        performPersonalizationWithControlGroup(
                generateControlGroupConfig("true", "0.0"),
                Arrays.asList("first-name-missing", "no-condition"),
                true,
                true,
                null,
                true);
    }

    @Test
    public void test_out_ControlGroup_profile_stored() throws Exception {
        performPersonalizationWithControlGroup(
                generateControlGroupConfig("false", "0.0"),
                Collections.singletonList("no-condition"),
                true,
                false,
                false,
                null);

        performPersonalizationWithControlGroup(
                generateControlGroupConfig("false", "100.0"),
                Collections.singletonList("no-condition"),
                true,
                false,
                false,
                null);
    }

    @Test
    public void test_out_ControlGroup_session_stored() throws Exception {
        performPersonalizationWithControlGroup(
                generateControlGroupConfig("true", "0.0"),
                Collections.singletonList("no-condition"),
                true,
                false,
                null,
                false);

        performPersonalizationWithControlGroup(
                generateControlGroupConfig("true", "100.0"),
                Collections.singletonList("no-condition"),
                true,
                false,
                null,
                false);
    }

    @Test
    public void test_advanced_ControlGroup_test() throws Exception {
        // STEP 1: start with no control group
        performPersonalizationWithControlGroup(
                null,
                Collections.singletonList("no-condition"),
                false,
                false,
                null,
                null);

        // STEP 2: then enable control group stored in profile
        performPersonalizationWithControlGroup(
                generateControlGroupConfig("false", "100.0"),
                Arrays.asList("first-name-missing", "no-condition"),
                true,
                true,
                true,
                null);

        // STEP 3: then re disable control group
        performPersonalizationWithControlGroup(
                null,
                Collections.singletonList("no-condition"),
                false,
                false,
                /* We can see we still have old control group check stored in the profile */ true,
                null);

        // STEP 4: then re-enable control group, but session scoped this time, with a 0 percentage
        performPersonalizationWithControlGroup(
                generateControlGroupConfig("true", "0.0"),
                Collections.singletonList("no-condition"),
                true,
                false,
                /* We can see we still have old control group check stored in the profile */ true,
                /* And now we also have a status saved in the session */ false);

        // STEP 5: then re-enable control group, but profile scoped this time, with a 0 percentage
        //         We should be in control group because of the STEP 2, the current profile already contains a persisted status for the perso.
        //         So even if current config is 0, old check already flagged current profile to be in the control group.
        performPersonalizationWithControlGroup(
                generateControlGroupConfig("false", "0.0"),
                Arrays.asList("first-name-missing", "no-condition"),
                true,
                true,
                /* We can see we still have old control group check stored in the profile */ true,
                /*  We can see we still have old control group check stored in the session too */ false);

        // STEP 6: then re-enable control group, but session scoped this time, with a 100 percentage
        //         We should not be in control group because of the STEP 4, the current session already contains a persisted status for the perso.
        //         So even if current config is 100, old check already flagged current profile to not be in the control group.
        performPersonalizationWithControlGroup(
                generateControlGroupConfig("true", "100.0"),
                Collections.singletonList("no-condition"),
                true,
                false,
                /* We can see we still have old control group check stored in the profile */ true,
                /*  We can see we still have old control group check stored in the session too */ false);

        // STEP 7: then re disable control group
        performPersonalizationWithControlGroup(
                null,
                Collections.singletonList("no-condition"),
                false,
                false,
                /* We can see we still have old control group check stored in the profile */ true,
                /*  We can see we still have old control group check stored in the session too */ false);
    }

    @Test
    public void testContextEndpointAuthentication() throws Exception {
        // Create a tenant for testing
        Tenant tenant = tenantService.createTenant("TestTenant", Collections.emptyMap());
        ApiKey publicKey = tenantService.generateApiKeyWithType(tenant.getItemId(), ApiKey.ApiKeyType.PUBLIC, null);
        ApiKey privateKey = tenantService.generateApiKeyWithType(tenant.getItemId(), ApiKey.ApiKeyType.PRIVATE, null);

        // Test without any authentication
        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setSessionId(TEST_SESSION_ID);

        HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.RequestResponse response = TestUtils.executeContextJSONRequest(request, TEST_SESSION_ID);
        Assert.assertEquals("Unauthenticated request should be rejected", 401, response.getStatusCode());

        // Test with JAAS authentication (should succeed)
        BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("karaf", "karaf"));
        CloseableHttpClient adminClient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();

        request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        CloseableHttpResponse jaasResponse = adminClient.execute(request);
        Assert.assertEquals("JAAS authenticated request should succeed", 200, jaasResponse.getStatusLine().getStatusCode());

        // Test with public API key (should succeed)
        contextRequest.setPublicApiKey(publicKey.getKey());
        request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        response = TestUtils.executeContextJSONRequest(request, TEST_SESSION_ID);
        Assert.assertEquals("Public API key request should succeed", 200, response.getStatusCode());

        // Test with private API key (should fail for public endpoint)
        request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.setHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(
            (tenant.getItemId() + ":" + privateKey.getKey()).getBytes()));
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        response = TestUtils.executeContextJSONRequest(request, TEST_SESSION_ID);
        Assert.assertEquals("Private API key should not be accepted for public endpoint", 401, response.getStatusCode());

        // Cleanup
        tenantService.deleteTenant(tenant.getItemId());
    }

    private void performPersonalizationWithControlGroup(Map<String, String> controlGroupConfig, List<String> expectedVariants,
                                                        boolean expectedControlGroupInfoInPersoResult, boolean expectedControlGroupValueInPersoResult,
                                                        Boolean expectedControlGroupValueInProfile, Boolean expectedControlGroupValueInSession) throws Exception {
        // Test normal personalization should not have control group info in response

        HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        if (controlGroupConfig != null) {
            request.setEntity(new StringEntity(getValidatedBundleJSON("personalization-control-group.json", controlGroupConfig), ContentType.APPLICATION_JSON));
        } else {
            request.setEntity(new StringEntity(getValidatedBundleJSON("personalization-no-control-group.json", null), ContentType.APPLICATION_JSON));
        }

        TestUtils.RequestResponse response = TestUtils.executeContextJSONRequest(request);
        ContextResponse contextResponse = response.getContextResponse();

        // Check variants
        List<String> variants = contextResponse.getPersonalizations().get("perso-control-group");
        assertEquals("Invalid response code", 200, response.getStatusCode());
        assertEquals("Perso should contains the good number of variants", expectedVariants.size(), variants.size());
        for (int i = 0; i < expectedVariants.size(); i++) {
            assertEquals("Variant is not the expected one", expectedVariants.get(i), variants.get(i));
        }
        PersonalizationResult personalizationResult = contextResponse.getPersonalizationResults().get("perso-control-group");
        variants = personalizationResult.getContentIds();
        assertEquals("Perso should contains the good number of variants", expectedVariants.size(), variants.size());
        for (int i = 0; i < expectedVariants.size(); i++) {
            assertEquals("Variant is not the expected one", expectedVariants.get(i), variants.get(i));
        }
        // Check control group info
        assertEquals("Perso result should contains control group info", expectedControlGroupInfoInPersoResult, personalizationResult.getAdditionalResultInfos().containsKey(PersonalizationResult.ADDITIONAL_RESULT_INFO_IN_CONTROL_GROUP));
        assertEquals("Perso should not be in control group then", expectedControlGroupValueInPersoResult, contextResponse.getPersonalizationResults().get("perso-control-group").isInControlGroup());

        // Check control group state on profile
        keepTrying("Incorrect control group on profile",
                () -> profileService.load(TEST_PROFILE_ID), storedProfile -> expectedControlGroupValueInProfile == getPersistedControlGroupStatus(storedProfile, "perso-control-group"),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // Check control group state on session
        keepTrying("Incorrect control group status on session",
                () -> persistenceService.load(TEST_SESSION_ID, Session.class), storedSession -> expectedControlGroupValueInSession == getPersistedControlGroupStatus(storedSession, "perso-control-group"),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @Test
    public void testConcealedProperties() throws Exception {
        String sessionId = "test-concealed-property-session-id";
        // Add custom profile property type
        PropertyType customPropertyType = new PropertyType(new Metadata("customProperty"));
        customPropertyType.setValueTypeId("text");
        profileService.setPropertyType(customPropertyType);
        // New profile with the custom property type
        Profile profile = new Profile("test-profile-id" + System.currentTimeMillis());
        profile.setProperty("customProperty", "concealedValue");
        profileService.save(profile);

        Thread.sleep(2000);
        // Get it from all properties
        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setRequiredProfileProperties(Arrays.asList("*"));
        contextRequest.setProfileId(profile.getItemId());
        contextRequest.setSessionId(sessionId);
        HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        assertEquals(TestUtils.executeContextJSONRequest(request, sessionId).getContextResponse().getProfileProperties().get("customProperty"), ("concealedValue"));
        // set the property as  concealed
        customPropertyType.getMetadata().getSystemTags().add("concealed");
        profileService.deletePropertyType(customPropertyType.getItemId());
        profileService.setPropertyType(customPropertyType);
        // Not in all properties
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        assertNull(TestUtils.executeContextJSONRequest(request, sessionId).getContextResponse().getProfileProperties().get("customProperty"));
        // Got it explicitly
        contextRequest.setRequiredProfileProperties(Arrays.asList("customProperty"));
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        assertEquals(TestUtils.executeContextJSONRequest(request, sessionId).getContextResponse().getProfileProperties().get("customProperty"), ("concealedValue"));
        // Got it with all
        contextRequest.setRequiredProfileProperties(Arrays.asList("*", "customProperty"));
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        assertEquals(TestUtils.executeContextJSONRequest(request, sessionId).getContextResponse().getProfileProperties().get("customProperty"), ("concealedValue"));

        // remove the concealed tag on the property type
        customPropertyType.getMetadata().getSystemTags().remove("concealed");
        profileService.deletePropertyType(customPropertyType.getItemId());
        profileService.setPropertyType(customPropertyType);

        // Got it from all properties
        contextRequest.setRequiredProfileProperties(Arrays.asList("*"));
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        assertEquals(TestUtils.executeContextJSONRequest(request, sessionId).getContextResponse().getProfileProperties().get("customProperty"), ("concealedValue"));
    }

    @Test
    public void testContextRequestWithPublicApiKey() throws Exception {
        // Create tenant with API keys
        Tenant tenant = tenantService.createTenant("ContextApiKeyTest", Collections.emptyMap());
        ApiKey publicKey = tenantService.getApiKey(tenant.getItemId(), ApiKey.ApiKeyType.PUBLIC);

        // Create context request with public API key
        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setSessionId(TEST_SESSION_ID);
        contextRequest.setPublicApiKey(publicKey.getKey());

        // Send request
        HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.RequestResponse response = TestUtils.executeContextJSONRequest(request, TEST_SESSION_ID);

        // Verify response
        ContextResponse contextResponse = response.getContextResponse();
        assertNotNull("Context response should not be null", contextResponse);

        // Test with invalid API key
        contextRequest.setPublicApiKey("invalid-key");
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        response = TestUtils.executeContextJSONRequest(request, TEST_SESSION_ID);

        // Verify error response for invalid key
        assertEquals("Should receive unauthorized response", 401, response.getStatusCode());
    }

    private Boolean getPersistedControlGroupStatus(SystemPropertiesItem systemPropertiesItem, String personalizationId) {
        if(systemPropertiesItem.getSystemProperties() != null && systemPropertiesItem.getSystemProperties().containsKey("personalizationStrategyStatus")) {
            List<Map<String, Object>> personalizationStrategyStatus = (List<Map<String, Object>>) systemPropertiesItem.getSystemProperties().get("personalizationStrategyStatus");
            for (Map<String, Object> strategyStatus : personalizationStrategyStatus) {
                if (personalizationId.equals(strategyStatus.get("personalizationId"))) {
                    return strategyStatus.containsKey("inControlGroup") && ((boolean) strategyStatus.get("inControlGroup"));
                }
            }
        }
        return null;
    }

    private Map<String, String> generateControlGroupConfig(String storeInSession, String percentage) {
        Map<String, String> controlGroupConfig = new HashMap<>();
        controlGroupConfig.put("storeInSession", storeInSession);
        controlGroupConfig.put("percentage", percentage);
        return controlGroupConfig;
    }
}
