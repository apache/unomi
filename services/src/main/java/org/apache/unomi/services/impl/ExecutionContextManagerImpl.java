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
import org.apache.unomi.api.ExecutionContext;
import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.api.security.TenantPrincipal;
import org.apache.unomi.api.security.UnomiRoles;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.Subject;
import java.security.AccessController;
import java.security.Principal;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class ExecutionContextManagerImpl implements ExecutionContextManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionContextManagerImpl.class);

    private final ThreadLocal<ExecutionContext> currentContext = new ThreadLocal<>();
    private SecurityService securityService;

    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public ExecutionContext getCurrentContext() {
        ExecutionContext context = currentContext.get();
        if (context == null) {
            context = createContext(securityService.getCurrentSubject());
            currentContext.set(context);

        }
        return context;
    }

    @Override
    public void setCurrentContext(ExecutionContext context) {
        if (context == null) {
            currentContext.remove();
        } else {
            currentContext.set(context);
        }
    }

    @Override
    public <T> T executeAsSystem(Supplier<T> operation) {
        ExecutionContext previousContext = currentContext.get();
        Subject previousSubject = securityService.getCurrentSubject();
        try {
            if (operation == null) {
                throw new IllegalArgumentException("System operation cannot be null");
            }

            Subject systemSubject = securityService.getSystemSubject();
            if (systemSubject == null) {
                throw new SecurityException("Failed to obtain system subject");
            }

            securityService.setCurrentSubject(systemSubject);
            Set<String> roles = securityService.extractRolesFromSubject(systemSubject);
            if (!roles.contains(UnomiRoles.ADMINISTRATOR)) {
                throw new SecurityException("System subject does not have required administrator role");
            }

            Set<String> permissions = getPermissionsForRoles(roles);
            ExecutionContext systemContext = new ExecutionContext(
                    ExecutionContext.SYSTEM_TENANT,
                    roles,
                    permissions
            );
            currentContext.set(systemContext);

            try {
                return operation.get();
            } catch (Exception e) {
                LOGGER.error("Error executing system operation: {}", e.getMessage(), e);
                throw e;
            }
        } finally {
            try {
                if (previousContext != null) {
                    currentContext.set(previousContext);
                } else {
                    currentContext.remove();
                }
                securityService.setCurrentSubject(previousSubject);
            } catch (Exception e) {
                LOGGER.error("Error restoring previous context: {}", e.getMessage(), e);
                // Still throw the error to ensure it's not silently ignored
                throw new SecurityException("Failed to restore security context", e);
            }
        }
    }

    @Override
    public void executeAsSystem(Runnable operation) {
        executeAsSystem(() -> {
            operation.run();
            return null;
        });
    }

    @Override
    public ExecutionContext createContext(String tenantId) {
        Subject subject = securityService.getCurrentSubject();
        Set<String> roles = securityService.extractRolesFromSubject(subject);
        Set<String> permissions = getPermissionsForRoles(roles);
        return new ExecutionContext(tenantId, roles, permissions);
    }

    @Override
    public <T> T executeAsTenant(String tenantId, Supplier<T> operation) {
        ExecutionContext previousContext = currentContext.get();
        try {
            ExecutionContext tenantContext = createContext(tenantId);
            currentContext.set(tenantContext);
            return operation.get();
        } finally {
            if (previousContext != null) {
                currentContext.set(previousContext);
            } else {
                currentContext.remove();
            }
        }
    }

    @Override
    public void executeAsTenant(String tenantId, Runnable operation) {
        executeAsTenant(tenantId, () -> {
            operation.run();
            return null;
        });
    }

    private Set<String> getCurrentRoles() {
        Set<String> roles = new HashSet<>();
        Subject subject = Subject.getSubject(AccessController.getContext());
        if (subject != null) {
            for (Principal principal : subject.getPrincipals()) {
                if (principal instanceof RolePrincipal) {
                    roles.add(principal.getName());
                }
            }
        }
        return roles;
    }

    private Set<String> getPermissionsForRoles(Set<String> roles) {
        Set<String> permissions = new HashSet<>();
        for (String role : roles) {
            permissions.addAll(securityService.getPermissionsForRole(role));
        }
        return permissions;
    }

    private ExecutionContext createContext(Subject subject) {
        String tenantId = ExecutionContext.SYSTEM_TENANT;
        if (subject != null) {
            for (Principal principal : subject.getPrincipals()) {
                if (principal instanceof TenantPrincipal) {
                    tenantId = ((TenantPrincipal) principal).getName();
                    break;
                }
            }
        }
        Set<String> roles = securityService.extractRolesFromSubject(subject);
        Set<String> permissions = getPermissionsForRoles(roles);
        return new ExecutionContext(tenantId, roles, permissions);
    }

}
