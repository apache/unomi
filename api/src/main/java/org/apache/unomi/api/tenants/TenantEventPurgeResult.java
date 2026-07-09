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
 * Result of a tenant-scoped event retention purge request.
 */
public class TenantEventPurgeResult {

    private String tenantId;
    private int retentionDays;
    /** Events matching the retention cutoff before delete-by-query was submitted; not a post-delete count. */
    private long eventsMatched;
    /**
     * {@code true} if the delete-by-query completed successfully; {@code false} if the
     * persistence layer reported a failure (see server logs for the cause). This is not a
     * partial-success indicator: {@link #eventsMatched} is a pre-delete estimate only, so a
     * {@code true} result does not by itself confirm how many events were actually removed.
     */
    private boolean purgeRequested;
    private long requestedAt;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public long getEventsMatched() {
        return eventsMatched;
    }

    public void setEventsMatched(long eventsMatched) {
        this.eventsMatched = eventsMatched;
    }

    public boolean isPurgeRequested() {
        return purgeRequested;
    }

    public void setPurgeRequested(boolean purgeRequested) {
        this.purgeRequested = purgeRequested;
    }

    public long getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(long requestedAt) {
        this.requestedAt = requestedAt;
    }
}
