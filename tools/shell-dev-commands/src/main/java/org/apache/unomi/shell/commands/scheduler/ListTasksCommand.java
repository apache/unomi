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
package org.apache.unomi.shell.commands.scheduler;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.table.Col;
import org.apache.karaf.shell.support.table.ShellTable;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.tasks.ScheduledTask;

import java.text.SimpleDateFormat;
import java.util.List;

@Command(scope = "unomi", name = "task-list", description = "Lists scheduled tasks")
@Service
public class ListTasksCommand implements Action {

    @Reference
    private SchedulerService schedulerService;

    @Option(name = "-s", aliases = "--status", description = "Filter by task status (SCHEDULED, RUNNING, COMPLETED, FAILED, CANCELLED, CRASHED)", required = false)
    private String status;

    @Option(name = "-t", aliases = "--type", description = "Filter by task type", required = false)
    private String type;

    @Option(name = "--limit", description = "Maximum number of tasks to display (default: 50)", required = false)
    private int limit = 50;

    @Override
    public Object execute() throws Exception {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        ShellTable table = new ShellTable();

        // Configure table columns
        table.column(new Col("ID").maxSize(36));
        table.column(new Col("Type").maxSize(30));
        table.column(new Col("Status").maxSize(10));
        table.column(new Col("Next Run").maxSize(19));
        table.column(new Col("Last Run").maxSize(19));
        table.column(new Col("Failures").alignRight());
        table.column(new Col("Successes").alignRight());
        table.column(new Col("Total Exec").alignRight());
        table.column(new Col("Persistent").maxSize(10));

        // Get tasks based on filters
        List<ScheduledTask> tasks;
        if (status != null) {
            try {
                ScheduledTask.TaskStatus taskStatus = ScheduledTask.TaskStatus.valueOf(status.toUpperCase());
                // Get persistent tasks
                PartialList<ScheduledTask> filteredTasks = schedulerService.getTasksByStatus(taskStatus, 0, limit, null);
                tasks = filteredTasks.getList();
                // Add memory tasks with matching status
                List<ScheduledTask> memoryTasks = schedulerService.getMemoryTasks();
                for (ScheduledTask task : memoryTasks) {
                    if (task.getStatus() == taskStatus) {
                        tasks.add(task);
                    }
                }
            } catch (IllegalArgumentException e) {
                System.err.println("Invalid status: " + status);
                return null;
            }
        } else if (type != null) {
            // Get persistent tasks
            PartialList<ScheduledTask> filteredTasks = schedulerService.getTasksByType(type, 0, limit, null);
            tasks = filteredTasks.getList();
            // Add memory tasks with matching type
            List<ScheduledTask> memoryTasks = schedulerService.getMemoryTasks();
            for (ScheduledTask task : memoryTasks) {
                if (task.getTaskType().equals(type)) {
                    tasks.add(task);
                }
            }
        } else {
            // Get all tasks from both storage and memory
            tasks = schedulerService.getAllTasks();
            if (tasks.size() > limit) {
                tasks = tasks.subList(0, limit);
            }
        }

        // Add rows to table
        for (ScheduledTask task : tasks) {
            int totalExecutions = task.getSuccessCount() + task.getFailureCount();

            table.addRow().addContent(
                task.getItemId(),
                task.getTaskType(),
                task.getStatus(),
                task.getNextScheduledExecution() != null ? dateFormat.format(task.getNextScheduledExecution()) : "-",
                task.getLastExecutionDate() != null ? dateFormat.format(task.getLastExecutionDate()) : "-",
                task.getFailureCount(),
                task.getSuccessCount(),
                totalExecutions,
                task.isPersistent() ? "Storage" : "Memory"
            );
        }

        table.print(System.out);

        if (tasks.isEmpty()) {
            System.out.println("No tasks found.");
        } else {
            int persistentCount = (int) tasks.stream().filter(ScheduledTask::isPersistent).count();
            int memoryCount = tasks.size() - persistentCount;
            System.out.println("\nShowing " + tasks.size() + " task(s) (" +
                persistentCount + " in storage, " + memoryCount + " in memory)" +
                (status != null ? " with status " + status : "") +
                (type != null ? " of type " + type : ""));
        }

        return null;
    }
}
