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
package org.apache.unomi.rest.endpoints;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.rest.scheduler.TaskEndpoint;
import org.apache.unomi.rest.tenants.TenantEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Admin REST endpoints advertise any origin without credentials; the CORS filter then grants
 * credentials only for origins listed in the admin allow-list.
 */
class AdminRestCorsTest {

    @Test
    void rulesEndpointDoesNotAdvertiseCredentials() {
        assertAdminCors(RulesServiceEndPoint.class);
    }

    @Test
    void profilesEndpointDoesNotAdvertiseCredentials() {
        assertAdminCors(ProfileServiceEndPoint.class);
    }

    @Test
    void tenantsEndpointDoesNotAdvertiseCredentials() {
        assertAdminCors(TenantEndpoint.class);
    }

    @Test
    void tasksEndpointDoesNotAdvertiseCredentials() {
        assertAdminCors(TaskEndpoint.class);
    }

    private static void assertAdminCors(Class<?> endpoint) {
        CrossOriginResourceSharing cors = endpoint.getAnnotation(CrossOriginResourceSharing.class);
        assertNotNull(cors, endpoint.getSimpleName() + " must declare CORS");
        assertTrue(cors.allowAllOrigins());
        assertFalse(cors.allowCredentials(), endpoint.getSimpleName() + " must not advertise credentials");
    }
}
