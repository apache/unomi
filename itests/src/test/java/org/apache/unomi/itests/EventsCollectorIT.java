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

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.EventsCollectorRequest;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.tenants.ApiKey;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
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
    private final static String TEST_EVENT_TYPE = "test-event";
    private final static String TEST_PROFILE_ID = "test-profile-id";
    private final static String TEST_SESSION_ID = "test-session-id";

    @Inject
    private TenantService tenantService;

    private Profile profile;

    @Before
    public void setUp() throws InterruptedException {
        profile = new Profile(TEST_PROFILE_ID);
        profileService.save(profile);

        keepTrying("Profile " + TEST_PROFILE_ID + " not found in the required time", () -> profileService.load(TEST_PROFILE_ID),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @Test
    public void testEventsCollectorWithPublicApiKey() throws Exception {
        // Create tenant with API keys
        Tenant tenant = tenantService.createTenant("EventsApiKeyTest", Collections.emptyMap());
        ApiKey publicKey = tenantService.getApiKey(tenant.getItemId(), ApiKey.ApiKeyType.PUBLIC);
        
        // Create event
        Event event = new Event(TEST_EVENT_TYPE, null, profile, null, null, null, new Date());
        
        // Create events collector request with public API key
        EventsCollectorRequest eventsRequest = new EventsCollectorRequest();
        eventsRequest.setSessionId(TEST_SESSION_ID);
        eventsRequest.setEvents(Collections.singletonList(event));
        eventsRequest.setPublicApiKey(publicKey.getKey());
        
        // Send request
        HttpPost request = new HttpPost(getFullUrl(EVENTS_URL));
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(eventsRequest), ContentType.APPLICATION_JSON));
        TestUtils.RequestResponse response = TestUtils.executeContextJSONRequest(request, TEST_SESSION_ID);
        
        // Verify response
        Assert.assertEquals("Should receive success response", 200, response.getStatusCode());
        
        // Test with invalid API key
        eventsRequest.setPublicApiKey("invalid-key");
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(eventsRequest), ContentType.APPLICATION_JSON));
        response = TestUtils.executeContextJSONRequest(request, TEST_SESSION_ID);
        
        // Verify error response for invalid key
        Assert.assertEquals("Should receive unauthorized response", 401, response.getStatusCode());
    }
} 