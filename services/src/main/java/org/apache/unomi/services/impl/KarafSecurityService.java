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
package org.apache.unomi.services.impl;

import org.apache.karaf.jaas.boot.principal.RolePrincipal;
import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.api.tenants.TenantAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.Subject;
import java.security.AccessController;
import java.security.Principal;
import java.util.HashSet;
import java.util.Set;

public class KarafSecurityService implements SecurityService {
    private static final Logger logger = LoggerFactory.getLogger(KarafSecurityService.class);

    private static final String ROLE_ADMIN = "unomi-admin";
    private static final String ROLE_USER = "unomi-user";

    private TenantAuditService tenantAuditService;

    public void setTenantAuditService(TenantAuditService tenantAuditService) {
        this.tenantAuditService = tenantAuditService;
    }

    @Override
    public boolean hasRole(String role) {
        Subject subject = getCurrentSubject();
        if (subject == null) {
            return false;
        }

        Set<Principal> principals = subject.getPrincipals();
        for (Principal principal : principals) {
            if (principal instanceof RolePrincipal && ((RolePrincipal) principal).getName().equals(role)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isAdmin() {
        return hasRole(ROLE_ADMIN);
    }

    @Override
    public boolean hasTenantAccess(String tenantId) {
        if (isAdmin()) {
            return true;
        }

        Principal principal = getCurrentPrincipal();
        if (principal == null) {
            return false;
        }

        return hasRole("tenant-" + tenantId) || hasRole(ROLE_USER);
    }

    @Override
    public Subject getCurrentSubject() {
        return Subject.getSubject(AccessController.getContext());
    }

    @Override
    public Principal getCurrentPrincipal() {
        Subject subject = getCurrentSubject();
        if (subject != null) {
            Set<Principal> principals = subject.getPrincipals();
            if (!principals.isEmpty()) {
                return principals.iterator().next();
            }
        }
        return null;
    }

    @Override
    public void validateTenantOperation(String tenantId, String operation) throws SecurityException {
        if (!hasTenantAccess(tenantId)) {
            throw new SecurityException("User does not have access to tenant: " + tenantId);
        }

        if (!hasPermission(operation)) {
            throw new SecurityException("User does not have permission to perform operation: " + operation);
        }
        tenantAuditService.logTenantOperation(tenantId, operation);
    }

    @Override
    public boolean hasPermission(String operation) {
        if (isAdmin()) {
            return true;
        }

        Set<String> requiredRoles = getRequiredRolesForOperation(operation);
        for (String role : requiredRoles) {
            if (hasRole(role)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String getCurrentTenantId() {
        Subject subject = getCurrentSubject();
        if (subject != null) {
            Set<Principal> principals = subject.getPrincipals();
            if (!principals.isEmpty()) {
                return principals.iterator().next().getName();
            }
        }
        return null;
    }

    @Override
    public byte[] getTenantEncryptionKey(String tenantId) {
        // @TODO implement this
        return new byte[0];
    }

    private Set<String> getRequiredRolesForOperation(String operation) {
        Set<String> roles = new HashSet<>();

        switch (operation) {
            case "QUERY":
            case "SCROLL_QUERY":
                roles.add(ROLE_USER);
                break;
            case "SAVE":
            case "UPDATE":
            case "DELETE":
                roles.add(ROLE_USER);
                break;
            case "PURGE":
            case "REMOVE_BY_QUERY":
            case "AGGREGATE":
                roles.add(ROLE_ADMIN);
                break;
            default:
                roles.add(ROLE_ADMIN);
        }

        return roles;
    }
} 