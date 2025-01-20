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
import org.apache.unomi.api.tenants.*;
import org.apache.unomi.shell.completers.EventTypeCompleter;
import org.apache.unomi.shell.completers.IPAddressCompleter;
import org.apache.unomi.shell.completers.TenantCompleter;
import org.apache.unomi.shell.completers.TenantStatusCompleter;

import java.util.*;

@Command(scope = "tenant", name = "update", description = "Update a tenant")
@Service
public class TenantUpdateCommand implements Action {

    private static final long MILLISECONDS_PER_DAY = 24L * 60L * 60L * 1000L;

    @Reference
    private TenantService tenantService;

    @Argument(index = 0, name = "tenantId", description = "Tenant ID to update", required = true)
    @Completion(TenantCompleter.class)
    String tenantId;

    @Option(name = "--name", description = "New tenant name")
    String name;

    @Option(name = "--description", description = "New tenant description")
    String description;

    @Option(name = "--status", description = "New tenant status (ACTIVE, DISABLED, SUSPENDED)")
    @Completion(TenantStatusCompleter.class)
    String status;

    @Option(name = "--max-profiles", description = "Maximum number of profiles allowed")
    Long maxProfiles;

    @Option(name = "--max-events", description = "Maximum number of events allowed")
    Long maxEvents;

    @Option(name = "--max-requests", description = "Maximum number of requests allowed")
    Long maxRequests;

    @Option(name = "--restricted-events", description = "Comma-separated list of restricted event types")
    @Completion(EventTypeCompleter.class)
    String restrictedEvents;

    @Option(name = "--authorized-ips", description = "Comma-separated list of authorized IP addresses/ranges")
    @Completion(IPAddressCompleter.class)
    String authorizedIPs;

    @Option(name = "--generate-public-key", description = "Generate a new public API key")
    boolean generatePublicKey;

    @Option(name = "--generate-private-key", description = "Generate a new private API key")
    boolean generatePrivateKey;

    @Option(name = "--key-validity", description = "Validity period for new API keys in days (0 for no expiration)")
    Integer keyValidityDays;

    @Override
    public Object execute() throws Exception {
        Tenant tenant = tenantService.getTenant(tenantId);
        if (tenant == null) {
            System.err.println("Tenant not found.");
            return null;
        }

        boolean modified = false;

        if (name != null) {
            tenant.setName(name);
            modified = true;
        }
        if (description != null) {
            tenant.setDescription(description);
            modified = true;
        }
        if (status != null) {
            try {
                tenant.setStatus(TenantStatus.valueOf(status.toUpperCase()));
                modified = true;
            } catch (IllegalArgumentException e) {
                System.err.println("Invalid status. Valid values are: " + Arrays.toString(TenantStatus.values()));
                return null;
            }
        }

        // Update resource quota
        if (maxProfiles != null || maxEvents != null || maxRequests != null) {
            ResourceQuota quota = tenant.getResourceQuota();
            if (quota == null) {
                quota = new ResourceQuota();
            }
            if (maxProfiles != null) {
                quota.setMaxProfiles(maxProfiles);
            }
            if (maxEvents != null) {
                quota.setMaxEvents(maxEvents);
            }
            if (maxRequests != null) {
                quota.setMaxRequests(maxRequests);
            }
            tenant.setResourceQuota(quota);
            modified = true;
        }

        // Update restricted events
        if (restrictedEvents != null) {
            Set<String> eventTypes = new HashSet<>();
            for (String event : restrictedEvents.split(",")) {
                eventTypes.add(event.trim());
            }
            tenant.setRestrictedEventPermissions(eventTypes);
            modified = true;
        }

        // Update authorized IPs
        if (authorizedIPs != null) {
            Set<String> ips = new HashSet<>();
            for (String ip : authorizedIPs.split(",")) {
                ips.add(ip.trim());
            }
            tenant.setAuthorizedIPs(ips);
            modified = true;
        }

        // Convert days to milliseconds for API key validity
        Long keyValidityPeriod = null;
        if (keyValidityDays != null && keyValidityDays > 0) {
            keyValidityPeriod = keyValidityDays * MILLISECONDS_PER_DAY;
        }

        // Generate and store new API keys if requested
        List<ApiKey> apiKeys = tenant.getApiKeys();
        if (apiKeys == null) {
            apiKeys = new ArrayList<>();
        }

        if (generatePublicKey) {
            // Remove old public key if exists
            apiKeys.removeIf(key -> key.getKeyType() == ApiKey.ApiKeyType.PUBLIC);
            // Generate and add new public key
            ApiKey publicKey = tenantService.generateApiKeyWithType(tenant.getItemId(), ApiKey.ApiKeyType.PUBLIC, keyValidityPeriod);
            apiKeys.add(publicKey);
            // Set as active public key
            tenant.setPublicApiKey(publicKey.getKey());
            System.out.println("New Public API Key: " + publicKey.getKey());
            if (keyValidityDays != null && keyValidityDays > 0) {
                System.out.println("Public API Key validity: " + keyValidityDays + " days");
            } else {
                System.out.println("Public API Key validity: No expiration");
            }
            modified = true;
        }

        if (generatePrivateKey) {
            // Remove old private key if exists
            apiKeys.removeIf(key -> key.getKeyType() == ApiKey.ApiKeyType.PRIVATE);
            // Generate and add new private key
            ApiKey privateKey = tenantService.generateApiKeyWithType(tenant.getItemId(), ApiKey.ApiKeyType.PRIVATE, keyValidityPeriod);
            apiKeys.add(privateKey);
            // Set as active private key
            tenant.setPrivateApiKey(privateKey.getKey());
            System.out.println("New Private API Key: " + privateKey.getKey());
            if (keyValidityDays != null && keyValidityDays > 0) {
                System.out.println("Private API Key validity: " + keyValidityDays + " days");
            } else {
                System.out.println("Private API Key validity: No expiration");
            }
            modified = true;
        }

        if (!apiKeys.isEmpty()) {
            tenant.setApiKeys(apiKeys);
        }

        if (modified) {
            tenantService.saveTenant(tenant);
            System.out.println("Tenant updated successfully.");

            // Show updated tenant details
            System.out.println("\nUpdated Tenant Details:");
            System.out.println("ID: " + tenant.getItemId());
            System.out.println("Name: " + tenant.getName());
            System.out.println("Description: " + tenant.getDescription());
            System.out.println("Status: " + tenant.getStatus());

            ResourceQuota quota = tenant.getResourceQuota();
            if (quota != null) {
                System.out.println("Resource Quota:");
                System.out.println("  Max Profiles: " + quota.getMaxProfiles());
                System.out.println("  Max Events: " + quota.getMaxEvents());
                System.out.println("  Max Requests: " + quota.getMaxRequests());
            }

            Set<String> restrictedEventTypes = tenant.getRestrictedEventPermissions();
            if (restrictedEventTypes != null && !restrictedEventTypes.isEmpty()) {
                System.out.println("Restricted Events: " + String.join(", ", restrictedEventTypes));
            }

            Set<String> authorizedIPAddresses = tenant.getAuthorizedIPs();
            if (authorizedIPAddresses != null && !authorizedIPAddresses.isEmpty()) {
                System.out.println("Authorized IPs: " + String.join(", ", authorizedIPAddresses));
            }
        } else {
            System.out.println("No changes were made to the tenant.");
        }
        return null;
    }
}
