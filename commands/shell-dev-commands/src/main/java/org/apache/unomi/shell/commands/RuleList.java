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
package org.apache.unomi.shell.commands;

import org.apache.commons.lang3.StringUtils;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.rules.RuleStatistics;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.RulesService;
import org.apache.unomi.common.DataTable;

import java.util.ArrayList;
import java.util.Map;

@Command(scope = "unomi", name = "rule-list", description = "This will list all the rules deployed in the Apache Unomi Context Server")
@Service
public class RuleList extends ListCommandSupport {

    @Reference
    RulesService rulesService;

    @Reference
    DefinitionsService definitionsService;

    @Argument(index = 0, name = "maxEntries", description = "The maximum number of entries to retrieve (defaults to 100)", required = false, multiValued = false)
    int maxEntries = 100;

    @Override
    protected String[] getHeaders() {
        return new String[] {
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
        };
    }

    @Override
    protected DataTable buildDataTable() {
        Query query = new Query();
        Condition matchAllCondition = new Condition(definitionsService.getConditionType("matchAllCondition"));
        query.setCondition(matchAllCondition);
        query.setLimit(maxEntries);
        PartialList<Metadata> ruleMetadatas = rulesService.getRuleMetadatas(query);
        if (ruleMetadatas.getList().size() != ruleMetadatas.getTotalSize()) {
            System.out.println("WARNING : Only the first " + ruleMetadatas.getPageSize() + " rules have been retrieved, there are " + ruleMetadatas.getTotalSize() + " rules registered in total. Use the maxEntries parameter to retrieve more rules");
        }
        Map<String,RuleStatistics> allRuleStatistics = rulesService.getAllRuleStatistics();

        DataTable dataTable = new DataTable();
        for (Metadata ruleMetadata : ruleMetadatas.getList()) {
            ArrayList<Comparable> rowData = new ArrayList<>();
            String ruleId = ruleMetadata.getId();
            rowData.add(ruleMetadata.isEnabled() ? "x" : "");
            rowData.add(ruleMetadata.isHidden() ? "x" : "");
            rowData.add(ruleMetadata.isReadOnly() ? "x" : "");
            rowData.add(ruleId);
            rowData.add(ruleMetadata.getScope());
            rowData.add(ruleMetadata.getName());
            rowData.add(StringUtils.join(ruleMetadata.getTags(), ","));
            rowData.add(StringUtils.join(ruleMetadata.getSystemTags(), ","));
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
            dataTable.addRow(rowData.toArray(new Comparable[rowData.size()]));
        }
        dataTable.sort(new DataTable.SortCriteria(9, DataTable.SortOrder.DESCENDING),
                new DataTable.SortCriteria(10, DataTable.SortOrder.DESCENDING),
                new DataTable.SortCriteria(5, DataTable.SortOrder.ASCENDING));
        return dataTable;
    }

}
