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
package org.apache.unomi.api.tenants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AdminTenantSelectionTest {

    @Mock
    private TenantService tenantService;

    private Tenant defaultTenant;

    @Before
    public void setUp() {
        defaultTenant = new Tenant();
        defaultTenant.setItemId("default");
    }

    @Test
    public void noHeaderUsesConfiguredDefaultWhenItExists() {
        when(tenantService.getTenant("default")).thenReturn(defaultTenant);

        AdminTenantSelection selection = AdminTenantSelection.select(null, "default", tenantService, true);

        assertEquals(AdminTenantSelection.Kind.TENANT, selection.getKind());
        assertEquals("default", selection.getTenantId());
    }

    @Test
    public void noHeaderFailsWhenConfiguredDefaultIsMissing() {
        when(tenantService.getTenant("default")).thenReturn(null);

        AdminTenantSelection selection = AdminTenantSelection.select("  ", "default", tenantService, true);

        assertEquals(AdminTenantSelection.Kind.MISSING_DEFAULT, selection.getKind());
        assertEquals("default", selection.getTenantId());
    }

    @Test
    public void headerSelectsExistingTenant() {
        Tenant tenant = new Tenant();
        tenant.setItemId("acme");
        when(tenantService.getTenant("acme")).thenReturn(tenant);

        AdminTenantSelection selection = AdminTenantSelection.select("acme", "default", tenantService, false);

        assertEquals(AdminTenantSelection.Kind.TENANT, selection.getKind());
        assertEquals("acme", selection.getTenantId());
    }

    @Test
    public void unknownHeaderTenantIsReported() {
        when(tenantService.getTenant("missing")).thenReturn(null);

        AdminTenantSelection selection = AdminTenantSelection.select("missing", "default", tenantService, true);

        assertEquals(AdminTenantSelection.Kind.UNKNOWN_TENANT, selection.getKind());
        assertEquals("missing", selection.getTenantId());
    }

    @Test
    public void systemHeaderRequiresFullAdministrator() {
        AdminTenantSelection forbidden = AdminTenantSelection.select("system", "default", tenantService, false);
        assertEquals(AdminTenantSelection.Kind.FORBIDDEN_SYSTEM, forbidden.getKind());
        assertNull(forbidden.getTenantId());

        AdminTenantSelection allowed = AdminTenantSelection.select("system", "default", tenantService, true);
        assertEquals(AdminTenantSelection.Kind.SYSTEM, allowed.getKind());
    }

    @Test
    public void systemScopedResourceGivesFullAdministratorSystemWithoutHeader() {
        AdminTenantSelection selection = AdminTenantSelection.select(null, "default", tenantService, true, true);

        assertEquals(AdminTenantSelection.Kind.SYSTEM, selection.getKind());
        verifyNoInteractions(tenantService);
    }

    @Test
    public void systemScopedResourceStillUsesDefaultForOtherSubjects() {
        when(tenantService.getTenant("default")).thenReturn(defaultTenant);

        AdminTenantSelection selection = AdminTenantSelection.select(null, "default", tenantService, false, true);

        assertEquals(AdminTenantSelection.Kind.TENANT, selection.getKind());
        assertEquals("default", selection.getTenantId());
    }

    @Test
    public void normalizeRejectsBlankAndSystem() {
        assertEquals("default", AdminTenantSelection.normalizeDefaultTenantId(null));
        assertEquals("default", AdminTenantSelection.normalizeDefaultTenantId("  "));
        assertEquals("default", AdminTenantSelection.normalizeDefaultTenantId("system"));
        assertEquals("acme", AdminTenantSelection.normalizeDefaultTenantId(" acme "));
    }
}
