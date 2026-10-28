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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * In-memory map from API key hash to tenant, so {@link TenantServiceImpl#getTenantByApiKey} does not
 * load every tenant on each request (UNOMI-1016). Same refresh pattern as the tenant origins snapshot:
 * <ul>
 *     <li>Loaded in full on the first lookup (and on each periodic refresh).</li>
 *     <li>Updated in place when a tenant is saved or removed through this node.</li>
 *     <li>Reloaded in full every {@value #REFRESH_SECONDS} seconds by one request while others keep
 *     the current state, to pick up changes made on other nodes.</li>
 *     <li>Reloaded on a miss at most every {@value #MISS_RELOAD_SECONDS} seconds.</li>
 *     <li>A tenant removed through this node is left out of full loads for {@value #REMOVED_SECONDS}
 *     seconds, until the store no longer returns it.</li>
 * </ul>
 * Key hashes are deterministic (SHA-256), so a plaintext key hashes to the same value as the stored
 * digest and resolves in one map lookup. Revoked and expired keys are refused at lookup time.
 */
final class TenantApiKeyIndex {

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantApiKeyIndex.class);

    static final int REFRESH_SECONDS = 30;
    static final int MISS_RELOAD_SECONDS = 5;
    /**
     * How long a tenant removed through this node stays excluded from full loads. The tenant service
     * notifies listeners before it deletes, so a load in between would otherwise bring the tenant back.
     */
    static final int REMOVED_SECONDS = 60;

    /** One consistent view; replaced as a whole. */
    static final class State {
        static final State EMPTY = new State(Collections.emptyMap(), Collections.emptyMap());

        /** Tenant id → tenant as last loaded or saved on this node. */
        final Map<String, Tenant> tenantsById;
        /** Key hash → tenant id. Duplicate hashes keep the last tenant seen when rebuilding. */
        final Map<String, String> tenantIdByKeyHash;

        State(Map<String, Tenant> tenantsById, Map<String, String> tenantIdByKeyHash) {
            this.tenantsById = tenantsById;
            this.tenantIdByKeyHash = tenantIdByKeyHash;
        }
    }

    private final Supplier<List<Tenant>> loader;
    private final LongSupplier clock;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile State state = State.EMPTY;
    /** Tenants removed through this node, and until when loads skip them; guarded by {@link #lock}. */
    private final Map<String, Long> removedUntil = new HashMap<>();
    private volatile boolean attempted;
    /** When the next periodic reload is due; meaningful once {@link #attempted}. */
    private volatile long nextRefreshAt;
    /** When the last full load was tried; meaningful once {@link #attempted}. */
    private volatile long lastAttemptAt;

    TenantApiKeyIndex(Supplier<List<Tenant>> loader, LongSupplier clock) {
        this.loader = loader;
        this.clock = clock;
    }

    /**
     * Resolves a plaintext API key to its tenant, refreshing the index when due or on a miss.
     *
     * @param plainTextKey the key from the request; may be {@code null}
     * @param keyType      required type, or {@code null} for any type
     * @param hashService  hashes the plaintext the same way keys are stored
     * @return the matching tenant, or {@code null} when missing, revoked, expired, or type-mismatched
     */
    Tenant find(String plainTextKey, ApiKey.ApiKeyType keyType, SecretHashService hashService) {
        if (plainTextKey == null || hashService == null) {
            return null;
        }
        String keyHash = hashService.hash(plainTextKey);
        Tenant found = resolve(current(), keyHash, keyType);
        if (found != null) {
            return found;
        }
        return resolve(afterMiss(), keyHash, keyType);
    }

    private static Tenant resolve(State state, String keyHash, ApiKey.ApiKeyType keyType) {
        String tenantId = state.tenantIdByKeyHash.get(keyHash);
        if (tenantId == null) {
            return null;
        }
        Tenant tenant = state.tenantsById.get(tenantId);
        if (tenant == null || tenant.getApiKeys() == null) {
            return null;
        }
        Date now = new Date();
        for (ApiKey apiKey : tenant.getApiKeys()) {
            if (apiKey == null || !keyHash.equals(apiKey.getKeyHash())) {
                continue;
            }
            if (keyType != null && apiKey.getKeyType() != keyType) {
                continue;
            }
            if (apiKey.isRevoked()) {
                continue;
            }
            if (apiKey.getExpirationDate() != null && !apiKey.getExpirationDate().after(now)) {
                continue;
            }
            return tenant;
        }
        return null;
    }

    /** The current state, refreshed first if the periodic reload is due. */
    State current() {
        if (!attempted || clock.getAsLong() - nextRefreshAt >= 0) {
            tryReload();
        }
        return state;
    }

    /**
     * Reloads now unless a full load was tried in the last {@value #MISS_RELOAD_SECONDS} seconds or
     * another reload is running, and returns the resulting state.
     */
    State afterMiss() {
        if (!attempted || clock.getAsLong() - lastAttemptAt >= TimeUnit.SECONDS.toNanos(MISS_RELOAD_SECONDS)) {
            tryReload();
        }
        return state;
    }

    /** Full load, waiting for any reload in progress. Useful from tests. */
    void load() {
        lock.lock();
        try {
            doLoad();
        } finally {
            lock.unlock();
        }
    }

    /** A tenant was created or updated through this node: replace its key hashes from the saved object. */
    void tenantSaved(Tenant tenant) {
        if (tenant == null || tenant.getItemId() == null) {
            return;
        }
        lock.lock();
        try {
            removedUntil.remove(tenant.getItemId());
            State previous = state;
            Map<String, Tenant> tenantsById = new HashMap<>(previous.tenantsById);
            Map<String, String> byHash = new HashMap<>(previous.tenantIdByKeyHash);
            removeHashesForTenant(tenant.getItemId(), byHash);
            put(tenant, tenantsById, byHash);
            state = new State(Collections.unmodifiableMap(tenantsById), Collections.unmodifiableMap(byHash));
        } finally {
            lock.unlock();
        }
    }

    /** A tenant was removed: drop it, whether or not it is still in the store. */
    void tenantRemoved(String tenantId) {
        if (tenantId == null) {
            return;
        }
        lock.lock();
        try {
            removedUntil.put(tenantId, clock.getAsLong() + TimeUnit.SECONDS.toNanos(REMOVED_SECONDS));
            State previous = state;
            if (!previous.tenantsById.containsKey(tenantId)) {
                return;
            }
            Map<String, Tenant> tenantsById = new HashMap<>(previous.tenantsById);
            Map<String, String> byHash = new HashMap<>(previous.tenantIdByKeyHash);
            tenantsById.remove(tenantId);
            removeHashesForTenant(tenantId, byHash);
            state = new State(Collections.unmodifiableMap(tenantsById), Collections.unmodifiableMap(byHash));
        } finally {
            lock.unlock();
        }
    }

    private void tryReload() {
        if (lock.tryLock()) {
            try {
                doLoad();
            } finally {
                lock.unlock();
            }
        }
    }

    private void doLoad() {
        long now = clock.getAsLong();
        attempted = true;
        lastAttemptAt = now;
        try {
            List<Tenant> tenants = loader.get();
            removedUntil.values().removeIf(until -> now - until >= 0);
            Map<String, Tenant> tenantsById = new HashMap<>();
            Map<String, String> byHash = new HashMap<>();
            for (Tenant tenant : tenants) {
                if (tenant != null && tenant.getItemId() != null && !removedUntil.containsKey(tenant.getItemId())) {
                    put(tenant, tenantsById, byHash);
                }
            }
            state = new State(Collections.unmodifiableMap(tenantsById), Collections.unmodifiableMap(byHash));
            nextRefreshAt = now + TimeUnit.SECONDS.toNanos(REFRESH_SECONDS);
        } catch (RuntimeException e) {
            nextRefreshAt = now + TimeUnit.SECONDS.toNanos(MISS_RELOAD_SECONDS);
            LOGGER.warn("Could not load the API key → tenant index; keeping the previous one: {}", e.getMessage());
            LOGGER.debug("API key index load failure", e);
        }
    }

    private static void put(Tenant tenant, Map<String, Tenant> tenantsById, Map<String, String> byHash) {
        String id = tenant.getItemId();
        tenantsById.put(id, tenant);
        if (tenant.getApiKeys() == null) {
            return;
        }
        for (ApiKey apiKey : tenant.getApiKeys()) {
            if (apiKey != null && apiKey.getKeyHash() != null) {
                byHash.put(apiKey.getKeyHash(), id);
            }
        }
    }

    private static void removeHashesForTenant(String tenantId, Map<String, String> byHash) {
        byHash.entrySet().removeIf(entry -> tenantId.equals(entry.getValue()));
    }
}
