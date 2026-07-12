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
 * Settings that control authentication, authorization, and API access.
 * Loaded at startup to configure how the security layer validates users,
 * roles, and tenant principals.
 */
public class SecurityServiceConfiguration {
    /**
     * Defines the permission key used when querying items, which is also
     * utilized to validate the tenant ID scope for query operations.
     */
    public static final String PERMISSION_QUERY = "QUERY";
    /**
     * Defines the permission key required for performing aggregations on item
     * data, and is used internally to validate the tenant ID scope during
     * aggregation queries.
     */
    public static final String PERMISSION_AGGREGATE = "AGGREGATE";
    /**
     * Defines the permission key necessary for continuing or
     * initiating a scroll query.
     * This constant is used to validate the tenant ID when
     * executing scroll operations.
     */
    public static final String PERMISSION_SCROLL_QUERY = "SCROLL_QUERY";
    /**
     * Defines the permission key required for saving new items. It is also used
     * internally to ensure that the correct tenant ID is set
     * during save operations.
     */
    public static final String PERMISSION_SAVE = "SAVE";
    /**
     * Defines the permission key necessary for updating existing items. This
     * constant is utilized to validate the tenant ID scope when
     * performing item updates.
     */
    public static final String PERMISSION_UPDATE = "UPDATE";
    /**
     * Defines the permission key required for deleting items. It is used both
     * in defining default roles and internally to validate the tenant ID during
     * deletion operations.
     */
    public static final String PERMISSION_DELETE = "DELETE";
    /**
     * Defines the permission key needed when removing multiple items
     * using a query condition.
     * This constant is used to validate the tenant ID scope for remove
     * by query operations.
     */
    public static final String PERMISSION_REMOVE_BY_QUERY = "REMOVE_BY_QUERY";
    /**
     * Defines the permission key required for purging data. It is utilized both
     * in defining default roles and internally to validate the tenant ID
     * during purge operations.
     */
    public static final String PERMISSION_PURGE = "PURGE";
    /**
     * Defines the permission key required for performing system
     * maintenance operations.
     */
    public static final String PERMISSION_SYSTEM_MAINTENANCE = "SYSTEM_MAINTENANCE";
    /**
     * Defines the permission key needed when profile data
     * encryption is required.
     */
    public static final String PERMISSION_ENCRYPT_PROFILE_DATA = "ENCRYPT_PROFILE_DATA";
    /**
     * Defines the permission key needed when profile data decryption is
     * required. This constant is used to validate the tenant ID scope
     * for remove operations.
     */
    public static final String PERMISSION_DECRYPT_PROFILE_DATA = "DECRYPT_PROFILE_DATA";
    /**
     * Defines the permission key required for writing or modifying schemas.
     * This constant is used to validate access during schema write operations.
     */
    public static final String PERMISSION_SCHEMA_WRITE = "SCHEMA_WRITE";
    /**
     * Defines the permission key required for deleting schemas. This constant
     * is used to validate access during schema delete operations.
     */
    public static final String PERMISSION_SCHEMA_DELETE = "SCHEMA_DELETE";

    private Map<String, String[]> permissionRoles;
    private String defaultRole;
    private Set<String> systemRoles = new HashSet<>();
    private boolean enableEncryption = false;

    /**
     * Initializes default system roles and permission-to-role mappings.
     */
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

    /**
     * Permission keys mapped to the roles allowed to perform them.
     *
     * @return the permission-to-roles map
     */
    public Map<String, String[]> getPermissionRoles() {
        return permissionRoles;
    }

    /**
     * Replaces the full permission-to-role mapping.
     *
     * @param permissionRoles the permission-to-roles map
     */
    public void setPermissionRoles(Map<String, String[]> permissionRoles) {
        this.permissionRoles = permissionRoles;
    }

    /**
     * Role assigned when no explicit mapping exists.
     *
     * @return the default role name
     */
    public String getDefaultRole() {
        return defaultRole;
    }

    /**
     * Sets the default role name.
     *
     * @param defaultRole the default role
     */
    public void setDefaultRole(String defaultRole) {
        this.defaultRole = defaultRole;
    }

    /**
     * Roles required for the given permission.
     *
     * @param permission the permission key
     * @return the required roles, or an array containing the default role if unmapped
     */
    public String[] getRequiredRolesForPermission(String permission) {
        return permissionRoles.getOrDefault(permission, new String[]{defaultRole});
    }

    /**
     * Platform roles recognized by the security service.
     *
     * @return the system role names
     */
    public Set<String> getSystemRoles() {
        return systemRoles;
    }

    /**
     * Replaces the set of recognized system roles.
     *
     * @param systemRoles the system role names
     */
    public void setSystemRoles(Set<String> systemRoles) {
        this.systemRoles = systemRoles;
    }

    /**
     * Adds a system role name.
     *
     * @param role the role to add
     */
    public void addSystemRole(String role) {
        systemRoles.add(role);
    }

    /**
     * Removes a system role name.
     *
     * @param role the role to remove
     */
    public void removeSystemRole(String role) {
        systemRoles.remove(role);
    }

    /**
     * Whether profile data encryption is enabled.
     *
     * @return {@code true} if encryption is enabled
     */
    public boolean isEnableEncryption() {
        return enableEncryption;
    }

    /**
     * Enables or disables profile data encryption.
     *
     * @param enableEncryption {@code true} to enable encryption
     */
    public void setEnableEncryption(boolean enableEncryption) {
        this.enableEncryption = enableEncryption;
    }

}
