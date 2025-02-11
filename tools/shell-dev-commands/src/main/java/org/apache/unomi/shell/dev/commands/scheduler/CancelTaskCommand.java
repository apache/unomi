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

@Command(scope = "unomi", name = "task-cancel", description = "Cancels a scheduled task")
@Service
public class CancelTaskCommand implements Action {

    @Reference
    private SchedulerService schedulerService;

    @Argument(index = 0, name = "taskId", description = "The ID of the task to cancel", required = true)
    private String taskId;

    @Override
    public Object execute() throws Exception {
        schedulerService.cancelTask(taskId);
        System.out.println("Task " + taskId + " has been cancelled successfully.");
        return null;
    }
} 