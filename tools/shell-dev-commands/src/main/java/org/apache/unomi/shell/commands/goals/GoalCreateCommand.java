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
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.goals.Goal;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.shell.commands.BaseCommand;

/**
 * Creates a new goal
 */
@Command(scope = "unomi", name = "goal-create", description = "Creates a new goal")
@Service
public class GoalCreateCommand extends BaseCommand {

    @Reference
    private DefinitionsService definitionsService;

    @Argument(index = 0, name = "goalId", description = "The identifier of the goal", required = true)
    String goalId;

    @Argument(index = 1, name = "name", description = "The name of the goal", required = true)
    String name;

    @Option(name = "--description", description = "The description of the goal", required = false)
    String description;

    @Option(name = "--scope", description = "The scope of the goal", required = false)
    String scope = "systemsite";

    @Option(name = "--campaign", description = "The campaign ID this goal belongs to", required = false)
    String campaignId;

    @Option(name = "--disabled", description = "Whether the goal should be disabled", required = false)
    boolean disabled = false;

    @Override
    public Object execute() throws Exception {
        Goal goal = new Goal(new Metadata(goalId, name, description, scope));
        goal.getMetadata().setEnabled(!disabled);
        
        if (campaignId != null) {
            goal.setCampaignId(campaignId);
        }

        persistenceService.save(goal);
        System.out.println("Goal '" + goalId + "' created successfully.");
        return null;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }
} 