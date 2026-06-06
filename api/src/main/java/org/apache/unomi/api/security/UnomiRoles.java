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

/**
 * Constants for roles in Unomi.
 */
public final class UnomiRoles {

    private UnomiRoles() {
        // Prevent instantiation
    }

    /**
     * Role for administrators with full system access
     */
    public static final String ADMINISTRATOR = "ROLE_UNOMI_ADMIN";

    /**
     * Role for tenant administrators
     */
    public static final String TENANT_ADMINISTRATOR = "ROLE_UNOMI_TENANT_ADMIN";

    /**
     * Role for regular users
     */
    public static final String USER = "ROLE_UNOMI_TENANT_USER";

    /**
     * Role for anonymous users
     */
    public static final String ANONYMOUS = "ROLE_UNOMI_ANONYMOUS";

    /**
     * Role for system-level operations
     */
    public static final String SYSTEM = "ROLE_UNOMI_SYSTEM";

    /**
     * Role for public tenant access
     */
    public static final String TENANT_PUBLIC = "ROLE_UNOMI_TENANT_PUBLIC";

    /**
     * Role for private tenant access
     */
    public static final String TENANT_PRIVATE = "ROLE_UNOMI_TENANT_PRIVATE";

    /**
     * Prefix for tenant-specific user roles
     */
    public static final String TENANT_USER_PREFIX = "ROLE_UNOMI_TENANT_USER_";

    /**
     * Prefix for tenant-specific admin roles
     */
    public static final String TENANT_ADMIN_PREFIX = "ROLE_UNOMI_TENANT_ADMIN_";

    /**
     * Role for profile encryption operations
     */
    public static final String PROFILE_ENCRYPT = "ROLE_UNOMI_PROFILE_ENCRYPT";

    /**
     * Role for profile decryption operations
     */
    public static final String PROFILE_DECRYPT = "ROLE_UNOMI_PROFILE_DECRYPT";

    /**
     * Permission for system maintenance operations
     */
    public static final String SYSTEM_MAINTENANCE = "ROLE_SYSTEM_MAINTENANCE";

    /**
     * Role for guest access
     */
    public static final String GUEST = "ROLE_UNOMI_GUEST";

    /**
     * Role for public API access
     */
    public static final String PUBLIC = "ROLE_UNOMI_PUBLIC";

    /**
     * Role for system operations
     */
    public static final String SYSTEM_OPERATIONS = "ROLE_UNOMI_SYSTEM_OPERATIONS";

    /**
     * Role for tenant operations
     */
    public static final String TENANT_OPERATIONS = "ROLE_UNOMI_TENANT_OPERATIONS";

    /**
     * Role for profile operations
     */
    public static final String PROFILE_OPERATIONS = "ROLE_UNOMI_PROFILE_OPERATIONS";

}
