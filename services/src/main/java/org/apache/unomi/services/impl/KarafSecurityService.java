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
import org.apache.karaf.jaas.boot.principal.UserPrincipal;
import org.apache.unomi.api.security.*;
import org.apache.unomi.api.tenants.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.Subject;
import java.security.AccessController;
import java.security.Principal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class KarafSecurityService implements SecurityService {
    private static final Logger LOGGER = LoggerFactory.getLogger(KarafSecurityService.class);

    public static final String SYSTEM_TENANT = "system";
    private final Subject SYSTEM_SUBJECT;

    private SecurityServiceConfiguration configuration;
    private EncryptionService encryptionService;
    private AuditService tenantAuditService;

    private final ThreadLocal<Subject> currentSubject = new ThreadLocal<>();
    private final ThreadLocal<Subject> privilegedSubject = new ThreadLocal<>();

    public KarafSecurityService() {
        SYSTEM_SUBJECT = createSystemSubject();
    }

    private Subject createSystemSubject() {
        Subject subject = new Subject();
        subject.getPrincipals().add(new UserPrincipal("system"));
        subject.getPrincipals().add(new RolePrincipal(UnomiRoles.ADMINISTRATOR));
        subject.getPrincipals().add(new RolePrincipal(UnomiRoles.TENANT_ADMINISTRATOR));
        subject.getPrincipals().add(new RolePrincipal(UnomiRoles.SYSTEM_MAINTENANCE));
        return subject;
    }

    public void init() {
        if (configuration == null) {
            configuration = new SecurityServiceConfiguration();
        }
        updateSystemSubject();
    }

    public void destroy() {
        // Cleanup
    }

    private void updateSystemSubject() {
        SYSTEM_SUBJECT.getPrincipals().clear();
        SYSTEM_SUBJECT.getPrincipals().add(new TenantPrincipal(SYSTEM_TENANT));
        SYSTEM_SUBJECT.getPrincipals().add(new UserPrincipal("system"));
        for (String role : configuration.getSystemRoles()) {
            SYSTEM_SUBJECT.getPrincipals().add(new RolePrincipal(role));
        }
    }

    public void setTenantAuditService(AuditService tenantAuditService) {
        this.tenantAuditService = tenantAuditService;
    }

    public void setConfiguration(SecurityServiceConfiguration configuration) {
        this.configuration = configuration;
    }

    public void bindEncryptionService(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    public void unbindEncryptionService(EncryptionService encryptionService) {
        this.encryptionService = null;
    }

    @Override
    public Subject getCurrentSubject() {
        // First check JAAS context
        Subject jaasSubject = Subject.getSubject(AccessController.getContext());
        if (jaasSubject != null) {
            return jaasSubject;
        }

        // Then check privileged subject
        Subject privSubject = privilegedSubject.get();
        if (privSubject != null) {
            return privSubject;
        }

        // Finally return current request subject
        return currentSubject.get();
    }

    @Override
    public Principal getCurrentPrincipal() {
        Subject subject = getCurrentSubject();
        return subject != null ? getFirstPrincipal(subject) : null;
    }

    @Override
    public void setCurrentSubject(Subject subject) {
        currentSubject.set(subject);
    }

    @Override
    public void clearCurrentSubject() {
        currentSubject.remove();
        privilegedSubject.remove();
    }

    /**
     * Sets a temporary privileged subject for operations that require elevated permissions.
     * This subject will be used in addition to the current subject for permission checks.
     *
     * @param subject the privileged subject to set
     */
    public void setPrivilegedSubject(Subject subject) {
        privilegedSubject.set(subject);
    }

    /**
     * Clears the temporary privileged subject.
     */
    public void clearPrivilegedSubject() {
        privilegedSubject.remove();
    }

    @Override
    public boolean hasRole(String role) {
        // Check JAAS context first
        Subject jaasSubject = Subject.getSubject(AccessController.getContext());
        if (jaasSubject != null && hasRoleInSubject(jaasSubject, role)) {
            return true;
        }

        // Then check privileged subject
        Subject privileged = privilegedSubject.get();
        if (privileged != null && hasRoleInSubject(privileged, role)) {
            return true;
        }

        // Finally check current subject
        Subject current = currentSubject.get();
        return current != null && hasRoleInSubject(current, role);
    }

    @Override
    public boolean isAdmin() {
        return hasRole(UnomiRoles.ADMINISTRATOR);
    }

    @Override
    public boolean hasSystemAccess() {
        return hasRole(UnomiRoles.ADMINISTRATOR) || hasRole(UnomiRoles.TENANT_ADMINISTRATOR);
    }

    @Override
    public boolean hasTenantAccess(String tenantId) {
        if (hasRole(UnomiRoles.TENANT_ADMINISTRATOR)) {
            return true;
        }
        return hasSystemAccess();
    }

    @Override
    public boolean hasPermission(String permission) {
        // First check JAAS context
        Subject jaasSubject = Subject.getSubject(AccessController.getContext());
        if (jaasSubject != null && hasPermissionInSubject(jaasSubject, permission)) {
            return true;
        }

        // Then check privileged subject
        Subject privSubject = privilegedSubject.get();
        if (privSubject != null && hasPermissionInSubject(privSubject, permission)) {
            return true;
        }

        // Finally check current subject
        Subject subject = currentSubject.get();
        return subject != null && hasPermissionInSubject(subject, permission);
    }

    private boolean hasRoleInSubject(Subject subject, String role) {
        return subject.getPrincipals(RolePrincipal.class).stream()
                .anyMatch(p -> p.getName().equals(role));
    }

    private boolean hasPermissionInSubject(Subject subject, String permission) {
        Set<String> roles = extractRolesFromSubject(subject);
        String[] requiredRoles = configuration.getRequiredRolesForOperation(permission);

        return requiredRoles != null &&
               roles.stream().anyMatch(role -> Arrays.asList(requiredRoles).contains(role));
    }

    @Override
    public void auditTenantOperation(String tenantId, String operation) {
        tenantAuditService.logTenantOperation(tenantId, operation);
    }

    private Principal getFirstPrincipal(Subject subject) {
        if (subject == null) {
            return null;
        }
        Set<Principal> principals = subject.getPrincipals();
        if (principals == null || principals.isEmpty()) {
            return null;
        }
        return principals.iterator().next();
    }

    @Override
    public void executeWithPrivilegedSubject(Subject subject, Runnable operation) {
        Subject oldPrivileged = privilegedSubject.get();
        try {
            privilegedSubject.set(subject);
            operation.run();
        } finally {
            if (oldPrivileged != null) {
                privilegedSubject.set(oldPrivileged);
            } else {
                privilegedSubject.remove();
            }
        }
    }

    @Override
    public String getCurrentSubjectTenantId() {
        Subject subject = getCurrentSubject();
        if (subject != null) {
            Set<TenantPrincipal> tenantPrincipals = subject.getPrincipals(TenantPrincipal.class);
            if (!tenantPrincipals.isEmpty()) {
                return tenantPrincipals.iterator().next().getTenantId();
            }
        }
        return SYSTEM_TENANT;
    }

    @Override
    public boolean isOperatingOnSystemTenant() {
        return false;
    }

    @Override
    public byte[] getTenantEncryptionKey(String tenantId) {
        if (encryptionService != null) {
            return encryptionService.getTenantEncryptionKey(tenantId);
        } else {
            return null;
        }
    }

    @Override
    public Subject getSystemSubject() {
        return SYSTEM_SUBJECT;
    }

    @Override
    public Set<String> extractRolesFromSubject(Subject subject) {
        if (subject == null) {
            return new HashSet<>();
        }
        return subject.getPrincipals(RolePrincipal.class).stream()
                .map(RolePrincipal::getName)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<String> getPermissionsForRole(String role) {
        if (configuration == null || configuration.getOperationRoles() == null) {
            return new HashSet<>();
        }

        Set<String> permissions = new HashSet<>();
        Map<String, String[]> operationRoles = configuration.getOperationRoles();

        // Iterate through all operations and check if the role is allowed
        for (Map.Entry<String, String[]> entry : operationRoles.entrySet()) {
            String operation = entry.getKey();
            String[] allowedRoles = entry.getValue();

            if (Arrays.asList(allowedRoles).contains(role)) {
                permissions.add(operation);
            }
        }

        return permissions;
    }

    @Override
    public SecurityServiceConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public Subject createSubject(String tenantId, boolean isPrivate) {
        Subject subject = new Subject();
        subject.getPrincipals().add(new TenantPrincipal(tenantId));
        subject.getPrincipals().add(new UserPrincipal(tenantId));
        if (isPrivate) {
            subject.getPrincipals().add(new RolePrincipal(UnomiRoles.TENANT_ADMINISTRATOR));
            subject.getPrincipals().add(new RolePrincipal(UnomiRoles.TENANT_ADMIN_PREFIX + tenantId));
            subject.getPrincipals().add(new RolePrincipal(UnomiRoles.USER));
            subject.getPrincipals().add(new RolePrincipal(UnomiRoles.TENANT_USER_PREFIX + tenantId));
        } else {
            subject.getPrincipals().add(new RolePrincipal(UnomiRoles.USER));
            subject.getPrincipals().add(new RolePrincipal(UnomiRoles.TENANT_USER_PREFIX + tenantId));
        }
        return subject;
    }
}
