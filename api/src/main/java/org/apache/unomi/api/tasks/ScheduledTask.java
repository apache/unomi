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
package org.apache.unomi.api.tasks;

import org.apache.unomi.api.Item;

import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Represents a persistent scheduled task that can be executed across a cluster.
 * This class provides a comprehensive model for task scheduling and execution with features including:
 * <ul>
 *   <li>Task lifecycle management through states (SCHEDULED, WAITING, RUNNING, etc.)</li>
 *   <li>Lock management for cluster coordination</li>
 *   <li>Execution history and checkpoint data for recovery</li>
 *   <li>Support for one-shot and periodic execution</li>
 *   <li>Task dependencies and parallel execution control</li>
 *   <li>Cluster-wide task distribution</li>
 * </ul>
 */
public class ScheduledTask extends Item implements Serializable {

    public static final String ITEM_TYPE = "scheduledTask";

    /**
     * Enumeration of possible task states in its lifecycle.
     * Tasks transition between these states based on execution progress and cluster conditions.
     */
    public enum TaskStatus {
        /** Task is scheduled but not yet running */
        SCHEDULED,
        /** Task is waiting for a lock to be released or dependencies to complete */
        WAITING,
        /** Task is currently executing */
        RUNNING,
        /** Task has completed successfully */
        COMPLETED,
        /** Task failed with an error */
        FAILED,
        /** Task was explicitly cancelled */
        CANCELLED,
        /** Task crashed due to node failure or other unexpected conditions */
        CRASHED
    }

    private String taskType;
    private Map<String, Object> parameters;
    private String executingNodeId;  // The ID of the node currently executing this task
    /**
     * The initial delay before first execution, in the specified time unit.
     */
    private long initialDelay;
    private long period;
    private TimeUnit timeUnit;
    private boolean fixedRate;
    /**
     * Gets the date of the last execution attempt.
     *
     * @return the last execution date or null if never executed
     */
    private Date lastExecutionDate;
    /**
     * Gets the node ID that last executed this task.
     *
     * @return the ID of the last executing node
     */
    private String lastExecutedBy;
    /**
     * Gets the error message from the last failed execution.
     *
     * @return the last error message or null if no error
     */
    private String lastError;
    private boolean enabled;
    private String lockOwner;
    /**
     * Gets the date when the current lock was acquired.
     *
     * @return the lock acquisition date or null if unlocked
     */
    private Date lockDate;
    private boolean oneShot;
    private boolean allowParallelExecution;
    /**
     * Gets the current task status.
     *
     * @return the current status
     */
    private TaskStatus status;
    private Map<String, Object> statusDetails;
    /**
     * Gets the next scheduled execution date for periodic tasks.
     *
     * @return the next scheduled execution date or null if not scheduled
     */
    private Date nextScheduledExecution;
    /**
     * Gets the number of consecutive execution failures.
     *
     * @return the failure count
     */
    private int failureCount;
    /**
     * Gets the number of successful executions.
     *
     * @return the success count
     */
    private int successCount;
    /**
     * Gets the maximum number of retry attempts after failures.
     * For one-shot tasks:
     * - When a task fails, it will be automatically retried up to this many times
     * - Each retry attempt occurs after waiting for retryDelay
     * - After reaching this limit, the task remains in FAILED state until manually retried
     *
     * For periodic tasks:
     * - Retries only apply within a single scheduled execution
     * - If retries are exhausted, the task will still attempt its next scheduled execution
     * - The next scheduled execution resets the failure count
     *
     * A value of 0 means no automatic retries in either case.
     *
     * @return the maximum retry count
     */
    private int maxRetries;
    /**
     * Gets the delay between retry attempts.
     * For one-shot tasks:
     * - This delay is applied between each retry attempt after a failure
     * - Helps prevent rapid-fire retries that could overload the system
     *
     * For periodic tasks:
     * - This delay is used between retry attempts within a single scheduled execution
     * - Does not affect the task's configured period/scheduling
     *
     * @return the retry delay in milliseconds
     */
    private long retryDelay;
    /**
     * Gets the name of the current execution step.
     * This is used to track progress through multi-step tasks.
     *
     * @return the current step name or null if not set
     */
    private String currentStep;
    /**
     * Gets the checkpoint data for task resumption.
     * This data allows a task to resume from where it left off after a crash.
     *
     * @return map of checkpoint data or null if no checkpoint
     */
    private Map<String, Object> checkpointData;
    private boolean persistent = true;  // By default tasks are persistent
    private boolean runOnAllNodes = false;  // By default tasks run on a single node
    /**
     * Indicates if this is a system task that should not be recreated on startup.
     * System tasks are created by the system during initialization and should be
     * preserved across restarts.
     */
    private boolean systemTask = false;  // By default tasks are not system tasks
    /**
     * Gets the task type that this task is waiting for a lock on.
     * This is used when tasks of the same type cannot run in parallel.
     *
     * @return the task type being waited on or null if not waiting
     */
    private String waitingForTaskType;
    private Set<String> dependsOn = new HashSet<>();  // Set of task IDs this task depends on
    private Set<String> waitingOnTasks = new HashSet<>();  // Set of task IDs this task is currently waiting on

    public ScheduledTask() {
        super();
        this.status = TaskStatus.SCHEDULED;
        this.failureCount = 0;
        this.maxRetries = 3;
        this.retryDelay = 60000; // 1 minute default retry delay
    }

    /**
     * Gets the task type identifier.
     * The task type determines which executor will handle this task.
     *
     * @return the task type identifier
     */
    public String getTaskType() {
        return taskType;
    }

    /**
     * Sets the task type identifier.
     *
     * @param taskType the task type identifier
     */
    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    /**
     * Gets the task parameters.
     * These parameters are passed to the task executor during execution.
     *
     * @return map of task parameters
     */
    public Map<String, Object> getParameters() {
        return parameters;
    }

    /**
     * Sets the task parameters.
     *
     * @param parameters map of task parameters
     */
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    /**
     * Gets the initial delay before first execution.
     *
     * @return the initial delay in the specified time unit
     */
    public long getInitialDelay() {
        return initialDelay;
    }

    /**
     * Sets the initial delay before first execution.
     *
     * @param initialDelay the initial delay in the specified time unit
     */
    public void setInitialDelay(long initialDelay) {
        this.initialDelay = initialDelay;
    }

    /**
     * Gets the period between successive task executions.
     * A period of 0 indicates a one-time task and will automatically set oneShot=true.
     *
     * @return the period between executions in the specified time unit
     */
    public long getPeriod() {
        return period;
    }

    /**
     * Sets the period for task execution.
     * A period of 0 indicates a one-time task and will automatically set oneShot=true.
     * A positive period indicates a recurring task and is incompatible with oneShot=true.
     *
     * @param period the period between successive task executions
     * @throws IllegalArgumentException if period is negative or if period > 0 and oneShot=true
     */
    public void setPeriod(long period) {
        if (period < 0) {
            throw new IllegalArgumentException("Period cannot be negative");
        }
        if (period > 0 && oneShot) {
            throw new IllegalArgumentException("One-shot tasks cannot have a period");
        }
        this.period = period;
        if (period == 0) {
            this.oneShot = true;
        }
    }

    /**
     * Gets the time unit for delay and period values.
     *
     * @return the time unit used for scheduling
     */
    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    /**
     * Sets the time unit for delay and period values.
     *
     * @param timeUnit the time unit to use for scheduling
     */
    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    /**
     * Gets whether this task uses fixed-rate scheduling.
     * If true, executions are scheduled at fixed intervals from the start time.
     * If false, executions are scheduled at fixed delays from completion.
     *
     * @return true if using fixed-rate scheduling
     */
    public boolean isFixedRate() {
        return fixedRate;
    }

    /**
     * Sets whether this task uses fixed-rate scheduling.
     *
     * @param fixedRate true to use fixed-rate scheduling, false for fixed-delay
     */
    public void setFixedRate(boolean fixedRate) {
        this.fixedRate = fixedRate;
    }

    /**
     * Gets the date of the last execution attempt.
     *
     * @return the last execution date or null if never executed
     */
    public Date getLastExecutionDate() {
        return lastExecutionDate;
    }

    /**
     * Sets the date of the last execution attempt.
     *
     * @param lastExecutionDate the last execution date
     */
    public void setLastExecutionDate(Date lastExecutionDate) {
        this.lastExecutionDate = lastExecutionDate;
    }

    /**
     * Gets the node ID that last executed this task.
     *
     * @return the ID of the last executing node
     */
    public String getLastExecutedBy() {
        return lastExecutedBy;
    }

    /**
     * Sets the node ID that last executed this task.
     *
     * @param lastExecutedBy the ID of the executing node
     */
    public void setLastExecutedBy(String lastExecutedBy) {
        this.lastExecutedBy = lastExecutedBy;
    }

    /**
     * Gets the error message from the last failed execution.
     *
     * @return the last error message or null if no error
     */
    public String getLastError() {
        return lastError;
    }

    /**
     * Sets the error message from a failed execution.
     *
     * @param lastError the error message
     */
    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    /**
     * Gets whether this task is enabled.
     * Disabled tasks will not be executed.
     *
     * @return true if the task is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether this task is enabled.
     *
     * @param enabled true to enable the task, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Gets the ID of the node that currently holds the execution lock.
     *
     * @return the current lock owner's node ID or null if unlocked
     */
    public String getLockOwner() {
        return lockOwner;
    }

    /**
     * Sets the ID of the node that holds the execution lock.
     *
     * @param lockOwner the lock owner's node ID
     */
    public void setLockOwner(String lockOwner) {
        this.lockOwner = lockOwner;
    }

    /**
     * Gets the date when the current lock was acquired.
     *
     * @return the lock acquisition date or null if unlocked
     */
    public Date getLockDate() {
        return lockDate;
    }

    /**
     * Sets the date when the current lock was acquired.
     *
     * @param lockDate the lock acquisition date
     */
    public void setLockDate(Date lockDate) {
        this.lockDate = lockDate;
    }

    /**
     * Returns whether this task should execute only once.
     * Tasks with period=0 are automatically marked as one-shot tasks.
     *
     * @return true if the task should execute only once
     */
    public boolean isOneShot() {
        return oneShot;
    }

    /**
     * Sets whether this task should execute only once.
     * Setting oneShot=true is incompatible with a period > 0.
     *
     * @param oneShot true if the task should execute only once
     * @throws IllegalArgumentException if oneShot=true and period > 0
     */
    public void setOneShot(boolean oneShot) {
        if (oneShot && period > 0) {
            throw new IllegalArgumentException("One-shot tasks cannot have a period");
        }
        this.oneShot = oneShot;
    }

    /**
     * Gets whether parallel execution is allowed for this task.
     * If true, multiple instances of this task can run simultaneously.
     * If false, the task uses locking to ensure only one instance runs at a time.
     *
     * @return true if parallel execution is allowed
     */
    public boolean isAllowParallelExecution() {
        return allowParallelExecution;
    }

    /**
     * Sets whether parallel execution is allowed for this task.
     *
     * @param allowParallelExecution true to allow parallel execution
     */
    public void setAllowParallelExecution(boolean allowParallelExecution) {
        this.allowParallelExecution = allowParallelExecution;
    }

    /**
     * Gets the current task status.
     *
     * @return the current status
     */
    public TaskStatus getStatus() {
        return status;
    }

    /**
     * Sets the task status.
     * Status transitions should be validated before setting.
     *
     * @param status the new status
     */
    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    /**
     * Gets additional details about the task's current status.
     * This may include execution progress, history, or other metadata.
     *
     * @return map of status details
     */
    public Map<String, Object> getStatusDetails() {
        return statusDetails;
    }

    /**
     * Sets additional details about the task's current status.
     *
     * @param statusDetails map of status details
     */
    public void setStatusDetails(Map<String, Object> statusDetails) {
        this.statusDetails = statusDetails;
    }

    /**
     * Gets the next scheduled execution date for periodic tasks.
     *
     * @return the next scheduled execution date or null if not scheduled
     */
    public Date getNextScheduledExecution() {
        return nextScheduledExecution;
    }

    /**
     * Sets the next scheduled execution date.
     *
     * @param nextScheduledExecution the next execution date
     */
    public void setNextScheduledExecution(Date nextScheduledExecution) {
        this.nextScheduledExecution = nextScheduledExecution;
    }

    /**
     * Gets the number of consecutive execution failures.
     *
     * @return the failure count
     */
    public int getFailureCount() {
        return failureCount;
    }

    /**
     * Sets the number of consecutive execution failures.
     *
     * @param failureCount the new failure count
     */
    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    /**
     * Gets the number of successful executions.
     *
     * @return the success count
     */
    public int getSuccessCount() {
        return successCount;
    }

    /**
     * Sets the number of successful executions.
     *
     * @param successCount the new success count
     */
    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    /**
     * Gets the maximum number of retry attempts after failures.
     * For one-shot tasks:
     * - When a task fails, it will be automatically retried up to this many times
     * - Each retry attempt occurs after waiting for retryDelay
     * - After reaching this limit, the task remains in FAILED state until manually retried
     *
     * For periodic tasks:
     * - Retries only apply within a single scheduled execution
     * - If retries are exhausted, the task will still attempt its next scheduled execution
     * - The next scheduled execution resets the failure count
     *
     * A value of 0 means no automatic retries in either case.
     *
     * @return the maximum retry count
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Sets the maximum number of retry attempts after failures.
     *
     * @param maxRetries the maximum retry count
     */
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    /**
     * Gets the delay between retry attempts.
     * For one-shot tasks:
     * - This delay is applied between each retry attempt after a failure
     * - Helps prevent rapid-fire retries that could overload the system
     *
     * For periodic tasks:
     * - This delay is used between retry attempts within a single scheduled execution
     * - Does not affect the task's configured period/scheduling
     *
     * @return the retry delay in milliseconds
     */
    public long getRetryDelay() {
        return retryDelay;
    }

    /**
     * Sets the delay between retry attempts.
     *
     * @param retryDelay the retry delay in milliseconds
     */
    public void setRetryDelay(long retryDelay) {
        this.retryDelay = retryDelay;
    }

    /**
     * Gets the name of the current execution step.
     * This is used to track progress through multi-step tasks.
     *
     * @return the current step name or null if not set
     */
    public String getCurrentStep() {
        return currentStep;
    }

    /**
     * Sets the name of the current execution step.
     *
     * @param currentStep the current step name
     */
    public void setCurrentStep(String currentStep) {
        this.currentStep = currentStep;
    }

    /**
     * Gets the checkpoint data for task resumption.
     * This data allows a task to resume from where it left off after a crash.
     *
     * @return map of checkpoint data or null if no checkpoint
     */
    public Map<String, Object> getCheckpointData() {
        return checkpointData;
    }

    /**
     * Sets the checkpoint data for task resumption.
     *
     * @param checkpointData map of checkpoint data
     */
    public void setCheckpointData(Map<String, Object> checkpointData) {
        this.checkpointData = checkpointData;
    }

    /**
     * Gets whether this task is stored persistently.
     * Persistent tasks survive system restarts and are visible across the cluster.
     * Non-persistent tasks exist only in memory on a single node.
     *
     * @return true if the task is persistent
     */
    public boolean isPersistent() {
        return persistent;
    }

    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    /**
     * Gets whether this task should run on all cluster nodes.
     * If false, the task runs only on executor nodes.
     *
     * @return true if the task should run on all nodes
     */
    public boolean isRunOnAllNodes() {
        return runOnAllNodes;
    }

    /**
     * Sets whether this task should run on all cluster nodes.
     *
     * @param runOnAllNodes true to run on all nodes, false for executor nodes only
     */
    public void setRunOnAllNodes(boolean runOnAllNodes) {
        this.runOnAllNodes = runOnAllNodes;
    }

    /**
     * Gets whether this task is a system task.
     * System tasks are created by the system during initialization and should be
     * preserved across restarts rather than being recreated.
     *
     * @return true if the task is a system task
     */
    public boolean isSystemTask() {
        return systemTask;
    }

    /**
     * Sets whether this task is a system task.
     *
     * @param systemTask true to mark the task as a system task
     */
    public void setSystemTask(boolean systemTask) {
        this.systemTask = systemTask;
    }

    /**
     * Gets the task type that this task is waiting for a lock on.
     * This is used when tasks of the same type cannot run in parallel.
     *
     * @return the task type being waited on or null if not waiting
     */
    public String getWaitingForTaskType() {
        return waitingForTaskType;
    }

    /**
     * Sets the task type that this task is waiting for a lock on.
     *
     * @param waitingForTaskType the task type to wait for
     */
    public void setWaitingForTaskType(String waitingForTaskType) {
        this.waitingForTaskType = waitingForTaskType;
    }

    /**
     * Gets the set of task IDs that this task depends on.
     * The task will not execute until all dependencies have completed.
     *
     * @return set of dependency task IDs
     */
    public Set<String> getDependsOn() {
        return dependsOn;
    }

    /**
     * Sets the set of task IDs that this task depends on.
     *
     * @param dependsOn set of dependency task IDs
     */
    public void setDependsOn(Set<String> dependsOn) {
        this.dependsOn = dependsOn;
    }

    /**
     * Gets the set of task IDs that this task is currently waiting on.
     * This represents the subset of dependencies that have not yet completed.
     *
     * @return set of task IDs being waited on
     */
    public Set<String> getWaitingOnTasks() {
        return waitingOnTasks;
    }

    /**
     * Sets the set of task IDs that this task is currently waiting on.
     *
     * @param waitingOnTasks set of task IDs to wait on
     */
    public void setWaitingOnTasks(Set<String> waitingOnTasks) {
        this.waitingOnTasks = waitingOnTasks;
    }

    /**
     * Adds a task dependency.
     * The task will not execute until all dependencies have completed.
     *
     * @param taskId ID of the task to depend on
     */
    public void addDependency(String taskId) {
        if (dependsOn == null) {
            dependsOn = new HashSet<>();
        }
        dependsOn.add(taskId);
    }

    /**
     * Removes a task dependency.
     *
     * @param taskId ID of the task to remove from dependencies
     */
    public void removeDependency(String taskId) {
        if (dependsOn != null) {
            dependsOn.remove(taskId);
        }
    }

    /**
     * Adds a task to the set of tasks being waited on.
     *
     * @param taskId ID of the task to wait on
     */
    public void addWaitingOnTask(String taskId) {
        if (waitingOnTasks == null) {
            waitingOnTasks = new HashSet<>();
        }
        waitingOnTasks.add(taskId);
    }

    /**
     * Removes a task from the set of tasks being waited on.
     *
     * @param taskId ID of the task to stop waiting on
     */
    public void removeWaitingOnTask(String taskId) {
        if (waitingOnTasks != null) {
            waitingOnTasks.remove(taskId);
        }
    }

    /**
     * Gets the ID of the node currently executing this task.
     * This is different from lockOwner as it specifically indicates which node
     * is actively executing the task, not just holding the lock.
     *
     * @return the ID of the executing node or null if not being executed
     */
    public String getExecutingNodeId() {
        return executingNodeId;
    }

    /**
     * Sets the ID of the node currently executing this task.
     *
     * @param executingNodeId the ID of the executing node
     */
    public void setExecutingNodeId(String executingNodeId) {
        this.executingNodeId = executingNodeId;
    }

    @Override
    public String toString() {
        return "ScheduledTask{" +
                "taskType='" + taskType + '\'' +
                ", parameters=" + parameters +
                ", executingNodeId='" + executingNodeId + '\'' +
                ", initialDelay=" + initialDelay +
                ", period=" + period +
                ", timeUnit=" + timeUnit +
                ", fixedRate=" + fixedRate +
                ", lastExecutionDate=" + lastExecutionDate +
                ", lastExecutedBy='" + lastExecutedBy + '\'' +
                ", lastError='" + lastError + '\'' +
                ", enabled=" + enabled +
                ", lockOwner='" + lockOwner + '\'' +
                ", lockDate=" + lockDate +
                ", oneShot=" + oneShot +
                ", allowParallelExecution=" + allowParallelExecution +
                ", status=" + status +
                ", statusDetails=" + statusDetails +
                ", nextScheduledExecution=" + nextScheduledExecution +
                ", failureCount=" + failureCount +
                ", successCount=" + successCount +
                ", maxRetries=" + maxRetries +
                ", retryDelay=" + retryDelay +
                ", currentStep='" + currentStep + '\'' +
                ", checkpointData=" + checkpointData +
                ", persistent=" + persistent +
                ", runOnAllNodes=" + runOnAllNodes +
                ", systemTask=" + systemTask +
                ", waitingForTaskType='" + waitingForTaskType + '\'' +
                ", dependsOn=" + dependsOn +
                ", waitingOnTasks=" + waitingOnTasks +
                '}';
    }
}
