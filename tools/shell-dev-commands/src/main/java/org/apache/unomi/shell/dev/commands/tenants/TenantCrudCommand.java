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
package org.apache.unomi.shell.dev.commands.tenants;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.tenants.*;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.shell.dev.services.BaseCrudCommand;
import org.apache.unomi.shell.dev.services.CrudCommand;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A command to perform CRUD operations on tenants
 */
@Component(service = CrudCommand.class, immediate = true)
public class TenantCrudCommand extends BaseCrudCommand {

    private static final ObjectMapper OBJECT_MAPPER = new CustomObjectMapper();
    private static final List<String> PROPERTY_NAMES = List.of(
        "itemId", "name", "description", "status", "creationDate", "lastModificationDate", "resourceQuota", "properties", "restrictedEventPermissions", "authorizedIPs"
    );

    @Reference
    private TenantService tenantService;

    @Override
    public String getObjectType() {
        return "tenant";
    }

    @Override
    public String[] getHeaders() {
        return prependTenantIdHeader(new String[]{"ID", "Name", "Description", "Status", "Created", "Modified"});
    }

    @Override
    protected PartialList<?> getItems(Query query) {
        List<Tenant> tenants = tenantService.getAllTenants();
        // Filter out system tenant
        tenants = tenants.stream()
            .filter(tenant -> !TenantService.SYSTEM_TENANT.equals(tenant.getItemId()))
            .collect(Collectors.toList());
        return new PartialList<>(tenants, 0, tenants.size(), tenants.size(), PartialList.Relation.EQUAL);
    }

    @Override
    protected Comparable[] buildRow(Object item) {
        Tenant tenant = (Tenant) item;
        return new Comparable[]{
            tenant.getItemId(),
            tenant.getName(),
            tenant.getDescription(),
            tenant.getStatus() != null ? tenant.getStatus().toString() : "",
            tenant.getCreationDate() != null ? tenant.getCreationDate().toString() : "",
            tenant.getLastModificationDate() != null ? tenant.getLastModificationDate().toString() : ""
        };
    }

    /**
     * Special case for tenants: the tenant ID is the same as the item ID for tenant objects.
     */
    @Override
    protected String getTenantIdFromItem(Object item) {
        if (item instanceof Tenant) {
            Tenant tenant = (Tenant) item;
            return tenant.getItemId();
        }
        return super.getTenantIdFromItem(item);
    }

    @Override
    public Map<String, Object> read(String id) {
        Tenant tenant = tenantService.getTenant(id);
        if (tenant == null || TenantService.SYSTEM_TENANT.equals(tenant.getItemId())) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(tenant, Map.class);
    }

    @Override
    public String create(Map<String, Object> properties) {
        String id = (String) properties.remove("itemId");
        if (id == null) {
            return null;
        }

        try {
            // Create the tenant
            Tenant tenant = tenantService.createTenant(id, properties);

            // Generate API keys with no expiration
            ApiKey publicKey = tenantService.generateApiKeyWithType(tenant.getItemId(), ApiKey.ApiKeyType.PUBLIC, null);
            ApiKey privateKey = tenantService.generateApiKeyWithType(tenant.getItemId(), ApiKey.ApiKeyType.PRIVATE, null);

            // Save the tenant with the new API keys
            tenantService.saveTenant(tenant);

            return tenant.getItemId();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void update(String id, Map<String, Object> properties) {
        Tenant tenant = tenantService.getTenant(id);
        if (tenant == null || TenantService.SYSTEM_TENANT.equals(tenant.getItemId())) {
            return;
        }

        try {
            // Update tenant properties
            if (properties.containsKey("name")) {
                tenant.setName((String) properties.get("name"));
            }
            if (properties.containsKey("description")) {
                tenant.setDescription((String) properties.get("description"));
            }
            if (properties.containsKey("status")) {
                tenant.setStatus(Enum.valueOf(TenantStatus.class, (String) properties.get("status")));
            }
            if (properties.containsKey("resourceQuota")) {
                tenant.setResourceQuota(OBJECT_MAPPER.convertValue(properties.get("resourceQuota"), ResourceQuota.class));
            }
            if (properties.containsKey("properties")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> props = (Map<String, Object>) properties.get("properties");
                tenant.setProperties(props);
            }
            if (properties.containsKey("restrictedEventPermissions")) {
                @SuppressWarnings("unchecked")
                Set<String> permissions = new HashSet<>((List<String>) properties.get("restrictedEventPermissions"));
                tenant.setRestrictedEventTypes(permissions);
            }
            if (properties.containsKey("authorizedIPs")) {
                @SuppressWarnings("unchecked")
                Set<String> ips = new HashSet<>((List<String>) properties.get("authorizedIPs"));
                tenant.setAuthorizedIPs(ips);
            }

            tenant.setLastModificationDate(new Date());
            tenantService.saveTenant(tenant);
        } catch (Exception e) {
            // Handle error
        }
    }

    @Override
    public void delete(String id) {
        Tenant tenant = tenantService.getTenant(id);
        if (tenant != null && !TenantService.SYSTEM_TENANT.equals(tenant.getItemId())) {
            tenantService.deleteTenant(id);
        }
    }

    @Override
    public List<String> completePropertyNames(String prefix) {
        return PROPERTY_NAMES.stream()
            .filter(name -> name.startsWith(prefix))
            .collect(Collectors.toList());
    }

    @Override
    public String getPropertiesHelp() {
        return String.join("\n",
            "Required properties:",
            "- itemId: The unique identifier of the tenant",
            "- name: The display name of the tenant",
            "",
            "Optional properties:",
            "- description: A description of the tenant's purpose or usage",
            "- status: The tenant's status (ACTIVE, DISABLED, etc.)",
            "- resourceQuota: Resource quota limits for the tenant (profiles, events, requests)",
            "- properties: Additional custom properties for the tenant",
            "- restrictedEventPermissions: List of event types that require special permissions",
            "- authorizedIPs: List of IP addresses or CIDR ranges authorized to make requests"
        );
    }

    @Override
    public List<String> completeId(String prefix) {
        try {
            // Get all tenants (typically not too many to need complex filtering)
            List<Tenant> tenants = tenantService.getAllTenants();
            
            // Filter out system tenant and any that don't match the prefix
            return tenants.stream()
                .filter(tenant -> !TenantService.SYSTEM_TENANT.equals(tenant.getItemId()))
                .map(Tenant::getItemId)
                .filter(id -> prefix.isEmpty() || id.startsWith(prefix))
                .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of(); // Return empty list on error
        }
    }
}
