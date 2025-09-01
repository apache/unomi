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

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.unomi.api.*;
import org.apache.unomi.api.tenants.ApiKey;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.itests.TestUtils.RequestResponse;
import org.apache.unomi.rest.authentication.RestAuthenticationConfig;
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

import java.io.IOException;
import java.util.*;
import java.util.Base64;
import java.util.Objects;

import static org.junit.Assert.*;

/**
 * Integration tests for V2 compatibility mode authentication.
 * Tests the behavior when switching between V2 and V3 authentication modes
 * using OSGi configuration admin without restarting bundles.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class V2CompatibilityModeIT extends BaseIT {

    private final static Logger LOGGER = LoggerFactory.getLogger(V2CompatibilityModeIT.class);
    private final static String CONTEXT_URL = "/cxs/context.json";
    private static final String TEST_SCOPE = "testScope";
    private final static String TEST_SESSION_ID = "v2-compat-test-session-" + System.currentTimeMillis();
    private final static String TEST_PROFILE_ID = "v2-compat-test-profile-" + System.currentTimeMillis();
    private final static String UNOMI_API_KEY_HEADER = "X-Unomi-Api-Key";
    private final static String UNOMI_TENANT_ID_HEADER = "X-Unomi-Tenant-Id";
    private final static String UNOMI_PEER_HEADER = "X-Unomi-Peer";

    private boolean originalV2Mode;
    private String originalDefaultTenantId;

    @Before
    public void setUp() throws InterruptedException, IOException {

        TestUtils.createScope(TEST_SCOPE, "Test scope", scopeService);
        keepTrying("Scope "+ TEST_SCOPE +" not found in the required time", () -> scopeService.getScope(TEST_SCOPE),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // Store original V2 mode setting and default tenant ID
        originalV2Mode = restAuthenticationConfig.isV2CompatibilityModeEnabled();
        originalDefaultTenantId = restAuthenticationConfig.getV2CompatibilityDefaultTenantId();

        // Configure V2 compatibility mode to use the BaseIT test tenant as default
        Map<String, Object> v2Config = new HashMap<>();
        v2Config.put("v2CompatibilityModeEnabled", false); // Start in V3 mode
        v2Config.put("v2CompatibilityDefaultTenantId", TEST_TENANT_ID); // Use BaseIT tenant

        updateConfiguration(null,
                "org.apache.unomi.rest.authentication",
                v2Config);

        // Wait for configuration to be applied
        keepTrying("V2 compatibility configuration not applied in the required time",
                () -> restAuthenticationConfig.getV2CompatibilityDefaultTenantId(),
                tenantId -> TEST_TENANT_ID.equals(tenantId), DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // Create test profile
        Profile profile = new Profile(TEST_PROFILE_ID);
        profileService.save(profile);

        keepTrying("Profile " + TEST_PROFILE_ID + " not found in the required time",
                () -> profileService.load(TEST_PROFILE_ID),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

    }

    @After
    public void tearDown() throws InterruptedException, IOException {
        try {
            // Restore original V2 mode setting and default tenant ID
            Map<String, Object> originalConfig = new HashMap<>();
            originalConfig.put("v2CompatibilityModeEnabled", originalV2Mode);
            if (originalDefaultTenantId != null) {
                originalConfig.put("v2CompatibilityDefaultTenantId", originalDefaultTenantId);
            }

            updateConfiguration(null,
                    "org.apache.unomi.rest.authentication",
                    originalConfig);
        } catch (Exception e) {
            LOGGER.warn("Failed to restore original V2 mode setting", e);
        }

        // Clean up test data
        try {
            TestUtils.removeAllEvents(definitionsService, persistenceService, true, tenantService, executionContextManager);
            TestUtils.removeAllSessions(definitionsService, persistenceService, true, tenantService, executionContextManager);
            TestUtils.removeAllProfiles(definitionsService, persistenceService, true, tenantService, executionContextManager);

            profileService.delete(TEST_PROFILE_ID, false);
            removeItems(Session.class);

            scopeService.delete(TEST_SCOPE);
        } catch (Exception e) {
            LOGGER.warn("Failed to clean up test data", e);
        }


    }

    @Test
    public void testV2CompatibilityModeSwitch() throws Exception {
        LOGGER.info("Starting V2 compatibility mode switch test");

        // STEP 1: Test V3 mode (default) - V2 requests should be rejected, V3 requests should work
        LOGGER.info("STEP 1: Testing V3 mode (default)");
        testV3ModeBehavior();

        // STEP 2: Switch to V2 compatibility mode
        LOGGER.info("STEP 2: Switching to V2 compatibility mode");
        updateConfiguration(null,
                "org.apache.unomi.rest.authentication",
                "v2CompatibilityModeEnabled",
                true);

        // Wait for configuration to take effect
        keepTrying("V2 compatibility mode not enabled in the required time",
                () -> restAuthenticationConfig.isV2CompatibilityModeEnabled(),
                enabled -> enabled, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // STEP 3: Test V2 mode - V2 requests should work, V3 requests should be rejected
        LOGGER.info("STEP 3: Testing V2 compatibility mode");
        testV2ModeBehavior();

        // STEP 4: Switch back to V3 mode
        LOGGER.info("STEP 4: Switching back to V3 mode");
        updateConfiguration(null,
                "org.apache.unomi.rest.authentication",
                "v2CompatibilityModeEnabled",
                false);

        // Wait for configuration to take effect
        keepTrying("V2 compatibility mode not disabled in the required time",
                () -> restAuthenticationConfig.isV2CompatibilityModeEnabled(),
                enabled -> !enabled, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // STEP 5: Test V3 mode again - V2 requests should be rejected, V3 requests should work
        LOGGER.info("STEP 5: Testing V3 mode again");
        testV3ModeBehavior();

        LOGGER.info("V2 compatibility mode switch test completed successfully");
    }

    /**
     * Test behavior in V3 mode (default):
     * - V2 requests (no auth) should be rejected
     * - V3 requests with proper authentication should work
     */
    private void testV3ModeBehavior() throws Exception {
        // Test V2-style request (no authentication) - should be rejected
        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setSessionId(TEST_SESSION_ID);

        HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.RequestResponse response = TestUtils.executeContextJSONRequest(request, TEST_SESSION_ID, 401, false);
        assertEquals("V2-style request should be rejected in V3 mode", 401, response.getStatusCode());

        // Test V3-style request with public API key - should work
        request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.addHeader(UNOMI_API_KEY_HEADER, testPublicKey.getKey());
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        response = TestUtils.executeContextJSONRequest(request, TEST_SESSION_ID);
        assertEquals("V3-style request with public API key should work in V3 mode", 200, response.getStatusCode());

        // Test V3-style request with private API key - should work
        request = new HttpPost(getFullUrl(CONTEXT_URL));
        addPrivateTenantAuth(request, testTenant, testPrivateKey);
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        response = TestUtils.executeContextJSONRequest(request, TEST_SESSION_ID);
        assertEquals("V3-style request with private API key should work in V3 mode", 200, response.getStatusCode());

        // Test V3-style request with JAAS authentication - should work
        request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.addHeader(UNOMI_TENANT_ID_HEADER, testTenant.getItemId());
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));

        BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("karaf", "karaf"));

        RequestConfig requestConfig = RequestConfig.custom()
                .setAuthenticationEnabled(true)
                .setTargetPreferredAuthSchemes(Arrays.asList(AuthSchemes.BASIC))
                .build();

        CloseableHttpClient adminClient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .setDefaultRequestConfig(requestConfig)
                .build();

        CloseableHttpResponse jaasResponse = adminClient.execute(request);
        assertEquals("V3-style request with JAAS auth should work in V3 mode", 200, jaasResponse.getStatusLine().getStatusCode());
        adminClient.close();
    }

    /**
     * Test behavior in V2 compatibility mode:
     * - V2 requests (no auth for public endpoints) should work
     * - V3 requests should be rejected
     */
    private void testV2ModeBehavior() throws Exception {
        // Test V2-style request (no authentication for public endpoint) - should work
        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setSessionId(TEST_SESSION_ID);

        HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.RequestResponse response = TestUtils.executeContextJSONRequest(request, TEST_SESSION_ID);
        assertEquals("V2-style request should work in V2 compatibility mode", 200, response.getStatusCode());

        // Test V2-style request with X-Unomi-Peer header (V2 third-party auth) - should work
        request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.addHeader(UNOMI_PEER_HEADER, "670c26d1cc413346c3b2fd9ce65dab41");
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        response = TestUtils.executeContextJSONRequest(request, TEST_SESSION_ID);
        assertEquals("V2-style request with X-Unomi-Peer should work in V2 compatibility mode", 200, response.getStatusCode());

        // Test V3-style request with public API key - should be rejected in V2 mode
        request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.addHeader(UNOMI_API_KEY_HEADER, testPublicKey.getKey());
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        response = TestUtils.executeContextJSONRequest(request, TEST_SESSION_ID);
        assertEquals("V3-style request with public API key should return 200 in V2 compatibility mode", 200, response.getStatusCode());
        assertEquals("V3-style request with public API key should have 0 processed events in V2 mode", 0, response.getContextResponse().getProcessedEvents());

        // Test V3-style request with private API key - should be rejected in V2 mode
        request = new HttpPost(getFullUrl(CONTEXT_URL));
        addPrivateTenantAuth(request, testTenant, testPrivateKey);
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        response = TestUtils.executeContextJSONRequest(request, TEST_SESSION_ID);
        assertEquals("V3-style request with private API key should return 200 in V2 compatibility mode", 200, response.getStatusCode());
        assertEquals("V3-style request with private API key should have 0 processed events in V2 mode", 0, response.getContextResponse().getProcessedEvents());

        // Test private endpoint with JAAS authentication - should work (like V2)
        HttpGet getRequest = new HttpGet(getFullUrl("/cxs/profiles/" + TEST_PROFILE_ID));

        BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("karaf", "karaf"));

        RequestConfig requestConfig = RequestConfig.custom()
                .setAuthenticationEnabled(true)
                .setTargetPreferredAuthSchemes(Arrays.asList(AuthSchemes.BASIC))
                .build();

        CloseableHttpClient adminClient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .setDefaultRequestConfig(requestConfig)
                .build();

        CloseableHttpResponse jaasResponse = adminClient.execute(getRequest);
        assertEquals("Private endpoint with JAAS auth should work in V2 compatibility mode", 200, jaasResponse.getStatusLine().getStatusCode());
        adminClient.close();
    }

    @Test
    public void testV2CompatibilityModeWithProtectedEvents() throws Exception {
        LOGGER.info("Testing V2 compatibility mode with protected events");

        // Switch to V2 compatibility mode
        updateConfiguration(null,
                "org.apache.unomi.rest.authentication",
                "v2CompatibilityModeEnabled",
                true);

        keepTrying("V2 compatibility mode not enabled in the required time",
                () -> restAuthenticationConfig.isV2CompatibilityModeEnabled(),
                enabled -> enabled, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // Test protected event (login) without V2 third-party authentication - should be rejected
        Event loginEvent = new Event();
        loginEvent.setEventType("login");
        loginEvent.setScope(TEST_SCOPE);

        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setSessionId(TEST_SESSION_ID);
        contextRequest.setEvents(Arrays.asList(loginEvent));

        HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.RequestResponse response = TestUtils.executeContextJSONRequest(request, TEST_SESSION_ID, 200, false);
        assertEquals("Protected event without V2 auth should return 200", 200, response.getStatusCode());
        assertEquals("Protected event without V2 auth should have 0 processed events", 0, response.getContextResponse().getProcessedEvents());

        // Test protected event with V2 third-party authentication - should work
        request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.addHeader(UNOMI_PEER_HEADER, "670c26d1cc413346c3b2fd9ce65dab41");
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        response = TestUtils.executeContextJSONRequest(request, TEST_SESSION_ID);
        assertEquals("Protected event with V2 auth should work", 200, response.getStatusCode());
        assertEquals("Protected event with V2 auth should have 1 processed event", 1, response.getContextResponse().getProcessedEvents());

        // Test protected event with empty X-Unomi-Peer header - should be rejected
        request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.addHeader(UNOMI_PEER_HEADER, "");
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        response = TestUtils.executeContextJSONRequest(request, TEST_SESSION_ID);
        assertEquals("Protected event with empty X-Unomi-Peer should return 200", 200, response.getStatusCode());
        assertEquals("Protected event with empty X-Unomi-Peer should have 0 processed events", 0, response.getContextResponse().getProcessedEvents());

        // Test non-protected event (view) without authentication - should work
        // Load the view event from JSON file
        String contextRequestJson = resourceAsString("events/viewEvent.json");
        
        // Replace the session ID with the test session ID
        contextRequestJson = contextRequestJson.replace("test-session-id", TEST_SESSION_ID);
        contextRequestJson = contextRequestJson.replace("testScope", TEST_SCOPE);

        request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.setEntity(new StringEntity(contextRequestJson, ContentType.APPLICATION_JSON));
        response = TestUtils.executeContextJSONRequest(request, TEST_SESSION_ID);
        assertEquals("Non-protected event without auth should work in V2 mode", 200, response.getStatusCode());
        assertEquals("Non-protected event without auth should have 1 processed event", 1, response.getContextResponse().getProcessedEvents());
    }

    @Test
    public void testV2CompatibilityModeDefaultTenant() throws Exception {
        LOGGER.info("Testing V2 compatibility mode default tenant behavior");

        // Verify the configuration was applied correctly in setUp()
        assertEquals("Default tenant should be set to BaseIT tenant", TEST_TENANT_ID, restAuthenticationConfig.getV2CompatibilityDefaultTenantId());

        // Switch to V2 compatibility mode
        updateConfiguration(null,
                "org.apache.unomi.rest.authentication",
                "v2CompatibilityModeEnabled",
                true);

        keepTrying("V2 compatibility mode not enabled in the required time",
                () -> restAuthenticationConfig.isV2CompatibilityModeEnabled(),
                enabled -> enabled, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // Verify the configuration was applied
        assertTrue("V2 compatibility mode should be enabled", restAuthenticationConfig.isV2CompatibilityModeEnabled());
        assertEquals("Default tenant should be set to BaseIT tenant", TEST_TENANT_ID, restAuthenticationConfig.getV2CompatibilityDefaultTenantId());

        // Test that requests work with the BaseIT tenant as default
        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setSessionId(TEST_SESSION_ID);

        HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.RequestResponse response = TestUtils.executeContextJSONRequest(request, TEST_SESSION_ID);
        assertEquals("V2-style request should work with BaseIT tenant as default", 200, response.getStatusCode());
    }

    @Test
    public void testV2CompatibilityModeConfigurationPersistence() throws Exception {
        LOGGER.info("Testing V2 compatibility mode configuration persistence");

        // Test that configuration changes persist across service updates
        updateConfiguration(null,
                "org.apache.unomi.rest.authentication",
                "v2CompatibilityModeEnabled",
                true);

        keepTrying("V2 compatibility mode not enabled in the required time",
                () -> restAuthenticationConfig.isV2CompatibilityModeEnabled(),
                enabled -> enabled, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // Verify configuration is applied
        assertTrue("V2 compatibility mode should be enabled", restAuthenticationConfig.isV2CompatibilityModeEnabled());
        assertEquals("Default tenant should persist", TEST_TENANT_ID, restAuthenticationConfig.getV2CompatibilityDefaultTenantId());

        // Update services to simulate service restart
        updateServices();

        // Verify configuration persists
        assertTrue("V2 compatibility mode should persist after service update", restAuthenticationConfig.isV2CompatibilityModeEnabled());
        assertEquals("Default tenant should persist after service update", TEST_TENANT_ID, restAuthenticationConfig.getV2CompatibilityDefaultTenantId());

        // Test that behavior is still correct
        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setSessionId(TEST_SESSION_ID);

        HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.RequestResponse response = TestUtils.executeContextJSONRequest(request, TEST_SESSION_ID);
        assertEquals("V2-style request should still work after service update", 200, response.getStatusCode());
    }

    private static void addPrivateTenantAuth(HttpPost request, Tenant tenant, ApiKey privateKey) {
        request.setHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(
            (tenant.getItemId() + ":" + privateKey.getKey()).getBytes()));
    }

    @Override
    public void updateServices() throws InterruptedException {
        super.updateServices();
        restAuthenticationConfig = getService(RestAuthenticationConfig.class);
    }
}
