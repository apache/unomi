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
 * Represents metadata used to track the state or existence of a backup
 * for a specific tenant.
 * This class encapsulates essential information linking a backup record
 * to its owner and time.
 * The {@link TenantBackupMetadata} holds two primary pieces of data: the unique
 * identifier of the tenant, stored in {@code tenantId}, and a timestamp
 * indicating when this metadata was generated or pertains to, stored
 * in {@code timestamp}.
 */
public class TenantBackupMetadata {
    private String tenantId;
    private long timestamp;

    /**
     * Retrieves the tenant ID associated with this metadata object.
     * @return The stored {@link String} tenant ID.
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Sets the tenant ID for this metadata object.
     * @param tenantId The new {@link String} tenant ID to set.
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * Retrieves the timestamp associated with this metadata object.
     * @return The stored {@link Long} timestamp.
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the timestamp for this metadata object.
     * @param timestamp The new {@link Long} timestamp to set.
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
} 