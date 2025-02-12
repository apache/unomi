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
package org.apache.unomi.shell.dev.commands.apikeys;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.tenants.ApiKey;
import org.apache.unomi.api.tenants.ApiKey.ApiKeyType;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.shell.dev.services.BaseCrudCommand;
import org.apache.unomi.shell.dev.services.CrudCommand;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component(service = CrudCommand.class, immediate = true)
public class ApiKeyCrudCommand extends BaseCrudCommand {

    private static final ObjectMapper OBJECT_MAPPER = new CustomObjectMapper();
    private static final List<String> PROPERTY_NAMES = List.of(
        "itemId", "name", "description", "keyType", "key", "tenantId"
    );

    @Reference
    private TenantService tenantService;

    @Override
    public String getObjectType() {
        return "apikey";
    }

    @Override
    public String[] getHeaders() {
        return new String[] {
            "Identifier",
            "Name",
            "Description",
            "Key Type",
            "Key",
            "Tenant"
        };
    }

    @Override
    protected PartialList<?> getItems(Query query) {
        List<ApiKey> allApiKeys = new ArrayList<>();
        for (Tenant tenant : tenantService.getAllTenants()) {
            if (tenant.getApiKeys() != null) {
                allApiKeys.addAll(tenant.getApiKeys());
            }
        }

        // Apply query limit
        Integer offset = query.getOffset();
        Integer limit = query.getLimit();
        int start = offset == null ? 0 : offset;
        int size = limit == null ? allApiKeys.size() : limit;
        int end = Math.min(start + size, allApiKeys.size());

        List<ApiKey> pagedApiKeys = allApiKeys.subList(start, end);
        return new PartialList<ApiKey>(pagedApiKeys, start, pagedApiKeys.size(), allApiKeys.size(), PartialList.Relation.EQUAL);
    }

    @Override
    protected String[] buildRow(Object item) {
        ApiKey apiKey = (ApiKey) item;
        return new String[] {
            apiKey.getItemId(),
            apiKey.getName(),
            apiKey.getDescription(),
            apiKey.getKeyType().toString(),
            apiKey.getKey(),
            apiKey.getTenantId()
        };
    }

    @Override
    public String create(Map<String, Object> properties) {
        String tenantId = (String) properties.get("tenantId");
        if (StringUtils.isBlank(tenantId)) {
            throw new IllegalArgumentException("tenantId is required");
        }

        ApiKeyType keyType = ApiKeyType.valueOf((String) properties.get("keyType"));
        Long validityPeriod = properties.containsKey("validityPeriod") ?
            Long.valueOf((String) properties.get("validityPeriod")) : null;

        ApiKey apiKey = tenantService.generateApiKeyWithType(tenantId, keyType, validityPeriod);
        if (apiKey != null) {
            apiKey.setName((String) properties.get("name"));
            apiKey.setDescription((String) properties.get("description"));

            // Update the tenant with the new API key
            Tenant tenant = tenantService.getTenant(tenantId);
            if (keyType == ApiKeyType.PUBLIC) {
                tenant.setPublicApiKey(apiKey.getKey());
            } else {
                tenant.setPrivateApiKey(apiKey.getKey());
            }
            tenantService.saveTenant(tenant);
        }
        return apiKey.getItemId();
    }

    @Override
    public Map<String, Object> read(String id) {
        for (Tenant tenant : tenantService.getAllTenants()) {
            if (tenant.getApiKeys() != null) {
                for (ApiKey apiKey : tenant.getApiKeys()) {
                    if (apiKey.getItemId().equals(id)) {
                        return OBJECT_MAPPER.convertValue(apiKey, Map.class);
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void update(String id, Map<String, Object> properties) {
        String tenantId = (String) properties.get("tenantId");
        if (StringUtils.isBlank(tenantId)) {
            throw new IllegalArgumentException("tenantId is required");
        }

        Tenant tenant = tenantService.getTenant(tenantId);
        if (tenant != null && tenant.getApiKeys() != null) {
            for (ApiKey apiKey : tenant.getApiKeys()) {
                if (apiKey.getItemId().equals(id)) {
                    apiKey.setName((String) properties.get("name"));
                    apiKey.setDescription((String) properties.get("description"));
                    tenantService.saveTenant(tenant);
                    return;
                }
            }
        }
        throw new IllegalArgumentException("API key not found: " + id);
    }

    @Override
    public void delete(String id) {
        for (Tenant tenant : tenantService.getAllTenants()) {
            if (tenant.getApiKeys() != null) {
                List<ApiKey> updatedKeys = tenant.getApiKeys().stream()
                    .filter(apiKey -> !apiKey.getItemId().equals(id))
                    .collect(Collectors.toList());

                if (updatedKeys.size() < tenant.getApiKeys().size()) {
                    tenant.setApiKeys(updatedKeys);
                    tenantService.saveTenant(tenant);
                    return;
                }
            }
        }
        throw new IllegalArgumentException("API key not found: " + id);
    }

    @Override
    public String getPropertiesHelp() {
        return String.join("\n",
            "Required properties:",
            "- tenantId: ID of the tenant",
            "- keyType: Type of API key (PUBLIC or PRIVATE)",
            "",
            "Optional properties:",
            "- name: Name of the API key",
            "- description: Description of the API key",
            "- validityPeriod: Validity period in milliseconds (null for no expiration)"
        );
    }

    @Override
    public List<String> completePropertyNames(String prefix) {
        return PROPERTY_NAMES.stream()
                .filter(name -> name.startsWith(prefix))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> completePropertyValue(String propertyName, String prefix) {
        if ("keyType".equals(propertyName)) {
            return List.of(ApiKeyType.values()).stream()
                    .map(Enum::name)
                    .filter(name -> name.startsWith(prefix.toUpperCase()))
                    .collect(Collectors.toList());
        }
        if ("tenantId".equals(propertyName)) {
            return tenantService.getAllTenants().stream()
                    .map(Tenant::getItemId)
                    .filter(id -> id.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return super.completePropertyValue(propertyName, prefix);
    }
}
