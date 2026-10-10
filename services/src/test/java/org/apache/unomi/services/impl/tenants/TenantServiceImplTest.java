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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
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

    @BeforeEach
    public void setUp() {
        tenantService = new TenantServiceImpl();
        tenantService.setPersistenceService(persistenceService);
        tenantService.setExecutionContextManager(executionContextManager);
        tenantService.setSecretHashService(secretHashService);

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
        Tenant existing = new Tenant();
        existing.setItemId("default");

        AtomicReference<Tenant> store = new AtomicReference<>(existing);
        when(persistenceService.load(eq("default"), eq(Tenant.class))).thenAnswer(invocation -> store.get());
        when(persistenceService.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            if (tenant.getApiKeys() == null) {
                tenant.setApiKeys(new ArrayList<>());
            }
            store.set(tenant);
            return true;
        });
        doAnswer(invocation -> {
            store.set(null);
            return null;
        }).when(persistenceService).remove("default", Tenant.class);

        assertSame(existing, tenantService.getOrCreateTenant("default", Collections.emptyMap()));

        tenantService.deleteTenant("default");
        assertNull(store.get());

        Tenant afterDelete = tenantService.getOrCreateTenant("default", Collections.singletonMap("name", "default"));
        assertNotNull(afterDelete);
        assertEquals("default", afterDelete.getItemId());
        verify(persistenceService).remove("default", Tenant.class);
    }

    @Test
    public void getOrCreateTenant_concurrentFirstRequestsCreateOnce() throws Exception {
        AtomicReference<Tenant> store = new AtomicReference<>();
        AtomicInteger initialSaves = new AtomicInteger();

        when(persistenceService.load(eq("default"), eq(Tenant.class))).thenAnswer(invocation -> store.get());
        when(persistenceService.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            if (tenant.getApiKeys() == null) {
                tenant.setApiKeys(new ArrayList<>());
            }
            // The first persistence write in createTenant has no API keys yet.
            if (tenant.getApiKeys().isEmpty() && store.get() == null) {
                initialSaves.incrementAndGet();
            }
            store.set(tenant);
            return true;
        });

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Tenant>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return tenantService.getOrCreateTenant("default", Collections.singletonMap("name", "default"));
            }));
        }
        start.countDown();

        for (Future<Tenant> future : futures) {
            Tenant tenant = future.get(10, TimeUnit.SECONDS);
            assertNotNull(tenant);
            assertEquals("default", tenant.getItemId());
        }
        pool.shutdown();
        assertEquals(1, initialSaves.get(), "Exactly one tenant must be created under concurrent first requests");
        verify(persistenceService, atLeastOnce()).save(any(Tenant.class));
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

        when(persistenceService.getAllItems(Tenant.class)).thenReturn(List.of(tenantWithoutKeys, tenantWithKeys));

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

        when(persistenceService.getAllItems(Tenant.class)).thenReturn(List.of(tenantWithoutKeys, tenantWithKeys));

        Tenant found = tenantService.getTenantByApiKey("valid-key", ApiKey.ApiKeyType.PRIVATE);
        assertEquals("tenant-with-keys", found.getItemId(), "Should find tenant with matching typed API key");
        assertNull(tenantService.getTenantByApiKey("valid-key", ApiKey.ApiKeyType.PUBLIC),
                "Key type mismatch should return null");
    }

    @Test
    public void getTenantByApiKeyRejectsWhenVerifyFails() {
        Tenant tenant = new Tenant();
        tenant.setItemId("tenant-with-keys");
        ApiKey apiKey = new ApiKey();
        apiKey.setKeyHash("stored-hash");
        apiKey.setKeyType(ApiKey.ApiKeyType.PUBLIC);
        tenant.setApiKeys(new ArrayList<>(List.of(apiKey)));

        when(persistenceService.getAllItems(Tenant.class)).thenReturn(List.of(tenant));
        when(secretHashService.verify("wrong-key", "stored-hash")).thenReturn(false);

        assertNull(tenantService.getTenantByApiKey("wrong-key"),
                "A key that fails verification should be rejected");
        verify(secretHashService).verify("wrong-key", "stored-hash");
    }

    @Test
    public void getTenantByApiKeyAcceptsWhenVerifySucceeds() {
        Tenant tenant = new Tenant();
        tenant.setItemId("tenant-with-keys");
        ApiKey apiKey = new ApiKey();
        apiKey.setKeyHash("stored-hash");
        apiKey.setKeyType(ApiKey.ApiKeyType.PUBLIC);
        tenant.setApiKeys(new ArrayList<>(List.of(apiKey)));

        when(persistenceService.getAllItems(Tenant.class)).thenReturn(List.of(tenant));
        when(secretHashService.verify("candidate-key", "stored-hash")).thenReturn(true);

        Tenant found = tenantService.getTenantByApiKey("candidate-key");
        assertEquals("tenant-with-keys", found.getItemId(),
                "A verified key should resolve to its tenant");
        verify(secretHashService).verify("candidate-key", "stored-hash");
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
}
