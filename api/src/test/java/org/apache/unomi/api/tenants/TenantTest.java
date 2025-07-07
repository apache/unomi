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
package org.apache.unomi.api.tenants;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

/**
 * Unit tests for the Tenant class, specifically testing the API key resolution functionality.
 */
public class TenantTest {

    @Test
    public void testGetPrivateApiKeyWithNoApiKeys() {
        Tenant tenant = new Tenant();
        tenant.setApiKeys(null);
        
        assertNull("Private API key should be null when no API keys exist", tenant.getPrivateApiKey());
    }

    @Test
    public void testGetPrivateApiKeyWithEmptyApiKeys() {
        Tenant tenant = new Tenant();
        tenant.setApiKeys(new ArrayList<>());
        
        assertNull("Private API key should be null when API keys list is empty", tenant.getPrivateApiKey());
    }

    @Test
    public void testGetPrivateApiKeyWithOnlyPublicKeys() {
        Tenant tenant = new Tenant();
        List<ApiKey> apiKeys = new ArrayList<>();
        
        ApiKey publicKey = new ApiKey();
        publicKey.setKey("public-key-1");
        publicKey.setKeyType(ApiKey.ApiKeyType.PUBLIC);
        publicKey.setRevoked(false);
        publicKey.setCreationDate(new Date());
        apiKeys.add(publicKey);
        
        tenant.setApiKeys(apiKeys);
        
        assertNull("Private API key should be null when only public keys exist", tenant.getPrivateApiKey());
    }

    @Test
    public void testGetPrivateApiKeyWithRevokedKeys() {
        Tenant tenant = new Tenant();
        List<ApiKey> apiKeys = new ArrayList<>();
        
        ApiKey revokedKey = new ApiKey();
        revokedKey.setKey("private-key-1");
        revokedKey.setKeyType(ApiKey.ApiKeyType.PRIVATE);
        revokedKey.setRevoked(true);
        revokedKey.setCreationDate(new Date());
        apiKeys.add(revokedKey);
        
        tenant.setApiKeys(apiKeys);
        
        assertNull("Private API key should be null when all private keys are revoked", tenant.getPrivateApiKey());
    }

    @Test
    public void testGetPrivateApiKeyWithExpiredKeys() {
        Tenant tenant = new Tenant();
        List<ApiKey> apiKeys = new ArrayList<>();
        
        ApiKey expiredKey = new ApiKey();
        expiredKey.setKey("private-key-1");
        expiredKey.setKeyType(ApiKey.ApiKeyType.PRIVATE);
        expiredKey.setRevoked(false);
        expiredKey.setExpirationDate(new Date(System.currentTimeMillis() - 1000)); // Expired
        expiredKey.setCreationDate(new Date());
        apiKeys.add(expiredKey);
        
        tenant.setApiKeys(apiKeys);
        
        assertNull("Private API key should be null when all private keys are expired", tenant.getPrivateApiKey());
    }

    @Test
    public void testGetPrivateApiKeyWithValidKey() {
        Tenant tenant = new Tenant();
        List<ApiKey> apiKeys = new ArrayList<>();
        
        ApiKey validKey = new ApiKey();
        validKey.setKey("private-key-1");
        validKey.setKeyType(ApiKey.ApiKeyType.PRIVATE);
        validKey.setRevoked(false);
        validKey.setCreationDate(new Date());
        apiKeys.add(validKey);
        
        tenant.setApiKeys(apiKeys);
        
        assertEquals("Private API key should be resolved from API keys", "private-key-1", tenant.getPrivateApiKey());
    }

    @Test
    public void testGetPrivateApiKeyWithMultipleKeysReturnsLatest() {
        Tenant tenant = new Tenant();
        List<ApiKey> apiKeys = new ArrayList<>();
        
        Date oldDate = new Date(System.currentTimeMillis() - 10000);
        Date newDate = new Date();
        
        ApiKey oldKey = new ApiKey();
        oldKey.setKey("private-key-old");
        oldKey.setKeyType(ApiKey.ApiKeyType.PRIVATE);
        oldKey.setRevoked(false);
        oldKey.setCreationDate(oldDate);
        apiKeys.add(oldKey);
        
        ApiKey newKey = new ApiKey();
        newKey.setKey("private-key-new");
        newKey.setKeyType(ApiKey.ApiKeyType.PRIVATE);
        newKey.setRevoked(false);
        newKey.setCreationDate(newDate);
        apiKeys.add(newKey);
        
        tenant.setApiKeys(apiKeys);
        
        assertEquals("Private API key should return the most recently created key", "private-key-new", tenant.getPrivateApiKey());
    }

    @Test
    public void testGetPrivateApiKeyAlwaysResolvesFromApiKeys() {
        Tenant tenant = new Tenant();
        List<ApiKey> apiKeys = new ArrayList<>();
        
        ApiKey validKey = new ApiKey();
        validKey.setKey("private-key-1");
        validKey.setKeyType(ApiKey.ApiKeyType.PRIVATE);
        validKey.setRevoked(false);
        validKey.setCreationDate(new Date());
        apiKeys.add(validKey);
        
        tenant.setApiKeys(apiKeys);
        
        // First call should resolve
        String firstCall = tenant.getPrivateApiKey();
        assertEquals("First call should return correct key", "private-key-1", firstCall);
        
        // Modify the API key to be revoked
        validKey.setRevoked(true);
        
        // Second call should return null since key is now revoked
        String secondCall = tenant.getPrivateApiKey();
        assertNull("Second call should return null after key is revoked", secondCall);
        
        // Reactivate the key
        validKey.setRevoked(false);
        
        // Third call should return the key again
        String thirdCall = tenant.getPrivateApiKey();
        assertEquals("Third call should return key again after reactivation", "private-key-1", thirdCall);
    }

    @Test
    public void testGetPublicApiKeyWithNoApiKeys() {
        Tenant tenant = new Tenant();
        tenant.setApiKeys(null);
        
        assertNull("Public API key should be null when no API keys exist", tenant.getPublicApiKey());
    }

    @Test
    public void testGetPublicApiKeyWithEmptyApiKeys() {
        Tenant tenant = new Tenant();
        tenant.setApiKeys(new ArrayList<>());
        
        assertNull("Public API key should be null when API keys list is empty", tenant.getPublicApiKey());
    }

    @Test
    public void testGetPublicApiKeyWithOnlyPrivateKeys() {
        Tenant tenant = new Tenant();
        List<ApiKey> apiKeys = new ArrayList<>();
        
        ApiKey privateKey = new ApiKey();
        privateKey.setKey("private-key-1");
        privateKey.setKeyType(ApiKey.ApiKeyType.PRIVATE);
        privateKey.setRevoked(false);
        privateKey.setCreationDate(new Date());
        apiKeys.add(privateKey);
        
        tenant.setApiKeys(apiKeys);
        
        assertNull("Public API key should be null when only private keys exist", tenant.getPublicApiKey());
    }

    @Test
    public void testGetPublicApiKeyWithValidKey() {
        Tenant tenant = new Tenant();
        List<ApiKey> apiKeys = new ArrayList<>();
        
        ApiKey validKey = new ApiKey();
        validKey.setKey("public-key-1");
        validKey.setKeyType(ApiKey.ApiKeyType.PUBLIC);
        validKey.setRevoked(false);
        validKey.setCreationDate(new Date());
        apiKeys.add(validKey);
        
        tenant.setApiKeys(apiKeys);
        
        assertEquals("Public API key should be resolved from API keys", "public-key-1", tenant.getPublicApiKey());
    }

    @Test
    public void testGetPublicApiKeyAlwaysResolvesFromApiKeys() {
        Tenant tenant = new Tenant();
        List<ApiKey> apiKeys = new ArrayList<>();
        
        ApiKey validKey = new ApiKey();
        validKey.setKey("public-key-1");
        validKey.setKeyType(ApiKey.ApiKeyType.PUBLIC);
        validKey.setRevoked(false);
        validKey.setCreationDate(new Date());
        apiKeys.add(validKey);
        
        tenant.setApiKeys(apiKeys);
        
        // First call should resolve
        String firstCall = tenant.getPublicApiKey();
        assertEquals("First call should return correct key", "public-key-1", firstCall);
        
        // Modify the API key to be revoked
        validKey.setRevoked(true);
        
        // Second call should return null since key is now revoked
        String secondCall = tenant.getPublicApiKey();
        assertNull("Second call should return null after key is revoked", secondCall);
        
        // Reactivate the key
        validKey.setRevoked(false);
        
        // Third call should return the key again
        String thirdCall = tenant.getPublicApiKey();
        assertEquals("Third call should return key again after reactivation", "public-key-1", thirdCall);
    }

    @Test
    public void testGetActivePrivateApiKeys() {
        Tenant tenant = new Tenant();
        List<ApiKey> apiKeys = new ArrayList<>();
        
        // Add various private keys
        ApiKey revokedKey = new ApiKey();
        revokedKey.setKey("revoked-private");
        revokedKey.setKeyType(ApiKey.ApiKeyType.PRIVATE);
        revokedKey.setRevoked(true);
        apiKeys.add(revokedKey);
        
        ApiKey expiredKey = new ApiKey();
        expiredKey.setKey("expired-private");
        expiredKey.setKeyType(ApiKey.ApiKeyType.PRIVATE);
        expiredKey.setRevoked(false);
        expiredKey.setExpirationDate(new Date(System.currentTimeMillis() - 1000));
        apiKeys.add(expiredKey);
        
        ApiKey validKey1 = new ApiKey();
        validKey1.setKey("valid-private-1");
        validKey1.setKeyType(ApiKey.ApiKeyType.PRIVATE);
        validKey1.setRevoked(false);
        apiKeys.add(validKey1);
        
        ApiKey validKey2 = new ApiKey();
        validKey2.setKey("valid-private-2");
        validKey2.setKeyType(ApiKey.ApiKeyType.PRIVATE);
        validKey2.setRevoked(false);
        apiKeys.add(validKey2);
        
        tenant.setApiKeys(apiKeys);
        
        List<ApiKey> activeKeys = tenant.getActivePrivateApiKeys();
        assertEquals("Should return 2 active private keys", 2, activeKeys.size());
        assertTrue("Should contain valid-private-1", activeKeys.stream().anyMatch(key -> "valid-private-1".equals(key.getKey())));
        assertTrue("Should contain valid-private-2", activeKeys.stream().anyMatch(key -> "valid-private-2".equals(key.getKey())));
    }

    @Test
    public void testGetActivePublicApiKeys() {
        Tenant tenant = new Tenant();
        List<ApiKey> apiKeys = new ArrayList<>();
        
        // Add various public keys
        ApiKey revokedKey = new ApiKey();
        revokedKey.setKey("revoked-public");
        revokedKey.setKeyType(ApiKey.ApiKeyType.PUBLIC);
        revokedKey.setRevoked(true);
        apiKeys.add(revokedKey);
        
        ApiKey expiredKey = new ApiKey();
        expiredKey.setKey("expired-public");
        expiredKey.setKeyType(ApiKey.ApiKeyType.PUBLIC);
        expiredKey.setRevoked(false);
        expiredKey.setExpirationDate(new Date(System.currentTimeMillis() - 1000));
        apiKeys.add(expiredKey);
        
        ApiKey validKey1 = new ApiKey();
        validKey1.setKey("valid-public-1");
        validKey1.setKeyType(ApiKey.ApiKeyType.PUBLIC);
        validKey1.setRevoked(false);
        apiKeys.add(validKey1);
        
        ApiKey validKey2 = new ApiKey();
        validKey2.setKey("valid-public-2");
        validKey2.setKeyType(ApiKey.ApiKeyType.PUBLIC);
        validKey2.setRevoked(false);
        apiKeys.add(validKey2);
        
        tenant.setApiKeys(apiKeys);
        
        List<ApiKey> activeKeys = tenant.getActivePublicApiKeys();
        assertEquals("Should return 2 active public keys", 2, activeKeys.size());
        assertTrue("Should contain valid-public-1", activeKeys.stream().anyMatch(key -> "valid-public-1".equals(key.getKey())));
        assertTrue("Should contain valid-public-2", activeKeys.stream().anyMatch(key -> "valid-public-2".equals(key.getKey())));
    }

    @Test
    public void testGetActiveApiKeys() {
        Tenant tenant = new Tenant();
        List<ApiKey> apiKeys = new ArrayList<>();
        
        // Add various keys
        ApiKey revokedPrivateKey = new ApiKey();
        revokedPrivateKey.setKey("revoked-private");
        revokedPrivateKey.setKeyType(ApiKey.ApiKeyType.PRIVATE);
        revokedPrivateKey.setRevoked(true);
        apiKeys.add(revokedPrivateKey);
        
        ApiKey expiredPublicKey = new ApiKey();
        expiredPublicKey.setKey("expired-public");
        expiredPublicKey.setKeyType(ApiKey.ApiKeyType.PUBLIC);
        expiredPublicKey.setRevoked(false);
        expiredPublicKey.setExpirationDate(new Date(System.currentTimeMillis() - 1000));
        apiKeys.add(expiredPublicKey);
        
        ApiKey validPrivateKey = new ApiKey();
        validPrivateKey.setKey("valid-private");
        validPrivateKey.setKeyType(ApiKey.ApiKeyType.PRIVATE);
        validPrivateKey.setRevoked(false);
        apiKeys.add(validPrivateKey);
        
        ApiKey validPublicKey = new ApiKey();
        validPublicKey.setKey("valid-public");
        validPublicKey.setKeyType(ApiKey.ApiKeyType.PUBLIC);
        validPublicKey.setRevoked(false);
        apiKeys.add(validPublicKey);
        
        tenant.setApiKeys(apiKeys);
        
        List<ApiKey> activeKeys = tenant.getActiveApiKeys();
        assertEquals("Should return 2 active keys", 2, activeKeys.size());
        assertTrue("Should contain valid-private", activeKeys.stream().anyMatch(key -> "valid-private".equals(key.getKey())));
        assertTrue("Should contain valid-public", activeKeys.stream().anyMatch(key -> "valid-public".equals(key.getKey())));
    }

    @Test
    public void testGetActiveApiKeysWithNullApiKeys() {
        Tenant tenant = new Tenant();
        tenant.setApiKeys(null);
        
        List<ApiKey> activeKeys = tenant.getActiveApiKeys();
        assertNotNull("Should return empty list, not null", activeKeys);
        assertTrue("Should return empty list", activeKeys.isEmpty());
    }

    @Test
    public void testGetActivePrivateApiKeysWithNullApiKeys() {
        Tenant tenant = new Tenant();
        tenant.setApiKeys(null);
        
        List<ApiKey> activeKeys = tenant.getActivePrivateApiKeys();
        assertNotNull("Should return empty list, not null", activeKeys);
        assertTrue("Should return empty list", activeKeys.isEmpty());
    }

    @Test
    public void testGetActivePublicApiKeysWithNullApiKeys() {
        Tenant tenant = new Tenant();
        tenant.setApiKeys(null);
        
        List<ApiKey> activeKeys = tenant.getActivePublicApiKeys();
        assertNotNull("Should return empty list, not null", activeKeys);
        assertTrue("Should return empty list", activeKeys.isEmpty());
    }

    @Test
    public void testMixedApiKeyTypes() {
        Tenant tenant = new Tenant();
        List<ApiKey> apiKeys = new ArrayList<>();
        
        ApiKey privateKey = new ApiKey();
        privateKey.setKey("private-key");
        privateKey.setKeyType(ApiKey.ApiKeyType.PRIVATE);
        privateKey.setRevoked(false);
        privateKey.setCreationDate(new Date());
        apiKeys.add(privateKey);
        
        ApiKey publicKey = new ApiKey();
        publicKey.setKey("public-key");
        publicKey.setKeyType(ApiKey.ApiKeyType.PUBLIC);
        publicKey.setRevoked(false);
        publicKey.setCreationDate(new Date());
        apiKeys.add(publicKey);
        
        tenant.setApiKeys(apiKeys);
        
        assertEquals("Private API key should be resolved correctly", "private-key", tenant.getPrivateApiKey());
        assertEquals("Public API key should be resolved correctly", "public-key", tenant.getPublicApiKey());
    }

    @Test
    public void testApiKeyExpirationLogic() {
        Tenant tenant = new Tenant();
        List<ApiKey> apiKeys = new ArrayList<>();
        
        // Create a key that expires in the future
        ApiKey futureExpiringKey = new ApiKey();
        futureExpiringKey.setKey("future-expiring-key");
        futureExpiringKey.setKeyType(ApiKey.ApiKeyType.PRIVATE);
        futureExpiringKey.setRevoked(false);
        futureExpiringKey.setExpirationDate(new Date(System.currentTimeMillis() + 10000)); // 10 seconds in future
        futureExpiringKey.setCreationDate(new Date());
        apiKeys.add(futureExpiringKey);
        
        // Create a key that has already expired
        ApiKey expiredKey = new ApiKey();
        expiredKey.setKey("expired-key");
        expiredKey.setKeyType(ApiKey.ApiKeyType.PRIVATE);
        expiredKey.setRevoked(false);
        expiredKey.setExpirationDate(new Date(System.currentTimeMillis() - 1000)); // 1 second ago
        expiredKey.setCreationDate(new Date());
        apiKeys.add(expiredKey);
        
        tenant.setApiKeys(apiKeys);
        
        assertEquals("Should return the valid future-expiring key", "future-expiring-key", tenant.getPrivateApiKey());
    }

    @Test
    public void testComplexApiKeyScenarios() {
        Tenant tenant = new Tenant();
        List<ApiKey> apiKeys = new ArrayList<>();
        
        // Add various types of keys
        ApiKey revokedPrivateKey = new ApiKey();
        revokedPrivateKey.setKey("revoked-private");
        revokedPrivateKey.setKeyType(ApiKey.ApiKeyType.PRIVATE);
        revokedPrivateKey.setRevoked(true);
        revokedPrivateKey.setCreationDate(new Date(System.currentTimeMillis() - 5000));
        apiKeys.add(revokedPrivateKey);
        
        ApiKey expiredPrivateKey = new ApiKey();
        expiredPrivateKey.setKey("expired-private");
        expiredPrivateKey.setKeyType(ApiKey.ApiKeyType.PRIVATE);
        expiredPrivateKey.setRevoked(false);
        expiredPrivateKey.setExpirationDate(new Date(System.currentTimeMillis() - 1000));
        expiredPrivateKey.setCreationDate(new Date(System.currentTimeMillis() - 3000));
        apiKeys.add(expiredPrivateKey);
        
        ApiKey validPrivateKey = new ApiKey();
        validPrivateKey.setKey("valid-private");
        validPrivateKey.setKeyType(ApiKey.ApiKeyType.PRIVATE);
        validPrivateKey.setRevoked(false);
        validPrivateKey.setCreationDate(new Date());
        apiKeys.add(validPrivateKey);
        
        ApiKey validPublicKey = new ApiKey();
        validPublicKey.setKey("valid-public");
        validPublicKey.setKeyType(ApiKey.ApiKeyType.PUBLIC);
        validPublicKey.setRevoked(false);
        validPublicKey.setCreationDate(new Date());
        apiKeys.add(validPublicKey);
        
        tenant.setApiKeys(apiKeys);
        
        assertEquals("Should return the valid private key", "valid-private", tenant.getPrivateApiKey());
        assertEquals("Should return the valid public key", "valid-public", tenant.getPublicApiKey());
    }

    @Test
    public void testApiKeyCreationDateOrdering() {
        Tenant tenant = new Tenant();
        List<ApiKey> apiKeys = new ArrayList<>();
        
        Date oldDate = new Date(System.currentTimeMillis() - 10000);
        Date newDate = new Date();
        
        // Create an older valid key
        ApiKey oldKey = new ApiKey();
        oldKey.setKey("old-key");
        oldKey.setKeyType(ApiKey.ApiKeyType.PRIVATE);
        oldKey.setRevoked(false);
        oldKey.setCreationDate(oldDate);
        apiKeys.add(oldKey);
        
        // Create a newer valid key
        ApiKey newKey = new ApiKey();
        newKey.setKey("new-key");
        newKey.setKeyType(ApiKey.ApiKeyType.PRIVATE);
        newKey.setRevoked(false);
        newKey.setCreationDate(newDate);
        apiKeys.add(newKey);
        
        tenant.setApiKeys(apiKeys);
        
        assertEquals("Should return the most recently created key", "new-key", tenant.getPrivateApiKey());
    }
} 