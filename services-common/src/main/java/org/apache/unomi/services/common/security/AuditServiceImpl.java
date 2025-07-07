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
package org.apache.unomi.services.common.security;

import org.apache.unomi.api.Item;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.tenants.AuditService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class AuditServiceImpl implements AuditService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditServiceImpl.class);

    private PersistenceService persistenceService;

    public void bindPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void unbindPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = null;
    }

    @Override
    public void auditCreate(Item item, String userId) {
        item.setCreatedBy(userId);
        item.setCreationDate(new Date());
        item.setVersion(1L);
        updateModificationMetadata(item, userId);
    }

    @Override
    public void auditUpdate(Item item, String userId) {
        updateModificationMetadata(item, userId);
        item.setVersion(item.getVersion() + 1);
    }

    @Override
    public void auditDelete(Item item, String userId) {
        updateModificationMetadata(item, userId);
    }

    @Override
    public List<Item> getModifiedItems(String tenantId, Date since) {
        if (persistenceService == null) {

        }
        Condition condition = new Condition();
        condition.setConditionTypeId("booleanCondition");
        condition.setParameter("operator", "and");
        condition.setParameter("subConditions", Arrays.asList(
            createPropertyCondition("metadata.tenantId", "equals", tenantId),
            createPropertyCondition("metadata.lastModificationDate", "greaterThan", since.getTime())
        ));
        return persistenceService.query(condition, "metadata.lastModificationDate", Item.class);
    }

    private Condition createPropertyCondition(String propertyName, String operator, Object value) {
        Condition condition = new Condition();
        condition.setConditionTypeId("propertyCondition");
        condition.setParameter("propertyName", propertyName);
        condition.setParameter("comparisonOperator", operator);
        condition.setParameter("propertyValue", value);
        return condition;
    }

    @Override
    public List<Item> getModifiedItemsSinceLastSync(String tenantId, String sourceInstanceId) {
        Date lastSync = getLastSyncDate(tenantId, sourceInstanceId);
        return getModifiedItems(tenantId, lastSync);
    }

    @Override
    public void updateLastSyncDate(String tenantId, String sourceInstanceId, Date syncDate) {
        if (persistenceService == null) {
            return;
        }
        Condition condition = new Condition();
        condition.setConditionTypeId("booleanCondition");
        condition.setParameter("operator", "and");
        condition.setParameter("subConditions", Arrays.asList(
            createPropertyCondition("metadata.tenantId", "equals", tenantId),
            createPropertyCondition("metadata.sourceInstanceId", "equals", sourceInstanceId)
        ));
        Map<String, Object> scriptParams = new HashMap<>();
        scriptParams.put("syncDate", syncDate);
        persistenceService.updateWithQueryAndScript(Item.class,
            new String[]{"ctx._source.metadata.lastSyncDate = params.syncDate"},
            new Map[]{scriptParams},
            new Condition[]{condition});
    }

    @Override
    public Date getLastSyncDate(String tenantId, String sourceInstanceId) {
        if (persistenceService == null) {
            return null;
        }
        Condition condition = new Condition();
        condition.setConditionTypeId("booleanCondition");
        condition.setParameter("operator", "and");
        condition.setParameter("subConditions", Arrays.asList(
            createPropertyCondition("metadata.tenantId", "equals", tenantId),
            createPropertyCondition("metadata.sourceInstanceId", "equals", sourceInstanceId)
        ));
        List<Item> items = persistenceService.query(condition, null, Item.class);
        if (items.isEmpty()) {
            return new Date(0L);
        }
        Date lastSyncDate = items.get(0).getLastSyncDate();
        return lastSyncDate != null ? lastSyncDate : new Date(0L);
    }

    @Override
    public void logTenantOperation(String tenantId, String operation) {
        LOGGER.info("Tenant operation: {} performed on tenant {}", operation, tenantId);
    }

    public void updateModificationMetadata(Item item, String userId) {
        item.setLastModifiedBy(userId);
        item.setLastModificationDate(new Date());
    }
}
