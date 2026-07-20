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
        ScheduledTask latest = TaskTestFixtures.baseTask("dist");
        latest.setItemId(task.getItemId());
        latest.setNextScheduledExecution(task.getNextScheduledExecution());
        latest.setSystemMetadata("seq_no", 3L);
        latest.setSystemMetadata("primary_term", 1L);
        when(schedulerService.getTask(task.getItemId())).thenReturn(latest);
        when(schedulerService.saveTaskWithRefresh(any(ScheduledTask.class))).thenReturn(true);

        assertTrue(lockManager.acquireLock(task));
        assertEquals(NODE, task.getLockOwner());
        assertEquals(1, metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_LOCK_ACQUIRED));
    }

    @Test
    public void testDistributedLockSuccessDoesNotReVerify() {
        // A successful CAS write is itself authoritative proof of acquisition: exactly one
        // GET-by-id (the pre-CAS read) and one CAS write, no post-write re-read/verification.
        ScheduledTask task = TaskTestFixtures.baseTask("dist-no-reverify");
        task.setNextScheduledExecution(new Date(System.currentTimeMillis() - 10_000));
        ScheduledTask latest = TaskTestFixtures.baseTask("dist-no-reverify");
        latest.setItemId(task.getItemId());
        latest.setNextScheduledExecution(task.getNextScheduledExecution());
        latest.setSystemMetadata("seq_no", 5L);
        latest.setSystemMetadata("primary_term", 1L);
        when(schedulerService.getTask(task.getItemId())).thenReturn(latest);
        when(schedulerService.saveTaskWithRefresh(any(ScheduledTask.class))).thenReturn(true);

        assertTrue(lockManager.acquireLock(task));
        verify(schedulerService, times(1)).getTask(task.getItemId());
        verify(schedulerService, times(1)).saveTaskWithRefresh(any(ScheduledTask.class));
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
        when(schedulerService.getTask(task.getItemId())).thenReturn(latest);
        when(schedulerService.saveTaskWithRefresh(any(ScheduledTask.class))).thenReturn(true);
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
    public void testDistributedLockLosesRaceToPeerCasWithoutClobbering() {
        // A peer's CAS lands between our read and our own write attempt (the backend rejects
        // our write because seq_no/primary_term no longer match what we read). There is no
        // verification window to race in anymore - the backend's own CAS result is the single,
        // atomic point of truth - so losing a race must fail closed immediately and must never
        // attempt to touch (let alone clear) whatever the peer just wrote.
        String taskId = "peer-race";
        ScheduledTask task = TaskTestFixtures.baseTask("peer-race");
        task.setItemId(taskId);
        task.setNextScheduledExecution(new Date(System.currentTimeMillis() - 10_000));
        ScheduledTask latest = TaskTestFixtures.baseTask("peer-race");
        latest.setItemId(taskId);
        latest.setNextScheduledExecution(task.getNextScheduledExecution());
        latest.setSystemMetadata("seq_no", 1L);
        latest.setSystemMetadata("primary_term", 1L);
        when(schedulerService.getTask(taskId)).thenReturn(latest);
        // Backend rejects our write: precondition (seq_no=1) no longer matches - a peer moved it.
        when(schedulerService.saveTaskWithRefresh(any(ScheduledTask.class))).thenReturn(false);

        assertFalse(lockManager.acquireLock(task));
        assertEquals(1, metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_LOCK_CONFLICTS));
        // Losing a CAS race must never trigger a release call - releaseLock() always re-loads
        // via getTask(id, true) first, so its absence proves we never attempted to touch the peer.
        verify(schedulerService, never()).getTask(eq(taskId), anyBoolean());
    }

    @Test
    public void testRenewLockSkipsNonExclusiveAndNonPersistentTasks() {
        ScheduledTask parallel = TaskTestFixtures.baseTask("renew-parallel");
        parallel.setAllowParallelExecution(true);
        assertTrue(lockManager.renewLock(parallel));

        ScheduledTask inMemory = TaskTestFixtures.baseTask("renew-mem");
        inMemory.setPersistent(false);
        assertTrue(lockManager.renewLock(inMemory));

        verify(schedulerService, never()).getTask(anyString());
        verify(schedulerService, never()).saveTaskWithRefresh(any(ScheduledTask.class));
    }

    @Test
    public void testRenewLockRefusesWhenNotLocalOwner() {
        ScheduledTask task = TaskTestFixtures.baseTask("renew-foreign");
        task.setLockOwner("other-node");
        task.setLockDate(new Date());

        assertFalse(lockManager.renewLock(task));
        verify(schedulerService, never()).saveTaskWithRefresh(any(ScheduledTask.class));
    }

    @Test
    public void testRenewLockRefusesWhenStoreOwnerChanged() {
        ScheduledTask task = TaskTestFixtures.baseTask("renew-stolen");
        task.setLockOwner(NODE);
        task.setLockDate(new Date());
        ScheduledTask storeView = TaskTestFixtures.baseTask("renew-stolen");
        storeView.setItemId(task.getItemId());
        storeView.setLockOwner("peer-node");
        storeView.setLockDate(new Date());
        when(schedulerService.getTask(task.getItemId())).thenReturn(storeView);

        assertFalse(lockManager.renewLock(task));
        verify(schedulerService, never()).saveTaskWithRefresh(any(ScheduledTask.class));
    }

    @Test
    public void testRenewLockRefreshesLockDateAndSyncsOccMetadata() {
        Date staleDate = new Date(System.currentTimeMillis() - 60_000);
        ScheduledTask task = TaskTestFixtures.baseTask("renew-ok");
        task.setLockOwner(NODE);
        task.setLockDate(staleDate);
        task.setSystemMetadata("seq_no", 3L);
        task.setSystemMetadata("primary_term", 1L);

        ScheduledTask storeView = TaskTestFixtures.baseTask("renew-ok");
        storeView.setItemId(task.getItemId());
        storeView.setLockOwner(NODE);
        storeView.setLockDate(staleDate);
        storeView.setSystemMetadata("seq_no", 7L);
        storeView.setSystemMetadata("primary_term", 2L);
        when(schedulerService.getTask(task.getItemId())).thenReturn(storeView);
        when(schedulerService.saveTaskWithRefresh(storeView)).thenAnswer(inv -> {
            // Simulate the store handing back post-save OCC tokens on the saved instance
            storeView.setSystemMetadata("seq_no", 8L);
            return true;
        });

        assertTrue(lockManager.renewLock(task));
        assertTrue(task.getLockDate().after(staleDate),
            "Renewal must refresh the caller's lockDate");
        assertEquals(8L, ((Number) task.getSystemMetadata("seq_no")).longValue(),
            "Renewal must sync post-save seq_no back so later CAS writes succeed");
        assertEquals(2L, ((Number) task.getSystemMetadata("primary_term")).longValue());
    }

    @Test
    public void testRenewLockFailsClosedWhenCasLost() {
        Date originalDate = new Date(System.currentTimeMillis() - 500);
        ScheduledTask task = TaskTestFixtures.baseTask("renew-cas");
        task.setLockOwner(NODE);
        task.setLockDate(originalDate);
        ScheduledTask storeView = TaskTestFixtures.baseTask("renew-cas");
        storeView.setItemId(task.getItemId());
        storeView.setLockOwner(NODE);
        storeView.setLockDate(originalDate);
        when(schedulerService.getTask(task.getItemId())).thenReturn(storeView);
        when(schedulerService.saveTaskWithRefresh(storeView)).thenReturn(false);

        assertFalse(lockManager.renewLock(task));
        assertEquals(originalDate, task.getLockDate(),
            "A lost renewal CAS must not touch the caller's lock state");
    }

    private void stubSuccessfulDistributedAcquire(ScheduledTask task) {
        ScheduledTask latest = TaskTestFixtures.baseTask(task.getTaskType());
        latest.setItemId(task.getItemId());
        latest.setNextScheduledExecution(task.getNextScheduledExecution());
        latest.setSystemMetadata("seq_no", 1L);
        latest.setSystemMetadata("primary_term", 1L);
        when(schedulerService.getTask(task.getItemId())).thenReturn(latest);
        when(schedulerService.saveTaskWithRefresh(any(ScheduledTask.class))).thenReturn(true);
    }
}
