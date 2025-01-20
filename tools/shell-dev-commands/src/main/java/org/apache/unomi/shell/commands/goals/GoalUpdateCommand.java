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
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.shell.commands.BaseCommand;
import org.apache.unomi.shell.completers.GoalCompleter;

/**
 * Updates an existing goal
 */
@Command(scope = "unomi", name = "goal-update", description = "Updates an existing goal")
@Service
public class GoalUpdateCommand extends BaseCommand {

    @Reference
    private DefinitionsService definitionsService;

    @Reference
    Session session;

    @Argument(index = 0, name = "goalId", description = "The identifier of the goal", required = true)
    @Completion(GoalCompleter.class)
    String goalId;

    @Option(name = "--name", description = "The name of the goal")
    String name;

    @Option(name = "--description", description = "The description of the goal")
    String description;

    @Option(name = "--scope", description = "The scope of the goal")
    String scope;

    @Option(name = "--campaign", description = "The campaign ID this goal belongs to")
    String campaignId;

    @Option(name = "--enabled", description = "Enable the goal")
    boolean enabled = false;

    @Option(name = "--disabled", description = "Disable the goal")
    boolean disabled = false;

    @Override
    public Object execute() throws Exception {
        Goal goal = persistenceService.load(goalId, Goal.class);
        if (goal == null) {
            System.err.println("Goal with id '" + goalId + "' not found.");
            return null;
        }

        boolean modified = false;

        if (name != null) {
            goal.getMetadata().setName(name);
            modified = true;
        }

        if (description != null) {
            goal.getMetadata().setDescription(description);
            modified = true;
        }

        if (scope != null) {
            goal.getMetadata().setScope(scope);
            modified = true;
        }

        if (campaignId != null) {
            goal.setCampaignId(campaignId);
            modified = true;
        }

        if (enabled && disabled) {
            System.err.println("Cannot specify both --enabled and --disabled");
            return null;
        }

        if (enabled || disabled) {
            goal.getMetadata().setEnabled(enabled);
            modified = true;
        }

        if (!modified) {
            System.out.println("No changes specified.");
            return null;
        }

        if (!confirm(session, "Update goal '" + goalId + "'?")) {
            System.out.println("Goal update cancelled");
            return null;
        }

        persistenceService.save(goal);
        System.out.println("Goal '" + goalId + "' updated successfully.");
        return null;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }
} 