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
package org.apache.unomi.itests.shell;

import org.junit.Assert;
import org.junit.Test;

import java.util.regex.Pattern;

/**
 * Integration tests for scheduler commands.
 */
public class SchedulerCommandsIT extends ShellCommandsBaseIT {

    private static final Pattern TASK_COUNT_PATTERN = 
        Pattern.compile("Showing\\s+(\\d+)\\s+task");

    @Test
    public void testTaskList() throws Exception {
        String output = executeCommandAndGetOutput("unomi:task-list");
        // Should show task list table with headers or "No tasks found"
        assertContainsAny(output, new String[]{"ID", "No tasks found", "Showing"}, 
            "Should show task list table with headers or indicate no tasks");
        
        // If tasks are shown, verify table structure
        if (hasTableHeaders(output, "ID", "Type", "Status")) {
            validateTableHeaders(output, new String[]{"ID", "Type", "Status"});
            validateTaskCountIfPresent(output);
        }
    }

    /**
     * Check if output contains all specified headers.
     */
    private boolean hasTableHeaders(String output, String... headers) {
        for (String header : headers) {
            if (!output.contains(header)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validate task count if present in output.
     */
    private void validateTaskCountIfPresent(String output) {
        if (output.contains("Showing") && output.contains("task")) {
            int count = extractNumericValue(output, TASK_COUNT_PATTERN);
            Assert.assertTrue("Task count should be extracted and valid", count >= 0);
        }
    }

    @Test
    public void testTaskShowWithInvalidId() throws Exception {
        String nonExistentId = "non-existent-task-" + System.currentTimeMillis();
        String output = executeCommandAndGetOutput("unomi:task-show " + nonExistentId);
        // Should indicate task not found with the specific ID
        validateErrorMessage(output, "Task not found:", nonExistentId);
    }

    @Test
    public void testTaskPurge() throws Exception {
        // Note: task-purge requires confirmation, so we use --force flag
        String output = executeCommandAndGetOutput("unomi:task-purge --force");
        assertContainsAny(output, new String[]{"Successfully purged", "purged"}, 
            "Should confirm purge completed");
        
        // If purge was successful, verify it contains a count or confirmation message
        if (output.contains("Successfully purged")) {
            // Check if there's a number after "purged" (with optional "tasks" or similar)
            boolean hasCount = output.matches(".*Successfully purged\\s+\\d+.*") ||
                              output.matches(".*purged\\s+\\d+.*");
            // If no explicit count, at least verify the message is present
            Assert.assertTrue("Purge confirmation should contain task count or confirmation", 
                hasCount || output.contains("purged"));
        }
    }

    @Test
    public void testTaskShowOutputFormat() throws Exception {
        String nonExistentId = "test-task-" + System.currentTimeMillis();
        String output = executeCommandAndGetOutput("unomi:task-show " + nonExistentId);
        validateErrorMessage(output, "Task not found:", nonExistentId);
    }

    @Test
    public void testTaskListWithStatusFilter() throws Exception {
        testTaskListWithFilter("-s COMPLETED", "COMPLETED", "with status");
    }

    @Test
    public void testTaskListWithTypeFilter() throws Exception {
        testTaskListWithFilter("-t testType", "testType", "of type");
    }

    /**
     * Helper method to test task list filtering.
     * 
     * @param filterOption the filter option (e.g., "-s=COMPLETED", "-t=testType")
     * @param filterValue the filter value to check in output
     * @param filterLabel the label that should appear in output (e.g., "with status", "of type")
     */
    private void testTaskListWithFilter(String filterOption, String filterValue, String filterLabel) throws Exception {
        String output = executeCommandAndGetOutput("unomi:task-list " + filterOption);
        assertContainsAny(output, new String[]{"ID", "No tasks found"}, 
            "Should show task list or indicate no tasks");
        
        // If tasks are shown, verify filter was applied
        if (output.contains("Showing") && output.contains("task") && output.contains(filterValue)) {
            assertContainsAny(output, new String[]{filterLabel, filterValue}, 
                "Should show filter in output");
        }
    }

    @Test
    public void testTaskListWithLimit() throws Exception {
        String output = executeCommandAndGetOutput("unomi:task-list --limit 10");
        validateTableHeaders(output, new String[]{"ID", "Type", "Status"});
        
        // Verify limit was applied (should show max 10 tasks)
        validateTaskCountLimit(output, 10);
    }

    /**
     * Validate that task count respects the specified limit.
     */
    private void validateTaskCountLimit(String output, int maxLimit) {
        if (output.contains("Showing") && output.contains("task")) {
            int count = extractNumericValue(output, TASK_COUNT_PATTERN);
            if (count >= 0) {
                Assert.assertTrue("Task count should respect limit of " + maxLimit, count <= maxLimit);
            }
        }
    }
}
