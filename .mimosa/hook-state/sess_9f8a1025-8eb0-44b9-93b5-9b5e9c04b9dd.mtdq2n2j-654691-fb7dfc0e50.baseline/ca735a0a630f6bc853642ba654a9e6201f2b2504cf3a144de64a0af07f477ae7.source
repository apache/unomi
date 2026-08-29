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
package org.apache.unomi.api.tenants.security;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Security activity summary for one tenant over a time window.
 * {@link TenantSecurityService} fills this report with authentication events,
 * per-type counts, and aggregate statistics so operators can review access
 * patterns and failed attempts for a given period.
 */
public class SecurityAuditReport {
    private String tenantId;
    private Date startDate;
    private Date endDate;
    private List<SecurityEvent> events;
    private Map<String, Integer> eventCounts;
    private Map<String, Object> statistics;

    /**
     * Tenant covered by this audit report.
     *
     * @return tenant id
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Sets the tenant id for this audit report.
     *
     * @param tenantId tenant id
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * Inclusive start of the audit window.
     *
     * @return start date
     */
    public Date getStartDate() {
        return startDate;
    }

    /**
     * Sets the inclusive start of the audit window.
     *
     * @param startDate start date
     */
    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    /**
     * Exclusive end of the audit window.
     *
     * @return end date
     */
    public Date getEndDate() {
        return endDate;
    }

    /**
     * Sets the exclusive end of the audit window.
     *
     * @param endDate end date
     */
    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    /**
     * Security events recorded in the audit window.
     *
     * @return security events
     */
    public List<SecurityEvent> getEvents() {
        return events;
    }

    /**
     * Sets the security events for this report.
     *
     * @param events security events
     */
    public void setEvents(List<SecurityEvent> events) {
        this.events = events;
    }

    /**
     * Event counts grouped by event type.
     *
     * @return map of event type to count
     */
    public Map<String, Integer> getEventCounts() {
        return eventCounts;
    }

    /**
     * Sets event counts grouped by event type.
     *
     * @param eventCounts map of event type to count
     */
    public void setEventCounts(Map<String, Integer> eventCounts) {
        this.eventCounts = eventCounts;
    }

    /**
     * Aggregate statistics for the audit window.
     *
     * @return statistics map
     */
    public Map<String, Object> getStatistics() {
        return statistics;
    }

    /**
     * Sets aggregate statistics for the audit window.
     *
     * @param statistics statistics map
     */
    public void setStatistics(Map<String, Object> statistics) {
        this.statistics = statistics;
    }

    /**
     * Represents a security-related event.
     */
    public static class SecurityEvent {
        private String type;
        private Date timestamp;
        private String description;
        private String userId;
        private String ipAddress;
        private Map<String, Object> details;

        /**
         * Security event type identifier.
         *
         * @return event type
         */
        public String getType() {
            return type;
        }

        /**
         * Sets the type identifier for this security event.
         * @param type The type string to set.
         */
        public void setType(String type) {
            this.type = type;
        }

        /**
         * When the security event occurred.
         *
         * @return event timestamp
         */
        public Date getTimestamp() {
            return timestamp;
        }

        /**
         * Sets the timestamp for this security event.
         * @param timestamp The {@link java.util.Date} to set as
         * the event timestamp.
         */
        public void setTimestamp(Date timestamp) {
            this.timestamp = timestamp;
        }

        /**
         * Human-readable description of the security event.
         *
         * @return event description
         */
        public String getDescription() {
            return description;
        }

        /**
         * Sets the descriptive text for this security event.
         * @param description The description string to set.
         */
        public void setDescription(String description) {
            this.description = description;
        }

        /**
         * User associated with the security event.
         *
         * @return user id
         */
        public String getUserId() {
            return userId;
        }

        /**
         * Sets the user ID associated with this security event.
         * @param userId The user ID to set.
         */
        public void setUserId(String userId) {
            this.userId = userId;
        }

        /**
         * Gets the IP address associated with this security event.
         * @return The stored {@link String} IP address.
         */
        public String getIpAddress() {
            return ipAddress;
        }

        /**
         * Sets the IP address associated with this security event.
         * @param ipAddress the IP address to set.
         */
        public void setIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
        }

        /**
         * Gets a map containing additional details about the security event.
         * @return The {@link java.util.Map} of details.
         */
        public Map<String, Object> getDetails() {
            return details;
        }

        /**
         * Sets a map containing additional details about the security event.
         * @param details the map of details to set.
         */
        public void setDetails(Map<String, Object> details) {
            this.details = details;
        }
    }
}