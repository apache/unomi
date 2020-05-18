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
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
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

import javax.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;


/**
 * Created by Ron Barabash on 5/4/2020.
 */

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ContextServletIT extends BaseIT {
	private final static String TEST_SESSION_ID = "test-session-id";
	private final static String TEST_EVENT_ID = "test-event-id";
	private final static String TEST_SCOPE = "test-scope";
	private final static String TEST_PROFILE_ID = "test-profile-id";
	private final static String EVENT_TYPE = "view";
	private final static String THIRD_PARTY_HEADER_NAME = "X-Unomi-Peer";
	private Profile profile = new Profile(TEST_PROFILE_ID);
	private Session session = new Session(TEST_SESSION_ID, profile, new Date(), TEST_SCOPE);
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

	@Before
	public void setUp() throws InterruptedException {
		Event pageViewEvent = new Event(TEST_EVENT_ID, EVENT_TYPE, session, profile, TEST_SCOPE, null, null, new Date());

		profileService.save(profile);
		this.eventService.send(pageViewEvent);

		Thread.sleep(2000);
	}

	@After
	public void tearDown() {
		//Using remove index due to document version is still persistent after deletion as referenced here https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-delete.html#delete-versioning
		this.persistenceService.removeIndex("event-date-*");
		this.profileService.delete(TEST_PROFILE_ID, false);
		this.persistenceService.refresh();
	}

	@Test
	public void testUpdateEventFromContextAuthorizedThirdParty_Success() throws IOException, InterruptedException {
		Event event = this.eventService.getEvent(TEST_EVENT_ID);
		Assert.assertEquals(new Long(1), event.getVersion());
		Profile profile = profileService.load(TEST_PROFILE_ID);
		Event pageViewEvent = new Event(TEST_EVENT_ID, EVENT_TYPE, session, profile, TEST_SCOPE, null, null, new Date());

		ContextRequest contextRequest = new ContextRequest();
		contextRequest.setSessionId(session.getItemId());
		contextRequest.setEvents(Collections.singletonList(pageViewEvent));

		HttpPost request = new HttpPost(URL + "/context.json");
		request.addHeader(THIRD_PARTY_HEADER_NAME, UNOMI_KEY);
		request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest),
			ContentType.create("application/json")));

		//Making sure Unomi is up and running
		Thread.sleep(5000);
		this.testUtils.executeContextJSONRequest(request, TEST_SESSION_ID);

		//Making sure event is updated in DB
		Thread.sleep(2000);
		event = this.eventService.getEvent(TEST_EVENT_ID);
		Assert.assertEquals(new Long(2), event.getVersion());
	}

	@Test
	public void testUpdateEventFromContextUnAuthorizedThirdParty_Fail() throws IOException, InterruptedException {
		Event event = this.eventService.getEvent(TEST_EVENT_ID);
		Assert.assertEquals(new Long(1), event.getVersion());
		Profile profile = profileService.load(TEST_PROFILE_ID);
		Event pageViewEvent = new Event(TEST_EVENT_ID, EVENT_TYPE, session, profile, TEST_SCOPE, null, null, new Date());
		ContextRequest contextRequest = new ContextRequest();
		contextRequest.setSessionId(session.getItemId());
		contextRequest.setEvents(Collections.singletonList(pageViewEvent));
		HttpPost request = new HttpPost(URL + "/context.json");
		request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest),
			ContentType.create("application/json")));

		//Making sure Unomi is up and running
		Thread.sleep(5000);
		this.testUtils.executeContextJSONRequest(request, TEST_SESSION_ID);

		//Making sure event is updated in DB
		Thread.sleep(2000);
		event = this.eventService.getEvent(TEST_EVENT_ID);
		Assert.assertEquals(new Long(1), event.getVersion());
	}


	@Test
	public void testUpdateEventFromContextAuthorizedThirdPartyNoItemID_Fail() throws IOException, InterruptedException {
		Event event = this.eventService.getEvent(TEST_EVENT_ID);
		Assert.assertEquals(new Long(1), event.getVersion());
		Profile profile = profileService.load(TEST_PROFILE_ID);
		Event pageViewEvent = new Event(EVENT_TYPE, session, profile, TEST_SCOPE, null, null, new Date());
		ContextRequest contextRequest = new ContextRequest();
		contextRequest.setSessionId(session.getItemId());
		contextRequest.setEvents(Collections.singletonList(pageViewEvent));
		HttpPost request = new HttpPost(URL + "/context.json");
		request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest),
			ContentType.create("application/json")));

		//Making sure Unomi is up and running
		Thread.sleep(5000);
		this.testUtils.executeContextJSONRequest(request, TEST_SESSION_ID);

		//Making sure event is updated in DB
		Thread.sleep(2000);
		event = this.eventService.getEvent(TEST_EVENT_ID);
		Assert.assertEquals(new Long(1), event.getVersion());
	}
}
