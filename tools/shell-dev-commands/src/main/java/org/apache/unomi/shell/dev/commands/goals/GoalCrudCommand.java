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
package org.apache.unomi.shell.dev.commands.goals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.goals.Goal;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.GoalsService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.shell.dev.services.BaseCrudCommand;
import org.apache.unomi.shell.dev.services.CrudCommand;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A command to perform CRUD operations on goals
 */
@Component(service = CrudCommand.class, immediate = true)
public class GoalCrudCommand extends BaseCrudCommand {

    private static final ObjectMapper OBJECT_MAPPER = new CustomObjectMapper();
    private static final List<String> PROPERTY_NAMES = List.of(
        "itemId", "name", "description", "scope", "campaignId", "enabled"
    );

    @Reference
    private GoalsService goalsService;

    @Override
    public String getObjectType() {
        return "goal";
    }

    @Override
    public String[] getHeaders() {
        return new String[]{"ID", "Name", "Description", "Scope", "Campaign ID", "Enabled"};
    }

    @Override
    protected PartialList<?> getItems(Query query) {
        // Convert Set<Metadata> to PartialList for consistency
        Set<Metadata> metadatas = goalsService.getGoalMetadatas(query);
        List<Goal> goals = metadatas.stream()
            .map(metadata -> goalsService.getGoal(metadata.getId()))
            .filter(goal -> goal != null)
            .collect(Collectors.toList());
        return new PartialList<>(goals, goals.size(), 0, goals.size(), null);
    }

    @Override
    protected Comparable[] buildRow(Object item) {
        Goal goal = (Goal) item;
        return new Comparable[]{
            goal.getItemId(),
            goal.getMetadata().getName(),
            goal.getMetadata().getDescription(),
            goal.getMetadata().getScope(),
            goal.getCampaignId(),
            goal.getMetadata().isEnabled()
        };
    }

    @Override
    public Map<String, Object> read(String id) {
        Goal goal = goalsService.getGoal(id);
        if (goal == null) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(goal, Map.class);
    }

    @Override
    public String create(Map<String, Object> properties) {
        Goal goal = OBJECT_MAPPER.convertValue(properties, Goal.class);
        goalsService.setGoal(goal);
        return goal.getItemId();
    }

    @Override
    public void update(String id, Map<String, Object> properties) {
        Goal existingGoal = goalsService.getGoal(id);
        if (existingGoal == null) {
            return;
        }

        Goal updatedGoal = OBJECT_MAPPER.convertValue(properties, Goal.class);
        updatedGoal.setItemId(id);
        goalsService.setGoal(updatedGoal);
    }

    @Override
    public void delete(String id) {
        Goal goal = goalsService.getGoal(id);
        if (goal != null) {
            goalsService.removeGoal(id);
        }
    }

    @Override
    public List<String> completePropertyNames(String prefix) {
        return PROPERTY_NAMES.stream()
            .filter(name -> name.startsWith(prefix))
            .collect(Collectors.toList());
    }

    @Override
    public String getPropertiesHelp() {
        return String.join("\n",
            "Required properties:",
            "- itemId: The unique identifier of the goal",
            "- name: The name of the goal",
            "- description: The description of the goal",
            "",
            "Optional properties:",
            "- scope: The scope of the goal (defaults to systemscope)",
            "- campaignId: The ID of the associated campaign",
            "- enabled: Whether the goal is enabled (true/false)",
            "- startEvent: The condition that triggers the start of the goal",
            "- targetEvent: The condition that marks the goal as achieved"
        );
    }
}
