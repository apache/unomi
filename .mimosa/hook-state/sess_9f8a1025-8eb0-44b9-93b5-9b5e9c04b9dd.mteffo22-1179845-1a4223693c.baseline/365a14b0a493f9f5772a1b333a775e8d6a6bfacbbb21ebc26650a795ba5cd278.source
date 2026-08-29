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

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.shell.dev.commands.CommandUtils;

import java.util.Map;

@Command(scope = "unomi", name = "task-show", description = "Shows detailed information about a task")
@Service
public class ShowTaskCommand extends BaseSchedulerCommand {

    @Argument(index = 0, name = "taskId", description = "The ID of the task to show", required = true)
    private String taskId;

    @Override
    public Object execute() throws Exception {
        ScheduledTask task = schedulerService.getTask(taskId);
        if (task == null) {
            println("Task not found: " + taskId);
            return null;
        }

        // Print basic information
        println("Task Details");
        println("-----------");
        println("ID: " + task.getItemId());
        println("Type: " + task.getTaskType());
        println("Status: " + task.getStatus());
        println("Persistent: " + task.isPersistent());
        println("Parallel Execution: " + task.isAllowParallelExecution());
        println("Fixed Rate: " + task.isFixedRate());
        println("One Shot: " + task.isOneShot());

        // Print timing information
        println("Next Run: " + CommandUtils.formatDate(task.getNextScheduledExecution()));
        println("Last Run: " + CommandUtils.formatDate(task.getLastExecutionDate()));
        println("Initial Delay: " + task.getInitialDelay() + " " + task.getTimeUnit());
        println("Period: " + task.getPeriod() + " " + task.getTimeUnit());

        // Print execution information
        println("Failure Count: " + task.getFailureCount());
        if (task.getLastError() != null) {
            println("Last Error: " + task.getLastError());
        }

        // Print parameters if any
        Map<String, Object> parameters = task.getParameters();
        if (parameters != null && !parameters.isEmpty()) {
            println("\nParameters");
            println("----------");
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                println(entry.getKey() + ": " + entry.getValue());
            }
        }

        // Print checkpoint data if any
        Map<String, Object> checkpointData = task.getCheckpointData();
        if (checkpointData != null && !checkpointData.isEmpty()) {
            println("\nCheckpoint Data");
            println("--------------");
            for (Map.Entry<String, Object> entry : checkpointData.entrySet()) {
                println(entry.getKey() + ": " + entry.getValue());
            }
        }

        return null;
    }
}
