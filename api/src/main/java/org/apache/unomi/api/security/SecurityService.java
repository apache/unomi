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
package org.apache.unomi.api.security;

import javax.security.auth.Subject;
import java.security.Principal;

/**
 * Service interface for handling security and authorization in Unomi
 */
public interface SecurityService {

    /**
     * Checks if the current user has the specified role
     * @param role the role to check
     * @return true if the user has the role, false otherwise
     */
    boolean hasRole(String role);

    /**
     * Checks if the current user has admin privileges
     * @return true if the user is an admin, false otherwise
     */
    boolean isAdmin();

    /**
     * Checks if the current user has access to the specified tenants
     * @param tenantId the tenants ID to check
     * @return true if the user has access to the tenants, false otherwise
     */
    boolean hasTenantAccess(String tenantId);

    /**
     * Gets the current authenticated subject
     * @return the current Subject or null if not authenticated
     */
    Subject getCurrentSubject();

    /**
     * Gets the current user principal
     * @return the current Principal or null if not authenticated
     */
    Principal getCurrentPrincipal();

    /**
     * Validates if the current user can perform the specified operation on the given tenants
     * @param tenantId the tenants ID
     * @param operation the operation to check
     * @throws SecurityException if the user doesn't have permission
     */
    void validateTenantOperation(String tenantId, String operation) throws SecurityException;

    /**
     * Checks if the current user has permission for the specified operation
     * @param operation the operation to check
     * @return true if the user has permission, false otherwise
     */
    boolean hasPermission(String operation);

    /**
     * Gets the current tenant ID.
     * @return the current tenant ID
     */
    String getCurrentTenantId();

    /**
     * Gets the encryption key for a specific tenant.
     * @param tenantId the tenant ID
     * @return the tenant's encryption key as a byte array
     */
    byte[] getTenantEncryptionKey(String tenantId);
}
