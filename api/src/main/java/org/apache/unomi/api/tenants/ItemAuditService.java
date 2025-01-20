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

import org.apache.unomi.api.Item;

import java.util.Date;
import java.util.List;

/**
 * A service to track and audit changes to items.
 */
public interface ItemAuditService {
    /**
     * Records the creation of an item.
     *
     * @param item the item being created
     * @param userId the user performing the creation
     */
    void auditCreate(Item item, String userId);

    /**
     * Records the update of an item.
     *
     * @param item the item being updated
     * @param userId the user performing the update
     */
    void auditUpdate(Item item, String userId);

    /**
     * Records the deletion of an item.
     *
     * @param item the item being deleted
     * @param userId the user performing the deletion
     */
    void auditDelete(Item item, String userId);

    /**
     * Retrieves items modified since a specific date.
     *
     * @param tenantId the tenant ID to filter by
     * @param since the date to check modifications from
     * @return a list of modified items
     */
    List<Item> getModifiedItems(String tenantId, Date since);

    /**
     * Retrieves items modified since the last synchronization.
     *
     * @param tenantId the tenant ID to filter by
     * @param sourceInstanceId the source instance ID
     * @return a list of modified items
     */
    List<Item> getModifiedItemsSinceLastSync(String tenantId, String sourceInstanceId);

    /**
     * Updates the last synchronization date.
     *
     * @param tenantId the tenant ID
     * @param sourceInstanceId the source instance ID
     * @param syncDate the synchronization date to set
     */
    void updateLastSyncDate(String tenantId, String sourceInstanceId, Date syncDate);

    /**
     * Retrieves the last synchronization date.
     *
     * @param tenantId the tenant ID
     * @param sourceInstanceId the source instance ID
     * @return the last synchronization date
     */
    Date getLastSyncDate(String tenantId, String sourceInstanceId);

    /**
     * Updates the modification metadata of an item.
     *
     * @param item the item to update
     * @param userId the user performing the modification
     */
    default void updateModificationMetadata(Item item, String userId) {
        item.setLastModifiedBy(userId);
        item.setLastModificationDate(new Date());
    }
} 