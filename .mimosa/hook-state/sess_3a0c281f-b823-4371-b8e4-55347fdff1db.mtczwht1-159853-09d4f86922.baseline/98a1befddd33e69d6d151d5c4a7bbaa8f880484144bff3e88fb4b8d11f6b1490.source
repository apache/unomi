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

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Shared builders for scheduler manager unit tests.
 *
 * Callers (same package): TaskStateManagerTest, TaskValidationManagerTest,
 * TaskHistoryManagerTest, TaskLockManagerTest, TaskRecoveryManagerTest,
 * TaskExecutionManagerTest, PersistenceSchedulerProviderTest.
 *
 * No existing TaskTestFixtures (Glob confirmed 0 matches). No data-file I/O.
 *
 * User instruction: "Could you add unit tests for all the Task*Managers and for
 * the SchedulerProvider implementations ?"
 */
final class TaskTestFixtures {

    private TaskTestFixtures() {
    }

    static ScheduledTask baseTask(String type) {
        ScheduledTask task = new ScheduledTask();
        task.setItemId(UUID.randomUUID().toString());
        task.setTaskType(type);
        task.setStatus(ScheduledTask.TaskStatus.SCHEDULED);
        task.setEnabled(true);
        task.setPersistent(true);
        task.setOneShot(true);
        task.setAllowParallelExecution(false);
        task.setMaxRetries(3);
        task.setRetryDelay(500);
        task.setCreationDate(new Date());
        task.setTimeUnit(TimeUnit.MILLISECONDS);
        return task;
    }

    static ScheduledTask runningTask(String type, String lockOwner) {
        ScheduledTask task = baseTask(type);
        task.setStatus(ScheduledTask.TaskStatus.RUNNING);
        task.setLockOwner(lockOwner);
        task.setLockDate(new Date(System.currentTimeMillis() - 10_000));
        task.setExecutingNodeId(lockOwner);
        return task;
    }

    static ScheduledTask rawTask(String type) {
        ScheduledTask task = new ScheduledTask();
        task.setItemId(UUID.randomUUID().toString());
        task.setTaskType(type);
        task.setStatus(ScheduledTask.TaskStatus.SCHEDULED);
        task.setEnabled(true);
        task.setPersistent(true);
        task.setAllowParallelExecution(false);
        task.setMaxRetries(3);
        task.setRetryDelay(500);
        task.setCreationDate(new Date());
        task.setTimeUnit(TimeUnit.MILLISECONDS);
        task.setDependsOn(new java.util.HashSet<>());
        return task;
    }

    static ScheduledTask periodicTask(String type, long periodMs) {
        ScheduledTask task = baseTask(type);
        task.setOneShot(false);
        task.setPeriod(periodMs);
        task.setTimeUnit(TimeUnit.MILLISECONDS);
        task.setFixedRate(false);
        return task;
    }

    static void setPeriodField(ScheduledTask task, long period) {
        try {
            java.lang.reflect.Field field = ScheduledTask.class.getDeclaredField("period");
            field.setAccessible(true);
            field.setLong(task, period);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns an itemId whose affinity primary is not {@code nodeId} for the given active set.
     */
    static String itemIdNotPrimaryFor(String nodeId, java.util.List<String> activeNodes) {
        java.util.List<String> sorted = new java.util.ArrayList<>(activeNodes);
        java.util.Collections.sort(sorted);
        for (int i = 0; i < 50_000; i++) {
            String id = "affinity-" + i;
            int primaryIndex = Math.abs(id.hashCode() % sorted.size());
            if (!sorted.get(primaryIndex).equals(nodeId)) {
                return id;
            }
        }
        throw new IllegalStateException("Could not find non-primary itemId for " + nodeId);
    }

    /**
     * Backup order of {@code nodeId} relative to the affinity primary of {@code itemId}
     * (1 = first backup after primary).
     */
    static int backupOrderFor(String itemId, String nodeId, java.util.List<String> activeNodes) {
        java.util.List<String> sorted = new java.util.ArrayList<>(activeNodes);
        java.util.Collections.sort(sorted);
        int primaryIndex = Math.abs(itemId.hashCode() % sorted.size());
        int ourIndex = sorted.indexOf(nodeId);
        if (ourIndex < 0) {
            throw new IllegalArgumentException(nodeId + " not in active nodes");
        }
        return (ourIndex - primaryIndex + sorted.size()) % sorted.size();
    }
}
