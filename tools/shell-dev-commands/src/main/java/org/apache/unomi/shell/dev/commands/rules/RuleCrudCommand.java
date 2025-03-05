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
package org.apache.unomi.shell.dev.commands.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.rules.RuleStatistics;
import org.apache.unomi.api.services.RulesService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.shell.dev.services.BaseCrudCommand;
import org.apache.unomi.shell.dev.services.CrudCommand;
import org.apache.unomi.api.conditions.Condition;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component(service = CrudCommand.class, immediate = true)
public class RuleCrudCommand extends BaseCrudCommand {

    private static final ObjectMapper OBJECT_MAPPER = new CustomObjectMapper();
    private static final List<String> PROPERTY_NAMES = List.of(
        "itemId", "name", "description", "priority", "condition", "actions", "metadata"
    );
    private static final List<String> CONDITION_TYPES = List.of(
        "booleanCondition", "profilePropertyCondition", "sessionPropertyCondition", "eventPropertyCondition",
        "pastEventCondition", "matchAllCondition", "notCondition", "orCondition", "andCondition"
    );

    @Reference
    private RulesService rulesService;

    @Override
    public String getObjectType() {
        return "rule";
    }

    @Override
    public String[] getHeaders() {
        return prependTenantIdHeader(new String[] {
            "Activated",
            "Hidden",
            "Read-only",
            "Identifier",
            "Scope",
            "Name",
            "Tags",
            "System tags",
            "Executions",
            "Conditions [ms]",
            "Actions [ms]"
        });
    }

    @Override
    protected PartialList<?> getItems(Query query) {
        return rulesService.getRuleDetails(query);
    }

    @Override
    protected Comparable[] buildRow(Object item) {
        Rule rule = (Rule) item;
        String ruleId = rule.getItemId();
        Map<String,RuleStatistics> allRuleStatistics = rulesService.getAllRuleStatistics();

        ArrayList<Comparable> rowData = new ArrayList<>();
        rowData.add(rule.getMetadata().isEnabled() ? "x" : "");
        rowData.add(rule.getMetadata().isHidden() ? "x" : "");
        rowData.add(rule.getMetadata().isReadOnly() ? "x" : "");
        rowData.add(ruleId);
        rowData.add(rule.getMetadata().getScope());
        rowData.add(rule.getMetadata().getName());
        rowData.add(StringUtils.join(rule.getMetadata().getTags(), ","));
        rowData.add(StringUtils.join(rule.getMetadata().getSystemTags(), ","));
        RuleStatistics ruleStatistics = allRuleStatistics.get(ruleId);
        if (ruleStatistics != null) {
            rowData.add(ruleStatistics.getExecutionCount());
            rowData.add(ruleStatistics.getConditionsTime());
            rowData.add(ruleStatistics.getActionsTime());
        } else {
            rowData.add(0L);
            rowData.add(0L);
            rowData.add(0L);
        }
        return rowData.toArray(new Comparable[0]);
    }

    @Override
    public String create(Map<String, Object> properties) {
        Rule rule = OBJECT_MAPPER.convertValue(properties, Rule.class);
        rulesService.setRule(rule);
        return rule.getItemId();
    }

    @Override
    public Map<String, Object> read(String id) {
        Rule rule = rulesService.getRule(id);
        if (rule == null) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(rule, Map.class);
    }

    @Override
    public void update(String id, Map<String, Object> properties) {
        properties.put("itemId", id);
        Rule rule = OBJECT_MAPPER.convertValue(properties, Rule.class);
        rulesService.setRule(rule);
    }

    @Override
    public void delete(String id) {
        rulesService.removeRule(id);
    }

    @Override
    public String getPropertiesHelp() {
        return String.join("\n",
            "Required properties:",
            "- itemId: Rule ID (string)",
            "- name: Rule name",
            "- condition: Rule condition object",
            "- actions: List of rule actions",
            "",
            "Optional properties:",
            "- description: Rule description",
            "- priority: Rule priority (integer)",
            "- metadata: Rule metadata",
            "",
            "Condition types:",
            "- booleanCondition: Simple true/false condition",
            "- profilePropertyCondition: Match profile property",
            "- sessionPropertyCondition: Match session property",
            "- eventPropertyCondition: Match event property",
            "- pastEventCondition: Match past events",
            "- matchAllCondition: Match all sub-conditions",
            "- notCondition: Negate sub-condition",
            "- orCondition: Match any sub-condition",
            "- andCondition: Match all sub-conditions"
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
        if ("condition.type".equals(propertyName)) {
            return CONDITION_TYPES.stream()
                    .filter(type -> type.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    @Override
    public List<String> completeId(String prefix) {
        // Create a query to find rules that match the prefix
        Query query = new Query();
        query.setLimit(20); // Reasonable limit for auto-completion
        
        // If prefix is not empty, use it to filter results
        if (!prefix.isEmpty()) {
            Condition condition = new Condition(definitionsService.getConditionType("sessionPropertyCondition"));
            condition.setParameter("comparisonOperator", "startsWith");
            condition.setParameter("propertyName", "itemId");
            condition.setParameter("propertyValue", prefix);
            query.setCondition(condition);
        } else {
            // Otherwise, match all
            Condition matchAllCondition = new Condition(definitionsService.getConditionType("matchAllCondition"));
            query.setCondition(matchAllCondition);
        }
        
        // Execute the query and extract rule IDs
        try {
            PartialList<Metadata> metadatas = rulesService.getRuleMetadatas(query);
            return metadatas.getList().stream()
                .map(Metadata::getId)
                .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of(); // Return empty list on error
        }
    }

}
