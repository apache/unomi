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
import org.apache.karaf.shell.commands.Command;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.rules.RuleStatistics;
import org.apache.unomi.api.services.RulesService;
import org.apache.unomi.common.DataTable;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

@Command(scope = "unomi", name = "rule-list", description = "This will list all the rules deployed in the Apache Unomi Context Server")
public class RuleListCommand extends ListCommandSupport {

    private RulesService rulesService;

    public void setRulesService(RulesService rulesService) {
        this.rulesService = rulesService;
    }

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
        Set<Metadata> ruleMetadatas = rulesService.getRuleMetadatas();
        Map<String,RuleStatistics> allRuleStatistics = rulesService.getAllRuleStatistics();

        DataTable dataTable = new DataTable();
        for (Metadata ruleMetadata : ruleMetadatas) {
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
