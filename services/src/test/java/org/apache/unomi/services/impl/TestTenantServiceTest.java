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
package org.apache.unomi.services.impl;

import org.apache.unomi.api.tenants.ApiKey;
import org.apache.unomi.api.tenants.Tenant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;

/**
 * Test for the TestTenantService to verify it works correctly with API key functionality.
 */
public class TestTenantServiceTest {

    @Test
    public void testCreateTenantWithApiKeys() {
        TestTenantService tenantService = new TestTenantService();
        
        // Create a tenant
        Tenant tenant = tenantService.createTenant("test-tenant", Collections.emptyMap());
        
        // Verify tenant was created
        assertNotNull(tenant, "Tenant should not be null");
        assertEquals("test-tenant", tenant.getItemId(), "Tenant ID should match");
        
        // Verify API keys were generated
        assertNotNull(tenant.getApiKeys(), "API keys should not be null");
        assertEquals(2, tenant.getApiKeys().size(), "Should have 2 API keys (public and private)");
        
        // Verify both public and private keys exist
        List<ApiKey> publicKeys = tenant.getActivePublicApiKeys();
        List<ApiKey> privateKeys = tenant.getActivePrivateApiKeys();
        
        assertEquals(1, publicKeys.size(), "Should have 1 public key");
        assertEquals(1, privateKeys.size(), "Should have 1 private key");
        
        // Verify getters work
        assertNotNull(tenant.getPublicApiKey(), "Public API key should be accessible");
        assertNotNull(tenant.getPrivateApiKey(), "Private API key should be accessible");
    }

    @Test
    public void testGenerateApiKeyWithType() {
        TestTenantService tenantService = new TestTenantService();
        
        // Create a tenant first
        Tenant tenant = tenantService.createTenant("test-tenant", Collections.emptyMap());
        
        // Generate a new public API key
        ApiKey newPublicKey = tenantService.generateApiKeyWithType("test-tenant", ApiKey.ApiKeyType.PUBLIC, null);
        
        // Verify the new key was generated
        assertNotNull(newPublicKey, "New API key should not be null");
        assertEquals(ApiKey.ApiKeyType.PUBLIC, newPublicKey.getKeyType(), "Key type should be PUBLIC");
        assertNotNull(newPublicKey.getKey(), "Key value should not be null");
        
        // Reload tenant and verify the new key is there
        Tenant reloadedTenant = tenantService.getTenant("test-tenant");
        assertEquals(1, reloadedTenant.getActivePublicApiKeys().size(), "Should still have 1 public key (replaced the old one)");
        assertEquals(1, reloadedTenant.getActivePrivateApiKeys().size(), "Should have 1 private key");
    }

    @Test
    public void testValidateApiKey() {
        TestTenantService tenantService = new TestTenantService();
        
        // Create a tenant
        Tenant tenant = tenantService.createTenant("test-tenant", Collections.emptyMap());
        String publicKey = tenant.getPublicApiKey();
        String privateKey = tenant.getPrivateApiKey();
        
        // Verify API key validation works
        assertTrue(tenantService.validateApiKey("test-tenant", publicKey), "Public API key should be valid");
        assertTrue(tenantService.validateApiKey("test-tenant", privateKey), "Private API key should be valid");
        assertFalse(tenantService.validateApiKey("test-tenant", "invalid-key"), "Invalid API key should not be valid");
        assertFalse(tenantService.validateApiKey("non-existent", publicKey), "Non-existent tenant should not be valid");
    }

    @Test
    public void testValidateApiKeyWithType() {
        TestTenantService tenantService = new TestTenantService();
        
        // Create a tenant
        Tenant tenant = tenantService.createTenant("test-tenant", Collections.emptyMap());
        String publicKey = tenant.getPublicApiKey();
        String privateKey = tenant.getPrivateApiKey();
        
        // Verify type-specific validation works
        assertTrue(tenantService.validateApiKeyWithType("test-tenant", publicKey, ApiKey.ApiKeyType.PUBLIC), 
            "Public API key should be valid for PUBLIC type");
        assertTrue(tenantService.validateApiKeyWithType("test-tenant", privateKey, ApiKey.ApiKeyType.PRIVATE), 
            "Private API key should be valid for PRIVATE type");
        assertFalse(tenantService.validateApiKeyWithType("test-tenant", publicKey, ApiKey.ApiKeyType.PRIVATE), 
            "Public API key should not be valid for PRIVATE type");
        assertFalse(tenantService.validateApiKeyWithType("test-tenant", privateKey, ApiKey.ApiKeyType.PUBLIC), 
            "Private API key should not be valid for PUBLIC type");
    }

    @Test
    public void testGetTenantByApiKey() {
        TestTenantService tenantService = new TestTenantService();
        
        // Create a tenant
        Tenant tenant = tenantService.createTenant("test-tenant", Collections.emptyMap());
        String publicKey = tenant.getPublicApiKey();
        
        // Verify tenant lookup by API key works
        Tenant foundTenant = tenantService.getTenantByApiKey(publicKey);
        assertNotNull(foundTenant, "Should find tenant by API key");
        assertEquals("test-tenant", foundTenant.getItemId(), "Found tenant should match");
        
        // Verify type-specific lookup works
        Tenant foundTenantByType = tenantService.getTenantByApiKey(publicKey, ApiKey.ApiKeyType.PUBLIC);
        assertNotNull(foundTenantByType, "Should find tenant by API key and type");
        assertEquals("test-tenant", foundTenantByType.getItemId(), "Found tenant should match");
        
        // Verify non-existent key returns null
        assertNull(tenantService.getTenantByApiKey("invalid-key"), "Non-existent key should return null");
    }

    @Test
    public void testGetApiKey() {
        TestTenantService tenantService = new TestTenantService();
        
        // Create a tenant
        tenantService.createTenant("test-tenant", Collections.emptyMap());
        
        // Verify API key retrieval works
        ApiKey publicKey = tenantService.getApiKey("test-tenant", ApiKey.ApiKeyType.PUBLIC);
        ApiKey privateKey = tenantService.getApiKey("test-tenant", ApiKey.ApiKeyType.PRIVATE);
        
        assertNotNull(publicKey, "Should retrieve public API key");
        assertNotNull(privateKey, "Should retrieve private API key");
        assertEquals(ApiKey.ApiKeyType.PUBLIC, publicKey.getKeyType(), "Public key type should be PUBLIC");
        assertEquals(ApiKey.ApiKeyType.PRIVATE, privateKey.getKeyType(), "Private key type should be PRIVATE");
        
        // Verify non-existent tenant returns null
        assertNull(
            tenantService.getApiKey("non-existent", ApiKey.ApiKeyType.PUBLIC),
            "Non-existent tenant should return null");
    }
} 