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
package org.apache.unomi.persistence.spi;

import java.io.IOException;

/**
 * The SecurityProvider interface defines the contract for implementing security
 * mechanisms in Apache Unomi's multi-tenant environment. This provider handles
 * tenant-level security operations including setup, removal, query security,
 * and access validation.
 *
 * <p>Implementations of this interface are responsible for:
 * <ul>
 *   <li>Managing tenant security configurations</li>
 *   <li>Enforcing tenant isolation</li>
 *   <li>Securing queries with tenant context</li>
 *   <li>Validating tenant access permissions</li>
 * </ul>
 *
 * <p>The security provider ensures proper data isolation between tenants
 * and enforces access controls at the persistence layer.
 *
 * @since 1.0.0
 */
public interface SecurityProvider {

    /**
     * Sets up security configuration for a new tenant in the system.
     * This method should initialize all necessary security contexts and
     * configurations required for tenant isolation.
     *
     * @param tenantId the unique identifier of the tenant
     * @param tenantName the display name of the tenant
     * @throws IOException if there is an error during tenant setup
     * @throws IllegalArgumentException if tenantId or tenantName is null or empty
     */
    void setupTenant(String tenantId, String tenantName) throws IOException;

    /**
     * Removes all security configurations and contexts associated with a tenant.
     * This method should be called when a tenant is being removed from the system
     * to ensure proper cleanup of security resources.
     *
     * @param tenantId the unique identifier of the tenant to remove
     * @throws IOException if there is an error during tenant removal
     * @throws IllegalArgumentException if tenantId is null or empty
     */
    void removeTenant(String tenantId) throws IOException;

    /**
     * Adds tenant-specific security constraints to a query string to ensure proper
     * data isolation. This method modifies the provided query to include
     * tenant context and security filters.
     *
     * @param query the original query string to be modified
     * @param tenantId the unique identifier of the tenant
     * @return the modified query string with tenant security constraints
     * @throws IllegalArgumentException if query or tenantId is null
     */
    String addTenantSecurity(String query, String tenantId);

    /**
     * Validates whether the current operation is allowed for the specified tenant.
     * This method performs security checks to ensure the tenant has appropriate
     * access rights for the requested operation.
     *
     * @param tenantId the unique identifier of the tenant to validate
     * @throws SecurityException if the tenant does not have valid access
     * @throws IllegalArgumentException if tenantId is null or empty
     */
    void validateAccess(String tenantId) throws SecurityException;

    /**
     * Checks if the current user has access to the specified tenant.
     *
     * @param tenantId the ID of the tenant to check access for
     * @return true if the user has access, false otherwise
     */
    boolean hasTenantAccess(String tenantId);

    /**
     * Gets the current user ID.
     *
     * @return the current user ID
     */
    String getCurrentUserId();
}
