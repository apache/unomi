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
package org.apache.unomi.shell.dev.commands.scheduler;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.tasks.ScheduledTask;

import java.text.SimpleDateFormat;
import java.util.Map;

@Command(scope = "unomi", name = "task-show", description = "Shows detailed information about a task")
@Service
public class ShowTaskCommand implements Action {

    @Reference
    private SchedulerService schedulerService;

    @Argument(index = 0, name = "taskId", description = "The ID of the task to show", required = true)
    private String taskId;

    @Override
    public Object execute() throws Exception {
        ScheduledTask task = schedulerService.getTask(taskId);
        if (task == null) {
            System.err.println("Task not found: " + taskId);
            return null;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        // Print basic information
        System.out.println("Task Details");
        System.out.println("-----------");
        System.out.println("ID: " + task.getItemId());
        System.out.println("Type: " + task.getTaskType());
        System.out.println("Status: " + task.getStatus());
        System.out.println("Persistent: " + task.isPersistent());
        System.out.println("Parallel Execution: " + task.isAllowParallelExecution());
        System.out.println("Fixed Rate: " + task.isFixedRate());
        System.out.println("One Shot: " + task.isOneShot());

        // Print timing information
        if (task.getNextScheduledExecution() != null) {
            System.out.println("Next Run: " + dateFormat.format(task.getNextScheduledExecution()));
        }
        if (task.getLastExecutionDate() != null) {
            System.out.println("Last Run: " + dateFormat.format(task.getLastExecutionDate()));
        }
        System.out.println("Initial Delay: " + task.getInitialDelay() + " " + task.getTimeUnit());
        System.out.println("Period: " + task.getPeriod() + " " + task.getTimeUnit());

        // Print execution information
        System.out.println("Failure Count: " + task.getFailureCount());
        if (task.getLastError() != null) {
            System.out.println("Last Error: " + task.getLastError());
        }

        // Print parameters if any
        Map<String, Object> parameters = task.getParameters();
        if (parameters != null && !parameters.isEmpty()) {
            System.out.println("\nParameters");
            System.out.println("----------");
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                System.out.println(entry.getKey() + ": " + entry.getValue());
            }
        }

        // Print checkpoint data if any
        Map<String, Object> checkpointData = task.getCheckpointData();
        if (checkpointData != null && !checkpointData.isEmpty()) {
            System.out.println("\nCheckpoint Data");
            System.out.println("--------------");
            for (Map.Entry<String, Object> entry : checkpointData.entrySet()) {
                System.out.println(entry.getKey() + ": " + entry.getValue());
            }
        }

        return null;
    }
}
