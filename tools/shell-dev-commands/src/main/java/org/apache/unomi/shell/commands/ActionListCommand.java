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
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.apache.karaf.shell.table.Row;
import org.apache.karaf.shell.table.ShellTable;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.common.DataTable;

import java.util.ArrayList;
import java.util.Collection;

@Command(scope = "action", name = "list", description = "This will list all the actions deployed in the Apache Unomi Context Server")
public class ActionListCommand extends OsgiCommandSupport{

    private DefinitionsService definitionsService;

    @Option(name = "--csv", description = "Output table in CSV format", required = false, multiValued = false)
    boolean csv;

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    @Override
    protected Object doExecute() throws Exception {
        Collection<ActionType> allActions = definitionsService.getAllActionTypes();

        String[] headers = {
                "Id",
                "Name",
                "System tags"
        };

        DataTable dataTable = new DataTable();

        for (ActionType actionType : allActions) {
            ArrayList<Comparable> rowData = new ArrayList<>();
            rowData.add(actionType.getItemId());
            rowData.add(actionType.getMetadata().getName());
            rowData.add(StringUtils.join(actionType.getMetadata().getSystemTags(), ","));
            dataTable.addRow(rowData.toArray(new Comparable[rowData.size()]));
        }

        dataTable.sort(new DataTable.SortCriteria(1, DataTable.SortOrder.ASCENDING));

        if (csv) {
            System.out.println(dataTable.toCSV(headers));
            return null;
        }

        ShellTable shellTable = new ShellTable();
        for (String header : headers) {
            shellTable.column(header);
        }
        for (DataTable.Row dataTableRow : dataTable.getRows()) {
            ArrayList<Object> rowData = new ArrayList<Object>();
            for (int i=0 ; i < dataTable.getMaxColumns(); i++) {
                rowData.add(dataTableRow.getData(i));
            }
            Row row = shellTable.addRow();
            row.addContent(rowData);
        }

        shellTable.print(System.out);
        return null;
    }
}
