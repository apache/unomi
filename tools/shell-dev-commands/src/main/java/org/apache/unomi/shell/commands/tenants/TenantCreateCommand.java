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
package org.apache.unomi.shell.commands.tenants;

import org.apache.karaf.shell.api.action.*;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.unomi.api.tenants.ApiKey;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.shell.completers.TenantStatusCompleter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Command(scope = "unomi", name = "tenant-create", description = "Create a new tenant")
@Service
public class TenantCreateCommand implements Action {

    private static final long MILLISECONDS_PER_DAY = 24L * 60L * 60L * 1000L;

    @Reference
    private TenantService tenantService;

    @Argument(index = 0, name = "id", description = "Tenant ID", required = true)
    String id;

    @Argument(index = 1, name = "name", description = "Tenant name", required = true)
    String name;

    @Option(name = "--description", description = "Tenant description")
    String description;

    @Option(name = "--status", description = "Initial tenant status (ACTIVE, DISABLED, SUSPENDED)")
    @Completion(TenantStatusCompleter.class)
    String status = "ACTIVE";

    @Option(name = "--key-validity", description = "Validity period for API keys in days (0 for no expiration)")
    Integer keyValidityDays;

    @Override
    public Object execute() throws Exception {
        Map<String, Object> properties = new HashMap<>();
        properties.put("name", name);
        if (description != null) {
            properties.put("description", description);
        }
        if (status != null) {
            properties.put("status", status);
        }

        // Convert days to milliseconds for API key validity
        Long keyValidityPeriod = null;
        if (keyValidityDays != null && keyValidityDays > 0) {
            keyValidityPeriod = keyValidityDays * MILLISECONDS_PER_DAY;
        }

        Tenant tenant = tenantService.createTenant(id, properties);
        if (tenant != null) {
            // Generate both API keys
            ApiKey publicKey = tenantService.generateApiKeyWithType(tenant.getItemId(), ApiKey.ApiKeyType.PUBLIC, keyValidityPeriod);
            ApiKey privateKey = tenantService.generateApiKeyWithType(tenant.getItemId(), ApiKey.ApiKeyType.PRIVATE, keyValidityPeriod);

            // Store API keys in tenant
            List<ApiKey> apiKeys = new ArrayList<>();
            apiKeys.add(publicKey);
            apiKeys.add(privateKey);
            tenant.setApiKeys(apiKeys);

            // Set active API keys
            tenant.setPublicApiKey(publicKey.getKey());
            tenant.setPrivateApiKey(privateKey.getKey());

            // Save tenant with API keys
            tenantService.saveTenant(tenant);

            System.out.println("Tenant created successfully.");
            System.out.println("ID: " + tenant.getItemId());
            System.out.println("Name: " + tenant.getName());
            System.out.println("Description: " + tenant.getDescription());
            System.out.println("Status: " + tenant.getStatus());
            System.out.println("Public API Key: " + publicKey.getKey());
            System.out.println("Private API Key: " + privateKey.getKey());
            if (keyValidityDays != null && keyValidityDays > 0) {
                System.out.println("API Keys validity: " + keyValidityDays + " days");
            } else {
                System.out.println("API Keys validity: No expiration");
            }
        } else {
            System.err.println("Failed to create tenant.");
        }
        return null;
    }
}
