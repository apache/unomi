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

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.PersonaSession;
import org.apache.unomi.api.PersonaWithSessions;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Integration tests for persona functionality.
 * This test class covers persona-related features including persona sessions.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class PersonaIT extends BaseIT {
    private static final Logger LOGGER = LoggerFactory.getLogger(PersonaIT.class);
    
    private static final String BASE_PROFILES_PATH = "/cxs/profiles";
    private static final String PERSONA_WITH_SESSIONS_ENDPOINT = BASE_PROFILES_PATH + "/personasWithSessions";
    private static final String PERSONA_ENDPOINT = BASE_PROFILES_PATH + "/personas";
    private static final String PERSONA_BY_ID_ENDPOINT = PERSONA_ENDPOINT + "/{personaId}";
    private static final String PERSONA_SESSIONS_ENDPOINT = PERSONA_ENDPOINT + "/{personaId}/sessions";
    
    private static final String TEST_PERSONA_ID = "test-persona-with-sessions";
    private static final String TEST_SESSION_ID = "test-session-1";
    private static final String PAYLOAD_RESOURCE = "persona/persona-with-sessions-payload.json";

    @Before
    public void setUp() throws InterruptedException {
        // Wait for persona REST endpoint to be available
        // Using GET /personas/{personaId} with a dummy ID to check endpoint availability
        String checkEndpoint = PERSONA_BY_ID_ENDPOINT.replace("{personaId}", "endpoint-check");
        keepTrying("Couldn't find persona endpoint", () -> {
            try (CloseableHttpResponse response = executeHttpRequest(new HttpGet(getFullUrl(checkEndpoint)), AuthType.JAAS_ADMIN)) {
                // Endpoint exists if we get 200 (persona exists), 204 (no content - persona not found), or 404 (not found)
                int statusCode = response.getStatusLine().getStatusCode();
                return (statusCode == 200 || statusCode == 204 || statusCode == 404) ? response : null;
            } catch (Exception e) {
                return null;
            }
        }, Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @After
    public void tearDown() {
        // Clean up: delete the test persona
        try {
            profileService.delete(TEST_PERSONA_ID, true);
        } catch (Exception e) {
            LOGGER.warn("Failed to clean up test persona: {}", e.getMessage());
        }
    }

    @Test
    public void testSavePersonaWithSessionsAndRetrieveSessions() throws Exception {
        // Create persona with sessions via REST API
        HttpPost createRequest = new HttpPost(getFullUrl(PERSONA_WITH_SESSIONS_ENDPOINT));
        createRequest.setEntity(new StringEntity(resourceAsString(PAYLOAD_RESOURCE), JSON_CONTENT_TYPE));

        PersonaWithSessions createdPersona;
        try (CloseableHttpResponse createResponse = executeHttpRequest(createRequest, AuthType.JAAS_ADMIN)) {
            int statusCode = createResponse.getStatusLine().getStatusCode();
            Assert.assertEquals("Persona creation should return 200 OK", 200, statusCode);

            String responseBody = EntityUtils.toString(createResponse.getEntity());
            createdPersona = CustomObjectMapper.getObjectMapper().readValue(responseBody, PersonaWithSessions.class);
        }

        Assert.assertNotNull("Created persona should not be null", createdPersona);
        Assert.assertNotNull("Created persona should have persona object", createdPersona.getPersona());
        Assert.assertEquals("Persona ID should match", TEST_PERSONA_ID, createdPersona.getPersona().getItemId());
        Assert.assertNotNull("Created persona should have sessions", createdPersona.getSessions());
        Assert.assertFalse("Created persona should have at least one session", createdPersona.getSessions().isEmpty());

        // Wait for persona's sessions to be indexed before testing session retrieval
        // This ensures the sessions are properly linked and queryable
        String sessionsUrl = PERSONA_SESSIONS_ENDPOINT.replace("{personaId}", TEST_PERSONA_ID);
        PartialList<PersonaSession> sessions = keepTrying(
            "Persona sessions should be retrievable after creation",
            () -> {
                try {
                    try (CloseableHttpResponse response = executeHttpRequest(new HttpGet(getFullUrl(sessionsUrl)), AuthType.JAAS_ADMIN)) {
                        if (response.getStatusLine().getStatusCode() == 200) {
                            String responseBody = EntityUtils.toString(response.getEntity());
                            PartialList<PersonaSession> result = CustomObjectMapper.getObjectMapper().readValue(
                                responseBody, new TypeReference<PartialList<PersonaSession>>() {});
                            // Check if the test session is present
                            if (result != null && result.getList() != null && !result.getList().isEmpty()) {
                                boolean hasTestSession = result.getList().stream()
                                    .anyMatch(session -> TEST_SESSION_ID.equals(session.getItemId()));
                                return hasTestSession ? result : null;
                            }
                        }
                        return null;
                    }
                } catch (Exception e) {
                    LOGGER.debug("Error retrieving persona sessions: {}", e.getMessage());
                    return null;
                }
            },
            Objects::nonNull,
            DEFAULT_TRYING_TIMEOUT,
            DEFAULT_TRYING_TRIES * 2 // Give more time for indexing
        );

        Assert.assertNotNull("Persona sessions should be retrievable", sessions);
        Assert.assertNotNull("Sessions list should not be null", sessions.getList());
        Assert.assertFalse("Sessions list should not be empty", sessions.getList().isEmpty());

        // Verify the test session is present and properly linked
        PersonaSession testSession = sessions.getList().stream()
            .filter(session -> TEST_SESSION_ID.equals(session.getItemId()))
            .findFirst()
            .orElse(null);

        Assert.assertNotNull("Test session should be found in retrieved sessions", testSession);
        Assert.assertNotNull("Session should have a profile reference", testSession.getProfile());
        Assert.assertEquals("Session should be linked to the correct persona", TEST_PERSONA_ID, testSession.getProfile().getItemId());
    }
}

