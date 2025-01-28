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
package org.apache.unomi.shell.dev.commands.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.api.Parameter;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Scope;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.ScopeService;
import org.apache.unomi.shell.dev.services.BaseCrudCommand;
import org.apache.unomi.shell.dev.services.CrudCommand;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component(service = CrudCommand.class, immediate = true)
public class ActionTypeCrudCommand extends BaseCrudCommand {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> PROPERTY_NAMES = List.of(
        "itemId", "name", "description", "scope", "tags", "systemTags", "parameters"
    );

    @Reference
    private ScopeService scopeService;

    @Override
    public String getObjectType() {
        return "actiontype";
    }

    @Override
    public String[] getHeaders() {
        return new String[] {
            "Identifier",
            "Name",
            "Description",
            "Scope",
            "Tags",
            "System Tags",
            "Parameters",
            "Action Executor"
        };
    }

    @Override
    protected PartialList<?> getItems(Query query) {
        List<ActionType> actionTypes = new ArrayList<>(definitionsService.getAllActionTypes());

        // Apply query limit
        Integer offset = query.getOffset();
        Integer limit = query.getLimit();
        int start = offset == null ? 0 : offset;
        int size = limit == null ? actionTypes.size() : limit;
        int end = Math.min(start + size, actionTypes.size());

        List<ActionType> pagedActionTypes = actionTypes.subList(start, end);
        return new PartialList<>(pagedActionTypes, start, pagedActionTypes.size(), actionTypes.size(), PartialList.Relation.EQUAL);
    }

    @Override
    protected String[] buildRow(Object item) {
        ActionType actionType = (ActionType) item;
        return new String[] {
            actionType.getItemId(),
            actionType.getMetadata().getName(),
            actionType.getMetadata().getDescription(),
            actionType.getMetadata().getScope(),
            String.join(",", actionType.getMetadata().getTags()),
            String.join(",", actionType.getMetadata().getSystemTags()),
            String.join(",", actionType.getParameters().stream().map(Parameter::getId).collect(Collectors.toList())),
            actionType.getActionExecutor()
        };
    }

    @Override
    public String create(Map<String, Object> properties) {
        ActionType actionType = OBJECT_MAPPER.convertValue(properties, ActionType.class);
        definitionsService.setActionType(actionType);
        return actionType.getItemId();
    }

    @Override
    public Map<String, Object> read(String id) {
        ActionType actionType = definitionsService.getActionType(id);
        if (actionType != null) {
            return OBJECT_MAPPER.convertValue(actionType, Map.class);
        }
        return null;
    }

    @Override
    public void update(String id, Map<String, Object> properties) {
        ActionType existing = definitionsService.getActionType(id);
        if (existing == null) {
            throw new IllegalArgumentException("Action type not found: " + id);
        }

        ActionType updated = OBJECT_MAPPER.convertValue(properties, ActionType.class);
        updated.setItemId(id);
        definitionsService.setActionType(updated);
    }

    @Override
    public void delete(String id) {
        definitionsService.removeActionType(id);
    }

    @Override
    public String getPropertiesHelp() {
        return String.join("\n",
            "Required properties:",
            "- itemId: Identifier for the action type",
            "- name: Name of the action type",
            "",
            "Optional properties:",
            "- description: Description of the action type",
            "- scope: Scope of the action type",
            "- tags: List of tags",
            "- systemTags: List of system tags",
            "- parameters: Map of parameter definitions"
        );
    }

    @Override
    public List<String> completePropertyNames(String prefix) {
        return PROPERTY_NAMES.stream()
                .filter(name -> name.startsWith(prefix))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> completePropertyValue(String propertyName, String prefix) {
        if ("scope".equals(propertyName)) {
            return scopeService.getScopes().stream()
                    .map(Scope::getItemId)
                    .filter(id -> id.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public void setScopeService(ScopeService scopeService) {
        this.scopeService = scopeService;
    }
}
