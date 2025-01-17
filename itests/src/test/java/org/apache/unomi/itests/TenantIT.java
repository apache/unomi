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

import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.tenants.ApiKey;
import org.apache.unomi.api.tenants.ResourceQuota;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class TenantIT extends BaseIT {

    private static final String REST_ENDPOINT = "http://localhost:8181/cxs/tenants";
    private CloseableHttpClient httpClient;
    private CustomObjectMapper objectMapper;

    @Inject
    private TenantService tenantService;

    @Before
    public void setUp() {
        httpClient = HttpClients.createDefault();
        objectMapper = new CustomObjectMapper();
    }

    @Test
    public void testRestEndpoint() throws Exception {
        // Test create tenant
        Map<String, Object> properties = new HashMap<>();
        properties.put("testProperty", "testValue");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", "RestTestTenant");
        requestBody.put("properties", properties);

        HttpPost createRequest = new HttpPost(REST_ENDPOINT);
        createRequest.setEntity(new StringEntity(objectMapper.writeValueAsString(requestBody), ContentType.APPLICATION_JSON));

        String createResponse = EntityUtils.toString(httpClient.execute(createRequest).getEntity());
        Tenant createdTenant = objectMapper.readValue(createResponse, Tenant.class);

        Assert.assertNotNull("Created tenant should not be null", createdTenant);
        Assert.assertEquals("RestTestTenant", createdTenant.getName());
        Assert.assertNotNull("Tenant should have public API key", createdTenant.getPublicApiKey());
        Assert.assertNotNull("Tenant should have private API key", createdTenant.getPrivateApiKey());

        // Test get tenant
        HttpGet getRequest = new HttpGet(REST_ENDPOINT + "/" + createdTenant.getItemId());
        String getResponse = EntityUtils.toString(httpClient.execute(getRequest).getEntity());
        Tenant retrievedTenant = objectMapper.readValue(getResponse, Tenant.class);

        Assert.assertEquals("Retrieved tenant should match created tenant", createdTenant.getItemId(), retrievedTenant.getItemId());

        // Test update tenant
        retrievedTenant.setName("UpdatedRestTestTenant");
        ResourceQuota quota = new ResourceQuota();
        quota.setMaxProfiles(1000L);
        quota.setMaxEvents(5000L);
        retrievedTenant.setResourceQuota(quota);

        HttpPut updateRequest = new HttpPut(REST_ENDPOINT + "/" + retrievedTenant.getItemId());
        updateRequest.setEntity(new StringEntity(objectMapper.writeValueAsString(retrievedTenant), ContentType.APPLICATION_JSON));

        String updateResponse = EntityUtils.toString(httpClient.execute(updateRequest).getEntity());
        Tenant updatedTenant = objectMapper.readValue(updateResponse, Tenant.class);

        Assert.assertEquals("Tenant name should be updated", "UpdatedRestTestTenant", updatedTenant.getName());
        Assert.assertEquals("Tenant quota should be updated", 1000L, updatedTenant.getResourceQuota().getMaxProfiles().longValue());

        // Test generate new API key
        String generateKeyUrl = String.format("%s/%s/apikeys?type=%s&validityDays=30",
            REST_ENDPOINT, updatedTenant.getItemId(), ApiKey.ApiKeyType.PUBLIC.name());
        HttpPost generateKeyRequest = new HttpPost(generateKeyUrl);

        String generateKeyResponse = EntityUtils.toString(httpClient.execute(generateKeyRequest).getEntity());
        ApiKey newApiKey = objectMapper.readValue(generateKeyResponse, ApiKey.class);

        Assert.assertNotNull("New API key should not be null", newApiKey);
        Assert.assertEquals("API key type should match requested type", ApiKey.ApiKeyType.PUBLIC, newApiKey.getKeyType());

        // Test validate API key
        String validateKeyUrl = String.format("%s/%s/apikeys/validate?key=%s&type=%s",
            REST_ENDPOINT, updatedTenant.getItemId(), newApiKey.getKey(), ApiKey.ApiKeyType.PUBLIC.name());
        HttpGet validateKeyRequest = new HttpGet(validateKeyUrl);

        int validateResponse = httpClient.execute(validateKeyRequest).getStatusLine().getStatusCode();
        Assert.assertEquals("API key validation should succeed", 200, validateResponse);

        // Test validate with wrong type
        String validateWrongTypeUrl = String.format("%s/%s/apikeys/validate?key=%s&type=%s",
            REST_ENDPOINT, updatedTenant.getItemId(), newApiKey.getKey(), ApiKey.ApiKeyType.PRIVATE.name());
        HttpGet validateWrongTypeRequest = new HttpGet(validateWrongTypeUrl);

        int validateWrongTypeResponse = httpClient.execute(validateWrongTypeRequest).getStatusLine().getStatusCode();
        Assert.assertEquals("API key validation with wrong type should fail", 401, validateWrongTypeResponse);

        // Test delete tenant
        HttpDelete deleteRequest = new HttpDelete(REST_ENDPOINT + "/" + updatedTenant.getItemId());
        int deleteResponse = httpClient.execute(deleteRequest).getStatusLine().getStatusCode();

        Assert.assertEquals("Delete response should be 204", 204, deleteResponse);

        // Verify tenant is deleted
        HttpGet verifyDeleteRequest = new HttpGet(REST_ENDPOINT + "/" + updatedTenant.getItemId());
        int verifyDeleteResponse = httpClient.execute(verifyDeleteRequest).getStatusLine().getStatusCode();

        Assert.assertEquals("Get deleted tenant should return 404", 404, verifyDeleteResponse);
    }

    @Test
    public void testTenantIsolation() throws Exception {
        // Create two tenants
        Tenant tenant1 = tenantService.createTenant("Tenant1", Collections.emptyMap());
        Tenant tenant2 = tenantService.createTenant("Tenant2", Collections.emptyMap());

        // Generate API keys
        ApiKey apiKey1 = tenantService.generateApiKey(tenant1.getItemId(), null);
        ApiKey apiKey2 = tenantService.generateApiKey(tenant2.getItemId(), null);

        // Create profile in tenant1
        tenantService.setCurrentTenant(tenant1.getItemId());
        Profile profile1 = new Profile();
        profile1.setItemId("profile1");
        profile1.setProperty("name", "John");
        persistenceService.save(profile1);

        // Try to access profile from tenant2
        tenantService.setCurrentTenant(tenant2.getItemId());
        Profile loadedProfile = persistenceService.load("profile1", Profile.class);
        Assert.assertNull("Profile should not be accessible from different tenants", loadedProfile);
    }

    @Test
    public void testApiKeyAuthentication() throws Exception {
        // Create tenants
        Tenant tenant = tenantService.createTenant("TestTenant", Collections.emptyMap());
        ApiKey apiKey = tenantService.generateApiKey(tenant.getItemId(), null);

        // Test valid API key
        String authHeader = tenant.getItemId() + ":" + apiKey.getItemId();
        Assert.assertTrue(tenantService.validateApiKey(tenant.getItemId(), apiKey.getItemId()));

        // Test invalid API key
        String invalidAuthHeader = tenant.getItemId() + ":invalid-key";
        Assert.assertFalse(tenantService.validateApiKey(tenant.getItemId(), "invalid-key"));
    }

    @Test
    public void testExpiredApiKey() throws Exception {
        // Create tenants with short-lived API key
        Tenant tenant = tenantService.createTenant("ExpiredTenant", Collections.emptyMap());
        ApiKey apiKey = tenantService.generateApiKey(tenant.getItemId(), 1L); // 1ms validity

        Thread.sleep(2); // Wait for key to expire

        Assert.assertFalse(tenantService.validateApiKey(tenant.getItemId(), apiKey.getItemId()));
    }


    @Test
    public void testTenantDeletion() throws Exception {
        // Create tenants
        Tenant tenant = tenantService.createTenant("DeleteTest", Collections.emptyMap());

        // Create data for tenants
        tenantService.setCurrentTenant(tenant.getItemId());
        Profile profile = new Profile();
        profile.setItemId("delete-test-profile");
        persistenceService.save(profile);

        // Delete tenants
        tenantService.deleteTenant(tenant.getItemId());

        // Verify data is inaccessible
        Profile loadedProfile = persistenceService.load("delete-test-profile", Profile.class);
        Assert.assertNull(loadedProfile);
    }

    @Test
    public void testCrossSearchPrevention() throws Exception {
        // Create two tenants
        Tenant tenant1 = tenantService.createTenant("SearchTest1", Collections.emptyMap());
        Tenant tenant2 = tenantService.createTenant("SearchTest2", Collections.emptyMap());

        // Add data to tenant1
        tenantService.setCurrentTenant(tenant1.getItemId());
        for (int i = 0; i < 10; i++) {
            Profile profile = new Profile();
            profile.setItemId("search-test-" + i);
            profile.setProperty("testKey", "testValue");
            persistenceService.save(profile);
        }

        // Search from tenant2
        tenantService.setCurrentTenant(tenant2.getItemId());
        Query query = new Query();
        List<Profile> results = persistenceService.query("testKey", "testValue", null, Profile.class);

        Assert.assertEquals(0, results.size());
    }

    @Test
    public void testPublicPrivateApiKeys() throws Exception {
        // Create tenant
        Tenant tenant = tenantService.createTenant("DualKeyTenant", Collections.emptyMap());

        // Verify both keys were created during tenant creation
        ApiKey publicKey = tenantService.getApiKey(tenant.getItemId(), ApiKey.ApiKeyType.PUBLIC);
        ApiKey privateKey = tenantService.getApiKey(tenant.getItemId(), ApiKey.ApiKeyType.PRIVATE);

        Assert.assertNotNull("Public key should exist", publicKey);
        Assert.assertNotNull("Private key should exist", privateKey);
        Assert.assertEquals("Public key should have correct type", ApiKey.ApiKeyType.PUBLIC, publicKey.getKeyType());
        Assert.assertEquals("Private key should have correct type", ApiKey.ApiKeyType.PRIVATE, privateKey.getKeyType());

        // Test key type validation
        Assert.assertTrue("Public key should validate as public",
            tenantService.validateApiKeyWithType(tenant.getItemId(), publicKey.getKey(), ApiKey.ApiKeyType.PUBLIC));
        Assert.assertFalse("Public key should not validate as private",
            tenantService.validateApiKeyWithType(tenant.getItemId(), publicKey.getKey(), ApiKey.ApiKeyType.PRIVATE));
        Assert.assertTrue("Private key should validate as private",
            tenantService.validateApiKeyWithType(tenant.getItemId(), privateKey.getKey(), ApiKey.ApiKeyType.PRIVATE));
        Assert.assertFalse("Private key should not validate as public",
            tenantService.validateApiKeyWithType(tenant.getItemId(), privateKey.getKey(), ApiKey.ApiKeyType.PUBLIC));
    }

    @Test
    public void testTenantLookupByApiKey() throws Exception {
        // Create tenant
        Tenant tenant = tenantService.createTenant("LookupTenant", Collections.emptyMap());
        ApiKey publicKey = tenantService.getApiKey(tenant.getItemId(), ApiKey.ApiKeyType.PUBLIC);
        ApiKey privateKey = tenantService.getApiKey(tenant.getItemId(), ApiKey.ApiKeyType.PRIVATE);

        // Test lookup by key
        Tenant foundByPublic = tenantService.getTenantByApiKey(publicKey.getKey());
        Tenant foundByPrivate = tenantService.getTenantByApiKey(privateKey.getKey());

        Assert.assertEquals("Should find correct tenant by public key", tenant.getItemId(), foundByPublic.getItemId());
        Assert.assertEquals("Should find correct tenant by private key", tenant.getItemId(), foundByPrivate.getItemId());

        // Test lookup with type validation
        Tenant foundByPublicAsPublic = tenantService.getTenantByApiKey(publicKey.getKey(), ApiKey.ApiKeyType.PUBLIC);
        Tenant foundByPublicAsPrivate = tenantService.getTenantByApiKey(publicKey.getKey(), ApiKey.ApiKeyType.PRIVATE);
        Tenant foundByPrivateAsPrivate = tenantService.getTenantByApiKey(privateKey.getKey(), ApiKey.ApiKeyType.PRIVATE);
        Tenant foundByPrivateAsPublic = tenantService.getTenantByApiKey(privateKey.getKey(), ApiKey.ApiKeyType.PUBLIC);

        Assert.assertNotNull("Should find tenant by public key when type matches", foundByPublicAsPublic);
        Assert.assertNull("Should not find tenant by public key when type is private", foundByPublicAsPrivate);
        Assert.assertNotNull("Should find tenant by private key when type matches", foundByPrivateAsPrivate);
        Assert.assertNull("Should not find tenant by private key when type is public", foundByPrivateAsPublic);
    }
}
