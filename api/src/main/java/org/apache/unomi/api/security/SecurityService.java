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
import java.util.Set;

/**
 * A service to manage security-related operations in Apache Unomi.
 * This service provides comprehensive security management including:
 * - Subject management (authentication and authorization)
 * - Role-based access control (RBAC)
 * - Tenant isolation and access control
 * - Operation validation
 * - System and privileged operations
 * - Encryption key management
 */
public interface SecurityService {
    /** The system tenant identifier used for system-wide operations */
    String SYSTEM_TENANT = "SYSTEM_TENANT";

    /**
     * Retrieves the current subject from the security context. The subject is determined in the following order:
     * 1. JAAS context - If a JAAS authentication is active
     * 2. Privileged subject - If a temporary privileged operation is in progress
     * 3. Current request subject - The subject associated with the current request
     *
     * @return the current subject or null if no subject is set in any context
     */
    Subject getCurrentSubject();

    /**
     * Retrieves the current principal from the active subject.
     * The principal represents the primary identity of the authenticated entity.
     *
     * @return the current principal or null if no subject is set or the subject has no principals
     */
    Principal getCurrentPrincipal();

    /**
     * Sets the current request subject and updates the tenant context accordingly.
     * This is typically called during authentication to establish the security context.
     * The tenant context will be updated based on the subject's tenant ID.
     *
     * @param subject the subject to set as the current request subject
     */
    void setCurrentSubject(Subject subject);

    /**
     * Clears all security contexts including:
     * - JAAS context
     * - Privileged subject
     * - Current request subject
     * This should be called when cleaning up after request processing or when switching contexts.
     */
    void clearCurrentSubject();

    /**
     * Checks if the current context has a specific role by examining subjects in the following order:
     * 1. JAAS context
     * 2. Privileged subject
     * 3. Current request subject
     *
     * @param role the role to check for (e.g., ROLE_UNOMI_ADMIN, ROLE_UNOMI_TENANT_USER)
     * @return true if any active subject has the specified role, false otherwise
     */
    boolean hasRole(String role);

    /**
     * Checks if the current context has a specific permission by examining subjects in order:
     * 1. JAAS context
     * 2. Privileged subject
     * 3. Current request subject
     *
     * Permissions are currently mapped directly to roles but may be enhanced in future versions.
     *
     * @param permission the permission to check for
     * @return true if any active subject has the specified permission, false otherwise
     */
    boolean hasPermission(String permission);

    /**
     * Executes code with temporarily elevated privileges using the specified subject.
     * The privileged subject will be available only during the execution of the operation
     * and will be automatically cleaned up afterward, restoring the previous context.
     *
     * This is useful for operations that require temporary elevation of privileges.
     *
     * @param privilegedSubject the subject with elevated privileges to use during execution
     * @param operation the operation to execute with elevated privileges
     */
    void executeWithPrivilegedSubject(Subject privilegedSubject, Runnable operation);

    /**
     * Retrieves the current tenant ID based on the active subject context.
     * The tenant ID is determined from the subject's principal.
     *
     * @return the current tenant ID, or SYSTEM_TENANT if operating in system context
     */
    String getCurrentSubjectTenantId();

    /**
     * Checks if the current operation is being performed in the system tenant context.
     * System tenant operations have special privileges and bypass tenant isolation.
     *
     * @return true if operating in the system tenant context, false otherwise
     */
    boolean isOperatingOnSystemTenant();

    /**
     * Retrieves the encryption key for a specific tenant.
     * This key is used for encrypting sensitive data within the tenant's context.
     *
     * @param tenantId the ID of the tenant whose encryption key should be retrieved
     * @return the tenant's encryption key as a byte array, or null if encryption is not configured
     */
    byte[] getTenantEncryptionKey(String tenantId);

    /**
     * Logs a tenant operation for auditing purposes.
     * This creates an audit trail of security-relevant operations performed within each tenant.
     *
     * @param tenantId the ID of the tenant where the operation was performed
     * @param operation the type of operation that was performed
     */
    void auditTenantOperation(String tenantId, String operation);

    /**
     * Sets a temporary privileged subject for operations requiring elevated permissions.
     * The privileged subject will be used in addition to the current subject for permission checks.
     *
     * Note: This is different from executeWithPrivilegedSubject as it doesn't automatically clean up.
     * You must call clearPrivilegedSubject() when the elevated privileges are no longer needed.
     *
     * @param subject the privileged subject to set
     */
    void setPrivilegedSubject(Subject subject);

    /**
     * Clears the temporary privileged subject.
     * This should be called after operations requiring elevated privileges are complete.
     */
    void clearPrivilegedSubject();

    /**
     * Checks if the current subject has administrative privileges.
     * An admin has elevated privileges within their scope but may still be restricted by tenant boundaries.
     *
     * @return true if the current subject has the ROLE_UNOMI_ADMIN role, false otherwise
     */
    boolean isAdmin();

    /**
     * Checks if the current subject has access to a specific tenant.
     * Access is granted if any of the following conditions are met:
     * - The subject has the ROLE_UNOMI_SYSTEM role
     * - The subject is an admin of the specified tenant
     * - The subject belongs to the specified tenant
     *
     * @param tenantId the ID of the tenant to check access for
     * @return true if the subject has access to the tenant, false otherwise
     */
    boolean hasTenantAccess(String tenantId);

    /**
     * Checks if the current subject has system-level access.
     * This includes both administrator and tenant administrator roles.
     *
     * @return true if the current subject has system-level access, false otherwise
     */
    boolean hasSystemAccess();

    /**
     * Get the system subject with administrative privileges
     * @return the system subject
     */
    Subject getSystemSubject();

    /**
     * Extract roles from a subject
     * @param subject the subject to extract roles from
     * @return set of role names
     */
    Set<String> extractRolesFromSubject(Subject subject);

    /**
     * Get the security service configuration
     * @return the security configuration
     */
    SecurityServiceConfiguration getConfiguration();

    /**
     * Gets all permissions associated with a specific role based on the security configuration.
     * 
     * @param role The role name to retrieve permissions for. This should be one of the standard
     *             roles defined in {@link UnomiRoles} or a custom role defined in the security
     *             configuration.
     * 
     * @return A Set of String containing all permissions granted to the specified role. The permissions
     *         are derived from the security configuration's operation roles mapping. If the role has no
     *         explicitly mapped permissions, or if the configuration is not properly set up, an empty
     *         Set will be returned.
     *         
     * @see SecurityServiceConfiguration#getOperationRoles()
     * @see UnomiRoles
     */
    Set<String> getPermissionsForRole(String role);
}
