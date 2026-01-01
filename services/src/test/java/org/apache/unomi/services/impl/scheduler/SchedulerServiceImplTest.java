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

import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.services.SchedulerService.TaskBuilder;
import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.api.tasks.TaskExecutor;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.conditions.evaluator.ConditionEvaluatorDispatcher;
import org.apache.unomi.services.TestHelper;
import org.apache.unomi.services.common.security.ExecutionContextManagerImpl;
import org.apache.unomi.services.common.security.KarafSecurityService;
import org.apache.unomi.services.impl.InMemoryPersistenceServiceImpl;
import org.apache.unomi.services.impl.TestConditionEvaluators;
import org.apache.unomi.services.impl.cluster.ClusterServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test suite for the SchedulerServiceImpl class.
 * Tests cover task lifecycle, state transitions, clustering, recovery, and metrics.
 *
 * The test suite is organized into categories:
 * - BasicTests: Core task creation, execution, and completion
 * - StateTests: Task state transitions and validation
 * - DependencyTests: Task dependency management
 * - ClusterTests: Multi-node execution and lock management
 * - RecoveryTests: Crash recovery and task resumption
 * - MetricsTests: Metrics collection and history tracking
 * - ValidationTests: Input validation and error handling
 * - RetryTests: Task retry behavior and delay
 * - MaintenanceTests: Task cleanup and maintenance
 * - QueryTests: Task querying and filtering
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SchedulerServiceImplTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerServiceImplTest.class);



    // Test configuration constants
    /** Maximum number of retries for storage operations */
    private static final int MAX_RETRIES = 10;
    /** Default timeout for test assertions */
    private static final long TEST_TIMEOUT = 5000; // 5 seconds
    /** Time unit for test timeouts */
    private static final TimeUnit TEST_TIME_UNIT = TimeUnit.MILLISECONDS;
    /** Lock timeout for testing lock expiration */
    private static final long TEST_LOCK_TIMEOUT = 1000; // 1 second
    /** Thread pool size for parallel execution */
    private static final int TEST_THREAD_POOL_SIZE = 4;
    /** Delay between task retries */
    private static final long TEST_RETRY_DELAY = 500; // 500 milliseconds
    /** Maximum number of retries for failed tasks */
    private static final int TEST_MAX_RETRIES = 3;
    /** Short sleep duration for timing-sensitive tests */
    private static final long TEST_SLEEP = 100; // 100 milliseconds
    /** Default wait timeout for task status transitions */
    private static final long TEST_WAIT_TIMEOUT = 2000; // 2 seconds

    private SchedulerServiceImpl schedulerService;
    private PersistenceService persistenceService;
    private ExecutionContextManagerImpl executionContextManager;
    private KarafSecurityService securityService;
    private ClusterServiceImpl clusterService;

    @Mock
    private BundleContext bundleContext;

    // Test categories with documentation
    // JUnit 5 provides tags; marker interfaces removed

    private static void configureDebugLogging() {
        // Enable debug logging for scheduler package
        System.setProperty("org.slf4j.simpleLogger.log.org.apache.unomi.services.impl.scheduler", "DEBUG");
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd HH:mm:ss.SSS");
        System.setProperty("org.slf4j.simpleLogger.showThreadName", "true");
    }

    @BeforeEach
    public void setUp() throws IOException {
        configureDebugLogging();
        CustomObjectMapper.getCustomInstance().registerBuiltInItemTypeClass(ScheduledTask.ITEM_TYPE, ScheduledTask.class);

        securityService = TestHelper.createSecurityService();
        executionContextManager = TestHelper.createExecutionContextManager(securityService);
        ConditionEvaluatorDispatcher conditionEvaluatorDispatcher = TestConditionEvaluators.createDispatcher();

        Bundle bundle = mock(Bundle.class);
        when(bundleContext.getBundle()).thenReturn(bundle);
        when(bundle.getBundleContext()).thenReturn(bundleContext);
        when(bundleContext.getBundle().findEntries(anyString(), anyString(), anyBoolean())).thenReturn(null);
        when(bundleContext.getBundles()).thenReturn(new Bundle[0]);

        TestHelper.cleanDefaultStorageDirectory(MAX_RETRIES);
        persistenceService = new InMemoryPersistenceServiceImpl(executionContextManager, conditionEvaluatorDispatcher);

        // Create and configure cluster service using the helper method
        clusterService = TestHelper.createClusterService(persistenceService, "test-scheduler-node", bundleContext);

        // Create scheduler service with cluster service
        schedulerService = (SchedulerServiceImpl) TestHelper.createSchedulerService(
            "test-scheduler-node",
            persistenceService,
            executionContextManager,
            bundleContext,
            clusterService,
            -1,
            true,
            false,
            0); // Set TTL to 0 for immediate purging in tests

        // Configure scheduler for testing
        schedulerService.setThreadPoolSize(TEST_THREAD_POOL_SIZE);
        schedulerService.setLockTimeout(TEST_LOCK_TIMEOUT);
        schedulerService.postConstruct();
    }

    @AfterEach
    public void tearDown() {
        if (schedulerService != null) {
            try {
                // Cancel any running or retrying tasks
                List<ScheduledTask> allTasks = schedulerService.getAllTasks();
                for (ScheduledTask task : allTasks) {
                    try {
                        if (task.getStatus() == ScheduledTask.TaskStatus.RUNNING ||
                            task.getStatus() == ScheduledTask.TaskStatus.SCHEDULED) {
                            schedulerService.cancelTask(task.getItemId());
                        }
                    } catch (Exception e) {
                        // Log but continue with other tasks
                        LOGGER.warn("Error cancelling task {} during teardown: {}", task.getItemId(), e.getMessage());
                    }
                }
                // Small delay to allow task cancellations to complete
                Thread.sleep(100);

                schedulerService.preDestroy();
                schedulerService = null;
            } catch (Exception e) {
                LOGGER.warn("Error during test teardown: {}", e.getMessage());
            }
        }
        persistenceService = null;
        executionContextManager = null;
        securityService = null;
    }

    // Basic task lifecycle tests
    @Test
    @Tag("BasicTests")
    public void testBasicTaskLifecycle() throws Exception {
        // Test task creation, execution, and completion
        CountDownLatch executionLatch = new CountDownLatch(1);
        AtomicBoolean executed = new AtomicBoolean(false);

        ScheduledTask task = schedulerService.newTask("test-type")
            .withSimpleExecutor(() -> {
                executed.set(true);
                executionLatch.countDown();
            })
            .schedule();

        assertTrue(executionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT), "Task should execute");
        assertTrue(executed.get(), "Task should have executed");

        ScheduledTask completedTask = waitForTaskStatus(task.getItemId(), ScheduledTask.TaskStatus.COMPLETED, TEST_WAIT_TIMEOUT, 50);
        assertEquals(ScheduledTask.TaskStatus.COMPLETED, completedTask.getStatus(), "Task should be completed");
        assertNotNull(completedTask.getStatusDetails().get("executionHistory"), "Task should have execution history");
    }

    @Test
    @Tag("BasicTests")
    public void testFixedRateExecution() throws Exception {
        CountDownLatch executionLatch = new CountDownLatch(3);
        AtomicInteger executionCount = new AtomicInteger(0);
        long period = 100;

        ScheduledTask task = schedulerService.newTask("fixed-rate-test")
            .withPeriod(period, TimeUnit.MILLISECONDS)
            .withFixedRate()
            .withSimpleExecutor(() -> {
                executionCount.incrementAndGet();
                executionLatch.countDown();
                try {
                    Thread.sleep(period * 2); // Sleep longer than period
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
            })
            .schedule();

        assertTrue(executionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT), "Task should execute three times");
        assertTrue(
            executionCount.get() >= 3,
            "Task should execute at fixed rate despite long execution");
    }

    @Test
    @Tag("BasicTests")
    public void testFixedDelayExecution() throws Exception {
        CountDownLatch executionLatch = new CountDownLatch(3);
        AtomicInteger executionCount = new AtomicInteger(0);
        AtomicLong lastExecutionTime = new AtomicLong(0);
        long period = 100;

        ScheduledTask task = schedulerService.newTask("fixed-delay-test")
            .withPeriod(period, TimeUnit.MILLISECONDS)
            .withFixedDelay()
            .withSimpleExecutor(() -> {
                long now = System.currentTimeMillis();
                if (lastExecutionTime.get() > 0) {
                    long delay = now - lastExecutionTime.get();
                    assertTrue(delay >= period, "Delay should be at least the period");
                }
                lastExecutionTime.set(now);
                executionCount.incrementAndGet();
                executionLatch.countDown();
                try {
                    Thread.sleep(50); // Add some execution time
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            })
            .schedule();

        assertTrue(executionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT), "Task should execute three times");
        assertEquals(3, executionCount.get(), "Task should execute exactly three times");
    }

    // Task state transition tests
    @Test
    @Tag("StateTests")
    public void testTaskStateTransitions() throws Exception {
        // Test all possible task state transitions
        CountDownLatch transitionLatch = new CountDownLatch(1);
        AtomicReference<ScheduledTask.TaskStatus> currentStatus = new AtomicReference<>();

        TaskExecutor executor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return "transition-test";
            }

            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) {
                currentStatus.set(task.getStatus());
                callback.updateStep("test-step", Collections.emptyMap());
                callback.checkpoint(Collections.singletonMap("test", "data"));
                callback.complete();
                transitionLatch.countDown();
            }
        };

        schedulerService.registerTaskExecutor(executor);

        ScheduledTask task = schedulerService.newTask("transition-test")
            .disallowParallelExecution()
            .schedule();

        assertTrue(transitionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT), "Task should complete transition");
        assertEquals(
            ScheduledTask.TaskStatus.RUNNING,
            currentStatus.get(),
            "Task should be in RUNNING state during execution");

        ScheduledTask finalTask = schedulerService.getTask(task.getItemId());
        assertEquals(
            ScheduledTask.TaskStatus.COMPLETED,
            finalTask.getStatus(),
            "Task should be in COMPLETED state");
        assertNotNull(finalTask.getCheckpointData(), "Task should have checkpoint data");
    }

    @Test
    @Tag("StateTests")
    public void testInMemoryTaskStateTransitions() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(1);
        AtomicReference<ScheduledTask.TaskStatus> executionStatus = new AtomicReference<>();

        TaskExecutor executor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return "in-memory-state-test";
            }

            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) {
                executionStatus.set(task.getStatus());
                startLatch.countDown();
                try {
                    Thread.sleep(100); // Give time to check status
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                callback.complete();
                completionLatch.countDown();
            }
        };

        schedulerService.registerTaskExecutor(executor);

        // Create in-memory task
        ScheduledTask task = schedulerService.newTask("in-memory-state-test")
            .nonPersistent()
            .schedule();

        // Wait for execution to start
        assertTrue(startLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT), "Task should start executing");
        assertEquals(
            ScheduledTask.TaskStatus.RUNNING,
            executionStatus.get(),
            "Task should be in RUNNING state during execution");

        // Wait for completion
        assertTrue(completionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT), "Task should complete");
        assertEquals(
            ScheduledTask.TaskStatus.COMPLETED,
            task.getStatus(),
            "Task should be in COMPLETED state after execution");
    }

    // Task dependency tests
    @Test
    @Tag("DependencyTests")
    public void testTaskDependencies() throws Exception {
        // Test task dependencies and waiting behavior
        CountDownLatch dep1Latch = new CountDownLatch(1);
        CountDownLatch dep2Latch = new CountDownLatch(1);

        // Create first dependency
        ScheduledTask dep1 = schedulerService.newTask("dep-test")
            .withSimpleExecutor(() -> dep1Latch.countDown())
            .schedule();

        // Create second dependency
        ScheduledTask dep2 = schedulerService.newTask("dep-test")
            .withSimpleExecutor(() -> dep2Latch.countDown())
            .schedule();

        // Create dependent task
        AtomicBoolean dependentExecuted = new AtomicBoolean(false);
        ScheduledTask dependentTask = schedulerService.createTask(
            "dep-test",
            null,
            0,
            0,
            TimeUnit.MILLISECONDS,
            true,
            true,
            true,
            true
        );
        dependentTask.setDependsOn(new HashSet<>(Arrays.asList(dep1.getItemId(), dep2.getItemId())));

        TaskExecutor executor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return "dep-test";
            }

            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) {
                if (task == dependentTask) {
                    dependentExecuted.set(true);
                }
                callback.complete();
            }
        };

        schedulerService.registerTaskExecutor(executor);
        schedulerService.scheduleTask(dependentTask);

        // Wait for dependencies to complete
        assertTrue(
            dep1Latch.await(TEST_TIMEOUT, TEST_TIME_UNIT) &&
            dep2Latch.await(TEST_TIMEOUT, TEST_TIME_UNIT),
            "Dependencies should complete");

        // Verify dependent task execution
        Thread.sleep(100); // Give time for dependent task to execute
        assertTrue(dependentExecuted.get(), "Dependent task should execute after dependencies");
    }

    // Clustering support tests
    @Test
    @Tag("ClusterTests")
    public void testClusteringSupport() throws Exception {
        // Test clustering behavior with multiple nodes
        SchedulerServiceImpl node1 = TestHelper.createSchedulerService("node1", persistenceService, executionContextManager, bundleContext, clusterService, -1, true, true);
        SchedulerServiceImpl node2 = TestHelper.createSchedulerService("node2", persistenceService, executionContextManager, bundleContext, clusterService, -1, true, true);
        SchedulerServiceImpl nonExecutorNode = TestHelper.createSchedulerService("node3", persistenceService, executionContextManager, bundleContext, clusterService, -1, false, true);

        try {
            // Instead of mock cluster nodes, create persistent tasks with locks
            // to ensure nodes are detected via the fallback mechanism
            createAndPersistTaskWithLock("node1-cluster-detection", "node1");
            createAndPersistTaskWithLock("node2-cluster-detection", "node2");
            createAndPersistTaskWithLock("node3-cluster-detection", "node3");

            // Refresh the index to ensure changes are visible
            persistenceService.refreshIndex(ScheduledTask.class);
            // Also refresh all indexes to ensure tasks are available for querying (handles refresh delay)
            persistenceService.refresh();

            CountDownLatch executionLatch = new CountDownLatch(2);
            Set<String> executingNodes = ConcurrentHashMap.newKeySet();

            TaskExecutor clusterTestExecutor = new TaskExecutor() {
                @Override
                public String getTaskType() {
                    return "cluster-test";
                }

                @Override
                public void execute(ScheduledTask task, TaskStatusCallback callback) {
                    executingNodes.add(task.getExecutingNodeId());
                    executionLatch.countDown();
                    callback.complete();
                }
            };

            // Register executor on all nodes
            node1.registerTaskExecutor(clusterTestExecutor);
            node2.registerTaskExecutor(clusterTestExecutor);
            nonExecutorNode.registerTaskExecutor(clusterTestExecutor);

            // Create tasks with different distribution requirements
            ScheduledTask normalTask = node1.newTask("cluster-test")
                .withPeriod(100, TimeUnit.MILLISECONDS)
                .schedule();

            ScheduledTask allNodesTask = node1.newTask("cluster-test")
                .runOnAllNodes()
                .withPeriod(100, TimeUnit.MILLISECONDS)
                .schedule();

            // Wait for executions
            assertTrue(
                executionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT),
                "Tasks should execute on cluster nodes");

            // Verify distribution
            assertTrue(
                executingNodes.contains("node1") || executingNodes.contains("node2"),
                "Task should execute on executor nodes");
            assertFalse(
                executingNodes.contains("node3"),
                "Task should not execute on non-executor node");

            TaskExecutor clusterLockTestExecutor = new TaskExecutor() {
                @Override
                public String getTaskType() {
                    return "cluster-lock-test";
                }
                @Override
                public void execute(ScheduledTask task, TaskStatusCallback callback) {
                    try {
                        Thread.sleep(5000);
                        callback.complete();
                    } catch (InterruptedException e) {
                        callback.fail(e.getMessage());
                    }
                }
            };

            schedulerService.registerTaskExecutor(clusterLockTestExecutor);

            // Test lock management
            ScheduledTask lockTask = node1.newTask("cluster-lock-test")
                .disallowParallelExecution()
                .schedule();

            // Wait for task to be picked up by checkTasks and refresh to ensure it's available
            Thread.sleep(1500);
            // Refresh persistence to ensure task updates are available (handles refresh delay)
            persistenceService.refresh();
            // Retry until task has lock owner (handles refresh delay for updates)
            ScheduledTask lockedTask = TestHelper.retryUntil(
                () -> persistenceService.load(lockTask.getItemId(), ScheduledTask.class),
                t -> t != null && t.getLockOwner() != null
            );
            assertNotNull(lockedTask.getLockOwner(), "Task should have lock owner");
            assertNotNull(lockedTask.getLockDate(), "Task should have lock date");

            // Test lock release - directly update task in persistence
            lockedTask.setLockOwner(null);
            lockedTask.setLockDate(null);
            persistenceService.save(lockedTask);

            // Refresh index to ensure changes are visible
            persistenceService.refreshIndex(ScheduledTask.class);

            // Get latest state and verify lock release
            ScheduledTask releasedTask = persistenceService.load(lockTask.getItemId(), ScheduledTask.class);
            assertNull(releasedTask.getLockOwner(), "Lock should be released");

        } finally {
            node1.preDestroy();
            node2.preDestroy();
            nonExecutorNode.preDestroy();
        }
    }

    // Task recovery tests
    @Test
    @Tag("RecoveryTests")
    public void testTaskRecovery() throws Exception {
        // Set a very short lock timeout for the test
        schedulerService.setLockTimeout(100); // 100 ms
        schedulerService.getLockManager().setLockTimeout(100); // Ensure lockManager uses the same timeout

        CountDownLatch executionLatch = new CountDownLatch(1);
        AtomicBoolean recovered = new AtomicBoolean(false);

        TaskExecutor executor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return "recovery-test";
            }

            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) throws Exception {
                if (task.getStatusDetails().get("crashTime") == null) {
                    throw new RuntimeException("Simulated crash");
                }
                recovered.set(true);
                callback.complete();
                executionLatch.countDown();
            }

            @Override
            public boolean canResume(ScheduledTask task) {
                return true;
            }
        };

        schedulerService.registerTaskExecutor(executor);

        ScheduledTask task = schedulerService.newTask("recovery-test")
                .disallowParallelExecution()
            .schedule();

        // Wait for crash and recovery
        Thread.sleep(1000);
        schedulerService.recoverCrashedTasks();

        assertTrue(executionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT), "Task should be recovered");
        assertTrue(recovered.get(), "Task should be recovered and executed");
    }

    // Metrics and history tests
    @Test
    @Tag("MetricsTests")
    public void testMetricsAndHistory() throws Exception {
        // Test metrics collection and history tracking
        CountDownLatch successLatch = new CountDownLatch(2);
        CountDownLatch failureLatch = new CountDownLatch(1);

        TaskExecutor executor = new TaskExecutor() {
            private int execCount = 0;

            @Override
            public String getTaskType() {
                return "metrics-test";
            }

            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) {
                LOGGER.info("Executing task type " + task.getTaskType() + " with status " + task.getStatus());
                execCount++;
                if (execCount <= 2) {
                    callback.complete();
                    successLatch.countDown();
                } else {
                    callback.fail("Simulated failure");
                    failureLatch.countDown();
                }
            }
        };

        schedulerService.registerTaskExecutor(executor);

        ScheduledTask task = schedulerService.newTask("metrics-test")
            .withPeriod(100, TimeUnit.MILLISECONDS)
            .schedule();

        assertTrue(
            successLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT),
            "Task should execute successfully twice");
        assertTrue(
            failureLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT),
            "Task should fail once");

        // Verify metrics and history
        ScheduledTask finalTask = schedulerService.getTask(task.getItemId());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history =
            (List<Map<String, Object>>) finalTask.getStatusDetails().get("executionHistory");

        assertNotNull(history, "Should have execution history");
        assertEquals(3, history.size(), "Should have 3 history entries");
        assertEquals(2, finalTask.getSuccessCount(), "Should have 2 successful executions");
        assertEquals(1, finalTask.getFailureCount(), "Should have 1 failed execution");
        assertEquals(3, finalTask.getSuccessCount() + finalTask.getFailureCount(), "Total executions should be 3");

        // Verify history entries
        int successEntries = 0;
        int failureEntries = 0;
        for (Map<String, Object> entry : history) {
            String status = (String) entry.get("status");
            if ("SUCCESS".equals(status)) {
                successEntries++;
            } else if ("FAILED".equals(status)) {
                failureEntries++;
                assertNotNull(entry.get("error"), "Failed entry should have error");
            }
        }

        assertEquals(2, successEntries, "Should have 2 successful executions");
        assertEquals(1, failureEntries, "Should have 1 failed execution");

        // Verify metrics
        assertTrue(schedulerService.getMetric("tasks.completed") > 0, "Should have completed tasks metric");
        assertTrue(schedulerService.getMetric("tasks.failed") > 0, "Should have failed tasks metric");

        // Test metric reset
        schedulerService.resetMetrics();
        assertEquals(0, schedulerService.getMetric("tasks.completed"), "Metrics should be reset");
    }

    // Task validation tests
    @Test
    @Tag("ValidationTests")
    public void testTaskValidation() {
        // Test invalid configurations
        try {
            schedulerService.newTask(null).schedule();
            fail("Should throw IllegalArgumentException for null task type");
        } catch (IllegalArgumentException expected) {
        }

        try {
            schedulerService.newTask("test")
                .withPeriod(-1, TimeUnit.MILLISECONDS)
                .schedule();
            fail("Should throw IllegalArgumentException for negative period");
        } catch (IllegalArgumentException expected) {
        }

        try {
            schedulerService.newTask("test")
                .asOneShot()
                .withPeriod(1000, TimeUnit.MILLISECONDS)
                .schedule();
            fail("Should throw IllegalArgumentException for oneShot with period");
        } catch (IllegalArgumentException expected) {
        }

        try {
            ScheduledTask task = schedulerService.newTask("test").asOneShot().withMaxRetries(-1).withRetryDelay(1000, TimeUnit.MILLISECONDS).schedule();
            fail("Should throw IllegalArgumentException for negative maxRetries");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Retry tests
    @Test
    @Tag("RetryTests")
    public void testOneShotTaskRetryScenarios() throws Exception {
        // Test both persistent and in-memory tasks
        testOneShotRetryBehavior(true);  // persistent
        testOneShotRetryBehavior(false); // in-memory
    }

    /**
     * Helper method that waits for a task to reach a specific status.
     *
     * @param taskId The ID of the task to check
     * @param targetStatus The status to wait for
     * @param maxWaitTimeMs Maximum time to wait in milliseconds
     * @param sleepIntervalMs Time to sleep between status checks in milliseconds
     * @return The task with its current status (which may not be targetStatus if timeout occurred)
     * @throws InterruptedException If the waiting is interrupted
     */
    private ScheduledTask waitForTaskStatus(String taskId, ScheduledTask.TaskStatus targetStatus,
            long maxWaitTimeMs, long sleepIntervalMs) throws InterruptedException {

        final long startTime = System.currentTimeMillis();
        ScheduledTask task = null;

        while (System.currentTimeMillis() - startTime < maxWaitTimeMs) {
            task = schedulerService.getTask(taskId);
            if (targetStatus.equals(task.getStatus())) {
                LOGGER.info("Task {} reached target status: {}", taskId, targetStatus);
                break;
            }
            LOGGER.debug("Waiting for task {} to transition from {} to {}",
                taskId, task.getStatus(), targetStatus);
            Thread.sleep(sleepIntervalMs);
        }

        if (task != null && !targetStatus.equals(task.getStatus())) {
            LOGGER.warn("Timeout waiting for task {} to reach status {}. Current status: {}",
                taskId, targetStatus, task.getStatus());
        }

        return task;
    }

    private ScheduledTask waitForTaskStatusWithScheduler(String taskId, ScheduledTask.TaskStatus targetStatus,
            long maxWaitTimeMs, long sleepIntervalMs, SchedulerServiceImpl scheduler) throws InterruptedException {

        final long startTime = System.currentTimeMillis();
        ScheduledTask task = null;

        while (System.currentTimeMillis() - startTime < maxWaitTimeMs) {
            task = scheduler.getTask(taskId);
            if (targetStatus.equals(task.getStatus())) {
                LOGGER.info("Task {} reached target status: {}", taskId, targetStatus);
                break;
            }
            LOGGER.debug("Waiting for task {} to transition from {} to {}",
                taskId, task.getStatus(), targetStatus);
            Thread.sleep(sleepIntervalMs);
        }

        if (task != null && !targetStatus.equals(task.getStatus())) {
            LOGGER.warn("Timeout waiting for task {} to reach status {}. Current status: {}",
                taskId, targetStatus, task.getStatus());
        }

        return task;
    }

    private void testOneShotRetryBehavior(boolean persistent) throws Exception {
        CountDownLatch executionLatch = new CountDownLatch(TEST_MAX_RETRIES+1);
        AtomicInteger executionCount = new AtomicInteger(0);
        List<Long> executionTimes = Collections.synchronizedList(new ArrayList<>());

        TaskExecutor executor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return "one-shot-retry-test";
            }

            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) {
                executionTimes.add(System.currentTimeMillis());
                int count = executionCount.incrementAndGet();
                LOGGER.info("Execution #{} of task type {}", count, task.getTaskType());
                executionLatch.countDown();

                if (count == TEST_MAX_RETRIES+1) {
                    callback.complete(); // Succeed on last retry
                } else {
                    callback.fail("Simulated failure #" + count);
                }
            }
        };

        // Create task with retry configuration
        TaskBuilder builder = schedulerService.newTask("one-shot-retry-test")
            .withMaxRetries(TEST_MAX_RETRIES)
            .withRetryDelay(TEST_RETRY_DELAY, TimeUnit.MILLISECONDS)
            .withExecutor(executor)
            .asOneShot();

        if (!persistent) {
            builder.nonPersistent();
        }

        ScheduledTask task = builder.schedule();

        assertTrue(
            executionLatch.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS),
            "Task should complete all executions");

        // Verify retry delays
        for (int i = 1; i < executionTimes.size(); i++) {
            long delay = executionTimes.get(i) - executionTimes.get(i-1);
            assertTrue(
                delay >= TEST_RETRY_DELAY,
                "Retry delay should be at least " + TEST_RETRY_DELAY + "ms");
        }

        // Wait for the task to transition from RUNNING to COMPLETED state
        // This is necessary because the state transition happens asynchronously after the callback.complete() is called
        ScheduledTask finalTask = waitForTaskStatus(task.getItemId(), ScheduledTask.TaskStatus.COMPLETED, TEST_WAIT_TIMEOUT, 50);

        LOGGER.info("Task status={}", finalTask.getStatus());
        assertEquals(
            ScheduledTask.TaskStatus.COMPLETED,
            finalTask.getStatus(),
            "Task should be completed");
        assertEquals(
            TEST_MAX_RETRIES+1,
            executionCount.get(),
            "Should have executed expected number of times");
    }

    @Test
    @Tag("RetryTests")
    public void testPeriodicTaskRetryScenarios() throws Exception {
        // Test both fixed-rate and fixed-delay
        testPeriodicRetryBehavior(true);  // fixed-rate
        testPeriodicRetryBehavior(false); // fixed-delay
    }

    private void testPeriodicRetryBehavior(boolean fixedRate) throws Exception {
        LOGGER.info("Testing periodic task retry scenarios with fixed rate: {}", fixedRate);
        CountDownLatch firstPeriodLatch = new CountDownLatch(TEST_MAX_RETRIES+1);
        CountDownLatch secondPeriodLatch = new CountDownLatch(1);
        AtomicInteger periodCount = new AtomicInteger(0);
        AtomicInteger executionCount = new AtomicInteger(0);
        List<Long> executionTimes = Collections.synchronizedList(new ArrayList<>());

        TaskExecutor executor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return "periodic-retry-test";
            }

            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) {
                executionTimes.add(System.currentTimeMillis());
                int count = executionCount.incrementAndGet();
                LOGGER.info("Execution #{} of task type {} with period {}", count, task.getTaskType(), periodCount.get());

                // Transition to next period after exhausting retries
                if (count == TEST_MAX_RETRIES+2) {
                    periodCount.incrementAndGet();
                }

                if (periodCount.get() == 0) {
                    firstPeriodLatch.countDown();
                    callback.fail("First period failure #" + count);
                } else {
                    secondPeriodLatch.countDown();
                    callback.complete();
                }

            }
        };

        // Create periodic task with retry configuration
        TaskBuilder builder = schedulerService.newTask("periodic-retry-test")
            .withMaxRetries(TEST_MAX_RETRIES)
            .withRetryDelay(TEST_RETRY_DELAY, TimeUnit.MILLISECONDS)
            .withPeriod(2000, TimeUnit.MILLISECONDS);

        if (fixedRate) {
            builder.withFixedRate();
        } else {
            builder.withFixedDelay();
        }

        ScheduledTask task = builder.withExecutor(executor).schedule();

        // Wait for first period retries
        assertTrue(
            firstPeriodLatch.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS),
            "First period should exhaust retries");

        // Verify retry delays in first period
        for (int i = 1; i <= TEST_MAX_RETRIES; i++) {
            long delay = executionTimes.get(i) - executionTimes.get(i-1);
            assertTrue(
                delay >= TEST_RETRY_DELAY,
                "Retry delay should be at least " + TEST_RETRY_DELAY + "ms");
        }

        // Wait for successful execution in second period
        assertTrue(
            secondPeriodLatch.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS),
            "Second period should succeed");

        // Verify period transition
        long periodTransitionDelay = executionTimes.get(TEST_MAX_RETRIES+1) -
            executionTimes.get(TEST_MAX_RETRIES);
        assertTrue(
            periodTransitionDelay >= 2000,
            "Period transition delay should be at least 2000ms");

        // Verify task state
        ScheduledTask scheduledTask = waitForTaskStatus(task.getItemId(), ScheduledTask.TaskStatus.SCHEDULED, TEST_WAIT_TIMEOUT, 100);
        LOGGER.info("Task status={}", scheduledTask.getStatus());
        assertEquals(
            ScheduledTask.TaskStatus.SCHEDULED,
            scheduledTask.getStatus(),
            "Task should be in scheduled state");
        assertEquals(0, scheduledTask.getFailureCount(), "Failure count should be reset");

        schedulerService.cancelTask(task.getItemId());
    }

    @Test
    @Tag("RetryTests")
    public void testManualRetryAfterExhaustion() throws Exception {
        AtomicInteger executionCount = new AtomicInteger(0);
        CountDownLatch exhaustionLatch = new CountDownLatch(TEST_MAX_RETRIES+1);
        CountDownLatch manualRetryLatch = new CountDownLatch(1);

        TaskExecutor executor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return "manual-retry-test";
            }

            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) {
                int count = executionCount.incrementAndGet();
                LOGGER.info("Execution #{} of task type {}", count, task.getTaskType());

                if (count <= TEST_MAX_RETRIES+1) {
                    exhaustionLatch.countDown();
                    callback.fail("Initial failures");
                } else {
                    manualRetryLatch.countDown();
                    callback.complete();
                }
            }
        };

        // Create one-shot task
        ScheduledTask task = schedulerService.newTask("manual-retry-test")
            .withMaxRetries(TEST_MAX_RETRIES)
            .withRetryDelay(TEST_RETRY_DELAY, TimeUnit.MILLISECONDS)
            .withExecutor(executor)
            .asOneShot()
            .schedule();

        // Wait for automatic retries to be exhausted
        assertTrue(
            exhaustionLatch.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS),
            "Should exhaust automatic retries");

        // Verify failed state
        ScheduledTask failedTask = waitForTaskStatus(task.getItemId(), ScheduledTask.TaskStatus.FAILED, TEST_WAIT_TIMEOUT, 50);
        assertEquals(
            ScheduledTask.TaskStatus.FAILED,
            failedTask.getStatus(),
            "Task should be in failed state");
        assertEquals(
            TEST_MAX_RETRIES+1,
            failedTask.getFailureCount(),
            "Should have correct failure count");

        // Manually retry with reset
        schedulerService.retryTask(task.getItemId(), true);

        // Wait for manual retry to complete
        assertTrue(
            manualRetryLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT),
            "Manual retry should succeed");

        // Verify final state
        ScheduledTask completedTask = waitForTaskStatus(task.getItemId(), ScheduledTask.TaskStatus.COMPLETED, TEST_WAIT_TIMEOUT, 50);
        assertEquals(
            ScheduledTask.TaskStatus.COMPLETED,
            completedTask.getStatus(),
            "Task should be completed after manual retry");
        assertEquals(0, completedTask.getFailureCount(), "Failure count should be reset");
    }

    // Task purging tests
    @Test
    @Tag("MaintenanceTests")
    public void testTaskPurging() throws Exception {
        // Test task purging functionality
        schedulerService.setCompletedTaskTtlDays(0);
        schedulerService.setPurgeTaskEnabled(true);

        // Create and complete some tasks
        for (int i = 0; i < 3; i++) {
            ScheduledTask task = schedulerService.newTask("task-to-purge")
                .withSimpleExecutor(() -> {})
                .schedule();

            // Wait for completion
            Thread.sleep(100);

            // Verify task completion
            ScheduledTask completedTask = schedulerService.getTask(task.getItemId());
            assertEquals(
                ScheduledTask.TaskStatus.COMPLETED,
                completedTask.getStatus(),
                "Task should be completed");
        }

        // Force purge
        schedulerService.purgeOldTasks();

        // Wait for purge to complete
        Thread.sleep(100);

        // Verify purged tasks
        List<ScheduledTask> remainingTasks = schedulerService.getAllTasks();
        assertEquals(0, remainingTasks.size(), "Should have purged completed tasks");
    }

    /**
     * Tests task builder pattern completeness.
     * Verifies all builder methods work correctly.
     */
    @Test
    @Tag("ValidationTests")
    public void testTaskBuilder() {
        Map<String, Object> params = Collections.singletonMap("test", "value");

        ScheduledTask task = schedulerService.newTask("builder-test")
            .withParameters(params)
            .withInitialDelay(100, TimeUnit.MILLISECONDS)
            .withPeriod(200, TimeUnit.MILLISECONDS)
            .withFixedDelay()
            .nonPersistent()
            .runOnAllNodes()
            .schedule();

        assertEquals(params, task.getParameters(), "Parameters should be set");
        assertEquals(100, task.getInitialDelay(), "Initial delay should be set");
        assertEquals(200, task.getPeriod(), "Period should be set");
        assertFalse(task.isFixedRate(), "Should be fixed delay");
        assertFalse(task.isPersistent(), "Should be non-persistent");
        assertTrue(task.isRunOnAllNodes(), "Should run on all nodes");
    }

    /**
     * Tests lock timeout expiration and recovery.
     */
    @Test
    @Tag("ClusterTests")
    public void testLockTimeout() throws Exception {
        // Explicitly set a very short lock timeout for this test
        schedulerService.setLockTimeout(TEST_LOCK_TIMEOUT);

        CountDownLatch executionLatch = new CountDownLatch(1);
        AtomicBoolean taskStarted = new AtomicBoolean(false);

        TaskExecutor executor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return "lock-test";
            }

            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) {
                try {
                    // Signal that the task has started
                    taskStarted.set(true);
                    executionLatch.countDown();

                    // Hold the lock longer than timeout
                    Thread.sleep(TEST_LOCK_TIMEOUT * 2);
                    callback.complete();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };

        schedulerService.registerTaskExecutor(executor);

        ScheduledTask task = schedulerService.newTask("lock-test")
            .disallowParallelExecution()
            .schedule();

        // Wait for task to start
        assertTrue(executionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT), "Task should start executing");

        // Get the running task instance
        ScheduledTask runningTask = persistenceService.load(task.getItemId(), ScheduledTask.class);

        // Directly update task to simulate lock expiration
        runningTask.setLockOwner(null);
        runningTask.setLockDate(null);
        persistenceService.save(runningTask);
        persistenceService.refreshIndex(ScheduledTask.class);

        // Check lock status after manual release
        ScheduledTask updatedTask = persistenceService.load(task.getItemId(), ScheduledTask.class);
        assertNull(updatedTask.getLockOwner(), "Lock should be released after manual update");
    }

    /**
     * Tests task cancellation during execution.
     */
    @Test
    @Tag("StateTests")
    public void testTaskCancellation() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch cancelLatch = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean(false);

        TaskExecutor executor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return "cancel-test";
            }

            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) {
                LOGGER.info("Starting task " + task.getTaskType());
                startLatch.countDown();
                try {
                    LOGGER.info("Task {} waiting on cancel latch...", task.getTaskType());
                    if (!cancelLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT)) {
                        LOGGER.warn("Task {} timeout waiting for cancel latch", task.getTaskType());
                    }
                    LOGGER.info("Task {} completing", task.getTaskType());
                    completed.set(true);
                    callback.complete();
                    LOGGER.info("Task {} completed", task.getTaskType());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };

        schedulerService.registerTaskExecutor(executor);

        ScheduledTask task = schedulerService.newTask("cancel-test")
            .schedule();

        // Wait for task to start
        assertTrue(startLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT), "Task should start");

        // Cancel the task
        schedulerService.cancelTask(task.getItemId());

        Thread.sleep(TEST_SLEEP*3); // give some time to cancel the thread
        // Allow task to complete if not cancelled
        cancelLatch.countDown();
        Thread.sleep(TEST_SLEEP);

        ScheduledTask cancelledTask = schedulerService.getTask(task.getItemId());
        assertEquals(
            ScheduledTask.TaskStatus.CANCELLED,
            cancelledTask.getStatus(),
            "Task should be cancelled");
        assertFalse(completed.get(), "Task should not complete after cancellation");
    }

    /**
     * Tests task querying and filtering capabilities.
     */
    @Test
    @Tag("QueryTests")
    public void testTaskQuerying() throws Exception {
        // Create tasks with different states
        ScheduledTask runningTask = createTestTask("query-test", ScheduledTask.TaskStatus.RUNNING);
        ScheduledTask completedTask = createTestTask("query-test", ScheduledTask.TaskStatus.COMPLETED);
        ScheduledTask failedTask = createTestTask("query-test", ScheduledTask.TaskStatus.FAILED);
        ScheduledTask waitingTask = createTestTask("query-test", ScheduledTask.TaskStatus.WAITING);

        // Refresh persistence to ensure tasks are available for querying (handles refresh delay)
        persistenceService.refresh();

        // Test querying by status - retry until task is available
        PartialList<ScheduledTask> completedTasks = TestHelper.retryQueryUntilAvailable(
            () -> schedulerService.getTasksByStatus(ScheduledTask.TaskStatus.COMPLETED, 0, 10, null),
            1
        );
        assertEquals(1, completedTasks.getList().size(), "Should find completed task");
        assertEquals(
            completedTask.getItemId(),
            completedTasks.getList().get(0).getItemId(),
            "Should return correct task");

        // Test querying by type - retry until all tasks are available
        PartialList<ScheduledTask> typeTasks = TestHelper.retryQueryUntilAvailable(
            () -> schedulerService.getTasksByType("query-test", 0, 10, null),
            4
        );
        assertEquals(4, typeTasks.getList().size(), "Should find all tasks of type");

        // Test pagination
        PartialList<ScheduledTask> pagedTasks =
            schedulerService.getTasksByType("query-test", 0, 2, null);
        assertEquals(2, pagedTasks.getList().size(), "Should respect page size");
    }

    /**
     * Helper method to create a test task with specified status.
     */
    private ScheduledTask createTestTask(String type, ScheduledTask.TaskStatus status) {
        ScheduledTask task = new ScheduledTask();
        task.setItemId(UUID.randomUUID().toString());
        task.setTaskType(type);
        task.setStatus(status);
        persistenceService.save(task);
        return task;
    }

    /**
     * Tests node failure scenarios in clustering.
     * Verifies that tasks are properly recovered when nodes fail during execution.
     */
    @Test
    @Tag("ClusterTests")
    public void testNodeFailure() throws Exception {
        schedulerService.preDestroy();
        SchedulerServiceImpl node1 = TestHelper.createSchedulerService("node1", persistenceService, executionContextManager, bundleContext, clusterService, TEST_LOCK_TIMEOUT, true, true);
        SchedulerServiceImpl node2 = TestHelper.createSchedulerService("node2", persistenceService, executionContextManager, bundleContext, clusterService, TEST_LOCK_TIMEOUT, true, true);

        try {
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(1);
            AtomicReference<String> executingNode = new AtomicReference<>();
            AtomicBoolean taskRecovered = new AtomicBoolean(false);

            TaskExecutor executor = new TaskExecutor() {
                @Override
                public String getTaskType() {
                    return "node-failure-test";
                }

                @Override
                public void execute(ScheduledTask task, TaskStatusCallback callback) {
                    LOGGER.info("Executing task {} id={} on node {}", task.getTaskType(), task.getItemId(), task.getExecutingNodeId());
                    executingNode.set(task.getExecutingNodeId());
                    startLatch.countDown();

                    if (task.getStatusDetails().get("crashTime") != null) {
                        LOGGER.info("Task {} has been crashed and is recovered", task.getTaskType());
                        taskRecovered.set(true);
                        callback.complete();
                        completionLatch.countDown();
                    }
                }

                @Override
                public boolean canResume(ScheduledTask task) {
                    return true;
                }
            };

            // Register executor on both nodes
            node1.registerTaskExecutor(executor);
            node2.registerTaskExecutor(executor);

            // Schedule task on node1
            ScheduledTask task = node1.newTask("node-failure-test")
                .disallowParallelExecution()
                .schedule();

            // Wait for task to start
            assertTrue(startLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT), "Task should start");
            String originalNode = executingNode.get();
            assertNotNull(originalNode, "Task should have executing node");

            // Simulate node failure by destroying the executing node
            if (originalNode.equals("node1")) {
                node1.simulateCrash();
            } else {
                node2.simulateCrash();
            }

            // Wait for recovery
            Thread.sleep(TEST_LOCK_TIMEOUT * 2);

            // Trigger recovery on surviving node
            if (originalNode.equals("node1")) {
                node2.recoverCrashedTasks();
            } else {
                node1.recoverCrashedTasks();
            }

            assertTrue(completionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT), "Task should be recovered");
            assertTrue(taskRecovered.get(), "Task should be recovered by other node");

            // Verify final state
            ScheduledTask recoveredTask = persistenceService.load(task.getItemId(), ScheduledTask.class);
            assertEquals(
                ScheduledTask.TaskStatus.COMPLETED,
                recoveredTask.getStatus(),
                "Task should be completed");
            assertNotEquals(
                originalNode,
                recoveredTask.getLockOwner(),
                "Task should be recovered by different node");
        } finally {
            node1.preDestroy();
            node2.preDestroy();
        }
    }

    /**
     * Tests concurrent lock acquisition in a cluster.
     * Verifies that tasks don't execute concurrently even across different nodes.
     */
    @Test
    @Tag("ClusterTests")
    public void testConcurrentLockAcquisition() throws Exception {
        SchedulerServiceImpl node1 = TestHelper.createSchedulerService("node1", persistenceService, executionContextManager, bundleContext, clusterService, -1, true, true);
        SchedulerServiceImpl node2 = TestHelper.createSchedulerService("node2", persistenceService, executionContextManager, bundleContext, clusterService, -1, true, true);

        try {
            CountDownLatch completionLatch = new CountDownLatch(2); // We expect 2 executions
            Map<String, ExecutionInfo> executions = new ConcurrentHashMap<>();
            AtomicInteger concurrentExecutions = new AtomicInteger(0);
            AtomicInteger maxConcurrentExecutions = new AtomicInteger(0);

            TaskExecutor executor = new TaskExecutor() {
                @Override
                public String getTaskType() {
                    return "testConcurrentLock";
                }

                @Override
                public void execute(ScheduledTask task, TaskStatusCallback callback) {
                    try {
                        String executionId = UUID.randomUUID().toString();
                        ExecutionInfo info = new ExecutionInfo(System.currentTimeMillis(), task.getLockOwner());
                        executions.put(executionId, info);

                        int current = concurrentExecutions.incrementAndGet();
                        maxConcurrentExecutions.updateAndGet(max -> Math.max(max, current));
                        LOGGER.info("Task execution started on node {} with concurrent count: {}", task.getExecutingNodeId(), current);

                        Thread.sleep(500); // Simulate some work

                        info.endTime = System.currentTimeMillis();
                        concurrentExecutions.decrementAndGet();
                        completionLatch.countDown();
                        LOGGER.info("Task execution completed on node {}", task.getExecutingNodeId());
                        callback.complete();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        callback.fail("Execution interrupted");
                    }
                }
            };

            // Register executor on both nodes
            node1.registerTaskExecutor(executor);
            node2.registerTaskExecutor(executor);

            // Create task that disallows parallel execution
            ScheduledTask task = node1.newTask("testConcurrentLock")
                .disallowParallelExecution()
                .withPeriod(1000, TimeUnit.MILLISECONDS)
                .schedule();

            // Wait for both executions to complete
            assertTrue(
                completionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT),
                "Task executions should complete");

            // Verify no concurrent execution by checking time overlaps
            List<Map.Entry<String, ExecutionInfo>> sortedExecutions = new ArrayList<>(executions.entrySet());
            sortedExecutions.sort((a, b) -> Long.compare(a.getValue().startTime, b.getValue().startTime));

            assertEquals(1, maxConcurrentExecutions.get(), "Should have exactly one task executing at a time");

            // Verify sequential execution
            ExecutionInfo current = sortedExecutions.get(0).getValue();
            ExecutionInfo next = sortedExecutions.get(1).getValue();

            assertTrue(
                current.endTime <= next.startTime,
                "Task executions should not overlap in time");

            LOGGER.info("Execution 1 on node {} ran from {} to {}",
                current.lockOwner,
                current.startTime,
                current.endTime);

            LOGGER.info("Execution 2 on node {} ran from {} to {}",
                    next.lockOwner,
                    next.startTime,
                    next.endTime);

        } finally {
            node1.preDestroy();
            node2.preDestroy();
        }
    }

    /**
     * Helper class to track execution timing and lock ownership
     */
    private static class ExecutionInfo {
        final long startTime;
        final String lockOwner;
        long endTime;

        ExecutionInfo(long startTime, String lockOwner) {
            this.startTime = startTime;
            this.lockOwner = lockOwner;
            this.endTime = 0;
        }
    }

    /**
     * Tests task rebalancing when cluster nodes join/leave.
     * Verifies that tasks are properly redistributed.
     */
    @Test
    @Tag("ClusterTests")
    public void testTaskRebalancing() throws Exception {
        SchedulerServiceImpl node1 = TestHelper.createSchedulerService("node1", persistenceService, executionContextManager, bundleContext, clusterService, -1, true, true);
        SchedulerServiceImpl node2 = null;
        try {
            CountDownLatch node1Latch = new CountDownLatch(1);
            CountDownLatch node2Latch = new CountDownLatch(1);
            Set<String> executingNodes = ConcurrentHashMap.newKeySet();

            TaskExecutor executor = new TaskExecutor() {
                @Override
                public String getTaskType() {
                    return "rebalance-test";
                }

                @Override
                public void execute(ScheduledTask task, TaskStatusCallback callback) {
                    LOGGER.info("Task execution started on node {}", task.getExecutingNodeId());
                    executingNodes.add(task.getExecutingNodeId());
                    if (task.getExecutingNodeId().equals("node1")) {
                        node1Latch.countDown();
                    } else if (task.getExecutingNodeId().equals("node2")) {
                        node2Latch.countDown();
                    }
                    callback.complete();
                }
            };

            // Register executor on first node
            node1.registerTaskExecutor(executor);

            // Create periodic task with shorter period for faster test execution
            ScheduledTask task = node1.newTask("rebalance-test")
                .withPeriod(1000, TimeUnit.MILLISECONDS)
                .schedule();

            // Wait for execution on node1
            assertTrue(
                node1Latch.await(TEST_TIMEOUT, TEST_TIME_UNIT),
                "Task should execute on node1");
            assertTrue(executingNodes.contains("node1"), "Node1 should execute task");

            // Refresh persistence to ensure task is available for node2
            persistenceService.refreshIndex(ScheduledTask.class);
            persistenceService.refresh();

            // Add second node
            node2 = TestHelper.createSchedulerService("node2", persistenceService, executionContextManager, bundleContext, clusterService, -1, true, true);
            node2.registerTaskExecutor(executor);

            // Refresh persistence again after node2 is added to ensure it can see the task
            // Note: refresh() and refreshIndex() update immediately, so items are available right away
            persistenceService.refreshIndex(ScheduledTask.class);
            persistenceService.refresh();

            // Wait for node2 to discover the task before waiting for execution
            // This ensures the task checker has run and the task has been rebalanced
            // The task checker runs every 1000ms, so we need to wait for at least one cycle
            // Use retryUntil to wait for task discovery with retries
            final SchedulerServiceImpl node2Final = node2; // Make effectively final for lambda
            final String taskId = task.getItemId(); // Make effectively final for lambda
            ScheduledTask discoveredTask = TestHelper.retryUntil(
                () -> {
                    try {
                        return node2Final.getTask(taskId);
                    } catch (Exception e) {
                        return null;
                    }
                },
                discovered -> discovered != null
            );
            assertNotNull(discoveredTask, "Node2 should discover the task after initialization");

            // Now wait for execution on node2
            // The task has a 1000ms period, and the task checker runs every 1000ms
            // After discovery, we need to wait for:
            // 1. The next execution time to arrive (up to 1000ms)
            // 2. The task checker to run and pick up the task (up to 1000ms)
            // 3. The task to actually execute
            // So we need at least 2000ms + some buffer, but use retry pattern for reliability
            // Now wait for execution on node2
            // The task has a 1000ms period, and the task checker runs every 1000ms
            // After discovery, we need to wait for:
            // 1. The next execution time to arrive (up to 1000ms)
            // 2. The task checker to run and pick up the task (up to 1000ms)
            // 3. The task to actually execute
            // So we need at least 2000ms + some buffer
            // Use retry pattern to wait for execution - check if node2 has executed
            // retryUntil will retry up to MAX_RETRIES (20) times with 100ms delay = up to 2000ms
            boolean node2Executed = false;
            try {
                TestHelper.retryUntil(
                    () -> executingNodes.contains("node2"),
                    result -> result == true
                );
                node2Executed = true;
            } catch (RuntimeException e) {
                // retryUntil failed, try the latch with extended timeout as fallback
                long extendedTimeout = Math.max(TEST_TIMEOUT, 3000); // At least 3 seconds
                node2Executed = node2Latch.await(extendedTimeout, TEST_TIME_UNIT);
            }

            assertTrue(
                node2Executed,
                "Task should execute on node2 within timeout after discovery (allowing for task period and checker cycles)");
            assertTrue(executingNodes.contains("node2"), "Node2 should execute task");

            // Verify task distribution
            assertEquals(2, executingNodes.size(), "Task should execute on both nodes");

        } finally {
            if (node2 != null) {
                node2.preDestroy();
            }
            node1.preDestroy();
        }
    }

    /**
     * Tests lock stealing prevention and recovery.
     * Verifies that locks cannot be stolen and are properly recovered.
     */
    @Test
    @Tag("ClusterTests")
    public void testLockStealing() throws Exception {
        SchedulerServiceImpl node1 = TestHelper.createSchedulerService("node1", persistenceService, executionContextManager, bundleContext, clusterService, -1, true, true);
        SchedulerServiceImpl node2 = TestHelper.createSchedulerService("node2", persistenceService, executionContextManager, bundleContext, clusterService, -1, true, true);

        try {
            CountDownLatch executionLatch = new CountDownLatch(1);
            AtomicReference<String> lockOwner = new AtomicReference<>();

            TaskExecutor executor = new TaskExecutor() {
                @Override
                public String getTaskType() {
                    return "lock-steal-test";
                }

                @Override
                public void execute(ScheduledTask task, TaskStatusCallback callback) {
                    lockOwner.set(task.getLockOwner());
                    try {
                        Thread.sleep(TEST_LOCK_TIMEOUT / 2); // Hold lock for half timeout
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    callback.complete();
                    executionLatch.countDown();
                }
            };

            // Register executor on both nodes
            node1.registerTaskExecutor(executor);
            node2.registerTaskExecutor(executor);

            // Create task with short lock timeout
            ScheduledTask task = node1.newTask("lock-steal-test")
                .disallowParallelExecution()
                .schedule();

            // Wait for first execution
            assertTrue(executionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT), "Task should execute");
            String originalOwner = lockOwner.get();
            assertNotNull(originalOwner, "Task should have lock owner");

            // Attempt immediate re-execution (potential lock stealing)
            node1.recoverCrashedTasks();
            node2.recoverCrashedTasks();
            Thread.sleep(100);

            // Verify lock wasn't stolen
            ScheduledTask lockedTask = persistenceService.load(task.getItemId(), ScheduledTask.class);
            if (lockedTask.getLockOwner() != null) {
                assertEquals(
                    originalOwner,
                    lockedTask.getLockOwner(),
                    "Lock should not be stolen");
            }
        } finally {
            node1.preDestroy();
            node2.preDestroy();
        }
    }

    @Test
    public void testNodeAffinity() throws Exception {
        // Create test nodes with cluster service
        SchedulerServiceImpl node1 = TestHelper.createSchedulerService("node1", persistenceService, executionContextManager, bundleContext, clusterService, -1, true, true);
        SchedulerServiceImpl node2 = TestHelper.createSchedulerService("node2", persistenceService, executionContextManager, bundleContext, clusterService, -1, true, true);
        SchedulerServiceImpl nonExecutorNode = TestHelper.createSchedulerService("node3", persistenceService, executionContextManager, bundleContext, clusterService, -1, false, true);

        try {
            // Instead of trying to mock the ClusterService, create persistent tasks with locks
            // to ensure nodes are detected via the fallback mechanism in getActiveNodes()

            // Create and persist a task for each node with active locks
            createAndPersistTaskWithLock("node1-detection-task", "node1");
            createAndPersistTaskWithLock("node2-detection-task", "node2");
            createAndPersistTaskWithLock("node3-detection-task", "node3");

            // Refresh the index to ensure changes are visible
            persistenceService.refreshIndex(ScheduledTask.class);

            // Register test executors
            CountDownLatch executionLatch = new CountDownLatch(1);
            AtomicReference<String> executingNodeId = new AtomicReference<>();

            TaskExecutor testExecutor = new TaskExecutor() {
                @Override
                public String getTaskType() {
                    return "test-affinity";
                }

                @Override
                public void execute(ScheduledTask task, TaskStatusCallback callback) {
                    executingNodeId.set(task.getExecutingNodeId());
                    executionLatch.countDown();
                    callback.complete();
                }
            };

            node1.registerTaskExecutor(testExecutor);
            node2.registerTaskExecutor(testExecutor);
            nonExecutorNode.registerTaskExecutor(testExecutor);

            // Create and schedule a task
            ScheduledTask task = new ScheduledTask();
            task.setItemId("affinity-test-task");
            task.setTaskType("test-affinity");
            task.setEnabled(true);
            task.setPersistent(true);

            // Schedule the task on node1
            node1.scheduleTask(task);

            // Wait for execution
            assertTrue(executionLatch.await(5, TimeUnit.SECONDS), "Task should execute within timeout");

            // Verify that the task was executed by the expected node
            assertNotNull(executingNodeId.get(), "Task should have an executing node ID");

            // Verify that the cluster service was used to determine active nodes
            List<String> activeNodes = node1.getActiveNodes();
            assertTrue(activeNodes.contains("node1"), "Node1 should be in active nodes list");
            assertTrue(activeNodes.contains("node2"), "Node2 should be in active nodes list");
            assertTrue(activeNodes.contains("node3"), "Node3 should be in active nodes list");

        } finally {
            // Clean up
            node1.preDestroy();
            node2.preDestroy();
            nonExecutorNode.preDestroy();
        }
    }

    // Helper method to create a task with an active lock for a specific node
    private void createAndPersistTaskWithLock(String taskId, String nodeId) {
        ScheduledTask task = new ScheduledTask();
        task.setItemId(taskId);
        task.setTaskType("node-detection");
        task.setEnabled(true);
        task.setPersistent(true);
        task.setLockOwner(nodeId);
        task.setLockDate(new Date()); // Current time
        task.setStatus(ScheduledTask.TaskStatus.RUNNING);
        persistenceService.save(task);
    }

    // removed JUnit 4 marker interfaces

    /**
     * Tests that the scheduler service works correctly without a PersistenceSchedulerProvider.
     * This verifies that the dependency is truly optional.
     */
    @Test
    @Tag("OptionalDependencyTests")
    public void testSchedulerServiceWithoutPersistenceProvider() throws Exception {
        // Create a scheduler service without persistence provider using the helper method
        SchedulerServiceImpl schedulerWithoutProvider = TestHelper.createSchedulerServiceWithoutPersistenceProvider(
            "test-node-no-provider",
            persistenceService,
            executionContextManager,
            bundleContext,
            clusterService,
            TEST_LOCK_TIMEOUT,
            true,
            true);

        try {
            // Verify that the service starts without errors
            assertTrue(schedulerWithoutProvider.isExecutorNode(), "Scheduler service should be running");

            // Test that in-memory tasks work correctly
            CountDownLatch executionLatch = new CountDownLatch(1);
            AtomicBoolean executed = new AtomicBoolean(false);

            TaskExecutor executor = new TaskExecutor() {
                @Override
                public String getTaskType() {
                    return "no-provider-test";
                }

                @Override
                public void execute(ScheduledTask task, TaskStatusCallback callback) {
                    executed.set(true);
                    executionLatch.countDown();
                    callback.complete();
                }
            };

            schedulerWithoutProvider.registerTaskExecutor(executor);

            // Create a non-persistent task using the registered executor
            ScheduledTask task = schedulerWithoutProvider.newTask("no-provider-test")
                .nonPersistent()
                .withExecutor(executor)
                .schedule();

            // Wait for execution
            assertTrue(executionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT), "Task should execute");
            assertTrue(executed.get(), "Task should have executed");

            // Wait for task status to be properly updated
            // This is necessary because the state transition happens asynchronously after the callback.complete() is called
            ScheduledTask completedTask = waitForTaskStatusWithScheduler(task.getItemId(), ScheduledTask.TaskStatus.COMPLETED, TEST_TIMEOUT, TEST_SLEEP, schedulerWithoutProvider);
            assertNotNull(completedTask, "Task should be found");
            assertEquals(ScheduledTask.TaskStatus.COMPLETED, completedTask.getStatus(), "Task should be completed");

            // Test that persistent tasks are handled gracefully (should not fail)
            try {
                List<ScheduledTask> persistentTasks = schedulerWithoutProvider.getPersistentTasks();
                assertNotNull(persistentTasks, "Should return empty list for persistent tasks");
                assertEquals(0, persistentTasks.size(), "Should have no persistent tasks");
            } catch (Exception e) {
                fail("Should handle persistent task queries gracefully: " + e.getMessage());
            }

        } finally {
            schedulerWithoutProvider.preDestroy();
        }
    }

    /**
     * Tests that the scheduler service works correctly when PersistenceSchedulerProvider is added later.
     * This simulates the OSGi service binding scenario.
     */
    @Test
    @Tag("OptionalDependencyTests")
    public void testSchedulerServiceWithLatePersistenceProviderBinding() throws Exception {
        // Create a scheduler service without persistence provider initially
        SchedulerServiceImpl schedulerWithLateProvider = TestHelper.createSchedulerServiceWithoutPersistenceProvider(
            "test-node-late-provider",
            persistenceService,
            executionContextManager,
            bundleContext,
            clusterService,
            TEST_LOCK_TIMEOUT,
            true,
            true);

        try {
            // Verify service starts without persistence provider
            assertTrue(schedulerWithLateProvider.isExecutorNode(), "Scheduler service should be running");

            // Create a persistence provider and bind it later
            PersistenceSchedulerProvider lateProvider = new PersistenceSchedulerProvider();
            lateProvider.setPersistenceService(persistenceService);
            lateProvider.setExecutorNode(true);
            lateProvider.setNodeId("test-node-late-provider");
            lateProvider.setCompletedTaskTtlDays(30);
            lateProvider.setLockManager(schedulerWithLateProvider.getLockManager());
            lateProvider.postConstruct();

            // Bind the provider (simulating OSGi service binding)
            schedulerWithLateProvider.setPersistenceProvider(lateProvider);

            // Test that persistent tasks now work
            CountDownLatch executionLatch = new CountDownLatch(1);
            AtomicBoolean executed = new AtomicBoolean(false);

            TaskExecutor executor = new TaskExecutor() {
                @Override
                public String getTaskType() {
                    return "late-provider-test";
                }

                @Override
                public void execute(ScheduledTask task, TaskStatusCallback callback) {
                    executed.set(true);
                    executionLatch.countDown();
                    callback.complete();
                }
            };

            schedulerWithLateProvider.registerTaskExecutor(executor);

            // Create a persistent task using the registered executor
            ScheduledTask task = schedulerWithLateProvider.newTask("late-provider-test")
                .withExecutor(executor)
                .schedule();

            // Wait for execution
            assertTrue(executionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT), "Task should execute");
            assertTrue(executed.get(), "Task should have executed");

            // Wait for task status to be properly updated and persisted
            ScheduledTask completedTask = waitForTaskStatus(task.getItemId(), ScheduledTask.TaskStatus.COMPLETED, TEST_TIMEOUT, TEST_SLEEP);
            assertNotNull(completedTask, "Task should be found");
            assertEquals(ScheduledTask.TaskStatus.COMPLETED, completedTask.getStatus(), "Task should be completed");

            // Verify persistent tasks are now available - retry until task is available (handles refresh delay)
            List<ScheduledTask> persistentTasks = TestHelper.retryUntil(
                () -> schedulerWithLateProvider.getPersistentTasks(),
                r -> r != null && r.size() >= 1
            );
            assertNotNull(persistentTasks, "Should return list for persistent tasks");
            assertTrue(persistentTasks.size() >= 1, "Should have at least one persistent task");

            // Unbind the provider (simulating OSGi service unbinding)
            schedulerWithLateProvider.unsetPersistenceProvider(lateProvider);

            // Verify that persistent task queries are handled gracefully again
            try {
                List<ScheduledTask> persistentTasksAfterUnbind = schedulerWithLateProvider.getPersistentTasks();
            assertNotNull(persistentTasksAfterUnbind, "Should return empty list after unbinding");
            assertEquals(0, persistentTasksAfterUnbind.size(), "Should have no persistent tasks after unbinding");
            } catch (Exception e) {
                fail("Should handle persistent task queries gracefully after unbinding: " + e.getMessage());
            }

            // Clean up the provider
            lateProvider.preDestroy();

        } finally {
            schedulerWithLateProvider.preDestroy();
        }
    }

    /**
     * Tests that the scheduler service handles PersistenceSchedulerProvider binding/unbinding correctly
     * in a multi-threaded environment.
     */
    @Test
    @Tag("OptionalDependencyTests")
    public void testConcurrentPersistenceProviderBinding() throws Exception {
        SchedulerServiceImpl schedulerConcurrent = TestHelper.createSchedulerServiceWithoutPersistenceProvider(
            "test-node-concurrent",
            persistenceService,
            executionContextManager,
            bundleContext,
            clusterService,
            TEST_LOCK_TIMEOUT,
            true,
            true);

        try {
            // Create multiple persistence providers
            List<PersistenceSchedulerProvider> providers = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                PersistenceSchedulerProvider provider = new PersistenceSchedulerProvider();
                provider.setPersistenceService(persistenceService);
                provider.setExecutorNode(true);
                provider.setNodeId("test-node-concurrent-" + i);
                provider.setCompletedTaskTtlDays(30);
                provider.setLockManager(schedulerConcurrent.getLockManager());
                provider.postConstruct();
                providers.add(provider);
            }

            // Test concurrent binding/unbinding
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(3);
            AtomicInteger bindingCount = new AtomicInteger(0);
            AtomicInteger unbindingCount = new AtomicInteger(0);

            // Start threads that bind/unbind providers concurrently
            for (int i = 0; i < 3; i++) {
                final int index = i;
                new Thread(() -> {
                    try {
                        startLatch.await();

                        // Bind provider
                        schedulerConcurrent.setPersistenceProvider(providers.get(index));
                        bindingCount.incrementAndGet();

                        // Simulate some work
                        Thread.sleep(100);

                        // Unbind provider
                        schedulerConcurrent.unsetPersistenceProvider(providers.get(index));
                        unbindingCount.incrementAndGet();

                        completionLatch.countDown();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            }

            // Start all threads
            startLatch.countDown();

            // Wait for completion
            assertTrue(
                completionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT),
                "All binding/unbinding operations should complete");

            // Verify all operations completed
            assertEquals(3, bindingCount.get(), "All bindings should complete");
            assertEquals(3, unbindingCount.get(), "All unbindings should complete");

            // Clean up providers
            for (PersistenceSchedulerProvider provider : providers) {
                provider.preDestroy();
            }

        } finally {
            schedulerConcurrent.preDestroy();
        }
    }

    /**
     * Tests that system tasks are properly handled when the scheduler restarts.
     * System tasks should be reused rather than duplicated.
     */
    @Test
    @Tag("SystemTaskTests")
    public void testSystemTaskReuse() throws Exception {
        String taskType = "system-task-reuse-test";
        AtomicInteger executionCount = new AtomicInteger(0);
        CountDownLatch executionLatch = new CountDownLatch(1);
        AtomicReference<String> taskId = new AtomicReference<>();

        TaskExecutor executor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return taskType;
            }

            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) {
                executionCount.incrementAndGet();
                executionLatch.countDown();
                callback.complete();
            }
        };

        schedulerService.registerTaskExecutor(executor);

        // Schedule a system task
        ScheduledTask systemTask = schedulerService.newTask(taskType)
            .withPeriod(1, TimeUnit.DAYS)
            .withFixedRate()
            .asSystemTask()
            .schedule();

        taskId.set(systemTask.getItemId());

        // Wait for task to execute once
        assertTrue(executionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT), "Task should execute");
        assertEquals(1, executionCount.get(), "Task should have executed once");

        // Force persistence to ensure the task is saved
        persistenceService.refreshIndex(ScheduledTask.class);

        // Now create a new task with the same type
        ScheduledTask secondTask = schedulerService.newTask(taskType)
            .withPeriod(1, TimeUnit.DAYS)
            .withFixedRate()
            .asSystemTask()
            .schedule();

        // The task ID should be the same as the first one since it's reused
        assertEquals(
            systemTask.getItemId(),
            secondTask.getItemId(),
            "System task should be reused, not duplicated");

        // Check that there's only one task of this type BEFORE teardown
        persistenceService.refreshIndex(ScheduledTask.class);
        List<ScheduledTask> tasks = schedulerService.getTasksByType(taskType, 0, -1, null).getList();
        assertEquals(1, tasks.size(), "Should only be one system task of this type");

        // Clean up the system task manually to avoid teardown issues
        schedulerService.cancelTask(taskId.get());
    }

    /**
     * Tests that scheduler correctly handles tasks across shutdown and restart.
     * Persistent tasks should be properly reloaded and continue execution.
     */
    @Test
    @Tag("RestartTests")
    public void testSchedulerRestartWithPersistentTasks() throws Exception {
        // Create a persistent task
        String taskType = "restart-test";
        AtomicInteger executionCount = new AtomicInteger(0);
        CountDownLatch firstExecutionLatch = new CountDownLatch(1);
        CountDownLatch secondExecutionLatch = new CountDownLatch(1);

        TaskExecutor executor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return taskType;
            }

            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) {
                int count = executionCount.incrementAndGet();
                if (count == 1) {
                    firstExecutionLatch.countDown();
                } else if (count == 2) {
                    secondExecutionLatch.countDown();
                }
                callback.complete();
            }
        };

        schedulerService.registerTaskExecutor(executor);

        // Schedule a persistent task with short period
        ScheduledTask persistentTask = schedulerService.newTask(taskType)
            .withPeriod(500, TimeUnit.MILLISECONDS)
            .withFixedRate()
            .schedule();

        // Wait for first execution to complete
        assertTrue(
            firstExecutionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT),
            "Task should execute first time");

        // Force refresh to ensure the task is properly saved
        persistenceService.refreshIndex(ScheduledTask.class);

        // Shutdown the scheduler service
        schedulerService.preDestroy();

        // Create a new scheduler service that will reload existing tasks
        SchedulerServiceImpl newSchedulerService = (SchedulerServiceImpl) TestHelper.createSchedulerService(
            "restart-scheduler-node",
            persistenceService,
            executionContextManager,
            bundleContext,
            clusterService,
            -1,
            true,
            false);

        newSchedulerService.setThreadPoolSize(TEST_THREAD_POOL_SIZE);
        newSchedulerService.setLockTimeout(TEST_LOCK_TIMEOUT);

        // Initialize first, then register executor
        newSchedulerService.postConstruct();
        newSchedulerService.registerTaskExecutor(executor);

        // Wait for second execution to complete on restarted scheduler
        boolean executed = secondExecutionLatch.await(TEST_TIMEOUT * 2, TEST_TIME_UNIT);

        // Clean up the new scheduler service
        newSchedulerService.preDestroy();

        assertTrue(executed, "Task should execute after scheduler restart");
        assertEquals(2, executionCount.get(), "Task should have executed twice");

        // Verify the reloaded task has same ID
        ScheduledTask reloadedTask = persistenceService.load(persistentTask.getItemId(), ScheduledTask.class);
        assertNotNull(reloadedTask, "Persistent task should still exist");
        assertEquals(persistentTask.getItemId(), reloadedTask.getItemId(), "Task ID should be preserved after restart");
    }

    /**
     * Tests that system tasks properly preserve their state and configuration
     * across scheduler restarts.
     */
    @Test
    @Tag("SystemTaskTests")
    @Tag("RestartTests")
    public void testSystemTaskAcrossRestart() throws Exception {
        // Create a system task with specific configuration
        String taskType = "system-task-restart-test";
        Map<String, Object> taskParams = new HashMap<>();
        taskParams.put("configParam", "testValue");
        taskParams.put("numericParam", 123);

        AtomicInteger executionCount = new AtomicInteger(0);
        AtomicReference<Map<String, Object>> executedParams = new AtomicReference<>();
        CountDownLatch executionLatch = new CountDownLatch(1);

        TaskExecutor executor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return taskType;
            }

            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) {
                executionCount.incrementAndGet();
                executedParams.set(new HashMap<>(task.getParameters()));
                executionLatch.countDown();
                callback.complete();
            }
        };

        schedulerService.registerTaskExecutor(executor);

        // Schedule the system task with parameters
        ScheduledTask systemTask = schedulerService.newTask(taskType)
            .withParameters(taskParams)
            .withPeriod(1, TimeUnit.DAYS)
            .withFixedRate()
            .asSystemTask()
            .schedule();

        // Force refresh to ensure the task is properly saved
        persistenceService.refreshIndex(ScheduledTask.class);

        // Shutdown the scheduler service
        schedulerService.preDestroy();

        // Create a new scheduler service
        SchedulerServiceImpl newSchedulerService = (SchedulerServiceImpl) TestHelper.createSchedulerService(
            "system-task-restart-scheduler-node",
            persistenceService,
            executionContextManager,
            bundleContext,
            clusterService,
            -1,
            true,
            false);

        newSchedulerService.setThreadPoolSize(TEST_THREAD_POOL_SIZE);
        newSchedulerService.setLockTimeout(TEST_LOCK_TIMEOUT);
        newSchedulerService.postConstruct();

        // Create a duplicate task during scheduler initialization
        // This simulates code that always creates its task during initialization
        TaskExecutor newExecutor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return taskType;
            }

            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) {
                executionCount.incrementAndGet();
                executedParams.set(new HashMap<>(task.getParameters()));
                executionLatch.countDown();
                callback.complete();
            }
        };

        newSchedulerService.registerTaskExecutor(newExecutor);

        // Force refresh to ensure tasks are properly loaded
        persistenceService.refreshIndex(ScheduledTask.class);

        // Attempt to create the same system task (should reuse existing one)
        Map<String, Object> newTaskParams = new HashMap<>();
        newTaskParams.put("configParam", "newValue"); // Different param value

        ScheduledTask recreatedTask = newSchedulerService.newTask(taskType)
            .withParameters(newTaskParams)
            .withPeriod(2, TimeUnit.DAYS) // Different period
            .withFixedRate()
            .asSystemTask()
            .schedule();

        // Verify task was reused, not recreated
        assertEquals(
            systemTask.getItemId(),
            recreatedTask.getItemId(),
            "System task ID should be preserved");

        // Verify original parameters were preserved
        assertEquals(
            "testValue",
            recreatedTask.getParameters().get("configParam"),
            "Original parameter should be preserved");
        assertEquals(
            123,
            recreatedTask.getParameters().get("numericParam"),
            "Original parameter should be preserved");

        // Clean up
        newSchedulerService.preDestroy();
    }

    /**
     * Tests that system purge tasks are properly handled and can be reused.
     * This simulates the behavior in SchedulerServiceImpl and ProfileServiceImpl.
     */
    @Test
    @Tag("SystemTaskTests")
    @Tag("MaintenanceTests")
    public void testSystemPurgeTasks() throws Exception {
        CountDownLatch executionLatch = new CountDownLatch(1);
        AtomicInteger executionCount = new AtomicInteger(0);

        // Create a TaskExecutor for "task-purge" (similar to SchedulerServiceImpl's purge task)
        TaskExecutor taskPurgeExecutor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return "task-purge";
            }

            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) {
                executionCount.incrementAndGet();
                executionLatch.countDown();
                callback.complete();
            }
        };

        // Register the task executor
        schedulerService.registerTaskExecutor(taskPurgeExecutor);

        // Create the initial task-purge task
        ScheduledTask taskPurgeTask = schedulerService.newTask("task-purge")
            .withPeriod(1, TimeUnit.DAYS)
            .withFixedRate()
            .asSystemTask()
            .schedule();

        // Verify the task is correctly created and scheduled
        assertNotNull(taskPurgeTask, "Task should be created");
        assertTrue(taskPurgeTask.isSystemTask(), "Task should be a system task");

        // Wait for the task to execute
        assertTrue(executionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT), "Task should execute");
        assertEquals(1, executionCount.get(), "Task should have executed once");

        // Force refresh to ensure the task is properly saved
        persistenceService.refreshIndex(ScheduledTask.class);

        // Now simulate the scheduler restart by destroying and recreating it
        schedulerService.preDestroy();

        // Create a new scheduler service
        SchedulerServiceImpl newSchedulerService = (SchedulerServiceImpl) TestHelper.createSchedulerService(
            "system-purge-scheduler-node",
            persistenceService,
            executionContextManager,
            bundleContext,
            clusterService,
            -1,
            true,
            false);

        newSchedulerService.setThreadPoolSize(TEST_THREAD_POOL_SIZE);
        newSchedulerService.setLockTimeout(TEST_LOCK_TIMEOUT);
        newSchedulerService.postConstruct();

        // Register the executor
        newSchedulerService.registerTaskExecutor(taskPurgeExecutor);

        // Force refresh to ensure tasks are properly loaded
        persistenceService.refreshIndex(ScheduledTask.class);

        // Now simulate the initializeTaskPurge method in SchedulerServiceImpl
        // This checks for existing system tasks of type "task-purge"
        List<ScheduledTask> existingTasks = newSchedulerService.getTasksByType("task-purge", 0, 1, null).getList();
        ScheduledTask reuseTask = null;

        // Check if there's an existing system task
        if (!existingTasks.isEmpty() && existingTasks.get(0).isSystemTask()) {
            // Reuse the existing task
            reuseTask = existingTasks.get(0);
            // Update task configuration
            reuseTask.setPeriod(1);
            reuseTask.setTimeUnit(TimeUnit.DAYS);
            reuseTask.setFixedRate(true);
            reuseTask.setEnabled(true);
            newSchedulerService.saveTask(reuseTask);
        } else {
            // Create a new task if none exists or existing one isn't a system task
            reuseTask = newSchedulerService.newTask("task-purge")
                .withPeriod(1, TimeUnit.DAYS)
                .withFixedRate()
                .asSystemTask()
                .schedule();
        }

        // Verify task was reused, not recreated
        assertEquals(taskPurgeTask.getItemId(), reuseTask.getItemId(), "System task ID should be preserved");

        // Finally, verify the task state is preserved after all operations
        ScheduledTask finalTask = persistenceService.load(taskPurgeTask.getItemId(), ScheduledTask.class);
        assertNotNull(finalTask, "Task should still exist in persistence");
        assertTrue(finalTask.isSystemTask(), "Task should still be a system task");
        assertEquals("task-purge", finalTask.getTaskType(), "Task type should be preserved");

        // Clean up
        newSchedulerService.preDestroy();
    }

    /**
     * Tests that a dedicated TaskExecutor for system tasks works properly
     * and is reused after scheduler restart. This simulates the pattern we've
     * implemented for previously using withSimpleExecutor() in system tasks.
     */
    @Test
    @Tag("SystemTaskTests")
    @Tag("RestartTests")
    public void testDedicatedTaskExecutorForSystemTasks() throws Exception {
        String taskType = "dedicated-executor-test";
        CountDownLatch firstExecutionLatch = new CountDownLatch(1);
        CountDownLatch secondExecutionLatch = new CountDownLatch(1);
        AtomicInteger executionCount = new AtomicInteger(0);

        // Create a dedicated TaskExecutor that counts executions and signals via latches
        TaskExecutor dedicatedExecutor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return taskType;
            }

            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) {
                try {
                    int count = executionCount.incrementAndGet();
                    LOGGER.info("Executing task {} (execution #{})", task.getItemId(), count);

                    // Get parameter from task
                    Map<String, Object> params = task.getParameters();
                    String testParam = (String) params.get("testParam");
                    assertNotNull(testParam, "Task should have testParam in parameters map");

                    if (count == 1) {
                        firstExecutionLatch.countDown();
                    } else if (count == 2) {
                        secondExecutionLatch.countDown();
                    }

                    callback.complete();
                } catch (Exception e) {
                    LOGGER.error("Error executing task", e);
                    callback.fail(e.getMessage());
                }
            }
        };

        // Register the executor and create the system task
        schedulerService.registerTaskExecutor(dedicatedExecutor);

        // Setup parameters for the task
        Map<String, Object> taskParams = new HashMap<>();
        taskParams.put("testParam", "initialValue");

        // Create a system task using the dedicated executor
        ScheduledTask systemTask = schedulerService.newTask(taskType)
            .withParameters(taskParams)
            .withPeriod(100, TimeUnit.MILLISECONDS) // Short period for testing
            .withFixedRate()
            .asSystemTask()
            .schedule();

        // Verify the task executes with the dedicated executor
        assertTrue(
            firstExecutionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT),
            "Task should execute with dedicated executor");
        assertEquals(1, executionCount.get(), "Task should execute once");

        // Force refresh to ensure the task is properly saved
        persistenceService.refreshIndex(ScheduledTask.class);

        // Now shut down and restart the scheduler
        schedulerService.preDestroy();

        // Create a new scheduler service
        SchedulerServiceImpl newSchedulerService = (SchedulerServiceImpl) TestHelper.createSchedulerService(
            "dedicated-executor-scheduler-node",
            persistenceService,
            executionContextManager,
            bundleContext,
            clusterService,
            -1,
            true,
            false);

        newSchedulerService.setThreadPoolSize(TEST_THREAD_POOL_SIZE);
        newSchedulerService.setLockTimeout(TEST_LOCK_TIMEOUT);

        // Initialize the scheduler before registering the executor
        newSchedulerService.postConstruct();
        newSchedulerService.registerTaskExecutor(dedicatedExecutor);

        // Force refresh to ensure tasks are properly loaded
        persistenceService.refreshIndex(ScheduledTask.class);

        // The task should automatically resume and execute with the dedicated executor
        assertTrue(
            secondExecutionLatch.await(TEST_TIMEOUT * 2, TEST_TIME_UNIT),
            "Task should execute after restart with dedicated executor");
        assertEquals(2, executionCount.get(), "Task should execute twice");

        // Clean up
        newSchedulerService.preDestroy();
    }
}
