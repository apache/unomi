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
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.services.SegmentService;
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
	private final static String CONTEXT_URL = "/context.json";
	private final static String THIRD_PARTY_HEADER_NAME = "X-Unomi-Peer";
	private final static String SEGMENT_EVENT_TYPE = "test-event-type";
	private final static String SEGMENT_ID = "test-segment-id";
	private final static int SEGMENT_NUMBER_OF_DAYS = 30;

	private final static String TEST_SESSION_ID = "dummy-session";
	private final static String TEST_PROFILE_ID = "test-profile-id";
	private final static String TEST_PROFILE_FIRST_NAME = "contextServletIT_profile";

	private ObjectMapper objectMapper = new ObjectMapper();

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

	private Profile profile;

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
		profile.setProperty("firstName", TEST_PROFILE_FIRST_NAME);
		profileService.save(profile);

		refreshPersistence();
	}

	@After
	public void tearDown() {
		TestUtils.removeAllEvents(definitionsService, persistenceService);
		TestUtils.removeAllSessions(definitionsService, persistenceService);
		TestUtils.removeAllProfiles(definitionsService, persistenceService);
		profileService.delete(profile.getItemId(), false);
		segmentService.removeSegmentDefinition(SEGMENT_ID,false);
	}

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
                1000, 10);

        // Check control group state on session
        keepTrying("Incorrect control group status on session",
                () -> persistenceService.load(TEST_SESSION_ID, Session.class), storedSession -> expectedControlGroupValueInSession == getPersistedControlGroupStatus(storedSession, "perso-control-group"),
                1000, 10);
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
