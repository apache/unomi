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
 *
 * <p>Profile, scope, segment, and rule counts are point-in-time totals. {@link #eventCount}
 * counts events whose {@code timeStamp} falls within {@link #periodStart} (inclusive) and
 * {@link #periodEnd} (exclusive).</p>
 */
public class TenantUsage {

    private String tenantId;
    /** Normalized period label, e.g. {@code 2026-07} for a calendar month. */
    private String period;
    /** Inclusive start of the reporting period (epoch millis, UTC). */
    private long periodStart;
    /** Exclusive end of the reporting period (epoch millis, UTC). */
    private long periodEnd;
    private long profileCount;
    /** Events in the calendar month identified by {@link #period}. */
    private long eventCount;
    /** Tenant scopes excluding {@code systemscope}. */
    private long scopeCount;
    private long segmentCount;
    private long ruleCount;
    /** Document count across tenant indices (not byte size). */
    private long storageDocumentCount;
    /** Active API keys on the tenant record. */
    private long activeApiKeyCount;
    /** In-memory REST request counter for this tenant since the Unomi process started. */
    private long restRequestCount;
    private List<TenantScopeUsage> scopeUsages = new ArrayList<>();
    private long collectedAt;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public long getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(long periodStart) {
        this.periodStart = periodStart;
    }

    public long getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(long periodEnd) {
        this.periodEnd = periodEnd;
    }

    public long getProfileCount() {
        return profileCount;
    }

    public void setProfileCount(long profileCount) {
        this.profileCount = profileCount;
    }

    public long getEventCount() {
        return eventCount;
    }

    public void setEventCount(long eventCount) {
        this.eventCount = eventCount;
    }

    public long getScopeCount() {
        return scopeCount;
    }

    public void setScopeCount(long scopeCount) {
        this.scopeCount = scopeCount;
    }

    public long getSegmentCount() {
        return segmentCount;
    }

    public void setSegmentCount(long segmentCount) {
        this.segmentCount = segmentCount;
    }

    public long getRuleCount() {
        return ruleCount;
    }

    public void setRuleCount(long ruleCount) {
        this.ruleCount = ruleCount;
    }

    public long getStorageDocumentCount() {
        return storageDocumentCount;
    }

    public void setStorageDocumentCount(long storageDocumentCount) {
        this.storageDocumentCount = storageDocumentCount;
    }

    public long getActiveApiKeyCount() {
        return activeApiKeyCount;
    }

    public void setActiveApiKeyCount(long activeApiKeyCount) {
        this.activeApiKeyCount = activeApiKeyCount;
    }

    public long getRestRequestCount() {
        return restRequestCount;
    }

    public void setRestRequestCount(long restRequestCount) {
        this.restRequestCount = restRequestCount;
    }

    public List<TenantScopeUsage> getScopeUsages() {
        return Collections.unmodifiableList(scopeUsages);
    }

    public void setScopeUsages(List<TenantScopeUsage> scopeUsages) {
        // Copy defensively: callers (including the internal usage cache) must not be able to
        // mutate this snapshot's state through a shared list reference after construction.
        this.scopeUsages = scopeUsages != null ? new ArrayList<>(scopeUsages) : new ArrayList<>();
    }

    public long getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(long collectedAt) {
        this.collectedAt = collectedAt;
    }
}
