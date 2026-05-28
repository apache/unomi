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
 * Represents the execution context for operations in Unomi, including security and tenant information.
 */
public class ExecutionContext {
    public static final String SYSTEM_TENANT = "system";
    
    private String tenantId;
    private Set<String> roles = new HashSet<>();
    private Set<String> permissions = new HashSet<>();
    private Stack<String> tenantStack = new Stack<>();
    private boolean isSystem = false;
    
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
    
    public static ExecutionContext systemContext() {
        ExecutionContext context = new ExecutionContext(SYSTEM_TENANT, null, null);
        context.isSystem = true;
        return context;
    }
    
    public String getTenantId() {
        return tenantId;
    }
    
    public Set<String> getRoles() {
        return new HashSet<>(roles);
    }
    
    public Set<String> getPermissions() {
        return new HashSet<>(permissions);
    }
    
    public boolean isSystem() {
        return isSystem;
    }
    
    public void setTenant(String tenantId) {
        tenantStack.push(this.tenantId);
        this.tenantId = tenantId;
    }
    
    public void restorePreviousTenant() {
        if (!tenantStack.isEmpty()) {
            this.tenantId = tenantStack.pop();
        }
    }
    
    public void validateAccess(String operation) {
        if (isSystem) {
            return;
        }
        
        if (!hasPermission(operation)) {
            throw new SecurityException("Access denied: Missing permission for operation " + operation + " for tenant " + tenantId + " and roles " + roles);
        }
    }
    
    public boolean hasPermission(String permission) {
        return isSystem || permissions.contains(permission);
    }
    
    public boolean hasRole(String role) {
        return isSystem || roles.contains(role);
    }
} 