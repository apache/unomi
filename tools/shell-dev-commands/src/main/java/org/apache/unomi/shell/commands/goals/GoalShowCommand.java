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
package org.apache.unomi.shell.commands.goals;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.table.ShellTable;
import org.apache.unomi.api.goals.Goal;
import org.apache.unomi.shell.commands.BaseCommand;
import org.apache.unomi.shell.completers.GoalCompleter;

/**
 * Shows details about a specific goal
 */
@Command(scope = "unomi", name = "goal-show", description = "Shows details about a specific goal")
@Service
public class GoalShowCommand extends BaseCommand {

    @Argument(index = 0, name = "goalId", description = "The identifier of the goal", required = true)
    @Completion(GoalCompleter.class)
    String goalId;

    @Override
    public Object execute() throws Exception {
        Goal goal = persistenceService.load(goalId, Goal.class);
        if (goal == null) {
            System.err.println("Goal with id '" + goalId + "' not found.");
            return null;
        }

        ShellTable table = new ShellTable();
        table.column("Property");
        table.column("Value");

        table.addRow().addContent("ID", goal.getItemId());
        table.addRow().addContent("Name", goal.getMetadata().getName());
        table.addRow().addContent("Description", goal.getMetadata().getDescription());
        table.addRow().addContent("Scope", goal.getMetadata().getScope());
        table.addRow().addContent("Start Event", goal.getStartEvent() != null ? goal.getStartEvent().toString() : "");
        table.addRow().addContent("Target Event", goal.getTargetEvent() != null ? goal.getTargetEvent().toString() : "");
        table.addRow().addContent("Campaign", goal.getCampaignId());
        table.addRow().addContent("Enabled", goal.getMetadata().isEnabled());

        table.print(System.out);
        return null;
    }
} 