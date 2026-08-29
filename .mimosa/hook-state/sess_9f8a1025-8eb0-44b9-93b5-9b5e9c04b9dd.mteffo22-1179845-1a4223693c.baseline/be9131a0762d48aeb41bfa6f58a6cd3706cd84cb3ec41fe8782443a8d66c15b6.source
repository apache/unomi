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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TaskValidationManager}.
 * Invoked by Surefire: {@code -Dtest=TaskValidationManagerTest}. No data-file I/O.
 * Glob: no prior TaskValidationManagerTest. User asked for Task*Managers unit tests.
 */
public class TaskValidationManagerTest {

    private TaskValidationManager validationManager;

    @BeforeEach
    public void setUp() {
        validationManager = new TaskValidationManager();
    }

    @Test
    public void testValidateTaskAcceptsValidOneShot() {
        ScheduledTask task = TaskTestFixtures.baseTask("valid");
        assertDoesNotThrow(() -> validationManager.validateTask(task, new HashMap<>()));
    }

    @Test
    public void testRejectsNullOrEmptyTypeAndId() {
        ScheduledTask task = TaskTestFixtures.baseTask("t");
        task.setTaskType(" ");
        assertThrows(IllegalArgumentException.class,
            () -> validationManager.validateTask(task, new HashMap<>()));

        ScheduledTask task2 = TaskTestFixtures.baseTask("t");
        task2.setItemId("");
        assertThrows(IllegalArgumentException.class,
            () -> validationManager.validateTask(task2, new HashMap<>()));
    }

    @Test
    public void testRejectsNegativePeriodAndMissingTimeUnit() {
        ScheduledTask task = TaskTestFixtures.rawTask("p");
        task.setOneShot(false);
        setPeriodField(task, -1L);
        assertThrows(IllegalArgumentException.class,
            () -> validationManager.validateTask(task, new HashMap<>()));

        ScheduledTask delayed = TaskTestFixtures.rawTask("d");
        delayed.setOneShot(true);
        delayed.setInitialDelay(5);
        delayed.setTimeUnit(null);
        assertThrows(IllegalArgumentException.class,
            () -> validationManager.validateTask(delayed, new HashMap<>()));
    }

    @Test
    public void testRejectsOneShotWithPeriod() {
        ScheduledTask task = TaskTestFixtures.rawTask("oneshot");
        task.setOneShot(true);
        setPeriodField(task, 10L);
        task.setTimeUnit(TimeUnit.SECONDS);
        assertThrows(IllegalArgumentException.class,
            () -> validationManager.validateTask(task, new HashMap<>()));
    }

    private static void setPeriodField(ScheduledTask task, long period) {
        TaskTestFixtures.setPeriodField(task, period);
    }

    @Test
    public void testRejectsMissingDependency() {
        ScheduledTask task = TaskTestFixtures.baseTask("dep");
        task.setDependsOn(new HashSet<>(Collections.singleton("missing-id")));
        assertThrows(IllegalArgumentException.class,
            () -> validationManager.validateTask(task, new HashMap<>()));
    }

    @Test
    public void testRejectsCircularDependency() {
        ScheduledTask a = TaskTestFixtures.baseTask("a");
        ScheduledTask b = TaskTestFixtures.baseTask("b");
        a.setDependsOn(new HashSet<>(Collections.singleton(b.getItemId())));
        b.setDependsOn(new HashSet<>(Collections.singleton(a.getItemId())));
        Map<String, ScheduledTask> existing = new HashMap<>();
        existing.put(a.getItemId(), a);
        existing.put(b.getItemId(), b);
        assertThrows(IllegalArgumentException.class,
            () -> validationManager.validateTask(a, existing));
    }

    @Test
    public void testRejectsRunOnAllNodesWithDisallowParallel() {
        ScheduledTask task = TaskTestFixtures.periodicTask("all", 1000);
        task.setRunOnAllNodes(true);
        task.setAllowParallelExecution(false);
        assertThrows(IllegalArgumentException.class,
            () -> validationManager.validateTask(task, new HashMap<>()));
    }

    @Test
    public void testRejectsOneShotRunOnAllNodes() {
        ScheduledTask task = TaskTestFixtures.baseTask("all");
        task.setRunOnAllNodes(true);
        task.setAllowParallelExecution(true);
        assertThrows(IllegalArgumentException.class,
            () -> validationManager.validateTask(task, new HashMap<>()));
    }

    @Test
    public void testValidateStateTransitionStricterThanStateManager() {
        ScheduledTask task = TaskTestFixtures.baseTask("v");
        task.setStatus(ScheduledTask.TaskStatus.SCHEDULED);
        assertThrows(IllegalStateException.class,
            () -> validationManager.validateStateTransition(task, ScheduledTask.TaskStatus.CANCELLED));
        assertDoesNotThrow(
            () -> validationManager.validateStateTransition(task, ScheduledTask.TaskStatus.RUNNING));
    }

    @Test
    public void testValidateExecutionPrerequisites() {
        ScheduledTask task = TaskTestFixtures.baseTask("exec");
        assertDoesNotThrow(() -> validationManager.validateExecutionPrerequisites(task, "node-1"));

        task.setEnabled(false);
        assertThrows(IllegalStateException.class,
            () -> validationManager.validateExecutionPrerequisites(task, "node-1"));

        task.setEnabled(true);
        task.setStatus(ScheduledTask.TaskStatus.COMPLETED);
        assertThrows(IllegalStateException.class,
            () -> validationManager.validateExecutionPrerequisites(task, "node-1"));
    }

    @Test
    public void testValidateExecutionPrerequisitesRejectsWrongLockOwner() {
        ScheduledTask task = TaskTestFixtures.baseTask("exec");
        task.setLockOwner("other-node");
        assertThrows(IllegalStateException.class,
            () -> validationManager.validateExecutionPrerequisites(task, "node-1"));
    }

    @Test
    public void testValidateRetryConfigurationRejectsNegatives() {
        ScheduledTask task = TaskTestFixtures.baseTask("retry");
        task.setMaxRetries(-1);
        assertThrows(IllegalArgumentException.class,
            () -> validationManager.validateRetryConfiguration(task));
        task.setMaxRetries(1);
        task.setRetryDelay(-5);
        assertThrows(IllegalArgumentException.class,
            () -> validationManager.validateRetryConfiguration(task));
    }

    @Test
    public void testRejectsSelfAndTransitiveDependencyCycles() {
        ScheduledTask self = TaskTestFixtures.baseTask("self");
        self.setDependsOn(new HashSet<>(Collections.singleton(self.getItemId())));
        Map<String, ScheduledTask> existing = new HashMap<>();
        existing.put(self.getItemId(), self);
        assertThrows(IllegalArgumentException.class,
            () -> validationManager.validateTask(self, existing));

        ScheduledTask a = TaskTestFixtures.baseTask("a");
        ScheduledTask b = TaskTestFixtures.baseTask("b");
        ScheduledTask c = TaskTestFixtures.baseTask("c");
        a.setDependsOn(new HashSet<>(Collections.singleton(b.getItemId())));
        b.setDependsOn(new HashSet<>(Collections.singleton(c.getItemId())));
        c.setDependsOn(new HashSet<>(Collections.singleton(a.getItemId())));
        Map<String, ScheduledTask> cycle = new HashMap<>();
        cycle.put(a.getItemId(), a);
        cycle.put(b.getItemId(), b);
        cycle.put(c.getItemId(), c);
        assertThrows(IllegalArgumentException.class,
            () -> validationManager.validateTask(a, cycle));
    }

    @Test
    public void testExecutionPrerequisitesAllowsWrongOwnerWhenRunOnAllNodes() {
        ScheduledTask task = TaskTestFixtures.periodicTask("all", 1000);
        task.setRunOnAllNodes(true);
        task.setAllowParallelExecution(true);
        task.setLockOwner("other-node");
        task.setStatus(ScheduledTask.TaskStatus.WAITING);
        assertDoesNotThrow(() -> validationManager.validateExecutionPrerequisites(task, "node-1"));

        task.setStatus(ScheduledTask.TaskStatus.CRASHED);
        assertDoesNotThrow(() -> validationManager.validateExecutionPrerequisites(task, "node-1"));
    }

    @Test
    public void testRejectsNegativeInitialDelay() {
        ScheduledTask task = TaskTestFixtures.baseTask("delay");
        task.setInitialDelay(-1);
        assertThrows(IllegalArgumentException.class,
            () -> validationManager.validateTask(task, new HashMap<>()));
    }

    @Test
    public void testRejectsBlankDependencyId() {
        ScheduledTask task = TaskTestFixtures.baseTask("dep");
        task.setDependsOn(new HashSet<>(Collections.singleton("")));
        assertThrows(IllegalArgumentException.class,
            () -> validationManager.validateTask(task, new HashMap<>()));
    }
}
