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
import java.util.concurrent.TimeUnit;

/**
 * Represents a persistent scheduled task that can be executed across a cluster
 */
public class ScheduledTask extends Item implements Serializable {

    public static final String ITEM_TYPE = "scheduledTask";

    public enum TaskStatus {
        SCHEDULED,    // Task is scheduled but not yet running
        RUNNING,      // Task is currently running
        COMPLETED,    // Task completed successfully
        FAILED,       // Task failed with an error
        CANCELLED,    // Task was cancelled
        CRASHED       // Task crashed (node failure)
    }

    private String taskType;
    private Map<String, Object> parameters;
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
    private int maxRetries;
    private long retryDelay;
    private String currentStep;
    private Map<String, Object> checkpointData;
    private boolean persistent = true;  // By default tasks are persistent
    private boolean runOnAllNodes = false;  // By default tasks run on a single node

    public ScheduledTask() {
        super();
        this.status = TaskStatus.SCHEDULED;
        this.failureCount = 0;
        this.maxRetries = 3;
        this.retryDelay = 60000; // 1 minute default retry delay
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public long getInitialDelay() {
        return initialDelay;
    }

    public void setInitialDelay(long initialDelay) {
        this.initialDelay = initialDelay;
    }

    public long getPeriod() {
        return period;
    }

    public void setPeriod(long period) {
        this.period = period;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public boolean isFixedRate() {
        return fixedRate;
    }

    public void setFixedRate(boolean fixedRate) {
        this.fixedRate = fixedRate;
    }

    public Date getLastExecutionDate() {
        return lastExecutionDate;
    }

    public void setLastExecutionDate(Date lastExecutionDate) {
        this.lastExecutionDate = lastExecutionDate;
    }

    public String getLastExecutedBy() {
        return lastExecutedBy;
    }

    public void setLastExecutedBy(String lastExecutedBy) {
        this.lastExecutedBy = lastExecutedBy;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getLockOwner() {
        return lockOwner;
    }

    public void setLockOwner(String lockOwner) {
        this.lockOwner = lockOwner;
    }

    public Date getLockDate() {
        return lockDate;
    }

    public void setLockDate(Date lockDate) {
        this.lockDate = lockDate;
    }

    public boolean isOneShot() {
        return oneShot;
    }

    public void setOneShot(boolean oneShot) {
        this.oneShot = oneShot;
    }

    public boolean isAllowParallelExecution() {
        return allowParallelExecution;
    }

    public void setAllowParallelExecution(boolean allowParallelExecution) {
        this.allowParallelExecution = allowParallelExecution;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public Map<String, Object> getStatusDetails() {
        return statusDetails;
    }

    public void setStatusDetails(Map<String, Object> statusDetails) {
        this.statusDetails = statusDetails;
    }

    public Date getNextScheduledExecution() {
        return nextScheduledExecution;
    }

    public void setNextScheduledExecution(Date nextScheduledExecution) {
        this.nextScheduledExecution = nextScheduledExecution;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(long retryDelay) {
        this.retryDelay = retryDelay;
    }

    public String getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(String currentStep) {
        this.currentStep = currentStep;
    }

    public Map<String, Object> getCheckpointData() {
        return checkpointData;
    }

    public void setCheckpointData(Map<String, Object> checkpointData) {
        this.checkpointData = checkpointData;
    }

    public boolean isPersistent() {
        return persistent;
    }

    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    public boolean isRunOnAllNodes() {
        return runOnAllNodes;
    }

    public void setRunOnAllNodes(boolean runOnAllNodes) {
        this.runOnAllNodes = runOnAllNodes;
    }

    @Override
    public String toString() {
        return "ScheduledTask{" +
                "taskType='" + taskType + '\'' +
                ", parameters=" + parameters +
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
                ", maxRetries=" + maxRetries +
                ", retryDelay=" + retryDelay +
                ", currentStep='" + currentStep + '\'' +
                ", checkpointData=" + checkpointData +
                ", persistent=" + persistent +
                ", runOnAllNodes=" + runOnAllNodes +
                '}';
    }
}
