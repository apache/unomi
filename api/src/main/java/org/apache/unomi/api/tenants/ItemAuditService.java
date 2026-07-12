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
 * Records create, update, and delete operations on configuration items.
 * Provides an audit trail for who changed segments, rules, and similar entities.
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
     * Returns configuration items modified after the given date.
     *
     * @param tenantId tenant identifier
     * @param since earliest modification timestamp (exclusive)
     * @return modified items
     */
    List<Item> getModifiedItems(String tenantId, Date since);

    /**
     * Returns items modified since the last sync with a source instance.
     *
     * @param tenantId tenant identifier
     * @param sourceInstanceId source cluster node identifier
     * @return modified items since last sync
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
     * Returns the last sync timestamp for a tenant and source instance pair.
     *
     * @param tenantId tenant identifier
     * @param sourceInstanceId source cluster node identifier
     * @return last synchronization date
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