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

import java.util.HashMap;
import java.util.Map;

/**
 * Defines resource quotas and limits for a tenant.
 * This class manages various resource constraints to ensure fair usage and prevent abuse.
 * Each quota represents a maximum limit that the tenant cannot exceed.
 * When a quota is reached, the system will prevent further resource allocation until
 * resources are freed or the quota is increased.
 */
public class ResourceQuota {
    /**
     * The maximum number of profiles that can be stored for this tenant.
     * When this limit is reached, attempts to create new profiles will be rejected.
     */
    private long maxProfiles;

    /**
     * The maximum number of events that can be processed per time period for this tenant.
     * Events beyond this limit will be rejected until the next period begins.
     */
    private long maxEvents;

    /**
     * The maximum number of rules that can be defined for this tenant.
     * Attempts to create rules beyond this limit will be rejected.
     */
    private long maxRules;

    /**
     * The maximum number of segments that can be defined for this tenant.
     * Attempts to create segments beyond this limit will be rejected.
     */
    private long maxSegments;

    /**
     * The maximum storage size in bytes that this tenant can use.
     * This includes all data associated with the tenant including profiles,
     * events, rules, and other stored data.
     */
    private long maxStorageSize;

    /**
     * The maximum number of concurrent API requests that can be processed
     * for this tenant. Additional requests will be rejected with a 429 status
     * until ongoing requests complete.
     */
    private int maxConcurrentRequests;

    /**
     * The maximum number of API keys (both public and private) that can be
     * generated for this tenant. This includes both active and historical keys
     * stored for auditing purposes.
     */
    private int maxApiKeys;

    /**
     * The maximum number of days that data will be retained for this tenant.
     * Data older than this period will be automatically purged from the system.
     * A value of 0 indicates no automatic purging.
     */
    private long maxDataRetentionDays;

    /**
     * The maximum number of API requests that can be made per time period
     * for this tenant. Requests beyond this limit will be rejected with
     * a 429 status until the next period begins.
     */
    private long maxRequests;

    /**
     * Custom quota limits that can be defined for tenant-specific needs.
     * The map keys represent the quota type and the values represent the limits.
     * These quotas can be used to limit custom resources or actions specific
     * to certain tenant use cases.
     */
    private Map<String, Long> customQuotas = new HashMap<>();

    /**
     * Gets the maximum number of profiles allowed for the tenant.
     * @return the maximum number of profiles
     */
    public long getMaxProfiles() {
        return maxProfiles;
    }

    /**
     * Sets the maximum number of profiles allowed for the tenant.
     * @param maxProfiles the maximum number of profiles to set (must be >= 0)
     */
    public void setMaxProfiles(long maxProfiles) {
        this.maxProfiles = maxProfiles;
    }

    /**
     * Gets the maximum number of events allowed for the tenant per time period.
     * @return the maximum number of events
     */
    public long getMaxEvents() {
        return maxEvents;
    }

    /**
     * Sets the maximum number of events allowed for the tenant per time period.
     * @param maxEvents the maximum number of events to set (must be >= 0)
     */
    public void setMaxEvents(long maxEvents) {
        this.maxEvents = maxEvents;
    }

    /**
     * Gets the maximum number of rules allowed for the tenant.
     * @return the maximum number of rules
     */
    public long getMaxRules() {
        return maxRules;
    }

    /**
     * Sets the maximum number of rules allowed for the tenant.
     * @param maxRules the maximum number of rules to set (must be >= 0)
     */
    public void setMaxRules(long maxRules) {
        this.maxRules = maxRules;
    }

    /**
     * Gets the maximum number of segments allowed for the tenant.
     * @return the maximum number of segments
     */
    public long getMaxSegments() {
        return maxSegments;
    }

    /**
     * Sets the maximum number of segments allowed for the tenant.
     * @param maxSegments the maximum number of segments to set (must be >= 0)
     */
    public void setMaxSegments(long maxSegments) {
        this.maxSegments = maxSegments;
    }

    /**
     * Gets the maximum storage size in bytes allowed for the tenant.
     * @return the maximum storage size in bytes
     */
    public long getMaxStorageSize() {
        return maxStorageSize;
    }

    /**
     * Sets the maximum storage size in bytes allowed for the tenant.
     * @param maxStorageSize the maximum storage size in bytes to set (must be >= 0)
     */
    public void setMaxStorageSize(long maxStorageSize) {
        this.maxStorageSize = maxStorageSize;
    }

    /**
     * Gets the maximum number of concurrent requests allowed for the tenant.
     * @return the maximum number of concurrent requests
     */
    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    /**
     * Sets the maximum number of concurrent requests allowed for the tenant.
     * @param maxConcurrentRequests the maximum number of concurrent requests to set (must be >= 0)
     */
    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
    }

    /**
     * Gets the maximum number of API keys allowed for the tenant.
     * @return the maximum number of API keys
     */
    public int getMaxApiKeys() {
        return maxApiKeys;
    }

    /**
     * Sets the maximum number of API keys allowed for the tenant.
     * @param maxApiKeys the maximum number of API keys to set (must be >= 0)
     */
    public void setMaxApiKeys(int maxApiKeys) {
        this.maxApiKeys = maxApiKeys;
    }

    /**
     * Gets the maximum number of days to retain data for the tenant.
     * @return the maximum data retention period in days (0 for no limit)
     */
    public long getMaxDataRetentionDays() {
        return maxDataRetentionDays;
    }

    /**
     * Sets the maximum number of days to retain data for the tenant.
     * @param maxDataRetentionDays the maximum data retention period in days to set (0 for no limit, must be >= 0)
     */
    public void setMaxDataRetentionDays(long maxDataRetentionDays) {
        this.maxDataRetentionDays = maxDataRetentionDays;
    }

    /**
     * Gets the maximum number of API requests allowed per time period.
     * @return the maximum number of requests per time period
     */
    public long getMaxRequests() {
        return maxRequests;
    }

    /**
     * Sets the maximum number of API requests allowed per time period.
     * @param maxRequests the maximum number of requests to set (must be >= 0)
     */
    public void setMaxRequests(long maxRequests) {
        this.maxRequests = maxRequests;
    }

    /**
     * Gets the custom quotas map. Custom quotas can be used to define
     * tenant-specific resource limits beyond the standard quotas.
     * @return map of custom quota types to their limits
     */
    public Map<String, Long> getCustomQuotas() {
        return customQuotas;
    }

    /**
     * Sets the custom quotas map. Custom quotas can be used to define
     * tenant-specific resource limits beyond the standard quotas.
     * @param customQuotas map of custom quota types to their limits (values must be >= 0)
     */
    public void setCustomQuotas(Map<String, Long> customQuotas) {
        this.customQuotas = customQuotas;
    }
}
