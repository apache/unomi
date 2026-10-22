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

import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.unomi.api.tenants.ApiKey;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.itests.tools.httpclient.HttpClientThatWaitsForUnomi;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * UNOMI-1013: each tenant lists its own website origins in its {@code publicCorsAllowedOrigins} property,
 * set through the tenant API when the tenant is provisioned, with no change to the operator configuration.
 * The origin only applies to requests made with that tenant's key.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class TenantTrackerOriginsIT extends BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantTrackerOriginsIT.class);

    private static final String CONTEXT_JSON = "/cxs/context.json";
    private static final String TENANT_ORIGINS_KEY = "publicCorsAllowedOrigins";

    private final List<String> createdTenants = new ArrayList<>();
    private String sessionId;

    @Before
    public void newSession() {
        // Session ids are limited to 60 characters by the request schema.
        sessionId = "tto-it-" + UUID.randomUUID();
    }

    @After
    public void tearDown() {
        for (String tenantId : createdTenants) {
            try {
                if (tenantService.getTenant(tenantId) != null) {
                    tenantService.deleteTenant(tenantId);
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Could not delete test tenant {}", tenantId, e);
            }
        }
        persistenceService.refresh();
        TestUtils.removeAllEvents(definitionsService, persistenceService, true, tenantService, executionContextManager);
        TestUtils.removeAllSessions(definitionsService, persistenceService, true, tenantService, executionContextManager);
        TestUtils.removeAllProfiles(definitionsService, persistenceService, true, tenantService, executionContextManager);
    }

    @Test
    public void aTenantsOwnOriginGetsCredentialsAndKeepsItsVisitor() throws Exception {
        String origin = "https://tenant-a-" + shortId() + ".example";
        String key = createTenantThroughApi(origin);

        try (CloseableHttpResponse preflight = execute(preflight(origin))) {
            assertEquals(origin, header(preflight, "Access-Control-Allow-Origin"));
            assertEquals("true", header(preflight, "Access-Control-Allow-Credentials"));
        }

        String[] visitor = newVisitor(key);
        HttpPost request = contextRequest(key);
        request.setHeader("Origin", origin);
        request.setHeader("Sec-Fetch-Site", "cross-site");
        request.setHeader("Cookie", visitor[1]);
        try (CloseableHttpResponse response = execute(request)) {
            assertEquals(origin, header(response, "Access-Control-Allow-Origin"));
            assertEquals("true", header(response, "Access-Control-Allow-Credentials"));
            assertEquals(visitor[0], profileId(response));
        }
    }

    @Test
    public void anotherTenantsOriginDoesNotUseTheVisitorCookie() throws Exception {
        String origin = "https://tenant-a-" + shortId() + ".example";
        createTenantThroughApi(origin);
        String otherKey = createTenantThroughApi(null);

        String[] visitor = newVisitor(otherKey);
        HttpPost request = contextRequest(otherKey);
        request.setHeader("Origin", origin);
        request.setHeader("Sec-Fetch-Site", "cross-site");
        request.setHeader("Cookie", visitor[1]);
        try (CloseableHttpResponse response = execute(request)) {
            assertEquals("*", header(response, "Access-Control-Allow-Origin"));
            assertNull(header(response, "Access-Control-Allow-Credentials"));
            assertNull("The visitor's cookie must not be replaced", header(response, "Set-Cookie"));
            assertNotEquals(visitor[0], profileId(response));
        }
    }

    @Test
    public void invalidOriginsAreRejectedWhenTheTenantIsCreated() throws Exception {
        String tenantId = "it-cors-" + shortId();
        HttpPost create = new HttpPost(getFullUrl("/cxs/tenants"));
        create.setEntity(new StringEntity("{\"requestedId\":\"" + tenantId + "\",\"properties\":{\"" + TENANT_ORIGINS_KEY
                + "\":[\"https://*.example\"]}}", ContentType.APPLICATION_JSON));
        try (CloseableHttpResponse response = executeHttpRequest(create, AuthType.JAAS_ADMIN)) {
            assertEquals(400, response.getStatusLine().getStatusCode());
        }
        assertNull(tenantService.getTenant(tenantId));
    }

    @Test
    public void aTenantCreatedElsewhereWorksWithoutRestart() throws Exception {
        // Saved directly, as another node would, so this node only learns about it from the request.
        String origin = "https://tenant-c-" + shortId() + ".example";
        String tenantId = "it-cors-" + shortId();
        Tenant tenant = tenantService.createTenant(tenantId, Collections.singletonMap(TENANT_ORIGINS_KEY, origin));
        createdTenants.add(tenant.getItemId());
        String key = tenantService.generateApiKeyWithType(tenantId, ApiKey.ApiKeyType.PUBLIC, null).getPlainTextKey();
        persistenceService.refresh();

        // Reloads for unknown origins are rate-limited, so allow a few seconds.
        keepTrying("A tenant created elsewhere should be picked up without a restart",
                () -> preflightAllowsCredentials(origin), allowed -> allowed, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        HttpPost request = contextRequest(key);
        request.setHeader("Origin", origin);
        request.setHeader("Sec-Fetch-Site", "cross-site");
        try (CloseableHttpResponse response = execute(request)) {
            assertEquals(origin, header(response, "Access-Control-Allow-Origin"));
            assertEquals("true", header(response, "Access-Control-Allow-Credentials"));
        }
    }

    @Test
    public void updatingATenantThroughTheApiKeepsItsKeysAndAppliesAtOnce() throws Exception {
        String origin = "https://tenant-d-" + shortId() + ".example";
        String key = createTenantThroughApi(null);
        String tenantId = lastTenantId;

        // Read the tenant as REST returns it (no key hashes), add the origin, send it back.
        putTenant(tenantId, tenant -> ((com.fasterxml.jackson.databind.node.ObjectNode) tenant.get("properties"))
                .putArray(TENANT_ORIGINS_KEY).add(origin));

        // No index refresh: the node that handled the PUT applies it at once, and the key still works.
        HttpPost request = contextRequest(key);
        request.setHeader("Origin", origin);
        request.setHeader("Sec-Fetch-Site", "cross-site");
        try (CloseableHttpResponse response = execute(request)) {
            assertEquals("The tenant's key must survive the update", 200, response.getStatusLine().getStatusCode());
            assertEquals(origin, header(response, "Access-Control-Allow-Origin"));
            assertEquals("true", header(response, "Access-Control-Allow-Credentials"));
        }

        putTenant(tenantId, tenant -> ((com.fasterxml.jackson.databind.node.ObjectNode) tenant.get("properties"))
                .remove(TENANT_ORIGINS_KEY));
        HttpPost after = contextRequest(key);
        after.setHeader("Origin", origin);
        after.setHeader("Sec-Fetch-Site", "cross-site");
        try (CloseableHttpResponse response = execute(after)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals("*", header(response, "Access-Control-Allow-Origin"));
            assertNull(header(response, "Access-Control-Allow-Credentials"));
        }
    }

    private void putTenant(String tenantId, java.util.function.Consumer<com.fasterxml.jackson.databind.JsonNode> change) throws Exception {
        com.fasterxml.jackson.databind.JsonNode tenant;
        try (CloseableHttpResponse response = executeHttpRequest(new org.apache.http.client.methods.HttpGet(
                getFullUrl("/cxs/tenants/" + tenantId)), AuthType.JAAS_ADMIN)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
            tenant = getObjectMapper().readTree(EntityUtils.toString(response.getEntity()));
        }
        change.accept(tenant);
        org.apache.http.client.methods.HttpPut put = new org.apache.http.client.methods.HttpPut(getFullUrl("/cxs/tenants/" + tenantId));
        put.setEntity(new StringEntity(getObjectMapper().writeValueAsString(tenant), ContentType.APPLICATION_JSON));
        try (CloseableHttpResponse response = executeHttpRequest(put, AuthType.JAAS_ADMIN)) {
            assertEquals(EntityUtils.toString(response.getEntity()), 200, response.getStatusLine().getStatusCode());
        }
    }

    private String lastTenantId;

    /** Creates a tenant through {@code /cxs/tenants}, as provisioning would, and returns its public key. */
    private String createTenantThroughApi(String origin) throws Exception {
        String tenantId = "it-cors-" + shortId();
        lastTenantId = tenantId;
        String properties = origin == null ? "{}" : "{\"" + TENANT_ORIGINS_KEY + "\":[\"" + origin + "\"]}";
        HttpPost create = new HttpPost(getFullUrl("/cxs/tenants"));
        create.setEntity(new StringEntity("{\"requestedId\":\"" + tenantId + "\",\"properties\":" + properties + "}",
                ContentType.APPLICATION_JSON));
        // Recorded first, so the tenant is cleaned up even if the assertion below fails after the server created it.
        createdTenants.add(tenantId);
        try (CloseableHttpResponse response = executeHttpRequest(create, AuthType.JAAS_ADMIN)) {
            assertEquals(EntityUtils.toString(response.getEntity()), 200, response.getStatusLine().getStatusCode());
        }
        String key = tenantService.generateApiKeyWithType(tenantId, ApiKey.ApiKeyType.PUBLIC, null).getPlainTextKey();
        persistenceService.refresh();
        return key;
    }

    private boolean preflightAllowsCredentials(String origin) {
        try (CloseableHttpResponse response = execute(preflight(origin))) {
            return origin.equals(header(response, "Access-Control-Allow-Origin"))
                    && "true".equals(header(response, "Access-Control-Allow-Credentials"));
        } catch (Exception e) {
            return false;
        }
    }

    /** Returns the profile id and the cookie of a visitor of this tenant created without any Origin header. */
    private String[] newVisitor(String key) throws Exception {
        try (CloseableHttpResponse response = execute(contextRequest(key))) {
            Header setCookie = response.getFirstHeader("Set-Cookie");
            assertNotNull("A new visitor gets a profile cookie", setCookie);
            return new String[]{profileId(response), setCookie.getValue().split(";")[0]};
        }
    }

    private HttpPost contextRequest(String key) throws Exception {
        HttpPost request = new HttpPost(getFullUrl(CONTEXT_JSON));
        request.setHeader("X-Unomi-Api-Key", key);
        request.setEntity(new StringEntity("{\"sessionId\":\"" + sessionId + "\"}", ContentType.APPLICATION_JSON));
        return request;
    }

    private HttpOptions preflight(String origin) throws Exception {
        HttpOptions request = new HttpOptions(getFullUrl(CONTEXT_JSON));
        request.setHeader("Origin", origin);
        request.setHeader("Access-Control-Request-Method", "POST");
        request.setHeader("Access-Control-Request-Headers", "content-type,x-unomi-api-key");
        return request;
    }

    private static CloseableHttpResponse execute(HttpUriRequest request) throws IOException {
        // The key, when any, is already on the request; no default key is added.
        return HttpClientThatWaitsForUnomi.doRequest(request, -1, false, false);
    }

    private String profileId(CloseableHttpResponse response) throws IOException {
        assertEquals(200, response.getStatusLine().getStatusCode());
        return getObjectMapper().readTree(EntityUtils.toString(response.getEntity())).get("profileId").asText();
    }

    private static String header(CloseableHttpResponse response, String name) {
        Header header = response.getFirstHeader(name);
        return header == null ? null : header.getValue();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
