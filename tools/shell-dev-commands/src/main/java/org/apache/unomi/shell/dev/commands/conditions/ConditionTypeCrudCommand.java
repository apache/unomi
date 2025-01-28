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
package org.apache.unomi.shell.dev.commands.conditions;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.shell.dev.services.BaseCrudCommand;
import org.apache.unomi.shell.dev.services.CrudCommand;
import org.osgi.service.component.annotations.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component(service = CrudCommand.class, immediate = true)
public class ConditionTypeCrudCommand extends BaseCrudCommand {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> PROPERTY_NAMES = List.of(
            "itemId", "scope", "name", "description", "conditionEvaluator", "queryBuilder", "parameters", "parentCondition"
    );

    @Override
    public String getObjectType() {
        return "conditiontype";
    }

    @Override
    public String[] getHeaders() {
        return new String[] {
            "Activated",
            "Hidden",
            "Read-only",
            "Identifier",
            "Scope",
            "Name",
            "Tags",
            "System tags"
        };
    }

    @Override
    protected PartialList<?> getItems(Query query) {
        List<ConditionType> allTypes = new ArrayList<>(definitionsService.getAllConditionTypes());
        int offset = query.getOffset();
        int pageSize = query.getLimit();
        int totalSize = allTypes.size();

        List<ConditionType> pageTypes = allTypes.subList(
            Math.min(offset, totalSize),
            Math.min(offset + pageSize, totalSize)
        );

        return new PartialList<ConditionType>(pageTypes, offset, pageSize, totalSize, PartialList.Relation.EQUAL);
    }

    @Override
    protected Comparable[] buildRow(Object item) {
        ConditionType type = (ConditionType) item;
        Metadata metadata = type.getMetadata();
        ArrayList<Comparable> rowData = new ArrayList<>();
        rowData.add(metadata.isEnabled() ? "x" : "");
        rowData.add(metadata.isHidden() ? "x" : "");
        rowData.add(metadata.isReadOnly() ? "x" : "");
        rowData.add(metadata.getId());
        rowData.add(metadata.getScope());
        rowData.add(metadata.getName());
        rowData.add(StringUtils.join(metadata.getTags(), ","));
        rowData.add(StringUtils.join(metadata.getSystemTags(), ","));
        return rowData.toArray(new Comparable[0]);
    }

    @Override
    public String create(Map<String, Object> properties) {
        ConditionType conditionType = OBJECT_MAPPER.convertValue(properties, ConditionType.class);
        definitionsService.setConditionType(conditionType);
        return conditionType.getItemId();
    }

    @Override
    public Map<String, Object> read(String id) {
        ConditionType conditionType = definitionsService.getConditionType(id);
        if (conditionType == null) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(conditionType, Map.class);
    }

    @Override
    public void update(String id, Map<String, Object> properties) {
        ConditionType conditionType = definitionsService.getConditionType(id);
        if (conditionType == null) {
            throw new IllegalArgumentException("Condition type with id '" + id + "' not found");
        }
        ConditionType updatedConditionType = OBJECT_MAPPER.convertValue(properties, ConditionType.class);
        updatedConditionType.setItemId(id);
        definitionsService.setConditionType(updatedConditionType);
    }

    @Override
    public void delete(String id) {
        definitionsService.removeConditionType(id);
    }

    @Override
    public List<String> completePropertyNames(String prefix) {
        return PROPERTY_NAMES.stream()
                .filter(name -> name.startsWith(prefix))
                .collect(Collectors.toList());
    }

    @Override
    public String getPropertiesHelp() {
        return "Required properties:\n" +
               "- itemId (string): Unique identifier for the condition type\n" +
               "- scope (string): Scope of the condition type\n" +
               "- name (string): Human-readable name\n" +
               "\n" +
               "Optional properties:\n" +
               "- description (string): Description of the condition type\n" +
               "- conditionEvaluator (string): Name of the condition evaluator implementation\n" +
               "- queryBuilder (string): Name of the query builder implementation\n" +
               "- parameters (array): List of parameters, each containing:\n" +
               "  - id (string): Parameter identifier\n" +
               "  - type (string): Parameter type\n" +
               "  - multivalued (boolean): Whether the parameter accepts multiple values\n" +
               "  - defaultValue (any): Default value for the parameter\n" +
               "- parentCondition (object): Parent condition definition\n" +
               "- enabled (boolean): Whether the condition type is enabled (default: true)\n" +
               "- hidden (boolean): Whether the condition type is hidden (default: false)\n" +
               "- readOnly (boolean): Whether the condition type is read-only (default: false)\n" +
               "- tags (array): List of tags for the condition type\n" +
               "- systemTags (array): List of system tags for the condition type";
    }
}
