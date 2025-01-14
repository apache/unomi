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

import java.util.List;
import java.util.Map;

/**
 * Service interface for managing multi-tenant functionality in Apache Unomi.
 * This service provides methods for creating, retrieving, and managing tenants,
 * as well as handling tenant-specific API keys and tenant context management.
 * It ensures proper isolation between different tenants' data and configurations.
 */
public interface TenantService {

    /**
     * Creates a new tenant in the system with the specified name and properties.
     *
     * @param name       the name of the tenant to create
     * @param properties additional properties to associate with the tenant
     * @return the newly created Tenant object
     * @throws IllegalArgumentException if the name is null or empty
     */
    Tenant createTenant(String name, Map<String, Object> properties);

    /**
     * Generates a new API key for the specified tenant with an optional validity period.
     *
     * @param tenantId       the ID of the tenant for which to generate the API key
     * @param validityPeriod the period (in milliseconds) for which the API key should be valid, null for no expiration
     * @return the generated ApiKey object containing the key and associated metadata
     * @throws IllegalArgumentException if tenantId is null or does not exist
     */
    ApiKey generateApiKey(String tenantId, Long validityPeriod);

    /**
     * Retrieves the ID of the tenant associated with the current context.
     * This method is typically used in request-scoped operations to determine
     * the tenant context in which the current operation is being executed.
     *
     * @return the current tenant ID, or null if no tenant context is set
     */
    String getCurrentTenantId();

    /**
     * Sets the current tenant context for the executing thread.
     * This method should be used with caution and typically wrapped in a try-finally block
     * to ensure the tenant context is properly cleared after use.
     *
     * @param tenantId the ID of the tenant to set as current
     * @throws IllegalArgumentException if the tenantId is null or does not exist
     */
    void setCurrentTenant(String tenantId);

    /**
     * Retrieves a tenant by its ID.
     *
     * @param tenantId the ID of the tenant to retrieve
     * @return the Tenant object if found, null otherwise
     */
    Tenant getTenant(String tenantId);

    /**
     * Retrieves all tenants registered in the system.
     * This method provides access to all tenant configurations and metadata,
     * and should be used with appropriate access controls.
     *
     * @return a List of all Tenant objects in the system
     */
    List<Tenant> getAllTenants();

    /**
     * Updates an existing tenant's information.
     *
     * @param tenant the tenant with updated information
     * @throws IllegalArgumentException if tenant is null or does not exist
     */
    void saveTenant(Tenant tenant);

    /**
     * Deletes a tenant and all associated data from the system.
     *
     * @param tenantId the ID of the tenant to delete
     * @throws IllegalArgumentException if tenantId is null or does not exist
     */
    void deleteTenant(String tenantId);

    /**
     * Validates an API key for a given tenant.
     *
     * @param tenantId the ID of the tenant
     * @param key the API key to validate
     * @return true if the key is valid, false otherwise
     */
    boolean validateApiKey(String tenantId, String key);
}
