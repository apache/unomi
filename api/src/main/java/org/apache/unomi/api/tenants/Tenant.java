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

/**
 * Represents a tenant in the system.
 * A tenant is an isolated entity within the system with its own users, data, and configuration.
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
     */
    private ResourceQuota resourceQuota;

    /**
     * The list of API keys associated with the tenant.
     */
    private List<ApiKey> apiKeys;

    /**
     * Additional custom properties for the tenant.
     */
    private Map<String, Object> properties;

    /**
     * Default constructor that initializes the tenant as an Item.
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
     * Gets the list of API keys associated with the tenant.
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

    private String itemId;
    private String privateApiKey;
    private String publicApiKey;
    private Set<String> restrictedEventPermissions = new HashSet<>();
    private Set<String> authorizedIPs = new HashSet<>();

    public Set<String> getRestrictedEventPermissions() {
        return restrictedEventPermissions;
    }

    public void setRestrictedEventPermissions(Set<String> restrictedEventPermissions) {
        this.restrictedEventPermissions = restrictedEventPermissions;
    }

    public Set<String> getAuthorizedIPs() {
        return authorizedIPs;
    }

    public void setAuthorizedIPs(Set<String> authorizedIPs) {
        this.authorizedIPs = authorizedIPs;
    }
}
