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
 * limitations under the License.
 */
package org.apache.unomi.itests;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.EventsCollectorRequest;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.tenants.ApiKey;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.itests.tools.httpclient.HttpClientThatWaitsForUnomi;
import org.apache.unomi.rest.models.EventCollectorResponse;
import org.apache.unomi.tracing.api.TraceNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Date;
import java.util.Objects;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class EventsCollectorIT extends BaseIT {
    private final static String EVENTS_URL = "/cxs/eventcollector";
    private final static String TEST_EVENT_TYPE = "testEventType";
    private final static String TEST_PROFILE_ID = "test-profile-id";
    private final static String TEST_SESSION_ID = "test-session-id";
    private final static String TEST_SCOPE = "testScope";
    private final static String TEST_TENANT_ID = "test-tenant";
    private final static String TEST_TENANT_NAME = "Test Tenant";
    private final static String TEST_TENANT_DESCRIPTION = "Test tenant for events collector";
    private final static String CONTENT_TYPE_HEADER = "Content-Type";
    private final static String APPLICATION_JSON = "application/json";

    private Profile profile;

    @Before
    public void setUp() throws InterruptedException {
        profile = new Profile(TEST_PROFILE_ID);
        profileService.save(profile);

        keepTrying("Profile " + TEST_PROFILE_ID + " not found in the required time", () -> profileService.load(TEST_PROFILE_ID),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // create schemas required for tests
        schemaService.saveSchema(resourceAsString("schemas/events/test-event-type.json"));
        keepTrying("Couldn't find json schemas",
                () -> schemaService.getInstalledJsonSchemaIds(),
                (schemaIds) -> schemaIds.contains("https://unomi.apache.org/schemas/json/events/testEventType/1-0-0"),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        TestUtils.createScope(TEST_SCOPE, "Test scope", scopeService);
        keepTrying("Scope test-scope not found in the required time", () -> scopeService.getScope(TEST_SCOPE),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @After
    public void tearDown() throws InterruptedException {
        persistenceService.refresh();
        TestUtils.removeAllEvents(definitionsService, persistenceService);
        TestUtils.removeAllSessions(definitionsService, persistenceService);
        TestUtils.removeAllProfiles(definitionsService, persistenceService);
        profileService.delete(profile.getItemId(), false);

        // cleanup schemas
        schemaService.deleteSchema("https://unomi.apache.org/schemas/json/events/testEventType/1-0-0");
        keepTrying("Should not find json schemas anymore",
                () -> schemaService.getInstalledJsonSchemaIds(),
                (schemaIds) -> (!schemaIds.contains("https://unomi.apache.org/schemas/json/events/testEventType/1-0-0")),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        scopeService.delete(TEST_SCOPE);
    }

    @Test
    public void testEventsCollectorWithPublicApiKey() throws Exception {

        // Create event and request
        Event event = new Event();
        event.setEventType(TEST_EVENT_TYPE);
        event.setScope(TEST_SCOPE);
        event.setSessionId(TEST_SESSION_ID);
        event.setProfileId(TEST_PROFILE_ID);

        EventsCollectorRequest eventsCollectorRequest = new EventsCollectorRequest();
        eventsCollectorRequest.setSessionId(TEST_SESSION_ID);
        eventsCollectorRequest.setEvents(Collections.singletonList(event));

        // Send request with public API key
        HttpPost request = new HttpPost(getFullUrl(EVENTS_URL));
        request.addHeader("Content-Type", "application/json");
        String requestBody = objectMapper.writeValueAsString(eventsCollectorRequest);
        request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

        // Execute request and verify response
        CloseableHttpResponse response = HttpClientThatWaitsForUnomi.doRequest(request, 200);
        String responseContent = EntityUtils.toString(response.getEntity());
        EventCollectorResponse eventResponse = objectMapper.readValue(responseContent, EventCollectorResponse.class);
        Assert.assertNotNull("Event collector response should not be null", eventResponse);

        // Check that the response indicates the session and profile were updated
        int expectedFlags = EventService.PROFILE_UPDATED | EventService.SESSION_UPDATED;
        Assert.assertEquals("Response should indicate that the session and profile were updated",
            expectedFlags, eventResponse.getUpdated());

        // Test with invalid API key
        request.removeHeaders("X-Unomi-Api-Key"); // We need to do this since we are reusing the request object since the last call added auth to it.
        HttpClientThatWaitsForUnomi.setTestTenant(null, null, null);
        response = HttpClientThatWaitsForUnomi.doRequest(request, 401);
        Assert.assertEquals("Request with invalid API key should return 401", 401, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testEventsCollectorWithExplain() throws Exception {

        // Create event and request
        Event event = new Event();
        event.setEventType(TEST_EVENT_TYPE);
        event.setScope(TEST_SCOPE);
        event.setSessionId(TEST_SESSION_ID);
        event.setProfileId(TEST_PROFILE_ID);

        EventsCollectorRequest eventsCollectorRequest = new EventsCollectorRequest();
        eventsCollectorRequest.setSessionId(TEST_SESSION_ID);
        eventsCollectorRequest.setEvents(Collections.singletonList(event));

        // Send request with explain parameter
        HttpPost request = new HttpPost(getFullUrl(EVENTS_URL + "?explain=true"));
        request.addHeader(CONTENT_TYPE_HEADER, APPLICATION_JSON);
        String requestBody = objectMapper.writeValueAsString(eventsCollectorRequest);
        request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

        // Execute request and verify response with private API key so that the explain is accepted
        CloseableHttpResponse response = HttpClientThatWaitsForUnomi.doRequest(request, 200, true, true);
        String responseContent = EntityUtils.toString(response.getEntity());
        EventCollectorResponse eventResponse = objectMapper.readValue(responseContent, EventCollectorResponse.class);
        Assert.assertNotNull("Event collector response should not be null", eventResponse);

        // Check that the response indicates both session and profile were updated
        int expectedFlags = EventService.SESSION_UPDATED | EventService.PROFILE_UPDATED;
        Assert.assertEquals("Response should indicate both session and profile were updated",
            expectedFlags, eventResponse.getUpdated());

        Assert.assertNotNull("Tracing information should be present", eventResponse.getRequestTracing());
    }

    @Test
    public void testEventsCollectorWithExplainUnauthorized() throws Exception {
        // Create event and request
        Event event = new Event();
        event.setEventType(TEST_EVENT_TYPE);
        event.setScope(TEST_SCOPE);
        event.setSessionId(TEST_SESSION_ID);
        event.setProfileId(TEST_PROFILE_ID);

        EventsCollectorRequest eventsCollectorRequest = new EventsCollectorRequest();
        eventsCollectorRequest.setSessionId(TEST_SESSION_ID);
        eventsCollectorRequest.setEvents(Collections.singletonList(event));

        // Send request with explain parameter but without admin privileges
        HttpPost request = new HttpPost(getFullUrl(EVENTS_URL + "?explain=true"));
        request.addHeader(CONTENT_TYPE_HEADER, APPLICATION_JSON);
        String requestBody = objectMapper.writeValueAsString(eventsCollectorRequest);
        request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

        // Execute request and verify response
        CloseableHttpResponse response = HttpClientThatWaitsForUnomi.doRequest(request, 403);
        Assert.assertEquals("Request with explain parameter but without admin privileges should return 403", 403, response.getStatusLine().getStatusCode());
    }
}
