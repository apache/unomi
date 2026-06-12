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

import org.apache.unomi.api.ExecutionContext;
import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.api.security.SecurityServiceConfiguration;
import org.apache.unomi.api.security.UnomiRoles;
import org.apache.unomi.services.common.security.ExecutionContextManagerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.security.auth.Subject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ExecutionContextManagerImplTest {

    @Mock
    private SecurityService securityService;

    private ExecutionContextManagerImpl contextManager;

    @BeforeEach
    public void setUp() {
        contextManager = new ExecutionContextManagerImpl();
        contextManager.setSecurityService(securityService);
    }

    @Test
    public void testCreateContextWithPermissions() {
        // Set up test data
        Subject subject = new Subject();
        Set<String> roles = new HashSet<>(Arrays.asList(UnomiRoles.ADMINISTRATOR, UnomiRoles.USER));

        // Mock security service behavior
        when(securityService.getCurrentSubject()).thenReturn(subject);
        when(securityService.extractRolesFromSubject(subject)).thenReturn(roles);
        when(securityService.getPermissionsForRole(UnomiRoles.ADMINISTRATOR))
            .thenReturn(new HashSet<>(Arrays.asList("READ", "WRITE", SecurityServiceConfiguration.PERMISSION_DELETE)));
        when(securityService.getPermissionsForRole(UnomiRoles.USER))
            .thenReturn(new HashSet<>(Arrays.asList("READ")));

        // Create context
        ExecutionContext context = contextManager.createContext("testTenant");

        // Verify roles and permissions
        assertEquals(roles, context.getRoles(), "Roles should match");
        Set<String> expectedPermissions = new HashSet<>(Arrays.asList("READ", "WRITE", SecurityServiceConfiguration.PERMISSION_DELETE));
        assertEquals(expectedPermissions, context.getPermissions(), "Permissions should be aggregated");

        // Verify security service interactions
        verify(securityService).getCurrentSubject();
        verify(securityService).extractRolesFromSubject(subject);
        verify(securityService).getPermissionsForRole(UnomiRoles.ADMINISTRATOR);
        verify(securityService).getPermissionsForRole(UnomiRoles.USER);
    }

    @Test
    public void testExecuteAsSystem() {
        // Set up test data
        Subject systemSubject = new Subject();
        Set<String> systemRoles = new HashSet<>(Arrays.asList(UnomiRoles.ADMINISTRATOR));
        Set<String> systemPermissions = new HashSet<>(Arrays.asList("READ", "WRITE", SecurityServiceConfiguration.PERMISSION_DELETE, "ADMIN"));

        // Mock security service behavior
        when(securityService.getSystemSubject()).thenReturn(systemSubject);
        when(securityService.extractRolesFromSubject(systemSubject)).thenReturn(systemRoles);
        when(securityService.getPermissionsForRole(UnomiRoles.ADMINISTRATOR)).thenReturn(systemPermissions);

        // Execute system operation
        String result = contextManager.executeAsSystem(() -> {
            ExecutionContext context = contextManager.getCurrentContext();
            assertTrue(context.hasRole(UnomiRoles.ADMINISTRATOR), "Should have admin role");
            assertTrue(context.hasPermission("ADMIN"), "Should have admin permission");
            return "success";
        });

        assertEquals("success", result, "Operation should execute successfully");

        // Verify security service interactions
        verify(securityService).getSystemSubject();
        verify(securityService).extractRolesFromSubject(systemSubject);
        verify(securityService).getPermissionsForRole(UnomiRoles.ADMINISTRATOR);
    }

    @Test
    public void testExecuteAsTenant() {
        // Set up test data
        Subject subject = new Subject();
        Set<String> roles = new HashSet<>(Arrays.asList(UnomiRoles.USER));
        Set<String> permissions = new HashSet<>(Arrays.asList("READ"));

        // Mock security service behavior
        when(securityService.getCurrentSubject()).thenReturn(subject);
        when(securityService.extractRolesFromSubject(subject)).thenReturn(roles);
        when(securityService.getPermissionsForRole(UnomiRoles.USER)).thenReturn(permissions);

        // Execute tenant operation
        String result = contextManager.executeAsTenant("testTenant", () -> {
            ExecutionContext context = contextManager.getCurrentContext();
            assertEquals("testTenant", context.getTenantId(), "Should have correct tenant");
            assertTrue(context.hasRole(UnomiRoles.USER), "Should have user role");
            assertTrue(context.hasPermission("READ"), "Should have read permission");
            return "success";
        });

        assertEquals("success", result, "Operation should execute successfully");

        // Verify security service interactions
        verify(securityService).getCurrentSubject();
        verify(securityService).extractRolesFromSubject(subject);
        verify(securityService).getPermissionsForRole(UnomiRoles.USER);
    }
}
