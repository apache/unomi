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
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.tenants.ApiKey;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TenantServiceImplTest {

    @Mock
    private PersistenceService persistenceService;

    @Mock
    private ExecutionContextManager executionContextManager;

    @Mock
    private SecretHashService secretHashService;

    private TenantServiceImpl tenantService;

    private final AtomicLong clock = new AtomicLong();

    @BeforeEach
    public void setUp() {
        tenantService = new TenantServiceImpl();
        tenantService.nanoClock = clock::get;
        tenantService.setPersistenceService(persistenceService);
        tenantService.setExecutionContextManager(executionContextManager);
        tenantService.setSecretHashService(secretHashService);

        // Compare-and-set writes succeed unless a test says otherwise.
        when(persistenceService.save(any(Tenant.class), eq(false), eq(false))).thenReturn(true);
        when(executionContextManager.executeAsSystem(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(executionContextManager).executeAsSystem(any(Runnable.class));

        // Treat the "hash" as the plaintext key itself, so tests can assert on plain values
        // without depending on the real hash implementation.
        when(secretHashService.verify(anyString(), anyString()))
                .thenAnswer(invocation -> Objects.equals(invocation.getArgument(0), invocation.getArgument(1)));
        when(secretHashService.hash(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void getOrCreateTenant_readsPersistenceOnceAfterCacheWarm() {
        Tenant existing = new Tenant();
        existing.setItemId("default");
        when(persistenceService.load("default", Tenant.class)).thenReturn(existing);

        assertSame(existing, tenantService.getOrCreateTenant("default", Collections.emptyMap()));
        assertSame(existing, tenantService.getOrCreateTenant("default", Collections.emptyMap()));
        assertSame(existing, tenantService.getOrCreateTenant("default", Collections.emptyMap()));

        verify(persistenceService, times(1)).load("default", Tenant.class);
    }

    @Test
    public void getOrCreateTenant_reloadsAfterSave() {
        Tenant first = new Tenant();
        first.setItemId("default");
        Map<String, Object> firstProps = new HashMap<>();
        firstProps.put("name", "first");
        first.setProperties(firstProps);

        Tenant second = new Tenant();
        second.setItemId("default");
        Map<String, Object> secondProps = new HashMap<>();
        secondProps.put("name", "second");
        second.setProperties(secondProps);

        when(persistenceService.load("default", Tenant.class)).thenReturn(first, second);

        assertEquals("first", tenantService.getOrCreateTenant("default", Collections.emptyMap())
                .getProperties().get("name"));

        tenantService.saveTenant(second);
        assertEquals("second", tenantService.getOrCreateTenant("default", Collections.emptyMap())
                .getProperties().get("name"));

        verify(persistenceService, times(2)).load("default", Tenant.class);
    }

    @Test
    public void getOrCreateTenant_reloadsAfterDelete() {
        Tenant existing = defaultTenant();
        AtomicReference<Tenant> store = inMemoryTenantStore(existing, tenant -> { });

        assertSame(existing, getOrCreateDefaultTenant());

        tenantService.deleteTenant("default");
        assertNull(store.get());

        Tenant afterDelete = getOrCreateDefaultTenant();
        assertNotSame(existing, afterDelete);
        assertSame(store.get(), afterDelete);
    }

    @Test
    public void getOrCreateTenant_reloadsAfterApiKeyGeneration() {
        inMemoryTenantStore(defaultTenant(), tenant -> { });

        getOrCreateDefaultTenant();
        tenantService.generateApiKeyWithType("default", ApiKey.ApiKeyType.PUBLIC, null);
        getOrCreateDefaultTenant();

        // One read per getOrCreateTenant call, plus the one generateApiKeyWithType does itself.
        verify(persistenceService, times(3)).load("default", Tenant.class);
    }

    /**
     * Nothing tells this node about a delete done on another one, so the entry has to expire by
     * itself for the tenant to be created again.
     */
    @Test
    public void getOrCreateTenant_recreatesTenantDeletedOnAnotherNodeOnceEntryExpires() {
        Tenant existing = defaultTenant();
        AtomicReference<Tenant> store = inMemoryTenantStore(existing, tenant -> { });
        assertSame(existing, getOrCreateDefaultTenant());

        store.set(null);
        assertSame(existing, getOrCreateDefaultTenant());

        clock.addAndGet(TenantServiceImpl.GET_OR_CREATE_CACHE_TTL_NANOS);
        Tenant recreated = getOrCreateDefaultTenant();
        assertNotSame(existing, recreated);
        assertSame(store.get(), recreated);
    }

    @Test
    public void getOrCreateTenant_answersFromExpiredEntryWhileAnotherThreadRefreshes() throws Exception {
        Tenant existing = defaultTenant();
        CountDownLatch refreshStarted = new CountDownLatch(1);
        CountDownLatch releaseRefresh = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        when(persistenceService.load("default", Tenant.class)).thenAnswer(invocation -> {
            if (loads.incrementAndGet() > 1) {
                refreshStarted.countDown();
                releaseRefresh.await();
            }
            return existing;
        });
        getOrCreateDefaultTenant();
        clock.addAndGet(TenantServiceImpl.GET_OR_CREATE_CACHE_TTL_NANOS);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Tenant> refresh = pool.submit(this::getOrCreateDefaultTenant);
            assertTrue(refreshStarted.await(10, TimeUnit.SECONDS));

            assertSame(existing, assertTimeoutPreemptively(Duration.ofSeconds(10), this::getOrCreateDefaultTenant));

            releaseRefresh.countDown();
            assertSame(existing, refresh.get(10, TimeUnit.SECONDS));
        } finally {
            releaseRefresh.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    public void getOrCreateTenant_concurrentFirstRequestsCreateOnce() throws Exception {
        AtomicInteger creations = new AtomicInteger();
        inMemoryTenantStore(null, tenant -> {
            // Only createTenant's own save carries no API key yet. Hold it open so that callers
            // which are not kept out by the lock would all get past the existence check.
            if (tenant.getApiKeys() == null || tenant.getApiKeys().isEmpty()) {
                creations.incrementAndGet();
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50));
            }
        });

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Tenant>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return getOrCreateDefaultTenant();
                }));
            }
            start.countDown();

            for (Future<Tenant> future : futures) {
                assertEquals("default", future.get(10, TimeUnit.SECONDS).getItemId());
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, creations.get(), "Exactly one tenant must be created under concurrent first requests");
    }

    @Test
    public void getOrCreateTenant_returnsTenantCreatedByAnotherNodeDuringCreation() {
        Tenant createdElsewhere = defaultTenant();
        // Missing on the first read, there by the time createTenant checks for a duplicate.
        when(persistenceService.load("default", Tenant.class)).thenReturn(null, createdElsewhere);

        assertSame(createdElsewhere, getOrCreateDefaultTenant());

        verify(persistenceService, never()).save(any(Tenant.class));
    }

    @Test
    public void getOrCreateTenant_doesNotCacheFailure() {
        Tenant existing = defaultTenant();
        when(persistenceService.load("default", Tenant.class))
                .thenThrow(new IllegalStateException("search engine unavailable"))
                .thenReturn(existing);

        assertThrows(IllegalStateException.class, this::getOrCreateDefaultTenant);
        // Retried from another thread, which a lock left held by the failed call would block.
        assertSame(existing, assertTimeoutPreemptively(Duration.ofSeconds(10), this::getOrCreateDefaultTenant));
    }

    @Test
    public void getOrCreateTenant_rejectsNullTenantId() {
        assertThrows(IllegalArgumentException.class,
                () -> tenantService.getOrCreateTenant(null, Collections.emptyMap()));
    }

    private static Tenant defaultTenant() {
        Tenant tenant = new Tenant();
        tenant.setItemId("default");
        return tenant;
    }

    private Tenant getOrCreateDefaultTenant() {
        return tenantService.getOrCreateTenant("default", Collections.singletonMap("name", "default"));
    }

    /** Backs tenant {@code default} with a single in-memory slot; {@code onSave} sees each tenant before it is stored. */
    private AtomicReference<Tenant> inMemoryTenantStore(Tenant initial, Consumer<Tenant> onSave) {
        AtomicReference<Tenant> store = new AtomicReference<>(initial);
        when(persistenceService.load(eq("default"), eq(Tenant.class))).thenAnswer(invocation -> store.get());
        when(persistenceService.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            onSave.accept(tenant);
            store.set(tenant);
            return true;
        });
        doAnswer(invocation -> {
            store.set(null);
            return null;
        }).when(persistenceService).remove("default", Tenant.class);
        return store;
    }

    @Test
    public void getTenantByApiKeySkipsTenantsWithNullApiKeys() {
        Tenant tenantWithoutKeys = new Tenant();
        tenantWithoutKeys.setItemId("tenant-no-keys");
        tenantWithoutKeys.setApiKeys(null);

        Tenant tenantWithKeys = new Tenant();
        tenantWithKeys.setItemId("tenant-with-keys");
        ApiKey apiKey = new ApiKey();
        apiKey.setKeyHash("valid-key");
        apiKey.setKeyType(ApiKey.ApiKeyType.PUBLIC);
        tenantWithKeys.setApiKeys(new ArrayList<>(List.of(apiKey)));

        storeTenants(tenantWithoutKeys, tenantWithKeys);

        Tenant found = tenantService.getTenantByApiKey("valid-key");
        assertEquals("tenant-with-keys", found.getItemId(), "Should find tenant with matching API key");
        assertNull(tenantService.getTenantByApiKey("missing-key"), "Non-existent key should return null");
    }

    @Test
    public void getTenantByApiKeyWithTypeSkipsTenantsWithNullApiKeys() {
        Tenant tenantWithoutKeys = new Tenant();
        tenantWithoutKeys.setItemId("tenant-no-keys");
        tenantWithoutKeys.setApiKeys(null);

        Tenant tenantWithKeys = new Tenant();
        tenantWithKeys.setItemId("tenant-with-keys");
        ApiKey apiKey = new ApiKey();
        apiKey.setKeyHash("valid-key");
        apiKey.setKeyType(ApiKey.ApiKeyType.PRIVATE);
        tenantWithKeys.setApiKeys(new ArrayList<>(List.of(apiKey)));

        storeTenants(tenantWithoutKeys, tenantWithKeys);

        Tenant found = tenantService.getTenantByApiKey("valid-key", ApiKey.ApiKeyType.PRIVATE);
        assertEquals("tenant-with-keys", found.getItemId(), "Should find tenant with matching typed API key");
        assertNull(tenantService.getTenantByApiKey("valid-key", ApiKey.ApiKeyType.PUBLIC),
                "Key type mismatch should return null");
    }

    @Test
    public void getTenantByApiKeyRejectsUnknownKey() {
        Tenant tenant = new Tenant();
        tenant.setItemId("tenant-with-keys");
        ApiKey apiKey = new ApiKey();
        apiKey.setKeyHash("stored-hash");
        apiKey.setKeyType(ApiKey.ApiKeyType.PUBLIC);
        tenant.setApiKeys(new ArrayList<>(List.of(apiKey)));

        storeTenants(tenant);

        assertNull(tenantService.getTenantByApiKey("wrong-key"),
                "A key whose hash is not in the index should be rejected");
        verify(secretHashService).hash("wrong-key");
        verify(secretHashService, never()).verify(anyString(), anyString());
    }

    @Test
    public void getTenantByApiKeyResolvesByHashWithoutScanningTenantsAgain() {
        Tenant tenant = new Tenant();
        tenant.setItemId("tenant-with-keys");
        ApiKey apiKey = new ApiKey();
        apiKey.setKeyHash("candidate-key");
        apiKey.setKeyType(ApiKey.ApiKeyType.PUBLIC);
        tenant.setApiKeys(new ArrayList<>(List.of(apiKey)));

        storeTenants(tenant);

        Tenant found = tenantService.getTenantByApiKey("candidate-key");
        assertEquals("tenant-with-keys", found.getItemId(),
                "A hashed key should resolve to its tenant");
        // Second lookup must use the in-memory index (UNOMI-1016).
        assertEquals("tenant-with-keys", tenantService.getTenantByApiKey("candidate-key").getItemId());
        verify(persistenceService, times(1)).getAllItems(Tenant.class);
        verify(secretHashService, never()).verify(anyString(), anyString());
    }

    @Test
    public void getTenantByApiKeySeesNewlySavedKeyWithoutWaitingForRefresh() {
        when(persistenceService.getAllItems(Tenant.class)).thenReturn(Collections.emptyList());
        assertNull(tenantService.getTenantByApiKey("new-key"));

        Tenant tenant = new Tenant();
        tenant.setItemId("fresh-tenant");
        ApiKey apiKey = new ApiKey();
        apiKey.setKeyHash("new-key");
        apiKey.setKeyType(ApiKey.ApiKeyType.PUBLIC);
        tenant.setApiKeys(new ArrayList<>(List.of(apiKey)));
        tenantService.saveTenant(tenant);
        when(persistenceService.load("fresh-tenant", Tenant.class)).thenReturn(tenant);

        assertEquals("fresh-tenant", tenantService.getTenantByApiKey("new-key").getItemId());
        verify(persistenceService, times(1)).getAllItems(Tenant.class);
    }

    @Test
    public void getTenantByApiKeyRefusesRevokedKey() {
        Tenant tenant = new Tenant();
        tenant.setItemId("tenant-with-keys");
        ApiKey apiKey = new ApiKey();
        apiKey.setKeyHash("revoked-key");
        apiKey.setKeyType(ApiKey.ApiKeyType.PUBLIC);
        apiKey.setRevoked(true);
        tenant.setApiKeys(new ArrayList<>(List.of(apiKey)));

        storeTenants(tenant);

        assertNull(tenantService.getTenantByApiKey("revoked-key"),
                "A revoked key must not authenticate");
    }

    @Test
    public void getTenantByApiKeyRefusesKeyRevokedOnAnotherNodeBeforeRefresh() {
        Tenant tenant = tenantWithKey("shared-key");
        storeTenants(tenant);
        assertEquals("acme", tenantService.getTenantByApiKey("shared-key").getItemId());

        Tenant revokedElsewhere = tenantWithKey("shared-key");
        revokedElsewhere.getApiKeys().get(0).setRevoked(true);
        when(persistenceService.load("acme", Tenant.class)).thenReturn(revokedElsewhere);

        assertNull(tenantService.getTenantByApiKey("shared-key"));
        verify(persistenceService, times(1)).getAllItems(Tenant.class);
    }

    @Test
    public void getTenantByApiKeyRefusesKeyOfDeletedTenant() {
        Tenant tenant = tenantWithKey("doomed-key");
        storeTenants(tenant);
        assertEquals("acme", tenantService.getTenantByApiKey("doomed-key").getItemId());

        tenantService.deleteTenant("acme");
        when(persistenceService.load("acme", Tenant.class)).thenReturn(null);

        assertNull(tenantService.getTenantByApiKey("doomed-key"));
    }

    @Test
    public void getTenantByApiKeyFollowsKeyRotation() {
        Tenant stored = tenantWithKey("old-hash");
        storeTenants(stored);
        assertEquals("acme", tenantService.getTenantByApiKey("old-hash").getItemId());

        // The mocked hash is the identity, so the new key's hash is also its plaintext.
        String newHash = tenantService.generateApiKeyWithType("acme", ApiKey.ApiKeyType.PUBLIC, null)
                .getApiKey().getKeyHash();

        assertNull(tenantService.getTenantByApiKey("old-hash"), "The replaced key must stop working");
        assertEquals("acme", tenantService.getTenantByApiKey(newHash).getItemId(),
                "The new key must work without waiting for a refresh");
        verify(persistenceService, times(1)).getAllItems(Tenant.class);
    }

    @Test
    public void getTenantByApiKeyStillWorksAfterSettingsUpdate() {
        Tenant stored = tenantWithKey("kept-key");
        storeTenants(stored);
        assertEquals("acme", tenantService.getTenantByApiKey("kept-key").getItemId());
        Tenant settings = new Tenant();
        settings.setItemId("acme");

        tenantService.updateTenantSettings(settings);

        assertEquals("acme", tenantService.getTenantByApiKey("kept-key").getItemId());
    }

    /** Backs {@code getAllItems} and load-by-id with the given tenants. */
    private void storeTenants(Tenant... tenants) {
        when(persistenceService.getAllItems(Tenant.class)).thenReturn(List.of(tenants));
        for (Tenant tenant : tenants) {
            when(persistenceService.load(tenant.getItemId(), Tenant.class)).thenReturn(tenant);
        }
    }

    @Test
    public void createTenantThrowsWhenReloadReturnsNull() {
        Tenant savedTenant = new Tenant();
        savedTenant.setItemId("test-tenant");
        savedTenant.setApiKeys(new ArrayList<>());

        when(persistenceService.load(eq("test-tenant"), eq(Tenant.class)))
                .thenReturn(null, savedTenant, savedTenant, null);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> tenantService.createTenant("test-tenant", Collections.emptyMap()));
        assertEquals("Failed to reload tenant after creation: test-tenant", exception.getMessage());
    }

    @Test
    public void deleteTenantThrowsWhenTenantMissing() {
        when(persistenceService.load("missing-tenant", Tenant.class)).thenReturn(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> tenantService.deleteTenant("missing-tenant"));
        assertEquals("Tenant not found: missing-tenant", exception.getMessage());
    }

    @Test
    public void updatingSettingsKeepsAKeyReplacedConcurrently() {
        // UNOMI-1013: a tenant update must never undo an API key change made while it was in progress.
        Tenant beforeRotation = tenantWithKey("old-hash");
        Tenant afterRotation = tenantWithKey("new-hash");
        when(persistenceService.load("acme", Tenant.class)).thenReturn(beforeRotation, afterRotation);
        // Tenants are equal by id, so the conflict is stubbed as a sequence: the first write loses the race.
        when(persistenceService.save(any(Tenant.class), eq(false), eq(false))).thenReturn(false, true);

        Tenant settings = new Tenant();
        settings.setItemId("acme");
        settings.setProperties(Map.of("publicCorsAllowedOrigins", "https://shop.example"));
        ApiKey fromBody = new ApiKey();
        fromBody.setKeyHash(null);
        settings.setApiKeys(List.of(fromBody));

        Tenant saved = tenantService.updateTenantSettings(settings);

        assertSame(afterRotation, saved);
        assertEquals("new-hash", saved.getApiKeys().get(0).getKeyHash());
        assertEquals("https://shop.example", saved.getProperties().get("publicCorsAllowedOrigins"));
        verify(persistenceService).refreshIndex(Tenant.class);
    }

    @Test
    public void updatingSettingsOfAnUnknownTenantReturnsNull() {
        when(persistenceService.load("missing", Tenant.class)).thenReturn(null);
        Tenant settings = new Tenant();
        settings.setItemId("missing");

        assertNull(tenantService.updateTenantSettings(settings));
        verify(persistenceService, never()).save(any(Tenant.class), eq(false), eq(false));
    }

    @Test
    public void updatingSettingsGivesUpOnATenantThatKeepsChanging() {
        when(persistenceService.load("acme", Tenant.class)).thenAnswer(invocation -> tenantWithKey("hash"));
        when(persistenceService.save(any(Tenant.class), eq(false), eq(false))).thenReturn(false);
        Tenant settings = new Tenant();
        settings.setItemId("acme");

        assertThrows(java.util.ConcurrentModificationException.class, () -> tenantService.updateTenantSettings(settings));
    }

    @Test
    public void replacingAKeyKeepsSettingsUpdatedConcurrently() {
        Tenant before = tenantWithKey("old-hash");
        Tenant updatedMeanwhile = tenantWithKey("old-hash");
        updatedMeanwhile.setProperties(Map.of("publicCorsAllowedOrigins", "https://shop.example"));
        when(persistenceService.load("acme", Tenant.class)).thenReturn(before, updatedMeanwhile);
        when(persistenceService.save(any(Tenant.class), eq(false), eq(false))).thenReturn(false, true);
        when(secretHashService.hash(anyString())).thenReturn("new-hash");

        tenantService.generateApiKeyWithType("acme", ApiKey.ApiKeyType.PUBLIC, null);

        assertEquals("https://shop.example", updatedMeanwhile.getProperties().get("publicCorsAllowedOrigins"));
        assertEquals("new-hash", updatedMeanwhile.getApiKeys().get(0).getKeyHash());
        verify(persistenceService).refreshIndex(Tenant.class);
    }

    @Test
    public void savingOrDeletingATenantRefreshesTheIndex() {
        Tenant tenant = tenantWithKey("hash");
        tenantService.saveTenant(tenant);
        when(persistenceService.load("acme", Tenant.class)).thenReturn(tenant);
        tenantService.deleteTenant("acme");

        verify(persistenceService, times(2)).refreshIndex(Tenant.class);
    }

    private static Tenant tenantWithKey(String hash) {
        ApiKey key = new ApiKey();
        key.setItemId("key-" + hash);
        key.setKeyType(ApiKey.ApiKeyType.PUBLIC);
        key.setKeyHash(hash);
        Tenant tenant = new Tenant();
        tenant.setItemId("acme");
        tenant.setApiKeys(new ArrayList<>(List.of(key)));
        tenant.setProperties(new HashMap<>());
        return tenant;
    }
}
