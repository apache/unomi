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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TaskHistoryManager}.
 * Invoked by Surefire: {@code -Dtest=TaskHistoryManagerTest}. No data-file I/O.
 * Glob: no prior TaskHistoryManagerTest. User asked for Task*Managers unit tests.
 */
public class TaskHistoryManagerTest {

    private TaskHistoryManager historyManager;
    private TaskMetricsManager metricsManager;

    @BeforeEach
    public void setUp() {
        metricsManager = new TaskMetricsManager();
        historyManager = new TaskHistoryManager();
        historyManager.setNodeId("history-node");
        historyManager.setMetricsManager(metricsManager);
    }

    @Test
    public void testRecordSuccessCreatesHistoryAndMetrics() {
        ScheduledTask task = TaskTestFixtures.baseTask("hist");
        historyManager.recordSuccess(task, 123);
        List<Map<String, Object>> history = historyManager.getExecutionHistory(task);
        assertEquals(1, history.size());
        assertEquals("SUCCESS", history.get(0).get("status"));
        assertEquals("history-node", history.get(0).get("nodeId"));
        assertEquals(123L, ((Number) history.get(0).get("executionTime")).longValue());
        assertEquals(1, metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_COMPLETED));
        assertEquals(123, metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_EXECUTION_TIME));
    }

    @Test
    public void testRecordFailureCrashCancelResumeRetry() {
        ScheduledTask task = TaskTestFixtures.baseTask("hist");
        historyManager.recordFailure(task, "boom");
        historyManager.recordCrash(task);
        historyManager.recordCancellation(task);
        historyManager.recordResume(task);
        historyManager.recordRetry(task);
        List<Map<String, Object>> history = historyManager.getExecutionHistory(task);
        assertEquals(5, history.size());
        assertEquals("FAILED", history.get(0).get("status"));
        assertEquals("CRASHED", history.get(1).get("status"));
        assertEquals("CANCELLED", history.get(2).get("status"));
        assertEquals("RESUMED", history.get(3).get("status"));
        assertEquals("RETRIED", history.get(4).get("status"));
        assertEquals(1, metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_FAILED));
        assertEquals(1, metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_CRASHED));
        assertEquals(1, metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_CANCELLED));
        assertEquals(1, metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_RESUMED));
        assertEquals(1, metricsManager.getMetric(TaskMetricsManager.METRIC_TASKS_RETRIED));
    }

    @Test
    public void testHistoryCappedAtTenFifo() {
        ScheduledTask task = TaskTestFixtures.baseTask("hist");
        for (int i = 0; i < 12; i++) {
            historyManager.recordFailure(task, "err-" + i);
        }
        List<Map<String, Object>> history = historyManager.getExecutionHistory(task);
        assertEquals(10, history.size());
        assertEquals("err-2", history.get(0).get("error"));
        assertEquals("err-11", history.get(9).get("error"));
    }

    @Test
    public void testUnmodifiableStatusDetailsAreCopied() {
        ScheduledTask task = TaskTestFixtures.baseTask("hist");
        task.setStatusDetails(Collections.unmodifiableMap(new HashMap<>()));
        assertDoesNotThrow(() -> historyManager.recordSuccess(task, 1));
        assertEquals(1, historyManager.getExecutionHistory(task).size());
    }

    @Test
    public void testGetExecutionHistoryEmptyWhenMissing() {
        ScheduledTask task = TaskTestFixtures.baseTask("hist");
        assertTrue(historyManager.getExecutionHistory(task).isEmpty());
        task.setStatusDetails(new HashMap<>());
        assertTrue(historyManager.getExecutionHistory(task).isEmpty());
    }

    @Test
    public void testUnmodifiableExecutionHistoryListIsCopied() {
        ScheduledTask task = TaskTestFixtures.baseTask("hist");
        Map<String, Object> first = new HashMap<>();
        first.put("status", "FAILED");
        first.put("error", "seed");
        Map<String, Object> details = new HashMap<>();
        details.put("executionHistory", Collections.unmodifiableList(Collections.singletonList(first)));
        task.setStatusDetails(details);

        assertDoesNotThrow(() -> historyManager.recordSuccess(task, 5));
        List<Map<String, Object>> history = historyManager.getExecutionHistory(task);
        assertEquals(2, history.size());
        assertEquals("SUCCESS", history.get(1).get("status"));

        for (int i = 0; i < 12; i++) {
            historyManager.recordFailure(task, "cap-" + i);
        }
        assertEquals(10, historyManager.getExecutionHistory(task).size());
    }
}
