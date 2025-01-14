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
 * Represents a security audit report for a tenant.
 * This class contains information about security-related events and statistics
 * within a specified time period.
 */
public class SecurityAuditReport {
    private String tenantId;
    private Date startDate;
    private Date endDate;
    private List<SecurityEvent> events;
    private Map<String, Integer> eventCounts;
    private Map<String, Object> statistics;

    /**
     * Gets the tenant ID associated with this report.
     * @return the tenant ID
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Sets the tenant ID associated with this report.
     * @param tenantId the tenant ID to set
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * Gets the start date of the audit period.
     * @return the start date
     */
    public Date getStartDate() {
        return startDate;
    }

    /**
     * Sets the start date of the audit period.
     * @param startDate the start date to set
     */
    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    /**
     * Gets the end date of the audit period.
     * @return the end date
     */
    public Date getEndDate() {
        return endDate;
    }

    /**
     * Sets the end date of the audit period.
     * @param endDate the end date to set
     */
    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    /**
     * Gets the list of security events.
     * @return list of security events
     */
    public List<SecurityEvent> getEvents() {
        return events;
    }

    /**
     * Sets the list of security events.
     * @param events list of security events to set
     */
    public void setEvents(List<SecurityEvent> events) {
        this.events = events;
    }

    /**
     * Gets the count of events by type.
     * @return map of event types to their counts
     */
    public Map<String, Integer> getEventCounts() {
        return eventCounts;
    }

    /**
     * Sets the count of events by type.
     * @param eventCounts map of event types to their counts
     */
    public void setEventCounts(Map<String, Integer> eventCounts) {
        this.eventCounts = eventCounts;
    }

    /**
     * Gets additional statistics about the audit period.
     * @return map of statistics
     */
    public Map<String, Object> getStatistics() {
        return statistics;
    }

    /**
     * Sets additional statistics about the audit period.
     * @param statistics map of statistics to set
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

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Date getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Date timestamp) {
            this.timestamp = timestamp;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getIpAddress() {
            return ipAddress;
        }

        public void setIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
        }

        public Map<String, Object> getDetails() {
            return details;
        }

        public void setDetails(Map<String, Object> details) {
            this.details = details;
        }
    }
} 