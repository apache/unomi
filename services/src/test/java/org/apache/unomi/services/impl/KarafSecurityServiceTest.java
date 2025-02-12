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
import org.apache.unomi.api.security.EncryptionService;
import org.apache.unomi.api.security.SecurityServiceConfiguration;
import org.apache.unomi.api.security.TenantPrincipal;
import org.apache.unomi.api.security.UnomiRoles;
import org.apache.unomi.api.tenants.AuditService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.security.auth.Subject;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class KarafSecurityServiceTest {

    private KarafSecurityService securityService;

    @Mock
    private AuditService auditService;

    @Mock
    private EncryptionService encryptionService;

    @Before
    public void setUp() {
        securityService = new KarafSecurityService();

        // Configure security service
        SecurityServiceConfiguration config = new SecurityServiceConfiguration();
        config.setSystemRoles(new HashSet<>(Arrays.asList(
            UnomiRoles.ADMINISTRATOR,
            UnomiRoles.TENANT_ADMINISTRATOR,
            UnomiRoles.SYSTEM_MAINTENANCE
        )));

        securityService.setConfiguration(config);
        securityService.setTenantAuditService(auditService);
        securityService.bindEncryptionService(encryptionService);
        securityService.init();
    }

    @After
    public void tearDown() {
        securityService.clearCurrentSubject();
        securityService.clearPrivilegedSubject();
    }

    @Test
    public void testGetSystemSubject() {
        Subject systemSubject = securityService.getSystemSubject();
        assertNotNull("System subject should not be null", systemSubject);

        Set<Principal> principals = systemSubject.getPrincipals();
        assertTrue("System subject should have UserPrincipal",
            principals.stream().anyMatch(p -> p instanceof UserPrincipal));
        assertTrue("System subject should have TenantPrincipal",
            principals.stream().anyMatch(p -> p instanceof TenantPrincipal));

        Set<String> roles = extractRoles(principals);
        assertTrue("System subject should have administrator role",
            roles.contains(UnomiRoles.ADMINISTRATOR));
        assertTrue("System subject should have tenant administrator role",
            roles.contains(UnomiRoles.TENANT_ADMINISTRATOR));
        assertTrue("System subject should have system maintenance role",
            roles.contains(UnomiRoles.SYSTEM_MAINTENANCE));
    }

    @Test
    public void testCurrentSubjectManagement() {
        // Test initial state
        assertNull("Initial current subject should be null", securityService.getCurrentSubject());

        // Test setting and getting current subject
        Subject testSubject = createTestSubject("testUser", "testRole");
        securityService.setCurrentSubject(testSubject);

        Subject currentSubject = securityService.getCurrentSubject();
        assertNotNull("Current subject should not be null after setting", currentSubject);
        assertEquals("Current subject should match set subject", testSubject, currentSubject);

        // Test clearing current subject
        securityService.clearCurrentSubject();
        assertNull("Current subject should be null after clearing", securityService.getCurrentSubject());
    }

    @Test
    public void testPrivilegedSubjectManagement() {
        // Set up a regular subject
        Subject regularSubject = createTestSubject("regularUser", "ROLE_USER");
        securityService.setCurrentSubject(regularSubject);

        // Set up a privileged subject
        Subject privilegedSubject = createTestSubject("adminUser", UnomiRoles.ADMINISTRATOR);
        securityService.setPrivilegedSubject(privilegedSubject);

        // Verify privileged subject takes precedence
        Subject currentSubject = securityService.getCurrentSubject();
        assertNotNull("Current subject should not be null", currentSubject);
        assertEquals("Privileged subject should be returned", privilegedSubject, currentSubject);

        // Clear privileged subject and verify regular subject is returned
        securityService.clearPrivilegedSubject();
        currentSubject = securityService.getCurrentSubject();
        assertEquals("Regular subject should be returned after clearing privileged", regularSubject, currentSubject);
    }

    @Test
    public void testGetCurrentPrincipal() {
        // Test with null subject
        assertNull("Principal should be null when no subject is set", securityService.getCurrentPrincipal());

        // Test with subject containing principals
        Subject subject = createTestSubject("testUser", "testRole");
        securityService.setCurrentSubject(subject);

        Principal principal = securityService.getCurrentPrincipal();
        assertNotNull("Principal should not be null", principal);
        assertTrue("Principal should be UserPrincipal", principal instanceof UserPrincipal);
        assertEquals("Principal name should match", "testUser", principal.getName());
    }

    @Test
    public void testRoleExtraction() {
        Subject subject = createTestSubject("testUser", UnomiRoles.ADMINISTRATOR, UnomiRoles.USER);
        Set<String> roles = securityService.extractRolesFromSubject(subject);

        assertNotNull("Extracted roles should not be null", roles);
        assertEquals("Should have extracted 2 roles", 2, roles.size());
        assertTrue("Should contain administrator role", roles.contains(UnomiRoles.ADMINISTRATOR));
        assertTrue("Should contain user role", roles.contains(UnomiRoles.USER));
    }

    @Test
    public void testHasRole() {
        // Test with privileged subject
        Subject privilegedSubject = createTestSubject("privUser", UnomiRoles.TENANT_ADMINISTRATOR);
        securityService.setPrivilegedSubject(privilegedSubject);
        assertTrue("Should have tenant admin role with privileged subject",
            securityService.hasRole(UnomiRoles.TENANT_ADMINISTRATOR));

        // Test with current subject
        Subject currentSubject = createTestSubject("currentUser", UnomiRoles.USER);
        securityService.setCurrentSubject(currentSubject);
        assertTrue("Should have user role with current subject",
            securityService.hasRole(UnomiRoles.USER));

        // Test role not present
        assertFalse("Should not have non-existent role",
            securityService.hasRole("NON_EXISTENT_ROLE"));
    }

    @Test
    public void testIsAdmin() {
        Subject regularSubject = createTestSubject("user", UnomiRoles.USER);
        securityService.setCurrentSubject(regularSubject);
        assertFalse("Regular user should not be admin", securityService.isAdmin());

        Subject adminSubject = createTestSubject("admin", UnomiRoles.ADMINISTRATOR);
        securityService.setCurrentSubject(adminSubject);
        assertTrue("Admin user should be admin", securityService.isAdmin());
    }

    @Test
    public void testHasSystemAccess() {
        Subject regularSubject = createTestSubject("user", UnomiRoles.USER);
        securityService.setCurrentSubject(regularSubject);
        assertFalse("Regular user should not have system access", securityService.hasSystemAccess());

        Subject tenantAdminSubject = createTestSubject("tenantAdmin", UnomiRoles.TENANT_ADMINISTRATOR);
        securityService.setCurrentSubject(tenantAdminSubject);
        assertTrue("Tenant admin should have system access", securityService.hasSystemAccess());

        Subject adminSubject = createTestSubject("admin", UnomiRoles.ADMINISTRATOR);
        securityService.setCurrentSubject(adminSubject);
        assertTrue("Admin should have system access", securityService.hasSystemAccess());
    }

    @Test
    public void testHasTenantAccess() {
        String testTenantId = "testTenant";

        Subject regularSubject = createTestSubject("user", UnomiRoles.USER);
        securityService.setCurrentSubject(regularSubject);
        assertFalse("Regular user should not have tenant access",
            securityService.hasTenantAccess(testTenantId));

        Subject tenantAdminSubject = createTestSubject("tenantAdmin", UnomiRoles.TENANT_ADMINISTRATOR);
        securityService.setCurrentSubject(tenantAdminSubject);
        assertTrue("Tenant admin should have tenant access",
            securityService.hasTenantAccess(testTenantId));
    }

    @Test
    public void testHasPermission() {
        // Configure required roles for test permission
        SecurityServiceConfiguration config = new SecurityServiceConfiguration();
        Map<String, String[]> permissionRoles = new HashMap<>();
        permissionRoles.put("TEST_PERMISSION", new String[]{UnomiRoles.ADMINISTRATOR});
        config.setPermissionRoles(permissionRoles);
        securityService.setConfiguration(config);

        // Test with insufficient privileges
        Subject regularSubject = createTestSubject("user", UnomiRoles.USER);
        securityService.setCurrentSubject(regularSubject);
        assertFalse("Regular user should not have test permission",
            securityService.hasPermission("TEST_PERMISSION"));

        // Test with sufficient privileges
        Subject adminSubject = createTestSubject("admin", UnomiRoles.ADMINISTRATOR);
        securityService.setCurrentSubject(adminSubject);
        assertTrue("Admin should have test permission",
            securityService.hasPermission("TEST_PERMISSION"));
    }

    @Test
    public void testAuditTenantOperation() {
        String testTenantId = "testTenant";
        String testOperation = "TEST_OPERATION";

        securityService.auditTenantOperation(testTenantId, testOperation);
        verify(auditService).logTenantOperation(testTenantId, testOperation);
    }

    @Test
    public void testExecuteWithPrivilegedSubject() {
        Subject regularSubject = createTestSubject("user", UnomiRoles.USER);
        securityService.setCurrentSubject(regularSubject);

        Subject privilegedSubject = createTestSubject("admin", UnomiRoles.ADMINISTRATOR);
        final boolean[] operationExecuted = {false};

        securityService.executeWithPrivilegedSubject(privilegedSubject, () -> {
            assertTrue("Should have admin role during operation",
                securityService.hasRole(UnomiRoles.ADMINISTRATOR));
            operationExecuted[0] = true;
        });

        assertTrue("Operation should have been executed", operationExecuted[0]);
        assertFalse("Should not have admin role after operation",
            securityService.hasRole(UnomiRoles.ADMINISTRATOR));
    }

    @Test
    public void testGetCurrentSubjectTenantId() {
        // Test with no subject
        assertEquals("Should return SYSTEM_TENANT when no subject",
            KarafSecurityService.SYSTEM_TENANT, securityService.getCurrentSubjectTenantId());

        // Test with subject having tenant
        String testTenantId = "testTenant";
        Subject subject = new Subject();
        subject.getPrincipals().add(new TenantPrincipal(testTenantId));
        securityService.setCurrentSubject(subject);

        assertEquals("Should return correct tenant ID",
            testTenantId, securityService.getCurrentSubjectTenantId());
    }

    @Test
    public void testIsOperatingOnSystemTenant() {
        assertFalse("Should return false by default", securityService.isOperatingOnSystemTenant());
    }

    @Test
    public void testGetTenantEncryptionKey() {
        String testTenantId = "testTenant";
        byte[] testKey = "testKey".getBytes();
        when(encryptionService.getTenantEncryptionKey(testTenantId)).thenReturn(testKey);

        assertArrayEquals("Should return correct encryption key",
            testKey, securityService.getTenantEncryptionKey(testTenantId));

        // Test with null encryption service
        securityService.unbindEncryptionService(encryptionService);
        assertNull("Should return null when encryption service is not available",
            securityService.getTenantEncryptionKey(testTenantId));
    }

    @Test
    public void testGetConfiguration() {
        SecurityServiceConfiguration config = new SecurityServiceConfiguration();
        securityService.setConfiguration(config);

        assertEquals("Should return correct configuration",
            config, securityService.getConfiguration());
    }

    @Test
    public void testGetPermissionsForRole() {
        // Set up test configuration
        SecurityServiceConfiguration config = new SecurityServiceConfiguration();
        Map<String, String[]> permissionRoles = new HashMap<>();
        permissionRoles.put("READ", new String[]{UnomiRoles.USER, UnomiRoles.ADMINISTRATOR});
        permissionRoles.put("WRITE", new String[]{UnomiRoles.ADMINISTRATOR});
        permissionRoles.put(SecurityServiceConfiguration.PERMISSION_DELETE, new String[]{UnomiRoles.ADMINISTRATOR});
        config.setPermissionRoles(permissionRoles);
        securityService.setConfiguration(config);

        // Test administrator role permissions
        Set<String> adminPermissions = securityService.getPermissionsForRole(UnomiRoles.ADMINISTRATOR);
        assertEquals("Admin should have all configured permissions", 3, adminPermissions.size());
        assertTrue("Admin should have READ permission", adminPermissions.contains("READ"));
        assertTrue("Admin should have WRITE permission", adminPermissions.contains("WRITE"));
        assertTrue("Admin should have DELETE permission", adminPermissions.contains(SecurityServiceConfiguration.PERMISSION_DELETE));

        // Test user role permissions
        Set<String> userPermissions = securityService.getPermissionsForRole(UnomiRoles.USER);
        assertEquals("User should have only READ permission", 1, userPermissions.size());
        assertTrue("User should have READ permission", userPermissions.contains("READ"));
        assertFalse("User should not have WRITE permission", userPermissions.contains("WRITE"));

        // Test role with no permissions
        Set<String> noPermissions = securityService.getPermissionsForRole("UNKNOWN_ROLE");
        assertTrue("Unknown role should have no permissions", noPermissions.isEmpty());

        // Test with null configuration
        securityService.setConfiguration(null);
        Set<String> nullConfigPermissions = securityService.getPermissionsForRole(UnomiRoles.ADMINISTRATOR);
        assertTrue("Null config should return empty permissions", nullConfigPermissions.isEmpty());
    }

    private Subject createTestSubject(String username, String... roles) {
        Subject subject = new Subject();
        subject.getPrincipals().add(new UserPrincipal(username));
        for (String role : roles) {
            subject.getPrincipals().add(new RolePrincipal(role));
        }
        return subject;
    }

    private Set<String> extractRoles(Set<Principal> principals) {
        return principals.stream()
            .filter(p -> p instanceof RolePrincipal)
            .map(Principal::getName)
            .collect(Collectors.toSet());
    }
}
