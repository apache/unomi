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
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.rules.RuleStatistics;
import org.apache.unomi.api.services.RulesService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.shell.dev.services.BaseCrudCommand;
import org.apache.unomi.shell.dev.services.CrudCommand;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A command to perform CRUD operations on rule statistics
 */
@Component(service = CrudCommand.class, immediate = true)
public class RuleStatisticsCrudCommand extends BaseCrudCommand {

    private static final ObjectMapper OBJECT_MAPPER = new CustomObjectMapper();
    private static final List<String> PROPERTY_NAMES = List.of(
        "itemId", "executionCount", "localExecutionCount", "conditionsTime", "localConditionsTime", "actionsTime", "localActionsTime", "lastSyncDate"
    );

    @Reference
    private RulesService rulesService;

    @Override
    public String getObjectType() {
        return "rulestats";
    }

    @Override
    public String[] getHeaders() {
        return new String[]{"ID", "Executions", "Local Executions", "Conditions Time", "Local Conditions Time", "Actions Time", "Local Actions Time", "Last Sync"};
    }

    @Override
    protected PartialList<?> getItems(Query query) {
        // Get all rules and their statistics
        Map<String, RuleStatistics> statisticsMap = rulesService.getAllRuleStatistics();
        List<RuleStatistics> statistics = new ArrayList<>(statisticsMap.values());
        return new PartialList<>(statistics, 0, statistics.size(), statistics.size(), PartialList.Relation.EQUAL);
    }

    @Override
    protected Comparable[] buildRow(Object item) {
        RuleStatistics stats = (RuleStatistics) item;
        return new Comparable[]{
            stats.getItemId(),
            stats.getExecutionCount(),
            stats.getLocalExecutionCount(),
            stats.getConditionsTime(),
            stats.getLocalConditionsTime(),
            stats.getActionsTime(),
            stats.getLocalActionsTime(),
            stats.getLastSyncDate() != null ? stats.getLastSyncDate().toString() : ""
        };
    }

    @Override
    public Map<String, Object> read(String id) {
        RuleStatistics stats = rulesService.getRuleStatistics(id);
        if (stats == null) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(stats, Map.class);
    }

    @Override
    public String create(Map<String, Object> properties) {
        // Note: RulesService doesn't provide a direct way to create rule statistics
        // They are automatically managed by the rules engine
        return null;
    }

    @Override
    public void update(String id, Map<String, Object> properties) {
        // Note: RulesService doesn't provide a direct way to update rule statistics
        // They are automatically managed by the rules engine
    }

    @Override
    public void delete(String id) {
        // Note: RulesService doesn't provide a direct way to delete individual rule statistics
        // They are automatically managed by the rules engine
        // You can use resetAllRuleStatistics() to reset all statistics to zero
        rulesService.resetAllRuleStatistics();
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
            "Rule statistics are automatically managed by the rules engine and cannot be directly modified.",
            "You can view statistics using the following properties:",
            "",
            "- itemId: The unique identifier of the rule statistics (matches the rule ID)",
            "- executionCount: Total number of rule executions in the cluster",
            "- localExecutionCount: Number of rule executions on this node since last sync",
            "- conditionsTime: Total time spent evaluating conditions in the cluster (ms)",
            "- localConditionsTime: Time spent evaluating conditions on this node since last sync (ms)",
            "- actionsTime: Total time spent executing actions in the cluster (ms)",
            "- localActionsTime: Time spent executing actions on this node since last sync (ms)",
            "- lastSyncDate: Date of the last synchronization with the cluster",
            "",
            "Note: Use 'unomi:crud rulestats reset' to reset all rule statistics to zero."
        );
    }
}
