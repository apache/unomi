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
package org.apache.unomi.rest.tenants;

import org.apache.unomi.api.tenants.TenantEventPurgeResult;
import org.apache.unomi.api.tenants.TenantUsage;
import org.apache.unomi.api.tenants.TenantUsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantEndpointTest {

    @Mock
    private TenantUsageService tenantUsageService;

    private TenantEndpoint endpoint;

    @BeforeEach
    void setUp() throws Exception {
        endpoint = new TenantEndpoint();
        Field field = TenantEndpoint.class.getDeclaredField("tenantUsageService");
        field.setAccessible(true);
        field.set(endpoint, tenantUsageService);
    }

    @Test
    void getTenantUsageReturnsOkWithUsageSnapshot() {
        TenantUsage usage = new TenantUsage();
        usage.setTenantId("tenant-a");
        usage.setPeriod("2026-07");
        when(tenantUsageService.getUsage("tenant-a", "current-month")).thenReturn(usage);

        Response response = endpoint.getTenantUsage("tenant-a", "current-month");

        assertEquals(200, response.getStatus());
        assertEquals(usage, response.getEntity());
    }

    @Test
    void getTenantUsageReturnsNotFoundWhenTenantMissing() {
        when(tenantUsageService.getUsage("missing", "current-month")).thenReturn(null);

        Response response = endpoint.getTenantUsage("missing", "current-month");

        assertEquals(404, response.getStatus());
    }

    @Test
    void getTenantUsageReturnsBadRequestForUnsupportedPeriod() {
        when(tenantUsageService.getUsage(eq("tenant-a"), eq("7d")))
                .thenThrow(new IllegalArgumentException("Unsupported usage period: 7d"));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> endpoint.getTenantUsage("tenant-a", "7d"));

        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    void purgeTenantEventsReturnsOkWithResult() {
        TenantEventPurgeResult result = new TenantEventPurgeResult();
        result.setTenantId("tenant-a");
        result.setRetentionDays(90);
        result.setEventsMatched(10L);
        result.setPurgeRequested(true);
        when(tenantUsageService.purgeEventsOlderThan("tenant-a", 90)).thenReturn(result);

        Response response = endpoint.purgeTenantEvents("tenant-a", 90);

        assertEquals(200, response.getStatus());
        assertEquals(result, response.getEntity());
    }

    @Test
    void purgeTenantEventsReturnsNotFoundWhenTenantMissing() {
        when(tenantUsageService.purgeEventsOlderThan("missing", 90)).thenReturn(null);

        Response response = endpoint.purgeTenantEvents("missing", 90);

        assertEquals(404, response.getStatus());
    }

    @Test
    void purgeTenantEventsRejectsNonPositiveRetention() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> endpoint.purgeTenantEvents("tenant-a", 0));

        assertEquals(400, ex.getResponse().getStatus());
        assertNotNull(ex.getMessage());
    }

    @Test
    void purgeTenantEventsReturnsBadRequestWhenRetentionBelowMinimum() {
        when(tenantUsageService.purgeEventsOlderThan("tenant-a", 3))
                .thenThrow(new IllegalArgumentException("retentionDays must be at least 7"));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> endpoint.purgeTenantEvents("tenant-a", 3));

        assertEquals(400, ex.getResponse().getStatus());
    }
}
