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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Configuration for the Security Service
 */
public class SecurityServiceConfiguration {
    private Map<String, String[]> operationRoles;
    private String defaultRole;
    private Set<String> systemRoles = new HashSet<>();
    private boolean enableEncryption = false;

    public SecurityServiceConfiguration() {
        // Initialize default system roles
        systemRoles.add(UnomiRoles.ADMINISTRATOR);
        systemRoles.add(UnomiRoles.TENANT_ADMINISTRATOR);

        // Initialize default operation roles
        operationRoles = new HashMap<>();
        operationRoles.put("QUERY", new String[]{UnomiRoles.USER, UnomiRoles.TENANT_ADMINISTRATOR});
        operationRoles.put("AGGREGATE", new String[]{UnomiRoles.USER, UnomiRoles.TENANT_ADMINISTRATOR});
        operationRoles.put("SCROLL_QUERY", new String[]{UnomiRoles.USER, UnomiRoles.TENANT_ADMINISTRATOR});
        operationRoles.put("SAVE", new String[]{UnomiRoles.USER, UnomiRoles.TENANT_ADMINISTRATOR});
        operationRoles.put("UPDATE", new String[]{UnomiRoles.USER, UnomiRoles.TENANT_ADMINISTRATOR});
        operationRoles.put("DELETE", new String[]{UnomiRoles.USER, UnomiRoles.TENANT_ADMINISTRATOR});
        operationRoles.put("PURGE", new String[]{UnomiRoles.SYSTEM_MAINTENANCE, UnomiRoles.TENANT_ADMINISTRATOR});
        operationRoles.put("SYSTEM_MAINTENANCE", new String[]{UnomiRoles.SYSTEM_MAINTENANCE});
        operationRoles.put("ENCRYPT_PROFILE_DATA", new String[]{UnomiRoles.PROFILE_ENCRYPT});
        operationRoles.put("DECRYPT_PROFILE_DATA", new String[]{UnomiRoles.PROFILE_DECRYPT});
        operationRoles.put("SCHEMA_WRITE", new String[]{UnomiRoles.TENANT_ADMINISTRATOR});
        operationRoles.put("SCHEMA_DELETE", new String[]{UnomiRoles.TENANT_ADMINISTRATOR});
        defaultRole = UnomiRoles.USER;
    }

    public Map<String, String[]> getOperationRoles() {
        return operationRoles;
    }

    public void setOperationRoles(Map<String, String[]> operationRoles) {
        this.operationRoles = operationRoles;
    }

    public String getDefaultRole() {
        return defaultRole;
    }

    public void setDefaultRole(String defaultRole) {
        this.defaultRole = defaultRole;
    }

    /**
     * Get required roles for an operation
     * @param operation the operation to check
     * @return array of required roles, or array containing default role if operation not mapped
     */
    public String[] getRequiredRolesForOperation(String operation) {
        return operationRoles.getOrDefault(operation, new String[]{defaultRole});
    }

    public Set<String> getSystemRoles() {
        return systemRoles;
    }

    public void setSystemRoles(Set<String> systemRoles) {
        this.systemRoles = systemRoles;
    }

    public void addSystemRole(String role) {
        systemRoles.add(role);
    }

    public void removeSystemRole(String role) {
        systemRoles.remove(role);
    }

    public boolean isEnableEncryption() {
        return enableEncryption;
    }

    public void setEnableEncryption(boolean enableEncryption) {
        this.enableEncryption = enableEncryption;
    }

}
