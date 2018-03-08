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
package org.apache.unomi.metrics.commands;

import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.support.table.Row;
import org.apache.karaf.shell.support.table.ShellTable;
import org.apache.unomi.common.DataTable;
import org.apache.unomi.metrics.Metric;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Command(scope = "metrics", name = "list", description = "This will list all the metrics")
public class ListCommand extends MetricsCommandSupport {

    @Option(name = "--csv", description = "Output table in CSV format", required = false, multiValued = false)
    boolean csv;

    @Override
    protected Object doExecute() throws Exception {

        System.out.println("Metrics service status: " + (metricsService.isActivated() ? "active" : "inactive"));

        Map<String,Metric> metrics = metricsService.getMetrics();

        String[] headers = {
                "Name",
                "Callers",
                "Count",
                "Time [ms]"
        };

        DataTable dataTable = new DataTable();
        for (Map.Entry<String,Metric> metricEntry : metrics.entrySet()) {
            Metric metric = metricEntry.getValue();
            dataTable.addRow(metric.getName(), metric.getCallerCounts().size(), metric.getTotalCount(), metric.getTotalTime());
        }
        dataTable.sort(new DataTable.SortCriteria(3, DataTable.SortOrder.DESCENDING),
                new DataTable.SortCriteria(2, DataTable.SortOrder.DESCENDING),
                new DataTable.SortCriteria(0, DataTable.SortOrder.ASCENDING));

        if (csv) {
            System.out.println(dataTable.toCSV(headers));
            return null;
        }

        ShellTable shellTable = new ShellTable();

        for (String header : headers) {
            shellTable.column(header);
        }

        for (DataTable.Row dataTableRow :dataTable.getRows()) {
            List<Object> rowData = new ArrayList<Object>();
            rowData.add(dataTableRow.getData(0));
            rowData.add(dataTableRow.getData(1));
            rowData.add(dataTableRow.getData(2));
            rowData.add(dataTableRow.getData(3));
            Row row = shellTable.addRow();
            row.addContent(rowData);
        }
        shellTable.print(System.out);
        return null;
    }
}
