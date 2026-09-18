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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Public tracker endpoints keep a broad origin policy and do not advertise credentialed CORS.
 */
class PublicTrackerCorsTest {

    @Test
    void contextJsonAllowsAnyOriginWithoutCredentials() {
        assertPublicTrackerCors(ContextJsonEndpoint.class);
    }

    @Test
    void eventsCollectorAllowsAnyOriginWithoutCredentials() {
        assertPublicTrackerCors(EventsCollectorEndpoint.class);
    }

    @Test
    void clientAllowsAnyOriginWithoutCredentials() {
        assertPublicTrackerCors(ClientEndpoint.class);
    }

    private static void assertPublicTrackerCors(Class<?> endpoint) {
        CrossOriginResourceSharing cors = endpoint.getAnnotation(CrossOriginResourceSharing.class);
        assertNotNull(cors, endpoint.getSimpleName() + " must declare CORS");
        assertTrue(cors.allowAllOrigins());
        assertFalse(cors.allowCredentials());
    }
}
