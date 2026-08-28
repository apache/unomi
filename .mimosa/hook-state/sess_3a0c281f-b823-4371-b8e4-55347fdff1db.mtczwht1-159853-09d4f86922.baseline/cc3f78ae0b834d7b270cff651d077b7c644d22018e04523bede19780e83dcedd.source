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

import org.apache.unomi.api.ClusterNode;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.ClusterService;
import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PersistenceSchedulerProvider} (the only SchedulerProvider impl).
 * Invoked by Surefire: {@code -Dtest=PersistenceSchedulerProviderTest}. No data-file I/O.
 * Glob: no prior PersistenceSchedulerProviderTest. User asked for SchedulerProvider impl tests.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PersistenceSchedulerProviderTest {

    @Mock private PersistenceService persistenceService;
    @Mock private ClusterService clusterService;
    @Mock private TaskLockManager lockManager;

    private PersistenceSchedulerProvider provider;

    @BeforeEach
    public void setUp() {
        provider = new PersistenceSchedulerProvider();
        provider.setPersistenceService(persistenceService);
        provider.setNodeId("provider-node");
        provider.setExecutorNode(true);
        provider.setCompletedTaskTtlDays(30);
        provider.setLockManager(lockManager);
        provider.setClusterService(clusterService);
    }

    @Test
    public void testSaveTaskNullAndNonPersistent() {
        assertFalse(provider.saveTask(null));
        ScheduledTask mem = TaskTestFixtures.baseTask("m");
        mem.setPersistent(false);
        assertFalse(provider.saveTask(mem));
        verify(persistenceService, never()).save(any());
    }

    @Test
    public void testSaveTaskPropagatesBoolean() {
        ScheduledTask task = TaskTestFixtures.baseTask("p");
        when(persistenceService.save(task)).thenReturn(true);
        assertTrue(provider.saveTask(task));
        when(persistenceService.save(task)).thenReturn(false);
        assertFalse(provider.saveTask(task));
    }

    @Test
    public void testSaveTaskCompareAndSetUsesAlwaysOverwriteFalse() {
        ScheduledTask task = TaskTestFixtures.baseTask("cas");
        when(persistenceService.save(task, false, false)).thenReturn(true);
        assertTrue(provider.saveTaskCompareAndSet(task));
        verify(persistenceService).save(task, false, false);
        assertFalse(provider.saveTaskCompareAndSet(null));
        ScheduledTask mem = TaskTestFixtures.baseTask("m");
        mem.setPersistent(false);
        assertFalse(provider.saveTaskCompareAndSet(mem));
    }

    @Test
    public void testFindTasksByLockOwnerNullPersistence() {
        provider.setPersistenceService(null);
        assertTrue(provider.findTasksByLockOwner("x").isEmpty());
    }

    @Test
    public void testFindEnabledScheduledOrWaitingIncludesCrashed() {
        ScheduledTask crashed = TaskTestFixtures.baseTask("c");
        crashed.setStatus(ScheduledTask.TaskStatus.CRASHED);
        PartialList<ScheduledTask> result = new PartialList<>(
            Collections.singletonList(crashed), 0, 1, 1, PartialList.Relation.EQUAL);
        when(persistenceService.query(any(Condition.class), anyString(), eq(ScheduledTask.class), eq(0), eq(-1)))
            .thenReturn(result);

        List<ScheduledTask> found = provider.findEnabledScheduledOrWaitingTasks();
        assertEquals(1, found.size());

        ArgumentCaptor<Condition> conditionCaptor = ArgumentCaptor.forClass(Condition.class);
        verify(persistenceService).query(conditionCaptor.capture(), eq("creationDate:asc"),
            eq(ScheduledTask.class), eq(0), eq(-1));
        Condition and = conditionCaptor.getValue();
        @SuppressWarnings("unchecked")
        List<Condition> subs = (List<Condition>) and.getParameter("subConditions");
        Condition status = subs.get(1);
        @SuppressWarnings("unchecked")
        List<Object> values = (List<Object>) status.getParameter("propertyValues");
        assertTrue(values.contains(ScheduledTask.TaskStatus.CRASHED));
        assertTrue(values.contains(ScheduledTask.TaskStatus.SCHEDULED));
        assertTrue(values.contains(ScheduledTask.TaskStatus.WAITING));
    }

    @Test
    public void testPurgeOldTasksSkippedOnNonExecutor() {
        provider.setExecutorNode(false);
        provider.purgeOldTasks();
        verify(persistenceService, never()).removeByQuery(any(), eq(ScheduledTask.class));
    }

    @Test
    public void testPurgeOldTasksOnExecutor() {
        provider.purgeOldTasks();
        verify(persistenceService).removeByQuery(any(Condition.class), eq(ScheduledTask.class));
    }

    @Test
    public void testDeleteTaskNullIsNoOp() {
        provider.deleteTask(null);
        verify(persistenceService, never()).remove(anyString(), eq(ScheduledTask.class));
    }

    @Test
    public void testGetActiveNodesIncludesSelfAndRecentClusterHeartbeats() {
        ClusterNode fresh = new ClusterNode();
        fresh.setItemId("peer");
        fresh.setLastHeartbeat(System.currentTimeMillis());
        ClusterNode stale = new ClusterNode();
        stale.setItemId("stale");
        stale.setLastHeartbeat(System.currentTimeMillis() - (10 * 60 * 1000));
        when(clusterService.getClusterNodes()).thenReturn(Arrays.asList(fresh, stale));

        List<String> nodes = provider.getActiveNodes();
        assertTrue(nodes.contains("provider-node"));
        assertTrue(nodes.contains("peer"));
        assertFalse(nodes.contains("stale"));
    }

    @Test
    public void testGetActiveNodesFallbackWhenClusterUnavailable() {
        when(clusterService.getClusterNodes()).thenThrow(new RuntimeException("down"));
        ScheduledTask locked = TaskTestFixtures.baseTask("l");
        locked.setLockOwner("fallback-peer");
        locked.setLockDate(new Date());
        when(persistenceService.query(any(Condition.class), anyString(), eq(ScheduledTask.class)))
            .thenReturn(Collections.singletonList(locked));

        List<String> nodes = provider.getActiveNodes();
        assertTrue(nodes.contains("provider-node"));
        assertTrue(nodes.contains("fallback-peer"));
    }

    @Test
    public void testPreDestroyReleasesLocksOnNonRunningTasks() {
        ScheduledTask locked = TaskTestFixtures.baseTask("l");
        locked.setLockOwner("provider-node");
        locked.setLockDate(new Date());
        locked.setStatus(ScheduledTask.TaskStatus.SCHEDULED);
        PartialList<ScheduledTask> result = new PartialList<>(
            Collections.singletonList(locked), 0, 1, 1, PartialList.Relation.EQUAL);
        when(persistenceService.query(any(Condition.class), isNull(), eq(ScheduledTask.class), eq(0), eq(-1)))
            .thenReturn(result);
        when(lockManager.releaseLock(locked)).thenReturn(true);

        provider.preDestroy();
        verify(lockManager).releaseLock(locked);
    }

    @Test
    public void testPreDestroySkipsRunningAndCrashedLocks() {
        ScheduledTask running = TaskTestFixtures.runningTask("r", "provider-node");
        ScheduledTask crashed = TaskTestFixtures.baseTask("c");
        crashed.setStatus(ScheduledTask.TaskStatus.CRASHED);
        crashed.setLockOwner("provider-node");
        crashed.setLockDate(new Date());
        PartialList<ScheduledTask> result = new PartialList<>(
            Arrays.asList(running, crashed), 0, 2, 2, PartialList.Relation.EQUAL);
        when(persistenceService.query(any(Condition.class), isNull(), eq(ScheduledTask.class), eq(0), eq(-1)))
            .thenReturn(result);

        provider.preDestroy();
        verify(lockManager, never()).releaseLock(any());
    }

    @Test
    public void testQueryExceptionReturnsEmpty() {
        when(persistenceService.query(any(Condition.class), any(), eq(ScheduledTask.class), anyInt(), anyInt()))
            .thenThrow(new RuntimeException("boom"));
        assertTrue(provider.findTasksByStatus(ScheduledTask.TaskStatus.RUNNING).isEmpty());
        PartialList<ScheduledTask> page = provider.getTasksByStatus(ScheduledTask.TaskStatus.SCHEDULED, 0, 10, null);
        assertTrue(page.getList().isEmpty());
    }

    @Test
    public void testGetTaskAndRefresh() {
        ScheduledTask task = TaskTestFixtures.baseTask("g");
        when(persistenceService.load(task.getItemId(), ScheduledTask.class)).thenReturn(task);
        assertEquals(task, provider.getTask(task.getItemId()));
        provider.refreshTasks();
        verify(persistenceService).refreshIndex(ScheduledTask.class);
    }

    @Test
    public void testSaveMethodsReturnFalseOnPersistenceException() {
        ScheduledTask task = TaskTestFixtures.baseTask("ex");
        when(persistenceService.save(task)).thenThrow(new RuntimeException("disk full"));
        when(persistenceService.save(task, false, false)).thenThrow(new RuntimeException("conflict store"));
        assertFalse(provider.saveTask(task));
        assertFalse(provider.saveTaskCompareAndSet(task));
    }

    @Test
    public void testFindLockedTasksQueryExcludesRunningAndCrashed() {
        PartialList<ScheduledTask> result = new PartialList<>(
            Collections.emptyList(), 0, 0, 0, PartialList.Relation.EQUAL);
        when(persistenceService.query(any(Condition.class), isNull(), eq(ScheduledTask.class), eq(0), eq(-1)))
            .thenReturn(result);

        assertTrue(provider.findLockedTasks().isEmpty());

        ArgumentCaptor<Condition> conditionCaptor = ArgumentCaptor.forClass(Condition.class);
        verify(persistenceService).query(conditionCaptor.capture(), isNull(),
            eq(ScheduledTask.class), eq(0), eq(-1));
        Condition and = conditionCaptor.getValue();
        @SuppressWarnings("unchecked")
        List<Condition> subs = (List<Condition>) and.getParameter("subConditions");
        Condition status = subs.get(1);
        @SuppressWarnings("unchecked")
        List<Object> values = (List<Object>) status.getParameter("propertyValues");
        assertTrue(values.contains(ScheduledTask.TaskStatus.SCHEDULED));
        assertTrue(values.contains(ScheduledTask.TaskStatus.WAITING));
        assertFalse(values.contains(ScheduledTask.TaskStatus.RUNNING));
        assertFalse(values.contains(ScheduledTask.TaskStatus.CRASHED));
    }

    @Test
    public void testPreDestroySkipsWhenPersistenceUnavailable() {
        provider.setPersistenceService(null);
        provider.preDestroy();
        verify(lockManager, never()).releaseLock(any());
    }
}
