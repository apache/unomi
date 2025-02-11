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
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.tasks.ScheduledTask;

import java.util.Calendar;
import java.util.Date;

@Command(scope = "unomi", name = "task-purge", description = "Purges old completed tasks")
@Service
public class PurgeTasksCommand implements Action {

    @Reference
    private SchedulerService schedulerService;

    @Reference
    private Session session;

    @Option(name = "-d", aliases = "--days", description = "Number of days to keep completed tasks (default: 7)", required = false)
    private int daysToKeep = 7;

    @Option(name = "-f", aliases = "--force", description = "Skip confirmation prompt", required = false)
    private boolean force = false;

    @Override
    public Object execute() throws Exception {
        if (!force) {
            String response = session.readLine(
                "This will permanently delete all completed tasks older than " + daysToKeep + " days. Continue? (y/n): ",
                null
            );
            if (!"y".equalsIgnoreCase(response != null ? response.trim() : "n")) {
                System.out.println("Operation cancelled.");
                return null;
            }
        }

        // Calculate cutoff date
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -daysToKeep);
        Date cutoffDate = cal.getTime();

        // Get completed tasks
        int offset = 0;
        int batchSize = 100;
        int purgedCount = 0;

        while (true) {
            var tasks = schedulerService.getTasksByStatus(ScheduledTask.TaskStatus.COMPLETED, offset, batchSize, null);
            if (tasks.getList().isEmpty()) {
                break;
            }

            // Cancel old completed tasks
            for (ScheduledTask task : tasks.getList()) {
                if (task.getLastExecutionDate() != null && task.getLastExecutionDate().before(cutoffDate)) {
                    schedulerService.cancelTask(task.getItemId());
                    purgedCount++;
                }
            }

            if (tasks.getList().size() < batchSize) {
                break;
            }
            offset += batchSize;
        }

        System.out.println("Successfully purged " + purgedCount + " completed tasks older than " + daysToKeep + " days.");
        return null;
    }
} 