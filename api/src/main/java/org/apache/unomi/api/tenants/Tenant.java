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

import org.apache.unomi.api.Item;

import java.util.*;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlTransient;

/**
 * Represents a tenant in the system.
 * A tenant is an isolated entity within the system with its own users, data, and configuration.
 * Each tenant has its own set of API keys (public and private) for authentication and authorization,
 * resource quotas to limit usage, and event permissions to control access to specific event types.
 * This class extends the base Item class and provides functionality for managing tenant
 * settings, resource quotas, and lifecycle.
 */
public class Tenant extends Item {
    /**
     * The item type for a tenant.
     */
    public static final String ITEM_TYPE = "tenant";

    /**
     * The display name of the tenant.
     */
    private String name;

    /**
     * A description of the tenant's purpose or usage.
     */
    private String description;

    /**
     * The current operational status of the tenant.
     */
    private TenantStatus status;

    /**
     * The date when the tenant was created.
     */
    private Date creationDate;

    /**
     * The date when the tenant was last modified.
     */
    private Date lastModificationDate;

    /**
     * The resource quota limits for the tenant.
     * This includes limits on profiles, events, and requests.
     */
    private ResourceQuota resourceQuota;

    /**
     * The list of all API keys (both active and historical) associated with the tenant.
     * This list maintains a history of all API keys that have been generated for the tenant,
     * including both public and private keys, for auditing purposes.
     */
    private List<ApiKey> apiKeys;

    /**
     * Additional custom properties for the tenant.
     */
    private Map<String, Object> properties;

    /**
     * The set of event types that are restricted for this tenant.
     * Events of these types will require IP validation before being processed.
     * This is used to control which event types require additional validation
     * at the tenant level.
     */
    private Set<String> restrictedEventTypes = new HashSet<>();

    /**
     * The set of IP addresses or CIDR ranges that are authorized to make requests
     * for this tenant. Requests from IP addresses not in this set will be rejected.
     */
    private Set<String> authorizedIPs = new HashSet<>();

    /**
     * Default constructor that initializes the tenant as an Item.
     * Sets the item type to TENANT and initializes empty collections.
     */
    public Tenant() {
        super();
        setItemType(ITEM_TYPE);
    }

    /**
     * Gets the tenant's display name.
     * @return the tenant name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the tenant's display name.
     * @param name the tenant name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the tenant's description.
     * @return the tenant description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the tenant's description.
     * @param description the tenant description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets the tenant's current status.
     * @return the tenant status
     */
    public TenantStatus getStatus() {
        return status;
    }

    /**
     * Sets the tenant's status.
     * @param status the tenant status to set
     */
    public void setStatus(TenantStatus status) {
        this.status = status;
    }

    /**
     * Gets the tenant's creation date.
     * @return the creation date
     */
    @Override
    public Date getCreationDate() {
        return creationDate;
    }

    /**
     * Sets the tenant's creation date.
     * @param creationDate the creation date to set
     */
    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    /**
     * Gets the tenant's last modification date.
     * @return the last modification date
     */
    @Override
    public Date getLastModificationDate() {
        return lastModificationDate;
    }

    /**
     * Sets the tenant's last modification date.
     * @param lastModificationDate the last modification date to set
     */
    public void setLastModificationDate(Date lastModificationDate) {
        this.lastModificationDate = lastModificationDate;
    }

    /**
     * Gets the tenant's resource quota settings.
     * @return the resource quota settings
     */
    public ResourceQuota getResourceQuota() {
        return resourceQuota;
    }

    /**
     * Sets the tenant's resource quota settings.
     * @param resourceQuota the resource quota settings to set
     */
    public void setResourceQuota(ResourceQuota resourceQuota) {
        this.resourceQuota = resourceQuota;
    }

    /**
     * Gets the list of all API keys associated with the tenant.
     * This includes both active and historical keys for auditing purposes.
     * @return the list of API keys
     */
    public List<ApiKey> getApiKeys() {
        return apiKeys;
    }

    /**
     * Sets the list of API keys associated with the tenant.
     * @param apiKeys the list of API keys to set
     */
    public void setApiKeys(List<ApiKey> apiKeys) {
        this.apiKeys = apiKeys;
    }

    /**
     * Gets additional tenant properties as key-value pairs.
     * @return map of additional properties
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * Sets additional tenant properties as key-value pairs.
     * @param properties map of additional properties to set
     */
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    /**
     * Gets the set of event types that are restricted for this tenant.
     * Events of these types will require IP validation before being processed.
     * @return the set of restricted event types
     */
    public Set<String> getRestrictedEventTypes() {
        return restrictedEventTypes;
    }

    /**
     * Sets the event types that are restricted for this tenant.
     * Events of these types will require IP validation before being processed.
     * @param restrictedEventTypes the set of restricted event types to set
     */
    public void setRestrictedEventTypes(Set<String> restrictedEventTypes) {
        this.restrictedEventTypes = restrictedEventTypes;
    }

    /**
     * Gets the set of authorized IP addresses or CIDR ranges for this tenant.
     * @return the set of authorized IP addresses/ranges
     */
    public Set<String> getAuthorizedIPs() {
        return authorizedIPs;
    }

    /**
     * Sets the authorized IP addresses or CIDR ranges for this tenant.
     * @param authorizedIPs the set of authorized IP addresses/ranges to set
     */
    public void setAuthorizedIPs(Set<String> authorizedIPs) {
        this.authorizedIPs = authorizedIPs;
    }

    /**
     * Gets the currently active private API key for the tenant.
     * This method resolves the active private API key from the API keys list.
     * It returns the most recently created, non-revoked, non-expired private key.
     * This key should be used for secure operations and administrative tasks.
     * @return the active private API key, or null if no valid private key exists
     */
    @XmlTransient
    public String getPrivateApiKey() {
        if (apiKeys == null) {
            return null;
        }
        
        return apiKeys.stream()
            .filter(key -> key.getKeyType() == ApiKey.ApiKeyType.PRIVATE)
            .filter(key -> !key.isRevoked())
            .filter(key -> key.getExpirationDate() == null || key.getExpirationDate().after(new Date()))
            .max(Comparator.comparing(ApiKey::getCreationDate))
            .map(ApiKey::getKey)
            .orElse(null);
    }

    /**
     * Gets the currently active public API key for the tenant.
     * This method resolves the active public API key from the API keys list.
     * It returns the most recently created, non-revoked, non-expired public key.
     * This key can be safely used in client-side applications.
     * @return the active public API key, or null if no valid public key exists
     */
    @XmlTransient
    public String getPublicApiKey() {
        if (apiKeys == null) {
            return null;
        }
        
        return apiKeys.stream()
            .filter(key -> key.getKeyType() == ApiKey.ApiKeyType.PUBLIC)
            .filter(key -> !key.isRevoked())
            .filter(key -> key.getExpirationDate() == null || key.getExpirationDate().after(new Date()))
            .max(Comparator.comparing(ApiKey::getCreationDate))
            .map(ApiKey::getKey)
            .orElse(null);
    }

    /**
     * Gets all active private API keys for the tenant.
     * This method returns all non-revoked, non-expired private keys.
     * @return list of active private API keys, or empty list if none exist
     */
    @XmlTransient
    public List<ApiKey> getActivePrivateApiKeys() {
        if (apiKeys == null) {
            return new ArrayList<>();
        }
        
        return apiKeys.stream()
            .filter(key -> key.getKeyType() == ApiKey.ApiKeyType.PRIVATE)
            .filter(key -> !key.isRevoked())
            .filter(key -> key.getExpirationDate() == null || key.getExpirationDate().after(new Date()))
            .collect(Collectors.toList());
    }

    /**
     * Gets all active public API keys for the tenant.
     * This method returns all non-revoked, non-expired public keys.
     * @return list of active public API keys, or empty list if none exist
     */
    @XmlTransient
    public List<ApiKey> getActivePublicApiKeys() {
        if (apiKeys == null) {
            return new ArrayList<>();
        }
        
        return apiKeys.stream()
            .filter(key -> key.getKeyType() == ApiKey.ApiKeyType.PUBLIC)
            .filter(key -> !key.isRevoked())
            .filter(key -> key.getExpirationDate() == null || key.getExpirationDate().after(new Date()))
            .collect(Collectors.toList());
    }

    /**
     * Gets all active API keys for the tenant.
     * This method returns all non-revoked, non-expired keys regardless of type.
     * @return list of all active API keys, or empty list if none exist
     */
    @XmlTransient
    public List<ApiKey> getActiveApiKeys() {
        if (apiKeys == null) {
            return new ArrayList<>();
        }
        
        return apiKeys.stream()
            .filter(key -> !key.isRevoked())
            .filter(key -> key.getExpirationDate() == null || key.getExpirationDate().after(new Date()))
            .collect(Collectors.toList());
    }
}
