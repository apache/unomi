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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TaskLockManager}.
 * Invoked by Surefire: {@code -Dtest=TaskLockManagerTest}. No data-file I/O.
 * Glob: existing TaskLockManagerTest. User: edge/breakage case coverage.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TaskLockManagerTest {

    private static final String NODE = "lock-node";

    @Mock
    private SchedulerServiceImpl schedulerService;

    private TaskMetricsManager metricsManager;
    private TaskLockManager lockManager;

    @BeforeEach
    public void setUp() {
        metricsManager = new TaskMetricsManager();
        lockManager = new TaskLockManager();
        lockManager.setNodeId(NODE);
        lockManager.setLockTimeout(1000);
        lockManager.setMetricsManager(metricsManager);
        lockManager.setSchedulerService(schedulerService);
        when(schedulerService.getActiveNodes()).thenReturn(Collections.singletonList(NODE));
        when(schedulerService.saveTask(any(ScheduledTask.class))).thenReturn(true);
        when(schedulerService.saveTask(any(ScheduledTask.class), anyBoolean())).thenReturn(true);
        when(schedulerService.saveTaskWithRefresh(any(ScheduledTask.class))).thenReturn(true);
        when(schedulerService.saveTaskWithRefresh(any(ScheduledTask.class), anyBoolean())).thenReturn(true);
    }

    @Test
    public void testNullTaskGuards() {
        assertFalse(lockManager.acquireLock(null));
        assertFalse(lockManager.releaseLock(null));
        assertTrue(lockManager.isLockExpired(null));
    }

    @Test
    public void testAllowParallelAlwaysAcquiresMarker() {
        ScheduledTask task = TaskTestFixtures.baseTask("parallel");
        task.setAllowParallelExecution(true);
        assertTrue(lockManager.acquireLock(task));
        assertEquals(NODE, task.getLockOwner());
        assertNotNull(task.getLockDate());
        verify(schedulerService, never()).saveTaskWithRefresh(any());
        assertEquals(1, metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_LOCK_ACQUIRED));
    }

    @Test
    public void testInMemoryLockRejectsOtherValidOwner() {
        ScheduledTask task = TaskTestFixtures.baseTask("mem");
        task.setPersistent(false);
        task.setLockOwner("other");
        task.setLockDate(new Date());
        assertFalse(lockManager.acquireLock(task));
    }

    @Test
    public void testInMemoryLockAllowsExpiredOwner() {
        ScheduledTask task = TaskTestFixtures.baseTask("mem");
        task.setPersistent(false);
        task.setLockOwner("other");
        task.setLockDate(new Date(System.currentTimeMillis() - 5000));
        assertTrue(lockManager.acquireLock(task));
        assertEquals(NODE, task.getLockOwner());
        verify(schedulerService).saveTask(task);
    }

    @Test
    public void testDistributedLockOccConflict() {
        ScheduledTask task = TaskTestFixtures.baseTask("dist");
        task.setNextScheduledExecution(new Date(System.currentTimeMillis() - 10_000));
        task.setSystemMetadata("seq_no", 1L);
        task.setSystemMetadata("primary_term", 1L);
        when(schedulerService.getTask(task.getItemId())).thenReturn(task);
        when(schedulerService.saveTaskWithRefresh(any(ScheduledTask.class))).thenReturn(false);

        assertFalse(lockManager.acquireLock(task));
        assertEquals(1, metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_LOCK_CONFLICTS));
    }

    @Test
    public void testDistributedLockSuccess() {
        ScheduledTask task = TaskTestFixtures.baseTask("dist");
        task.setNextScheduledExecution(new Date(System.currentTimeMillis() - 10_000));
        task.setSystemMetadata("seq_no", 3L);
        task.setSystemMetadata("primary_term", 1L);
        when(schedulerService.getTask(task.getItemId())).thenReturn(task);
        when(schedulerService.saveTaskWithRefresh(any(ScheduledTask.class))).thenAnswer(inv -> {
            ScheduledTask t = inv.getArgument(0);
            t.setLockOwner(NODE);
            return true;
        });
        // After verification delay, return same owner + matching lockVersion
        when(schedulerService.getTask(eq(task.getItemId()))).thenAnswer(inv -> {
            ScheduledTask copy = TaskTestFixtures.baseTask("dist");
            copy.setItemId(task.getItemId());
            copy.setLockOwner(NODE);
            copy.setLockDate(new Date());
            Object ver = task.getSystemMetadata("lockVersion");
            if (ver != null) {
                copy.setSystemMetadata("lockVersion", ver);
            } else {
                copy.setSystemMetadata("lockVersion", 1L);
            }
            copy.setSystemMetadata("seq_no", 3L);
            copy.setSystemMetadata("primary_term", 1L);
            return copy;
        });

        // First getTask in acquire returns original; verification getTask returns answer above.
        // Need sequential stubbing:
        ScheduledTask latest = TaskTestFixtures.baseTask("dist");
        latest.setItemId(task.getItemId());
        latest.setNextScheduledExecution(task.getNextScheduledExecution());
        latest.setSystemMetadata("seq_no", 3L);
        latest.setSystemMetadata("primary_term", 1L);
        ScheduledTask verified = TaskTestFixtures.baseTask("dist");
        verified.setItemId(task.getItemId());
        verified.setLockOwner(NODE);
        verified.setLockDate(new Date());
        verified.setSystemMetadata("lockVersion", 1L);

        when(schedulerService.getTask(task.getItemId())).thenReturn(latest, verified);
        when(schedulerService.saveTaskWithRefresh(any(ScheduledTask.class))).thenAnswer(inv -> {
            ScheduledTask t = inv.getArgument(0);
            verified.setSystemMetadata("lockVersion", t.getSystemMetadata("lockVersion"));
            return true;
        });

        assertTrue(lockManager.acquireLock(task));
        assertEquals(NODE, task.getLockOwner());
        assertEquals(1, metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_LOCK_ACQUIRED));
    }

    @Test
    public void testCrashedTaskBypassesAffinity() {
        ScheduledTask task = TaskTestFixtures.baseTask("crash");
        task.setStatus(ScheduledTask.TaskStatus.CRASHED);
        task.setNextScheduledExecution(new Date()); // would otherwise be in primary window for other node
        when(schedulerService.getActiveNodes()).thenReturn(Arrays.asList("other-node", NODE));
        ScheduledTask latest = TaskTestFixtures.baseTask("crash");
        latest.setItemId(task.getItemId());
        latest.setStatus(ScheduledTask.TaskStatus.CRASHED);
        latest.setNextScheduledExecution(task.getNextScheduledExecution());
        latest.setSystemMetadata("seq_no", 1L);
        latest.setSystemMetadata("primary_term", 1L);
        ScheduledTask verified = TaskTestFixtures.baseTask("crash");
        verified.setItemId(task.getItemId());
        verified.setLockOwner(NODE);
        verified.setLockDate(new Date());
        verified.setSystemMetadata("lockVersion", 1L);
        when(schedulerService.getTask(task.getItemId())).thenReturn(latest, verified);
        when(schedulerService.saveTaskWithRefresh(any(ScheduledTask.class))).thenAnswer(inv -> {
            ScheduledTask t = inv.getArgument(0);
            verified.setSystemMetadata("lockVersion", t.getSystemMetadata("lockVersion"));
            return true;
        });
        assertTrue(lockManager.acquireLock(task));
    }

    @Test
    public void testReleaseLockRejectsNonOwnerValidLock() {
        ScheduledTask task = TaskTestFixtures.baseTask("rel");
        task.setLockOwner("other");
        task.setLockDate(new Date());
        assertFalse(lockManager.releaseLock(task));
    }

    @Test
    public void testReleaseLockClearsExpiredUsingFreshLoad() {
        ScheduledTask caller = TaskTestFixtures.baseTask("rel");
        caller.setStatus(ScheduledTask.TaskStatus.SCHEDULED); // mutated retry view
        caller.setLockOwner("other");
        caller.setLockDate(new Date(System.currentTimeMillis() - 5000));

        ScheduledTask stored = TaskTestFixtures.runningTask("rel", "other");
        stored.setItemId(caller.getItemId());
        when(schedulerService.getTask(eq(caller.getItemId()), eq(true))).thenReturn(stored);

        assertTrue(lockManager.releaseLock(caller));
        assertNull(stored.getLockOwner());
        assertEquals(ScheduledTask.TaskStatus.RUNNING, stored.getStatus());
        verify(schedulerService).saveTaskWithRefresh(stored, true);
        assertEquals(1, metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_LOCK_RELEASED));
    }

    @Test
    public void testIsLockExpired() {
        ScheduledTask task = TaskTestFixtures.baseTask("exp");
        assertTrue(lockManager.isLockExpired(task));
        task.setLockDate(new Date());
        assertFalse(lockManager.isLockExpired(task));
        task.setLockDate(new Date(System.currentTimeMillis() - 5000));
        assertTrue(lockManager.isLockExpired(task));
    }

    @Test
    public void testIsLockExpiredFalseWhenAgeEqualsTimeout() {
        ScheduledTask task = TaskTestFixtures.baseTask("exp");
        task.setLockDate(new Date(System.currentTimeMillis() - 1000));
        assertFalse(lockManager.isLockExpired(task));
    }

    @Test
    public void testAffinityBlocksBackupDuringPrimaryWindow() {
        List<String> nodes = Arrays.asList("aaa-node", NODE, "zzz-node");
        when(schedulerService.getActiveNodes()).thenReturn(nodes);
        String itemId = TaskTestFixtures.itemIdNotPrimaryFor(NODE, nodes);
        ScheduledTask task = TaskTestFixtures.baseTask("aff");
        task.setItemId(itemId);
        task.setNextScheduledExecution(new Date(System.currentTimeMillis() - 500));

        assertFalse(lockManager.acquireLock(task));
        verify(schedulerService, never()).saveTaskWithRefresh(any());
    }

    @Test
    public void testAffinityAllowsBackupInStaggeredWindow() {
        List<String> nodes = Arrays.asList("aaa-node", NODE, "zzz-node");
        when(schedulerService.getActiveNodes()).thenReturn(nodes);
        String itemId = TaskTestFixtures.itemIdNotPrimaryFor(NODE, nodes);
        int backupOrder = TaskTestFixtures.backupOrderFor(itemId, NODE, nodes);
        assertTrue(backupOrder >= 1);

        long delayMs = 3000 + ((backupOrder - 1) * 500L) + 50;
        ScheduledTask task = TaskTestFixtures.baseTask("aff");
        task.setItemId(itemId);
        task.setNextScheduledExecution(new Date(System.currentTimeMillis() - delayMs));
        stubSuccessfulDistributedAcquire(task);

        assertTrue(lockManager.acquireLock(task));
    }

    @Test
    public void testAffinityOpenFieldAfterAllBackupWindows() {
        List<String> nodes = Arrays.asList("aaa-node", NODE, "zzz-node");
        when(schedulerService.getActiveNodes()).thenReturn(nodes);
        String itemId = TaskTestFixtures.itemIdNotPrimaryFor(NODE, nodes);
        // openFieldStart = 3000 + 2*500 = 4000
        ScheduledTask task = TaskTestFixtures.baseTask("aff");
        task.setItemId(itemId);
        task.setNextScheduledExecution(new Date(System.currentTimeMillis() - 4500));
        stubSuccessfulDistributedAcquire(task);

        assertTrue(lockManager.acquireLock(task));
    }

    @Test
    public void testAffinityRejectsNodeNotInActiveList() {
        List<String> nodes = Arrays.asList("aaa-node", "bbb-node", "ccc-node");
        when(schedulerService.getActiveNodes()).thenReturn(nodes);
        ScheduledTask task = TaskTestFixtures.baseTask("aff");
        task.setItemId("affinity-0");
        task.setNextScheduledExecution(new Date(System.currentTimeMillis() - 3500));

        assertFalse(lockManager.acquireLock(task));
        verify(schedulerService, never()).saveTaskWithRefresh(any());
    }

    @Test
    public void testDistributedLockRejectsValidForeignOwner() {
        ScheduledTask task = TaskTestFixtures.baseTask("dist");
        task.setNextScheduledExecution(new Date(System.currentTimeMillis() - 10_000));
        ScheduledTask latest = TaskTestFixtures.baseTask("dist");
        latest.setItemId(task.getItemId());
        latest.setLockOwner("other");
        latest.setLockDate(new Date());
        latest.setSystemMetadata("seq_no", 1L);
        latest.setSystemMetadata("primary_term", 1L);
        when(schedulerService.getTask(task.getItemId())).thenReturn(latest);

        assertFalse(lockManager.acquireLock(task));
        verify(schedulerService, never()).saveTaskWithRefresh(any());
    }

    @Test
    public void testDistributedLockFailsWhenTaskMissing() {
        ScheduledTask task = TaskTestFixtures.baseTask("dist");
        task.setNextScheduledExecution(new Date(System.currentTimeMillis() - 10_000));
        when(schedulerService.getTask(task.getItemId())).thenReturn(null);

        assertFalse(lockManager.acquireLock(task));
        verify(schedulerService, never()).saveTaskWithRefresh(any());
    }

    @Test
    public void testDistributedLockVerificationFailureIncrementsConflict() {
        ScheduledTask task = TaskTestFixtures.baseTask("dist");
        task.setNextScheduledExecution(new Date(System.currentTimeMillis() - 10_000));
        ScheduledTask latest = TaskTestFixtures.baseTask("dist");
        latest.setItemId(task.getItemId());
        latest.setNextScheduledExecution(task.getNextScheduledExecution());
        latest.setSystemMetadata("seq_no", 2L);
        latest.setSystemMetadata("primary_term", 1L);
        ScheduledTask stolen = TaskTestFixtures.baseTask("dist");
        stolen.setItemId(task.getItemId());
        stolen.setLockOwner("thief");
        stolen.setLockDate(new Date());
        stolen.setSystemMetadata("lockVersion", 99L);
        when(schedulerService.getTask(task.getItemId())).thenReturn(latest, stolen);
        when(schedulerService.saveTaskWithRefresh(any(ScheduledTask.class))).thenReturn(true);

        assertFalse(lockManager.acquireLock(task));
        assertEquals(1, metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_LOCK_CONFLICTS));
    }

    @Test
    public void testDistributedLockInterruptedDuringVerificationReleases() throws Exception {
        ScheduledTask task = TaskTestFixtures.baseTask("dist");
        task.setNextScheduledExecution(new Date(System.currentTimeMillis() - 10_000));
        ScheduledTask latest = TaskTestFixtures.baseTask("dist");
        latest.setItemId(task.getItemId());
        latest.setNextScheduledExecution(task.getNextScheduledExecution());
        latest.setSystemMetadata("seq_no", 1L);
        latest.setSystemMetadata("primary_term", 1L);
        when(schedulerService.getTask(task.getItemId())).thenReturn(latest);
        when(schedulerService.saveTaskWithRefresh(any(ScheduledTask.class))).thenReturn(true);
        when(schedulerService.getTask(eq(task.getItemId()), eq(true))).thenReturn(task);

        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean acquired = new AtomicBoolean(true);
        Thread t = new Thread(() -> {
            started.countDown();
            acquired.set(lockManager.acquireLock(task));
        }, "lock-interrupt-test");
        t.start();
        assertTrue(started.await(2, TimeUnit.SECONDS));
        // Interrupt during VERIFICATION_DELAY_MS sleep
        Thread.sleep(20);
        t.interrupt();
        t.join(2000);
        assertFalse(acquired.get());
        verify(schedulerService, atLeastOnce()).saveTaskWithRefresh(any(ScheduledTask.class), eq(true));
    }

    @Test
    public void testReleaseLockReturnsFalseWhenPersistFails() {
        ScheduledTask task = TaskTestFixtures.runningTask("rel", NODE);
        when(schedulerService.getTask(eq(task.getItemId()), eq(true))).thenReturn(task);
        when(schedulerService.saveTaskWithRefresh(any(ScheduledTask.class), eq(true))).thenReturn(false);

        assertFalse(lockManager.releaseLock(task));
        assertEquals(0, metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_LOCK_RELEASED));
    }


    @Test
    public void testReleaseLockDoesNotClearPeersFreshLock() {
        // Caller still thinks the dead owner's expired lock is clearable...
        ScheduledTask caller = TaskTestFixtures.baseTask("steal");
        caller.setLockOwner("dead-node");
        caller.setLockDate(new Date(System.currentTimeMillis() - 5000));

        // ...but the store already has a fresh lock held by a peer stealer.
        ScheduledTask peerHeld = TaskTestFixtures.runningTask("steal", "peer-node");
        peerHeld.setItemId(caller.getItemId());
        peerHeld.setLockDate(new Date());
        when(schedulerService.getTask(eq(caller.getItemId()), eq(true))).thenReturn(peerHeld);

        assertFalse(lockManager.releaseLock(caller));
        assertEquals("peer-node", peerHeld.getLockOwner());
        verify(schedulerService, never()).saveTaskWithRefresh(any(ScheduledTask.class), eq(true));
        assertEquals(0, metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_LOCK_RELEASED));
    }


    @Test
    public void testDistributedLockVerifyFalseNegativeReleasesWhenStillOwned() {
        ScheduledTask task = TaskTestFixtures.baseTask("verify-miss");
        task.setNextScheduledExecution(new Date(System.currentTimeMillis() - 10_000));
        ScheduledTask latest = TaskTestFixtures.baseTask("verify-miss");
        latest.setItemId(task.getItemId());
        latest.setNextScheduledExecution(task.getNextScheduledExecution());
        latest.setSystemMetadata("seq_no", 1L);
        latest.setSystemMetadata("primary_term", 1L);
        // After CAS, verification GETs return null (visibility miss / disappear) then still-owned
        when(schedulerService.getTask(task.getItemId())).thenReturn(latest, null, null);
        when(schedulerService.saveTaskWithRefresh(any(ScheduledTask.class))).thenReturn(true);
        when(schedulerService.getTask(eq(task.getItemId()), eq(true))).thenAnswer(inv -> {
            ScheduledTask owned = TaskTestFixtures.baseTask("verify-miss");
            owned.setItemId(task.getItemId());
            owned.setLockOwner(NODE);
            owned.setLockDate(new Date());
            owned.setSystemMetadata("lockVersion", task.getSystemMetadata("lockVersion"));
            return owned;
        });

        assertFalse(lockManager.acquireLock(task));
        verify(schedulerService, atLeastOnce()).saveTaskWithRefresh(any(ScheduledTask.class), eq(true));
        assertEquals(1, metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_LOCK_CONFLICTS));
    }

    @Test
    public void testDistributedLockVerifyRecheckSucceedsAfterTransientMiss() {
        ScheduledTask task = TaskTestFixtures.baseTask("verify-retry");
        task.setNextScheduledExecution(new Date(System.currentTimeMillis() - 10_000));
        ScheduledTask latest = TaskTestFixtures.baseTask("verify-retry");
        latest.setItemId(task.getItemId());
        latest.setNextScheduledExecution(task.getNextScheduledExecution());
        latest.setSystemMetadata("seq_no", 2L);
        latest.setSystemMetadata("primary_term", 1L);
        ScheduledTask verified = TaskTestFixtures.baseTask("verify-retry");
        verified.setItemId(task.getItemId());
        verified.setLockOwner(NODE);
        verified.setLockDate(new Date());
        verified.setSystemMetadata("lockVersion", 1L);
        when(schedulerService.getTask(task.getItemId())).thenReturn(latest, null, verified);
        when(schedulerService.saveTaskWithRefresh(any(ScheduledTask.class))).thenAnswer(inv -> {
            ScheduledTask t = inv.getArgument(0);
            verified.setSystemMetadata("lockVersion", t.getSystemMetadata("lockVersion"));
            return true;
        });

        assertTrue(lockManager.acquireLock(task));
        assertEquals(1, metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_LOCK_ACQUIRED));
    }

    @Test
    public void testInMemoryExclusiveLockSerializesConcurrentAcquires() throws Exception {
        ScheduledTask shared = TaskTestFixtures.baseTask("mem-race");
        shared.setPersistent(false);
        when(schedulerService.getTask(shared.getItemId())).thenReturn(shared);
        when(schedulerService.saveTask(any(ScheduledTask.class))).thenAnswer(inv -> {
            ScheduledTask t = inv.getArgument(0);
            shared.setLockOwner(t.getLockOwner());
            shared.setLockDate(t.getLockDate());
            return true;
        });

        // Distinct node IDs — same-node re-acquire is allowed; exclusivity is cross-owner.
        TaskLockManager[] managers = new TaskLockManager[8];
        for (int i = 0; i < managers.length; i++) {
            TaskLockManager m = new TaskLockManager();
            m.setNodeId("mem-node-" + i);
            m.setLockTimeout(1000);
            m.setMetricsManager(new TaskMetricsManager());
            m.setSchedulerService(schedulerService);
            managers[i] = m;
        }

        java.util.concurrent.atomic.AtomicInteger wins = new java.util.concurrent.atomic.AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(managers.length);
        for (TaskLockManager m : managers) {
            Thread t = new Thread(() -> {
                try {
                    start.await();
                    ScheduledTask view = TaskTestFixtures.baseTask("mem-race");
                    view.setItemId(shared.getItemId());
                    view.setPersistent(false);
                    if (m.acquireLock(view)) {
                        wins.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            t.start();
        }
        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(1, wins.get(), "Only one in-memory exclusive acquire should win across node ids");
        assertNotNull(shared.getLockOwner());
    }

    @Test
    public void testPeerStealDuringVerificationWindowIsNotClobberedByLoser() throws Exception {
        // Simulates a peer legitimately stealing the lock (via its own CAS, after our lock
        // aged past a short lockTimeout) while we are inside acquireDistributedLock's
        // VERIFICATION_DELAY_MS post-CAS sleep — the exact narrow window the CAS+verify
        // protocol exists to protect. Unlike the canned-sequential-stub tests above, the
        // steal here is driven by a real background thread racing real wall-clock time
        // against our verification sleep, not pre-scripted return values.
        String taskId = "verify-window";
        java.util.concurrent.atomic.AtomicLong seqNo = new java.util.concurrent.atomic.AtomicLong(1L);
        java.util.concurrent.atomic.AtomicReference<String> owner = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicLong lockVersion = new java.util.concurrent.atomic.AtomicLong(0L);
        CountDownLatch nodeACasDone = new CountDownLatch(1);
        CountDownLatch peerStealDone = new CountDownLatch(1);

        when(schedulerService.getTask(eq(taskId))).thenAnswer(inv -> {
            ScheduledTask snapshot = TaskTestFixtures.baseTask("verify-window");
            snapshot.setItemId(taskId);
            snapshot.setSystemMetadata("seq_no", seqNo.get());
            snapshot.setSystemMetadata("primary_term", 1L);
            String currentOwner = owner.get();
            if (currentOwner != null) {
                snapshot.setLockOwner(currentOwner);
                snapshot.setLockDate(new Date());
                snapshot.setSystemMetadata("lockVersion", lockVersion.get());
            }
            return snapshot;
        });
        when(schedulerService.saveTaskWithRefresh(any(ScheduledTask.class))).thenAnswer(inv -> {
            ScheduledTask t = inv.getArgument(0);
            Object givenSeq = t.getSystemMetadata("seq_no");
            if (!Long.valueOf(seqNo.get()).equals(givenSeq)) {
                return false;
            }
            owner.set(t.getLockOwner());
            Object ver = t.getSystemMetadata("lockVersion");
            lockVersion.set(ver instanceof Number ? ((Number) ver).longValue() : 0L);
            seqNo.incrementAndGet();
            nodeACasDone.countDown();
            return true;
        });

        // Peer steal thread: waits for our CAS to land, then performs its own legitimate
        // CAS using the now-current seq_no — exactly what a real peer's acquireDistributedLock
        // would do once it observes our lock as expired (short lockTimeout below). Runs during
        // our real Thread.sleep(VERIFICATION_DELAY_MS) inside acquireDistributedLock.
        Thread stealer = new Thread(() -> {
            try {
                assertTrue(nodeACasDone.await(2, TimeUnit.SECONDS));
                long stolenSeq = seqNo.get();
                owner.set("peer-node");
                lockVersion.set(999L);
                seqNo.set(stolenSeq + 1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                peerStealDone.countDown();
            }
        }, "peer-steal-thread");
        stealer.start();

        ScheduledTask task = TaskTestFixtures.baseTask("verify-window");
        task.setItemId(taskId);

        assertFalse(lockManager.acquireLock(task),
            "Verification must fail once a peer has legitimately stolen the lock mid-window");
        assertTrue(peerStealDone.await(2, TimeUnit.SECONDS));
        assertEquals(1, metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_LOCK_CONFLICTS));

        // The loser must not have clobbered the peer's win on its way out.
        assertEquals("peer-node", owner.get(), "Losing node must not clear the peer's legitimate lock");
        assertEquals(999L, lockVersion.get());
    }

    private void stubSuccessfulDistributedAcquire(ScheduledTask task) {
        ScheduledTask latest = TaskTestFixtures.baseTask(task.getTaskType());
        latest.setItemId(task.getItemId());
        latest.setNextScheduledExecution(task.getNextScheduledExecution());
        latest.setSystemMetadata("seq_no", 1L);
        latest.setSystemMetadata("primary_term", 1L);
        ScheduledTask verified = TaskTestFixtures.baseTask(task.getTaskType());
        verified.setItemId(task.getItemId());
        verified.setLockOwner(NODE);
        verified.setLockDate(new Date());
        verified.setSystemMetadata("lockVersion", 1L);
        when(schedulerService.getTask(task.getItemId())).thenReturn(latest, verified);
        when(schedulerService.saveTaskWithRefresh(any(ScheduledTask.class))).thenAnswer(inv -> {
            ScheduledTask t = inv.getArgument(0);
            verified.setSystemMetadata("lockVersion", t.getSystemMetadata("lockVersion"));
            return true;
        });
    }
}
