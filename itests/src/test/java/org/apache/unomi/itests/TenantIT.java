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
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.tenants.ApiKey;
import org.apache.unomi.api.tenants.ApiKeyCreationResult;
import org.apache.unomi.api.tenants.Tenant;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.apache.http.util.EntityUtils;

import java.time.YearMonth;
import java.util.*;
import java.util.Base64;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class TenantIT extends BaseIT {

    private static final String REST_ENDPOINT = "/cxs/tenants";

    @Before
    public void setUp() throws InterruptedException {
        // Wait for tenant REST endpoint to be available
        keepTrying("Couldn't find tenant endpoint", () -> {
            try (CloseableHttpResponse response = executeHttpRequest(new HttpGet(getFullUrl(REST_ENDPOINT)), AuthType.JAAS_ADMIN)) {
                return response.getStatusLine().getStatusCode() == 200 ? response : null;
            } catch (Exception e) {
                return null;
            }
        }, Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @Test
    public void testRestEndpoint() throws Exception {
        // Test create tenant
        Map<String, Object> properties = new HashMap<>();
        properties.put("testProperty", "testValue");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("requestedId", "rest-test-tenant");
        requestBody.put("properties", properties);

        HttpPost createRequest = new HttpPost(getFullUrl(REST_ENDPOINT));
        createRequest.setEntity(new StringEntity(getObjectMapper().writeValueAsString(requestBody), ContentType.APPLICATION_JSON));

        String createResponse;
        Tenant createdTenant;
        try (CloseableHttpResponse response = executeHttpRequest(createRequest, AuthType.JAAS_ADMIN)) {
            createResponse = EntityUtils.toString(response.getEntity());
            createdTenant = getObjectMapper().readValue(createResponse, Tenant.class);
        }

        Assert.assertNotNull("Created tenant should not be null", createdTenant);
        Assert.assertEquals("rest-test-tenant", createdTenant.getItemId());
        Assert.assertNotNull("Tenant should have public API key", createdTenant.getPublicApiKey());
        Assert.assertNotNull("Tenant should have private API key", createdTenant.getPrivateApiKey());

        boolean tenantDeleted = false;
        try {
            // Test get tenant
            String getResponse;
            Tenant retrievedTenant;
            try (CloseableHttpResponse response = executeHttpRequest(new HttpGet(getFullUrl(REST_ENDPOINT + "/" + createdTenant.getItemId())), AuthType.JAAS_ADMIN)) {
                getResponse = EntityUtils.toString(response.getEntity());
                retrievedTenant = getObjectMapper().readValue(getResponse, Tenant.class);
            }

            Assert.assertEquals("Retrieved tenant should match created tenant", createdTenant.getItemId(), retrievedTenant.getItemId());

            // Test update tenant
            retrievedTenant.setName("Updated Rest Test Tenant");
            retrievedTenant.setDescription("Updated REST test description");

            HttpPut updateRequest = new HttpPut(getFullUrl(REST_ENDPOINT + "/" + retrievedTenant.getItemId()));
            updateRequest.setEntity(new StringEntity(getObjectMapper().writeValueAsString(retrievedTenant), ContentType.APPLICATION_JSON));

            String updateResponse;
            Tenant updatedTenant;
            try (CloseableHttpResponse response = executeHttpRequest(updateRequest, AuthType.JAAS_ADMIN)) {
                updateResponse = EntityUtils.toString(response.getEntity());
                updatedTenant = getObjectMapper().readValue(updateResponse, Tenant.class);
            }

            Assert.assertEquals("Tenant name should be updated", "Updated Rest Test Tenant", updatedTenant.getName());
            Assert.assertEquals("Tenant description should be updated", "Updated REST test description", updatedTenant.getDescription());

            // Test generate new API key
            String generateKeyUrl = String.format("%s/%s/apikeys?type=%s&validityDays=30",
                getFullUrl(REST_ENDPOINT), updatedTenant.getItemId(), ApiKey.ApiKeyType.PUBLIC.name());
            HttpPost generateKeyRequest = new HttpPost(generateKeyUrl);

            String generateKeyResponse;
            ApiKeyCreationResult newApiKeyResult;
            try (CloseableHttpResponse response = executeHttpRequest(generateKeyRequest, AuthType.JAAS_ADMIN)) {
                generateKeyResponse = EntityUtils.toString(response.getEntity());
                newApiKeyResult = getObjectMapper().readValue(generateKeyResponse, ApiKeyCreationResult.class);
            }

            Assert.assertNotNull("New API key result should not be null", newApiKeyResult);
            Assert.assertNotNull("New API key should not be null", newApiKeyResult.getApiKey());
            Assert.assertEquals("API key type should match requested type", ApiKey.ApiKeyType.PUBLIC, newApiKeyResult.getApiKey().getKeyType());
            String newApiKeyValue = newApiKeyResult.getPlainTextKey();

            // Test validate API key
            String validateKeyUrl = String.format("%s/%s/apikeys/validate?key=%s&type=%s",
                getFullUrl(REST_ENDPOINT), updatedTenant.getItemId(), newApiKeyValue, ApiKey.ApiKeyType.PUBLIC.name());
            int validateResponse;
            try (CloseableHttpResponse response = executeHttpRequest(new HttpGet(validateKeyUrl), AuthType.JAAS_ADMIN)) {
                validateResponse = response.getStatusLine().getStatusCode();
            }
            Assert.assertEquals("API key validation should succeed", 200, validateResponse);

            // Test validate with wrong type
            String validateWrongTypeUrl = String.format("%s/%s/apikeys/validate?key=%s&type=%s",
                getFullUrl(REST_ENDPOINT), updatedTenant.getItemId(), newApiKeyValue, ApiKey.ApiKeyType.PRIVATE.name());
            int validateWrongTypeResponse;
            try (CloseableHttpResponse response = executeHttpRequest(new HttpGet(validateWrongTypeUrl), AuthType.JAAS_ADMIN)) {
                validateWrongTypeResponse = response.getStatusLine().getStatusCode();
            }
            Assert.assertEquals("API key validation with wrong type should fail", 401, validateWrongTypeResponse);

            // Test delete tenant
            int deleteResponse;
            try (CloseableHttpResponse response = executeHttpRequest(new HttpDelete(getFullUrl(REST_ENDPOINT + "/" + updatedTenant.getItemId())), AuthType.JAAS_ADMIN)) {
                deleteResponse = response.getStatusLine().getStatusCode();
            }
            Assert.assertEquals("Delete response should be 204", 204, deleteResponse);
            tenantDeleted = true;

            // Verify tenant is deleted
            int verifyDeleteResponse;
            try (CloseableHttpResponse response = executeHttpRequest(new HttpGet(getFullUrl(REST_ENDPOINT + "/" + updatedTenant.getItemId())), AuthType.JAAS_ADMIN)) {
                verifyDeleteResponse = response.getStatusLine().getStatusCode();
            }
            Assert.assertEquals("Get deleted tenant should return 404", 404, verifyDeleteResponse);
        } finally {
            if (!tenantDeleted) {
                try (CloseableHttpResponse r = executeHttpRequest(new HttpDelete(getFullUrl(REST_ENDPOINT + "/" + createdTenant.getItemId())), AuthType.JAAS_ADMIN)) {
                    // best-effort cleanup
                }
            }
        }
    }

    @Test
    public void testTenantEndpointAuthentication() throws Exception {
        // Test without any authentication
        try (CloseableHttpResponse response = executeHttpRequest(new HttpGet(getFullUrl(REST_ENDPOINT)), AuthType.NONE)) {
            Assert.assertEquals("Unauthenticated request should be rejected", 401, response.getStatusLine().getStatusCode());
        }

        // Create test tenant for API key tests
        BasicCredentialsProvider adminCredsProvider = new BasicCredentialsProvider();
        adminCredsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("karaf", "karaf"));

        try (CloseableHttpClient adminClient = HttpClients.custom().setDefaultCredentialsProvider(adminCredsProvider).build()) {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("requestedId", "auth-test-tenant");
            requestBody.put("properties", Collections.emptyMap());

            HttpPost createRequest = new HttpPost(getFullUrl(REST_ENDPOINT));
            createRequest.setEntity(new StringEntity(getObjectMapper().writeValueAsString(requestBody), ContentType.APPLICATION_JSON));

            String createResponse;
            Tenant tenant;
            try (CloseableHttpResponse response = adminClient.execute(createRequest)) {
                createResponse = EntityUtils.toString(response.getEntity());
                tenant = getObjectMapper().readValue(createResponse, Tenant.class);
            }

            try {
                // Test with public API key (should fail)
                try (CloseableHttpResponse response = executeHttpRequest(new HttpGet(getFullUrl(REST_ENDPOINT)), AuthType.PUBLIC_KEY)) {
                    Assert.assertEquals("Public API key should not grant access to tenant endpoints", 401, response.getStatusLine().getStatusCode());
                }

                // Test with private API key (should fail)
                try (CloseableHttpResponse response = executeHttpRequest(new HttpGet(getFullUrl(REST_ENDPOINT)), AuthType.PRIVATE_KEY)) {
                    Assert.assertEquals("Private API key should not grant access to tenant endpoints", 401, response.getStatusLine().getStatusCode());
                }

                // Test with invalid JAAS credentials (should fail)
                BasicCredentialsProvider wrongCredsProvider = new BasicCredentialsProvider();
                wrongCredsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("wrong", "wrong"));
                try (CloseableHttpClient wrongClient = HttpClients.custom().setDefaultCredentialsProvider(wrongCredsProvider).build();
                     CloseableHttpResponse response = wrongClient.execute(new HttpGet(getFullUrl(REST_ENDPOINT)))) {
                    Assert.assertEquals("Invalid JAAS credentials should be rejected", 401, response.getStatusLine().getStatusCode());
                }

                // Test with valid JAAS credentials (should succeed)
                try (CloseableHttpResponse response = adminClient.execute(new HttpGet(getFullUrl(REST_ENDPOINT)))) {
                    Assert.assertEquals("Valid JAAS credentials should be accepted", 200, response.getStatusLine().getStatusCode());
                }
            } finally {
                try { tenantService.deleteTenant(tenant.getItemId()); } catch (Exception ignored) {}
            }
        }
    }

    @Test
    public void testPublicEndpointAuthentication() throws Exception {
        // Create test tenant
        Tenant tenant = tenantService.createTenant("public-test-tenant", Collections.emptyMap());

        // Generate fresh keys to capture their one-time plaintext values (UNOMI-938: tenant.getPublicApiKey()/
        // getPrivateApiKey() only return masked keys, which cannot be used for authentication).
        String privateKeyValue = tenantService.generateApiKeyWithType(tenant.getItemId(), ApiKey.ApiKeyType.PRIVATE, null).getPlainTextKey();
        String publicKeyValue = tenantService.generateApiKeyWithType(tenant.getItemId(), ApiKey.ApiKeyType.PUBLIC, null).getPlainTextKey();

        // Refresh persistence to ensure tenant is immediately available for API key lookup
        persistenceService.refresh();

        try {
            // Test without any authentication
            String sessionId = "test-session-" + System.currentTimeMillis();
            try (CloseableHttpResponse response = executeHttpRequest(new HttpGet(getFullUrl("/context.json?sessionId=" + sessionId)), AuthType.NONE)) {
                Assert.assertEquals("Unauthenticated public request should be rejected", 401, response.getStatusLine().getStatusCode());
            }

            // Test with private API key (should succeed - private keys have higher privileges)
            HttpGet publicRequest = new HttpGet(getFullUrl("/context.json?sessionId=" + sessionId));
            publicRequest.setHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                (tenant.getItemId() + ":" + privateKeyValue).getBytes()));
            try (CloseableHttpResponse response = executeHttpRequest(publicRequest, AuthType.PRIVATE_KEY)) {
                Assert.assertEquals("Private API key should grant access to public endpoints (higher privileges)", 200, response.getStatusLine().getStatusCode());
            }

            // Test with valid public API key (should succeed)
            publicRequest = new HttpGet(getFullUrl("/context.json?sessionId=" + sessionId));
            publicRequest.setHeader("X-Unomi-Api-Key", publicKeyValue);
            try (CloseableHttpResponse response = executeHttpRequest(publicRequest, AuthType.PUBLIC_KEY)) {
                Assert.assertEquals("Valid public API key should grant access to public endpoints", 200, response.getStatusLine().getStatusCode());
            }

            // Test with JAAS auth (should succeed) — use a fresh request to avoid carrying X-Unomi-Api-Key from previous step
            BasicCredentialsProvider adminCredsProvider = new BasicCredentialsProvider();
            adminCredsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("karaf", "karaf"));
            try (CloseableHttpClient adminClient = HttpClients.custom().setDefaultCredentialsProvider(adminCredsProvider).build();
                 CloseableHttpResponse response = adminClient.execute(new HttpGet(getFullUrl("/context.json?sessionId=" + sessionId)))) {
                Assert.assertEquals("JAAS auth should grant access to public endpoints", 200, response.getStatusLine().getStatusCode());
            }
        } finally {
            tenantService.deleteTenant(tenant.getItemId());
        }
    }

    @Test
    public void testPrivateEndpointAuthentication() throws Exception {
        // Create test tenant
        Tenant tenant = tenantService.createTenant("private-test-tenant", Collections.emptyMap());

        // Generate fresh keys to capture their one-time plaintext values (UNOMI-938: tenant.getPublicApiKey()
        // only returns a masked key, which cannot be used for authentication).
        String publicKeyValue = tenantService.generateApiKeyWithType(tenant.getItemId(), ApiKey.ApiKeyType.PUBLIC, null).getPlainTextKey();
        String privateKeyValue = tenantService.generateApiKeyWithType(tenant.getItemId(), ApiKey.ApiKeyType.PRIVATE, null).getPlainTextKey();
        persistenceService.refresh();

        try {
            // Test without any authentication
            try (CloseableHttpResponse response = executeHttpRequest(new HttpGet(getFullUrl("/cxs/profiles/count")), AuthType.NONE)) {
                Assert.assertEquals("Unauthenticated private request should be rejected", 401, response.getStatusLine().getStatusCode());
            }

            // Test with public API key (should fail)
            HttpGet privateRequest = new HttpGet(getFullUrl("/cxs/profiles/count"));
            privateRequest.setHeader("X-Unomi-Api-Key", publicKeyValue);
            try (CloseableHttpResponse response = executeHttpRequest(privateRequest, AuthType.PUBLIC_KEY)) {
                Assert.assertEquals("Public API key should not grant access to private endpoints", 401, response.getStatusLine().getStatusCode());
            }

            // Test with invalid private API key (should fail)
            privateRequest = new HttpGet(getFullUrl("/cxs/profiles/count"));
            privateRequest.setHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                (tenant.getItemId() + ":wrong-key").getBytes()));
            try (CloseableHttpResponse response = executeHttpRequest(privateRequest, AuthType.PRIVATE_KEY)) {
                Assert.assertEquals("Invalid private API key should be rejected", 401, response.getStatusLine().getStatusCode());
            }

            // Test with valid private API key (should succeed)
            privateRequest = new HttpGet(getFullUrl("/cxs/profiles/count"));
            privateRequest.setHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                (tenant.getItemId() + ":" + privateKeyValue).getBytes()));
            try (CloseableHttpResponse response = executeHttpRequest(privateRequest, AuthType.PRIVATE_KEY)) {
                Assert.assertEquals("Valid private API key should grant access to private endpoints", 200, response.getStatusLine().getStatusCode());
            }

            // Test with JAAS auth (should succeed) — use a fresh request to avoid carrying Authorization from previous step
            BasicCredentialsProvider adminCredsProvider = new BasicCredentialsProvider();
            adminCredsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("karaf", "karaf"));
            try (CloseableHttpClient adminClient = HttpClients.custom().setDefaultCredentialsProvider(adminCredsProvider).build();
                 CloseableHttpResponse response = adminClient.execute(new HttpGet(getFullUrl("/cxs/profiles/count")))) {
                Assert.assertEquals("JAAS auth should grant access to private endpoints", 200, response.getStatusLine().getStatusCode());
            }
        } finally {
            tenantService.deleteTenant(tenant.getItemId());
        }
    }

    @Test
    public void testTenantIsolation() throws Exception {
        // Create two tenants
        Tenant tenant1 = tenantService.createTenant("tenant-1", Collections.emptyMap());
        Tenant tenant2 = tenantService.createTenant("tenant-2", Collections.emptyMap());

        try {
            // Create profile in tenant1
            executionContextManager.executeAsTenant(tenant1.getItemId(), () -> {
                Profile profile1 = new Profile();
                profile1.setItemId("profile1");
                profile1.setProperty("name", "John");
                persistenceService.save(profile1);
            });

            // Try to access profile from tenant2
            executionContextManager.executeAsTenant(tenant2.getItemId(), () -> {
                Profile loadedProfile = persistenceService.load("profile1", Profile.class);
                Assert.assertNull("Profile should not be accessible from different tenants", loadedProfile);
            });
        } finally {
            tenantService.deleteTenant(tenant1.getItemId());
            tenantService.deleteTenant(tenant2.getItemId());
        }
    }

    @Test
    public void testApiKeyAuthentication() throws Exception {
        // Create test tenant
        Tenant tenant = tenantService.createTenant("test-tenant-auth", Collections.emptyMap());

        try {
            // Test with private API key (should succeed)
            ApiKeyCreationResult privateKeyResult = tenantService.generateApiKeyWithType(tenant.getItemId(), ApiKey.ApiKeyType.PRIVATE, null);
            HttpGet getRequest = new HttpGet(getFullUrl("/cxs/profiles/count"));
            getRequest.setHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                (tenant.getItemId() + ":" + privateKeyResult.getPlainTextKey()).getBytes()));
            try (CloseableHttpResponse response = executeHttpRequest(getRequest, AuthType.PRIVATE_KEY)) {
                Assert.assertEquals("Private API key should grant access to private endpoints", 200, response.getStatusLine().getStatusCode());
            }

            // Test with JAAS authentication (should succeed)
            getRequest = new HttpGet(getFullUrl("/cxs/profiles/count"));
            getRequest.setHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(("karaf:karaf").getBytes()));
            try (CloseableHttpResponse response = executeHttpRequest(getRequest, AuthType.JAAS_ADMIN)) {
                Assert.assertEquals("JAAS authentication should grant access to private endpoints", 200, response.getStatusLine().getStatusCode());
            }

            // Test with public API key (should fail)
            ApiKeyCreationResult publicKeyResult = tenantService.generateApiKeyWithType(tenant.getItemId(), ApiKey.ApiKeyType.PUBLIC, null);
            getRequest = new HttpGet(getFullUrl("/cxs/profiles/count"));
            getRequest.setHeader("X-Unomi-Api-Key", publicKeyResult.getPlainTextKey());
            try (CloseableHttpResponse response = executeHttpRequest(getRequest, AuthType.PUBLIC_KEY)) {
                Assert.assertEquals("Public API key should not grant access to private endpoints", 401, response.getStatusLine().getStatusCode());
            }

            // Test without any authentication (should fail)
            getRequest = new HttpGet(getFullUrl("/cxs/profiles/count"));
            try (CloseableHttpResponse response = executeHttpRequest(getRequest, AuthType.NONE)) {
                Assert.assertEquals("Unauthenticated request should be rejected", 401, response.getStatusLine().getStatusCode());
            }
        } finally {
            // Cleanup
            tenantService.deleteTenant(tenant.getItemId());
        }
    }

    @Test
    public void testExpiredApiKey() throws Exception {
        Tenant tenant = tenantService.createTenant("expired-tenant", Collections.emptyMap());
        try {
            ApiKeyCreationResult apiKeyResult = tenantService.generateApiKey(tenant.getItemId(), 1L); // 1ms validity
            Thread.sleep(2); // Wait for key to expire
            Assert.assertFalse(tenantService.validateApiKey(tenant.getItemId(), apiKeyResult.getPlainTextKey()));
        } finally {
            tenantService.deleteTenant(tenant.getItemId());
        }
    }

    @Test
    public void testTenantDeletion() throws Exception {
        Tenant tenant = tenantService.createTenant("delete-test", Collections.emptyMap());

        try {
            executionContextManager.executeAsTenant(tenant.getItemId(), () -> {
                Profile profile = new Profile();
                profile.setItemId("delete-test-profile");
                persistenceService.save(profile);
            });
        } catch (Exception e) {
            tenantService.deleteTenant(tenant.getItemId());
            throw e;
        }

        // Deletion is the operation under test
        tenantService.deleteTenant(tenant.getItemId());

        Profile loadedProfile = persistenceService.load("delete-test-profile", Profile.class);
        Assert.assertNull(loadedProfile);
    }

    @Test
    public void testCrossSearchPrevention() throws Exception {
        // Create two tenants
        Tenant tenant1 = tenantService.createTenant("search-test-1", Collections.emptyMap());
        Tenant tenant2 = tenantService.createTenant("search-test-2", Collections.emptyMap());

        try {
            // Add data to tenant1
            executionContextManager.executeAsTenant(tenant1.getItemId(), () -> {
                for (int i = 0; i < 10; i++) {
                    Profile profile = new Profile();
                    profile.setItemId("search-test-" + i);
                    profile.setProperty("testKey", "testValue");
                    persistenceService.save(profile);
                }
            });

            // Search from tenant2
            executionContextManager.executeAsTenant(tenant2.getItemId(), () -> {
                Query query = new Query();
                List<Profile> results = persistenceService.query("testKey", "testValue", null, Profile.class);
                Assert.assertEquals(0, results.size());
            });
        } finally {
            tenantService.deleteTenant(tenant1.getItemId());
            tenantService.deleteTenant(tenant2.getItemId());
        }
    }

    @Test
    public void testPublicPrivateApiKeys() throws Exception {
        Tenant tenant = tenantService.createTenant("dual-key-tenant", Collections.emptyMap());

        try {
            // Generate fresh keys to capture their one-time plaintext values (UNOMI-938: getApiKey() only
            // returns metadata — a hash and a masked key — never the plaintext value).
            ApiKeyCreationResult publicKeyResult = tenantService.generateApiKeyWithType(tenant.getItemId(), ApiKey.ApiKeyType.PUBLIC, null);
            ApiKeyCreationResult privateKeyResult = tenantService.generateApiKeyWithType(tenant.getItemId(), ApiKey.ApiKeyType.PRIVATE, null);
            ApiKey publicKey = publicKeyResult.getApiKey();
            ApiKey privateKey = privateKeyResult.getApiKey();

            Assert.assertNotNull("Public key should exist", publicKey);
            Assert.assertNotNull("Private key should exist", privateKey);
            Assert.assertEquals("Public key should have correct type", ApiKey.ApiKeyType.PUBLIC, publicKey.getKeyType());
            Assert.assertEquals("Private key should have correct type", ApiKey.ApiKeyType.PRIVATE, privateKey.getKeyType());

            Assert.assertTrue("Public key should validate as public",
                tenantService.validateApiKeyWithType(tenant.getItemId(), publicKeyResult.getPlainTextKey(), ApiKey.ApiKeyType.PUBLIC));
            Assert.assertFalse("Public key should not validate as private",
                tenantService.validateApiKeyWithType(tenant.getItemId(), publicKeyResult.getPlainTextKey(), ApiKey.ApiKeyType.PRIVATE));
            Assert.assertTrue("Private key should validate as private",
                tenantService.validateApiKeyWithType(tenant.getItemId(), privateKeyResult.getPlainTextKey(), ApiKey.ApiKeyType.PRIVATE));
            Assert.assertFalse("Private key should not validate as public",
                tenantService.validateApiKeyWithType(tenant.getItemId(), privateKeyResult.getPlainTextKey(), ApiKey.ApiKeyType.PUBLIC));
        } finally {
            tenantService.deleteTenant(tenant.getItemId());
        }
    }

    @Test
    public void testTenantLookupByApiKey() throws Exception {
        Tenant tenant = tenantService.createTenant("lookup-tenant", Collections.emptyMap());

        try {
            // Generate fresh keys to capture their one-time plaintext values (UNOMI-938: getApiKey() only
            // returns metadata — a hash and a masked key — never the plaintext value).
            String publicKeyValue = tenantService.generateApiKeyWithType(tenant.getItemId(), ApiKey.ApiKeyType.PUBLIC, null).getPlainTextKey();
            String privateKeyValue = tenantService.generateApiKeyWithType(tenant.getItemId(), ApiKey.ApiKeyType.PRIVATE, null).getPlainTextKey();

            persistenceService.refresh();

            Tenant foundByPublic = tenantService.getTenantByApiKey(publicKeyValue);
            Tenant foundByPrivate = tenantService.getTenantByApiKey(privateKeyValue);

            Assert.assertEquals("Should find correct tenant by public key", tenant.getItemId(), foundByPublic.getItemId());
            Assert.assertEquals("Should find correct tenant by private key", tenant.getItemId(), foundByPrivate.getItemId());

            Tenant foundByPublicAsPublic = tenantService.getTenantByApiKey(publicKeyValue, ApiKey.ApiKeyType.PUBLIC);
            Tenant foundByPublicAsPrivate = tenantService.getTenantByApiKey(publicKeyValue, ApiKey.ApiKeyType.PRIVATE);
            Tenant foundByPrivateAsPrivate = tenantService.getTenantByApiKey(privateKeyValue, ApiKey.ApiKeyType.PRIVATE);
            Tenant foundByPrivateAsPublic = tenantService.getTenantByApiKey(privateKeyValue, ApiKey.ApiKeyType.PUBLIC);

            Assert.assertNotNull("Should find tenant by public key when type matches", foundByPublicAsPublic);
            Assert.assertNull("Should not find tenant by public key when type is private", foundByPublicAsPrivate);
            Assert.assertNotNull("Should find tenant by private key when type matches", foundByPrivateAsPrivate);
            Assert.assertNull("Should not find tenant by private key when type is public", foundByPrivateAsPublic);
        } finally {
            tenantService.deleteTenant(tenant.getItemId());
        }
    }

    @Test
    public void testTenantIdValidation() throws Exception {
        // Test tenant ID too long (>32 chars)
        try {
            tenantService.createTenant("this-tenant-id-is-way-too-long-to-be-valid", Collections.emptyMap());
            Assert.fail("Should reject tenant ID longer than 32 characters");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        // Test tenant ID with invalid characters
        try {
            tenantService.createTenant("invalid@chars#here", Collections.emptyMap());
            Assert.fail("Should reject tenant ID with invalid characters");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        // Test tenant ID starting with hyphen
        try {
            tenantService.createTenant("-invalid-start", Collections.emptyMap());
            Assert.fail("Should reject tenant ID starting with hyphen");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        // Test tenant ID ending with hyphen
        try {
            tenantService.createTenant("invalid-end-", Collections.emptyMap());
            Assert.fail("Should reject tenant ID ending with hyphen");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        // Test tenant ID starting with underscore
        try {
            tenantService.createTenant("_invalid_start", Collections.emptyMap());
            Assert.fail("Should reject tenant ID starting with underscore");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        // Test tenant ID ending with underscore
        try {
            tenantService.createTenant("invalid_end_", Collections.emptyMap());
            Assert.fail("Should reject tenant ID ending with underscore");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        // Test system tenant ID
        try {
            tenantService.createTenant("SYSTEM", Collections.emptyMap());
            Assert.fail("Should reject SYSTEM tenant ID");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        // Test duplicate tenant ID
        Tenant tenant = tenantService.createTenant("valid-tenant", Collections.emptyMap());
        try {
            tenantService.createTenant("valid-tenant", Collections.emptyMap());
            Assert.fail("Should reject duplicate tenant ID");
        } catch (IllegalArgumentException e) {
            // Expected
        } finally {
            tenantService.deleteTenant(tenant.getItemId());
        }

        // Test valid tenant ID with hyphens
        tenant = tenantService.createTenant("valid-tenant-123", Collections.emptyMap());
        Assert.assertNotNull("Should create tenant with valid ID containing hyphens", tenant);
        Assert.assertEquals("Tenant ID should match requested ID", "valid-tenant-123", tenant.getItemId());
        tenantService.deleteTenant(tenant.getItemId());

        // Test valid tenant ID with underscores
        tenant = tenantService.createTenant("valid_tenant_123", Collections.emptyMap());
        Assert.assertNotNull("Should create tenant with valid ID containing underscores", tenant);
        Assert.assertEquals("Tenant ID should match requested ID", "valid_tenant_123", tenant.getItemId());
        tenantService.deleteTenant(tenant.getItemId());

        // Test valid tenant ID with mix of hyphens and underscores
        tenant = tenantService.createTenant("valid-tenant_123", Collections.emptyMap());
        Assert.assertNotNull("Should create tenant with valid ID containing both hyphens and underscores", tenant);
        Assert.assertEquals("Tenant ID should match requested ID", "valid-tenant_123", tenant.getItemId());
        tenantService.deleteTenant(tenant.getItemId());
    }

    @Test
    public void testContextJsonAuthenticationDetection() throws Exception {
        // Test that context.json is properly detected as a public endpoint
        // This test verifies that the AUTO authentication works correctly
        String sessionId = "test-session-" + System.currentTimeMillis();
        try (CloseableHttpResponse response = executeHttpRequest(new HttpGet(getFullUrl("/context.json?sessionId=" + sessionId)), AuthType.AUTO)) {
            // Should succeed with public key authentication
            Assert.assertEquals("context.json should be accessible with auto-detected public authentication",
                200, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testTenantUsageEndpoint() throws Exception {
        Tenant tenant = tenantService.createTenant("usage-test-tenant", Collections.emptyMap());
        try {
            String usageUrl = getFullUrl(REST_ENDPOINT + "/" + tenant.getItemId() + "/usage");
            String usageResponse;
            try (CloseableHttpResponse response = executeHttpRequest(new HttpGet(usageUrl), AuthType.JAAS_ADMIN)) {
                Assert.assertEquals("Usage endpoint should return 200", 200, response.getStatusLine().getStatusCode());
                usageResponse = EntityUtils.toString(response.getEntity());
            }
            Map<?, ?> usage = getObjectMapper().readValue(usageResponse, Map.class);
            Assert.assertEquals("Usage tenantId should match", tenant.getItemId(), usage.get("tenantId"));
            Assert.assertTrue("Default period should normalize to YYYY-MM", usage.get("period").toString().matches("\\d{4}-\\d{2}"));
            Assert.assertNotNull("periodStart should be present", usage.get("periodStart"));
            Assert.assertNotNull("periodEnd should be present", usage.get("periodEnd"));
            Assert.assertNotNull("scopeCount should be present", usage.get("scopeCount"));
            Assert.assertNotNull("activeApiKeyCount should be present", usage.get("activeApiKeyCount"));
            Assert.assertNotNull("scopeUsages should be present", usage.get("scopeUsages"));
            Assert.assertNotNull("collectedAt should be present", usage.get("collectedAt"));

            String legacyPeriodUrl = usageUrl + "?period=24h";
            try (CloseableHttpResponse response = executeHttpRequest(new HttpGet(legacyPeriodUrl), AuthType.JAAS_ADMIN)) {
                Assert.assertEquals("Legacy 24h period should return 200", 200, response.getStatusLine().getStatusCode());
            }

            String badPeriodUrl = usageUrl + "?period=7d";
            try (CloseableHttpResponse response = executeHttpRequest(new HttpGet(badPeriodUrl), AuthType.JAAS_ADMIN)) {
                Assert.assertEquals("Unsupported period should return 400", 400, response.getStatusLine().getStatusCode());
            }
        } finally {
            tenantService.deleteTenant(tenant.getItemId());
        }
    }

    @Test
    public void testTenantEventPurgeEndpoint() throws Exception {
        Tenant tenant = tenantService.createTenant("purge-test-tenant", Collections.emptyMap());
        try {
            String purgeUrl = getFullUrl(REST_ENDPOINT + "/" + tenant.getItemId() + "/purge/events?retentionDays=90");
            String purgeResponse;
            try (CloseableHttpResponse response = executeHttpRequest(new HttpPost(purgeUrl), AuthType.JAAS_ADMIN)) {
                Assert.assertEquals("Purge endpoint should return 200", 200, response.getStatusLine().getStatusCode());
                purgeResponse = EntityUtils.toString(response.getEntity());
            }
            Map<?, ?> purge = getObjectMapper().readValue(purgeResponse, Map.class);
            Assert.assertEquals("Purge tenantId should match", tenant.getItemId(), purge.get("tenantId"));
            Assert.assertEquals("Retention days should match", 90, purge.get("retentionDays"));
            Assert.assertNotNull("eventsMatched should be present", purge.get("eventsMatched"));
            Assert.assertNotNull("purgeRequested should be present", purge.get("purgeRequested"));

            String lowRetentionUrl = getFullUrl(REST_ENDPOINT + "/" + tenant.getItemId() + "/purge/events?retentionDays=3");
            try (CloseableHttpResponse response = executeHttpRequest(new HttpPost(lowRetentionUrl), AuthType.JAAS_ADMIN)) {
                Assert.assertEquals("Retention below minimum should return 400", 400, response.getStatusLine().getStatusCode());
            }

            String missingTenantUrl = getFullUrl(REST_ENDPOINT + "/missing-tenant/purge/events?retentionDays=90");
            try (CloseableHttpResponse response = executeHttpRequest(new HttpPost(missingTenantUrl), AuthType.JAAS_ADMIN)) {
                Assert.assertEquals("Missing tenant should return 404", 404, response.getStatusLine().getStatusCode());
            }
        } finally {
            tenantService.deleteTenant(tenant.getItemId());
        }
    }

    @Test
    public void testTenantUsageEndpointNotFound() throws Exception {
        try (CloseableHttpResponse response = executeHttpRequest(
                new HttpGet(getFullUrl(REST_ENDPOINT + "/missing-usage-tenant/usage")), AuthType.JAAS_ADMIN)) {
            Assert.assertEquals("Missing tenant usage request should return 404", 404,
                    response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testTenantUsageEndpointRequiresAuthentication() throws Exception {
        Tenant tenant = tenantService.createTenant("usage-auth-tenant", Collections.emptyMap());
        try {
            String usageUrl = getFullUrl(REST_ENDPOINT + "/" + tenant.getItemId() + "/usage");
            try (CloseableHttpResponse response = executeHttpRequest(new HttpGet(usageUrl), AuthType.NONE)) {
                Assert.assertEquals("Unauthenticated usage request should be rejected", 401,
                        response.getStatusLine().getStatusCode());
            }
        } finally {
            tenantService.deleteTenant(tenant.getItemId());
        }
    }

    @Test
    public void testTenantUsageEndpointWithExplicitPeriod() throws Exception {
        Tenant tenant = tenantService.createTenant("usage-period-tenant", Collections.emptyMap());
        try {
            String period = YearMonth.now(java.time.ZoneOffset.UTC).toString();
            String usageUrl = getFullUrl(REST_ENDPOINT + "/" + tenant.getItemId() + "/usage?period=" + period);
            try (CloseableHttpResponse response = executeHttpRequest(new HttpGet(usageUrl), AuthType.JAAS_ADMIN)) {
                Assert.assertEquals("Explicit period usage request should return 200", 200,
                        response.getStatusLine().getStatusCode());
                Map<?, ?> usage = getObjectMapper().readValue(EntityUtils.toString(response.getEntity()), Map.class);
                Assert.assertEquals("Period should match requested month", period, usage.get("period"));
            }
        } finally {
            tenantService.deleteTenant(tenant.getItemId());
        }
    }

    @Test
    public void testTenantUsageReflectsSeededTenantData() throws Exception {
        Tenant tenant = tenantService.createTenant("usage-seeded-tenant", Collections.emptyMap());
        try {
            executionContextManager.executeAsTenant(tenant.getItemId(), () -> {
                TestUtils.createScope("usage-scope", "Usage Scope", scopeService);
                Profile profile = new Profile();
                profile.setItemId("usage-profile");
                persistenceService.save(profile);
                Event event = new Event();
                event.setItemId("usage-event");
                event.setEventType("pageView");
                event.setProfileId(profile.getItemId());
                event.setScope("usage-scope");
                event.setTimeStamp(new Date());
                persistenceService.save(event);
                Metadata segmentMetadata = new Metadata("usage-segment");
                segmentMetadata.setScope("usage-scope");
                segmentMetadata.setEnabled(false);
                Segment segment = new Segment();
                segment.setMetadata(segmentMetadata);
                segment.setCondition(null);
                segmentService.setSegmentDefinition(segment);
            });

            String usageUrl = getFullUrl(REST_ENDPOINT + "/" + tenant.getItemId() + "/usage");
            Map<?, ?> usage = keepTrying("Usage should reflect seeded tenant data", () -> {
                try (CloseableHttpResponse response = executeHttpRequest(new HttpGet(usageUrl), AuthType.JAAS_ADMIN)) {
                    if (response.getStatusLine().getStatusCode() != 200) {
                        return null;
                    }
                    Map<?, ?> body = getObjectMapper().readValue(EntityUtils.toString(response.getEntity()), Map.class);
                    Number profileCount = (Number) body.get("profileCount");
                    Number eventCount = (Number) body.get("eventCount");
                    Number scopeCount = (Number) body.get("scopeCount");
                    if (profileCount == null || profileCount.longValue() < 1L) {
                        return null;
                    }
                    if (eventCount == null || eventCount.longValue() < 1L) {
                        return null;
                    }
                    if (scopeCount == null || scopeCount.longValue() < 1L) {
                        return null;
                    }
                    return body;
                } catch (Exception e) {
                    return null;
                }
            }, Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

            Assert.assertNotNull("Usage response should be populated", usage);
            Assert.assertTrue("Active API key count should include tenant keys",
                    ((Number) usage.get("activeApiKeyCount")).longValue() >= 2L);
            List<?> scopeUsages = (List<?>) usage.get("scopeUsages");
            Assert.assertNotNull("scopeUsages should be present", scopeUsages);
            boolean foundScope = false;
            for (Object entry : scopeUsages) {
                Map<?, ?> scopeUsage = (Map<?, ?>) entry;
                if ("usage-scope".equals(scopeUsage.get("scopeId"))) {
                    foundScope = true;
                    Assert.assertTrue("Segment count for scope should be at least 1",
                            ((Number) scopeUsage.get("segmentCount")).longValue() >= 1L);
                }
            }
            Assert.assertTrue("scopeUsages should include the seeded scope", foundScope);
        } finally {
            tenantService.deleteTenant(tenant.getItemId());
        }
    }

    @Test
    public void testTenantEventPurgeEndpointRequiresAuthentication() throws Exception {
        Tenant tenant = tenantService.createTenant("purge-auth-tenant", Collections.emptyMap());
        try {
            String purgeUrl = getFullUrl(REST_ENDPOINT + "/" + tenant.getItemId() + "/purge/events?retentionDays=90");
            try (CloseableHttpResponse response = executeHttpRequest(new HttpPost(purgeUrl), AuthType.NONE)) {
                Assert.assertEquals("Unauthenticated purge request should be rejected", 401,
                        response.getStatusLine().getStatusCode());
            }
        } finally {
            tenantService.deleteTenant(tenant.getItemId());
        }
    }

    @Test
    public void testTenantEventPurgeEndpointRejectsNonPositiveRetention() throws Exception {
        Tenant tenant = tenantService.createTenant("purge-invalid-tenant", Collections.emptyMap());
        try {
            String purgeUrl = getFullUrl(REST_ENDPOINT + "/" + tenant.getItemId() + "/purge/events?retentionDays=0");
            try (CloseableHttpResponse response = executeHttpRequest(new HttpPost(purgeUrl), AuthType.JAAS_ADMIN)) {
                Assert.assertEquals("Non-positive retention should return 400", 400,
                        response.getStatusLine().getStatusCode());
            }
        } finally {
            tenantService.deleteTenant(tenant.getItemId());
        }
    }


}
