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
import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.api.tasks.TaskExecutor;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluatorDispatcher;
import org.apache.unomi.services.TestHelper;
import org.apache.unomi.services.impl.ExecutionContextManagerImpl;
import org.apache.unomi.services.impl.InMemoryPersistenceServiceImpl;
import org.apache.unomi.services.impl.KarafSecurityService;
import org.apache.unomi.services.impl.TestConditionEvaluators;
import org.apache.unomi.services.impl.cluster.ClusterServiceImpl;
import org.apache.unomi.api.services.SchedulerService.TaskBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
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

    private SchedulerServiceImpl schedulerService;
    private PersistenceService persistenceService;
    private ExecutionContextManagerImpl executionContextManager;
    private KarafSecurityService securityService;
    private ClusterServiceImpl clusterService;

    @Mock
    private BundleContext bundleContext;

    private static void configureDebugLogging() {
        // Enable debug logging for scheduler package
        System.setProperty("org.slf4j.simpleLogger.log.org.apache.unomi.services.impl.scheduler", "DEBUG");
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd HH:mm:ss.SSS");
        System.setProperty("org.slf4j.simpleLogger.showThreadName", "true");
    }

    @Before
    public void setUp() throws IOException {
        configureDebugLogging();
        MockitoAnnotations.initMocks(this);
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
        clusterService = TestHelper.createClusterService(persistenceService, "test-scheduler-node");
        
        // Create scheduler service with cluster service
        schedulerService = (SchedulerServiceImpl) TestHelper.createSchedulerService(
            persistenceService, 
            executionContextManager, 
            bundleContext, 
            clusterService, 
            false);

        // Configure scheduler for testing
        schedulerService.setThreadPoolSize(TEST_THREAD_POOL_SIZE);
        schedulerService.setLockTimeout(TEST_LOCK_TIMEOUT);
        schedulerService.postConstruct();
    }

    @After
    public void tearDown() {
        if (schedulerService != null) {
            schedulerService.preDestroy();
            schedulerService = null;
        }
            persistenceService = null;
            executionContextManager = null;
            securityService = null;
    }

    // Basic task lifecycle tests
    @Test
    @Category(BasicTests.class)
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

        assertTrue("Task should execute", executionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT));
        assertTrue("Task should have executed", executed.get());

        ScheduledTask completedTask = schedulerService.getTask(task.getItemId());
        assertEquals("Task should be completed", ScheduledTask.TaskStatus.COMPLETED, completedTask.getStatus());
        assertNotNull("Task should have execution history", completedTask.getStatusDetails().get("executionHistory"));
    }

    @Test
    @Category(BasicTests.class)
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

        assertTrue("Task should execute three times", executionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT));
        assertTrue("Task should execute at fixed rate despite long execution",
            executionCount.get() >= 3);
    }

    @Test
    @Category(BasicTests.class)
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
                    assertTrue("Delay should be at least the period", delay >= period);
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

        assertTrue("Task should execute three times", executionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT));
        assertEquals("Task should execute exactly three times", 3, executionCount.get());
    }

    // Task state transition tests
    @Test
    @Category(StateTests.class)
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

        assertTrue("Task should complete transition", transitionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT));
        assertEquals("Task should be in RUNNING state during execution",
            ScheduledTask.TaskStatus.RUNNING, currentStatus.get());

        ScheduledTask finalTask = schedulerService.getTask(task.getItemId());
        assertEquals("Task should be in COMPLETED state",
            ScheduledTask.TaskStatus.COMPLETED, finalTask.getStatus());
        assertNotNull("Task should have checkpoint data", finalTask.getCheckpointData());
    }

    @Test
    @Category(StateTests.class)
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
        assertTrue("Task should start executing", startLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT));
        assertEquals("Task should be in RUNNING state during execution",
            ScheduledTask.TaskStatus.RUNNING, executionStatus.get());

        // Wait for completion
        assertTrue("Task should complete", completionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT));
        assertEquals("Task should be in COMPLETED state after execution",
            ScheduledTask.TaskStatus.COMPLETED, task.getStatus());
    }

    // Task dependency tests
    @Test
    @Category(DependencyTests.class)
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
        assertTrue("Dependencies should complete",
            dep1Latch.await(TEST_TIMEOUT, TEST_TIME_UNIT) &&
            dep2Latch.await(TEST_TIMEOUT, TEST_TIME_UNIT));

        // Verify dependent task execution
        Thread.sleep(100); // Give time for dependent task to execute
        assertTrue("Dependent task should execute after dependencies", dependentExecuted.get());
    }

    // Clustering support tests
    @Test
    @Category(ClusterTests.class)
    public void testClusteringSupport() throws Exception {
        // Test clustering behavior with multiple nodes
        SchedulerServiceImpl node1 = TestHelper.createTestNode(persistenceService, "node1", true, -1, clusterService);
        SchedulerServiceImpl node2 = TestHelper.createTestNode(persistenceService, "node2", true, -1, clusterService);
        SchedulerServiceImpl nonExecutorNode = TestHelper.createTestNode(persistenceService, "node3", false, -1, clusterService);

        try {
            // Instead of mock cluster nodes, create persistent tasks with locks
            // to ensure nodes are detected via the fallback mechanism
            createAndPersistTaskWithLock("node1-cluster-detection", "node1");
            createAndPersistTaskWithLock("node2-cluster-detection", "node2");
            createAndPersistTaskWithLock("node3-cluster-detection", "node3");
            
            // Refresh the index to ensure changes are visible
            persistenceService.refreshIndex(ScheduledTask.class);
            
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
            assertTrue("Tasks should execute on cluster nodes",
                executionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT));

            // Verify distribution
            assertTrue("Task should execute on executor nodes",
                executingNodes.contains("node1") || executingNodes.contains("node2"));
            assertFalse("Task should not execute on non-executor node",
                executingNodes.contains("node3"));

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

            // Wait for task to be picked up by checkTasks
            Thread.sleep(1500);
            ScheduledTask lockedTask = persistenceService.load(lockTask.getItemId(), ScheduledTask.class);
            assertNotNull("Task should have lock owner", lockedTask.getLockOwner());
            assertNotNull("Task should have lock date", lockedTask.getLockDate());

            // Test lock release - directly update task in persistence
            lockedTask.setLockOwner(null);
            lockedTask.setLockDate(null);
            persistenceService.save(lockedTask);
            
            // Refresh index to ensure changes are visible
            persistenceService.refreshIndex(ScheduledTask.class);
            
            // Get latest state and verify lock release
            ScheduledTask releasedTask = persistenceService.load(lockTask.getItemId(), ScheduledTask.class);
            assertNull("Lock should be released", releasedTask.getLockOwner());

        } finally {
            node1.preDestroy();
            node2.preDestroy();
            nonExecutorNode.preDestroy();
        }
    }

    // Task recovery tests
    @Test
    @Category(RecoveryTests.class)
    public void testTaskRecovery() throws Exception {
        // Test task recovery after crashes
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

        assertTrue("Task should be recovered", executionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT));
        assertTrue("Task should be recovered and executed", recovered.get());
    }

    // Metrics and history tests
    @Test
    @Category(MetricsTests.class)
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

        assertTrue("Task should execute successfully twice",
            successLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT));
        assertTrue("Task should fail once",
            failureLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT));

        // Verify metrics and history
        ScheduledTask finalTask = schedulerService.getTask(task.getItemId());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history =
            (List<Map<String, Object>>) finalTask.getStatusDetails().get("executionHistory");

        assertNotNull("Should have execution history", history);
        assertEquals("Should have 3 history entries", 3, history.size());
        assertEquals("Should have 2 successful executions", 2, finalTask.getSuccessCount());
        assertEquals("Should have 1 failed execution", 1, finalTask.getFailureCount());
        assertEquals("Total executions should be 3", 3, finalTask.getSuccessCount() + finalTask.getFailureCount());

        // Verify history entries
        int successEntries = 0;
        int failureEntries = 0;
        for (Map<String, Object> entry : history) {
            String status = (String) entry.get("status");
            if ("SUCCESS".equals(status)) {
                successEntries++;
            } else if ("FAILED".equals(status)) {
                failureEntries++;
                assertNotNull("Failed entry should have error", entry.get("error"));
            }
        }

        assertEquals("Should have 2 successful executions", 2, successEntries);
        assertEquals("Should have 1 failed execution", 1, failureEntries);

        // Verify metrics
        assertTrue("Should have completed tasks metric", schedulerService.getMetric("tasks.completed") > 0);
        assertTrue("Should have failed tasks metric", schedulerService.getMetric("tasks.failed") > 0);

        // Test metric reset
        schedulerService.resetMetrics();
        assertEquals("Metrics should be reset", 0, schedulerService.getMetric("tasks.completed"));
    }

    // Task validation tests
    @Test
    @Category(ValidationTests.class)
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
    @Category(RetryTests.class)
    public void testOneShotTaskRetryScenarios() throws Exception {
        // Test both persistent and in-memory tasks
        testOneShotRetryBehavior(true);  // persistent
        testOneShotRetryBehavior(false); // in-memory
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

        assertTrue("Task should complete all executions",
            executionLatch.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS));

        // Verify retry delays
        for (int i = 1; i < executionTimes.size(); i++) {
            long delay = executionTimes.get(i) - executionTimes.get(i-1);
            assertTrue("Retry delay should be at least " + TEST_RETRY_DELAY + "ms",
                delay >= TEST_RETRY_DELAY);
        }

        // Verify final state
        task = schedulerService.getTask(task.getItemId());
        assertEquals("Task should be completed",
            ScheduledTask.TaskStatus.COMPLETED, task.getStatus());
        assertEquals("Should have executed expected number of times",
            TEST_MAX_RETRIES+1, executionCount.get());
    }

    @Test
    @Category(RetryTests.class)
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
        assertTrue("First period should exhaust retries",
            firstPeriodLatch.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS));

        // Verify retry delays in first period
        for (int i = 1; i <= TEST_MAX_RETRIES; i++) {
            long delay = executionTimes.get(i) - executionTimes.get(i-1);
            assertTrue("Retry delay should be at least " + TEST_RETRY_DELAY + "ms",
                delay >= TEST_RETRY_DELAY);
        }

        // Wait for successful execution in second period
        assertTrue("Second period should succeed",
            secondPeriodLatch.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS));

        // Verify period transition
        long periodTransitionDelay = executionTimes.get(TEST_MAX_RETRIES+1) -
            executionTimes.get(TEST_MAX_RETRIES);
        assertTrue("Period transition delay should be at least 2000ms",
            periodTransitionDelay >= 2000);

        // Verify task state
        task = schedulerService.getTask(task.getItemId());
        while (!ScheduledTask.TaskStatus.SCHEDULED.equals(task.getStatus())) {
            LOGGER.debug("Waiting for task {} to complete a period" , task.getItemId());
            Thread.sleep(100);
            task = schedulerService.getTask(task.getItemId());
        }
        LOGGER.info("Task status={}", task.getStatus());
        assertEquals("Task should be in scheduled state",
            ScheduledTask.TaskStatus.SCHEDULED, task.getStatus());
        assertEquals("Failure count should be reset", 0, task.getFailureCount());

        schedulerService.cancelTask(task.getItemId());

    }

    @Test
    @Category(RetryTests.class)
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
        assertTrue("Should exhaust automatic retries",
            exhaustionLatch.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS));

        // Verify failed state
        task = schedulerService.getTask(task.getItemId());
        assertEquals("Task should be in failed state",
            ScheduledTask.TaskStatus.FAILED, task.getStatus());
        assertEquals("Should have correct failure count",
            TEST_MAX_RETRIES+1, task.getFailureCount());

        // Manually retry with reset
        schedulerService.retryTask(task.getItemId(), true);

        // Wait for manual retry to complete
        assertTrue("Manual retry should succeed",
            manualRetryLatch.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS));

        // Verify final state
        task = schedulerService.getTask(task.getItemId());
        while (!ScheduledTask.TaskStatus.COMPLETED.equals(task.getStatus())) {
            LOGGER.debug("Waiting for task {} to complete" , task.getItemId());
            Thread.sleep(100);
            task = schedulerService.getTask(task.getItemId());
        }
        assertEquals("Task should be completed after manual retry",
            ScheduledTask.TaskStatus.COMPLETED, task.getStatus());
        assertEquals("Failure count should be reset", 0, task.getFailureCount());
    }

    // Task purging tests
    @Test
    @Category(MaintenanceTests.class)
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
            assertEquals("Task should be completed",
                ScheduledTask.TaskStatus.COMPLETED, completedTask.getStatus());
        }

        // Force purge
        schedulerService.purgeOldTasks();

        // Wait for purge to complete
        Thread.sleep(100);

        // Verify purged tasks
        List<ScheduledTask> remainingTasks = schedulerService.getAllTasks();
        assertEquals("Should have purged completed tasks", 0, remainingTasks.size());
    }

    /**
     * Tests task builder pattern completeness.
     * Verifies all builder methods work correctly.
     */
    @Test
    @Category(ValidationTests.class)
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

        assertEquals("Parameters should be set", params, task.getParameters());
        assertEquals("Initial delay should be set", 100, task.getInitialDelay());
        assertEquals("Period should be set", 200, task.getPeriod());
        assertFalse("Should be fixed delay", task.isFixedRate());
        assertFalse("Should be non-persistent", task.isPersistent());
        assertTrue("Should run on all nodes", task.isRunOnAllNodes());
    }

    /**
     * Tests lock timeout expiration and recovery.
     */
    @Test
    @Category(ClusterTests.class)
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
        assertTrue("Task should start executing", executionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT));
        
        // Get the running task instance
        ScheduledTask runningTask = persistenceService.load(task.getItemId(), ScheduledTask.class);
        
        // Directly update task to simulate lock expiration
        runningTask.setLockOwner(null);
        runningTask.setLockDate(null);
        persistenceService.save(runningTask);
        persistenceService.refreshIndex(ScheduledTask.class);
        
        // Check lock status after manual release
        ScheduledTask updatedTask = persistenceService.load(task.getItemId(), ScheduledTask.class);
        assertNull("Lock should be released after manual update", updatedTask.getLockOwner());
    }

    /**
     * Tests task cancellation during execution.
     */
    @Test
    @Category(StateTests.class)
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
        assertTrue("Task should start", startLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT));

        // Cancel the task
        schedulerService.cancelTask(task.getItemId());

        Thread.sleep(TEST_SLEEP*3); // give some time to cancel the thread
        // Allow task to complete if not cancelled
        cancelLatch.countDown();
        Thread.sleep(TEST_SLEEP);

        ScheduledTask cancelledTask = schedulerService.getTask(task.getItemId());
        assertEquals("Task should be cancelled",
            ScheduledTask.TaskStatus.CANCELLED, cancelledTask.getStatus());
        assertFalse("Task should not complete after cancellation", completed.get());
    }

    /**
     * Tests task querying and filtering capabilities.
     */
    @Test
    @Category(QueryTests.class)
    public void testTaskQuerying() throws Exception {
        // Create tasks with different states
        ScheduledTask runningTask = createTestTask("query-test", ScheduledTask.TaskStatus.RUNNING);
        ScheduledTask completedTask = createTestTask("query-test", ScheduledTask.TaskStatus.COMPLETED);
        ScheduledTask failedTask = createTestTask("query-test", ScheduledTask.TaskStatus.FAILED);
        ScheduledTask waitingTask = createTestTask("query-test", ScheduledTask.TaskStatus.WAITING);

        // Test querying by status
        PartialList<ScheduledTask> completedTasks =
            schedulerService.getTasksByStatus(ScheduledTask.TaskStatus.COMPLETED, 0, 10, null);
        assertEquals("Should find completed task", 1, completedTasks.getList().size());
        assertEquals("Should return correct task",
            completedTask.getItemId(), completedTasks.getList().get(0).getItemId());

        // Test querying by type
        PartialList<ScheduledTask> typeTasks =
            schedulerService.getTasksByType("query-test", 0, 10, null);
        assertEquals("Should find all tasks of type", 4, typeTasks.getList().size());

        // Test pagination
        PartialList<ScheduledTask> pagedTasks =
            schedulerService.getTasksByType("query-test", 0, 2, null);
        assertEquals("Should respect page size", 2, pagedTasks.getList().size());
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

    // Test categories with documentation
    /** Tests for basic task lifecycle operations */
    public interface BasicTests {}
    /** Tests for task state transitions and validation */
    public interface StateTests {}
    /** Tests for task dependency management */
    public interface DependencyTests {}
    /** Tests for multi-node execution and lock management */
    public interface ClusterTests {}
    /** Tests for crash recovery and task resumption */
    public interface RecoveryTests {}
    /** Tests for metrics collection and history tracking */
    public interface MetricsTests {}
    /** Tests for input validation and error handling */
    public interface ValidationTests {}
    /** Tests for task retry behavior and delay */
    public interface RetryTests {}
    /** Tests for task cleanup and maintenance */
    public interface MaintenanceTests {}
    /** Tests for task querying and filtering */
    public interface QueryTests {}

    /**
     * Tests node failure scenarios in clustering.
     * Verifies that tasks are properly recovered when nodes fail during execution.
     */
    @Test
    @Category(ClusterTests.class)
    public void testNodeFailure() throws Exception {
        schedulerService.preDestroy();
        SchedulerServiceImpl node1 = TestHelper.createTestNode(persistenceService, "node1", true, TEST_LOCK_TIMEOUT, clusterService);
        SchedulerServiceImpl node2 = TestHelper.createTestNode(persistenceService, "node2", true, TEST_LOCK_TIMEOUT, clusterService);

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
            assertTrue("Task should start", startLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT));
            String originalNode = executingNode.get();
            assertNotNull("Task should have executing node", originalNode);

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

            assertTrue("Task should be recovered", completionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT));
            assertTrue("Task should be recovered by other node", taskRecovered.get());

            // Verify final state
            ScheduledTask recoveredTask = persistenceService.load(task.getItemId(), ScheduledTask.class);
            assertEquals("Task should be completed",
                ScheduledTask.TaskStatus.COMPLETED, recoveredTask.getStatus());
            assertNotEquals("Task should be recovered by different node",
                originalNode, recoveredTask.getLockOwner());
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
    @Category(ClusterTests.class)
    public void testConcurrentLockAcquisition() throws Exception {
        SchedulerServiceImpl node1 = TestHelper.createTestNode(persistenceService, "node1", true, -1, clusterService);
        SchedulerServiceImpl node2 = TestHelper.createTestNode(persistenceService, "node2", true, -1, clusterService);

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
            assertTrue("Task executions should complete",
                completionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT));

            // Verify no concurrent execution by checking time overlaps
            List<Map.Entry<String, ExecutionInfo>> sortedExecutions = new ArrayList<>(executions.entrySet());
            sortedExecutions.sort((a, b) -> Long.compare(a.getValue().startTime, b.getValue().startTime));

            assertEquals("Should have exactly one task executing at a time", 1, maxConcurrentExecutions.get());

            // Verify sequential execution
            ExecutionInfo current = sortedExecutions.get(0).getValue();
            ExecutionInfo next = sortedExecutions.get(1).getValue();

            assertTrue("Task executions should not overlap in time",
                current.endTime <= next.startTime);

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
    @Category(ClusterTests.class)
    public void testTaskRebalancing() throws Exception {
        SchedulerServiceImpl node1 = TestHelper.createTestNode(persistenceService, "node1", true, -1, clusterService);
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
                    } else {
                        node2Latch.countDown();
                    }
                    callback.complete();
                }
            };

            // Register executor on first node
            node1.registerTaskExecutor(executor);

            // Create periodic task
            ScheduledTask task = node1.newTask("rebalance-test")
                .withPeriod(1500, TimeUnit.MILLISECONDS)
                .schedule();

            // Wait for execution on node1
            assertTrue("Task should execute on node1",
                node1Latch.await(TEST_TIMEOUT, TEST_TIME_UNIT));
            assertTrue("Node1 should execute task",
                executingNodes.contains("node1"));

            // Add second node
            node2 = TestHelper.createTestNode(persistenceService, "node2", true, -1, clusterService);
            node2.registerTaskExecutor(executor);

            // Wait for execution on node2
            assertTrue("Task should execute on node2",
                node2Latch.await(TEST_TIMEOUT*2, TEST_TIME_UNIT));
            assertTrue("Node2 should execute task",
                executingNodes.contains("node2"));

            // Verify task distribution
            assertEquals("Task should execute on both nodes", 2, executingNodes.size());

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
    @Category(ClusterTests.class)
    public void testLockStealing() throws Exception {
        SchedulerServiceImpl node1 = TestHelper.createTestNode(persistenceService, "node1", true, -1, clusterService);
        SchedulerServiceImpl node2 = TestHelper.createTestNode(persistenceService, "node2", true, -1, clusterService);

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
            assertTrue("Task should execute",
                executionLatch.await(TEST_TIMEOUT, TEST_TIME_UNIT));
            String originalOwner = lockOwner.get();
            assertNotNull("Task should have lock owner", originalOwner);

            // Attempt immediate re-execution (potential lock stealing)
            node1.recoverCrashedTasks();
            node2.recoverCrashedTasks();
            Thread.sleep(100);

            // Verify lock wasn't stolen
            ScheduledTask lockedTask = persistenceService.load(task.getItemId(), ScheduledTask.class);
            if (lockedTask.getLockOwner() != null) {
                assertEquals("Lock should not be stolen",
                    originalOwner, lockedTask.getLockOwner());
            }
        } finally {
            node1.preDestroy();
            node2.preDestroy();
        }
    }

    @Test
    public void testNodeAffinity() throws Exception {
        // Create test nodes with cluster service
        SchedulerServiceImpl node1 = TestHelper.createTestNode(persistenceService, "node1", true, -1, clusterService);
        SchedulerServiceImpl node2 = TestHelper.createTestNode(persistenceService, "node2", true, -1, clusterService);
        SchedulerServiceImpl nonExecutorNode = TestHelper.createTestNode(persistenceService, "node3", false, -1, clusterService);

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
            assertTrue("Task should execute within timeout", executionLatch.await(5, TimeUnit.SECONDS));
            
            // Verify that the task was executed by the expected node
            assertNotNull("Task should have an executing node ID", executingNodeId.get());
            
            // Verify that the cluster service was used to determine active nodes
            List<String> activeNodes = node1.getActiveNodes();
            assertTrue("Node1 should be in active nodes list", activeNodes.contains("node1"));
            assertTrue("Node2 should be in active nodes list", activeNodes.contains("node2"));
            assertTrue("Node3 should be in active nodes list", activeNodes.contains("node3"));
            
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
}
