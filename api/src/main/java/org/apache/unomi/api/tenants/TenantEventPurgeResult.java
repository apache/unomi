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
 * Outcome of a request to delete old events for one tenant.
 * Reports how many events matched the retention cutoff and whether the
 * delete-by-query completed successfully.
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

    /**
     * Retrieves the unique identifier of the tenant
     * associated with this result.
     * @return The tenant ID string.
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Sets the unique identifier of the tenant associated with this result.
     * @param tenantId The tenant ID to set.
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * Retrieves the number of days events must be retained before
     * purge consideration.
     * @return The retention period in days.
     */
    public int getRetentionDays() {
        return retentionDays;
    }

    /**
     * Sets the number of days events must be retained before
     * purge consideration.
     * @param retentionDays The retention period in days.
     */
    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    /**
     * Retrieves the estimated number of events that matched the retention
     * cutoff before the delete-by-query was submitted; not a post-delete count.
     * @return The count of matching events.
     */
    public long getEventsMatched() {
        return eventsMatched;
    }

    /**
     * Sets the estimated number of events that matched the retention cutoff
     * before the delete-by-query was submitted; not a post-delete count.
     * @param eventsMatched The estimated count of matching events.
     */
    public void setEventsMatched(long eventsMatched) {
        this.eventsMatched = eventsMatched;
    }

    /**
     * Checks whether the delete-by-query for event purging completed
     * successfully.
     *
     * @return {@code true} if the delete-by-query completed successfully,
     *         {@code false} if the persistence layer reported a failure
     */
    public boolean isPurgeRequested() {
        return purgeRequested;
    }

    /**
     * Sets whether the delete-by-query for event purging completed
     * successfully.
     *
     * @param purgeRequested {@code true} if the delete-by-query completed
     *                       successfully, {@code false} otherwise
     */
    public void setPurgeRequested(boolean purgeRequested) {
        this.purgeRequested = purgeRequested;
    }

    /**
     * Gets the timestamp (in milliseconds) when this event retention purge
     * request was initiated.
     * @return The requested time in milliseconds since the epoch.
     */
    public long getRequestedAt() {
        return requestedAt;
    }

    /**
     * Sets the timestamp when the event retention purge request was initiated.
     * @param requestedAt the time in milliseconds since the epoch
     */
    public void setRequestedAt(long requestedAt) {
        this.requestedAt = requestedAt;
    }
}
