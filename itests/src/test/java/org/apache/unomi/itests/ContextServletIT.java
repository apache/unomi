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
import org.apache.unomi.api.ContextRequest;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;

import static org.junit.Assert.assertEquals;


/**
 * Created by Ron Barabash on 5/4/2020.
 */

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ContextServletIT extends BaseIT {
	private final static String CONTEXT_URL = "/context.json";
	private final static String THIRD_PARTY_HEADER_NAME = "X-Unomi-Peer";

	private ObjectMapper objectMapper = new ObjectMapper();
	private TestUtils testUtils = new TestUtils();

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

	@After
	public void tearDown() {
		TestUtils.removeAllEvents(definitionsService, persistenceService);
		TestUtils.removeAllSessions(definitionsService, persistenceService);
		TestUtils.removeAllProfiles(definitionsService, persistenceService);
		persistenceService.refresh();
	}

	@Test
	public void testUpdateEventFromContextAuthorizedThirdParty_Success() throws IOException, InterruptedException {
		//Arrange
		String eventId = "test-event-id1";
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
		Thread.sleep(2000);
		event.setEventType(eventTypeUpdated); //change the event so we can see the update effect

		//Act
		ContextRequest contextRequest = new ContextRequest();
		contextRequest.setSessionId(session.getItemId());
		contextRequest.setEvents(Arrays.asList(event));
		HttpPost request = new HttpPost(URL + CONTEXT_URL);
		request.addHeader(THIRD_PARTY_HEADER_NAME, UNOMI_KEY);
		request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.create("application/json")));
		this.testUtils.executeContextJSONRequest(request, sessionId);
		Thread.sleep(2000); //Making sure event is updated in DB

		//Assert
		event = this.eventService.getEvent(eventId);
		assertEquals(2, event.getVersion().longValue());
		assertEquals(eventTypeUpdated,event.getEventType());
	}

	@Test
	public void testUpdateEventFromContextUnAuthorizedThirdParty_Fail() throws IOException, InterruptedException {
		//Arrange
		String eventId = "test-event-id2";
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
		Thread.sleep(2000);
		event.setEventType(eventTypeUpdated); //change the event so we can see the update effect

		//Act
		ContextRequest contextRequest = new ContextRequest();
		contextRequest.setSessionId(session.getItemId());
		contextRequest.setEvents(Arrays.asList(event));
		HttpPost request = new HttpPost(URL + CONTEXT_URL);
		request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.create("application/json")));
		this.testUtils.executeContextJSONRequest(request, sessionId);
		Thread.sleep(2000); //Making sure event is updated in DB

		//Assert
		event = this.eventService.getEvent(eventId);
		assertEquals(1, event.getVersion().longValue());
		assertEquals(eventTypeOriginal,event.getEventType());
	}


	@Test
	public void testUpdateEventFromContextAuthorizedThirdPartyNoItemID_Fail() throws IOException, InterruptedException {
		//Arrange
		String eventId = "test-event-id3";
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
		Thread.sleep(2000);
		event.setEventType(eventTypeUpdated); //change the event so we can see the update effect

		//Act
		ContextRequest contextRequest = new ContextRequest();
		contextRequest.setSessionId(session.getItemId());
		contextRequest.setEvents(Arrays.asList(event));
		HttpPost request = new HttpPost(URL + CONTEXT_URL);
		request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.create("application/json")));
		this.testUtils.executeContextJSONRequest(request, sessionId);
		Thread.sleep(2000); //Making sure event is updated in DB

		//Assert
		event = this.eventService.getEvent(eventId);
		assertEquals(1, event.getVersion().longValue());
		assertEquals(eventTypeOriginal,event.getEventType());
	}
}
