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

package org.apache.unomi.api.services;

import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.api.tasks.TaskExecutor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service for scheduling and managing tasks in a cluster-aware manner
 */
public interface SchedulerService {

    /**
     * Get the single-threaded scheduler for local tasks
     * @return the scheduler
     */
    ScheduledExecutorService getScheduleExecutorService();

    /**
     * Get the multi-threaded shared scheduler for local tasks
     * @return the shared scheduler
     */
    ScheduledExecutorService getSharedScheduleExecutorService();

    /**
     * Create a new persistent scheduled task
     * @param taskType the type of task
     * @param parameters task parameters
     * @param initialDelay initial delay before first execution
     * @param period period between executions
     * @param timeUnit time unit for delay and period
     * @param fixedRate whether to use fixed rate or fixed delay
     * @param oneShot whether this is a one-time task
     * @param allowParallelExecution whether parallel execution is allowed
     * @param persistent whether the task should be stored in the persistence service or only in local memory.
     * @return the created task
     */
    ScheduledTask createTask(String taskType,
                           Map<String, Object> parameters,
                           long initialDelay,
                           long period,
                           TimeUnit timeUnit,
                           boolean fixedRate,
                           boolean oneShot,
                           boolean allowParallelExecution,
                           boolean persistent);

    /**
     * Schedule an existing task
     * @param task the task to schedule
     */
    void scheduleTask(ScheduledTask task);

    /**
     * Cancel a scheduled task
     * @param taskId the task ID to cancel
     */
    void cancelTask(String taskId);

    /**
     * Get all scheduled tasks
     * @return list of all tasks
     */
    List<ScheduledTask> getAllTasks();

    /**
     * Get a specific task by ID
     * @param taskId the task ID
     * @return the task or null if not found
     */
    ScheduledTask getTask(String taskId);

    /**
     * Register a task executor
     * @param executor the executor to register
     */
    void registerTaskExecutor(TaskExecutor executor);

    /**
     * Unregister a task executor
     * @param executor the executor to unregister
     */
    void unregisterTaskExecutor(TaskExecutor executor);

    /**
     * Check if this node is a task executor node
     * @return true if this node executes tasks
     */
    boolean isExecutorNode();

    /**
     * Get the node ID of this scheduler instance
     * @return the node ID
     */
    String getNodeId();

    /**
     * Get tasks with the specified status
     * @param status the task status to filter by
     * @param offset the starting offset
     * @param size the maximum number of tasks to return
     * @param sortBy optional sort field
     * @return partial list of matching tasks
     */
    PartialList<ScheduledTask> getTasksByStatus(ScheduledTask.TaskStatus status, int offset, int size, String sortBy);

    /**
     * Get tasks for a specific executor type
     * @param taskType the task type to filter by
     * @param offset the starting offset
     * @param size the maximum number of tasks to return
     * @param sortBy optional sort field
     * @return partial list of matching tasks
     */
    PartialList<ScheduledTask> getTasksByType(String taskType, int offset, int size, String sortBy);

    /**
     * Retry a failed task
     * @param taskId the task ID to retry
     * @param resetFailureCount whether to reset the failure count
     */
    void retryTask(String taskId, boolean resetFailureCount);

    /**
     * Resume a crashed task from its last checkpoint
     * @param taskId the task ID to resume
     */
    void resumeTask(String taskId);

    /**
     * Update task configuration
     * @param taskId the task ID to update
     * @param maxRetries new maximum retry count
     * @param retryDelay new retry delay in milliseconds
     */
    void updateTaskConfig(String taskId, int maxRetries, long retryDelay);

    /**
     * Check for crashed tasks from other nodes and attempt recovery
     */
    void recoverCrashedTasks();

    /**
     * Creates a simple recurring task with default settings.
     * Useful for services that just need periodic execution.
     *
     * @param taskType unique identifier for the task type
     * @param period time between executions
     * @param timeUnit unit for the period
     * @param runnable the code to execute
     * @param persistent whether the task should be stored in the persistence service or only stored in local memory
     * @return the created and scheduled task
     */
    ScheduledTask createRecurringTask(String taskType, long period, TimeUnit timeUnit, Runnable runnable, boolean persistent);

    /**
     * Creates a new task builder for fluent task creation.
     * Example usage:
     * <pre>
     * schedulerService.newTask("myTask")
     *     .withPeriod(1, TimeUnit.HOURS)
     *     .withSimpleExecutor(() -> doSomething())
     *     .schedule();
     * </pre>
     *
     * @param taskType unique identifier for the task type
     * @return a builder to configure and create the task
     */
    TaskBuilder newTask(String taskType);

    /**
     * Builder interface for fluent task creation
     */
    interface TaskBuilder {
        TaskBuilder withParameters(Map<String, Object> parameters);
        TaskBuilder withInitialDelay(long initialDelay, TimeUnit timeUnit);
        TaskBuilder withPeriod(long period, TimeUnit timeUnit);
        TaskBuilder withFixedDelay();
        TaskBuilder withFixedRate();
        TaskBuilder asOneShot();
        TaskBuilder disallowParallelExecution();
        TaskBuilder withExecutor(TaskExecutor executor);
        TaskBuilder withSimpleExecutor(Runnable runnable);
        TaskBuilder nonPersistent();
        TaskBuilder runOnAllNodes();
        ScheduledTask schedule();
    }
}
