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

package org.apache.unomi.didvc.rest;

import org.apache.unomi.api.security.UnomiRoles;
import org.apache.unomi.rest.security.RequiresRole;
import org.junit.jupiter.api.Test;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Authorization matrix for the DID-VC admin APIs (FR-G3): every
 * mutating endpoint (schema, issuer/DID, status-list, trust-registry,
 * pairwise-binding, consent and credential-revocation administration)
 * must carry {@code @RequiresRole(ADMINISTRATOR)} — the admin trust
 * domain enforced by the platform SecurityFilter — while read/verification
 * endpoints stay in the customer-facing domain (platform API key, no
 * administrator role required).
 */
class AdminAuthorizationMatrixTest {

    private static final Class<?>[] ENDPOINTS = {
            DidvcRegistryEndPoint.class,
            DidServiceEndPoint.class,
            CredentialEndPoint.class,
    };

    private static boolean isMutating(Method method) {
        return method.isAnnotationPresent(POST.class)
                || method.isAnnotationPresent(PUT.class)
                || method.isAnnotationPresent(DELETE.class);
    }

    private static boolean requiresAdministrator(Method method) {
        RequiresRole requiresRole = method.getAnnotation(RequiresRole.class);
        if (requiresRole == null) {
            return false;
        }
        for (String role : requiresRole.value()) {
            if (UnomiRoles.ADMINISTRATOR.equals(role)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void everyMutatingAdminEndpointRequiresAdministratorRole() {
        List<String> unguarded = new ArrayList<>();
        for (Class<?> endpoint : ENDPOINTS) {
            for (Method method : endpoint.getDeclaredMethods()) {
                if (isMutating(method) && !requiresAdministrator(method)) {
                    unguarded.add(endpoint.getSimpleName() + "#" + method.getName());
                }
            }
        }
        assertTrue(unguarded.isEmpty(),
                "mutating endpoints missing @RequiresRole(ADMINISTRATOR): " + unguarded);
    }

    @Test
    void readEndpointsStayInCustomerFacingDomain() {
        // Reads must NOT demand the administrator role — the edge and
        // verifier rely on them with the platform API key only
        List<String> overRestricted = new ArrayList<>();
        for (Class<?> endpoint : ENDPOINTS) {
            for (Method method : endpoint.getDeclaredMethods()) {
                if (method.isAnnotationPresent(GET.class) && requiresAdministrator(method)) {
                    overRestricted.add(endpoint.getSimpleName() + "#" + method.getName());
                }
            }
        }
        assertTrue(overRestricted.isEmpty(),
                "read endpoints must not require the administrator role: " + overRestricted);
    }

    @Test
    void adminSurfaceCoversTheGovernedOperations() {
        // The governed operations named by FR-G3: schema administration,
        // issuer/DID administration, status-list publication (incl.
        // revocation), trust-registry administration, credential
        // revocation
        String[] governed = {"saveSchema", "deleteSchema", "createStatusList", "publishStatusList",
                "saveTrustEntry", "deleteTrustEntry", "create", "rotate", "deactivate", "revoke"};
        for (String name : governed) {
            boolean found = false;
            for (Class<?> endpoint : ENDPOINTS) {
                for (Method method : endpoint.getDeclaredMethods()) {
                    if (method.getName().equals(name) && requiresAdministrator(method)) {
                        found = true;
                    }
                }
            }
            if (!found) {
                fail("governed operation not admin-protected: " + name);
            }
        }
        assertFalse(governed.length == 0);
    }
}
