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
package org.apache.unomi.itests.security;

import org.apache.http.HttpResponse;
import org.apache.unomi.api.tenants.Tenant;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

/**
 * Integration tests for tenant security features.
 * Note: IP filtering and rate limiting are handled by Apache CXF.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class TenantSecurityIT extends BaseIT {

    @Test
    public void testJWTAuthentication() throws Exception {
        // Create tenants with JWT config
        Tenant tenant = createTenantWithJWTConfig();

        // Generate valid JWT
        String validToken = generateValidJWT(tenant);

        // Test with valid token
        HttpResponse response = executeRequest(validToken, tenant.getItemId());
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        // Test with expired token
        String expiredToken = generateExpiredJWT(tenant);
        response = executeRequest(expiredToken, tenant.getItemId());
        Assert.assertEquals(401, response.getStatusLine().getStatusCode());

        // Test with invalid issuer
        String invalidIssuerToken = generateJWTWithInvalidIssuer(tenant);
        response = executeRequest(invalidIssuerToken, tenant.getItemId());
        Assert.assertEquals(401, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testOAuth2Authentication() throws Exception {
        // Create tenants with OAuth2 config
        Tenant tenant = createTenantWithOAuth2Config();

        // Test with valid OAuth2 token
        String validToken = getValidOAuth2Token(tenant);
        HttpResponse response = executeRequest(validToken, tenant.getItemId());
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        // Test with invalid OAuth2 token
        response = executeRequest("invalid-token", tenant.getItemId());
        Assert.assertEquals(401, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testTenantIsolation() throws Exception {
        // Create two tenants
        Tenant tenant1 = createTenant("tenant1");
        Tenant tenant2 = createTenant("tenant2");

        // Save items for tenant1
        Item item1 = createItem("item1", tenant1.getId());
        persistenceService.save(item1);

        // Try to access tenant1's item from tenant2
        setCurrentTenant(tenant2);
        Assert.assertNull(persistenceService.load(item1.getItemId(), Item.class));
    }

    @Test
    public void testTenantEncryption() throws Exception {
        Tenant tenant = createTenant("encrypted_tenant");
        enableTenantEncryption(tenant.getId());

        // Save sensitive data
        Item sensitiveItem = createSensitiveItem(tenant.getId());
        persistenceService.save(sensitiveItem);

        // Verify data is encrypted in Elasticsearch
        verifyEncryptedStorage(sensitiveItem);
    }

    @Test
    public void testBulkOperationSecurity() throws Exception {
        Tenant tenant = createTenant("bulk_tenant");

        // Try bulk operation with mixed tenants data
        List<Item> items = createMixedTenantItems();
        try {
            persistenceService.saveItems(items);
            fail("Should not allow bulk operation with mixed tenants data");
        } catch (SecurityException e) {
            // Expected
        }
    }
}
