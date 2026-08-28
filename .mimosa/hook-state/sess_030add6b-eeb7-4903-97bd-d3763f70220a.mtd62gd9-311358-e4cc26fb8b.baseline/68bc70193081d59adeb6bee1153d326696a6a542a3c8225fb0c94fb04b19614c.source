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
package org.apache.unomi.api.tenants.security;

import java.util.List;
import java.util.Map;

/**
 * Tenant-level security policy loaded and enforced by {@link TenantSecurityService}.
 * Groups authentication rules (token/session settings), authorization mappings,
 * rate limits, and API access constraints that apply to REST calls for one tenant.
 */
public class SecuritySettings {
    private boolean enabled;
    private AuthenticationConfig authentication;
    private AuthorizationConfig authorization;
    private Map<String, Object> additionalSettings;

    /**
     * Whether tenant security enforcement is enabled.
     *
     * @return {@code true} when security is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables tenant security enforcement.
     *
     * @param enabled {@code true} to enable security
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Authentication policy for this tenant.
     *
     * @return authentication configuration
     */
    public AuthenticationConfig getAuthentication() {
        return authentication;
    }

    /**
     * Sets the authentication policy for this tenant.
     *
     * @param authentication authentication configuration
     */
    public void setAuthentication(AuthenticationConfig authentication) {
        this.authentication = authentication;
    }

    /**
     * Authorization policy for this tenant.
     *
     * @return authorization configuration
     */
    public AuthorizationConfig getAuthorization() {
        return authorization;
    }

    /**
     * Sets the authorization policy for this tenant.
     *
     * @param authorization authorization configuration
     */
    public void setAuthorization(AuthorizationConfig authorization) {
        this.authorization = authorization;
    }

    /**
     * Extra security settings not covered by authentication or authorization.
     *
     * @return additional settings map
     */
    public Map<String, Object> getAdditionalSettings() {
        return additionalSettings;
    }

    /**
     * Sets extra security settings not covered by authentication or authorization.
     *
     * @param additionalSettings additional settings map
     */
    public void setAdditionalSettings(Map<String, Object> additionalSettings) {
        this.additionalSettings = additionalSettings;
    }

    /**
     * Configuration for authentication settings.
     */
    public static class AuthenticationConfig {
        private List<String> allowedAuthMethods;
        private int maxLoginAttempts;
        private int lockoutDurationMinutes;
        private boolean requireMfa;

        /**
         * Gets the list of allowed authentication methods.
         * @return A {@link java.util.List} of {@link String} representing the
         * allowed auth methods.
         */
        public List<String> getAllowedAuthMethods() {
            return allowedAuthMethods;
        }

        /**
         * Sets the list of allowed authentication methods for the tenant.
         * @param allowedAuthMethods The list of allowed authentication methods.
         */
        public void setAllowedAuthMethods(List<String> allowedAuthMethods) {
            this.allowedAuthMethods = allowedAuthMethods;
        }

        /**
         * Maximum failed login attempts before account lockout.
         *
         * @return max login attempts
         */
        public int getMaxLoginAttempts() {
            return maxLoginAttempts;
        }

        /**
         * Sets the maximum number of consecutive failed login attempts allowed.
         * @param maxLoginAttempts The maximum number of login attempts.
         */
        public void setMaxLoginAttempts(int maxLoginAttempts) {
            this.maxLoginAttempts = maxLoginAttempts;
        }

        /**
         * Gets the duration, in minutes, that an account remains locked after
         * exceeding login attempts.
         * @return The lockout duration in minutes.
         */
        public int getLockoutDurationMinutes() {
            return lockoutDurationMinutes;
        }

        /**
         * Sets the duration (in minutes) for which a user account will be
         * locked out upon failed logins.
         * @param lockoutDurationMinutes The desired lockout
         * duration in minutes.
         */
        public void setLockoutDurationMinutes(int lockoutDurationMinutes) {
            this.lockoutDurationMinutes = lockoutDurationMinutes;
        }

        /**
         * Checks if multi-factor authentication is required for login.
         * @return {@code true} if MFA is required, {@code false} otherwise.
         */
        public boolean isRequireMfa() {
            return requireMfa;
        }

        /**
         * Sets whether multi-factor authentication must be used during login.
         * @param requireMfa If {@code true}, MFA is mandatory;
         * otherwise, it is optional.
         */
        public void setRequireMfa(boolean requireMfa) {
            this.requireMfa = requireMfa;
        }
    }

    /**
     * Configuration for authorization settings.
     */
    public static class AuthorizationConfig {
        private List<String> roles;
        private List<String> permissions;
        private Map<String, List<String>> rolePermissions;

        /**
         * Roles defined for this tenant.
         *
         * @return role names
         */
        public List<String> getRoles() {
            return roles;
        }

        /**
         * Sets the list of roles for this tenant.
         * @param roles The list of role names to set.
         */
        public void setRoles(List<String> roles) {
            this.roles = roles;
        }

        /**
         * Permissions defined for this tenant.
         *
         * @return permission names
         */
        public List<String> getPermissions() {
            return permissions;
        }

        /**
         * Sets the list of permissions for this tenant.
         * @param permissions The list of permission names to set.
         */
        public void setPermissions(List<String> permissions) {
            this.permissions = permissions;
        }

        /**
         * Maps each role to the permissions it grants.
         *
         * @return role-to-permissions map
         */
        public Map<String, List<String>> getRolePermissions() {
            return rolePermissions;
        }

        /**
         * Sets the mapping defining which permissions belong to specific roles.
         * @param rolePermissions The map containing
         * role-to-permission mappings to set.
         */
        public void setRolePermissions(Map<String, List<String>> rolePermissions) {
            this.rolePermissions = rolePermissions;
        }
    }
}