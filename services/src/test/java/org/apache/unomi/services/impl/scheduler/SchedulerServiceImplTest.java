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

import org.apache.commons.io.FileUtils;
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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SchedulerServiceImplTest {

    private SchedulerServiceImpl schedulerService;
    private PersistenceService persistenceService;
    private ExecutionContextManagerImpl executionContextManager;
    private KarafSecurityService securityService;

    @Mock
    private BundleContext bundleContext;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        CustomObjectMapper.getCustomInstance().registerBuiltInItemTypeClass(ScheduledTask.ITEM_TYPE, ScheduledTask.class);

        // Setup security and context
        securityService = TestHelper.createSecurityService();
        executionContextManager = TestHelper.createExecutionContextManager(securityService);

        // Setup condition evaluator
        ConditionEvaluatorDispatcher conditionEvaluatorDispatcher = TestConditionEvaluators.createDispatcher();

        // Mock bundle context
        Bundle bundle = mock(Bundle.class);
        when(bundleContext.getBundle()).thenReturn(bundle);
        when(bundle.getBundleContext()).thenReturn(bundleContext);
        when(bundleContext.getBundle().findEntries(anyString(), anyString(), anyBoolean())).thenReturn(null);
        when(bundleContext.getBundles()).thenReturn(new Bundle[0]);

        Path defaultStorageDir = Paths.get(InMemoryPersistenceServiceImpl.DEFAULT_STORAGE_DIR).toAbsolutePath().normalize();
        FileUtils.deleteDirectory(defaultStorageDir.toFile());

        // Create persistence service
        persistenceService = new InMemoryPersistenceServiceImpl(executionContextManager, conditionEvaluatorDispatcher);

        // Create scheduler service using TestHelper
        schedulerService = (SchedulerServiceImpl) TestHelper.createSchedulerService(persistenceService, executionContextManager);
    }

    @After
    public void tearDown() {
        if (schedulerService != null) {
            schedulerService.preDestroy();
            schedulerService = null;
        }
        if (persistenceService != null) {
            persistenceService = null;
        }
        if (executionContextManager != null) {
            executionContextManager = null;
        }
        if (securityService != null) {
            securityService = null;
        }
    }

    @Test
    public void testInitialization() {
        // Test with default thread pool size
        SchedulerServiceImpl newSchedulerService = (SchedulerServiceImpl) TestHelper.createSchedulerService(persistenceService, executionContextManager);

        assertNotNull("Single thread scheduler should be initialized", newSchedulerService.getScheduleExecutorService());
        assertNotNull("Shared scheduler should be initialized", newSchedulerService.getSharedScheduleExecutorService());
        assertFalse("Scheduler should not be shutdown", newSchedulerService.getScheduleExecutorService().isShutdown());
        assertFalse("Shared scheduler should not be shutdown", newSchedulerService.getSharedScheduleExecutorService().isShutdown());

        newSchedulerService.preDestroy();
    }

    @Test
    public void testShutdown() {
        SchedulerServiceImpl newSchedulerService = (SchedulerServiceImpl) TestHelper.createSchedulerService(persistenceService, executionContextManager);
        newSchedulerService.preDestroy();

        assertTrue("Scheduler should be shutdown", newSchedulerService.getScheduleExecutorService().isShutdown());
        assertTrue("Shared scheduler should be shutdown", newSchedulerService.getSharedScheduleExecutorService().isShutdown());
    }

    @Test
    public void testThreadPoolSize() {
        // Test with various thread pool sizes
        int[] testSizes = {1, 4, 8, 16};

        for (int size : testSizes) {
            SchedulerServiceImpl service = new SchedulerServiceImpl();
            service.setThreadPoolSize(size);
            service.postConstruct();

            // Verify thread pool size by submitting tasks
            AtomicInteger concurrentTasks = new AtomicInteger(0);
            AtomicInteger maxConcurrentTasks = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(size);

            for (int i = 0; i < size; i++) {
                service.getSharedScheduleExecutorService().submit(() -> {
                    try {
                        int current = concurrentTasks.incrementAndGet();
                        maxConcurrentTasks.set(Math.max(maxConcurrentTasks.get(), current));
                        Thread.sleep(100); // Give time for other threads to start
                        concurrentTasks.decrementAndGet();
                        latch.countDown();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            try {
                latch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Test interrupted");
            }

            assertEquals("Should use configured thread pool size", size, maxConcurrentTasks.get());
            service.preDestroy();
        }
    }

    @Test
    public void testGetTimeDiffInSeconds() {
        // Test cases for different scenarios
        ZonedDateTime[] testTimes = {
            ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
            ZonedDateTime.of(2024, 1, 1, 12, 30, 15, 0, ZoneOffset.UTC),
            ZonedDateTime.of(2024, 1, 1, 23, 59, 59, 0, ZoneOffset.UTC)
        };

        int[] testHours = {0, 6, 12, 18, 23};

        for (ZonedDateTime now : testTimes) {
            for (int targetHour : testHours) {
                long diff = SchedulerServiceImpl.getTimeDiffInSeconds(targetHour, now);
                assertTrue("Time difference should be non-negative", diff >= 0);
                assertTrue("Time difference should be less than 24 hours", diff < 24 * 60 * 60);

                // Verify the calculation
                ZonedDateTime nextRun = now.withHour(targetHour).withMinute(0).withSecond(0);
                if (now.compareTo(nextRun) > 0) {
                    nextRun = nextRun.plusDays(1);
                }
                assertEquals("Time difference should match expected value",
                    Duration.between(now, nextRun).getSeconds(), diff);
            }
        }
    }

    @Test
    public void testTaskScheduling() throws Exception {
        schedulerService.setThreadPoolSize(2);
        schedulerService.postConstruct();

        // Test single thread scheduler
        CountDownLatch singleLatch = new CountDownLatch(1);
        AtomicInteger singleCounter = new AtomicInteger(0);

        ScheduledFuture<?> singleFuture = schedulerService.getScheduleExecutorService()
            .scheduleAtFixedRate(() -> {
                singleCounter.incrementAndGet();
                singleLatch.countDown();
            }, 0, 1, TimeUnit.SECONDS);

        assertTrue("Task should execute within timeout", singleLatch.await(2, TimeUnit.SECONDS));
        assertEquals("Task should execute once", 1, singleCounter.get());
        singleFuture.cancel(true);

        // Test shared scheduler
        CountDownLatch sharedLatch = new CountDownLatch(2);
        AtomicInteger sharedCounter = new AtomicInteger(0);

        ScheduledFuture<?> sharedFuture1 = schedulerService.getSharedScheduleExecutorService()
            .scheduleAtFixedRate(() -> {
                sharedCounter.incrementAndGet();
                sharedLatch.countDown();
            }, 0, 1, TimeUnit.SECONDS);

        ScheduledFuture<?> sharedFuture2 = schedulerService.getSharedScheduleExecutorService()
            .scheduleAtFixedRate(() -> {
                sharedCounter.incrementAndGet();
                sharedLatch.countDown();
            }, 0, 1, TimeUnit.SECONDS);

        assertTrue("Tasks should execute within timeout", sharedLatch.await(2, TimeUnit.SECONDS));
        assertEquals("Both tasks should execute", 2, sharedCounter.get());
        sharedFuture1.cancel(true);
        sharedFuture2.cancel(true);
    }

    @Test
    public void testTaskCancellation() throws Exception {
        schedulerService.setThreadPoolSize(1);
        schedulerService.postConstruct();

        AtomicInteger counter = new AtomicInteger(0);
        ScheduledFuture<?> future = schedulerService.getScheduleExecutorService()
            .scheduleAtFixedRate(() -> counter.incrementAndGet(), 0, 100, TimeUnit.MILLISECONDS);

        // Let some executions happen
        Thread.sleep(250);
        future.cancel(true);
        int count = counter.get();
        Thread.sleep(200);

        // Verify no more executions after cancellation
        assertEquals("Task should not execute after cancellation", count, counter.get());
    }

    @Test
    public void testSchedulerResilience() throws Exception {
        schedulerService.setThreadPoolSize(1);
        schedulerService.postConstruct();

        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger errorCounter = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(2);

        // Schedule a task that throws an exception
        schedulerService.getScheduleExecutorService().scheduleAtFixedRate(() -> {
            try {
                throw new RuntimeException("Test exception");
            } catch (Exception e) {
                errorCounter.incrementAndGet();
                latch.countDown();
            }
        }, 0, 100, TimeUnit.MILLISECONDS);

        // Schedule a normal task
        schedulerService.getScheduleExecutorService().scheduleAtFixedRate(() -> {
            successCounter.incrementAndGet();
            latch.countDown();
        }, 0, 100, TimeUnit.MILLISECONDS);

        assertTrue("Tasks should execute within timeout", latch.await(2, TimeUnit.SECONDS));
        assertTrue("Error task should execute", errorCounter.get() > 0);
        assertTrue("Success task should execute", successCounter.get() > 0);
    }

    @Test
    public void testCreateAndScheduleTask() throws Exception {
        // Create a test executor
        TestTaskExecutor executor = new TestTaskExecutor();
        schedulerService.registerTaskExecutor(executor);
        schedulerService.postConstruct();

        // Create a new task
        Map<String, Object> params = new HashMap<>();
        params.put("testParam", "value");
        ScheduledTask task = schedulerService.createTask(
            "test-type",
            params,
            100,
            1000,
            TimeUnit.MILLISECONDS,
            true,
            false,
            false,
            true
        );

        // Verify task was saved
        ScheduledTask savedTask = persistenceService.load(task.getItemId(), ScheduledTask.class);
        assertNotNull("Task should be saved", savedTask);
        assertEquals("Task should have correct type", "test-type", savedTask.getTaskType());

        // Schedule the task
        schedulerService.scheduleTask(task);

        // Wait for execution
        Thread.sleep(200);

        // Verify execution
        assertTrue("Task should have executed", executor.wasExecuted());

        // Verify task status was updated
        savedTask = persistenceService.load(task.getItemId(), ScheduledTask.class);
        assertEquals("Task should be in COMPLETED status", ScheduledTask.TaskStatus.COMPLETED, savedTask.getStatus());
    }

    @Test
    public void testTaskCrashRecovery() throws Exception {
        // Create a crashed task
        ScheduledTask crashedTask = new ScheduledTask();
        crashedTask.setItemId("crashed-task");
        crashedTask.setTaskType("test-type");
        crashedTask.setStatus(ScheduledTask.TaskStatus.RUNNING);
        crashedTask.setLockOwner("dead-node");
        crashedTask.setLockDate(new Date(System.currentTimeMillis() - 10 * 60 * 1000)); // 10 minutes ago
        crashedTask.setEnabled(true);
        persistenceService.save(crashedTask);

        // Create executor that supports resuming
        TestResumableExecutor executor = new TestResumableExecutor();
        schedulerService.registerTaskExecutor(executor);
        schedulerService.postConstruct();

        // Trigger crash recovery
        schedulerService.recoverCrashedTasks();

        // Verify task was marked as crashed
        ScheduledTask updatedTask = persistenceService.load(crashedTask.getItemId(), ScheduledTask.class);
        assertEquals("Task should be marked as CRASHED", ScheduledTask.TaskStatus.CRASHED, updatedTask.getStatus());
        assertNull("Lock should be released", updatedTask.getLockOwner());
    }

    @Test
    public void testTaskRetryAndFailure() throws Exception {
        // Create a failed task
        ScheduledTask failedTask = new ScheduledTask();
        failedTask.setItemId("failed-task");
        failedTask.setTaskType("test-type");
        failedTask.setStatus(ScheduledTask.TaskStatus.FAILED);
        failedTask.setFailureCount(2);
        failedTask.setEnabled(true);
        failedTask.setRetryDelay(1000L); // Set retry delay in milliseconds
        failedTask.setMaxRetries(3); // Set max retries
        failedTask.setTimeUnit(TimeUnit.MILLISECONDS);
        // Remove period and initial delay as they are not needed for retries
        persistenceService.save(failedTask);

        // Create executor that will fail
        TestFailingExecutor executor = new TestFailingExecutor();
        schedulerService.registerTaskExecutor(executor);

        // Try to retry the task
        schedulerService.retryTask("failed-task", false);

        // Wait for execution
        Thread.sleep(1500); // Wait longer than the retry delay

        // Verify failure count increases
        ScheduledTask updatedTask = persistenceService.load(failedTask.getItemId(), ScheduledTask.class);
        assertEquals("Failure count should increase", 3, updatedTask.getFailureCount());
        assertNotNull("Error message should be set", updatedTask.getLastError());
    }

    @Test
    public void testTaskQuerying() {
        // Create some test tasks
        ScheduledTask task1 = new ScheduledTask();
        task1.setItemId("task1");
        task1.setTaskType("type1");
        task1.setStatus(ScheduledTask.TaskStatus.COMPLETED);
        persistenceService.save(task1);

        ScheduledTask task2 = new ScheduledTask();
        task2.setItemId("task2");
        task2.setTaskType("type1");
        task2.setStatus(ScheduledTask.TaskStatus.FAILED);
        persistenceService.save(task2);

        // Test querying by status
        PartialList<ScheduledTask> completedTasks = schedulerService.getTasksByStatus(
            ScheduledTask.TaskStatus.COMPLETED, 0, 10, null);
        assertEquals("Should return matching tasks", 1, completedTasks.getList().size());
        assertEquals("Should return task1", task1.getItemId(), completedTasks.getList().get(0).getItemId());

        // Test querying by type
        PartialList<ScheduledTask> typeTasks = schedulerService.getTasksByType("type1", 0, 10, null);
        assertEquals("Should return all matching tasks", 2, typeTasks.getList().size());
    }

    @Test
    public void testTaskCheckpointing() throws Exception {
        // Create a test executor that uses checkpoints
        TestCheckpointingExecutor executor = new TestCheckpointingExecutor();
        schedulerService.registerTaskExecutor(executor);
        schedulerService.postConstruct();

        // Create and schedule a task
        ScheduledTask task = schedulerService.createTask(
            "checkpoint-type",
            Collections.emptyMap(),
            0,
            1000,
            TimeUnit.MILLISECONDS,
            false,
            true,
            false,
            true
        );

        schedulerService.scheduleTask(task);

        // Wait for execution
        Thread.sleep(200);

        // Verify checkpoints were saved by loading the task from persistence
        ScheduledTask updatedTask = persistenceService.load(task.getItemId(), ScheduledTask.class);
        assertNotNull("Task should exist in persistence", updatedTask);
        assertNotNull("Checkpoint data should exist", updatedTask.getCheckpointData());
        assertEquals("Checkpoint data should be saved", "step1", updatedTask.getCheckpointData().get("lastStep"));
    }

    @Test
    public void testNonPersistentTask() throws Exception {
        // Create a test executor
        TestTaskExecutor executor = new TestTaskExecutor();
        schedulerService.registerTaskExecutor(executor);
        schedulerService.postConstruct();

        // Create a non-persistent task using the builder
        ScheduledTask task = schedulerService.newTask("test-type")
            .nonPersistent()
            .withPeriod(100, TimeUnit.MILLISECONDS)
            .withSimpleExecutor(() -> {})
            .schedule();

        // Wait for execution
        Thread.sleep(200);

        // Verify task was not saved to persistence
        ScheduledTask savedTask = persistenceService.load(task.getItemId(), ScheduledTask.class);
        assertNull("Non-persistent task should not be saved", savedTask);
    }

    @Test
    public void testRunOnAllNodes() throws Exception {
        // Create a test executor
        TestTaskExecutor executor = new TestTaskExecutor();
        schedulerService.registerTaskExecutor(executor);

        // First test with executorNode = false
        schedulerService.setExecutorNode(false);
        schedulerService.postConstruct();

        AtomicBoolean executed = new AtomicBoolean(false);

        // Create a normal task (should not run on non-executor node)
        ScheduledTask normalTask = schedulerService.newTask("test-type")
            .withPeriod(100, TimeUnit.MILLISECONDS)
            .withSimpleExecutor(() -> executed.set(true))
            .schedule();

        Thread.sleep(200);
        assertFalse("Normal task should not execute on non-executor node", executed.get());

        // Create a runOnAllNodes task (should run even on non-executor node)
        executed.set(false);
        ScheduledTask allNodesTask = schedulerService.newTask("test-type")
            .runOnAllNodes()
            .withPeriod(100, TimeUnit.MILLISECONDS)
            .withSimpleExecutor(() -> executed.set(true))
            .schedule();

        Thread.sleep(200);
        assertTrue("Task with runOnAllNodes should execute on all nodes", executed.get());
    }

    @Test
    public void testTaskPurging() throws Exception {
        // Set a short TTL for testing
        schedulerService.preDestroy(); // Shutdown existing service

        // Create and complete some tasks
        ScheduledTask recentTask = new ScheduledTask();
        recentTask.setItemId("recent-task");
        recentTask.setTaskType("test-type");
        recentTask.setStatus(ScheduledTask.TaskStatus.COMPLETED);
        recentTask.setLastExecutionDate(new Date());
        recentTask.setOneShot(true);
        persistenceService.save(recentTask);

        // Create a task that's older than the TTL
        ScheduledTask oldTask = new ScheduledTask();
        oldTask.setItemId("old-task");
        oldTask.setTaskType("test-type");
        oldTask.setStatus(ScheduledTask.TaskStatus.COMPLETED);
        oldTask.setLastExecutionDate(new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2))); // 2 days old
        oldTask.setOneShot(true);
        persistenceService.save(oldTask);

        // Create a non-completed old task (should not be purged)
        ScheduledTask runningTask = new ScheduledTask();
        runningTask.setItemId("running-task");
        runningTask.setTaskType("test-type");
        runningTask.setStatus(ScheduledTask.TaskStatus.RUNNING);
        runningTask.setLastExecutionDate(new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2))); // 2 days old
        persistenceService.save(runningTask);

        schedulerService = new SchedulerServiceImpl();
        schedulerService.setPersistenceService(persistenceService);
        schedulerService.setThreadPoolSize(2);
        schedulerService.setExecutorNode(true);
        schedulerService.setCompletedTaskTtlDays(1);
        schedulerService.setPurgeTaskEnabled(true); // Enable purge task specifically for this test
        schedulerService.postConstruct();

        // Wait for purge task to run
        Thread.sleep(1000);

        // Verify results
        assertNotNull("Recent completed task should not be purged",
            persistenceService.load(recentTask.getItemId(), ScheduledTask.class));
        assertNull("Old completed task should be purged",
            persistenceService.load(oldTask.getItemId(), ScheduledTask.class));
        assertNotNull("Running task should not be purged regardless of age",
            persistenceService.load(runningTask.getItemId(), ScheduledTask.class));
    }

    @Test
    public void testTaskBuilderConfigurations() throws Exception {
        schedulerService.postConstruct();

        // Test all builder configurations
        AtomicBoolean executed = new AtomicBoolean(false);
        Map<String, Object> testParams = Collections.singletonMap("test", "value");

        ScheduledTask task = schedulerService.newTask("test-type")
            .withParameters(testParams)
            .withInitialDelay(100, TimeUnit.MILLISECONDS)
            .withPeriod(200, TimeUnit.MILLISECONDS)
            .withFixedDelay()
            .disallowParallelExecution()
            .withSimpleExecutor(() -> executed.set(true))
            .schedule();

        Thread.sleep(300);
        assertTrue("Task should execute", executed.get());

        // Verify all configurations were applied
        ScheduledTask savedTask = persistenceService.load(task.getItemId(), ScheduledTask.class);
        assertNotNull("Task should be saved", savedTask);
        assertEquals("Parameters should be set", testParams, savedTask.getParameters());
        assertEquals("Initial delay should be set", 100, savedTask.getInitialDelay());
        assertEquals("Period should be set", 200, savedTask.getPeriod());
        assertFalse("Should be fixed delay", savedTask.isFixedRate());
        assertFalse("Should disallow parallel execution", savedTask.isAllowParallelExecution());
    }

    @Test
    public void testFixedRateVsFixedDelay() throws Exception {
        schedulerService.postConstruct();
        CountDownLatch latch = new CountDownLatch(8); // Wait for 4 executions of each task

        // For fixed rate, we'll track actual execution times
        List<Long> fixedRateExecutionTimes = Collections.synchronizedList(new ArrayList<>());

        // For fixed delay, we'll track actual execution times
        List<Long> fixedDelayExecutionTimes = Collections.synchronizedList(new ArrayList<>());

        // Test fixed rate execution - should try to maintain consistent rate
        schedulerService.newTask("fixed-rate")
            .withPeriod(100, TimeUnit.MILLISECONDS)
            .withFixedRate()
            .withSimpleExecutor(() -> {
                fixedRateExecutionTimes.add(System.nanoTime());
                latch.countDown();
                try {
                    // Simulate varying work time between 50-100ms
                    Thread.sleep(50 + new Random().nextInt(50));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            })
            .schedule();

        // Test fixed delay execution - should wait fixed time after completion
        schedulerService.newTask("fixed-delay")
            .withPeriod(100, TimeUnit.MILLISECONDS)
            .withFixedDelay()
            .withSimpleExecutor(() -> {
                fixedDelayExecutionTimes.add(System.nanoTime());
                latch.countDown();
                try {
                    // Simulate varying work time between 50-100ms
                    Thread.sleep(50 + new Random().nextInt(50));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            })
            .schedule();

        assertTrue("Tasks should execute", latch.await(5, TimeUnit.SECONDS));

        // Calculate average intervals for both types
        double avgFixedRateInterval = calculateAverageInterval(fixedRateExecutionTimes);
        double avgFixedDelayInterval = calculateAverageInterval(fixedDelayExecutionTimes);

        // Fixed rate should maintain closer to the specified period (100ms) despite execution time
        // Fixed delay will always be period (100ms) + execution time (50-100ms)
        assertTrue("Fixed rate intervals should be shorter: " +
                String.format("fixed rate avg=%.2fms vs fixed delay avg=%.2fms",
                        avgFixedRateInterval / 1_000_000.0,
                        avgFixedDelayInterval / 1_000_000.0),
            avgFixedRateInterval < avgFixedDelayInterval);
    }

    private double calculateAverageInterval(List<Long> executionTimes) {
        if (executionTimes.size() < 2) {
            return 0;
        }
        long totalInterval = 0;
        for (int i = 1; i < executionTimes.size(); i++) {
            totalInterval += executionTimes.get(i) - executionTimes.get(i-1);
        }
        return totalInterval / (double)(executionTimes.size() - 1);
    }

    @Test
    public void testParallelExecutionControl() throws Exception {
        final CountDownLatch startLatch = new CountDownLatch(2);  // Used to wait for both tasks to start
        final CountDownLatch executingLatch = new CountDownLatch(2);  // Used to signal both tasks are executing
        final CountDownLatch completionLatch = new CountDownLatch(2);  // Used to wait for both tasks to complete
        final Set<Long> concurrentThreads = Collections.synchronizedSet(new HashSet<>());
        final AtomicInteger maxConcurrentThreads = new AtomicInteger(0);

        Runnable taskLogic = () -> {
            try {
                startLatch.countDown();  // Signal this task has started
                // Wait for both tasks to start
                if (!startLatch.await(1, TimeUnit.SECONDS)) {
                    return;  // Timeout - test will fail
                }

                // Add this thread to the set of concurrent threads
                concurrentThreads.add(Thread.currentThread().getId());
                // Update max concurrent threads
                maxConcurrentThreads.set(Math.max(maxConcurrentThreads.get(), concurrentThreads.size()));

                executingLatch.countDown();  // Signal this task is executing
                // Wait for both tasks to be executing
                if (!executingLatch.await(1, TimeUnit.SECONDS)) {
                    return;  // Timeout - test will fail
                }

                // Keep executing to maintain overlap
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                concurrentThreads.remove(Thread.currentThread().getId());
                completionLatch.countDown();
            }
        };

        // Schedule two instances of the same task
        schedulerService.newTask("parallel-allowed-1")
            .withPeriod(100, TimeUnit.MILLISECONDS)
            .withSimpleExecutor(taskLogic)
            .schedule();

        schedulerService.newTask("parallel-allowed-2")
            .withPeriod(100, TimeUnit.MILLISECONDS)
            .withSimpleExecutor(taskLogic)
            .schedule();

        assertTrue("Tasks should start", startLatch.await(1, TimeUnit.SECONDS));
        assertTrue("Tasks should reach execution phase", executingLatch.await(1, TimeUnit.SECONDS));
        assertTrue("Tasks should complete", completionLatch.await(2, TimeUnit.SECONDS));
        assertEquals("Should allow parallel execution", 2, maxConcurrentThreads.get());

        // Reset for second test
        final CountDownLatch startLatch2 = new CountDownLatch(2);
        final CountDownLatch completionLatch2 = new CountDownLatch(2);
        final Set<Long> concurrentThreads2 = Collections.synchronizedSet(new HashSet<>());
        final AtomicInteger maxConcurrentThreads2 = new AtomicInteger(0);

        Runnable taskLogic2 = () -> {
            try {
                startLatch2.countDown();
                concurrentThreads2.add(Thread.currentThread().getId());
                maxConcurrentThreads2.set(Math.max(maxConcurrentThreads2.get(), concurrentThreads2.size()));
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                concurrentThreads2.remove(Thread.currentThread().getId());
                completionLatch2.countDown();
            }
        };

        // Schedule two instances of the non-parallel task
        schedulerService.newTask("parallel-disabled-1")
            .withPeriod(100, TimeUnit.MILLISECONDS)
            .disallowParallelExecution()
            .withSimpleExecutor(taskLogic2)
            .schedule();

        schedulerService.newTask("parallel-disabled-2")
            .withPeriod(100, TimeUnit.MILLISECONDS)
            .disallowParallelExecution()
            .withSimpleExecutor(taskLogic2)
            .schedule();

        assertTrue("Tasks should complete", completionLatch2.await(3, TimeUnit.SECONDS));
        assertEquals("Should prevent parallel execution", 1, maxConcurrentThreads2.get());
    }

    @Test
    public void testTaskStatusTransitions() throws Exception {
        schedulerService.postConstruct();
        CountDownLatch executionLatch = new CountDownLatch(1);
        AtomicReference<ScheduledTask.TaskStatus> statusDuringExecution = new AtomicReference<>();

        // Create a task that will check its status during execution
        final ScheduledTask[] taskRef = new ScheduledTask[1];
        taskRef[0] = schedulerService.newTask("status-test")
            .withSimpleExecutor(() -> {
                try {
                    ScheduledTask currentTask = persistenceService.load(taskRef[0].getItemId(), ScheduledTask.class);
                    statusDuringExecution.set(currentTask.getStatus());
                } finally {
                    executionLatch.countDown();
                }
            })
            .schedule();

        // Verify initial status
        assertEquals("Initial status should be SCHEDULED",
            ScheduledTask.TaskStatus.SCHEDULED, taskRef[0].getStatus());

        // Wait for execution
        assertTrue("Task should execute", executionLatch.await(1, TimeUnit.SECONDS));
        assertEquals("Status during execution should be RUNNING",
            ScheduledTask.TaskStatus.RUNNING, statusDuringExecution.get());

        // Wait for completion
        Thread.sleep(100);
        ScheduledTask completedTask = persistenceService.load(taskRef[0].getItemId(), ScheduledTask.class);
        assertEquals("Final status should be COMPLETED",
            ScheduledTask.TaskStatus.COMPLETED, completedTask.getStatus());

        // Test cancellation
        ScheduledTask taskToCancel = schedulerService.newTask("cancel-test")
            .withPeriod(1, TimeUnit.DAYS)
            .schedule();

        schedulerService.cancelTask(taskToCancel.getItemId());
        ScheduledTask cancelledTask = persistenceService.load(taskToCancel.getItemId(), ScheduledTask.class);
        assertEquals("Status after cancellation should be CANCELLED",
            ScheduledTask.TaskStatus.CANCELLED, cancelledTask.getStatus());
    }

    @Test
    public void testOneShotTask() throws Exception {
        schedulerService.postConstruct();
        CountDownLatch executionLatch = new CountDownLatch(1);
        AtomicInteger executionCount = new AtomicInteger(0);

        ScheduledTask task = schedulerService.newTask("one-shot")
            .asOneShot()
            .withSimpleExecutor(() -> {
                executionCount.incrementAndGet();
                executionLatch.countDown();
            })
            .schedule();

        assertTrue("Task should execute once", executionLatch.await(1, TimeUnit.SECONDS));
        Thread.sleep(200); // Wait to ensure no more executions
        assertEquals("One-shot task should execute exactly once", 1, executionCount.get());

        ScheduledTask savedTask = persistenceService.load(task.getItemId(), ScheduledTask.class);
        assertTrue("One-shot flag should be set", savedTask.isOneShot());
        assertEquals("Status should be COMPLETED", ScheduledTask.TaskStatus.COMPLETED, savedTask.getStatus());
    }

    @Test
    public void testNonPersistentTaskBehavior() throws Exception {
        CountDownLatch executionLatch = new CountDownLatch(1);
        AtomicInteger executionCount = new AtomicInteger(0);

        // Create a non-persistent task
        ScheduledTask task = schedulerService.newTask("non-persistent-test-type")
            .nonPersistent()
            .withPeriod(100, TimeUnit.MILLISECONDS)
            .withSimpleExecutor(() -> {
                executionCount.incrementAndGet();
                executionLatch.countDown();
            })
            .schedule();

        // Verify task executes
        assertTrue("Task should execute", executionLatch.await(1, TimeUnit.SECONDS));
        assertEquals("Task should execute once", 1, executionCount.get());

        // Verify task is not persisted
        assertNull("Non-persistent task should not be saved",
            persistenceService.load(task.getItemId(), ScheduledTask.class));

        // Verify task is not returned in getAllTasks
        List<ScheduledTask> allTasks = schedulerService.getAllTasks();
        assertTrue("Non-persistent task should not be in getAllTasks",
            allTasks.stream().noneMatch(t -> t.getItemId().equals(task.getItemId())));

        // Verify task can be cancelled
        schedulerService.cancelTask(task.getItemId());
        Thread.sleep(200); // Wait for another potential execution
        int finalCount = executionCount.get();
        Thread.sleep(200); // Wait again
        assertEquals("Task should not execute after cancellation", finalCount, executionCount.get());

        // Create a mix of persistent and non-persistent tasks
        ScheduledTask persistentTask = schedulerService.newTask("persistent-test-type")
            .withPeriod(1000, TimeUnit.MILLISECONDS)
            .withSimpleExecutor(() -> {})
            .schedule();

        ScheduledTask anotherNonPersistentTask = schedulerService.newTask("non-persistent-test-type")
            .nonPersistent()
            .withPeriod(1000, TimeUnit.MILLISECONDS)
            .withSimpleExecutor(() -> {})
            .schedule();

        // Verify only persistent tasks are saved
        assertNotNull("Persistent task should be saved",
            persistenceService.load(persistentTask.getItemId(), ScheduledTask.class));
        assertNull("Non-persistent task should not be saved",
            persistenceService.load(anotherNonPersistentTask.getItemId(), ScheduledTask.class));

        // Verify task status updates don't persist for non-persistent tasks
        anotherNonPersistentTask.setStatus(ScheduledTask.TaskStatus.COMPLETED);
        Thread.sleep(100); // Give time for any potential persistence
        assertNull("Status update should not persist non-persistent task",
            persistenceService.load(anotherNonPersistentTask.getItemId(), ScheduledTask.class));
    }

    // Test utility classes

    private static class TestTaskExecutor implements TaskExecutor {
        private final AtomicBoolean executed = new AtomicBoolean();

        @Override
        public String getTaskType() {
            return "test-type";
        }

        @Override
        public void execute(ScheduledTask task, TaskStatusCallback callback) {
            executed.set(true);
            callback.complete();
        }

        public boolean wasExecuted() {
            return executed.get();
        }
    }

    private static class TestResumableExecutor implements TaskExecutor {
        @Override
        public String getTaskType() {
            return "test-type";
        }

        @Override
        public void execute(ScheduledTask task, TaskStatusCallback callback) {
            callback.complete();
        }

        @Override
        public boolean canResume(ScheduledTask task) {
            return true;
        }

        @Override
        public void resume(ScheduledTask task, TaskStatusCallback callback) {
            callback.complete();
        }
    }

    private static class TestFailingExecutor implements TaskExecutor {
        @Override
        public String getTaskType() {
            return "test-type";
        }

        @Override
        public void execute(ScheduledTask task, TaskStatusCallback callback) {
            callback.fail("Test failure");
        }
    }

    private static class TestCheckpointingExecutor implements TaskExecutor {
        @Override
        public String getTaskType() {
            return "checkpoint-type";
        }

        @Override
        public void execute(ScheduledTask task, TaskStatusCallback callback) throws Exception {
            callback.updateStep("step1", Collections.emptyMap());
            Map<String, Object> checkpoint = new HashMap<>();
            checkpoint.put("lastStep", "step1");
            callback.checkpoint(checkpoint);
            callback.complete();
        }
    }
}
