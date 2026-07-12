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
    /** Identifier of the system tenant, used for cross-tenant administration. */
    public static final String SYSTEM_TENANT = "system";

    private String tenantId;
    private Set<String> roles = new HashSet<>();
    private Set<String> permissions = new HashSet<>();
    private Stack<String> tenantStack = new Stack<>();
    private boolean isSystem = false;

    /**
     * Creates a context for the given tenant, roles, and permissions.
     *
     * @param tenantId tenant id
     * @param roles roles assigned in this context, or {@code null}
     * @param permissions explicit permissions granted in this context, or {@code null}
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
     * Returns a context for the system tenant.
     *
     * @return system execution context
     */
    public static ExecutionContext systemContext() {
        ExecutionContext context = new ExecutionContext(SYSTEM_TENANT, null, null);
        context.isSystem = true;
        return context;
    }

    /**
     * Active tenant id.
     *
     * @return tenant id
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Roles assigned to this context.
     *
     * @return copy of the role set
     */
    public Set<String> getRoles() {
        return new HashSet<>(roles);
    }

    /**
     * Permissions granted to this context.
     *
     * @return copy of the permission set
     */
    public Set<String> getPermissions() {
        return new HashSet<>(permissions);
    }

    /**
     * Whether this context represents the system tenant.
     *
     * @return {@code true} for the system tenant
     */
    public boolean isSystem() {
        return isSystem;
    }

    /**
     * Switches to a new tenant, saving the previous tenant on an internal stack.
     *
     * @param tenantId new tenant id
     */
    public void setTenant(String tenantId) {
        tenantStack.push(this.tenantId);
        this.tenantId = tenantId;
        this.isSystem = SYSTEM_TENANT.equals(tenantId);
    }

    /**
     * Restores the tenant saved by the most recent {@link #setTenant(String)} call.
     */
    public void restorePreviousTenant() {
        if (!tenantStack.isEmpty()) {
            this.tenantId = tenantStack.pop();
            this.isSystem = SYSTEM_TENANT.equals(this.tenantId);
        }
    }

    /**
     * Checks that this context may perform the given operation.
     * System contexts always pass. Otherwise the operation name must be present
     * in the granted permissions.
     *
     * @param operation operation name to validate
     * @throws SecurityException if the permission is missing
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
     * Whether this context has the given permission.
     * System contexts always return {@code true}.
     *
     * @param permission permission name
     * @return {@code true} if the permission is granted
     */
    public boolean hasPermission(String permission) {
        return isSystem || permissions.contains(permission);
    }

    /**
     * Whether this context has the given role.
     * System contexts always return {@code true}.
     *
     * @param role role name
     * @return {@code true} if the role is granted
     */
    public boolean hasRole(String role) {
        return isSystem || roles.contains(role);
    }
}