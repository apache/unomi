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
package org.apache.unomi.api.tenants;

/**
 * Provides read-only per-tenant usage metrics and tenant-scoped maintenance operations
 * for operators and upstream control planes. Unomi does not enforce quotas; callers use
 * these metrics to apply limits upstream.
 */
public interface TenantUsageService {

    /** Calendar month containing the current instant (UTC). */
    String DEFAULT_PERIOD = "current-month";

    /**
     * Minimum retention window accepted by {@link #purgeEventsOlderThan(String, int)}, guarding
     * against accidentally purging recent/active event data with a mistakenly small value.
     */
    int MIN_EVENT_RETENTION_DAYS = 7;

    /**
     * Returns cached usage for the tenant, refreshing on demand when no snapshot exists yet.
     *
     * @param tenantId tenant identifier
     * @param period reporting window: {@value #DEFAULT_PERIOD}, {@code YYYY-MM}, or legacy {@code 24h}
     * @return usage snapshot, or {@code null} if the tenant does not exist
     */
    TenantUsage getUsage(String tenantId, String period);

    /**
     * Records one authenticated REST request for the tenant (in-memory counter).
     * @param tenantId tenant identifier
     */
    void recordRestRequest(String tenantId);

    /**
     * Deletes events for the tenant whose {@code timeStamp} is at least {@code retentionDays}
     * days old (the boundary day itself is included). Runs under an explicit tenant execution
     * context.
     *
     * @param tenantId tenant identifier
     * @param retentionDays age cutoff in whole days (minimum {@value #MIN_EVENT_RETENTION_DAYS})
     * @return purge summary, or {@code null} if the tenant does not exist
     * @throws IllegalArgumentException when {@code retentionDays} is below the minimum
     */
    TenantEventPurgeResult purgeEventsOlderThan(String tenantId, int retentionDays);
}
