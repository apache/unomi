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

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.table.Row;
import org.apache.karaf.shell.table.ShellTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Command(scope = "metrics", name = "caller-status", description = "This command will list all the caller configurations, or change the caller status of a specific metric")
public class CallerStatusCommand extends MetricsCommandSupport {

    @Option(name = "--no-format", description = "Disable table rendered output", required = false, multiValued = false)
    boolean noFormat;

    @Argument(index = 0, name = "metricName", description = "The identifier for the metric", required = false, multiValued = false)
    String metricName;

    @Argument(index = 1, name = "status", description = "The new status for the metric's caller tracing", required = false, multiValued = false)
    Boolean metricStatus;

    @Override
    protected Object doExecute() throws Exception {
        if (metricName != null && metricStatus != null) {
            metricsService.setCallerActivated(metricName, metricStatus);
            System.out.println("Metric callers " + metricName + " set to " + metricStatus);
            return null;
        }
        Map<String,Boolean> callersStatus = metricsService.getCallersStatus();
        ShellTable shellTable = new ShellTable();
        shellTable.column("Metric");
        shellTable.column("Activated");

        for (Map.Entry<String,Boolean> callerStatusEntry : callersStatus.entrySet()) {
            List<Object> rowData = new ArrayList<Object>();
            rowData.add(callerStatusEntry.getKey());
            rowData.add(callerStatusEntry.getValue() ? "x" : "");
            Row row = shellTable.addRow();
            row.addContent(rowData);
        }

        shellTable.print(System.out, !noFormat);
        return null;
    }
}
