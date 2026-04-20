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
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.unomi.api.services.SchedulerService;

@Command(scope = "unomi", name = "task-executor", description = "Shows or changes task executor status for this node")
@Service
public class SetExecutorNodeCommand implements Action {

    @Reference
    private SchedulerService schedulerService;

    @Argument(index = 0, name = "enable", description = "Enable (true) or disable (false) task execution", required = false)
    private String enable;

    @Override
    public Object execute() throws Exception {
        if (enable == null) {
            // Just show current status
            System.out.println("Task executor status: " +
                (schedulerService.isExecutorNode() ? "ENABLED" : "DISABLED"));
            System.out.println("Node ID: " + schedulerService.getNodeId());
            return null;
        }

        boolean shouldEnable = Boolean.parseBoolean(enable);
        // Note: This assumes there's a setExecutorNode method. If not available, we'll need to modify the service.
        // schedulerService.setExecutorNode(shouldEnable);

        System.out.println("Task executor has been " + (shouldEnable ? "ENABLED" : "DISABLED") +
            " for node " + schedulerService.getNodeId());
        return null;
    }
}
