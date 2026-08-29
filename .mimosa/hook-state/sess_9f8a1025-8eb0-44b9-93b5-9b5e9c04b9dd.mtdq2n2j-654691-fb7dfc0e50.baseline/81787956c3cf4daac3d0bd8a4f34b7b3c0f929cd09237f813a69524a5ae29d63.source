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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TaskStateManager}.
 * Invoked by Surefire: {@code -Dtest=TaskStateManagerTest}. No data-file I/O.
 * Glob: no prior TaskStateManagerTest. User asked for Task*Managers unit tests.
 */
public class TaskStateManagerTest {

    private TaskStateManager stateManager;

    @BeforeEach
    public void setUp() {
        stateManager = new TaskStateManager();
    }

    @Test
    public void testValidTransitionsIncludeRunningToRunning() {
        assertTrue(TaskStateManager.TaskTransition.isValidTransition(TaskStatus.RUNNING, TaskStatus.RUNNING));
        assertTrue(TaskStateManager.TaskTransition.isValidTransition(TaskStatus.SCHEDULED, TaskStatus.RUNNING));
        assertTrue(TaskStateManager.TaskTransition.isValidTransition(TaskStatus.RUNNING, TaskStatus.CRASHED));
        assertTrue(TaskStateManager.TaskTransition.isValidTransition(TaskStatus.COMPLETED, TaskStatus.SCHEDULED));
    }

    @Test
    public void testCancelledToCrashedIsRejected() {
        ScheduledTask task = TaskTestFixtures.baseTask("state");
        task.setStatus(TaskStatus.CANCELLED);
        assertThrows(IllegalStateException.class,
            () -> stateManager.updateTaskState(task, TaskStatus.CRASHED, "x", "node-1"));
    }

    @Test
    public void testUpdateToCompletedClearsLockAndSetsLastExecution() {
        ScheduledTask task = TaskTestFixtures.runningTask("state", "node-1");
        task.setCurrentStep("step");
        stateManager.updateTaskState(task, TaskStatus.COMPLETED, null, "node-1");
        assertEquals(TaskStatus.COMPLETED, task.getStatus());
        assertNull(task.getLockOwner());
        assertNull(task.getLockDate());
        assertNull(task.getCurrentStep());
        assertNotNull(task.getLastExecutionDate());
    }

    @Test
    public void testUpdateToCrashedPreservesCrashDetails() {
        ScheduledTask task = TaskTestFixtures.runningTask("state", "dead-node");
        stateManager.updateTaskState(task, TaskStatus.CRASHED, "node died", "survivor");
        assertEquals(TaskStatus.CRASHED, task.getStatus());
        assertEquals("CRASHED", task.getCurrentStep());
        assertEquals("node died", task.getLastError());
        assertNotNull(task.getStatusDetails().get("crashTime"));
        assertEquals("dead-node", task.getStatusDetails().get("crashedNode"));
    }

    @Test
    public void testUpdateToRunningSetsStatusDetails() {
        ScheduledTask task = TaskTestFixtures.baseTask("state");
        stateManager.updateTaskState(task, TaskStatus.RUNNING, null, "node-1");
        assertEquals(TaskStatus.RUNNING, task.getStatus());
        assertEquals("node-1", task.getStatusDetails().get("executingNode"));
        assertNotNull(task.getStatusDetails().get("startTime"));
    }

    @Test
    public void testCanRescheduleWithCompletedDependencies() {
        ScheduledTask dep = TaskTestFixtures.baseTask("dep");
        dep.setStatus(TaskStatus.COMPLETED);
        ScheduledTask task = TaskTestFixtures.baseTask("dependent");
        task.setDependsOn(new HashSet<>(Collections.singleton(dep.getItemId())));
        Map<String, ScheduledTask> deps = new HashMap<>();
        deps.put(dep.getItemId(), dep);
        assertTrue(stateManager.canRescheduleTask(task, deps));
    }

    @Test
    public void testCanRescheduleFalseWhenDependencyMissingOrIncomplete() {
        ScheduledTask task = TaskTestFixtures.baseTask("dependent");
        task.setDependsOn(new HashSet<>(Collections.singleton("missing")));
        assertFalse(stateManager.canRescheduleTask(task, new HashMap<>()));

        ScheduledTask dep = TaskTestFixtures.baseTask("dep");
        dep.setStatus(TaskStatus.RUNNING);
        task.setDependsOn(new HashSet<>(Collections.singleton(dep.getItemId())));
        Map<String, ScheduledTask> deps = Collections.singletonMap(dep.getItemId(), dep);
        assertFalse(stateManager.canRescheduleTask(task, deps));
    }

    @Test
    public void testCanReschedulePrefersWaitingOnTasks() {
        ScheduledTask done = TaskTestFixtures.baseTask("done");
        done.setStatus(TaskStatus.COMPLETED);
        ScheduledTask pending = TaskTestFixtures.baseTask("pending");
        pending.setStatus(TaskStatus.RUNNING);

        ScheduledTask task = TaskTestFixtures.baseTask("dependent");
        task.setDependsOn(new HashSet<>(Collections.singleton(done.getItemId())));
        task.setWaitingOnTasks(new HashSet<>(Collections.singleton(pending.getItemId())));

        Map<String, ScheduledTask> deps = new HashMap<>();
        deps.put(done.getItemId(), done);
        deps.put(pending.getItemId(), pending);
        assertFalse(stateManager.canRescheduleTask(task, deps));
    }

    @Test
    public void testResetTaskToScheduledClearsWaiting() {
        ScheduledTask task = TaskTestFixtures.baseTask("wait");
        task.setStatus(TaskStatus.WAITING);
        task.setWaitingOnTasks(new HashSet<>(Collections.singleton("x")));
        task.setWaitingForTaskType("t");
        stateManager.resetTaskToScheduled(task);
        assertEquals(TaskStatus.SCHEDULED, task.getStatus());
        assertNull(task.getWaitingOnTasks());
        assertNull(task.getWaitingForTaskType());
    }

    @Test
    public void testCalculateNextExecutionRetryUsesMillisecondRetryDelay() {
        ScheduledTask task = TaskTestFixtures.baseTask("retry");
        task.setRetryDelay(250);
        long before = System.currentTimeMillis();
        stateManager.calculateNextExecutionTime(task, true);
        long after = System.currentTimeMillis();
        long next = task.getNextScheduledExecution().getTime();
        assertTrue(next >= before + 250);
        assertTrue(next <= after + 250);
    }

    @Test
    public void testCalculateNextExecutionOneShotSecondRunDisables() {
        ScheduledTask task = TaskTestFixtures.baseTask("oneshot");
        task.setLastExecutionDate(new Date());
        stateManager.calculateNextExecutionTime(task, false);
        assertNull(task.getNextScheduledExecution());
        assertFalse(task.isEnabled());
    }

    @Test
    public void testCalculateNextExecutionFixedDelayFromNow() {
        ScheduledTask task = TaskTestFixtures.periodicTask("periodic", 1000);
        task.setLastExecutionDate(new Date());
        long before = System.currentTimeMillis();
        stateManager.calculateNextExecutionTime(task, false);
        assertTrue(task.getNextScheduledExecution().getTime() >= before + 1000);
    }

    @Test
    public void testValidateTaskRejectsOneShotWithPeriod() {
        ScheduledTask task = TaskTestFixtures.rawTask("bad");
        task.setOneShot(true);
        TaskTestFixtures.setPeriodField(task, 5L);
        task.setTimeUnit(TimeUnit.SECONDS);
        assertThrows(IllegalArgumentException.class,
            () -> stateManager.validateTask(task, new HashMap<>()));
    }

    @Test
    public void testCancelFromCompletedIsAllowed() {
        ScheduledTask task = TaskTestFixtures.baseTask("cancel");
        task.setStatus(TaskStatus.COMPLETED);
        assertDoesNotThrow(() -> stateManager.updateTaskState(task, TaskStatus.CANCELLED, null, "node-1"));
        assertEquals(TaskStatus.CANCELLED, task.getStatus());
    }

    @Test
    public void testUpdateToWaitingClearsLockInfo() {
        ScheduledTask task = TaskTestFixtures.runningTask("wait", "node-1");
        stateManager.updateTaskState(task, TaskStatus.WAITING, null, "node-1");
        assertEquals(TaskStatus.WAITING, task.getStatus());
        assertNull(task.getLockOwner());
        assertNull(task.getLockDate());
    }

    @Test
    public void testFixedRateCatchesUpPastIntervals() {
        ScheduledTask task = TaskTestFixtures.periodicTask("fixed", 1000);
        task.setFixedRate(true);
        long now = System.currentTimeMillis();
        task.setLastExecutionDate(new Date(now - 3500));
        task.setNextScheduledExecution(new Date(now - 3500));
        stateManager.calculateNextExecutionTime(task, false);
        assertTrue(task.getNextScheduledExecution().getTime() > now);
        assertTrue(task.getNextScheduledExecution().getTime() <= now + 1000);
    }

    @Test
    public void testInitialDelayCreatesCreationDateWhenMissing() {
        ScheduledTask task = TaskTestFixtures.baseTask("delay");
        task.setCreationDate(null);
        task.setInitialDelay(200);
        task.setTimeUnit(TimeUnit.MILLISECONDS);
        long before = System.currentTimeMillis();
        stateManager.calculateNextExecutionTime(task, false);
        assertNotNull(task.getCreationDate());
        assertTrue(task.getNextScheduledExecution().getTime() >= before + 200);
    }

    @Test
    public void testPeriodZeroDoesNotSetNextExecution() {
        ScheduledTask task = TaskTestFixtures.rawTask("zero");
        task.setOneShot(false);
        TaskTestFixtures.setPeriodField(task, 0L);
        task.setLastExecutionDate(new Date());
        task.setNextScheduledExecution(null);
        stateManager.calculateNextExecutionTime(task, false);
        assertNull(task.getNextScheduledExecution());
    }

    @Test
    public void testCanRescheduleFallsBackWhenWaitingOnEmpty() {
        ScheduledTask dep = TaskTestFixtures.baseTask("dep");
        dep.setStatus(TaskStatus.COMPLETED);
        ScheduledTask task = TaskTestFixtures.baseTask("dependent");
        task.setDependsOn(new HashSet<>(Collections.singleton(dep.getItemId())));
        task.setWaitingOnTasks(new HashSet<>());
        Map<String, ScheduledTask> deps = new HashMap<>();
        deps.put(dep.getItemId(), dep);
        assertTrue(stateManager.canRescheduleTask(task, deps));
    }

    @Test
    public void testValidateTaskRejectsBlankDependencyAndNullTimeUnit() {
        ScheduledTask blankDep = TaskTestFixtures.baseTask("blank");
        blankDep.setDependsOn(new HashSet<>(Collections.singleton("  ")));
        assertThrows(IllegalArgumentException.class,
            () -> stateManager.validateTask(blankDep, new HashMap<>()));

        ScheduledTask noUnit = TaskTestFixtures.rawTask("nounit");
        noUnit.setOneShot(false);
        TaskTestFixtures.setPeriodField(noUnit, 5L);
        noUnit.setTimeUnit(null);
        assertThrows(IllegalArgumentException.class,
            () -> stateManager.validateTask(noUnit, new HashMap<>()));

        ScheduledTask negRetries = TaskTestFixtures.baseTask("retries");
        negRetries.setMaxRetries(-1);
        assertThrows(IllegalArgumentException.class,
            () -> stateManager.validateTask(negRetries, new HashMap<>()));
    }
}

