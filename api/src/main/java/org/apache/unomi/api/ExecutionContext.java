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
package org.apache.unomi.api;

import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

/**
 * Security and tenant context for an in-flight operation.
 * Carries the active tenant id, roles, permissions, and helpers to check
 * access or temporarily switch tenant scope. Services read this object to
 * enforce multi-tenant isolation and authorization.
 */
public class ExecutionContext {
    /** The constant string used to identify the system tenant. */
    public static final String SYSTEM_TENANT = "system";
    
    private String tenantId;
    private Set<String> roles = new HashSet<>();
    private Set<String> permissions = new HashSet<>();
    private Stack<String> tenantStack = new Stack<>();
    private boolean isSystem = false;
    
    /**
     * Constructs a new execution context with specified
     * security and tenant details.
     * @param tenantId The unique identifier of the tenant for this context.
     * @param roles A set of roles assigned to the user in this
     * context. Can be null.
     * @param permissions A set of explicit permissions granted to the user in
     * this context. Can be null.
     */
    public ExecutionContext(String tenantId, Set<String> roles, Set<String> permissions) {
        this.tenantId = tenantId;
        if (tenantId != null && tenantId.equals(SYSTEM_TENANT)) {
            this.isSystem = true;
        }
        if (roles != null) {
            this.roles.addAll(roles);
        }
        if (permissions != null) {
            this.permissions.addAll(permissions);
        }
    }
    
    /**
     * Creates and returns a dedicated execution context
     * representing the system tenant.
     * @return An {@link ExecutionContext} instance configured
     * for the system tenant.
     */
    public static ExecutionContext systemContext() {
        ExecutionContext context = new ExecutionContext(SYSTEM_TENANT, null, null);
        context.isSystem = true;
        return context;
    }
    
    /**
     * Retrieves the unique identifier of the current tenant
     * associated with this context.
     * @return The tenant ID string.
     */
    public String getTenantId() {
        return tenantId;
    }
    
    /**
     * Returns a copy of the set of roles assigned to this execution context.
     * @return A new {@link Set} containing a copy of all assigned roles.
     */
    public Set<String> getRoles() {
        return new HashSet<>(roles);
    }
    
    /**
     * Returns a copy of the set of permissions granted to
     * this execution context.
     * @return A new {@link Set} containing a copy of all explicit permissions.
     */
    public Set<String> getPermissions() {
        return new HashSet<>(permissions);
    }
    
    /**
     * Checks if this execution context is designated as the system context.
     * @return true if the context represents the system
     * tenant, false otherwise.
     */
    public boolean isSystem() {
        return isSystem;
    }
    
    /**
     * Sets a new tenant ID for the current execution context. This operation
     * saves the previous tenant ID onto an internal stack and updates the
     * system status accordingly.
     * @param tenantId The new tenant identifier to set.
     */
    public void setTenant(String tenantId) {
        tenantStack.push(this.tenantId);
        this.tenantId = tenantId;
        this.isSystem = SYSTEM_TENANT.equals(tenantId);
    }

    /**
     * Restores the execution context to the previously active tenant.
     * If a previous tenant was set, this method pops that tenant ID from the
     * internal stack and updates the current context state accordingly.
     */
    public void restorePreviousTenant() {
        if (!tenantStack.isEmpty()) {
            this.tenantId = tenantStack.pop();
            this.isSystem = SYSTEM_TENANT.equals(this.tenantId);
        }
    }
    
    /**
     * Validates whether the current execution context has sufficient
     * permissions to perform a given operation.
     * If the context is system-level, validation passes immediately. Otherwise,
     * it checks if the required permission exists in the context's
     * granted permissions.
     * @param operation The specific operation name required
     * for access validation.
     * @throws SecurityException if the current context lacks the necessary
     * permission for the given operation.
     */
    public void validateAccess(String operation) {
        if (isSystem) {
            return;
        }
        
        if (!hasPermission(operation)) {
            throw new SecurityException("Access denied: Missing permission for operation " + operation + " for tenant " + tenantId + " and roles " + roles);
        }
    }
    
    /**
     * Checks if the current execution context possesses a specific permission.
     * The context is considered to have the permission if it is system-level or
     * if the permission name is explicitly listed in the context's
     * granted permissions.
     * @param permission The name of the permission to check for.
     * @return true if the context is system-level or has been granted the
     * permission, false otherwise.
     */
    public boolean hasPermission(String permission) {
        return isSystem || permissions.contains(permission);
    }
    
    /**
     * Checks if the current execution context possesses a specific role.
     * The context is considered to have the role if it is system-level or if
     * the role name is explicitly listed in the context's granted roles.
     * @param role The name of the role to check for.
     * @return true if the context is system-level or has been granted the
     * role, false otherwise.
     */
    public boolean hasRole(String role) {
        return isSystem || roles.contains(role);
    }
} 