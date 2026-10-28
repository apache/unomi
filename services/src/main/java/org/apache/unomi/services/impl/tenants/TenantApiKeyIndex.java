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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * In-memory map from API key hash to tenant id, so {@link TenantServiceImpl#getTenantByApiKey} does not
 * load every tenant on each request (UNOMI-1016). Same refresh pattern as the tenant origins snapshot:
 * <ul>
 *     <li>Loaded in full on the first lookup. Lookups never wait on a load another request is running:
 *     during the first one they are refused, and the load is retried on a miss at most every
 *     {@value #MISS_RELOAD_SECONDS} seconds if it fails.</li>
 *     <li>Updated in place when a tenant is saved or removed through this node.</li>
 *     <li>Reloaded in full every {@value #REFRESH_SECONDS} seconds by one request while others keep
 *     the current state, to pick up keys created on other nodes.</li>
 *     <li>Reloaded on a miss at most every {@value #MISS_RELOAD_SECONDS} seconds.</li>
 * </ul>
 * The index only says which tenant a hash may belong to. A hit is confirmed against that tenant freshly
 * loaded by id, so a key revoked, rotated or expired — or a tenant deleted — on any node is refused on
 * the next request, not on the next refresh. Key hashes are deterministic (SHA-256), so a plaintext key
 * hashes to the same value as the stored digest.
 */
final class TenantApiKeyIndex {

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantApiKeyIndex.class);

    static final int REFRESH_SECONDS = 30;
    static final int MISS_RELOAD_SECONDS = 5;

    private final Supplier<List<Tenant>> loader;
    private final Function<String, Tenant> tenantById;
    private final LongSupplier clock;
    /** Serializes full loads. Not held by saves and removals, so they never wait on a slow load. */
    private final ReentrantLock loadLock = new ReentrantLock();
    /** Guards {@link #tenantIdByKeyHash} updates and {@link #changesDuringLoad}. */
    private final Object stateLock = new Object();

    /** Key hash → tenant id; replaced as a whole. Duplicate hashes keep the last tenant seen. */
    private volatile Map<String, String> tenantIdByKeyHash = Collections.emptyMap();
    /**
     * Saves (id → key hashes) and removals (id → {@code null}) made while a full load runs, reapplied on
     * top of its result so a load that read the store before them cannot undo them. {@code null} when no
     * load is running; guarded by {@link #stateLock}.
     */
    private Map<String, List<String>> changesDuringLoad;
    private volatile boolean attempted;
    /** When the next periodic reload is due; meaningful once {@link #attempted}. */
    private volatile long nextRefreshAt;
    /** When the last full load was tried; meaningful once {@link #attempted}. */
    private volatile long lastAttemptAt;

    TenantApiKeyIndex(Supplier<List<Tenant>> loader, Function<String, Tenant> tenantById, LongSupplier clock) {
        this.loader = loader;
        this.tenantById = tenantById;
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
        String tenantId = current().get(keyHash);
        if (tenantId == null) {
            tenantId = afterMiss().get(keyHash);
        }
        if (tenantId == null) {
            return null;
        }
        Tenant tenant = tenantById.apply(tenantId);
        return accepts(tenant, keyHash, keyType) ? tenant : null;
    }

    private static boolean accepts(Tenant tenant, String keyHash, ApiKey.ApiKeyType keyType) {
        if (tenant == null || tenant.getApiKeys() == null) {
            return false;
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
            return true;
        }
        return false;
    }

    /**
     * The current hash → tenant id map, refreshed first if the periodic reload is due. Never waits: while
     * another request runs a load, this one answers from the map as it is — so until the first load has
     * finished, keys this node has not seen saved are refused rather than queued behind it.
     */
    Map<String, String> current() {
        if (!attempted || clock.getAsLong() - nextRefreshAt >= 0) {
            tryReload();
        }
        return tenantIdByKeyHash;
    }

    /**
     * Reloads now unless a full load was tried in the last {@value #MISS_RELOAD_SECONDS} seconds or
     * another reload is running, and returns the resulting map.
     */
    Map<String, String> afterMiss() {
        if (!attempted || clock.getAsLong() - lastAttemptAt >= TimeUnit.SECONDS.toNanos(MISS_RELOAD_SECONDS)) {
            tryReload();
        }
        return tenantIdByKeyHash;
    }

    /** Full load, waiting for any reload in progress. Useful from tests. */
    void load() {
        loadLock.lock();
        try {
            doLoad();
        } finally {
            loadLock.unlock();
        }
    }

    /** A tenant was created or updated through this node: replace its key hashes from the saved object. */
    void tenantSaved(Tenant tenant) {
        if (tenant == null || tenant.getItemId() == null) {
            return;
        }
        applyChange(tenant.getItemId(), hashesOf(tenant));
    }

    /** A tenant was removed through this node: drop its key hashes. */
    void tenantRemoved(String tenantId) {
        if (tenantId == null) {
            return;
        }
        applyChange(tenantId, null);
    }

    private void applyChange(String tenantId, List<String> hashes) {
        synchronized (stateLock) {
            Map<String, String> byHash = new HashMap<>(tenantIdByKeyHash);
            replaceHashes(byHash, tenantId, hashes);
            tenantIdByKeyHash = Collections.unmodifiableMap(byHash);
            if (changesDuringLoad != null) {
                changesDuringLoad.put(tenantId, hashes);
            }
        }
    }

    private void tryReload() {
        if (loadLock.tryLock()) {
            try {
                doLoad();
            } finally {
                loadLock.unlock();
            }
        }
    }

    /** Runs with {@link #loadLock} held. */
    private void doLoad() {
        long now = clock.getAsLong();
        attempted = true;
        lastAttemptAt = now;
        synchronized (stateLock) {
            changesDuringLoad = new LinkedHashMap<>();
        }
        try {
            List<Tenant> tenants = loader.get();
            Map<String, String> byHash = new HashMap<>();
            for (Tenant tenant : tenants) {
                if (tenant != null && tenant.getItemId() != null) {
                    for (String hash : hashesOf(tenant)) {
                        byHash.put(hash, tenant.getItemId());
                    }
                }
            }
            synchronized (stateLock) {
                changesDuringLoad.forEach((tenantId, hashes) -> replaceHashes(byHash, tenantId, hashes));
                changesDuringLoad = null;
                tenantIdByKeyHash = Collections.unmodifiableMap(byHash);
            }
            nextRefreshAt = now + TimeUnit.SECONDS.toNanos(REFRESH_SECONDS);
        } catch (RuntimeException e) {
            synchronized (stateLock) {
                changesDuringLoad = null;
            }
            nextRefreshAt = now + TimeUnit.SECONDS.toNanos(MISS_RELOAD_SECONDS);
            LOGGER.warn("Could not load the API key → tenant index; keeping the previous one: {}", e.getMessage());
            LOGGER.debug("API key index load failure", e);
        }
    }

    private static List<String> hashesOf(Tenant tenant) {
        List<String> hashes = new ArrayList<>();
        if (tenant.getApiKeys() != null) {
            for (ApiKey apiKey : tenant.getApiKeys()) {
                if (apiKey != null && apiKey.getKeyHash() != null) {
                    hashes.add(apiKey.getKeyHash());
                }
            }
        }
        return hashes;
    }

    /** Drops the tenant's hashes, then maps {@code hashes} to it; {@code null} means the tenant is gone. */
    private static void replaceHashes(Map<String, String> byHash, String tenantId, List<String> hashes) {
        byHash.values().removeIf(tenantId::equals);
        if (hashes != null) {
            for (String hash : hashes) {
                byHash.put(hash, tenantId);
            }
        }
    }
}
