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
package org.apache.unomi.rest.cors;

import org.apache.unomi.api.tenants.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantOriginsSnapshotTest {

    private static final long REFRESH = TimeUnit.SECONDS.toNanos(TenantOriginsSnapshot.REFRESH_SECONDS);
    private static final long MISS = TimeUnit.SECONDS.toNanos(TenantOriginsSnapshot.MISS_RELOAD_SECONDS);

    private final List<Tenant> tenants = new ArrayList<>();
    private final AtomicInteger loads = new AtomicInteger();
    private volatile RuntimeException failure;
    // Positive, as System.nanoTime usually is, so an overflowing comparison would show.
    private final long[] now = {TimeUnit.DAYS.toNanos(3)};
    private TenantOriginsSnapshot snapshot;

    @BeforeEach
    void setUp() {
        tenants.add(tenant("acme", "https://shop.example"));
        snapshot = new TenantOriginsSnapshot(() -> {
            loads.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            return new ArrayList<>(tenants);
        }, () -> now[0]);
    }

    @Test
    void refreshesExactlyWhenDue() {
        snapshot.load();
        now[0] += REFRESH - 1;
        snapshot.current();
        assertEquals(1, loads.get());

        now[0] += 1;
        snapshot.current();
        assertEquals(2, loads.get());
    }

    @Test
    void missReloadsAreLimited() {
        snapshot.load();
        now[0] += MISS - 1;
        snapshot.afterMiss();
        assertEquals(1, loads.get());

        now[0] += 1;
        snapshot.afterMiss();
        snapshot.afterMiss();
        assertEquals(2, loads.get());
    }

    @Test
    void aFailedFirstLoadIsRetriedAfterTheBackoff() {
        // For example the search engine is not ready when the bundle starts.
        failure = new IllegalStateException("not ready");
        snapshot.load();
        assertTrue(snapshot.current().tenantIds.isEmpty());
        assertEquals(1, loads.get());

        failure = null;
        now[0] += MISS - 1;
        snapshot.current();
        assertEquals(1, loads.get());

        now[0] += 1;
        assertTrue(snapshot.current().lists.containsKey("acme"));
    }

    @Test
    void aFailedRefreshKeepsThePreviousStateAndBacksOff() {
        snapshot.load();
        TenantOriginsSnapshot.State loaded = snapshot.current();
        failure = new IllegalStateException("search engine unavailable");

        now[0] += REFRESH;
        assertSame(loaded, snapshot.current());
        int afterFailure = loads.get();
        for (int i = 0; i < 50; i++) {
            snapshot.current();
        }
        assertEquals(afterFailure, loads.get());
    }

    @Test
    void savedAndRemovedTenantsApplyWithoutLoading() {
        snapshot.load();

        snapshot.tenantSaved(tenant("globex", "https://blog.example"));
        snapshot.tenantSaved(tenant("acme", null));
        assertTrue(snapshot.current().lists.containsKey("globex"));
        assertFalse(snapshot.current().lists.containsKey("acme"));
        assertTrue(snapshot.current().tenantIds.contains("acme"));

        // Still in the store: the tenant service notifies before deleting.
        snapshot.tenantRemoved("globex");
        assertFalse(snapshot.current().tenantIds.contains("globex"));
        assertEquals(1, loads.get());
    }

    @Test
    void noRequestWaitsForAReloadInProgress() throws Exception {
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TenantOriginsSnapshot slow = new TenantOriginsSnapshot(() -> {
            loading.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new ArrayList<>(tenants);
        }, () -> now[0]);
        Thread reloader = new Thread(slow::load);
        reloader.start();
        assertTrue(loading.await(5, TimeUnit.SECONDS));

        // Past the miss limit, so afterMiss() does try to reload rather than skip the attempt.
        now[0] += MISS;
        long started = System.nanoTime();
        TenantOriginsSnapshot.State seen = slow.current();
        TenantOriginsSnapshot.State afterMiss = slow.afterMiss();
        assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(1), "a request waited for the reload");
        // Both answered from the current state (empty here: the reload in progress has not finished).
        assertSame(TenantOriginsSnapshot.State.EMPTY, seen);
        assertSame(TenantOriginsSnapshot.State.EMPTY, afterMiss);

        release.countDown();
        reloader.join();
    }

    @Test
    void aReloadDoesNotBringBackATenantJustRemoved() {
        // The tenant service notifies before it deletes, and the index may still return the tenant.
        snapshot.load();
        snapshot.tenantRemoved("acme");

        now[0] += REFRESH;
        assertFalse(snapshot.current().tenantIds.contains("acme"));

        // Once the marker has expired, a tenant the store still returns is taken again.
        now[0] += TimeUnit.SECONDS.toNanos(TenantOriginsSnapshot.REMOVED_SECONDS);
        assertTrue(snapshot.current().tenantIds.contains("acme"));
    }

    @Test
    void savingATenantAgainClearsItsRemovalMarker() {
        snapshot.load();
        snapshot.tenantRemoved("acme");
        snapshot.tenantSaved(tenant("acme", "https://shop.example"));

        now[0] += REFRESH;
        assertTrue(snapshot.current().lists.containsKey("acme"));
    }

    private static Tenant tenant(String id, String origins) {
        Tenant tenant = new Tenant();
        tenant.setItemId(id);
        Map<String, Object> properties = new HashMap<>();
        if (origins != null) {
            properties.put(PublicTrackerSettings.TENANT_PROPERTY_ALLOWED_ORIGINS, origins);
        }
        tenant.setProperties(properties);
        return tenant;
    }
}
