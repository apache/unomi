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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.security.auth.Subject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ExecutionContextManagerImplTest {

    @Mock
    private SecurityService securityService;

    private ExecutionContextManagerImpl contextManager;

    @Before
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
        assertEquals("Roles should match", roles, context.getRoles());
        Set<String> expectedPermissions = new HashSet<>(Arrays.asList("READ", "WRITE", SecurityServiceConfiguration.PERMISSION_DELETE));
        assertEquals("Permissions should be aggregated", expectedPermissions, context.getPermissions());

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
            assertTrue("Should have admin role", context.hasRole(UnomiRoles.ADMINISTRATOR));
            assertTrue("Should have admin permission", context.hasPermission("ADMIN"));
            return "success";
        });

        assertEquals("Operation should execute successfully", "success", result);

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
            assertEquals("Should have correct tenant", "testTenant", context.getTenantId());
            assertTrue("Should have user role", context.hasRole(UnomiRoles.USER));
            assertTrue("Should have read permission", context.hasPermission("READ"));
            return "success";
        });

        assertEquals("Operation should execute successfully", "success", result);

        // Verify security service interactions
        verify(securityService).getCurrentSubject();
        verify(securityService).extractRolesFromSubject(subject);
        verify(securityService).getPermissionsForRole(UnomiRoles.USER);
    }
}
