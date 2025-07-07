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
import org.junit.Test;
import static org.junit.Assert.*;

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
        assertNotNull("Tenant should not be null", tenant);
        assertEquals("Tenant ID should match", "test-tenant", tenant.getItemId());
        
        // Verify API keys were generated
        assertNotNull("API keys should not be null", tenant.getApiKeys());
        assertEquals("Should have 2 API keys (public and private)", 2, tenant.getApiKeys().size());
        
        // Verify both public and private keys exist
        List<ApiKey> publicKeys = tenant.getActivePublicApiKeys();
        List<ApiKey> privateKeys = tenant.getActivePrivateApiKeys();
        
        assertEquals("Should have 1 public key", 1, publicKeys.size());
        assertEquals("Should have 1 private key", 1, privateKeys.size());
        
        // Verify getters work
        assertNotNull("Public API key should be accessible", tenant.getPublicApiKey());
        assertNotNull("Private API key should be accessible", tenant.getPrivateApiKey());
    }

    @Test
    public void testGenerateApiKeyWithType() {
        TestTenantService tenantService = new TestTenantService();
        
        // Create a tenant first
        Tenant tenant = tenantService.createTenant("test-tenant", Collections.emptyMap());
        
        // Generate a new public API key
        ApiKey newPublicKey = tenantService.generateApiKeyWithType("test-tenant", ApiKey.ApiKeyType.PUBLIC, null);
        
        // Verify the new key was generated
        assertNotNull("New API key should not be null", newPublicKey);
        assertEquals("Key type should be PUBLIC", ApiKey.ApiKeyType.PUBLIC, newPublicKey.getKeyType());
        assertNotNull("Key value should not be null", newPublicKey.getKey());
        
        // Reload tenant and verify the new key is there
        Tenant reloadedTenant = tenantService.getTenant("test-tenant");
        assertEquals("Should still have 1 public key (replaced the old one)", 1, reloadedTenant.getActivePublicApiKeys().size());
        assertEquals("Should have 1 private key", 1, reloadedTenant.getActivePrivateApiKeys().size());
    }

    @Test
    public void testValidateApiKey() {
        TestTenantService tenantService = new TestTenantService();
        
        // Create a tenant
        Tenant tenant = tenantService.createTenant("test-tenant", Collections.emptyMap());
        String publicKey = tenant.getPublicApiKey();
        String privateKey = tenant.getPrivateApiKey();
        
        // Verify API key validation works
        assertTrue("Public API key should be valid", tenantService.validateApiKey("test-tenant", publicKey));
        assertTrue("Private API key should be valid", tenantService.validateApiKey("test-tenant", privateKey));
        assertFalse("Invalid API key should not be valid", tenantService.validateApiKey("test-tenant", "invalid-key"));
        assertFalse("Non-existent tenant should not be valid", tenantService.validateApiKey("non-existent", publicKey));
    }

    @Test
    public void testValidateApiKeyWithType() {
        TestTenantService tenantService = new TestTenantService();
        
        // Create a tenant
        Tenant tenant = tenantService.createTenant("test-tenant", Collections.emptyMap());
        String publicKey = tenant.getPublicApiKey();
        String privateKey = tenant.getPrivateApiKey();
        
        // Verify type-specific validation works
        assertTrue("Public API key should be valid for PUBLIC type", 
            tenantService.validateApiKeyWithType("test-tenant", publicKey, ApiKey.ApiKeyType.PUBLIC));
        assertTrue("Private API key should be valid for PRIVATE type", 
            tenantService.validateApiKeyWithType("test-tenant", privateKey, ApiKey.ApiKeyType.PRIVATE));
        assertFalse("Public API key should not be valid for PRIVATE type", 
            tenantService.validateApiKeyWithType("test-tenant", publicKey, ApiKey.ApiKeyType.PRIVATE));
        assertFalse("Private API key should not be valid for PUBLIC type", 
            tenantService.validateApiKeyWithType("test-tenant", privateKey, ApiKey.ApiKeyType.PUBLIC));
    }

    @Test
    public void testGetTenantByApiKey() {
        TestTenantService tenantService = new TestTenantService();
        
        // Create a tenant
        Tenant tenant = tenantService.createTenant("test-tenant", Collections.emptyMap());
        String publicKey = tenant.getPublicApiKey();
        
        // Verify tenant lookup by API key works
        Tenant foundTenant = tenantService.getTenantByApiKey(publicKey);
        assertNotNull("Should find tenant by API key", foundTenant);
        assertEquals("Found tenant should match", "test-tenant", foundTenant.getItemId());
        
        // Verify type-specific lookup works
        Tenant foundTenantByType = tenantService.getTenantByApiKey(publicKey, ApiKey.ApiKeyType.PUBLIC);
        assertNotNull("Should find tenant by API key and type", foundTenantByType);
        assertEquals("Found tenant should match", "test-tenant", foundTenantByType.getItemId());
        
        // Verify non-existent key returns null
        assertNull("Non-existent key should return null", tenantService.getTenantByApiKey("invalid-key"));
    }

    @Test
    public void testGetApiKey() {
        TestTenantService tenantService = new TestTenantService();
        
        // Create a tenant
        tenantService.createTenant("test-tenant", Collections.emptyMap());
        
        // Verify API key retrieval works
        ApiKey publicKey = tenantService.getApiKey("test-tenant", ApiKey.ApiKeyType.PUBLIC);
        ApiKey privateKey = tenantService.getApiKey("test-tenant", ApiKey.ApiKeyType.PRIVATE);
        
        assertNotNull("Should retrieve public API key", publicKey);
        assertNotNull("Should retrieve private API key", privateKey);
        assertEquals("Public key type should be PUBLIC", ApiKey.ApiKeyType.PUBLIC, publicKey.getKeyType());
        assertEquals("Private key type should be PRIVATE", ApiKey.ApiKeyType.PRIVATE, privateKey.getKeyType());
        
        // Verify non-existent tenant returns null
        assertNull("Non-existent tenant should return null", 
            tenantService.getApiKey("non-existent", ApiKey.ApiKeyType.PUBLIC));
    }
} 