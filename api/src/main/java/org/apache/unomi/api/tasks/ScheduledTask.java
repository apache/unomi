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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.HashSet;

/**
 * Persistent {@link Item} representing a cluster-aware scheduled task managed by {@link org.apache.unomi.api.services.SchedulerService}.
 * <p>
 * Tasks are identified by {@link #taskType} and executed by registered {@link TaskExecutor} implementations. The model supports:
 * <ul>
 *   <li>Lifecycle states via {@link TaskStatus} (scheduled, waiting, running, completed, failed, cancelled, crashed)</li>
 *   <li>Cluster coordination through lock ownership and executing node tracking</li>
 *   <li>One-shot and periodic scheduling with fixed-rate or fixed-delay semantics</li>
 *   <li>Retry, checkpoint, and dependency management for long-running or multi-step work</li>
 *   <li>Persistent storage (cluster-visible) or in-memory execution (single node)</li>
 * </ul>
 *
 * @see org.apache.unomi.api.services.SchedulerService
 * @see TaskExecutor
 */
public class ScheduledTask extends Item implements Serializable {

    /**
     * Java serialization version; Unomi does not rely on Java serialization of this type as a cross-version persistence contract.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Item type identifier for scheduled tasks, used by {@link org.apache.unomi.api.Item#getItemType()}.
     */
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
    private String executingNodeId;
    private long initialDelay;
    private long period;
    private TimeUnit timeUnit;
    private boolean fixedRate;
    private Date lastExecutionDate;
    private String lastExecutedBy;
    private String lastError;
    private boolean enabled;
    private String lockOwner;
    private Date lockDate;
    private boolean oneShot;
    private boolean allowParallelExecution;
    private TaskStatus status;
    private Map<String, Object> statusDetails;
    private Date nextScheduledExecution;
    private int failureCount;
    private int successCount;
    private int maxRetries;
    private long retryDelay;
    private String currentStep;
    private Map<String, Object> checkpointData;
    private boolean persistent = true;
    private boolean runOnAllNodes = false;
    private boolean systemTask = false;
    private String waitingForTaskType;
    private Set<String> dependsOn = new HashSet<>();
    private Set<String> waitingOnTasks = new HashSet<>();

    /**
     * Instantiates a new scheduled task with default status {@link TaskStatus#SCHEDULED},
     * {@code maxRetries} of 3, and a default {@code retryDelay} of 60 seconds.
     */
    public ScheduledTask() {
        super();
        this.status = TaskStatus.SCHEDULED;
        this.failureCount = 0;
        this.maxRetries = 3;
        this.retryDelay = 60000; // 1 minute default retry delay
    }

    /**
     * Retrieves the task type identifier.
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
     * Retrieves the task parameters.
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
     * Retrieves the initial delay before the first execution, expressed in {@link #getTimeUnit()}.
     *
     * @return the initial delay in the configured time unit
     */
    public long getInitialDelay() {
        return initialDelay;
    }

    /**
     * Sets the initial delay before the first execution, expressed in {@link #getTimeUnit()}.
     *
     * @param initialDelay the initial delay in the configured time unit
     */
    public void setInitialDelay(long initialDelay) {
        this.initialDelay = initialDelay;
    }

    /**
     * Retrieves the period between successive task executions.
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
     * Retrieves the time unit for delay and period values.
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
     * Determines whether this task uses fixed-rate scheduling.
     * If true, executions are scheduled at fixed intervals from the start time.
     * If false, executions are scheduled at fixed delays from completion.
     * 
     * @return {@code true} if using fixed-rate scheduling
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
     * Retrieves the date of the last execution attempt.
     * 
     * @return the last execution date or {@code null} if never executed
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
     * Retrieves the node ID that last executed this task.
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
     * Retrieves the error message from the last failed execution.
     * 
     * @return the last error message or {@code null} if no error
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
     * Determines whether this task is enabled.
     * Disabled tasks will not be executed.
     * 
     * @return {@code true} if the task is enabled
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
     * Retrieves the ID of the node that currently holds the execution lock.
     * 
     * @return the current lock owner's node ID or {@code null} if unlocked
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
     * Retrieves the date when the current lock was acquired.
     * 
     * @return the lock acquisition date or {@code null} if unlocked
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
     * Determines whether this task should execute only once.
     * Tasks with period=0 are automatically marked as one-shot tasks.
     * 
     * @return {@code true} if the task should execute only once
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
     * Determines whether parallel execution is allowed for this task.
     * If true, multiple instances of this task can run simultaneously.
     * If false, the task uses locking to ensure only one instance runs at a time.
     * 
     * @return {@code true} if parallel execution is allowed
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
     * Retrieves the current task status.
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
     * Retrieves additional details about the task's current status.
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
     * Retrieves the next scheduled execution date for periodic tasks.
     * 
     * @return the next scheduled execution date or {@code null} if not scheduled
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
     * Retrieves the number of consecutive execution failures.
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
     * Retrieves the number of successful executions.
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
     * Retrieves the maximum number of retry attempts after failures.
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
     * Retrieves the delay between retry attempts.
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
     * Retrieves the name of the current execution step.
     * This is used to track progress through multi-step tasks.
     * 
     * @return the current step name or {@code null} if not set
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
     * Retrieves the checkpoint data for task resumption.
     * This data allows a task to resume from where it left off after a crash.
     * 
     * @return map of checkpoint data or {@code null} if no checkpoint
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
     * Determines whether this task is stored persistently.
     * Persistent tasks survive system restarts and are visible across the cluster.
     * Non-persistent tasks exist only in memory on a single node.
     * 
     * @return {@code true} if the task is persistent
     */
    public boolean isPersistent() {
        return persistent;
    }

    /**
     * Sets whether this task is stored persistently.
     * Persistent tasks survive system restarts and are visible across the cluster.
     * Non-persistent tasks exist only in memory on a single node.
     *
     * @param persistent {@code true} to persist the task, {@code false} for in-memory execution
     */
    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    /**
     * Determines whether this task should run on all cluster nodes.
     * If false, the task runs only on executor nodes.
     * 
     * @return {@code true} if the task should run on all nodes
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
     * Determines whether this task is a system task.
     * System tasks are created by the system during initialization and should be 
     * preserved across restarts rather than being recreated.
     * 
     * @return {@code true} if the task is a system task
     */
    public boolean isSystemTask() {
        return systemTask;
    }

    /**
     * Sets whether this task is a system task.
     * System tasks are created during initialization and should be preserved across
     * restarts rather than being recreated.
     *
     * @param systemTask {@code true} to mark the task as a system task
     */
    public void setSystemTask(boolean systemTask) {
        this.systemTask = systemTask;
    }

    /**
     * Retrieves the task type that this task is waiting for a lock on.
     * This is used when tasks of the same type cannot run in parallel.
     * 
     * @return the task type being waited on or {@code null} if not waiting
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
     * Retrieves the set of task IDs that this task depends on.
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
     * Retrieves the set of task IDs that this task is currently waiting on.
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
     * Retrieves the ID of the node currently executing this task.
     * This is different from lockOwner as it specifically indicates which node
     * is actively executing the task, not just holding the lock.
     * 
     * @return the ID of the executing node or {@code null} if not being executed
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

    /**
     * Returns a diagnostic string representation of this task for logging and debugging.
     *
     * @return a string containing the main task fields
     */
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
