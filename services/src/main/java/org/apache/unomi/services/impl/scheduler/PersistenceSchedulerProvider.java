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
package org.apache.unomi.services.impl.scheduler;

import org.apache.unomi.api.ClusterNode;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.services.ClusterService;
import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class PersistenceSchedulerProvider implements SchedulerProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(PersistenceSchedulerProvider.class.getName());

    static {
        SchedulerProvider.PROPERTY_CONDITION_TYPE.setItemId("propertyCondition");
        SchedulerProvider.PROPERTY_CONDITION_TYPE.setItemType(ConditionType.ITEM_TYPE);
        SchedulerProvider.PROPERTY_CONDITION_TYPE.setVersion(1L);
        SchedulerProvider.PROPERTY_CONDITION_TYPE.setConditionEvaluator("propertyConditionEvaluator");
        SchedulerProvider.PROPERTY_CONDITION_TYPE.setQueryBuilder("propertyConditionQueryBuilder");
    };

    static {
        SchedulerProvider.BOOLEAN_CONDITION_TYPE.setItemId("booleanCondition");
        SchedulerProvider.BOOLEAN_CONDITION_TYPE.setItemType(ConditionType.ITEM_TYPE);
        SchedulerProvider.BOOLEAN_CONDITION_TYPE.setVersion(1L);
        SchedulerProvider.BOOLEAN_CONDITION_TYPE.setQueryBuilder("booleanConditionQueryBuilder");
        SchedulerProvider.BOOLEAN_CONDITION_TYPE.setConditionEvaluator("booleanConditionEvaluator");
    };

    private PersistenceService persistenceService;
    private boolean executorNode;
    private String nodeId;
    private long completedTaskTtlDays;
    private TaskLockManager lockManager;
    private ClusterService clusterService;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setExecutorNode(boolean executorNode) {
        this.executorNode = executorNode;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public void setCompletedTaskTtlDays(long completedTaskTtlDays) {
        this.completedTaskTtlDays = completedTaskTtlDays;
    }

    public void setLockManager(TaskLockManager lockManager) {
        this.lockManager = lockManager;
    }

    public void setClusterService(ClusterService clusterService) {
        this.clusterService = clusterService;
    }

    public void unsetClusterService(ClusterService clusterService) {
        this.clusterService = null;
    }

    public void postConstruct() {

    }

    public void preDestroy() {
        // Check if persistence service is still available before trying to use it
        if (persistenceService == null) {
            LOGGER.debug("Persistence service not available during shutdown, skipping lock release");
            return;
        }
        try {
            List<ScheduledTask> tasks = findTasksByLockOwner(nodeId);
            for (ScheduledTask task : tasks) {
                try {
                    if (lockManager != null) {
                        lockManager.releaseLock(task);
                    }
                } catch (Exception e) {
                    LOGGER.debug("Error releasing lock for task {} during shutdown: {}", task.getItemId(), e.getMessage());
                }
            }
            LOGGER.debug("Task locks released");
        } catch (Exception e) {
            // During shutdown, services may be unavailable - this is expected
            LOGGER.debug("Error finding locked tasks during shutdown (this is expected if services are shutting down): {}", e.getMessage());
        }
    }

    @Override
    public List<ScheduledTask> findTasksByLockOwner(String owner) {
        // Check if persistence service is available before using it
        if (persistenceService == null) {
            LOGGER.debug("Persistence service not available, returning empty list for findTasksByLockOwner");
            return new ArrayList<>();
        }
        try {
            Condition condition = new Condition(SchedulerProvider.PROPERTY_CONDITION_TYPE);
            condition.setParameter("propertyName", "lockOwner");
            condition.setParameter("comparisonOperator", "equals");
            condition.setParameter("propertyValue", owner);
            return persistenceService.query(condition, null, ScheduledTask.class, 0, -1).getList();
        } catch (Exception e) {
            // During shutdown, this is expected - only log at debug level
            LOGGER.debug("Error finding tasks by lock owner (may occur during shutdown): {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public List<ScheduledTask> findEnabledScheduledOrWaitingTasks() {
        try {
            Condition enabledCondition = new Condition(SchedulerProvider.PROPERTY_CONDITION_TYPE);
            enabledCondition.setParameter("propertyName", "enabled");
            enabledCondition.setParameter("comparisonOperator", "equals");
            enabledCondition.setParameter("propertyValue", "true");

            Condition statusCondition = new Condition(SchedulerProvider.PROPERTY_CONDITION_TYPE);
            statusCondition.setParameter("propertyName", "status");
            statusCondition.setParameter("comparisonOperator", "in");
            statusCondition.setParameter("propertyValues", Arrays.asList(
                    ScheduledTask.TaskStatus.SCHEDULED,
                    ScheduledTask.TaskStatus.WAITING
            ));

            Condition andCondition = new Condition(SchedulerProvider.BOOLEAN_CONDITION_TYPE);
            andCondition.setParameter("operator", "and");
            andCondition.setParameter("subConditions", Arrays.asList(enabledCondition, statusCondition));

            return persistenceService.query(andCondition, "creationDate:asc", ScheduledTask.class, 0, -1).getList();
        } catch (Exception e) {
            LOGGER.error("Error finding enabled scheduled or waiting tasks: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public List<ScheduledTask> findTasksByTypeAndStatus(String taskType, ScheduledTask.TaskStatus status) {
        try {
            Condition typeCondition = new Condition(SchedulerProvider.PROPERTY_CONDITION_TYPE);
            typeCondition.setParameter("propertyName", "taskType");
            typeCondition.setParameter("comparisonOperator", "equals");
            typeCondition.setParameter("propertyValue", taskType);

            Condition statusCondition = new Condition(SchedulerProvider.PROPERTY_CONDITION_TYPE);
            statusCondition.setParameter("propertyName", "status");
            statusCondition.setParameter("comparisonOperator", "equals");
            statusCondition.setParameter("propertyValue", status.toString());

            Condition andCondition = new Condition(SchedulerProvider.BOOLEAN_CONDITION_TYPE);
            andCondition.setParameter("operator", "and");
            andCondition.setParameter("subConditions", Arrays.asList(typeCondition, statusCondition));

            return persistenceService.query(andCondition, null, ScheduledTask.class, 0, -1).getList();
        } catch (Exception e) {
            LOGGER.error("Error finding tasks by type and status: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public ScheduledTask getTask(String taskId) {
        try {
            return persistenceService.load(taskId, ScheduledTask.class);
        } catch (Exception e) {
            LOGGER.error("Error loading task {}: {}", taskId, e.getMessage());
            return null;
        }
    }

    @Override
    public List<ScheduledTask> getAllTasks() {
        try {
            return persistenceService.getAllItems(ScheduledTask.class, 0, -1, null).getList();
        } catch (Exception e) {
            LOGGER.error("Error getting persistent tasks: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public PartialList<ScheduledTask> getTasksByStatus(ScheduledTask.TaskStatus status, int offset, int size, String sortBy) {
        try {
            Condition condition = new Condition(SchedulerProvider.PROPERTY_CONDITION_TYPE);
            condition.setParameter("propertyName", "status");
            condition.setParameter("comparisonOperator", "equals");
            condition.setParameter("propertyValue", status.toString());
            return persistenceService.query(condition, sortBy, ScheduledTask.class, offset, size);
        } catch (Exception e) {
            LOGGER.error("Error getting tasks by status: {}", e.getMessage());
            return new PartialList<ScheduledTask>(new ArrayList<>(), 0, 0, 0, PartialList.Relation.EQUAL);
        }
    }

    @Override
    public PartialList<ScheduledTask> getTasksByType(String taskType, int offset, int size, String sortBy) {
        try {
            Condition condition = new Condition(SchedulerProvider.PROPERTY_CONDITION_TYPE);
            condition.setParameter("propertyName", "taskType");
            condition.setParameter("comparisonOperator", "equals");
            condition.setParameter("propertyValue", taskType);
            return persistenceService.query(condition, sortBy, ScheduledTask.class, offset, size);
        } catch (Exception e) {
            LOGGER.error("Error getting tasks by type: {}", e.getMessage());
            return new PartialList<ScheduledTask>(new ArrayList<>(), 0, 0, 0, PartialList.Relation.EQUAL);
        }
    }

    @Override
    public void purgeOldTasks() {
        if (!executorNode) {
            LOGGER.debug("Not an executor node, skipping purge");
            return;
        }

        try {
            LOGGER.debug("Starting purge of old completed tasks with TTL: {} days", completedTaskTtlDays);
            long purgeBeforeTime = System.currentTimeMillis() - (completedTaskTtlDays * 24 * 60 * 60 * 1000);
            Date purgeBeforeDate = new Date(purgeBeforeTime);

            Condition statusCondition = new Condition(SchedulerProvider.PROPERTY_CONDITION_TYPE);
            statusCondition.setParameter("propertyName", "status");
            statusCondition.setParameter("comparisonOperator", "equals");
            statusCondition.setParameter("propertyValue", ScheduledTask.TaskStatus.COMPLETED.toString());

            Condition dateCondition = new Condition(SchedulerProvider.PROPERTY_CONDITION_TYPE);
            dateCondition.setParameter("propertyName", "lastExecutionDate");
            dateCondition.setParameter("comparisonOperator", "lessThanOrEqualTo");
            dateCondition.setParameter("propertyValueDate", purgeBeforeDate);

            Condition andCondition = new Condition(SchedulerProvider.BOOLEAN_CONDITION_TYPE);
            andCondition.setParameter("operator", "and");
            andCondition.setParameter("subConditions", Arrays.asList(statusCondition, dateCondition));

            persistenceService.removeByQuery(andCondition, ScheduledTask.class);
            LOGGER.debug("Completed purge of old tasks before date: {}", purgeBeforeDate);
        } catch (Exception e) {
            LOGGER.error("Error purging old tasks", e);
        }
    }

    @Override
    public boolean saveTask(ScheduledTask task) {
        if (task == null) {
            return false;
        }

        if (task.isPersistent()) {
            try {
                persistenceService.save(task);
                LOGGER.debug("Saved task {} to persistence", task.getItemId());
                return true;
            } catch (Exception e) {
                LOGGER.error("Error saving task {} to persistence", task.getItemId(), e);
                return false;
            }
        } else {
            LOGGER.error("Can't handle in-memory task saving !");
            return false;
        }
    }

    @Override
    public List<String> getActiveNodes() {
        Set<String> activeNodes = new HashSet<>();

        // Add this node
        activeNodes.add(nodeId);

        // Use ClusterService if available to get cluster nodes
        if (clusterService != null) {
            try {
                List<ClusterNode> clusterNodes = clusterService.getClusterNodes();
                if (clusterNodes != null && !clusterNodes.isEmpty()) {
                    // Consider nodes with recent heartbeats as active
                    long cutoffTime = System.currentTimeMillis() - (5 * 60 * 1000); // 5 minutes threshold

                    for (ClusterNode node : clusterNodes) {
                        if (node.getLastHeartbeat() > cutoffTime) {
                            activeNodes.add(node.getItemId());
                        }
                    }

                    LOGGER.debug("Detected active cluster nodes via ClusterService: {}", activeNodes);
                    return new ArrayList<>(activeNodes);
                }
            } catch (Exception e) {
                LOGGER.warn("Error retrieving cluster nodes from ClusterService: {}", e.getMessage());
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Error details:", e);
                }
            }
        }

        // Fallback: Look for other active nodes by checking tasks with recent locks
        try {
            // Create a condition to find tasks with recent locks
            Condition recentLocksCondition = new Condition();
            recentLocksCondition.setConditionType(SchedulerProvider.PROPERTY_CONDITION_TYPE);
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("propertyName", "lockDate");
            parameters.put("comparisonOperator", "exists");
            recentLocksCondition.setParameterValues(parameters);

            // Query for tasks with lock information
            List<ScheduledTask> recentlyLockedTasks = persistenceService.query(recentLocksCondition, "lockDate", ScheduledTask.class);

            // Get current time for filtering
            long fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000);

            // Extract unique node IDs from lock owners with recent locks
            for (ScheduledTask task : recentlyLockedTasks) {
                if (task.getLockOwner() != null && task.getLockDate() != null &&
                        task.getLockDate().getTime() > fiveMinutesAgo) {
                    activeNodes.add(task.getLockOwner());
                }
            }
        } catch (Exception e) {
            // If we can't determine active nodes, just fall back to this node only
            LOGGER.warn("Error detecting active cluster nodes: {}", e.getMessage());
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Error details:", e);
            }
        }

        LOGGER.debug("Detected active cluster nodes: {}", activeNodes);
        return new ArrayList<>(activeNodes);
    }

    @Override
    public void refreshTasks() {
        try {
            persistenceService.refreshIndex(ScheduledTask.class);
        } catch (Exception e) {
            LOGGER.error("Error refreshing task indices", e);
        }
    }

    @Override
    public List<ScheduledTask> findTasksByStatus(ScheduledTask.TaskStatus status) {
        try {
            Condition statusCondition = new Condition(SchedulerProvider.PROPERTY_CONDITION_TYPE);
            statusCondition.setParameter("propertyName", "status");
            statusCondition.setParameter("comparisonOperator", "equals");
            statusCondition.setParameter("propertyValue", status);

            return persistenceService.query(statusCondition, null, ScheduledTask.class, 0, -1).getList();
        } catch (Exception e) {
            LOGGER.error("Failed to find tasks by status: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<ScheduledTask> findLockedTasks() {
        Condition lockCondition = new Condition(SchedulerProvider.PROPERTY_CONDITION_TYPE);
        lockCondition.setParameter("propertyName", "lockOwner");
        lockCondition.setParameter("comparisonOperator", "exists");

        Condition statusCondition = new Condition(SchedulerProvider.PROPERTY_CONDITION_TYPE);
        statusCondition.setParameter("propertyName", "status");
        statusCondition.setParameter("comparisonOperator", "in");
        statusCondition.setParameter("propertyValues", Arrays.asList(
                ScheduledTask.TaskStatus.SCHEDULED,
                ScheduledTask.TaskStatus.WAITING
        ));

        Condition andCondition = new Condition(SchedulerProvider.BOOLEAN_CONDITION_TYPE);
        andCondition.setParameter("operator", "and");
        andCondition.setParameter("subConditions", Arrays.asList(lockCondition, statusCondition));

        return persistenceService.query(andCondition, null, ScheduledTask.class, 0, -1).getList();
    }

}
