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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TenantApiKeyIndexTest {

    private static final long REFRESH = TimeUnit.SECONDS.toNanos(TenantApiKeyIndex.REFRESH_SECONDS);
    private static final long MISS = TimeUnit.SECONDS.toNanos(TenantApiKeyIndex.MISS_RELOAD_SECONDS);

    private final List<Tenant> tenants = new ArrayList<>();
    private final AtomicInteger loads = new AtomicInteger();
    private volatile RuntimeException failure;
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
            return new ArrayList<>(tenants);
        }, () -> now[0]);
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
        index.tenantSaved(updated);

        assertNull(index.find("plain-public", null, hashService));
        assertSame(updated, index.find("rotated-key", null, hashService));
        assertEquals(1, loads.get());
    }

    @Test
    void tenantRemovedDropsKeysImmediately() {
        index.load();
        index.tenantRemoved("acme");
        assertNull(index.find("plain-public", null, hashService));
        assertEquals(1, loads.get());
    }

    @Test
    void refusesRevokedAndExpiredKeys() {
        index.load();
        Tenant revoked = tenantWithKey("acme", "plain-public", ApiKey.ApiKeyType.PUBLIC);
        revoked.getApiKeys().get(0).setRevoked(true);
        index.tenantSaved(revoked);
        assertNull(index.find("plain-public", null, hashService));

        Tenant expired = tenantWithKey("acme", "plain-public", ApiKey.ApiKeyType.PUBLIC);
        expired.getApiKeys().get(0).setExpirationDate(new Date(System.currentTimeMillis() - 60_000));
        index.tenantSaved(expired);
        assertNull(index.find("plain-public", null, hashService));
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
