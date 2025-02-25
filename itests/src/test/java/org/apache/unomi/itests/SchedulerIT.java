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
package org.apache.unomi.itests;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.api.tasks.TaskExecutor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * Integration tests for the Scheduler REST API
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class SchedulerIT extends BaseIT {

    private final static String TEST_TASK_TYPE = "test-task";
    private String testTaskId;

    @Inject @Filter(timeout = 600000)
    protected SchedulerService schedulerService;

    @Before
    public void setUp() {
        // Register a test task executor
        TestTaskExecutor executor = new TestTaskExecutor();
        schedulerService.registerTaskExecutor(executor);

        // Create a test task
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("testParam", "testValue");

        ScheduledTask task = schedulerService.createTask(
            TEST_TASK_TYPE,
            parameters,
            0,
            1000,
            TimeUnit.MILLISECONDS,
            true,
            false,
            false,
            true
        );
        testTaskId = task.getItemId();
        schedulerService.scheduleTask(task);
    }

    @After
    public void tearDown() {
        // Clean up test task
        if (testTaskId != null) {
            try {
                schedulerService.cancelTask(testTaskId);
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    @Test
    public void testGetTasks() throws Exception {
        // Test getting all tasks
        PartialList<ScheduledTask> tasks = get("/cxs/tasks", PartialList.class);
        assertNotNull("Tasks list should not be null", tasks);
        assertTrue("Should have at least one task", tasks.getList().size() > 0);

        // Test filtering by status
        tasks = get("/cxs/tasks?status=SCHEDULED", PartialList.class);
        assertNotNull("Filtered tasks list should not be null", tasks);

        // Test filtering by type
        tasks = get("/cxs/tasks?type=" + TEST_TASK_TYPE, PartialList.class);
        assertNotNull("Type-filtered tasks list should not be null", tasks);
        assertTrue("Should find test task", tasks.getList().size() > 0);
    }

    @Test
    public void testGetTask() throws Exception {
        ScheduledTask task = get("/cxs/tasks/" + testTaskId, ScheduledTask.class);
        assertNotNull("Task should not be null", task);
        assertEquals("Task ID should match", testTaskId, task.getItemId());
        assertEquals("Task type should match", TEST_TASK_TYPE, task.getTaskType());
    }

    @Test
    public void testGetNonExistentTask() throws Exception {
        ScheduledTask task = get("/cxs/tasks/non-existent-task", ScheduledTask.class);
        assertNull("Task should be null", task);
    }

    @Test
    public void testCancelTask() throws Exception {
        CloseableHttpResponse response = delete("/cxs/tasks/" + testTaskId);
        assertEquals("Response should be No Content", 204, response.getStatusLine().getStatusCode());

        // Verify task is cancelled
        ScheduledTask task = schedulerService.getTask(testTaskId);
        assertEquals("Task should be cancelled", ScheduledTask.TaskStatus.CANCELLED, task.getStatus());
    }

    @Test
    public void testRetryTask() throws Exception {
        // First make the task fail
        TestTaskExecutor.shouldFail.set(true);
        try {
            Thread.sleep(1500); // Wait for task to execute and fail
        } catch (InterruptedException e) {
            // Ignore
        }

        // Now retry the task
        CloseableHttpResponse response = post("/cxs/tasks/" + testTaskId + "/retry?resetFailureCount=true", null);
        assertEquals("Response should be OK", 200, response.getStatusLine().getStatusCode());

        String responseBody = EntityUtils.toString(response.getEntity());
        ScheduledTask task = objectMapper.readValue(responseBody, ScheduledTask.class);
        assertNotNull("Task should not be null", task);
        assertEquals("Task should be scheduled", ScheduledTask.TaskStatus.SCHEDULED, task.getStatus());
        assertEquals("Failure count should be reset", 0, task.getFailureCount());
    }

    private static class TestTaskExecutor implements TaskExecutor {
        static final AtomicBoolean shouldFail = new AtomicBoolean(false);

        @Override
        public String getTaskType() {
            return TEST_TASK_TYPE;
        }

        @Override
        public void execute(ScheduledTask task, TaskStatusCallback callback) throws Exception {
            if (shouldFail.get()) {
                throw new Exception("Test failure");
            }
            callback.complete();
        }
    }
}
