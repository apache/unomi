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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The website origins each tenant lists in its {@value PublicTrackerSettings#TENANT_PROPERTY_ALLOWED_ORIGINS}
 * property, kept in memory so that requests never load tenants.
 * <ul>
 *     <li>Loaded in full at activation.</li>
 *     <li>Updated in place from the tenant object when a tenant is saved or removed through this node, so the
 *     change does not wait for the search index and does not depend on when removal listeners are called.</li>
 *     <li>Reloaded in full every {@value #REFRESH_SECONDS} seconds, by one request while the others keep the
 *     current state, to pick up changes made on other nodes.</li>
 *     <li>Reloaded when an unknown tenant or origin shows up, at most every {@value #MISS_RELOAD_SECONDS}
 *     seconds, so a tenant created on another node works on its first request and requests cannot force
 *     reloads. No request ever waits for a reload made by another.</li>
 *     <li>After a failed load, the previous state is kept and the next attempt waits
 *     {@value #MISS_RELOAD_SECONDS} seconds.</li>
 * </ul>
 */
final class TenantOriginsSnapshot {

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantOriginsSnapshot.class);

    static final int REFRESH_SECONDS = 30;
    static final int MISS_RELOAD_SECONDS = 5;

    /** One consistent view; replaced as a whole. */
    static final class State {
        static final State EMPTY = new State(Collections.emptySet(), Collections.emptyMap(), Collections.emptyMap());

        final Set<String> tenantIds;
        /** Only tenants that list at least one valid origin. */
        final Map<String, Set<String>> lists;
        /** The property as configured, to report invalid entries once per change. */
        final Map<String, Object> configured;

        State(Set<String> tenantIds, Map<String, Set<String>> lists, Map<String, Object> configured) {
            this.tenantIds = tenantIds;
            this.lists = lists;
            this.configured = configured;
        }
    }

    private final Supplier<List<Tenant>> loader;
    private final LongSupplier clock;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile State state = State.EMPTY;
    private volatile boolean attempted;
    /** When the next periodic reload is due; meaningful once {@link #attempted}. */
    private volatile long nextRefreshAt;
    /** When the last full load was tried; meaningful once {@link #attempted}. */
    private volatile long lastAttemptAt;

    TenantOriginsSnapshot(Supplier<List<Tenant>> loader, LongSupplier clock) {
        this.loader = loader;
        this.clock = clock;
    }

    /** The current state, refreshed first if the periodic reload is due. */
    State current() {
        if (!attempted || clock.getAsLong() - nextRefreshAt >= 0) {
            tryReload();
        }
        return state;
    }

    /**
     * Reloads now unless a full load was tried in the last {@value #MISS_RELOAD_SECONDS} seconds or another
     * reload is running, and returns the resulting state. For a tenant or origin the snapshot does not know.
     */
    State afterMiss() {
        if (!attempted || clock.getAsLong() - lastAttemptAt >= TimeUnit.SECONDS.toNanos(MISS_RELOAD_SECONDS)) {
            tryReload();
        }
        return state;
    }

    /** Full load, waiting for any reload in progress. At activation. */
    void load() {
        lock.lock();
        try {
            doLoad();
        } finally {
            lock.unlock();
        }
    }

    /** A tenant was created or updated through this node: apply its list from the saved object. */
    void tenantSaved(Tenant tenant) {
        if (tenant == null || tenant.getItemId() == null) {
            return;
        }
        lock.lock();
        try {
            State previous = state;
            Set<String> ids = new HashSet<>(previous.tenantIds);
            Map<String, Set<String>> lists = new HashMap<>(previous.lists);
            Map<String, Object> configured = new HashMap<>(previous.configured);
            put(tenant, ids, lists, configured, previous);
            state = new State(Collections.unmodifiableSet(ids), Collections.unmodifiableMap(lists),
                    Collections.unmodifiableMap(configured));
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
            State previous = state;
            if (!previous.tenantIds.contains(tenantId)) {
                return;
            }
            Set<String> ids = new HashSet<>(previous.tenantIds);
            Map<String, Set<String>> lists = new HashMap<>(previous.lists);
            Map<String, Object> configured = new HashMap<>(previous.configured);
            ids.remove(tenantId);
            lists.remove(tenantId);
            configured.remove(tenantId);
            state = new State(Collections.unmodifiableSet(ids), Collections.unmodifiableMap(lists),
                    Collections.unmodifiableMap(configured));
        } finally {
            lock.unlock();
        }
    }

    private void tryReload() {
        // Never wait: a request either does the reload or keeps the current state.
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
            State previous = state;
            Set<String> ids = new HashSet<>();
            Map<String, Set<String>> lists = new HashMap<>();
            Map<String, Object> configured = new HashMap<>();
            for (Tenant tenant : tenants) {
                put(tenant, ids, lists, configured, previous);
            }
            state = new State(Collections.unmodifiableSet(ids), Collections.unmodifiableMap(lists),
                    Collections.unmodifiableMap(configured));
            nextRefreshAt = now + TimeUnit.SECONDS.toNanos(REFRESH_SECONDS);
        } catch (RuntimeException e) {
            // Keep the previous state and back off rather than retry on every request.
            nextRefreshAt = now + TimeUnit.SECONDS.toNanos(MISS_RELOAD_SECONDS);
            LOGGER.warn("Could not load the tenants' public CORS allowed origins; keeping the previous ones: {}", e.getMessage());
            LOGGER.debug("Tenant origins load failure", e);
        }
    }

    private static void put(Tenant tenant, Set<String> ids, Map<String, Set<String>> lists,
                            Map<String, Object> configured, State previous) {
        String id = tenant.getItemId();
        ids.add(id);
        Object value = tenant.getProperties() == null ? null : tenant.getProperties().get(PublicTrackerSettings.TENANT_PROPERTY_ALLOWED_ORIGINS);
        Set<String> origins = PublicTrackerCorsPolicy.parseTenantOrigins(value);
        if (origins.isEmpty()) {
            lists.remove(id);
        } else {
            lists.put(id, origins);
        }
        if (value == null) {
            configured.remove(id);
        } else {
            configured.put(id, value);
        }
        // Tenants saved through /cxs/tenants are validated; others (shell, direct service calls) are not.
        if (value != null && !Objects.equals(value, previous.configured.get(id))) {
            List<String> problems = PublicTrackerCorsPolicy.tenantOriginProblems(value);
            if (!problems.isEmpty()) {
                LOGGER.warn("Ignoring invalid entries in the {} of tenant {}: {}",
                        PublicTrackerSettings.TENANT_PROPERTY_ALLOWED_ORIGINS, id, String.join("; ", problems));
            }
        }
    }
}
