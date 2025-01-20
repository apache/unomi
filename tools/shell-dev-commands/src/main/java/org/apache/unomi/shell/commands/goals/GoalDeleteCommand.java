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
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.apache.unomi.api.goals.Goal;
import org.apache.unomi.shell.commands.BaseCommand;
import org.apache.unomi.shell.completers.GoalCompleter;

/**
 * Deletes a goal
 */
@Command(scope = "unomi", name = "goal-delete", description = "Deletes a goal")
@Service
public class GoalDeleteCommand extends BaseCommand {

    @Reference
    Session session;

    @Argument(index = 0, name = "goalId", description = "The identifier of the goal", required = true)
    @Completion(GoalCompleter.class)
    String goalId;

    @Option(name = "--force", description = "Skip confirmation", required = false)
    boolean force = false;

    @Override
    public Object execute() throws Exception {
        Goal goal = persistenceService.load(goalId, Goal.class);
        if (goal == null) {
            System.err.println("Goal with id '" + goalId + "' not found.");
            return null;
        }

        if (!force && !confirm(session, "Are you sure you want to delete goal '" + goalId + "'?")) {
            System.out.println("Goal deletion cancelled");
            return null;
        }

        persistenceService.remove(goalId, Goal.class);
        System.out.println("Goal '" + goalId + "' deleted successfully.");
        return null;
    }
} 