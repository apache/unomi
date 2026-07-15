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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-only usage snapshot for a tenant. Values are refreshed on a background schedule
 * (see {@link TenantUsageService}) and may be stale until the next collection cycle.
 * <p>Profile, scope, segment, and rule counts are point-in-time totals. {@link #eventCount}
 * counts events whose {@code timeStamp} falls within {@link #periodStart} (inclusive) and
 * {@link #periodEnd} (exclusive).</p>
 */
public class TenantUsage {

    /**
     * Tenant this snapshot describes.
     * @api.example acme
     */
    private String tenantId;
    /**
     * Normalized period label. For month windows typically {@code YYYY-MM} (for example {@code 2026-07}).
     * Request {@code period} query accepts {@code current-month}, {@code YYYY-MM}, or legacy {@code 24h}.
     * @api.example 2026-07
     */
    private String period;
    /** Inclusive start of the reporting period (epoch millis, UTC). */
    private long periodStart;
    /** Exclusive end of the reporting period (epoch millis, UTC). */
    private long periodEnd;
    /** Point-in-time profile count at collection. */
    private long profileCount;
    /** Events whose {@code timeStamp} falls in [{@link #periodStart}, {@link #periodEnd}). */
    private long eventCount;
    /** Tenant scopes excluding {@code systemscope}. */
    private long scopeCount;
    /** Point-in-time segment count. */
    private long segmentCount;
    /** Point-in-time rule count. */
    private long ruleCount;
    /** Document count across tenant indices (not byte size). */
    private long storageDocumentCount;
    /** Active (non-revoked, non-expired) API keys on the tenant record. */
    private long activeApiKeyCount;
    /** In-memory REST request counter for this tenant since the Unomi process started (resets on restart). */
    private long restRequestCount;
    /** Per-scope segment/rule breakdown; empty when no scoped usage was collected. */
    private List<TenantScopeUsage> scopeUsages = new ArrayList<>();
    /** When this snapshot was collected (epoch millis, UTC). */
    private long collectedAt;

    /**
     * Tenant this usage snapshot describes.
     *
     * @return tenant id
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Sets the unique identifier for the tenant associated with
     * this usage snapshot.
     *
     * @param tenantId The ID of the tenant.
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * Returns the normalized period label, e.g., {@code 2026-07}
     * for a calendar month.
     *
     * @return The period label string.
     */
    public String getPeriod() {
        return period;
    }

    /**
     * Sets the normalized period label, e.g., {@code 2026-07}
     * for a calendar month.
     *
     * @param period The desired period label.
     */
    public void setPeriod(String period) {
        this.period = period;
    }

    /**
     * Returns the inclusive start of the reporting period (epoch millis, UTC).
     *
     * @return The starting epoch millisecond value.
     */
    public long getPeriodStart() {
        return periodStart;
    }

    /**
     * Sets the inclusive start of the reporting period (epoch millis, UTC).
     *
     * @param periodStart The starting epoch millisecond value.
     */
    public void setPeriodStart(long periodStart) {
        this.periodStart = periodStart;
    }

    /**
     * Returns the exclusive end of the reporting period (epoch millis, UTC).
     *
     * @return The ending epoch millisecond value.
     */
    public long getPeriodEnd() {
        return periodEnd;
    }

    /**
     * Sets the exclusive end of the reporting period (epoch millis, UTC).
     *
     * @param periodEnd The ending epoch millisecond value.
     */
    public void setPeriodEnd(long periodEnd) {
        this.periodEnd = periodEnd;
    }

    /**
     * Total profile count at collection time.
     *
     * @return profile count
     */
    public long getProfileCount() {
        return profileCount;
    }

    /**
     * Sets the number of profiles for this tenant usage snapshot.
     *
     * @param profileCount The total count of profiles.
     */
    public void setProfileCount(long profileCount) {
        this.profileCount = profileCount;
    }

    /**
     * Returns the number of events counted for this tenant in the reporting
     * period. Events are included when their {@code timeStamp} falls within
     * {@link #getPeriodStart()} (inclusive) and {@link #getPeriodEnd()}
     * (exclusive).
     *
     * @return the event count for the reporting period
     */
    public long getEventCount() {
        return eventCount;
    }

    /**
     * Sets the count of events for this tenant usage snapshot.
     *
     * @param eventCount The total number of events.
     */
    public void setEventCount(long eventCount) {
        this.eventCount = eventCount;
    }

    /**
     * Tenant scope count excluding {@code systemscope}.
     *
     * @return scope count
     */
    public long getScopeCount() {
        return scopeCount;
    }

    /**
     * Sets the count of tenant scopes excluding {@code systemscope}.
     *
     * @param scopeCount The total number of scopes.
     */
    public void setScopeCount(long scopeCount) {
        this.scopeCount = scopeCount;
    }

    /**
     * Segment count at collection time.
     *
     * @return segment count
     */
    public long getSegmentCount() {
        return segmentCount;
    }

    /**
     * Sets the count of segments for this tenant usage snapshot.
     *
     * @param segmentCount The total number of segments.
     */
    public void setSegmentCount(long segmentCount) {
        this.segmentCount = segmentCount;
    }

    /**
     * Rule count at collection time.
     *
     * @return rule count
     */
    public long getRuleCount() {
        return ruleCount;
    }

    /**
     * Sets the count of rules associated with this tenant usage snapshot.
     *
     * @param ruleCount The total number of rules.
     */
    public void setRuleCount(long ruleCount) {
        this.ruleCount = ruleCount;
    }

    /**
     * Document count across all tenant indices at collection time.
     *
     * @return total document count
     */
    public long getStorageDocumentCount() {
        return storageDocumentCount;
    }

    /**
     * Sets the document count across all tenant indices
     * recorded in this snapshot.
     *
     * @param storageDocumentCount The total number of documents.
     */
    public void setStorageDocumentCount(long storageDocumentCount) {
        this.storageDocumentCount = storageDocumentCount;
    }

    /**
     * Active API key count on the tenant record.
     *
     * @return active API key count
     */
    public long getActiveApiKeyCount() {
        return activeApiKeyCount;
    }

    /**
     * Sets the count of active API keys on the tenant record
     * for this usage period.
     *
     * @param activeApiKeyCount The total number of active API keys.
     */
    public void setActiveApiKeyCount(long activeApiKeyCount) {
        this.activeApiKeyCount = activeApiKeyCount;
    }

    /**
     * In-memory REST request counter for this tenant since process start.
     *
     * @return REST request count
     */
    public long getRestRequestCount() {
        return restRequestCount;
    }

    /**
     * Sets the in-memory REST request counter for this tenant since the
     * Unomi process started.
     *
     * @param restRequestCount The total number of REST requests recorded.
     */
    public void setRestRequestCount(long restRequestCount) {
        this.restRequestCount = restRequestCount;
    }

    /**
     * Per-scope usage breakdown for this snapshot.
     *
     * @return unmodifiable scope usage list
     */
    public List<TenantScopeUsage> getScopeUsages() {
        return Collections.unmodifiableList(scopeUsages);
    }

    /**
     * Sets the list of usage details for all tenant scopes. This method
     * performs a defensive copy of the provided list to ensure
     * internal state immutability.
     *
     * @param scopeUsages The list of {@link TenantScopeUsage} objects.
     */
    public void setScopeUsages(List<TenantScopeUsage> scopeUsages) {
        // Copy defensively: callers (including the internal usage cache) must not be able to
        // mutate this snapshot's state through a shared list reference after construction.
        this.scopeUsages = scopeUsages != null ? new ArrayList<>(scopeUsages) : new ArrayList<>();
    }

    /**
     * Gets the timestamp (epoch millis) when this usage snapshot was collected.
     *
     * @return The collection time in epoch milliseconds.
     */
    public long getCollectedAt() {
        return collectedAt;
    }

    /**
     * Sets the timestamp (epoch millis) indicating when this usage
     * snapshot was collected.
     *
     * @param collectedAt The collection time in epoch milliseconds.
     */
    public void setCollectedAt(long collectedAt) {
        this.collectedAt = collectedAt;
    }
}
