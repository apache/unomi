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

import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.api.tasks.ScheduledTask.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Manages task state transitions and validation.
 * This class centralizes all state-related logic for scheduled tasks.
 */
public class TaskStateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskStateManager.class);

    /**
     * Enum defining valid task state transitions.
     * This ensures tasks move through states in a controlled manner.
     */
    public enum TaskTransition {
        SCHEDULE(TaskStatus.SCHEDULED, EnumSet.of(TaskStatus.WAITING, TaskStatus.CRASHED, TaskStatus.FAILED, TaskStatus.COMPLETED)),
        EXECUTE(TaskStatus.RUNNING, EnumSet.of(TaskStatus.SCHEDULED, TaskStatus.CRASHED, TaskStatus.WAITING)),
        COMPLETE(TaskStatus.COMPLETED, EnumSet.of(TaskStatus.RUNNING)),
        FAIL(TaskStatus.FAILED, EnumSet.of(TaskStatus.RUNNING)),
        CANCEL(TaskStatus.CANCELLED, EnumSet.of(TaskStatus.RUNNING, TaskStatus.SCHEDULED, TaskStatus.WAITING)),
        CRASH(TaskStatus.CRASHED, EnumSet.of(TaskStatus.RUNNING, TaskStatus.SCHEDULED)),
        WAIT(TaskStatus.WAITING, EnumSet.of(TaskStatus.SCHEDULED, TaskStatus.RUNNING));

        private final TaskStatus endState;
        private final Set<TaskStatus> validStartStates;

        TaskTransition(TaskStatus endState, Set<TaskStatus> validStartStates) {
            this.endState = endState;
            this.validStartStates = validStartStates;
        }

        public static boolean isValidTransition(TaskStatus from, TaskStatus to) {
            // Allow same state transitions during recovery
            if (from == to && from == TaskStatus.RUNNING) {
                return true;
            }
            return Arrays.stream(values())
                .filter(t -> t.endState == to)
                .anyMatch(t -> t.validStartStates.contains(from));
        }
    }

    /**
     * Updates task state with validation and state-specific updates
     */
    public void updateTaskState(ScheduledTask task, TaskStatus newStatus, String error, String nodeId) {
        TaskStatus currentStatus = task.getStatus();
        validateStateTransition(currentStatus, newStatus);

        task.setStatus(newStatus);
        if (error != null) {
            task.setLastError(error);
        }

        updateStateSpecificFields(task, newStatus, nodeId);

        LOGGER.debug("Task {} state changed from {} to {}", task.getItemId(), currentStatus, newStatus);
    }

    /**
     * Validates a state transition
     */
    private void validateStateTransition(TaskStatus currentStatus, TaskStatus newStatus) {
        if (!TaskTransition.isValidTransition(currentStatus, newStatus)) {
            throw new IllegalStateException(
                String.format("Invalid state transition from %s to %s",
                    currentStatus, newStatus));
        }
    }

    /**
     * Updates state-specific fields based on the new status
     */
    private void updateStateSpecificFields(ScheduledTask task, TaskStatus newStatus, String nodeId) {
        switch (newStatus) {
            case COMPLETED:
            case FAILED:
                clearTaskExecution(task);
                task.setLastExecutionDate(new Date());
                break;

            case CRASHED:
                preserveCrashState(task, nodeId);
                break;

            case WAITING:
                clearLockInfo(task);
                break;

            case RUNNING:
                updateRunningState(task, nodeId);
                break;
        }
    }

    private void clearTaskExecution(ScheduledTask task) {
        task.setLockOwner(null);
        task.setLockDate(null);
        task.setWaitingForTaskType(null);
        task.setCurrentStep(null);
    }

    private void preserveCrashState(ScheduledTask task, String nodeId) {
        task.setCurrentStep("CRASHED");
        Map<String, Object> details = getOrCreateStatusDetails(task);
        details.put("crashTime", new Date());
        details.put("crashedNode", task.getLockOwner());
    }

    private void clearLockInfo(ScheduledTask task) {
        task.setLockOwner(null);
        task.setLockDate(null);
    }

    private void updateRunningState(ScheduledTask task, String nodeId) {
        Map<String, Object> details = getOrCreateStatusDetails(task);
        details.put("startTime", new Date());
        details.put("executingNode", nodeId);
    }

    private Map<String, Object> getOrCreateStatusDetails(ScheduledTask task) {
        Map<String, Object> details = task.getStatusDetails();
        if (details == null) {
            details = new HashMap<>();
            task.setStatusDetails(details);
        }
        return details;
    }

    /**
     * Checks if a task can be rescheduled based on its dependencies
     */
    public boolean canRescheduleTask(ScheduledTask task, Map<String, ScheduledTask> dependencies) {
        if (task.getWaitingOnTasks() == null || task.getWaitingOnTasks().isEmpty()) {
            return true;
        }

        for (String dependencyId : task.getWaitingOnTasks()) {
            ScheduledTask dependency = dependencies.get(dependencyId);
            if (dependency != null && dependency.getStatus() != TaskStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Resets a task's waiting state and marks it as scheduled
     */
    public void resetTaskToScheduled(ScheduledTask task) {
        task.setStatus(TaskStatus.SCHEDULED);
        task.setWaitingOnTasks(null);
        task.setWaitingForTaskType(null);
    }

    /**
     * Validates task configuration
     */
    public void validateTask(ScheduledTask task, Map<String, ScheduledTask> existingTasks) {
        if (task.getTaskType() == null || task.getTaskType().trim().isEmpty()) {
            throw new IllegalArgumentException("Task type cannot be null or empty");
        }

        if (task.getPeriod() < 0) {
            throw new IllegalArgumentException("Period cannot be negative");
        }

        if (task.getTimeUnit() == null && (task.getPeriod() > 0 || task.getInitialDelay() > 0)) {
            throw new IllegalArgumentException("TimeUnit cannot be null for periodic or delayed tasks");
        }

        if (task.getPeriod() > 0 && task.isOneShot()) {
            throw new IllegalArgumentException("One-shot tasks cannot have a period");
        }

        validateDependencies(task, existingTasks);

        if (task.getMaxRetries() < 0) {
            throw new IllegalArgumentException("Max retries cannot be negative");
        }

        if (task.getRetryDelay() < 0) {
            throw new IllegalArgumentException("Retry delay cannot be negative");
        }
    }

    private void validateDependencies(ScheduledTask task, Map<String, ScheduledTask> existingTasks) {
        if (task.getDependsOn() != null) {
            for (String dependencyId : task.getDependsOn()) {
                if (dependencyId == null || dependencyId.trim().isEmpty()) {
                    throw new IllegalArgumentException("Task dependency ID cannot be null or empty");
                }
                if (!existingTasks.containsKey(dependencyId)) {
                    throw new IllegalArgumentException("Dependent task not found: " + dependencyId);
                }
            }
        }
    }

    /**
     * Calculates the next execution time for a task
     * @param task The task to calculate next execution for
     * @param isRetry Whether this calculation is for a retry attempt
     */
    public void calculateNextExecutionTime(ScheduledTask task, boolean isRetry) {
        long now = System.currentTimeMillis();

        // Handle retry case first
        if (isRetry) {
            long nextExecutionTime = now + task.getTimeUnit().toMillis(task.getRetryDelay());
            task.setNextScheduledExecution(new Date(nextExecutionTime));
            return;
        }

        // Handle one-shot tasks
        if (task.isOneShot()) {
            if (task.getLastExecutionDate() == null) {
                // For first execution
                if (task.getInitialDelay() > 0) {
                    if (task.getCreationDate() == null) {
                        task.setCreationDate(new Date(now));
                    }
                    long nextExecutionTime = task.getCreationDate().getTime() +
                        task.getTimeUnit().toMillis(task.getInitialDelay());
                    task.setNextScheduledExecution(new Date(nextExecutionTime));
                } else {
                    // Execute immediately
                    task.setNextScheduledExecution(new Date(now));
                }
            } else {
                // One-shot task already executed, clear next execution
                task.setNextScheduledExecution(null);
                task.setEnabled(false);
            }
            return;
        }

        // Handle periodic tasks
        if (task.getPeriod() > 0) {
            if (task.getLastExecutionDate() == null) {
                // First execution of periodic task
                if (task.getInitialDelay() > 0) {
                    if (task.getCreationDate() == null) {
                        task.setCreationDate(new Date(now));
                    }
                    long nextExecutionTime = task.getCreationDate().getTime() +
                        task.getTimeUnit().toMillis(task.getInitialDelay());
                    task.setNextScheduledExecution(new Date(nextExecutionTime));
                } else {
                    // Execute immediately
                    task.setNextScheduledExecution(new Date(now));
                }
            } else {
                // Subsequent executions
                if (task.isFixedRate()) {
                    // For fixed-rate, calculate from last scheduled time
                    long lastScheduledTime = task.getNextScheduledExecution() != null ?
                        task.getNextScheduledExecution().getTime() :
                        task.getLastExecutionDate().getTime();
                    long nextExecutionTime = lastScheduledTime + task.getTimeUnit().toMillis(task.getPeriod());

                    // If we're behind schedule, move to the next interval
                    while (nextExecutionTime <= now) {
                        nextExecutionTime += task.getTimeUnit().toMillis(task.getPeriod());
                    }
                    task.setNextScheduledExecution(new Date(nextExecutionTime));
                } else {
                    // For fixed-delay, calculate from completion time
                    long nextExecutionTime = now + task.getTimeUnit().toMillis(task.getPeriod());
                    task.setNextScheduledExecution(new Date(nextExecutionTime));
                }
            }
        }
    }

    /**
     * Calculates the next execution time for a task (non-retry case)
     * @param task The task to calculate next execution for
     */
    public void calculateNextExecutionTime(ScheduledTask task) {
        calculateNextExecutionTime(task, false);
    }
}
