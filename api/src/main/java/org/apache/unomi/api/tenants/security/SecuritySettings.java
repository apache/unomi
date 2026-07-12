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
 * Represents security settings for a tenant.
 * This class contains configuration for various security aspects including
 * authentication, authorization, and API access.
 */
public class SecuritySettings {
    private boolean enabled;
    private AuthenticationConfig authentication;
    private AuthorizationConfig authorization;
    private Map<String, Object> additionalSettings;

    /**
     * Gets whether security is enabled for the tenant.
     * @return true if security is enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether security is enabled for the tenant.
     * @param enabled true to enable security, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Gets the authentication configuration.
     * @return the authentication configuration
     */
    public AuthenticationConfig getAuthentication() {
        return authentication;
    }

    /**
     * Sets the authentication configuration.
     * @param authentication the authentication configuration to set
     */
    public void setAuthentication(AuthenticationConfig authentication) {
        this.authentication = authentication;
    }

    /**
     * Gets the authorization configuration.
     * @return the authorization configuration
     */
    public AuthorizationConfig getAuthorization() {
        return authorization;
    }

    /**
     * Sets the authorization configuration.
     * @param authorization the authorization configuration to set
     */
    public void setAuthorization(AuthorizationConfig authorization) {
        this.authorization = authorization;
    }

    /**
     * Gets additional security settings as key-value pairs.
     * @return map of additional settings
     */
    public Map<String, Object> getAdditionalSettings() {
        return additionalSettings;
    }

    /**
     * Sets additional security settings as key-value pairs.
     * @param additionalSettings map of additional settings to set
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
         * Retrieves the maximum number of login attempts
         * permitted before lockout.
         * @return The maximum number of login attempts.
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
         * Retrieves the list of roles configured for this tenant.
         * @return A {@link java.util.List} of strings
         * representing the assigned roles.
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
         * Retrieves the list of permissions configured for this tenant.
         * @return A {@link java.util.List} of strings representing the
         * assigned permissions.
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
         * Retrieves the mapping of roles to their associated permissions.
         * @return A {@link java.util.Map} where keys are role names and values
         * are lists of permissions.
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