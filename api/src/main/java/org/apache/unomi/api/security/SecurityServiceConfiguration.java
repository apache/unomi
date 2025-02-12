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
    // Permission constants
    public static final String PERMISSION_QUERY = "QUERY";
    public static final String PERMISSION_AGGREGATE = "AGGREGATE";
    public static final String PERMISSION_SCROLL_QUERY = "SCROLL_QUERY";
    public static final String PERMISSION_SAVE = "SAVE";
    public static final String PERMISSION_UPDATE = "UPDATE";
    public static final String PERMISSION_DELETE = "DELETE";
    public static final String PERMISSION_REMOVE_BY_QUERY = "REMOVE_BY_QUERY";
    public static final String PERMISSION_PURGE = "PURGE";
    public static final String PERMISSION_SYSTEM_MAINTENANCE = "SYSTEM_MAINTENANCE";
    public static final String PERMISSION_ENCRYPT_PROFILE_DATA = "ENCRYPT_PROFILE_DATA";
    public static final String PERMISSION_DECRYPT_PROFILE_DATA = "DECRYPT_PROFILE_DATA";
    public static final String PERMISSION_SCHEMA_WRITE = "SCHEMA_WRITE";
    public static final String PERMISSION_SCHEMA_DELETE = "SCHEMA_DELETE";

    private Map<String, String[]> permissionRoles;
    private String defaultRole;
    private Set<String> systemRoles = new HashSet<>();
    private boolean enableEncryption = false;

    public SecurityServiceConfiguration() {
        // Initialize default system roles
        systemRoles.add(UnomiRoles.ADMINISTRATOR);
        systemRoles.add(UnomiRoles.TENANT_ADMINISTRATOR);

        // Initialize default operation roles
        permissionRoles = new HashMap<>();
        permissionRoles.put(PERMISSION_QUERY, new String[]{UnomiRoles.USER, UnomiRoles.TENANT_ADMINISTRATOR});
        permissionRoles.put(PERMISSION_AGGREGATE, new String[]{UnomiRoles.USER, UnomiRoles.TENANT_ADMINISTRATOR});
        permissionRoles.put(PERMISSION_SCROLL_QUERY, new String[]{UnomiRoles.USER, UnomiRoles.TENANT_ADMINISTRATOR});
        permissionRoles.put(PERMISSION_SAVE, new String[]{UnomiRoles.USER, UnomiRoles.TENANT_ADMINISTRATOR});
        permissionRoles.put(PERMISSION_UPDATE, new String[]{UnomiRoles.USER, UnomiRoles.TENANT_ADMINISTRATOR});
        permissionRoles.put(PERMISSION_DELETE, new String[]{UnomiRoles.USER, UnomiRoles.TENANT_ADMINISTRATOR});
        permissionRoles.put(PERMISSION_REMOVE_BY_QUERY, new String[]{UnomiRoles.USER, UnomiRoles.TENANT_ADMINISTRATOR});
        permissionRoles.put(PERMISSION_PURGE, new String[]{UnomiRoles.SYSTEM_MAINTENANCE, UnomiRoles.TENANT_ADMINISTRATOR});
        permissionRoles.put(PERMISSION_SYSTEM_MAINTENANCE, new String[]{UnomiRoles.SYSTEM_MAINTENANCE});
        permissionRoles.put(PERMISSION_ENCRYPT_PROFILE_DATA, new String[]{UnomiRoles.PROFILE_ENCRYPT});
        permissionRoles.put(PERMISSION_DECRYPT_PROFILE_DATA, new String[]{UnomiRoles.PROFILE_DECRYPT});
        permissionRoles.put(PERMISSION_SCHEMA_WRITE, new String[]{UnomiRoles.TENANT_ADMINISTRATOR});
        permissionRoles.put(PERMISSION_SCHEMA_DELETE, new String[]{UnomiRoles.TENANT_ADMINISTRATOR});
        defaultRole = UnomiRoles.USER;
    }

    public Map<String, String[]> getPermissionRoles() {
        return permissionRoles;
    }

    public void setPermissionRoles(Map<String, String[]> permissionRoles) {
        this.permissionRoles = permissionRoles;
    }

    public String getDefaultRole() {
        return defaultRole;
    }

    public void setDefaultRole(String defaultRole) {
        this.defaultRole = defaultRole;
    }

    /**
     * Get required roles for an permission
     * @param permission the permission to check
     * @return array of required roles, or array containing default role if permission not mapped
     */
    public String[] getRequiredRolesForPermission(String permission) {
        return permissionRoles.getOrDefault(permission, new String[]{defaultRole});
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
