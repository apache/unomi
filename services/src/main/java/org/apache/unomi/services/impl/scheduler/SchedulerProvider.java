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

import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.tasks.ScheduledTask;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface for scheduler providers that handle task execution with different storage strategies.
 *
 * Providers implement different approaches to task storage and execution:
 * - Memory providers for fast, non-persistent tasks
 * - Persistence providers for durable, cluster-aware tasks
 *
 * Each provider is responsible for:
 * - Task lifecycle management within its domain
 * - Appropriate locking mechanisms
 * - Provider-specific capabilities and limitations
 */
public interface SchedulerProvider {

    ConditionType PROPERTY_CONDITION_TYPE = new ConditionType();
    ConditionType BOOLEAN_CONDITION_TYPE = new ConditionType();

    /**
     * Finds tasks currently locked by the given owner node.
     *
     * @param owner the lock owner node ID
     * @return tasks locked by the owner
     */
    List<ScheduledTask> findTasksByLockOwner(String owner);

    /**
     * Finds enabled tasks in SCHEDULED or WAITING status.
     *
     * @return matching tasks
     */
    List<ScheduledTask> findEnabledScheduledOrWaitingTasks();

    /**
     * Finds tasks of the given type and status.
     *
     * @param taskType the task type
     * @param status the task status
     * @return matching tasks
     */
    List<ScheduledTask> findTasksByTypeAndStatus(String taskType, ScheduledTask.TaskStatus status);

    /**
     * Loads a task by ID.
     *
     * @param taskId the task ID
     * @return the task, or null if not found
     */
    ScheduledTask getTask(String taskId);

    /**
     * Returns all tasks from this provider.
     *
     * @return all tasks
     */
    List<ScheduledTask> getAllTasks();

    /**
     * Returns a paginated list of tasks with the given status.
     *
     * @param status the task status filter
     * @param offset pagination offset
     * @param size page size (-1 for all)
     * @param sortBy sort field
     * @return paginated task list
     */
    PartialList<ScheduledTask> getTasksByStatus(ScheduledTask.TaskStatus status, int offset, int size, String sortBy);

    /**
     * Returns a paginated list of tasks of the given type.
     *
     * @param taskType the task type filter
     * @param offset pagination offset
     * @param size page size (-1 for all)
     * @param sortBy sort field
     * @return paginated task list
     */
    PartialList<ScheduledTask> getTasksByType(String taskType, int offset, int size, String sortBy);

    /**
     * Removes completed tasks older than the configured TTL.
     */
    void purgeOldTasks();

    /**
     * Permanently removes a task record from storage.
     *
     * @param taskId the task ID to remove
     */
    void deleteTask(String taskId);

    /**
     * Saves a task to the persistence service if it's persistent.
     *
     * @param task The task to save
     * @return true if the task was successfully saved, false otherwise
     */
    boolean saveTask(ScheduledTask task);

    /**
     * Returns the list of currently active cluster nodes.
     * This is used for node affinity in the distributed locking mechanism.
     *
     * This method is designed to handle the case when ClusterService is not available (null),
     * which can happen during startup when services are being initialized in a particular order,
     * or in standalone mode. When ClusterService is null, this method will return just the current
     * node, effectively making this a single-node operation.
     *
     * @return List of active node IDs
     */
    List<String> getActiveNodes();

    /**
     * Refreshes the task indices to ensure up-to-date view.
     * This is used by the distributed locking mechanism to ensure
     * all nodes see the latest task state.
     */
    void refreshTasks();

    /**
     * Finds tasks with the given status.
     *
     * @param status the task status filter
     * @return matching tasks
     */
    List<ScheduledTask> findTasksByStatus(ScheduledTask.TaskStatus status);

    /**
     * Finds tasks that currently hold a lock.
     *
     * @return locked tasks
     */
    List<ScheduledTask> findLockedTasks();
}
