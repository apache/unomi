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
 * This class manages various resource constraints including limits on profiles,
 * events, rules, segments, storage, API keys, and data retention periods.
 */
public class ResourceQuota {
    private long maxProfiles;
    private long maxEvents;
    private long maxRules;
    private long maxSegments;
    private long maxStorageSize;
    private int maxConcurrentRequests;
    private int maxApiKeys;
    private long maxDataRetentionDays;
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
     * @param maxProfiles the maximum number of profiles to set
     */
    public void setMaxProfiles(long maxProfiles) {
        this.maxProfiles = maxProfiles;
    }

    /**
     * Gets the maximum number of events allowed for the tenant.
     * @return the maximum number of events
     */
    public long getMaxEvents() {
        return maxEvents;
    }

    /**
     * Sets the maximum number of events allowed for the tenant.
     * @param maxEvents the maximum number of events to set
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
     * @param maxRules the maximum number of rules to set
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
     * @param maxSegments the maximum number of segments to set
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
     * @param maxStorageSize the maximum storage size in bytes to set
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
     * @param maxConcurrentRequests the maximum number of concurrent requests to set
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
     * @param maxApiKeys the maximum number of API keys to set
     */
    public void setMaxApiKeys(int maxApiKeys) {
        this.maxApiKeys = maxApiKeys;
    }

    /**
     * Gets the maximum number of days to retain data for the tenant.
     * @return the maximum data retention period in days
     */
    public long getMaxDataRetentionDays() {
        return maxDataRetentionDays;
    }

    /**
     * Sets the maximum number of days to retain data for the tenant.
     * @param maxDataRetentionDays the maximum data retention period in days to set
     */
    public void setMaxDataRetentionDays(long maxDataRetentionDays) {
        this.maxDataRetentionDays = maxDataRetentionDays;
    }

    /**
     * Gets the custom quotas map.
     * @return map of custom quota types to their limits
     */
    public Map<String, Long> getCustomQuotas() {
        return customQuotas;
    }

    /**
     * Sets the custom quotas map.
     * @param customQuotas map of custom quota types to their limits
     */
    public void setCustomQuotas(Map<String, Long> customQuotas) {
        this.customQuotas = customQuotas;
    }
}
