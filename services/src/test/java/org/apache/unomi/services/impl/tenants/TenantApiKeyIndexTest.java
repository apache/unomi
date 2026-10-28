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
package org.apache.unomi.services.impl.tenants;

import org.apache.unomi.api.security.SecretHashService;
import org.apache.unomi.api.tenants.ApiKey;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.services.security.SecretHashServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantApiKeyIndexTest {

    private static final long REFRESH = TimeUnit.MILLISECONDS.toNanos(TenantApiKeyIndex.DEFAULT_REFRESH_INTERVAL_MILLIS);
    private static final long MISS = TimeUnit.MILLISECONDS.toNanos(TenantApiKeyIndex.DEFAULT_MISS_REFRESH_INTERVAL_MILLIS);

    private final List<Tenant> tenants = new CopyOnWriteArrayList<>();
    private final AtomicInteger loads = new AtomicInteger();
    private volatile RuntimeException failure;
    /** When set, a full load blocks on it after reading {@link #tenants}. */
    private volatile CountDownLatch loadGate;
    /** Counted down once a full load has read {@link #tenants}. */
    private volatile CountDownLatch loadRead;
    private final long[] now = {TimeUnit.DAYS.toNanos(3)};
    private final SecretHashService hashService = new SecretHashServiceImpl();
    private TenantApiKeyIndex index;

    @BeforeEach
    void setUp() {
        tenants.add(tenantWithKey("acme", "plain-public", ApiKey.ApiKeyType.PUBLIC));
        index = new TenantApiKeyIndex(() -> {
            loads.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            List<Tenant> read = new ArrayList<>(tenants);
            if (loadRead != null) {
                loadRead.countDown();
            }
            if (loadGate != null) {
                try {
                    loadGate.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return read;
        }, this::byId, () -> now[0]);
    }

    @Test
    void refreshesExactlyWhenDue() {
        index.load();
        assertEquals(1, loads.get());
        now[0] += REFRESH - 1;
        index.current();
        assertEquals(1, loads.get());

        now[0] += 1;
        index.current();
        assertEquals(2, loads.get());
    }

    @Test
    void usesConfiguredIntervals() {
        TenantApiKeyIndex custom = new TenantApiKeyIndex(() -> {
            loads.incrementAndGet();
            return new ArrayList<>(tenants);
        }, this::byId, () -> now[0], 2_000, 500);
        custom.load();
        now[0] += TimeUnit.MILLISECONDS.toNanos(500);
        custom.afterMiss();
        assertEquals(2, loads.get());
        // Any full load restarts the refresh interval.
        now[0] += TimeUnit.MILLISECONDS.toNanos(2_000) - 1;
        custom.current();
        assertEquals(2, loads.get());
        now[0] += 1;
        custom.current();
        assertEquals(3, loads.get());
    }

    @Test
    void missReloadsAreLimited() {
        index.load();
        now[0] += MISS - 1;
        index.afterMiss();
        assertEquals(1, loads.get());

        now[0] += 1;
        index.afterMiss();
        assertEquals(2, loads.get());
    }

    @Test
    void findResolvesWithoutFurtherLoads() {
        index.load();
        assertEquals(1, loads.get());

        Tenant found = index.find("plain-public", ApiKey.ApiKeyType.PUBLIC, hashService);
        assertEquals("acme", found.getItemId());
        assertEquals(1, loads.get(), "A hit must not reload tenants");

        assertNull(index.find("plain-public", ApiKey.ApiKeyType.PRIVATE, hashService));
        assertEquals(1, loads.get());
    }

    @Test
    void missTriggersReloadThenFindsKeyFromOtherNode() {
        index.load();
        tenants.add(tenantWithKey("beta", "beta-key", ApiKey.ApiKeyType.PUBLIC));

        assertNull(index.find("beta-key", null, hashService));
        now[0] += MISS;
        Tenant found = index.find("beta-key", null, hashService);
        assertEquals("beta", found.getItemId());
        assertEquals(2, loads.get());
    }

    @Test
    void tenantSavedUpdatesIndexImmediately() {
        index.load();
        Tenant updated = tenantWithKey("acme", "rotated-key", ApiKey.ApiKeyType.PUBLIC);
        tenants.set(0, updated);
        index.tenantSaved(updated);

        assertNull(index.find("plain-public", null, hashService));
        assertSame(updated, index.find("rotated-key", null, hashService));
        assertEquals(1, loads.get());
    }

    @Test
    void tenantRemovedDropsKeysImmediately() {
        index.load();
        index.tenantRemoved("acme");
        assertNull(index.current().get(hashService.hash("plain-public")));
    }

    @Test
    void refusesKeysRevokedExpiredOrDeletedOnAnotherNodeWithoutWaitingForRefresh() {
        index.load();
        Tenant revoked = tenantWithKey("acme", "plain-public", ApiKey.ApiKeyType.PUBLIC);
        revoked.getApiKeys().get(0).setRevoked(true);
        tenants.set(0, revoked);
        assertNull(index.find("plain-public", null, hashService));

        Tenant expired = tenantWithKey("acme", "plain-public", ApiKey.ApiKeyType.PUBLIC);
        expired.getApiKeys().get(0).setExpirationDate(new Date(System.currentTimeMillis() - 60_000));
        tenants.set(0, expired);
        assertNull(index.find("plain-public", null, hashService));

        tenants.clear();
        assertNull(index.find("plain-public", null, hashService));
        assertEquals(1, loads.get(), "Refusals must come from the tenant loaded by id, not a refresh");
    }

    @Test
    void lookupsDuringALoadAreRefusedWithoutWaiting() throws Exception {
        loadGate = new CountDownLatch(1);
        loadRead = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Tenant> first = pool.submit(() -> index.find("plain-public", null, hashService));
            assertTrue(loadRead.await(10, TimeUnit.SECONDS));
            // The first load is still running: answer at once instead of queueing behind it.
            assertNull(index.find("plain-public", null, hashService));
            loadGate.countDown();
            assertEquals("acme", first.get(10, TimeUnit.SECONDS).getItemId());
        } finally {
            pool.shutdownNow();
        }
        assertEquals("acme", index.find("plain-public", null, hashService).getItemId());
        assertEquals(1, loads.get());
    }

    @Test
    void manyUnknownKeysTriggerAtMostOneLoadPerMissWindow() {
        index.load();
        for (int i = 0; i < 1_000; i++) {
            assertNull(index.find("unknown-" + i, null, hashService));
        }
        assertEquals(1, loads.get());
        now[0] += MISS;
        for (int i = 0; i < 1_000; i++) {
            assertNull(index.find("unknown-" + i, null, hashService));
        }
        assertEquals(2, loads.get());
    }

    @Test
    void savesAndRemovalsDuringALoadAreNotUndoneByIt() throws Exception {
        index.load();
        loadGate = new CountDownLatch(1);
        loadRead = new CountDownLatch(1);
        now[0] += REFRESH;
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> reload = pool.submit(() -> index.current());
            assertTrue(loadRead.await(10, TimeUnit.SECONDS));
            // The reload has read the store; these must neither wait for it nor be lost when it finishes.
            Tenant beta = tenantWithKey("beta", "beta-key", ApiKey.ApiKeyType.PUBLIC);
            tenants.add(beta);
            index.tenantSaved(beta);
            index.tenantRemoved("acme");
            loadGate.countDown();
            reload.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(2, loads.get());
        assertEquals("beta", index.current().get(hashService.hash("beta-key")));
        assertNull(index.current().get(hashService.hash("plain-public")));
    }

    @Test
    void keepsPreviousStateWhenLoadFails() {
        index.load();
        failure = new RuntimeException("store down");
        now[0] += REFRESH;
        index.current();
        assertEquals(2, loads.get());
        assertEquals("acme", index.find("plain-public", null, hashService).getItemId());
    }

    private Tenant byId(String id) {
        return tenants.stream().filter(t -> id.equals(t.getItemId())).findFirst().orElse(null);
    }

    private Tenant tenantWithKey(String id, String plainTextKey, ApiKey.ApiKeyType type) {
        Tenant tenant = new Tenant();
        tenant.setItemId(id);
        ApiKey apiKey = new ApiKey();
        apiKey.setKeyHash(hashService.hash(plainTextKey));
        apiKey.setKeyType(type);
        tenant.setApiKeys(new ArrayList<>(List.of(apiKey)));
        return tenant;
    }
}
