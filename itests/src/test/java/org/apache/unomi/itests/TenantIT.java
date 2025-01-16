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

import org.apache.unomi.api.Profile;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.tenants.ApiKey;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class TenantIT extends BaseIT {

    @Inject
    private TenantService tenantService;

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
}
