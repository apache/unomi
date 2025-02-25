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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Manages task validation, including configuration validation,
 * dependency validation, and state transition validation.
 */
public class TaskValidationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskValidationManager.class);

    /**
     * Validates task configuration and dependencies
     */
    public void validateTask(ScheduledTask task, Map<String, ScheduledTask> existingTasks) {
        validateBasicConfiguration(task);
        validateSchedulingConfiguration(task);
        validateDependencies(task, existingTasks);
        validateRetryConfiguration(task);
        validateExecutionConfiguration(task);
    }

    private void validateBasicConfiguration(ScheduledTask task) {
        if (task.getTaskType() == null || task.getTaskType().trim().isEmpty()) {
            throw new IllegalArgumentException("Task type cannot be null or empty");
        }

        if (task.getItemId() == null || task.getItemId().trim().isEmpty()) {
            throw new IllegalArgumentException("Task ID cannot be null or empty");
        }
    }

    private void validateSchedulingConfiguration(ScheduledTask task) {
        if (task.getPeriod() < 0) {
            throw new IllegalArgumentException("Period cannot be negative");
        }

        if (task.getInitialDelay() < 0) {
            throw new IllegalArgumentException("Initial delay cannot be negative");
        }

        if (task.getTimeUnit() == null && (task.getPeriod() > 0 || task.getInitialDelay() > 0)) {
            throw new IllegalArgumentException("TimeUnit cannot be null for periodic or delayed tasks");
        }

        if (task.getPeriod() > 0 && task.isOneShot()) {
            throw new IllegalArgumentException("One-shot tasks cannot have a period");
        }
    }

    private void validateDependencies(ScheduledTask task, Map<String, ScheduledTask> existingTasks) {
        if (task.getDependsOn() != null) {
            for (String dependencyId : task.getDependsOn()) {
                validateDependency(dependencyId, existingTasks);
            }
            validateDependencyCycles(task, existingTasks);
        }
    }

    private void validateDependency(String dependencyId, Map<String, ScheduledTask> existingTasks) {
        if (dependencyId == null || dependencyId.trim().isEmpty()) {
            throw new IllegalArgumentException("Task dependency ID cannot be null or empty");
        }
        if (!existingTasks.containsKey(dependencyId)) {
            throw new IllegalArgumentException("Dependent task not found: " + dependencyId);
        }
    }

    private void validateDependencyCycles(ScheduledTask task, Map<String, ScheduledTask> existingTasks) {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        detectCycle(task.getItemId(), existingTasks, visited, recursionStack);
    }

    private void detectCycle(String taskId, Map<String, ScheduledTask> existingTasks,
                           Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(taskId)) {
            throw new IllegalArgumentException("Circular dependency detected involving task: " + taskId);
        }

        if (!visited.contains(taskId)) {
            visited.add(taskId);
            recursionStack.add(taskId);

            ScheduledTask task = existingTasks.get(taskId);
            if (task != null && task.getDependsOn() != null) {
                for (String dependencyId : task.getDependsOn()) {
                    detectCycle(dependencyId, existingTasks, visited, recursionStack);
                }
            }

            recursionStack.remove(taskId);
        }
    }

    void validateRetryConfiguration(ScheduledTask task) {
        if (task.getMaxRetries() < 0) {
            throw new IllegalArgumentException("Max retries cannot be negative");
        }

        if (task.getRetryDelay() < 0) {
            throw new IllegalArgumentException("Retry delay cannot be negative");
        }
    }

    private void validateExecutionConfiguration(ScheduledTask task) {
        if (!task.isAllowParallelExecution() && task.isRunOnAllNodes()) {
            throw new IllegalArgumentException(
                "Task cannot be configured to run on all nodes while disallowing parallel execution: " +
                task.getItemId());
        }

        if (task.isOneShot() && task.isRunOnAllNodes()) {
            throw new IllegalArgumentException(
                "One-shot tasks cannot be configured to run on all nodes: " + task.getItemId());
        }
    }

    /**
     * Validates a state transition
     */
    public void validateStateTransition(ScheduledTask task, ScheduledTask.TaskStatus newStatus) {
        ScheduledTask.TaskStatus currentStatus = task.getStatus();
        if (!isValidTransition(currentStatus, newStatus)) {
            throw new IllegalStateException(
                String.format("Invalid state transition from %s to %s for task %s",
                    currentStatus, newStatus, task.getItemId()));
        }
    }

    private boolean isValidTransition(ScheduledTask.TaskStatus from, ScheduledTask.TaskStatus to) {
        switch (to) {
            case SCHEDULED:
                return from == ScheduledTask.TaskStatus.WAITING ||
                       from == ScheduledTask.TaskStatus.CRASHED ||
                       from == ScheduledTask.TaskStatus.FAILED;
            case RUNNING:
                return from == ScheduledTask.TaskStatus.SCHEDULED ||
                       from == ScheduledTask.TaskStatus.CRASHED ||
                       from == ScheduledTask.TaskStatus.WAITING;
            case COMPLETED:
            case FAILED:
            case CANCELLED:
                return from == ScheduledTask.TaskStatus.RUNNING;
            case CRASHED:
                return from == ScheduledTask.TaskStatus.RUNNING;
            case WAITING:
                return from == ScheduledTask.TaskStatus.SCHEDULED ||
                       from == ScheduledTask.TaskStatus.RUNNING;
            default:
                return false;
        }
    }

    /**
     * Validates task execution prerequisites
     */
    public void validateExecutionPrerequisites(ScheduledTask task, String nodeId) {
        if (task.getStatus() != ScheduledTask.TaskStatus.SCHEDULED &&
            task.getStatus() != ScheduledTask.TaskStatus.CRASHED) {
            throw new IllegalStateException(
                "Task must be in SCHEDULED or CRASHED state to execute, current state: " +
                task.getStatus());
        }

        if (!task.isEnabled()) {
            throw new IllegalStateException("Cannot execute disabled task: " + task.getItemId());
        }

        // Validate node-specific execution
        if (!task.isRunOnAllNodes() && task.getLockOwner() != null &&
            !task.getLockOwner().equals(nodeId)) {
            throw new IllegalStateException(
                String.format("Task %s can only be executed on its assigned node %s, current node: %s",
                    task.getItemId(), task.getLockOwner(), nodeId));
        }
    }
}
