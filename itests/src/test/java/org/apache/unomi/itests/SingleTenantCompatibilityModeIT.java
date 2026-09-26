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
import org.apache.unomi.rest.authentication.V2ThirdPartyConfigService;
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
 * Integration tests for single-tenant compatibility mode authentication.
 * Tests the behavior when switching between V2 and V3 authentication modes
 * using OSGi configuration admin without restarting bundles.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class SingleTenantCompatibilityModeIT extends BaseIT {

    private final static Logger LOGGER = LoggerFactory.getLogger(SingleTenantCompatibilityModeIT.class);
    private final static String CONTEXT_URL = "/cxs/context.json";
    private static final String TEST_SCOPE = "testScope";
    private String TEST_SESSION_ID;
    /** The tenant the single-tenant compatibility mode runs on, as AuthenticationFilter names it. */
    private static final String COMPATIBILITY_TENANT_ID = "default";

    private String TEST_PROFILE_ID;
    private final static String UNOMI_API_KEY_HEADER = "X-Unomi-Api-Key";
    private final static String UNOMI_TENANT_ID_HEADER = "X-Unomi-Tenant-Id";
    private final static String UNOMI_PEER_HEADER = "X-Unomi-Peer";

    private boolean originalV2Mode;
    private V2ThirdPartyConfigService v2ThirdPartyConfigService;

    @Before
    public void setUp() throws InterruptedException, IOException {
        TEST_SESSION_ID = "v2-compat-test-session-" + UUID.randomUUID();
        TEST_PROFILE_ID = "v2-compat-test-profile-" + UUID.randomUUID();
        v2ThirdPartyConfigService = getService(V2ThirdPartyConfigService.class);

        TestUtils.createScope(TEST_SCOPE, "Test scope", scopeService);

        // The compatibility mode runs on its own tenant, not the one BaseIT works in, so an event it
        // carries is validated against the scopes of that tenant. Create the scope there too, then
        // put the context back where BaseIT left it.
        executionContextManager.setCurrentContext(executionContextManager.createContext(COMPATIBILITY_TENANT_ID));
        try {
            TestUtils.createScope(TEST_SCOPE, "Test scope", scopeService);
        } finally {
            executionContextManager.setCurrentContext(executionContextManager.createContext(testTenant.getItemId()));
        }
        keepTrying("Scope "+ TEST_SCOPE +" not found in the required time", () -> scopeService.getScope(TEST_SCOPE),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // Store original V2 mode setting and default tenant ID
        originalV2Mode = restAuthenticationConfig.isSingleTenantCompatibilityModeEnabled();

        // Configure single-tenant compatibility mode to use the BaseIT test tenant as default
        Map<String, Object> v2Config = new HashMap<>();
        v2Config.put("singletenantcompatibility.enabled", false); // Start in V3 mode

        updateConfiguration(null,
                "org.apache.unomi.rest.authentication",
                v2Config);


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
            originalConfig.put("singletenantcompatibility.enabled", originalV2Mode);

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
        LOGGER.info("Starting single-tenant compatibility mode switch test");

        // STEP 1: Test V3 mode (default) - V2 requests should be rejected, V3 requests should work
        LOGGER.info("STEP 1: Testing V3 mode (default)");
        testV3ModeBehavior();

        // STEP 2: Switch to single-tenant compatibility mode
        LOGGER.info("STEP 2: Switching to single-tenant compatibility mode");
        updateConfiguration(null,
                "org.apache.unomi.rest.authentication",
                "singletenantcompatibility.enabled",
                true);

        // Wait for configuration to take effect
        keepTrying("single-tenant compatibility mode not enabled in the required time",
                () -> restAuthenticationConfig.isSingleTenantCompatibilityModeEnabled(),
                enabled -> enabled, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // STEP 3: Test V2 mode - V2 requests should work, V3 requests should be rejected
        LOGGER.info("STEP 3: Testing single-tenant compatibility mode");
        testV2ModeBehavior();

        // STEP 4: Switch back to V3 mode
        LOGGER.info("STEP 4: Switching back to V3 mode");
        updateConfiguration(null,
                "org.apache.unomi.rest.authentication",
                "singletenantcompatibility.enabled",
                false);

        // Wait for configuration to take effect
        keepTrying("single-tenant compatibility mode not disabled in the required time",
                () -> restAuthenticationConfig.isSingleTenantCompatibilityModeEnabled(),
                enabled -> !enabled, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // STEP 5: Test V3 mode again - V2 requests should be rejected, V3 requests should work
        LOGGER.info("STEP 5: Testing V3 mode again");
        testV3ModeBehavior();

        LOGGER.info("single-tenant compatibility mode switch test completed successfully");
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
        request.setEntity(new StringEntity(getObjectMapper().writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.RequestResponse response = executeContextJSONRequest(request, TEST_SESSION_ID, 401, false);
        assertEquals("V2-style request should be rejected in V3 mode", 401, response.getStatusCode());

        // Test V3-style request with public API key - should work
        request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.addHeader(UNOMI_API_KEY_HEADER, testPublicKeyValue);
        request.setEntity(new StringEntity(getObjectMapper().writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        response = executeContextJSONRequest(request, TEST_SESSION_ID);
        assertEquals("V3-style request with public API key should work in V3 mode", 200, response.getStatusCode());

        // Test V3-style request with private API key - should work
        request = new HttpPost(getFullUrl(CONTEXT_URL));
        addPrivateTenantAuth(request, testTenant, testPrivateKeyValue);
        request.setEntity(new StringEntity(getObjectMapper().writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        response = executeContextJSONRequest(request, TEST_SESSION_ID);
        assertEquals("V3-style request with private API key should work in V3 mode", 200, response.getStatusCode());

        // Test V3-style request with JAAS authentication - should work
        request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.addHeader(UNOMI_TENANT_ID_HEADER, testTenant.getItemId());
        request.setEntity(new StringEntity(getObjectMapper().writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));

        BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(BASIC_AUTH_USER_NAME, BASIC_AUTH_PASSWORD));

        RequestConfig requestConfig = RequestConfig.custom()
                .setAuthenticationEnabled(true)
                .setTargetPreferredAuthSchemes(Arrays.asList(AuthSchemes.BASIC))
                .build();

        try (CloseableHttpClient adminClient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .setDefaultRequestConfig(requestConfig)
                .build();
             CloseableHttpResponse jaasResponse = adminClient.execute(request)) {
            assertEquals("V3-style request with JAAS auth should work in V3 mode", 200, jaasResponse.getStatusLine().getStatusCode());
        }
    }

    /**
     * Test behavior in single-tenant compatibility mode:
     * - V2 requests (no auth for public endpoints) should work
     * - V3 requests should be rejected
     */
    private void testV2ModeBehavior() throws Exception {
        // Test V2-style request (no authentication for public endpoint) - should work
        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setSessionId(TEST_SESSION_ID);

        HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.setEntity(new StringEntity(getObjectMapper().writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.RequestResponse response = executeContextJSONRequest(request, TEST_SESSION_ID);
        assertEquals("V2-style request should work in single-tenant compatibility mode", 200, response.getStatusCode());
        // The profile this request created belongs to the tenant the compatibility mode runs on, which
        // is not the tenant BaseIT works in. Read that profile back below, so the write and the read
        // both go through the compatibility path.
        String compatibilityProfileId = response.getContextResponse().getProfileId();
        assertNotNull("V2-style request should have created a profile", compatibilityProfileId);

        // Test V2-style request with X-Unomi-Peer header (V2 third-party auth) - should work
        request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.addHeader(UNOMI_PEER_HEADER, "670c26d1cc413346c3b2fd9ce65dab41");
        request.setEntity(new StringEntity(getObjectMapper().writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        response = executeContextJSONRequest(request, TEST_SESSION_ID);
        assertEquals("V2-style request with X-Unomi-Peer should work in single-tenant compatibility mode", 200, response.getStatusCode());

        // Test V3-style request with public API key - in V2 mode, V3 API keys are ignored (request succeeds but no events processed)
        request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.addHeader(UNOMI_API_KEY_HEADER, testPublicKeyValue);
        request.setEntity(new StringEntity(getObjectMapper().writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        response = executeContextJSONRequest(request, TEST_SESSION_ID);
        assertEquals("V3-style request with public API key should return 200 in single-tenant compatibility mode", 200, response.getStatusCode());
        assertEquals("V3-style request with public API key should have 0 processed events in V2 mode", 0, response.getContextResponse().getProcessedEvents());

        // Test V3-style request with private API key - in V2 mode, V3 API keys are ignored (request succeeds but no events processed)
        request = new HttpPost(getFullUrl(CONTEXT_URL));
        addPrivateTenantAuth(request, testTenant, testPrivateKeyValue);
        request.setEntity(new StringEntity(getObjectMapper().writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        response = executeContextJSONRequest(request, TEST_SESSION_ID);
        assertEquals("V3-style request with private API key should return 200 in single-tenant compatibility mode", 200, response.getStatusCode());
        assertEquals("V3-style request with private API key should have 0 processed events in V2 mode", 0, response.getContextResponse().getProcessedEvents());

        // Test private endpoint with JAAS authentication - should work (like V2)
        HttpGet getRequest = new HttpGet(getFullUrl("/cxs/profiles/" + compatibilityProfileId));

        BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(BASIC_AUTH_USER_NAME, BASIC_AUTH_PASSWORD));

        RequestConfig requestConfig = RequestConfig.custom()
                .setAuthenticationEnabled(true)
                .setTargetPreferredAuthSchemes(Arrays.asList(AuthSchemes.BASIC))
                .build();

        try (CloseableHttpClient adminClient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .setDefaultRequestConfig(requestConfig)
                .build()) {
            try (CloseableHttpResponse jaasResponse = adminClient.execute(getRequest)) {
                assertEquals("Private endpoint with JAAS auth should work in single-tenant compatibility mode", 200, jaasResponse.getStatusLine().getStatusCode());
            }
            try (CloseableHttpResponse privacyResponse = adminClient.execute(new HttpGet(getFullUrl("/cxs/privacy/info")))) {
                assertEquals("GET /cxs/privacy/info with Karaf auth should work in single-tenant compatibility mode", 200, privacyResponse.getStatusLine().getStatusCode());
            }
        }
    }

    @Test
    public void testV2CompatibilityModeWithProtectedEvents() throws Exception {
        LOGGER.info("Testing single-tenant compatibility mode with protected events");

        // Switch to single-tenant compatibility mode
        updateConfiguration(null,
                "org.apache.unomi.rest.authentication",
                "singletenantcompatibility.enabled",
                true);

        keepTrying("single-tenant compatibility mode not enabled in the required time",
                () -> restAuthenticationConfig.isSingleTenantCompatibilityModeEnabled(),
                enabled -> enabled, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // Test protected event (login) without V2 third-party authentication - should be rejected
        Event loginEvent = new Event();
        loginEvent.setEventType("login");
        loginEvent.setScope(TEST_SCOPE);

        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setSessionId(TEST_SESSION_ID);
        contextRequest.setEvents(Arrays.asList(loginEvent));

        HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.setEntity(new StringEntity(getObjectMapper().writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        TestUtils.RequestResponse response = executeContextJSONRequest(request, TEST_SESSION_ID, 200, false);
        assertEquals("Protected event without V2 auth should return 200", 200, response.getStatusCode());
        assertEquals("Protected event without V2 auth should have 0 processed events", 0, response.getContextResponse().getProcessedEvents());

        // Test protected event with V2 third-party authentication - should work
        request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.addHeader(UNOMI_PEER_HEADER, "670c26d1cc413346c3b2fd9ce65dab41");
        request.setEntity(new StringEntity(getObjectMapper().writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        response = executeContextJSONRequest(request, TEST_SESSION_ID);
        assertEquals("Protected event with V2 auth should work", 200, response.getStatusCode());
        assertEquals("Protected event with V2 auth should have 1 processed event", 1, response.getContextResponse().getProcessedEvents());

        // Test protected event with empty X-Unomi-Peer header - should be rejected
        request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.addHeader(UNOMI_PEER_HEADER, "");
        request.setEntity(new StringEntity(getObjectMapper().writeValueAsString(contextRequest), ContentType.APPLICATION_JSON));
        response = executeContextJSONRequest(request, TEST_SESSION_ID);
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
        response = executeContextJSONRequest(request, TEST_SESSION_ID);
        assertEquals("Non-protected event without auth should work in V2 mode", 200, response.getStatusCode());
        assertEquals("Non-protected event without auth should have 1 processed event", 1, response.getContextResponse().getProcessedEvents());
    }

    @Test
    public void testV2CompatibilityProtectedEventNegativeCases() throws Exception {
        LOGGER.info("Testing single-tenant compatibility mode - protected event negative cases");

        updateConfiguration(null, "org.apache.unomi.rest.authentication", "singletenantcompatibility.enabled", true);
        keepTrying("single-tenant compatibility mode not enabled in the required time",
                () -> restAuthenticationConfig.isSingleTenantCompatibilityModeEnabled(),
                enabled -> enabled, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        Event loginEvent = new Event();
        loginEvent.setEventType("login");
        loginEvent.setScope(TEST_SCOPE);
        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setSessionId(TEST_SESSION_ID);
        contextRequest.setEvents(Arrays.asList(loginEvent));
        String requestBody = getObjectMapper().writeValueAsString(contextRequest);

        // Case 1: protected event with an unknown provider key → rejected (0 processed events)
        HttpPost request = new HttpPost(getFullUrl(CONTEXT_URL));
        request.addHeader(UNOMI_PEER_HEADER, "unknownkey000000000000000000000000");
        request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
        TestUtils.RequestResponse response = executeContextJSONRequest(request, TEST_SESSION_ID);
        assertEquals("Protected event with unknown provider key should return 200", 200, response.getStatusCode());
        assertEquals("Protected event with unknown provider key should have 0 processed events", 0, response.getContextResponse().getProcessedEvents());

        try {
            // Case 2: valid key but source IP not in provider's allowed list.
            // Configure a provider whose IP allowlist only contains a non-loopback address so
            // the test client (connecting from loopback) is rejected.
            String testProviderKey = "testproviderip0000000000000000000";
            Map<String, Object> wrongIpConfig = new HashMap<>();
            wrongIpConfig.put("thirdparty.testprovider.key", testProviderKey);
            wrongIpConfig.put("thirdparty.testprovider.ipAddresses", "10.0.0.1");
            wrongIpConfig.put("thirdparty.testprovider.allowedEvents", "login,updateProperties");
            updateConfiguration(null, "org.apache.unomi.thirdparty", wrongIpConfig);
            keepTrying("Third-party wrong-IP config not applied",
                    () -> v2ThirdPartyConfigService.getProviderKey("testprovider"),
                    testProviderKey::equals, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

            request = new HttpPost(getFullUrl(CONTEXT_URL));
            request.addHeader(UNOMI_PEER_HEADER, testProviderKey);
            request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
            response = executeContextJSONRequest(request, TEST_SESSION_ID);
            assertEquals("Protected event with valid key but wrong source IP should return 200", 200, response.getStatusCode());
            assertEquals("Protected event with valid key but wrong source IP should have 0 processed events", 0, response.getContextResponse().getProcessedEvents());

            // Case 3: valid key but event type (login) not in provider's allowedEvents.
            // Configure a provider that only allows updateProperties, not login.
            String limitedKey = "testproviderlimited000000000000000";
            Map<String, Object> limitedEventsConfig = new HashMap<>();
            limitedEventsConfig.put("thirdparty.limitedprovider.key", limitedKey);
            limitedEventsConfig.put("thirdparty.limitedprovider.ipAddresses", "127.0.0.1,::1");
            limitedEventsConfig.put("thirdparty.limitedprovider.allowedEvents", "updateProperties");
            updateConfiguration(null, "org.apache.unomi.thirdparty", limitedEventsConfig);
            keepTrying("Third-party limited-events config not applied",
                    () -> v2ThirdPartyConfigService.isValidProvider("limitedprovider"),
                    valid -> valid, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

            request = new HttpPost(getFullUrl(CONTEXT_URL));
            request.addHeader(UNOMI_PEER_HEADER, limitedKey);
            request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
            response = executeContextJSONRequest(request, TEST_SESSION_ID);
            assertEquals("Protected login event with key that only allows updateProperties should return 200", 200, response.getStatusCode());
            assertEquals("Protected login event with key that only allows updateProperties should have 0 processed events", 0, response.getContextResponse().getProcessedEvents());
        } finally {
            // Restore thirdparty config to defaults by deleting the test entries
            configurationAdmin.getConfiguration("org.apache.unomi.thirdparty", null).delete();
        }
    }

    private static void addPrivateTenantAuth(HttpPost request, Tenant tenant, String privateKeyValue) {
        request.setHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(
            (tenant.getItemId() + ":" + privateKeyValue).getBytes()));
    }

    @Override
    public void updateServices() throws InterruptedException {
        super.updateServices();
        restAuthenticationConfig = getService(RestAuthenticationConfig.class);
        v2ThirdPartyConfigService = getService(V2ThirdPartyConfigService.class);
    }
}
