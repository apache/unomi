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
import org.apache.unomi.api.tasks.TaskExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TaskRecoveryManager}.
 * Invoked by Surefire: {@code -Dtest=TaskRecoveryManagerTest}. No data-file I/O.
 * Glob: no prior TaskRecoveryManagerTest. User asked for Task*Managers unit tests.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TaskRecoveryManagerTest {

    private static final String NODE = "recovery-node";

    @Mock private SchedulerServiceImpl schedulerService;
    @Mock private TaskLockManager lockManager;
    @Mock private TaskExecutionManager executionManager;
    @Mock private TaskExecutorRegistry executorRegistry;
    @Mock private TaskExecutor resumeExecutor;
    @Mock private TaskExecutor plainExecutor;

    private TaskStateManager stateManager;
    private TaskMetricsManager metricsManager;
    private TaskRecoveryManager recoveryManager;

    @BeforeEach
    public void setUp() {
        stateManager = new TaskStateManager();
        metricsManager = new TaskMetricsManager();
        recoveryManager = new TaskRecoveryManager();
        recoveryManager.setNodeId(NODE);
        recoveryManager.setStateManager(stateManager);
        recoveryManager.setLockManager(lockManager);
        recoveryManager.setMetricsManager(metricsManager);
        recoveryManager.setExecutionManager(executionManager);
        recoveryManager.setExecutorRegistry(executorRegistry);
        recoveryManager.setSchedulerService(schedulerService);

        when(schedulerService.saveTask(any(ScheduledTask.class))).thenReturn(true);
        when(schedulerService.saveTaskWithRefresh(any(ScheduledTask.class))).thenReturn(true);
        when(schedulerService.getTask(anyString(), anyBoolean())).thenAnswer(inv -> {
            // Default: no separate store copy — recover against the in-memory instance.
            return null;
        });
        when(lockManager.isLockExpired(any(ScheduledTask.class))).thenReturn(true);
        recoveryManager.setExecutorNode(true);
        when(schedulerService.findLockedTasks()).thenReturn(Collections.emptyList());
        when(resumeExecutor.canResume(any())).thenReturn(true);
        when(plainExecutor.canResume(any())).thenReturn(false);
    }

    @Test
    public void testPrepareForShutdownSkipsRecovery() {
        recoveryManager.prepareForShutdown();
        when(schedulerService.findTasksByStatus(ScheduledTask.TaskStatus.RUNNING))
            .thenReturn(Collections.singletonList(TaskTestFixtures.runningTask("t", "dead")));
        recoveryManager.recoverCrashedTasks();
        verify(executionManager, never()).executeTask(any(), any());
    }

    @Test
    public void testRecoverRunningExpiredResumesWhenCanResume() {
        ScheduledTask task = TaskTestFixtures.runningTask("resume-type", "dead");
        task.setCheckpointData(Collections.singletonMap("step", 1));
        when(schedulerService.findTasksByStatus(ScheduledTask.TaskStatus.RUNNING))
            .thenReturn(Collections.singletonList(task));
        when(executorRegistry.getExecutor("resume-type")).thenReturn(resumeExecutor);

        recoveryManager.recoverCrashedTasks();

        assertEquals(ScheduledTask.TaskStatus.CRASHED, task.getStatus());
        assertNull(task.getLockOwner());
        assertNotNull(task.getStatusDetails().get("crashTime"));
        verify(executionManager).executeTask(same(task), same(resumeExecutor));
        assertEquals(1, metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_CRASHED));
        assertEquals(1, metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_RESUMED));
    }

    @Test
    public void testRecoverRunningExpiredRestartsWhenCannotResume() {
        ScheduledTask task = TaskTestFixtures.runningTask("plain-type", "dead");
        task.setFailureCount(0);
        task.setMaxRetries(3);
        task.setLastExecutionDate(new Date());
        when(schedulerService.findTasksByStatus(ScheduledTask.TaskStatus.RUNNING))
            .thenReturn(Collections.singletonList(task));
        when(executorRegistry.getExecutor("plain-type")).thenReturn(plainExecutor);

        recoveryManager.recoverCrashedTasks();

        assertEquals(ScheduledTask.TaskStatus.SCHEDULED, task.getStatus());
        assertNotNull(task.getNextScheduledExecution());
        verify(executionManager).executeTask(same(task), same(plainExecutor));
    }

    @Test
    public void testRecoverSkipsRestartWhenRetryBudgetExhausted() {
        ScheduledTask task = TaskTestFixtures.runningTask("plain-type", "dead");
        task.setFailureCount(4);
        task.setMaxRetries(3);
        when(schedulerService.findTasksByStatus(ScheduledTask.TaskStatus.RUNNING))
            .thenReturn(Collections.singletonList(task));
        when(executorRegistry.getExecutor("plain-type")).thenReturn(plainExecutor);

        recoveryManager.recoverCrashedTasks();

        assertEquals(ScheduledTask.TaskStatus.CRASHED, task.getStatus());
        verify(executionManager, never()).executeTask(any(), any());
    }

    @Test
    public void testRecoverLockedWaitingTaskReschedules() {
        ScheduledTask waiting = TaskTestFixtures.baseTask("wait");
        waiting.setStatus(ScheduledTask.TaskStatus.WAITING);
        waiting.setLockOwner("dead");
        waiting.setLockDate(new Date(System.currentTimeMillis() - 10_000));
        when(schedulerService.findTasksByStatus(ScheduledTask.TaskStatus.RUNNING))
            .thenReturn(Collections.emptyList());
        when(schedulerService.findLockedTasks()).thenReturn(Collections.singletonList(waiting));
        when(executorRegistry.getExecutor("wait")).thenReturn(plainExecutor);
        when(lockManager.releaseLock(waiting)).thenAnswer(inv -> {
            waiting.setLockOwner(null);
            waiting.setLockDate(null);
            return true;
        });

        recoveryManager.recoverCrashedTasks();

        assertEquals(ScheduledTask.TaskStatus.SCHEDULED, waiting.getStatus());
        verify(executionManager).executeTask(same(waiting), same(plainExecutor));
    }

    @Test
    public void testRecoverLockedSkipsRunningAndCrashed() {
        ScheduledTask running = TaskTestFixtures.runningTask("r", "dead");
        ScheduledTask crashed = TaskTestFixtures.baseTask("c");
        crashed.setStatus(ScheduledTask.TaskStatus.CRASHED);
        crashed.setLockOwner("dead");
        crashed.setLockDate(new Date(0));
        when(schedulerService.findTasksByStatus(ScheduledTask.TaskStatus.RUNNING))
            .thenReturn(Collections.emptyList());
        when(schedulerService.findLockedTasks()).thenReturn(Arrays.asList(running, crashed));

        recoveryManager.recoverCrashedTasks();

        verify(lockManager, never()).releaseLock(running);
        verify(lockManager, never()).releaseLock(crashed);
    }

    @Test
    public void testRecoverDoesNotDispatchWhenSaveFails() {
        ScheduledTask task = TaskTestFixtures.runningTask("plain-type", "dead");
        when(schedulerService.findTasksByStatus(ScheduledTask.TaskStatus.RUNNING))
            .thenReturn(Collections.singletonList(task));
        when(schedulerService.saveTask(any(ScheduledTask.class))).thenReturn(false);
        when(schedulerService.saveTaskWithRefresh(any(ScheduledTask.class))).thenReturn(false);
        when(executorRegistry.getExecutor("plain-type")).thenReturn(plainExecutor);

        recoveryManager.recoverCrashedTasks();

        assertEquals(ScheduledTask.TaskStatus.CRASHED, task.getStatus());
        verify(executionManager, never()).executeTask(any(), any());
    }

    @Test
    public void testRecoverDoesNotRestartDisabledTask() {
        ScheduledTask task = TaskTestFixtures.runningTask("plain-type", "dead");
        task.setEnabled(false);
        when(schedulerService.findTasksByStatus(ScheduledTask.TaskStatus.RUNNING))
            .thenReturn(Collections.singletonList(task));
        when(executorRegistry.getExecutor("plain-type")).thenReturn(plainExecutor);

        recoveryManager.recoverCrashedTasks();

        assertEquals(ScheduledTask.TaskStatus.CRASHED, task.getStatus());
        verify(executionManager, never()).executeTask(any(), any());
    }

    @Test
    public void testRecoverSkipsWhenNoExecutorRegistered() {
        ScheduledTask task = TaskTestFixtures.runningTask("missing-type", "dead");
        when(schedulerService.findTasksByStatus(ScheduledTask.TaskStatus.RUNNING))
            .thenReturn(Collections.singletonList(task));
        when(executorRegistry.getExecutor("missing-type")).thenReturn(null);

        recoveryManager.recoverCrashedTasks();

        assertEquals(ScheduledTask.TaskStatus.CRASHED, task.getStatus());
        verify(executionManager, never()).executeTask(any(), any());
    }

    @Test
    public void testRecoverRestartsWhenFailureCountEqualsMaxRetries() {
        ScheduledTask task = TaskTestFixtures.runningTask("plain-type", "dead");
        task.setFailureCount(3);
        task.setMaxRetries(3);
        when(schedulerService.findTasksByStatus(ScheduledTask.TaskStatus.RUNNING))
            .thenReturn(Collections.singletonList(task));
        when(executorRegistry.getExecutor("plain-type")).thenReturn(plainExecutor);

        recoveryManager.recoverCrashedTasks();

        assertEquals(ScheduledTask.TaskStatus.SCHEDULED, task.getStatus());
        verify(executionManager).executeTask(same(task), same(plainExecutor));
    }

    @Test
    public void testRecoverLockedWaitingKeepsWaitingWhenDepsUnmet() {
        ScheduledTask waiting = TaskTestFixtures.baseTask("wait");
        waiting.setStatus(ScheduledTask.TaskStatus.WAITING);
        waiting.setLockOwner("dead");
        waiting.setLockDate(new Date(System.currentTimeMillis() - 10_000));
        waiting.setDependsOn(new java.util.HashSet<>(Collections.singleton("dep-1")));
        ScheduledTask dep = TaskTestFixtures.baseTask("dep");
        dep.setItemId("dep-1");
        dep.setStatus(ScheduledTask.TaskStatus.RUNNING);
        when(schedulerService.findTasksByStatus(ScheduledTask.TaskStatus.RUNNING))
            .thenReturn(Collections.emptyList());
        when(schedulerService.findLockedTasks()).thenReturn(Collections.singletonList(waiting));
        when(schedulerService.getTask("dep-1")).thenReturn(dep);
        when(lockManager.releaseLock(waiting)).thenReturn(true);

        recoveryManager.recoverCrashedTasks();

        assertEquals(ScheduledTask.TaskStatus.WAITING, waiting.getStatus());
        verify(executionManager, never()).executeTask(any(), any());
    }

    @Test
    public void testRecoverAbortsIndividualTaskWhenShutdownFlips() {
        ScheduledTask task = TaskTestFixtures.runningTask("plain-type", "dead");
        when(schedulerService.findTasksByStatus(ScheduledTask.TaskStatus.RUNNING))
            .thenAnswer(inv -> {
                recoveryManager.prepareForShutdown();
                return Collections.singletonList(task);
            });

        recoveryManager.recoverCrashedTasks();

        assertEquals(ScheduledTask.TaskStatus.RUNNING, task.getStatus());
        verify(executionManager, never()).executeTask(any(), any());
        verify(schedulerService, never()).saveTask(task);
    }

    @Test
    public void testRecoverLockedScheduledDispatchesExecute() {
        ScheduledTask scheduled = TaskTestFixtures.baseTask("sched");
        scheduled.setStatus(ScheduledTask.TaskStatus.SCHEDULED);
        scheduled.setLockOwner("dead");
        scheduled.setLockDate(new Date(System.currentTimeMillis() - 10_000));
        when(schedulerService.findTasksByStatus(ScheduledTask.TaskStatus.RUNNING))
            .thenReturn(Collections.emptyList());
        when(schedulerService.findLockedTasks()).thenReturn(Collections.singletonList(scheduled));
        when(executorRegistry.getExecutor("sched")).thenReturn(plainExecutor);
        when(lockManager.releaseLock(scheduled)).thenReturn(true);

        recoveryManager.recoverCrashedTasks();

        verify(lockManager).releaseLock(scheduled);
        verify(executionManager).executeTask(same(scheduled), same(plainExecutor));
    }

    @Test
    public void testNonExecutorMarksCrashedButDoesNotDispatch() {
        recoveryManager.setExecutorNode(false);
        ScheduledTask task = TaskTestFixtures.runningTask("plain-type", "dead");
        when(schedulerService.findTasksByStatus(ScheduledTask.TaskStatus.RUNNING))
            .thenReturn(Collections.singletonList(task));
        when(executorRegistry.getExecutor("plain-type")).thenReturn(plainExecutor);

        recoveryManager.recoverCrashedTasks();

        assertEquals(ScheduledTask.TaskStatus.CRASHED, task.getStatus());
        verify(executionManager, never()).executeTask(any(), any());
    }

    @Test
    public void testNonExecutorDispatchesRunOnAllNodesRecovery() {
        recoveryManager.setExecutorNode(false);
        ScheduledTask task = TaskTestFixtures.runningTask("plain-type", "dead");
        task.setRunOnAllNodes(true);
        task.setAllowParallelExecution(true);
        when(schedulerService.findTasksByStatus(ScheduledTask.TaskStatus.RUNNING))
            .thenReturn(Collections.singletonList(task));
        when(executorRegistry.getExecutor("plain-type")).thenReturn(plainExecutor);

        recoveryManager.recoverCrashedTasks();

        verify(executionManager).executeTask(any(ScheduledTask.class), same(plainExecutor));
    }

    @Test
    public void testRecoverCrashedCasConflictSkipsDispatch() {
        ScheduledTask task = TaskTestFixtures.runningTask("plain-type", "dead");
        task.setSystemMetadata("seq_no", 1L);
        task.setSystemMetadata("primary_term", 1L);
        ScheduledTask storeView = TaskTestFixtures.runningTask("plain-type", "dead");
        storeView.setItemId(task.getItemId());
        storeView.setSystemMetadata("seq_no", 1L);
        storeView.setSystemMetadata("primary_term", 1L);
        when(schedulerService.findTasksByStatus(ScheduledTask.TaskStatus.RUNNING))
            .thenReturn(Collections.singletonList(task));
        when(schedulerService.getTask(eq(task.getItemId()), eq(true))).thenReturn(storeView);
        when(schedulerService.saveTaskWithRefresh(any(ScheduledTask.class))).thenReturn(false);
        when(executorRegistry.getExecutor("plain-type")).thenReturn(plainExecutor);

        recoveryManager.recoverCrashedTasks();

        verify(executionManager, never()).executeTask(any(), any());
    }

    @Test
    public void testRecoverSkipsWhenStoreNoLongerRunning() {
        ScheduledTask task = TaskTestFixtures.runningTask("plain-type", "dead");
        ScheduledTask completed = TaskTestFixtures.baseTask("plain-type");
        completed.setItemId(task.getItemId());
        completed.setStatus(ScheduledTask.TaskStatus.COMPLETED);
        when(schedulerService.findTasksByStatus(ScheduledTask.TaskStatus.RUNNING))
            .thenReturn(Collections.singletonList(task));
        when(schedulerService.getTask(eq(task.getItemId()), eq(true))).thenReturn(completed);

        recoveryManager.recoverCrashedTasks();

        assertEquals(ScheduledTask.TaskStatus.RUNNING, task.getStatus());
        verify(executionManager, never()).executeTask(any(), any());
    }
}
