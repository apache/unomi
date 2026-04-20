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

    List<ScheduledTask> findTasksByLockOwner(String owner);

    List<ScheduledTask> findEnabledScheduledOrWaitingTasks();

    List<ScheduledTask> findTasksByTypeAndStatus(String taskType, ScheduledTask.TaskStatus status);

    ScheduledTask getTask(String taskId);

    List<ScheduledTask> getAllTasks();

    PartialList<ScheduledTask> getTasksByStatus(ScheduledTask.TaskStatus status, int offset, int size, String sortBy);

    PartialList<ScheduledTask> getTasksByType(String taskType, int offset, int size, String sortBy);

    void purgeOldTasks();

    /**
     * Saves a task to the persistence service if it's persistent.
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
     * Finds tasks by status
     */
    List<ScheduledTask> findTasksByStatus(ScheduledTask.TaskStatus status);

    /**
     * Finds tasks with locks
     */
    List<ScheduledTask> findLockedTasks();
}
