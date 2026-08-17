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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Multi-node race and failure scenarios for the scheduler.
 *
 * These tests rely on InMemoryPersistenceServiceImpl deep-copy + OCC semantics so each
 * node's load/query returns an isolated instance — matching Elasticsearch behaviour and
 * exposing split-brain / lock / affinity bugs that shared-identity harnesses hide.
 *
 * Invoked by Surefire as part of the services module unit suite
 * ({@code mvn -pl services test -Dtest=SchedulerServiceClusterRaceTest}).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("ClusterTests")
@ExtendWith(SchedulerDiagnosticsExtension.class)
public class SchedulerServiceClusterRaceTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerServiceClusterRaceTest.class);

    private static final long TEST_TIMEOUT_MS = 20000;
    private static final long SHORT_LOCK_TIMEOUT_MS = 800;
    private static final int MAX_RETRIES = 10;

    private PersistenceService persistenceService;
    private ExecutionContextManagerImpl executionContextManager;
    private KarafSecurityService securityService;
    private ClusterServiceImpl clusterService;
    private final List<SchedulerServiceImpl> nodes = new ArrayList<>();

    @Mock
    private BundleContext bundleContext;

    @BeforeEach
    public void setUp() throws IOException {
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
        InMemoryPersistenceServiceImpl inMemory =
            new InMemoryPersistenceServiceImpl(executionContextManager, conditionEvaluatorDispatcher);
        inMemory.setRefreshPolicy(ScheduledTask.ITEM_TYPE,
            InMemoryPersistenceServiceImpl.RefreshPolicy.WAIT_FOR);
        persistenceService = inMemory;
        clusterService = TestHelper.createClusterService(persistenceService, "cluster-race-seed", bundleContext);
    }

    @AfterEach
    public void tearDown() {
        for (SchedulerServiceImpl node : nodes) {
            try {
                node.preDestroy();
            } catch (Exception e) {
                LOGGER.warn("Error destroying node {}: {}", node.getNodeId(), e.getMessage());
            }
        }
        nodes.clear();
    }

    private SchedulerServiceImpl createNode(String nodeId, boolean executorNode, long lockTimeoutMs) {
        SchedulerServiceImpl node = (SchedulerServiceImpl) TestHelper.createSchedulerService(
            nodeId, persistenceService, executionContextManager, bundleContext, clusterService,
            lockTimeoutMs, executorNode, true, 0);
        node.setLockTimeout(lockTimeoutMs);
        nodes.add(node);
        return node;
    }

    private void seedActiveNodes(String... nodeIds) {
        for (String id : nodeIds) {
            ScheduledTask marker = new ScheduledTask();
            marker.setItemId(id + "-active-marker");
            marker.setTaskType("cluster-active-marker");
            marker.setPersistent(true);
            marker.setEnabled(false);
            marker.setStatus(ScheduledTask.TaskStatus.COMPLETED);
            marker.setOneShot(true);
            marker.setCreationDate(new Date());
            marker.setLockOwner(id);
            marker.setLockDate(new Date());
            persistenceService.save(marker);
        }
        persistenceService.refreshIndex(ScheduledTask.class);
        persistenceService.refresh();
    }

    private ScheduledTask waitForStatus(SchedulerServiceImpl node, String taskId,
                                        ScheduledTask.TaskStatus status, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        ScheduledTask task = null;
        while (System.currentTimeMillis() < deadline) {
            task = node.getTask(taskId);
            if (task != null && status.equals(task.getStatus())) {
                return task;
            }
            Thread.sleep(50);
        }
        return task;
    }

    /**
     * Asserts that two nodes see the same logical task via independent deep-copied instances.
     * Shared identity would mean the in-memory harness is leaking store references across nodes.
     */
    private void assertDistinctTaskViews(SchedulerServiceImpl nodeA, SchedulerServiceImpl nodeB, String taskId) {
        ScheduledTask a = nodeA.getTask(taskId);
        ScheduledTask b = nodeB.getTask(taskId);
        assertNotNull(a, "nodeA must see task " + taskId);
        assertNotNull(b, "nodeB must see task " + taskId);
        assertNotSame(a, b, "Nodes must not share the same ScheduledTask instance in memory");
        assertEquals(a.getItemId(), b.getItemId());
        assertEquals(a.getStatus(), b.getStatus());
        if (a.getCheckpointData() != null && b.getCheckpointData() != null) {
            assertNotSame(a.getCheckpointData(), b.getCheckpointData(),
                "checkpointData maps must be deep-copied per load");
        }
        if (a.getStatusDetails() != null && b.getStatusDetails() != null) {
            assertNotSame(a.getStatusDetails(), b.getStatusDetails(),
                "statusDetails maps must be deep-copied per load");
        }
    }

    private void expireLockInStore(String taskId) {
        ScheduledTask stored = persistenceService.load(taskId, ScheduledTask.class);
        assertNotNull(stored, "task must exist to expire lock: " + taskId);
        stored.setLockDate(new Date(System.currentTimeMillis() - 60_000));
        assertTrue(persistenceService.save(stored));
        persistenceService.refreshIndex(ScheduledTask.class);
        persistenceService.refresh();
    }

    private void registerOnAll(TaskExecutor executor, SchedulerServiceImpl... nodeArr) {
        for (SchedulerServiceImpl node : nodeArr) {
            node.registerTaskExecutor(executor);
        }
    }

    @Test
    public void testInMemoryLoadReturnsIsolatedInstance() {
        ScheduledTask original = new ScheduledTask();
        original.setItemId("isolate-1");
        original.setTaskType("isolate");
        original.setPersistent(true);
        original.setEnabled(true);
        original.setStatus(ScheduledTask.TaskStatus.SCHEDULED);
        original.setOneShot(true);
        original.setCreationDate(new Date());
        Map<String, Object> checkpoint = new HashMap<>();
        checkpoint.put("step", 1);
        original.setCheckpointData(checkpoint);
        Map<String, Object> details = new HashMap<>();
        details.put("note", "seed");
        original.setStatusDetails(details);
        persistenceService.save(original);

        // Mutating the caller's instance after save must not rewrite the store (save stores a copy).
        original.setStatus(ScheduledTask.TaskStatus.RUNNING);
        original.getCheckpointData().put("step", 99);

        ScheduledTask loaded1 = persistenceService.load("isolate-1", ScheduledTask.class);
        ScheduledTask loaded2 = persistenceService.load("isolate-1", ScheduledTask.class);
        assertNotSame(loaded1, loaded2, "Each load must return a distinct instance");
        assertNotSame(original, loaded1, "Load must not return the caller's instance");
        assertEquals(ScheduledTask.TaskStatus.SCHEDULED, loaded1.getStatus(),
            "Post-save mutation of caller must not affect stored document");
        assertEquals(1, ((Number) loaded1.getCheckpointData().get("step")).intValue());

        loaded1.setStatus(ScheduledTask.TaskStatus.RUNNING);
        loaded1.getCheckpointData().put("step", 42);
        loaded1.getStatusDetails().put("note", "mutated");
        ScheduledTask loaded3 = persistenceService.load("isolate-1", ScheduledTask.class);
        assertEquals(ScheduledTask.TaskStatus.SCHEDULED, loaded3.getStatus(),
            "Mutating a loaded copy must not change the stored document");
        assertEquals(1, ((Number) loaded3.getCheckpointData().get("step")).intValue());
        assertEquals("seed", loaded3.getStatusDetails().get("note"));
        assertNotSame(loaded1.getCheckpointData(), loaded3.getCheckpointData());
        assertNotSame(loaded1.getStatusDetails(), loaded3.getStatusDetails());
    }

    @Test
    public void testOptimisticConcurrencyConflictRejectsStaleLockWrite() throws Exception {
        SchedulerServiceImpl node1 = createNode("occ-node1", true, 5000);
        createNode("occ-node2", true, 5000);
        seedActiveNodes("occ-node1", "occ-node2");

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        node1.newTask("occ-lock-test")
            .disallowParallelExecution()
            .asOneShot()
            .withSimpleExecutor(() -> {
                started.countDown();
                try {
                    release.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            })
            .schedule();

        assertTrue(started.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        ScheduledTask locked = persistenceService.load(
            persistenceService.query("taskType", "occ-lock-test", null, ScheduledTask.class).get(0).getItemId(),
            ScheduledTask.class);
        assertNotNull(locked.getLockOwner());
        Object staleSeq = locked.getSystemMetadata("seq_no");
        assertNotNull(staleSeq, "Stored task must expose ES-aligned seq_no");

        ScheduledTask fresh = persistenceService.load(locked.getItemId(), ScheduledTask.class);
        fresh.setCurrentStep("bump");
        assertTrue(persistenceService.save(fresh), "Fresh save should succeed");

        locked.setLockOwner("occ-node2");
        locked.setSystemMetadata("seq_no", staleSeq);
        locked.setSystemMetadata("_seq_no", staleSeq);
        // alwaysOverwrite=false is required for OCC (matches ES/OpenSearch + lock CAS path)
        assertFalse(persistenceService.save(locked, false, false), "Stale seq_no write must be rejected");
        release.countDown();
    }

    @Test
    public void testExclusiveLockPreventsDualNodeDoubleExecution() throws Exception {
        SchedulerServiceImpl node1 = createNode("lock-node1", true, 10000);
        SchedulerServiceImpl node2 = createNode("lock-node2", true, 10000);
        seedActiveNodes("lock-node1", "lock-node2");

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger(0);
        Set<String> executors = ConcurrentHashMap.newKeySet();

        TaskExecutor executor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return "exclusive-dual-node";
            }

            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) throws Exception {
                executions.incrementAndGet();
                executors.add(task.getExecutingNodeId());
                started.countDown();
                assertTrue(release.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
                callback.complete();
            }
        };
        node1.registerTaskExecutor(executor);
        node2.registerTaskExecutor(executor);

        ScheduledTask task = node1.newTask("exclusive-dual-node")
            .disallowParallelExecution()
            .asOneShot()
            .schedule();

        assertTrue(started.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS), "One node should start the task");
        Thread.sleep(2500);
        release.countDown();

        ScheduledTask done = waitForStatus(node1, task.getItemId(), ScheduledTask.TaskStatus.COMPLETED, TEST_TIMEOUT_MS);
        assertEquals(ScheduledTask.TaskStatus.COMPLETED, done.getStatus());
        assertEquals(1, executions.get(), "Exclusive task must execute exactly once across the cluster");
        assertEquals(1, executors.size(), "Exactly one node should have executed");
    }

    /**
     * A node configured with a SHORTER lock timeout than a peer must not "recover" that peer's
     * live, renewed lock.
     * <p>
     * The owner renews its lock every {@code lockTimeout/3} — a cadence derived from its OWN
     * timeout. Before lock leases were recorded ({@link ScheduledTask#getLockLeaseMillis()}),
     * expiry was judged against the <em>observer's</em> timeout, so an observer whose timeout was
     * shorter than the owner's renewal cadence saw every renewal gap as an expired lock: it marked
     * the live execution CRASHED and cleared the lock, and the next peer tick re-dispatched the
     * task while the original execution was still running. This reproduced deterministically as
     * {@code maxConcurrent=2} with a 1s-timeout observer against 10s-timeout workers, and is also a
     * production hazard under config drift or rolling upgrades. The recorded lease makes expiry
     * owner-relative, so the divergent observer becomes harmless.
     */
    @Test
    public void testShortTimeoutObserverCannotRecoverLiveRenewedLock() throws Exception {
        SchedulerServiceImpl worker1 = createNode("lease-worker1", true, 10000);
        SchedulerServiceImpl worker2 = createNode("lease-worker2", true, 10000);
        // Divergent config: this node judges everything with a 500ms timeout. It registers no
        // executor for the task type, so any double execution must come via a worker re-dispatch.
        SchedulerServiceImpl watchdog = createNode("lease-watchdog", true, 500);
        seedActiveNodes("lease-worker1", "lease-worker2", "lease-watchdog");

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger(0);

        TaskExecutor executor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return "lease-liveness-test";
            }

            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) throws Exception {
                executions.incrementAndGet();
                started.countDown();
                assertTrue(release.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
                callback.complete();
            }
        };
        worker1.registerTaskExecutor(executor);
        worker2.registerTaskExecutor(executor);

        ScheduledTask task = worker1.newTask("lease-liveness-test")
            .disallowParallelExecution()
            .asOneShot()
            .schedule();

        assertTrue(started.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS), "One worker should start the task");

        // Let the lock age past the watchdog's 500ms timeout while staying far inside the owner's
        // 10s lease (the owner's renewal cadence is 10s/3, so the age check below cannot be
        // satisfied by a renewal racing us — any observed age > 600ms is a genuine renewal gap).
        long deadline = System.currentTimeMillis() + TEST_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            ScheduledTask stored = persistenceService.load(task.getItemId(), ScheduledTask.class);
            if (stored != null && stored.getLockDate() != null
                    && System.currentTimeMillis() - stored.getLockDate().getTime() > 600) {
                break;
            }
            Thread.sleep(50);
        }

        // Force the divergent observer's recovery pass repeatedly — the deterministic version of
        // the background tick that used to steal the lock.
        for (int i = 0; i < 3; i++) {
            watchdog.recoverCrashedTasks();
        }

        ScheduledTask observed = persistenceService.load(task.getItemId(), ScheduledTask.class);
        assertEquals(ScheduledTask.TaskStatus.RUNNING, observed.getStatus(),
            "A live, renewed lock must not be marked CRASHED by a shorter-timeout observer");
        assertNotNull(observed.getLockOwner(), "The owner's lock must not be cleared");

        release.countDown();

        ScheduledTask done = waitForStatus(worker1, task.getItemId(), ScheduledTask.TaskStatus.COMPLETED, TEST_TIMEOUT_MS);
        assertEquals(ScheduledTask.TaskStatus.COMPLETED, done.getStatus());
        assertEquals(1, executions.get(),
            "The task must execute exactly once despite the divergent-timeout observer");
    }

    /**
     * The recovery-enabling direction of lease-based expiry: a genuinely DEAD owner must still be
     * recovered, and the moment that happens is decided by the lease the dead owner recorded, not
     * by the survivor's own (here much longer) timeout. This is the guarantee that keeps crash
     * failover working after the lease change — and it is now faster when the dead node ran with
     * a short timeout, because peers no longer wait out their own longer opinion.
     */
    @Test
    public void testDeadOwnersShortLeaseDrivesPromptRecoveryByPatientSurvivor() throws Exception {
        SchedulerServiceImpl survivor = createNode("lease-survivor", true, 30_000);
        seedActiveNodes("lease-survivor");

        CountDownLatch recovered = new CountDownLatch(1);
        TaskExecutor executor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return "dead-owner-lease-test";
            }

            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) {
                recovered.countDown();
                callback.complete();
            }
        };
        survivor.registerTaskExecutor(executor);

        // Manufacture what a crashed node leaves behind: RUNNING, locked, lease recorded from a
        // short timeout, and silent (no renewal will ever come). lockDate is backdated past the
        // lease so the very first recovery pass can act.
        ScheduledTask ghost = new ScheduledTask();
        ghost.setItemId("ghost-owned-task");
        ghost.setTaskType("dead-owner-lease-test");
        ghost.setEnabled(true);
        ghost.setPersistent(true);
        ghost.setOneShot(true);
        ghost.setStatus(ScheduledTask.TaskStatus.RUNNING);
        ghost.setExecutingNodeId("ghost-node");
        ghost.setLockOwner("ghost-node");
        ghost.setLockDate(new Date(System.currentTimeMillis() - 2000));
        ghost.setLockLeaseMillis(500);
        persistenceService.save(ghost);
        persistenceService.refreshIndex(ScheduledTask.class);
        persistenceService.refresh();

        // Force recovery passes rather than waiting for background ticks. The survivor's own
        // timeout is 30s: pre-lease it would have refused to touch this lock for 30s, and this
        // latch (10s) would time out. The recorded 500ms lease is what lets it act now.
        long deadline = System.currentTimeMillis() + TEST_TIMEOUT_MS;
        while (recovered.getCount() > 0 && System.currentTimeMillis() < deadline) {
            survivor.recoverCrashedTasks();
            recovered.await(250, TimeUnit.MILLISECONDS);
        }

        assertTrue(recovered.getCount() == 0,
            "a patient survivor must recover a dead owner's task as soon as the OWNER's lease expires");
        ScheduledTask done = waitForStatus(survivor, "ghost-owned-task", ScheduledTask.TaskStatus.COMPLETED, TEST_TIMEOUT_MS);
        assertEquals(ScheduledTask.TaskStatus.COMPLETED, done.getStatus(),
            "the recovered task must run to completion on the survivor");
    }

    @Test
    public void testAffinityOpenFieldAfterBackupWindowsWhenPrimaryDead() throws Exception {
        SchedulerServiceImpl backup1 = createNode("aff-backup1", true, 10000);
        SchedulerServiceImpl backup2 = createNode("aff-backup2", true, 10000);
        seedActiveNodes("aff-primary-dead", "aff-backup1", "aff-backup2");

        CountDownLatch executed = new CountDownLatch(1);
        AtomicReference<String> executorNode = new AtomicReference<>();

        TaskExecutor executor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return "affinity-failover";
            }

            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) {
                executorNode.set(task.getExecutingNodeId());
                executed.countDown();
                callback.complete();
            }
        };
        backup1.registerTaskExecutor(executor);
        backup2.registerTaskExecutor(executor);

        ScheduledTask task = backup1.newTask("affinity-failover")
            .disallowParallelExecution()
            .asOneShot()
            .schedule();

        assertTrue(executed.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS),
            "A backup node must acquire the task after affinity windows expire (open field)");
        assertTrue(
            "aff-backup1".equals(executorNode.get()) || "aff-backup2".equals(executorNode.get()),
            "Execution must be on a live backup, not the dead primary");

        ScheduledTask done = waitForStatus(backup1, task.getItemId(), ScheduledTask.TaskStatus.COMPLETED, TEST_TIMEOUT_MS);
        assertEquals(ScheduledTask.TaskStatus.COMPLETED, done.getStatus());
    }

    @Test
    public void testDeadNodeMidRetryIsRecoveredByPeer() throws Exception {
        long lockTimeout = SHORT_LOCK_TIMEOUT_MS;
        SchedulerServiceImpl node1 = createNode("retry-node1", true, lockTimeout);
        SchedulerServiceImpl node2 = createNode("retry-node2", true, lockTimeout);
        seedActiveNodes("retry-node1", "retry-node2");

        CountDownLatch retryAttemptStarted = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger(0);

        TaskExecutor executor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return "dead-mid-retry";
            }

            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) throws Exception {
                int n = attempts.incrementAndGet();
                if (n == 1) {
                    callback.fail("first failure");
                    return;
                }
                if (n == 2) {
                    retryAttemptStarted.countDown();
                    Thread.sleep(lockTimeout + 1500);
                    callback.complete();
                    completed.countDown();
                    return;
                }
                callback.complete();
                completed.countDown();
            }
        };
        node1.registerTaskExecutor(executor);
        node2.registerTaskExecutor(executor);

        ScheduledTask task = node1.newTask("dead-mid-retry")
            .disallowParallelExecution()
            .withMaxRetries(3)
            .withRetryDelay(200, TimeUnit.MILLISECONDS)
            .asOneShot()
            .schedule();

        assertTrue(retryAttemptStarted.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS),
            "Retry attempt should start");
        node1.simulateCrash();

        assertTrue(completed.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS),
            "Peer node must complete the task after the owning node dies mid-retry");

        ScheduledTask finalTask = waitForStatus(node2, task.getItemId(), ScheduledTask.TaskStatus.COMPLETED, TEST_TIMEOUT_MS);
        assertNotNull(finalTask);
        assertEquals(ScheduledTask.TaskStatus.COMPLETED, finalTask.getStatus(),
            "Task must reach COMPLETED, not stay stranded in CRASHED/SCHEDULED");
        assertTrue(attempts.get() >= 2, "At least the initial failure and a recovery/retry attempt");
    }

    @Test
    public void testCheckpointResumeIsInvokedNotExecute() throws Exception {
        // Long lock timeout so the peer does not steal the task via lock-expiry recovery
        // while the executor is still inside execute() — that path can restart (SCHEDULED)
        // instead of resume() if it races the checkpoint write.
        long lockTimeoutMs = TEST_TIMEOUT_MS;
        SchedulerServiceImpl node1 = createNode("resume-node1", true, lockTimeoutMs);
        SchedulerServiceImpl node2 = createNode("resume-node2", true, lockTimeoutMs);
        seedActiveNodes("resume-node1", "resume-node2");

        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch holdExecution = new CountDownLatch(1);
        CountDownLatch resumeCalled = new CountDownLatch(1);
        AtomicInteger executeCount = new AtomicInteger(0);
        AtomicInteger resumeCount = new AtomicInteger(0);
        AtomicReference<String> executingNodeId = new AtomicReference<>();

        TaskExecutor executor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return "checkpoint-resume";
            }

            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) throws Exception {
                executeCount.incrementAndGet();
                executingNodeId.set(task.getExecutingNodeId());
                callback.checkpoint(Collections.singletonMap("step", 1));
                firstStarted.countDown();
                // Hold until the test crashes this node (interrupt) or releases the latch.
                holdExecution.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                callback.complete();
            }

            @Override
            public boolean canResume(ScheduledTask task) {
                return task.getCheckpointData() != null;
            }

            @Override
            public void resume(ScheduledTask task, TaskStatusCallback callback) {
                resumeCount.incrementAndGet();
                resumeCalled.countDown();
                callback.complete();
            }
        };
        node1.registerTaskExecutor(executor);
        node2.registerTaskExecutor(executor);

        ScheduledTask task = node1.newTask("checkpoint-resume")
            .disallowParallelExecution()
            .asOneShot()
            .schedule();

        assertTrue(firstStarted.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS), "First attempt should start");
        assertNotNull(executingNodeId.get(), "execute() must record the owning node");

        // Crash the node that holds the in-process dispatch claim. Affinity may pick either
        // node; crashing the peer leaves the claim held and blocks resume dispatch.
        SchedulerServiceImpl crashed = "resume-node1".equals(executingNodeId.get()) ? node1 : node2;
        SchedulerServiceImpl survivor = crashed == node1 ? node2 : node1;
        crashed.simulateCrash();

        // simulateCrash releases the lock; mark lock expired from the survivor's perspective
        // by clearing any residual lock metadata, then drive recoverCrashedTasks().
        ScheduledTask afterCrash = persistenceService.load(task.getItemId(), ScheduledTask.class);
        assertEquals(ScheduledTask.TaskStatus.RUNNING, afterCrash.getStatus(),
            "Task must still be RUNNING after executor crash (not prematurely rescheduled)");
        assertNotNull(afterCrash.getCheckpointData(), "Checkpoint must be persisted before crash");

        long deadline = System.currentTimeMillis() + TEST_TIMEOUT_MS;
        while (resumeCount.get() == 0 && System.currentTimeMillis() < deadline) {
            survivor.recoverCrashedTasks();
            if (resumeCalled.await(200, TimeUnit.MILLISECONDS)) {
                break;
            }
        }

        assertTrue(resumeCount.get() > 0,
            "Recovery must call resume(), not execute(), when canResume is true");
        assertEquals(1, resumeCount.get(), "resume() should be invoked exactly once");
        assertEquals(1, executeCount.get(), "execute() should only have run on the original node");

        ScheduledTask done = waitForStatus(survivor, task.getItemId(), ScheduledTask.TaskStatus.COMPLETED, TEST_TIMEOUT_MS);
        assertEquals(ScheduledTask.TaskStatus.COMPLETED, done.getStatus());
        holdExecution.countDown();
    }

    @Test
    public void testOneShotCrashedMidRetryIsRestartedWhenBudgetRemains() throws Exception {
        SchedulerServiceImpl node = createNode("crash-budget-node", true, SHORT_LOCK_TIMEOUT_MS);

        CountDownLatch completed = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger(0);

        TaskExecutor executor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return "crash-budget";
            }

            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) throws Exception {
                int n = attempts.incrementAndGet();
                if (n == 1) {
                    callback.fail("fail once so lastExecutionDate is set");
                    return;
                }
                if (n == 2) {
                    Thread.sleep(SHORT_LOCK_TIMEOUT_MS + 2500);
                    callback.fail("stalled attempt");
                    return;
                }
                completed.countDown();
                callback.complete();
            }
        };
        node.registerTaskExecutor(executor);

        ScheduledTask task = node.newTask("crash-budget")
            .withMaxRetries(3)
            .withRetryDelay(200, TimeUnit.MILLISECONDS)
            .asOneShot()
            .schedule();

        assertTrue(completed.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS),
            "One-shot with prior failure must be restarted after CRASHED when retry budget remains");
        ScheduledTask done = waitForStatus(node, task.getItemId(), ScheduledTask.TaskStatus.COMPLETED, TEST_TIMEOUT_MS);
        assertEquals(ScheduledTask.TaskStatus.COMPLETED, done.getStatus());
        assertTrue(attempts.get() >= 3, "Expected fail, crashed attempt, then successful restart");
    }

    @Test
    public void testPeriodicFailureCountResetsBetweenPeriods() throws Exception {
        SchedulerServiceImpl node = createNode("period-reset-node", true, 10000);

        CountDownLatch period2Retried = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger(0);
        AtomicInteger period2Attempts = new AtomicInteger(0);

        TaskExecutor executor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return "period-reset";
            }

            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) {
                int n = attempts.incrementAndGet();
                if (n <= 2) {
                    callback.fail("period1 failure #" + n);
                    return;
                }
                int p2 = period2Attempts.incrementAndGet();
                if (p2 == 1) {
                    callback.fail("period2 first failure");
                    return;
                }
                period2Retried.countDown();
                callback.complete();
            }
        };
        node.registerTaskExecutor(executor);

        node.newTask("period-reset")
            .withMaxRetries(1)
            .withRetryDelay(100, TimeUnit.MILLISECONDS)
            .withPeriod(800, TimeUnit.MILLISECONDS)
            .withFixedDelay()
            .schedule();

        assertTrue(period2Retried.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS),
            "Period 2 must get a fresh retry budget after period 1 exhausted retries");
        assertTrue(period2Attempts.get() >= 2, "Period 2 should have retried after first failure");
    }

    @Test
    public void testDependencyBlocksUntilBothCompleteAcrossNodes() throws Exception {
        SchedulerServiceImpl node1 = createNode("dep-node1", true, 10000);
        SchedulerServiceImpl node2 = createNode("dep-node2", true, 10000);
        seedActiveNodes("dep-node1", "dep-node2");

        CountDownLatch dep1Started = new CountDownLatch(1);
        CountDownLatch dep1Release = new CountDownLatch(1);
        CountDownLatch dep2Done = new CountDownLatch(1);
        CountDownLatch dependentDone = new CountDownLatch(1);
        AtomicBoolean dependentRanEarly = new AtomicBoolean(false);

        TaskExecutor executor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return "cross-node-dep";
            }

            @Override
            public void execute(ScheduledTask task, TaskStatusCallback callback) throws Exception {
                String name = String.valueOf(task.getParameters().get("name"));
                if ("dependent".equals(name)) {
                    if (dep1Release.getCount() > 0) {
                        dependentRanEarly.set(true);
                    }
                    dependentDone.countDown();
                    callback.complete();
                    return;
                }
                if ("dep1".equals(name)) {
                    dep1Started.countDown();
                    assertTrue(dep1Release.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
                    callback.complete();
                    return;
                }
                dep2Done.countDown();
                callback.complete();
            }
        };
        node1.registerTaskExecutor(executor);
        node2.registerTaskExecutor(executor);

        ScheduledTask dep1 = node1.newTask("cross-node-dep")
            .withParameters(Collections.singletonMap("name", "dep1"))
            .disallowParallelExecution()
            .asOneShot()
            .schedule();
        assertTrue(dep1Started.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));

        ScheduledTask dep2 = node2.newTask("cross-node-dep")
            .withParameters(Collections.singletonMap("name", "dep2"))
            .asOneShot()
            .schedule();

        ScheduledTask dependent = node1.newTask("cross-node-dep")
            .withParameters(Collections.singletonMap("name", "dependent"))
            .withDependencies(dep1.getItemId(), dep2.getItemId())
            .asOneShot()
            .schedule();

        ScheduledTask waiting = node1.getTask(dependent.getItemId());
        assertEquals(ScheduledTask.TaskStatus.WAITING, waiting.getStatus());
        assertFalse(dependentRanEarly.get());

        dep1Release.countDown();
        assertTrue(dep2Done.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertTrue(dependentDone.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertFalse(dependentRanEarly.get(), "Dependent must not run while dep1 is still held");

        ScheduledTask done = waitForStatus(node1, dependent.getItemId(), ScheduledTask.TaskStatus.COMPLETED, TEST_TIMEOUT_MS);
        assertEquals(ScheduledTask.TaskStatus.COMPLETED, done.getStatus());
    }

    @Test
    public void testCircularDependencyIsRejected() {
        SchedulerServiceImpl node = createNode("cycle-node", true, 10000);

        // Build a↔b entirely before either is scheduled so both edges are present in the
        // validation map (avoids relying on post-hoc mutation of an already-completed task).
        ScheduledTask a = node.createTask("cycle-type", null, 0, 0, TimeUnit.MILLISECONDS,
            true, true, true, true);
        ScheduledTask b = node.createTask("cycle-type", null, 0, 0, TimeUnit.MILLISECONDS,
            true, true, true, true);
        a.setDependsOn(new java.util.HashSet<>(Collections.singleton(b.getItemId())));
        b.setDependsOn(new java.util.HashSet<>(Collections.singleton(a.getItemId())));
        assertTrue(node.saveTask(a));
        assertTrue(node.saveTask(b));

        ScheduledTask aReloaded = node.getTask(a.getItemId());
        assertNotNull(aReloaded.getDependsOn());
        assertTrue(aReloaded.getDependsOn().contains(b.getItemId()),
            "dependsOn must survive persistence round-trip for cycle detection");

        assertThrows(IllegalArgumentException.class, () -> node.scheduleTask(b),
            "Circular dependsOn must be rejected");
    }

    @Test
    public void testDualNodeGetTaskReturnsDistinctInstances() {
        SchedulerServiceImpl node1 = createNode("iso-node1", true, 10000);
        SchedulerServiceImpl node2 = createNode("iso-node2", true, 10000);
        seedActiveNodes("iso-node1", "iso-node2");

        ScheduledTask created = node1.newTask("dual-view-iso")
            .asOneShot()
            .schedule();

        assertDistinctTaskViews(node1, node2, created.getItemId());

        ScheduledTask from1 = node1.getTask(created.getItemId());
        ScheduledTask again1 = node1.getTask(created.getItemId());
        assertNotSame(from1, again1, "Repeated getTask on same node must also deep-copy");
    }

    @Test
    public void testStaleLocalViewUnchangedAfterPeerSave() {
        SchedulerServiceImpl node1 = createNode("stale-node1", true, 10000);
        SchedulerServiceImpl node2 = createNode("stale-node2", true, 10000);
        seedActiveNodes("stale-node1", "stale-node2");

        ScheduledTask created = node1.newTask("stale-view")
            .asOneShot()
            .schedule();

        ScheduledTask stale = node1.getTask(created.getItemId());
        ScheduledTask.TaskStatus before = stale.getStatus();

        ScheduledTask peer = node2.getTask(created.getItemId());
        assertNotSame(stale, peer);
        peer.setCurrentStep("peer-updated");
        peer.setStatus(ScheduledTask.TaskStatus.WAITING);
        assertTrue(node2.saveTask(peer));

        assertEquals(before, stale.getStatus(),
            "Stale local reference must not auto-update when peer persists a different copy");
        assertNull(stale.getCurrentStep(),
            "Stale local reference must not see peer field writes");

        ScheduledTask fresh = node1.getTask(created.getItemId());
        assertNotSame(stale, fresh);
        assertEquals(ScheduledTask.TaskStatus.WAITING, fresh.getStatus());
        assertEquals("peer-updated", fresh.getCurrentStep());
    }

    @Test
    public void testFindTasksByStatusReturnsIsolatedCopiesPerNode() {
        SchedulerServiceImpl node1 = createNode("find-node1", true, 10000);
        SchedulerServiceImpl node2 = createNode("find-node2", true, 10000);
        seedActiveNodes("find-node1", "find-node2");

        ScheduledTask created = node1.newTask("find-iso")
            .asOneShot()
            .schedule();

        List<ScheduledTask> list1 = node1.findTasksByStatus(ScheduledTask.TaskStatus.SCHEDULED);
        List<ScheduledTask> list2 = node2.findTasksByStatus(ScheduledTask.TaskStatus.SCHEDULED);
        ScheduledTask t1 = list1.stream().filter(t -> created.getItemId().equals(t.getItemId())).findFirst().orElse(null);
        ScheduledTask t2 = list2.stream().filter(t -> created.getItemId().equals(t.getItemId())).findFirst().orElse(null);
        assertNotNull(t1);
        assertNotNull(t2);
        assertNotSame(t1, t2, "findTasksByStatus must deep-copy per node/query");

        t1.setStatus(ScheduledTask.TaskStatus.RUNNING);
        List<ScheduledTask> list2Again = node2.findTasksByStatus(ScheduledTask.TaskStatus.SCHEDULED);
        ScheduledTask t2Again = list2Again.stream()
            .filter(t -> created.getItemId().equals(t.getItemId())).findFirst().orElse(null);
        assertNotNull(t2Again);
        assertEquals(ScheduledTask.TaskStatus.SCHEDULED, t2Again.getStatus(),
            "Mutating one node's query result must not alter the store or the peer's view");
    }

    @Test
    public void testNestedMapsIsolatedAcrossNodesDuringCheckpoint() throws Exception {
        SchedulerServiceImpl node1 = createNode("nest-node1", true, TEST_TIMEOUT_MS);
        SchedulerServiceImpl node2 = createNode("nest-node2", true, TEST_TIMEOUT_MS);
        seedActiveNodes("nest-node1", "nest-node2");

        CountDownLatch checkpointed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        TaskExecutor executor = new TaskExecutor() {
            @Override public String getTaskType() { return "nest-iso"; }
            @Override public void execute(ScheduledTask task, TaskStatusCallback callback) throws Exception {
                Map<String, Object> cp = new HashMap<>();
                cp.put("step", 7);
                cp.put("payload", "alpha");
                callback.checkpoint(cp);
                checkpointed.countDown();
                assertTrue(release.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
                callback.complete();
            }
        };
        registerOnAll(executor, node1, node2);

        ScheduledTask task = node1.newTask("nest-iso")
            .disallowParallelExecution()
            .asOneShot()
            .schedule();

        assertTrue(checkpointed.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertDistinctTaskViews(node1, node2, task.getItemId());

        ScheduledTask view1 = node1.getTask(task.getItemId());
        ScheduledTask view2 = node2.getTask(task.getItemId());
        assertNotNull(view1.getCheckpointData());
        assertNotSame(view1.getCheckpointData(), view2.getCheckpointData());
        view1.getCheckpointData().put("payload", "mutated-locally");
        ScheduledTask view2Fresh = node2.getTask(task.getItemId());
        assertEquals("alpha", view2Fresh.getCheckpointData().get("payload"),
            "Peer must not observe nested-map mutations on another node's copy");

        release.countDown();
        ScheduledTask done = waitForStatus(node1, task.getItemId(), ScheduledTask.TaskStatus.COMPLETED, TEST_TIMEOUT_MS);
        assertEquals(ScheduledTask.TaskStatus.COMPLETED, done.getStatus());
    }

    @Test
    public void testDualSurvivorRecoverDoesNotDoubleResume() throws Exception {
        long lockTimeout = SHORT_LOCK_TIMEOUT_MS;
        SchedulerServiceImpl victim = createNode("dual-rec-victim", true, lockTimeout);
        SchedulerServiceImpl survivor1 = createNode("dual-rec-s1", true, lockTimeout);
        SchedulerServiceImpl survivor2 = createNode("dual-rec-s2", true, lockTimeout);
        seedActiveNodes("dual-rec-victim", "dual-rec-s1", "dual-rec-s2");

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch hold = new CountDownLatch(1);
        AtomicInteger executeCount = new AtomicInteger();
        AtomicInteger resumeCount = new AtomicInteger();
        AtomicReference<String> owner = new AtomicReference<>();

        TaskExecutor executor = new TaskExecutor() {
            @Override public String getTaskType() { return "dual-recover"; }
            @Override public void execute(ScheduledTask task, TaskStatusCallback callback) throws Exception {
                executeCount.incrementAndGet();
                owner.set(task.getExecutingNodeId());
                callback.checkpoint(Collections.singletonMap("step", 1));
                started.countDown();
                hold.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                callback.complete();
            }
            @Override public boolean canResume(ScheduledTask task) {
                return task.getCheckpointData() != null;
            }
            @Override public void resume(ScheduledTask task, TaskStatusCallback callback) {
                resumeCount.incrementAndGet();
                callback.complete();
            }
        };
        registerOnAll(executor, victim, survivor1, survivor2);

        ScheduledTask task = victim.newTask("dual-recover")
            .disallowParallelExecution()
            .asOneShot()
            .schedule();

        assertTrue(started.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertNotNull(owner.get());

        SchedulerServiceImpl crashed =
            "dual-rec-victim".equals(owner.get()) ? victim
                : ("dual-rec-s1".equals(owner.get()) ? survivor1 : survivor2);
        List<SchedulerServiceImpl> survivors = new ArrayList<>();
        for (SchedulerServiceImpl n : new SchedulerServiceImpl[]{victim, survivor1, survivor2}) {
            if (n != crashed) {
                survivors.add(n);
            }
        }
        crashed.simulateCrash();

        // Ensure lock is expired so both survivors' recover passes see it as crashed work.
        expireLockInStore(task.getItemId());

        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(survivors.size());
        for (SchedulerServiceImpl s : survivors) {
            Thread t = new Thread(() -> {
                try {
                    go.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    s.recoverCrashedTasks();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "recover-" + s.getNodeId());
            t.setDaemon(true);
            t.start();
        }
        go.countDown();
        assertTrue(done.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));

        // Drive a few more recover passes in case the first concurrent pass lost the OCC race
        long deadline = System.currentTimeMillis() + TEST_TIMEOUT_MS;
        while (resumeCount.get() == 0 && System.currentTimeMillis() < deadline) {
            for (SchedulerServiceImpl s : survivors) {
                s.recoverCrashedTasks();
            }
            Thread.sleep(100);
        }

        assertEquals(1, executeCount.get(), "Only the original node should have called execute()");
        assertEquals(1, resumeCount.get(), "Exactly one survivor may resume after dual recover");
        ScheduledTask completed = waitForStatus(survivors.get(0), task.getItemId(),
            ScheduledTask.TaskStatus.COMPLETED, TEST_TIMEOUT_MS);
        assertEquals(ScheduledTask.TaskStatus.COMPLETED, completed.getStatus());
        assertDistinctTaskViews(survivors.get(0), survivors.get(1), task.getItemId());
        hold.countDown();
    }

    @Test
    public void testRunOnAllNodesExecutesOnEveryNodeWithIsolatedViews() throws Exception {
        SchedulerServiceImpl node1 = createNode("all-node1", true, 10000);
        SchedulerServiceImpl node2 = createNode("all-node2", true, 10000);
        SchedulerServiceImpl node3 = createNode("all-node3", false, 10000);
        seedActiveNodes("all-node1", "all-node2", "all-node3");

        CountDownLatch allRan = new CountDownLatch(1);
        Set<String> nodesSeen = ConcurrentHashMap.newKeySet();
        Set<Integer> identityHashes = ConcurrentHashMap.newKeySet();

        TaskExecutor executor = new TaskExecutor() {
            @Override public String getTaskType() { return "run-all-iso"; }
            @Override public void execute(ScheduledTask task, TaskStatusCallback callback) {
                nodesSeen.add(task.getExecutingNodeId());
                identityHashes.add(System.identityHashCode(task));
                if (nodesSeen.size() >= 3) {
                    allRan.countDown();
                }
                callback.complete();
            }
        };
        registerOnAll(executor, node1, node2, node3);

        ScheduledTask task = node1.newTask("run-all-iso")
            .runOnAllNodes()
            .withPeriod(200, TimeUnit.MILLISECONDS)
            .schedule();

        assertTrue(allRan.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS),
            "runOnAllNodes must execute on every node including non-executors");
        assertTrue(nodesSeen.contains("all-node1"));
        assertTrue(nodesSeen.contains("all-node2"));
        assertTrue(nodesSeen.contains("all-node3"));
        assertTrue(identityHashes.size() >= 3,
            "Each node execution must use a distinct deep-copied task instance, got "
                + identityHashes.size());

        // Parallel-execution tasks give each node an independent, uncoordinated status write
        // (prepareForExecution()'s RUNNING transition is a blind, non-CAS save for parallel
        // tasks by design). With a 200ms period, reading status right after the latch fires can
        // race an in-flight node's RUNNING->SCHEDULED transition. Cancel on every node first -
        // idempotent and terminal - so all nodes converge on the same shared-store status before
        // the isolation assertions below, which only care about per-node deep-copy isolation.
        node1.cancelTask(task.getItemId());
        node2.cancelTask(task.getItemId());
        node3.cancelTask(task.getItemId());

        assertDistinctTaskViews(node1, node2, task.getItemId());
        assertDistinctTaskViews(node2, node3, task.getItemId());
    }

    @Test
    public void testLockStealAfterExpiryUsesOccAndSingleWinner() throws Exception {
        long lockTimeout = SHORT_LOCK_TIMEOUT_MS;
        SchedulerServiceImpl holder = createNode("steal-holder", true, lockTimeout);
        SchedulerServiceImpl peer = createNode("steal-peer", true, lockTimeout);
        seedActiveNodes("steal-holder", "steal-peer");

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        Set<String> executors = ConcurrentHashMap.newKeySet();

        TaskExecutor executor = new TaskExecutor() {
            @Override public String getTaskType() { return "lock-steal"; }
            @Override public void execute(ScheduledTask task, TaskStatusCallback callback) throws Exception {
                executions.incrementAndGet();
                executors.add(task.getExecutingNodeId());
                started.countDown();
                assertTrue(release.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
                callback.complete();
            }
        };
        registerOnAll(executor, holder, peer);

        ScheduledTask task = holder.newTask("lock-steal")
            .disallowParallelExecution()
            .asOneShot()
            .schedule();

        assertTrue(started.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        ScheduledTask lockedView = peer.getTask(task.getItemId());
        assertNotNull(lockedView.getLockOwner());
        Object staleSeq = lockedView.getSystemMetadata("seq_no");
        assertNotNull(staleSeq);

        // Expire lock while holder is still in execute(); peer recovery/checker may steal.
        expireLockInStore(task.getItemId());
        Thread.sleep(lockTimeout + 200);
        peer.recoverCrashedTasks();

        // Stale CAS from the pre-steal peer view must lose once the store moved forward.
        ScheduledTask staleWrite = peer.getTask(task.getItemId());
        // Re-load a snapshot taken conceptually before steal: reuse staleSeq on a fresh deep copy
        ScheduledTask casAttempt = persistenceService.load(task.getItemId(), ScheduledTask.class);
        Object currentSeq = casAttempt.getSystemMetadata("seq_no");
        // Force a stale seq from the earlier view
        casAttempt.setSystemMetadata("seq_no", staleSeq);
        casAttempt.setSystemMetadata("_seq_no", staleSeq);
        casAttempt.setLockOwner("steal-peer-stale");
        if (!staleSeq.equals(currentSeq)) {
            assertFalse(persistenceService.save(casAttempt, false, false),
                "OCC must reject stale seq_no after peer advanced the document");
        }

        release.countDown();
        // Regardless of reclaim/steal outcome, only one logical completion and distinct views.
        long deadline = System.currentTimeMillis() + TEST_TIMEOUT_MS;
        ScheduledTask finalTask = null;
        while (System.currentTimeMillis() < deadline) {
            finalTask = holder.getTask(task.getItemId());
            ScheduledTask peerView = peer.getTask(task.getItemId());
            assertNotSame(finalTask, peerView);
            if (finalTask != null && (
                ScheduledTask.TaskStatus.COMPLETED.equals(finalTask.getStatus())
                    || ScheduledTask.TaskStatus.CRASHED.equals(finalTask.getStatus())
                    || ScheduledTask.TaskStatus.SCHEDULED.equals(finalTask.getStatus())
                    || ScheduledTask.TaskStatus.FAILED.equals(finalTask.getStatus()))) {
                // Keep waiting for COMPLETED when reclaim path wins
                if (ScheduledTask.TaskStatus.COMPLETED.equals(finalTask.getStatus())) {
                    break;
                }
            }
            Thread.sleep(50);
        }
        finalTask = waitForStatus(holder, task.getItemId(), ScheduledTask.TaskStatus.COMPLETED, TEST_TIMEOUT_MS);
        assertEquals(ScheduledTask.TaskStatus.COMPLETED, finalTask.getStatus());
        assertEquals(1, executors.size(), "Exactly one node should own execution under exclusive lock");
        assertDistinctTaskViews(holder, peer, task.getItemId());
    }

    @Test
    public void testDualCheckersExclusiveTaskSingleExecutionWithDistinctViews() throws Exception {
        SchedulerServiceImpl node1 = createNode("chk-node1", true, 10000);
        SchedulerServiceImpl node2 = createNode("chk-node2", true, 10000);
        seedActiveNodes("chk-node1", "chk-node2");

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        Set<Integer> identityHashes = ConcurrentHashMap.newKeySet();

        TaskExecutor executor = new TaskExecutor() {
            @Override public String getTaskType() { return "dual-checker"; }
            @Override public void execute(ScheduledTask task, TaskStatusCallback callback) throws Exception {
                executions.incrementAndGet();
                identityHashes.add(System.identityHashCode(task));
                started.countDown();
                // While held, both nodes' checkers keep polling — sample identity isolation.
                assertTrue(release.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
                callback.complete();
            }
        };
        registerOnAll(executor, node1, node2);

        ScheduledTask task = node1.newTask("dual-checker")
            .disallowParallelExecution()
            .asOneShot()
            .schedule();

        assertTrue(started.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        // Contended window: both nodes repeatedly load distinct copies
        for (int i = 0; i < 20; i++) {
            assertDistinctTaskViews(node1, node2, task.getItemId());
            Thread.sleep(25);
        }
        release.countDown();

        ScheduledTask done = waitForStatus(node1, task.getItemId(), ScheduledTask.TaskStatus.COMPLETED, TEST_TIMEOUT_MS);
        assertEquals(ScheduledTask.TaskStatus.COMPLETED, done.getStatus());
        assertEquals(1, executions.get(), "Dual checkers must not double-execute exclusive task");
        assertEquals(1, identityHashes.size(), "Winning execution uses one in-process instance");
        assertDistinctTaskViews(node1, node2, task.getItemId());
    }

    @Test
    public void testConcurrentShutdownRacingPeerRecoveryDoesNotDoubleDispatch() throws Exception {
        // Gap flagged in the UNOMI-967 PR review: existing crash-recovery tests trigger
        // recovery *after* a crash has already happened (simulateCrash() then recover()).
        // This drives victim.preDestroy() (which marks RUNNING work CRASHED and clears its
        // own lock) concurrently with peer.recoverCrashedTasks() racing for the same
        // still-locked document, exercising the shutdown-vs-recovery interleaving directly.
        long lockTimeout = SHORT_LOCK_TIMEOUT_MS;
        SchedulerServiceImpl victim = createNode("shutdown-race-victim", true, lockTimeout);
        SchedulerServiceImpl peer = createNode("shutdown-race-peer", true, lockTimeout);
        seedActiveNodes("shutdown-race-victim", "shutdown-race-peer");

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger executeCount = new AtomicInteger();
        AtomicInteger resumeCount = new AtomicInteger();

        TaskExecutor executor = new TaskExecutor() {
            @Override public String getTaskType() { return "shutdown-race"; }
            @Override public void execute(ScheduledTask task, TaskStatusCallback callback) throws Exception {
                executeCount.incrementAndGet();
                callback.checkpoint(Collections.singletonMap("step", 1));
                started.countDown();
                release.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                callback.complete();
            }
            @Override public boolean canResume(ScheduledTask task) {
                return task.getCheckpointData() != null;
            }
            @Override public void resume(ScheduledTask task, TaskStatusCallback callback) {
                resumeCount.incrementAndGet();
                callback.complete();
            }
        };
        registerOnAll(executor, victim, peer);

        ScheduledTask task = victim.newTask("shutdown-race")
            .disallowParallelExecution()
            .asOneShot()
            .schedule();

        assertTrue(started.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        // Age the lock past lockTimeout so peer's recovery pass considers it eligible,
        // at the same moment we drive the victim's own shutdown-triggered crash-mark.
        expireLockInStore(task.getItemId());

        AtomicReference<Throwable> victimError = new AtomicReference<>();
        AtomicReference<Throwable> peerError = new AtomicReference<>();
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        Thread shutdownThread = new Thread(() -> {
            try {
                go.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                victim.preDestroy();
            } catch (Throwable t) {
                victimError.set(t);
            } finally {
                done.countDown();
            }
        }, "shutdown-race-victim-thread");
        Thread recoveryThread = new Thread(() -> {
            try {
                go.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                peer.recoverCrashedTasks();
            } catch (Throwable t) {
                peerError.set(t);
            } finally {
                done.countDown();
            }
        }, "shutdown-race-peer-thread");
        shutdownThread.start();
        recoveryThread.start();
        go.countDown();
        assertTrue(done.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        release.countDown();

        assertNull(victimError.get(), "victim.preDestroy() must not throw when racing peer recovery");
        assertNull(peerError.get(), "peer.recoverCrashedTasks() must not throw when racing victim shutdown");

        // Drive a few more recover passes in case the first concurrent pass lost the OCC race
        // (victim's own shutdown save and peer's recovery save both target the same document).
        long deadline = System.currentTimeMillis() + TEST_TIMEOUT_MS;
        while (resumeCount.get() == 0 && System.currentTimeMillis() < deadline) {
            peer.recoverCrashedTasks();
            Thread.sleep(100);
        }

        assertEquals(1, executeCount.get(),
            "Concurrent shutdown + peer recovery must not cause a second independent execute()");
        assertTrue(resumeCount.get() <= 1,
            "Concurrent shutdown + peer recovery must not double-resume the same crashed work");
    }
}
