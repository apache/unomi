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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.unomi.api.*;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.segments.Scoring;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.services.*;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
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
    private final static String JSONSCHEMA_URL = "/cxs/jsonSchema";

    private final static String THIRD_PARTY_HEADER_NAME = "X-Unomi-Peer";
    private final static String TEST_EVENT_TYPE = "test-event-type";
    private final static String TEST_EVENT_TYPE_SCHEMA = "test-event-type.json";
    private final static String FLOAT_PROPERTY_EVENT_TYPE = "float-property-type";
    private final static String FLOAT_PROPERTY_EVENT_TYPE_SCHEMA = "float-property-type.json";
    private final static String SEGMENT_ID = "test-segment-id";
    private final static int SEGMENT_NUMBER_OF_DAYS = 30;

    private static final int DEFAULT_TRYING_TIMEOUT = 2000;
    private static final int DEFAULT_TRYING_TRIES = 30;

    private ObjectMapper objectMapper = new ObjectMapper();

    private final static Logger LOGGER = LoggerFactory.getLogger(ContextServletIT.class);

    @Inject
    @Filter(timeout = 600000)
    protected EventService eventService;

    @Inject
    @Filter(timeout = 600000)
    protected PersistenceService persistenceService;

    @Inject
    @Filter(timeout = 600000)
    protected ProfileService profileService;

    @Inject
    @Filter(timeout = 600000)
    protected DefinitionsService definitionsService;

    @Inject
    @Filter(timeout = 600000)
    protected SegmentService segmentService;

    @Inject
    @Filter(timeout = 600000)
    protected BundleContext bundleContext;

    private Profile profile;

    @Before
    public void setUp() throws InterruptedException, IOException {
        this.registerEventType(TEST_EVENT_TYPE_SCHEMA);
        this.registerEventType(FLOAT_PROPERTY_EVENT_TYPE_SCHEMA);

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

        String profileId = "test-profile-id";
        profile = new Profile(profileId);
        profileService.save(profile);

        keepTrying("Couldn't find json schema endpoint",
                () -> get(JSONSCHEMA_URL, List.class), Objects::nonNull,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        refreshPersistence();
    }

    @After
    public void tearDown() {
        TestUtils.removeAllEvents(definitionsService, persistenceService);
        TestUtils.removeAllSessions(definitionsService, persistenceService);
        TestUtils.removeAllProfiles(definitionsService, persistenceService);
        profileService.delete(profile.getItemId(), false);
        segmentService.removeSegmentDefinition(SEGMENT_ID, false);

        String encodedString = Base64.getEncoder()
                .encodeToString("https://unomi.apache.org/schemas/json/events/test-event-type/1-0-0".getBytes());
        delete(JSONSCHEMA_URL + "/" + encodedString);

        encodedString = Base64.getEncoder()
                .encodeToString("https://unomi.apache.org/schemas/json/events/float-property-type/1-0-0".getBytes());
        delete(JSONSCHEMA_URL + "/" + encodedString);

        encodedString = Base64.getEncoder()
                .encodeToString("https://unomi.apache.org/schemas/json/events/float-property-type/1-0-0".getBytes());
        delete(JSONSCHEMA_URL + "/" + encodedString);
    }

    private void registerEventType(String jsonSchemaFileName) {
        post(JSONSCHEMA_URL, "schemas/events/" + jsonSchemaFileName, ContentType.TEXT_PLAIN);
    }

    @Test
    public void testUpdateEventFromContextAuthorizedThirdParty_Success() throws IOException, InterruptedException {
        //Arrange
        String eventId = "test-event-id-" + System.currentTimeMillis();
        String profileId = "test-profile-id";
        String sessionId = "test-session-id";
        String scope = "test-scope";
        String eventTypeOriginal = "test-event-type-original";
        String eventTypeUpdated = TEST_EVENT_TYPE;
        Profile profile = new Profile(profileId);
        Session session = new Session(sessionId, profile, new Date(), scope);
        Event event = new Event(eventId, eventTypeOriginal, session, profile, scope, null, null, new Date());
        profileService.save(profile);
        this.eventService.send(event);
        refreshPersistence();
        Thread.sleep(2000);
        event.setEventType(eventTypeUpdated); //change the event so we can see the update effect

        //Act
        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setSessionId(session.getItemId());
        contextRequest.setEvents(Arrays.asList(event));
        HttpPost request = new HttpPost(URL + CONTEXT_URL);
        request.addHeader(THIRD_PARTY_HEADER_NAME, UNOMI_KEY);
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.executeContextJSONRequest(request, sessionId);
        refreshPersistence();
        Thread.sleep(2000); //Making sure event is updated in DB

        //Assert
        event = this.eventService.getEvent(eventId);
        assertEquals(2, event.getVersion().longValue());
        assertEquals(eventTypeUpdated, event.getEventType());
    }

    @Test
    public void testUpdateEventFromContextUnAuthorizedThirdParty_Fail() throws IOException, InterruptedException {
        //Arrange
        String eventId = "test-event-id-" + System.currentTimeMillis();
        String profileId = "test-profile-id";
        String sessionId = "test-session-id";
        String scope = "test-scope";
        String eventTypeOriginal = "test-event-type-original";
        String eventTypeUpdated = TEST_EVENT_TYPE;
        Profile profile = new Profile(profileId);
        Session session = new Session(sessionId, profile, new Date(), scope);
        Event event = new Event(eventId, eventTypeOriginal, session, profile, scope, null, null, new Date());
        profileService.save(profile);
        this.eventService.send(event);
        refreshPersistence();
        Thread.sleep(2000);
        event.setEventType(eventTypeUpdated); //change the event so we can see the update effect

        //Act
        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setSessionId(session.getItemId());
        contextRequest.setEvents(Arrays.asList(event));
        HttpPost request = new HttpPost(URL + CONTEXT_URL);
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.executeContextJSONRequest(request, sessionId);
        refreshPersistence();
        Thread.sleep(2000); //Making sure event is updated in DB

        //Assert
        event = this.eventService.getEvent(eventId);
        assertEquals(1, event.getVersion().longValue());
        assertEquals(eventTypeOriginal, event.getEventType());
    }


    @Test
    public void testUpdateEventFromContextAuthorizedThirdPartyNoItemID_Fail() throws IOException, InterruptedException {
        //Arrange
        String eventId = "test-event-id-" + System.currentTimeMillis();
        String sessionId = "test-session-id";
        String scope = "test-scope";
        String eventTypeOriginal = "test-event-type-original";
        String eventTypeUpdated = TEST_EVENT_TYPE;
        Session session = new Session(sessionId, profile, new Date(), scope);
        Event event = new Event(eventId, eventTypeOriginal, session, profile, scope, null, null, new Date());
        this.eventService.send(event);
        refreshPersistence();
        Thread.sleep(2000);
        event.setEventType(eventTypeUpdated); //change the event so we can see the update effect

        //Act
        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setSessionId(session.getItemId());
        contextRequest.setEvents(Arrays.asList(event));
        HttpPost request = new HttpPost(URL + CONTEXT_URL);
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.executeContextJSONRequest(request, sessionId);
        refreshPersistence();
        Thread.sleep(2000); //Making sure event is updated in DB

        //Assert
        event = this.eventService.getEvent(eventId);
        assertEquals(1, event.getVersion().longValue());
        assertEquals(eventTypeOriginal, event.getEventType());
    }

    @Test
    public void testCreateEventsWithNoTimestampParam_profileAddedToSegment() throws IOException, InterruptedException {
        //Arrange
        String sessionId = "test-session-id";
        String scope = "test-scope";
        Event event = new Event();
        event.setEventType(TEST_EVENT_TYPE);
        event.setScope(scope);

        //Act
        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setSessionId(sessionId);
        contextRequest.setRequireSegments(true);
        contextRequest.setEvents(Arrays.asList(event));
        HttpPost request = new HttpPost(URL + CONTEXT_URL);
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        String cookieHeaderValue = TestUtils.executeContextJSONRequest(request, sessionId).getCookieHeaderValue();
        refreshPersistence();
        Thread.sleep(1000); //Making sure DB is updated

        //Add the context-profile-id cookie to the second event
        request.addHeader("Cookie", cookieHeaderValue);
        ContextResponse response = (TestUtils.executeContextJSONRequest(request, sessionId)).getContextResponse(); //second event

        refreshPersistence();

        //Assert
        assertEquals(1, response.getProfileSegments().size());
        assertThat(response.getProfileSegments(), hasItem(SEGMENT_ID));
    }

    @Test
    public void testCreateEventWithTimestampParam_pastEvent_profileIsNotAddedToSegment() throws IOException, InterruptedException {
        //Arrange
        String sessionId = "test-session-id";
        String scope = "test-scope";
        Event event = new Event();
        event.setEventType(TEST_EVENT_TYPE);
        event.setScope(scope);
        String regularURI = URL + CONTEXT_URL;
        long oldTimestamp = LocalDateTime.now(ZoneId.of("UTC")).minusDays(SEGMENT_NUMBER_OF_DAYS + 1).toInstant(ZoneOffset.UTC).toEpochMilli();
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
        refreshPersistence();
        //The second event is with a customized timestamp
        request.setURI(URI.create(customTimestampURI));
        request.addHeader("Cookie", cookieHeaderValue);
        ContextResponse response = (TestUtils.executeContextJSONRequest(request, sessionId)).getContextResponse(); //second event
        refreshPersistence();

        //Assert
        assertEquals(0, response.getProfileSegments().size());
    }

    @Test
    public void testCreateEventWithTimestampParam_futureEvent_profileIsNotAddedToSegment() throws IOException, InterruptedException {
        //Arrange
        String sessionId = "test-session-id";
        String scope = "test-scope";
        Event event = new Event();
        event.setEventType(TEST_EVENT_TYPE);
        event.setScope(scope);
        String regularURI = URL + CONTEXT_URL;
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
        TestUtils.RequestResponse response = TestUtils.executeContextJSONRequest(request, sessionId);
        String cookieHeaderValue = response.getCookieHeaderValue();
        refreshPersistence();
        //The second event is with a customized timestamp
        request.setURI(URI.create(customTimestampURI));
        request.addHeader("Cookie", cookieHeaderValue);
        response = (TestUtils.executeContextJSONRequest(request, sessionId)); //second event
        refreshPersistence();

        //Assert
        assertEquals(0, response.getContextResponse().getProfileSegments().size());
    }

    @Test
    public void testCreateEventWithProfileId_Success() throws IOException, InterruptedException {
        //Arrange
        String eventId = "test-event-id-" + System.currentTimeMillis();
        String profileId = "test-profile-id";
        String eventType = "test-event-type";
        Event event = new Event();
        event.setEventType(eventType);
        event.setItemId(eventId);

        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setProfileId(profileId);
        contextRequest.setEvents(Arrays.asList(event));

        //Act
        HttpPost request = new HttpPost(URL + CONTEXT_URL);
        request.addHeader(THIRD_PARTY_HEADER_NAME, UNOMI_KEY);
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.executeContextJSONRequest(request);
        refreshPersistence();
        Thread.sleep(2000); //Making sure event is updated in DB

        //Assert
        Profile profile = this.profileService.load(profileId);
        assertEquals(profileId, profile.getItemId());
    }

    @Test
    public void testCreateEventWithPropertiesValidation_Success() throws IOException, InterruptedException {
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
        HttpPost request = new HttpPost(URL + CONTEXT_URL);
        request.addHeader(THIRD_PARTY_HEADER_NAME, UNOMI_KEY);
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.executeContextJSONRequest(request);
        refreshPersistence();
        Thread.sleep(2000); //Making sure event is updated in DB

        //Assert
        event = this.eventService.getEvent(eventId);
        assertEquals(eventType, event.getEventType());
        assertEquals(3.14159, event.getProperty("floatProperty"));
    }

    @Test
    public void testCreateEventWithPropertyValueValidation_Failure() throws IOException, InterruptedException {
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
        HttpPost request = new HttpPost(URL + CONTEXT_URL);
        request.addHeader(THIRD_PARTY_HEADER_NAME, UNOMI_KEY);
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.executeContextJSONRequest(request);
        refreshPersistence();
        Thread.sleep(2000); //Making sure event is updated in DB

        //Assert
        event = this.eventService.getEvent(eventId);
        assertNull(event);
    }

    @Test
    public void testCreateEventWithPropertyNameValidation_Failure() throws IOException, InterruptedException {
        //Arrange
        String eventId = "invalid-event-prop-id-" + System.currentTimeMillis();
        String profileId = "invalid-profile-id";
        String eventType = FLOAT_PROPERTY_EVENT_TYPE;
        Event event = new Event();
        event.setEventType(eventType);
        event.setItemId(eventId);
        Map<String, Object> props = new HashMap<>();
        props.put("ffloatProperty", 3.14159);
        event.setProperties(props);

        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setProfileId(profileId);
        contextRequest.setEvents(Arrays.asList(event));

        //Act
        HttpPost request = new HttpPost(URL + CONTEXT_URL);
        request.addHeader(THIRD_PARTY_HEADER_NAME, UNOMI_KEY);
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.executeContextJSONRequest(request);
        refreshPersistence();
        Thread.sleep(2000); //Making sure event is updated in DB

        //Assert
        event = this.eventService.getEvent(eventId);
        assertNull(event);
    }

    @Test
    public void testOGNLVulnerability() throws IOException, InterruptedException {

        File vulnFile = new File("target/vuln-file.txt");
        if (vulnFile.exists()) {
            vulnFile.delete();
        }
        String vulnFileCanonicalPath = vulnFile.getCanonicalPath();
        vulnFileCanonicalPath = vulnFileCanonicalPath.replace("\\", "\\\\"); // this is required for Windows support

        Map<String, String> parameters = new HashMap<>();
        parameters.put("VULN_FILE_PATH", vulnFileCanonicalPath);
        HttpPost request = new HttpPost(URL + CONTEXT_URL);
        request.setEntity(new StringEntity(getValidatedBundleJSON("security/ognl-payload-1.json", parameters), ContentType.APPLICATION_JSON));
        TestUtils.executeContextJSONRequest(request);
        refreshPersistence();
        Thread.sleep(2000); //Making sure event is updated in DB

        assertFalse("Vulnerability successfully executed ! File created at " + vulnFileCanonicalPath, vulnFile.exists());

    }

    @Test
    public void testMVELVulnerability() throws IOException, InterruptedException {

        File vulnFile = new File("target/vuln-file.txt");
        if (vulnFile.exists()) {
            vulnFile.delete();
        }
        String vulnFileCanonicalPath = vulnFile.getCanonicalPath();
        vulnFileCanonicalPath = vulnFileCanonicalPath.replace("\\", "\\\\"); // this is required for Windows support

        Map<String, String> parameters = new HashMap<>();
        parameters.put("VULN_FILE_PATH", vulnFileCanonicalPath);
        HttpPost request = new HttpPost(URL + CONTEXT_URL);
        request.setEntity(new StringEntity(getValidatedBundleJSON("security/mvel-payload-1.json", parameters), ContentType.APPLICATION_JSON));
        TestUtils.executeContextJSONRequest(request);
        refreshPersistence();
        Thread.sleep(2000); //Making sure event is updated in DB

        assertFalse("Vulnerability successfully executed ! File created at " + vulnFileCanonicalPath, vulnFile.exists());

    }

	@Test
	public void testPersonalization() throws IOException, InterruptedException {

		Map<String,String> parameters = new HashMap<>();
		HttpPost request = new HttpPost(URL + CONTEXT_URL);
		request.setEntity(new StringEntity(getValidatedBundleJSON("personalization.json", parameters), ContentType.APPLICATION_JSON));
		TestUtils.RequestResponse response = TestUtils.executeContextJSONRequest(request);
		assertEquals("Invalid response code", 200, response.getStatusCode());
		refreshPersistence();
		Thread.sleep(2000); //Making sure event is updated in DB

	}

    @Test
    public void testPersonalizationWithControlGroup() throws IOException, InterruptedException {

        Map<String,String> parameters = new HashMap<>();
        parameters.put("storeInSession", "false");
        HttpPost request = new HttpPost(URL + CONTEXT_URL);
        request.setEntity(new StringEntity(getValidatedBundleJSON("personalization-controlgroup.json", parameters), ContentType.APPLICATION_JSON));
        TestUtils.RequestResponse response = TestUtils.executeContextJSONRequest(request);
        assertEquals("Invalid response code", 200, response.getStatusCode());
        refreshPersistence();
        Thread.sleep(2000); //Making sure event is updated in DB
        ContextResponse contextResponse = response.getContextResponse();

        Map<String,List<String>> personalizations = contextResponse.getPersonalizations();

        validatePersonalizations(personalizations);

        // let's check that the persisted profile has the control groups;
        Map<String,Object> profileProperties = contextResponse.getProfileProperties();
        List<Map<String,Object>> profileControlGroups = (List<Map<String,Object>>) profileProperties.get("unomiControlGroups");
        assertControlGroups(profileControlGroups);

        Profile updatedProfile = profileService.load(contextResponse.getProfileId());
        profileControlGroups = (List<Map<String,Object>>) updatedProfile.getProperty("unomiControlGroups");
        assertNotNull("Profile control groups not found in persisted profile", profileControlGroups);
        assertControlGroups(profileControlGroups);

        // now let's test with session storage
        parameters.put("storeInSession", "true");
        request = new HttpPost(URL + CONTEXT_URL);
        request.setEntity(new StringEntity(getValidatedBundleJSON("personalization-controlgroup.json", parameters), ContentType.APPLICATION_JSON));
        response = TestUtils.executeContextJSONRequest(request);
        assertEquals("Invalid response code", 200, response.getStatusCode());
        refreshPersistence();
        Thread.sleep(2000); //Making sure event is updated in DB
        contextResponse = response.getContextResponse();

        personalizations = contextResponse.getPersonalizations();

        validatePersonalizations(personalizations);

        Map<String,Object> sessionProperties = contextResponse.getSessionProperties();
        List<Map<String,Object>> sessionControlGroups = (List<Map<String,Object>>) sessionProperties.get("unomiControlGroups");
        assertControlGroups(sessionControlGroups);

        Session updatedSession = profileService.loadSession(contextResponse.getSessionId(), new Date());
        sessionControlGroups = (List<Map<String,Object>>) updatedSession.getProperty("unomiControlGroups");
        assertNotNull("Session control groups not found in persisted session", sessionControlGroups);
        assertControlGroups(sessionControlGroups);

    }

    private void validatePersonalizations(Map<String, List<String>> personalizations) {
        assertEquals("Personalizations don't have expected size", 2, personalizations.size());

        List<String> perso1Contents = personalizations.get("perso1");
        assertEquals("Perso 1 content list size doesn't match", 10, perso1Contents.size());
        List<String> expectedPerso1Contents = new ArrayList<>();
        expectedPerso1Contents.add("perso1content1");
        expectedPerso1Contents.add("perso1content2");
        expectedPerso1Contents.add("perso1content3");
        expectedPerso1Contents.add("perso1content4");
        expectedPerso1Contents.add("perso1content5");
        expectedPerso1Contents.add("perso1content6");
        expectedPerso1Contents.add("perso1content7");
        expectedPerso1Contents.add("perso1content8");
        expectedPerso1Contents.add("perso1content9");
        expectedPerso1Contents.add("perso1content10");
        assertEquals("Perso1 contents do not match", expectedPerso1Contents, perso1Contents);
    }

    private void assertControlGroups(List<Map<String, Object>> profileControlGroups) {
        assertNotNull("Couldn't find control groups for profile", profileControlGroups);
        assertTrue("Control group size should be 1", profileControlGroups.size() == 1);
        Map<String,Object> controlGroup = profileControlGroups.get(0);
        assertEquals("Invalid ID for control group", "perso1", controlGroup.get("id"));
        assertEquals("Invalid path for control group", "/home/perso1.html", controlGroup.get("path"));
        assertEquals("Invalid displayName for control group", "First perso", controlGroup.get("displayName"));
        assertNotNull("Null timestamp for control group", controlGroup.get("timeStamp"));
    }


    @Test
    public void testRequireScoring() throws IOException, InterruptedException {

        Map<String,String> parameters = new HashMap<>();
        String scoringSource = getValidatedBundleJSON("score1.json", parameters);
        Scoring scoring = CustomObjectMapper.getObjectMapper().readValue(scoringSource, Scoring.class);
        segmentService.setScoringDefinition(scoring);
        refreshPersistence();

        // first let's make sure everything works without the requireScoring parameter
        parameters = new HashMap<>();
        HttpPost request = new HttpPost(URL + CONTEXT_URL);
        request.setEntity(new StringEntity(getValidatedBundleJSON("withoutRequireScores.json", parameters), ContentType.APPLICATION_JSON));
        TestUtils.RequestResponse response = TestUtils.executeContextJSONRequest(request);
        assertEquals("Invalid response code", 200, response.getStatusCode());
        refreshPersistence();
        Thread.sleep(2000); //Making sure event is updated in DB

        assertNotNull("Context response should not be null", response.getContextResponse());
        Map<String,Integer> scores = response.getContextResponse().getProfileScores();
        assertNull("Context response should not contain scores", scores);

        // now let's test adding it.
        parameters = new HashMap<>();
        request = new HttpPost(URL + CONTEXT_URL);
        request.setEntity(new StringEntity(getValidatedBundleJSON("withRequireScores.json", parameters), ContentType.APPLICATION_JSON));
        response = TestUtils.executeContextJSONRequest(request);
        assertEquals("Invalid response code", 200, response.getStatusCode());
        refreshPersistence();
        Thread.sleep(2000); //Making sure event is updated in DB

        assertNotNull("Context response should not be null", response.getContextResponse());
        scores = response.getContextResponse().getProfileScores();
        assertNotNull("Context response should contain scores", scores);
        assertNotNull("score1 not found in profile scores", scores.get("score1"));
        assertEquals("score1 does not have expected value", 1, scores.get("score1").intValue());

        segmentService.removeScoringDefinition(scoring.getItemId(), false);
    }

}
