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

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.unomi.api.*;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.segments.Scoring;
import org.apache.unomi.api.segments.Segment;
<<<<<<< HEAD
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.PersistenceService;
=======
import org.apache.unomi.persistence.spi.CustomObjectMapper;
>>>>>>> 40130ee8d (UNOMI-690, UNOMI-696: refactor control group (#531))
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import java.io.File;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

import static org.hamcrest.core.IsCollectionContaining.hasItem;
<<<<<<< HEAD
import static org.junit.Assert.*;

=======
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
>>>>>>> 40130ee8d (UNOMI-690, UNOMI-696: refactor control group (#531))

/**
 * Created by Ron Barabash on 5/4/2020.
 */

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ContextServletIT extends BaseIT {
	private final static String CONTEXT_URL = "/context.json";
	private final static String THIRD_PARTY_HEADER_NAME = "X-Unomi-Peer";
	private final static String SEGMENT_EVENT_TYPE = "test-event-type";
	private final static String SEGMENT_ID = "test-segment-id";
	private final static int SEGMENT_NUMBER_OF_DAYS = 30;

<<<<<<< HEAD
	private ObjectMapper objectMapper = new ObjectMapper();
=======
    private final static String THIRD_PARTY_HEADER_NAME = "X-Unomi-Peer";
    private final static String TEST_EVENT_TYPE = "testEventType";
    private final static String TEST_EVENT_TYPE_SCHEMA = "schemas/events/test-event-type.json";
    private final static String FLOAT_PROPERTY_EVENT_TYPE = "floatPropertyType";
    private final static String FLOAT_PROPERTY_EVENT_TYPE_SCHEMA = "schemas/events/float-property-type.json";
    private final static String TEST_SESSION_ID = "dummy-session";
    private final static String TEST_PROFILE_ID = "test-profile-id";
    private final static String TEST_PROFILE_FIRST_NAME = "contextServletIT_profile";
>>>>>>> 40130ee8d (UNOMI-690, UNOMI-696: refactor control group (#531))

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

<<<<<<< HEAD
	private Profile profile;
=======
        profile = new Profile(TEST_PROFILE_ID);
        profile.setProperty("firstName", TEST_PROFILE_FIRST_NAME);
        profileService.save(profile);
>>>>>>> 40130ee8d (UNOMI-690, UNOMI-696: refactor control group (#531))

	@Before
	public void setUp() throws InterruptedException {
		//Create a past-event segment
		Metadata segmentMetadata = new Metadata(SEGMENT_ID);
		Segment segment = new Segment(segmentMetadata);
		Condition segmentCondition = new Condition(definitionsService.getConditionType("pastEventCondition"));
		segmentCondition.setParameter("minimumEventCount",2);
		segmentCondition.setParameter("numberOfDays",SEGMENT_NUMBER_OF_DAYS);
		Condition pastEventEventCondition = new Condition(definitionsService.getConditionType("eventTypeCondition"));
		pastEventEventCondition.setParameter("eventTypeId",SEGMENT_EVENT_TYPE);
		segmentCondition.setParameter("eventCondition",pastEventEventCondition);
		segment.setCondition(segmentCondition);
		segmentService.setSegmentDefinition(segment);

		String profileId = "test-profile-id";
		profile = new Profile(profileId);
		profileService.save(profile);

		refreshPersistence();
	}

<<<<<<< HEAD
	@After
	public void tearDown() {
		TestUtils.removeAllEvents(definitionsService, persistenceService);
		TestUtils.removeAllSessions(definitionsService, persistenceService);
		TestUtils.removeAllProfiles(definitionsService, persistenceService);
		profileService.delete(profile.getItemId(), false);
		segmentService.removeSegmentDefinition(SEGMENT_ID,false);
	}
=======
    @After
    public void tearDown() throws InterruptedException {
        TestUtils.removeAllEvents(definitionsService, persistenceService);
        TestUtils.removeAllSessions(definitionsService, persistenceService);
        TestUtils.removeAllProfiles(definitionsService, persistenceService);
        profileService.delete(profile.getItemId(), false);
        removeItems(Session.class);
        segmentService.removeSegmentDefinition(SEGMENT_ID, false);
>>>>>>> 40130ee8d (UNOMI-690, UNOMI-696: refactor control group (#531))

	@Test
	public void testUpdateEventFromContextAuthorizedThirdParty_Success() throws IOException, InterruptedException {
		//Arrange
		String eventId = "test-event-id-" + System.currentTimeMillis();
		String profileId = "test-profile-id";
		String sessionId = "test-session-id";
		String scope = "test-scope";
		String eventTypeOriginal = "test-event-type-original";
		String eventTypeUpdated = "test-event-type-updated";
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
		request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.create("application/json")));
		TestUtils.executeContextJSONRequest(request, sessionId);
		refreshPersistence();
		Thread.sleep(2000); //Making sure event is updated in DB

		//Assert
		event = this.eventService.getEvent(eventId);
		assertEquals(2, event.getVersion().longValue());
		assertEquals(eventTypeUpdated,event.getEventType());
	}

	@Test
	public void testUpdateEventFromContextUnAuthorizedThirdParty_Fail() throws IOException, InterruptedException {
		//Arrange
		String eventId = "test-event-id-" + System.currentTimeMillis();
		String profileId = "test-profile-id";
		String sessionId = "test-session-id";
		String scope = "test-scope";
		String eventTypeOriginal = "test-event-type-original";
		String eventTypeUpdated = "test-event-type-updated";
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
		request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.create("application/json")));
		TestUtils.executeContextJSONRequest(request, sessionId);
		refreshPersistence();
		Thread.sleep(2000); //Making sure event is updated in DB

		//Assert
		event = this.eventService.getEvent(eventId);
		assertEquals(1, event.getVersion().longValue());
		assertEquals(eventTypeOriginal,event.getEventType());
	}


	@Test
	public void testUpdateEventFromContextAuthorizedThirdPartyNoItemID_Fail() throws IOException, InterruptedException {
		//Arrange
		String eventId = "test-event-id-" + System.currentTimeMillis();
		String sessionId = "test-session-id";
		String scope = "test-scope";
		String eventTypeOriginal = "test-event-type-original";
		String eventTypeUpdated = "test-event-type-updated";
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
		request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.create("application/json")));
		TestUtils.executeContextJSONRequest(request, sessionId);
		refreshPersistence();
		Thread.sleep(2000); //Making sure event is updated in DB

		//Assert
		event = this.eventService.getEvent(eventId);
		assertEquals(1, event.getVersion().longValue());
		assertEquals(eventTypeOriginal,event.getEventType());
	}

	@Test
	public void testCreateEventsWithNoTimestampParam_profileAddedToSegment() throws IOException, InterruptedException {
		//Arrange
		String sessionId = "test-session-id";
		String scope = "test-scope";
		Event event = new Event();
		event.setEventType(SEGMENT_EVENT_TYPE);
		event.setScope(scope);

		//Act
		ContextRequest contextRequest = new ContextRequest();
		contextRequest.setSessionId(sessionId);
		contextRequest.setRequireSegments(true);
		contextRequest.setEvents(Arrays.asList(event));
		HttpPost request = new HttpPost(URL + CONTEXT_URL);
		request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.create("application/json")));
		String cookieHeaderValue = TestUtils.executeContextJSONRequest(request, sessionId).getCookieHeaderValue();
		refreshPersistence();
		Thread.sleep(1000); //Making sure DB is updated

		//Add the context-profile-id cookie to the second event
		request.addHeader("Cookie", cookieHeaderValue);
		ContextResponse response = (TestUtils.executeContextJSONRequest(request, sessionId)).getContextResponse(); //second event

		refreshPersistence();

		//Assert
		assertEquals(1, response.getProfileSegments().size());
		assertThat(response.getProfileSegments(),hasItem(SEGMENT_ID));
	}

	@Test
	public void testCreateEventWithTimestampParam_pastEvent_profileIsNotAddedToSegment() throws IOException, InterruptedException {
		//Arrange
		String sessionId = "test-session-id";
		String scope = "test-scope";
		Event event = new Event();
		event.setEventType(SEGMENT_EVENT_TYPE);
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
		request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.create("application/json")));
		//The first event is with a default timestamp (now)
		String cookieHeaderValue = TestUtils.executeContextJSONRequest(request, sessionId).getCookieHeaderValue();
		refreshPersistence();
		//The second event is with a customized timestamp
		request.setURI(URI.create(customTimestampURI));
		request.addHeader("Cookie", cookieHeaderValue);
		ContextResponse response = (TestUtils.executeContextJSONRequest(request, sessionId)).getContextResponse(); //second event
		refreshPersistence();

		//Assert
		assertEquals(0,response.getProfileSegments().size());
	}

	@Test
	public void testCreateEventWithTimestampParam_futureEvent_profileIsNotAddedToSegment() throws IOException, InterruptedException {
		//Arrange
		String sessionId = "test-session-id";
		String scope = "test-scope";
		Event event = new Event();
		event.setEventType(SEGMENT_EVENT_TYPE);
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
		request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.create("application/json")));
		//The first event is with a default timestamp (now)
		String cookieHeaderValue = TestUtils.executeContextJSONRequest(request, sessionId).getCookieHeaderValue();
		refreshPersistence();
		//The second event is with a customized timestamp
		request.setURI(URI.create(customTimestampURI));
		request.addHeader("Cookie", cookieHeaderValue);
		ContextResponse response = (TestUtils.executeContextJSONRequest(request, sessionId)).getContextResponse(); //second event
		refreshPersistence();

		//Assert
		assertEquals(0,response.getProfileSegments().size());
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
		request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.create("application/json")));
		TestUtils.executeContextJSONRequest(request);
		refreshPersistence();
		Thread.sleep(2000); //Making sure event is updated in DB

		//Assert
		Profile profile =  this.profileService.load(profileId);
		assertEquals(profileId, profile.getItemId());
	}

	@Test
	public void testOGNLVulnerability() throws IOException, InterruptedException {

		File vulnFile = new File("target/vuln-file.txt");
		if (vulnFile.exists()) {
			vulnFile.delete();
		}
		String vulnFileCanonicalPath = vulnFile.getCanonicalPath();
		vulnFileCanonicalPath = vulnFileCanonicalPath.replace("\\", "\\\\"); // this is required for Windows support

		Map<String,String> parameters = new HashMap<>();
		parameters.put("VULN_FILE_PATH", vulnFileCanonicalPath);
		HttpPost request = new HttpPost(URL + CONTEXT_URL);
		request.setEntity(new StringEntity(getValidatedBundleJSON("security/ognl-payload-1.json", parameters), ContentType.create("application/json")));
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

		Map<String,String> parameters = new HashMap<>();
		parameters.put("VULN_FILE_PATH", vulnFileCanonicalPath);
		HttpPost request = new HttpPost(URL + CONTEXT_URL);
		request.setEntity(new StringEntity(getValidatedBundleJSON("security/mvel-payload-1.json", parameters), ContentType.create("application/json")));
		TestUtils.executeContextJSONRequest(request);
		refreshPersistence();
		Thread.sleep(2000); //Making sure event is updated in DB

		assertFalse("Vulnerability successfully executed ! File created at " + vulnFileCanonicalPath, vulnFile.exists());

	}

	@Test
	public void testPersonalization() throws IOException, InterruptedException {

		Map<String,String> parameters = new HashMap<>();
		HttpPost request = new HttpPost(URL + CONTEXT_URL);
		request.setEntity(new StringEntity(getValidatedBundleJSON("personalization.json", parameters), ContentType.create("application/json")));
		TestUtils.RequestResponse response = TestUtils.executeContextJSONRequest(request);
		assertEquals("Invalid response code", 200, response.getStatusCode());
		refreshPersistence();
		Thread.sleep(2000); //Making sure event is updated in DB

	}

    @Test
    public void testPersonalizationWithControlGroup() throws IOException, InterruptedException {

<<<<<<< HEAD
        Map<String,String> parameters = new HashMap<>();
        parameters.put("storeInSession", "false");
        HttpPost request = new HttpPost(URL + CONTEXT_URL);
        request.setEntity(new StringEntity(getValidatedBundleJSON("personalization-controlgroup.json", parameters), ContentType.create("application/json")));
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
        request.setEntity(new StringEntity(getValidatedBundleJSON("personalization-controlgroup.json", parameters), ContentType.create("application/json")));
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
=======
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
>>>>>>> 40130ee8d (UNOMI-690, UNOMI-696: refactor control group (#531))
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
        request.setEntity(new StringEntity(getValidatedBundleJSON("withoutRequireScores.json", parameters), ContentType.create("application/json")));
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
        request.setEntity(new StringEntity(getValidatedBundleJSON("withRequireScores.json", parameters), ContentType.create("application/json")));
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

<<<<<<< HEAD
=======
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
>>>>>>> 40130ee8d (UNOMI-690, UNOMI-696: refactor control group (#531))
}
