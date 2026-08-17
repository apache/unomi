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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TaskExecutionManager}.
 * Invoked by Surefire: {@code -Dtest=TaskExecutionManagerTest}. No data-file I/O.
 * Glob: no prior TaskExecutionManagerTest. User asked for Task*Managers unit tests.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TaskExecutionManagerTest {

    private static final String NODE = "exec-node";

    @Mock private SchedulerServiceImpl schedulerService;
    @Mock private TaskLockManager lockManager;
    @Mock private TaskExecutorRegistry executorRegistry;

    private TaskStateManager stateManager;
    private TaskMetricsManager metricsManager;
    private TaskHistoryManager historyManager;
    private TaskExecutionManager executionManager;

    @BeforeEach
    public void setUp() {
        stateManager = new TaskStateManager();
        metricsManager = new TaskMetricsManager();
        historyManager = new TaskHistoryManager();
        historyManager.setNodeId(NODE);
        historyManager.setMetricsManager(metricsManager);

        executionManager = new TaskExecutionManager();
        executionManager.setNodeId(NODE);
        executionManager.setThreadPoolSize(4);
        executionManager.setStateManager(stateManager);
        executionManager.setLockManager(lockManager);
        executionManager.setMetricsManager(metricsManager);
        executionManager.setHistoryManager(historyManager);
        executionManager.setExecutorRegistry(executorRegistry);
        executionManager.setSchedulerService(schedulerService);
        executionManager.initialize();

        when(schedulerService.isShutdownNow()).thenReturn(false);
        when(schedulerService.saveTask(any(ScheduledTask.class))).thenReturn(true);
        when(schedulerService.saveTask(any(ScheduledTask.class), anyBoolean())).thenReturn(true);
        when(schedulerService.saveTaskWithRefresh(any(ScheduledTask.class))).thenReturn(true);
        when(lockManager.acquireLock(any(ScheduledTask.class))).thenReturn(true);
        when(lockManager.releaseLock(any(ScheduledTask.class))).thenReturn(true);
        when(lockManager.isLockExpired(any(ScheduledTask.class))).thenReturn(false);
    }

    @AfterEach
    public void tearDown() {
        executionManager.shutdown();
    }

    /**
     * A terminal handler must increment counters from the STORE's values, not from the possibly
     * stale copy the wrapper is carrying.
     * <p>
     * The dispatch path discovers tasks with a search query, which lags the store by up to the
     * index refresh interval, so the executing instance can hold counters that predate writes
     * already committed. {@code persistTerminalState}'s compare-and-set protects only the document
     * version, so incrementing a stale base then CAS-writing it succeeds and silently loses the
     * newer count. Observed in CI as a periodic task reporting one success after two successful
     * executions ({@code SchedulerServiceImplTest.testMetricsAndHistory}).
     */
    @Test
    public void testTerminalCompletionRebasesCountersOnStoreValues() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        TaskExecutor executor = new TaskExecutor() {
            @Override public String getTaskType() { return "stale-counters"; }
            @Override public void execute(ScheduledTask task, TaskStatusCallback callback) {
                callback.complete();
                done.countDown();
            }
        };

        // What the wrapper carries: a search-lagged view that has not seen the first success.
        ScheduledTask stale = TaskTestFixtures.baseTask("stale-counters");
        stale.setOneShot(false);
        stale.setPeriod(60_000);
        stale.setSuccessCount(0);
        stale.setFailureCount(0);

        // What the store actually holds: one success already recorded, with its history entry.
        ScheduledTask store = TaskTestFixtures.baseTask("stale-counters");
        store.setItemId(stale.getItemId());
        store.setStatus(ScheduledTask.TaskStatus.RUNNING);
        store.setExecutingNodeId(NODE);
        store.setSuccessCount(1);
        Map<String, Object> storeDetails = new HashMap<>();
        List<Map<String, Object>> storeHistory = new ArrayList<>();
        storeHistory.add(Collections.singletonMap("status", "SUCCESS"));
        storeDetails.put("executionHistory", storeHistory);
        store.setStatusDetails(storeDetails);
        when(schedulerService.getTask(eq(stale.getItemId()), eq(true))).thenReturn(store);

        executionManager.executeTask(stale, executor);
        assertTrue(done.await(5, TimeUnit.SECONDS));
        awaitStatus(stale, ScheduledTask.TaskStatus.SCHEDULED, 5000);

        assertEquals(2, stale.getSuccessCount(),
            "the second success must count from the store's value (1), not the stale copy's (0)");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history =
            (List<Map<String, Object>>) stale.getStatusDetails().get("executionHistory");
        assertEquals(2, history.size(),
            "history must extend the store's entries rather than restart from the stale copy's");
    }

    @Test
    public void testPrepareForExecutionRejectsDisabledAndWrongStatus() {
        ScheduledTask disabled = TaskTestFixtures.baseTask("p");
        disabled.setEnabled(false);
        assertFalse(executionManager.prepareForExecution(disabled));

        ScheduledTask completed = TaskTestFixtures.baseTask("p");
        completed.setStatus(ScheduledTask.TaskStatus.COMPLETED);
        assertFalse(executionManager.prepareForExecution(completed));
    }

    @Test
    public void testPrepareForExecutionRejectsNotDueScheduled() {
        ScheduledTask task = TaskTestFixtures.baseTask("p");
        task.setNextScheduledExecution(new Date(System.currentTimeMillis() + 60_000));
        assertFalse(executionManager.prepareForExecution(task));
        verify(lockManager, never()).acquireLock(any());
    }

    @Test
    public void testPrepareForExecutionAcquiresLockAndSetsRunning() {
        ScheduledTask task = TaskTestFixtures.baseTask("p");
        task.setNextScheduledExecution(new Date(System.currentTimeMillis() - 1000));
        assertTrue(executionManager.prepareForExecution(task));
        assertEquals(ScheduledTask.TaskStatus.RUNNING, task.getStatus());
        verify(lockManager).acquireLock(task);
        verify(schedulerService).saveTask(task);
    }

    @Test
    public void testPrepareForExecutionFailsWhenLockDenied() {
        when(lockManager.acquireLock(any())).thenReturn(false);
        ScheduledTask task = TaskTestFixtures.baseTask("p");
        assertFalse(executionManager.prepareForExecution(task));
        assertEquals(ScheduledTask.TaskStatus.SCHEDULED, task.getStatus());
    }

    /**
     * Waits for the wrapper's asynchronous terminal transition to land on the shared task object.
     * The executor's callback returns before the wrapper finishes its bookkeeping, so asserting
     * the final status right after the latch (or after a fixed sleep) races the wrapper thread.
     */
    private static void awaitStatus(ScheduledTask task, ScheduledTask.TaskStatus expected, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (task.getStatus() != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
    }

    @Test
    public void testExecuteTaskDuplicateDispatchIsSkipped() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger runs = new AtomicInteger();
        TaskExecutor executor = new TaskExecutor() {
            @Override public String getTaskType() { return "dup"; }
            @Override public void execute(ScheduledTask task, TaskStatusCallback callback) throws Exception {
                runs.incrementAndGet();
                started.countDown();
                release.await(5, TimeUnit.SECONDS);
                callback.complete();
            }
        };
        ScheduledTask task = TaskTestFixtures.baseTask("dup");
        executionManager.executeTask(task, executor);
        assertTrue(started.await(5, TimeUnit.SECONDS));
        // Second dispatch while claim held
        executionManager.executeTask(task, executor);
        release.countDown();
        // Deliberate quiet window for a NEGATIVE assertion: a wrongly accepted duplicate
        // dispatch would start within milliseconds. Too short can only miss a violation
        // (false green), never fail a healthy run.
        Thread.sleep(200);
        assertEquals(1, runs.get());
    }

    @Test
    public void testExecuteTaskInvokesResumeForCrashedWhenCanResume() throws Exception {
        CountDownLatch resumed = new CountDownLatch(1);
        AtomicInteger executeCount = new AtomicInteger();
        AtomicInteger resumeCount = new AtomicInteger();
        TaskExecutor executor = new TaskExecutor() {
            @Override public String getTaskType() { return "resume"; }
            @Override public void execute(ScheduledTask task, TaskStatusCallback callback) {
                executeCount.incrementAndGet();
                callback.complete();
            }
            @Override public boolean canResume(ScheduledTask task) { return true; }
            @Override public void resume(ScheduledTask task, TaskStatusCallback callback) {
                resumeCount.incrementAndGet();
                resumed.countDown();
                callback.complete();
            }
        };
        ScheduledTask task = TaskTestFixtures.baseTask("resume");
        task.setStatus(ScheduledTask.TaskStatus.CRASHED);
        task.setCheckpointData(Collections.singletonMap("step", 1));
        executionManager.executeTask(task, executor);
        assertTrue(resumed.await(5, TimeUnit.SECONDS));
        assertEquals(1, resumeCount.get());
        assertEquals(0, executeCount.get());
    }

    @Test
    public void testCancelTaskCancelsFutureAndAllowsEventualRedispatch() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        TaskExecutor executor = new TaskExecutor() {
            @Override public String getTaskType() { return "cancel"; }
            @Override public void execute(ScheduledTask task, TaskStatusCallback callback) throws Exception {
                started.countDown();
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    callback.fail("cancelled");
                    return;
                }
                callback.complete();
            }
        };
        ScheduledTask task = TaskTestFixtures.baseTask("cancel");
        executionManager.executeTask(task, executor);
        assertTrue(started.await(5, TimeUnit.SECONDS));
        executionManager.cancelTask(task.getItemId());
        // Wrapper exits after interrupt and releases claim; then redispatch works
        CountDownLatch started2 = new CountDownLatch(1);
        TaskExecutor executor2 = new TaskExecutor() {
            @Override public String getTaskType() { return "cancel"; }
            @Override public void execute(ScheduledTask t, TaskStatusCallback callback) {
                started2.countDown();
                callback.complete();
            }
        };
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline && started2.getCount() > 0) {
            executionManager.executeTask(task, executor2);
            if (started2.await(100, TimeUnit.MILLISECONDS)) {
                break;
            }
        }
        assertEquals(0, started2.getCount(), "after cancel+wrapper cleanup, redispatch should succeed");
    }

    @Test
    public void testHandleTaskErrorSchedulesRetryWithinBudget() throws Exception {
        CountDownLatch failed = new CountDownLatch(1);
        CountDownLatch retried = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        TaskExecutor executor = new TaskExecutor() {
            @Override public String getTaskType() { return "retry-ok"; }
            @Override public void execute(ScheduledTask task, TaskStatusCallback callback) {
                int n = attempts.incrementAndGet();
                if (n == 1) {
                    callback.fail("transient");
                    failed.countDown();
                } else {
                    callback.complete();
                    retried.countDown();
                }
            }
        };
        when(executorRegistry.getExecutor("retry-ok")).thenReturn(executor);
        ScheduledTask task = TaskTestFixtures.baseTask("retry-ok");
        task.setMaxRetries(3);
        task.setRetryDelay(50);
        task.setFailureCount(0);

        executionManager.executeTask(task, executor);
        assertTrue(failed.await(5, TimeUnit.SECONDS));
        assertTrue(retried.await(5, TimeUnit.SECONDS));
        assertEquals(2, attempts.get());
        long deadline = System.currentTimeMillis() + 2000;
        while (task.getStatus() != ScheduledTask.TaskStatus.COMPLETED
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(ScheduledTask.TaskStatus.COMPLETED, task.getStatus());
        verify(schedulerService, atLeast(1)).saveTaskWithRefresh(task);
    }

    @Test
    public void testHandleTaskErrorOneShotExhaustsRetriesStaysFailed() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        TaskExecutor executor = new TaskExecutor() {
            @Override public String getTaskType() { return "exhaust"; }
            @Override public void execute(ScheduledTask task, TaskStatusCallback callback) {
                callback.fail("permanent");
                done.countDown();
            }
        };
        ScheduledTask task = TaskTestFixtures.baseTask("exhaust");
        task.setMaxRetries(0);
        task.setFailureCount(0);
        task.setRetryDelay(10);

        executionManager.executeTask(task, executor);
        assertTrue(done.await(5, TimeUnit.SECONDS));
        awaitStatus(task, ScheduledTask.TaskStatus.FAILED, 5000);
        assertEquals(ScheduledTask.TaskStatus.FAILED, task.getStatus());
        assertEquals(1, task.getFailureCount());
        assertTrue(task.isEnabled());
        verify(schedulerService, atLeastOnce()).saveTaskWithRefresh(task);
        verify(executorRegistry, never()).getExecutor(anyString());
    }

    @Test
    public void testHandleTaskErrorPeriodicResetsFailureCountAfterExhaustion() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        TaskExecutor executor = new TaskExecutor() {
            @Override public String getTaskType() { return "period-fail"; }
            @Override public void execute(ScheduledTask task, TaskStatusCallback callback) {
                callback.fail("boom");
                done.countDown();
            }
        };
        ScheduledTask task = TaskTestFixtures.periodicTask("period-fail", 60_000);
        task.setMaxRetries(0);
        task.setFailureCount(0);
        task.setRetryDelay(10);

        executionManager.executeTask(task, executor);
        assertTrue(done.await(5, TimeUnit.SECONDS));
        awaitStatus(task, ScheduledTask.TaskStatus.SCHEDULED, 5000);
        assertEquals(0, task.getFailureCount());
        assertEquals(ScheduledTask.TaskStatus.SCHEDULED, task.getStatus());
        assertNotNull(task.getNextScheduledExecution());
        verify(schedulerService, atLeastOnce()).saveTaskWithRefresh(task);
    }

    @Test
    public void testHandleTaskErrorSkipsRetryScheduleDuringShutdown() throws Exception {
        CountDownLatch inFlight = new CountDownLatch(1);
        CountDownLatch allowFail = new CountDownLatch(1);
        TaskExecutor executor = new TaskExecutor() {
            @Override public String getTaskType() { return "shut-retry"; }
            @Override public void execute(ScheduledTask task, TaskStatusCallback callback) throws Exception {
                inFlight.countDown();
                allowFail.await(5, TimeUnit.SECONDS);
                callback.fail("while-shutting-down");
            }
        };
        ScheduledTask task = TaskTestFixtures.baseTask("shut-retry");
        task.setMaxRetries(3);
        task.setRetryDelay(50);
        when(executorRegistry.getExecutor("shut-retry")).thenReturn(executor);

        executionManager.executeTask(task, executor);
        assertTrue(inFlight.await(5, TimeUnit.SECONDS));
        // Ignore the stubbing / any pre-shutdown registry lookups; we only care about retries after shutdown.
        clearInvocations(executorRegistry);
        // Release the in-flight task shortly after shutdown starts so awaitTermination can finish
        // without waiting the full timeout (shutdown cancels futures then awaits the pool).
        Thread releaser = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            allowFail.countDown();
        }, "shut-retry-releaser");
        releaser.setDaemon(true);
        releaser.start();
        executionManager.shutdown();
        releaser.join(2000);
        awaitStatus(task, ScheduledTask.TaskStatus.SCHEDULED, 5000);
        assertEquals(ScheduledTask.TaskStatus.SCHEDULED, task.getStatus());
        assertEquals(1, task.getFailureCount());
        // No second attempt — retry schedule skipped after scheduler shutdown
        verify(executorRegistry, never()).getExecutor("shut-retry");
    }

    @Test
    public void testHandleTaskCompletionOneShotDisablesAndClearsNext() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        TaskExecutor executor = new TaskExecutor() {
            @Override public String getTaskType() { return "oneshot-ok"; }
            @Override public void execute(ScheduledTask task, TaskStatusCallback callback) {
                callback.complete();
                done.countDown();
            }
        };
        ScheduledTask task = TaskTestFixtures.baseTask("oneshot-ok");
        task.setNextScheduledExecution(new Date());

        executionManager.executeTask(task, executor);
        assertTrue(done.await(5, TimeUnit.SECONDS));
        awaitStatus(task, ScheduledTask.TaskStatus.COMPLETED, 5000);
        assertEquals(ScheduledTask.TaskStatus.COMPLETED, task.getStatus());
        assertFalse(task.isEnabled());
        assertNull(task.getNextScheduledExecution());
        verify(schedulerService, atLeastOnce()).saveTaskWithRefresh(task);
    }

    @Test
    public void testHandleTaskCompletionPeriodicReschedules() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        TaskExecutor executor = new TaskExecutor() {
            @Override public String getTaskType() { return "period-ok"; }
            @Override public void execute(ScheduledTask task, TaskStatusCallback callback) {
                callback.complete();
                done.countDown();
            }
        };
        ScheduledTask task = TaskTestFixtures.periodicTask("period-ok", 5_000);
        long before = System.currentTimeMillis();

        executionManager.executeTask(task, executor);
        assertTrue(done.await(5, TimeUnit.SECONDS));
        awaitStatus(task, ScheduledTask.TaskStatus.SCHEDULED, 5000);
        assertEquals(ScheduledTask.TaskStatus.SCHEDULED, task.getStatus());
        assertNotNull(task.getNextScheduledExecution());
        assertTrue(task.getNextScheduledExecution().getTime() >= before + 5_000);
    }

    @Test
    public void testHandleTaskCompletionPeriodZeroDoesNotReschedule() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        TaskExecutor executor = new TaskExecutor() {
            @Override public String getTaskType() { return "period-zero"; }
            @Override public void execute(ScheduledTask task, TaskStatusCallback callback) {
                callback.complete();
                done.countDown();
            }
        };
        ScheduledTask task = TaskTestFixtures.rawTask("period-zero");
        task.setOneShot(false);
        TaskTestFixtures.setPeriodField(task, 0L);

        executionManager.executeTask(task, executor);
        assertTrue(done.await(5, TimeUnit.SECONDS));
        awaitStatus(task, ScheduledTask.TaskStatus.COMPLETED, 5000);
        assertEquals(ScheduledTask.TaskStatus.COMPLETED, task.getStatus());
    }

    @Test
    public void testCompletionAndErrorIgnoredWhenNotRunning() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        TaskExecutor executor = new TaskExecutor() {
            @Override public String getTaskType() { return "ignored-cb"; }
            @Override public void execute(ScheduledTask task, TaskStatusCallback callback) {
                task.setStatus(ScheduledTask.TaskStatus.CANCELLED);
                callback.complete();
                callback.fail("should-ignore");
                done.countDown();
            }
        };
        ScheduledTask task = TaskTestFixtures.baseTask("ignored-cb");
        long completedBefore = metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_COMPLETED);

        executionManager.executeTask(task, executor);
        assertTrue(done.await(5, TimeUnit.SECONDS));
        // Deliberate quiet window for a NEGATIVE assertion (callbacks must have been ignored);
        // a poll cannot confirm that nothing happened.
        Thread.sleep(100);
        assertEquals(ScheduledTask.TaskStatus.CANCELLED, task.getStatus());
        assertEquals(completedBefore, metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_COMPLETED));
    }

    @Test
    public void testReclaimPrematureCrashBeforeCompletion() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        TaskExecutor executor = new TaskExecutor() {
            @Override public String getTaskType() { return "reclaim"; }
            @Override public void execute(ScheduledTask task, TaskStatusCallback callback) {
                task.setStatus(ScheduledTask.TaskStatus.CRASHED);
                // executingNodeId already set by wrapper to NODE
                callback.complete();
                done.countDown();
            }
        };
        ScheduledTask task = TaskTestFixtures.baseTask("reclaim");

        executionManager.executeTask(task, executor);
        assertTrue(done.await(5, TimeUnit.SECONDS));
        awaitStatus(task, ScheduledTask.TaskStatus.COMPLETED, 5000);
        assertEquals(ScheduledTask.TaskStatus.COMPLETED, task.getStatus());
        assertFalse(task.isEnabled());
    }

    @Test
    public void testWrapperSkipsOnShutdownAndReleasesDispatchClaim() throws Exception {
        when(schedulerService.isShutdownNow()).thenReturn(true);
        CountDownLatch executed = new CountDownLatch(1);
        TaskExecutor executor = new TaskExecutor() {
            @Override public String getTaskType() { return "shut-skip"; }
            @Override public void execute(ScheduledTask task, TaskStatusCallback callback) {
                executed.countDown();
                callback.complete();
            }
        };
        ScheduledTask task = TaskTestFixtures.baseTask("shut-skip");
        executionManager.executeTask(task, executor);
        Thread.sleep(150);
        assertEquals(1, executed.getCount());

        when(schedulerService.isShutdownNow()).thenReturn(false);
        CountDownLatch started = new CountDownLatch(1);
        TaskExecutor executor2 = new TaskExecutor() {
            @Override public String getTaskType() { return "shut-skip"; }
            @Override public void execute(ScheduledTask t, TaskStatusCallback callback) {
                started.countDown();
                callback.complete();
            }
        };
        executionManager.executeTask(task, executor2);
        assertTrue(started.await(5, TimeUnit.SECONDS), "dispatch claim must be released after shutdown skip");
    }

    @Test
    public void testPrepareForExecutionClearsWaitingState() {
        ScheduledTask task = TaskTestFixtures.baseTask("waiting");
        task.setStatus(ScheduledTask.TaskStatus.WAITING);
        task.setWaitingOnTasks(new java.util.HashSet<>(Collections.singleton("dep")));
        task.setWaitingForTaskType("depType");
        task.setNextScheduledExecution(new Date(System.currentTimeMillis() - 1000));

        assertTrue(executionManager.prepareForExecution(task));
        assertEquals(ScheduledTask.TaskStatus.RUNNING, task.getStatus());
        assertNull(task.getWaitingOnTasks());
        assertNull(task.getWaitingForTaskType());
    }

    @Test
    public void testPrepareForExecutionCrashedIgnoresFutureNextScheduled() {
        ScheduledTask task = TaskTestFixtures.baseTask("crash-due");
        task.setStatus(ScheduledTask.TaskStatus.CRASHED);
        task.setNextScheduledExecution(new Date(System.currentTimeMillis() + 60_000));

        assertTrue(executionManager.prepareForExecution(task));
        assertEquals(ScheduledTask.TaskStatus.RUNNING, task.getStatus());
    }

    @Test
    public void testExecuteTaskSkipsWhenAlreadyRunningOrDisabled() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        TaskExecutor executor = new TaskExecutor() {
            @Override public String getTaskType() { return "skip"; }
            @Override public void execute(ScheduledTask task, TaskStatusCallback callback) {
                runs.incrementAndGet();
                callback.complete();
            }
        };
        ScheduledTask running = TaskTestFixtures.runningTask("skip", NODE);
        executionManager.executeTask(running, executor);
        Thread.sleep(100);
        assertEquals(0, runs.get());

        ScheduledTask disabled = TaskTestFixtures.baseTask("skip");
        disabled.setEnabled(false);
        executionManager.executeTask(disabled, executor);
        Thread.sleep(100);
        assertEquals(0, runs.get());
    }

    @Test
    public void testTerminalCompleteSkippedWhenCancelledInStore() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        TaskExecutor executor = new TaskExecutor() {
            @Override public String getTaskType() { return "cancel-race"; }
            @Override public void execute(ScheduledTask task, TaskStatusCallback callback) {
                ScheduledTask cancelled = TaskTestFixtures.baseTask("cancel-race");
                cancelled.setItemId(task.getItemId());
                cancelled.setStatus(ScheduledTask.TaskStatus.CANCELLED);
                when(schedulerService.getTask(eq(task.getItemId()), eq(true))).thenReturn(cancelled);
                callback.complete();
                done.countDown();
            }
        };
        ScheduledTask task = TaskTestFixtures.baseTask("cancel-race");
        executionManager.executeTask(task, executor);
        assertTrue(done.await(5, TimeUnit.SECONDS));
        // persistTerminalState() is skipped (terminal transition correctly bailed out above), but
        // the wrapper's cleanup still CAS-clears executingNodeId once; that write is expected to
        // fail harmlessly against a real store since the document moved on to CANCELLED.
        // timeout() waits for the asynchronous cleanup instead of betting a fixed sleep on it.
        verify(schedulerService, timeout(5000).times(1)).saveTaskWithRefresh(any());
        assertEquals(ScheduledTask.TaskStatus.CANCELLED, task.getStatus());
        assertEquals(0, metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_COMPLETED));
    }

    @Test
    public void testTerminalCompleteSkippedWhenPeerHoldsLock() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        TaskExecutor executor = new TaskExecutor() {
            @Override public String getTaskType() { return "peer-lock"; }
            @Override public void execute(ScheduledTask task, TaskStatusCallback callback) {
                ScheduledTask peer = TaskTestFixtures.runningTask("peer-lock", "peer-node");
                peer.setItemId(task.getItemId());
                peer.setLockDate(new Date());
                when(schedulerService.getTask(eq(task.getItemId()), eq(true))).thenReturn(peer);
                when(lockManager.isLockExpired(peer)).thenReturn(false);
                callback.complete();
                done.countDown();
            }
        };
        ScheduledTask task = TaskTestFixtures.baseTask("peer-lock");
        executionManager.executeTask(task, executor);
        assertTrue(done.await(5, TimeUnit.SECONDS));
        // persistTerminalState() is skipped (peer holds the lock), but the wrapper's cleanup still
        // CAS-clears executingNodeId once; that write is expected to fail harmlessly against a real
        // store since the peer is the authoritative owner.
        // timeout() waits for the asynchronous cleanup instead of betting a fixed sleep on it.
        verify(schedulerService, timeout(5000).times(1)).saveTaskWithRefresh(any());
        assertEquals(ScheduledTask.TaskStatus.RUNNING, task.getStatus());
    }

    @Test
    public void testAbortPreparedExecutionOnShutdownReleasesLockAndMarksCrashed() throws Exception {
        AtomicInteger prepares = new AtomicInteger();
        // First call: not shutting down so prepare runs; flip during prepare via answer on acquireLock
        when(lockManager.acquireLock(any())).thenAnswer(inv -> {
            prepares.incrementAndGet();
            when(schedulerService.isShutdownNow()).thenReturn(true);
            return true;
        });

        CountDownLatch executed = new CountDownLatch(1);
        TaskExecutor executor = new TaskExecutor() {
            @Override public String getTaskType() { return "abort-prep"; }
            @Override public void execute(ScheduledTask task, TaskStatusCallback callback) {
                executed.countDown();
                callback.complete();
            }
        };
        ScheduledTask task = TaskTestFixtures.baseTask("abort-prep");
        executionManager.executeTask(task, executor);
        // Positive half: wait for the asynchronous abort to land instead of a fixed sleep.
        awaitStatus(task, ScheduledTask.TaskStatus.CRASHED, 5000);
        assertEquals(ScheduledTask.TaskStatus.CRASHED, task.getStatus());
        // Negative half: the executor must never have run (green-direction check).
        assertEquals(1, executed.getCount(), "executor must not run after shutdown-abort");
        assertNull(task.getLockOwner());
        verify(schedulerService, atLeastOnce()).saveTask(any(ScheduledTask.class), eq(true));
    }
}
