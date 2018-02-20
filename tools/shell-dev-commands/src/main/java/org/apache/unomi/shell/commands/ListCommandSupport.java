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

import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.apache.karaf.shell.table.Row;
import org.apache.karaf.shell.table.ShellTable;
import org.apache.unomi.common.DataTable;

import java.util.ArrayList;

/**
 * A utility class to make it easier to build tables for listing Apache Unomi objects.
 */
public abstract class ListCommandSupport extends OsgiCommandSupport {

    @Option(name = "--csv", description = "Output table in CSV format", required = false, multiValued = false)
    boolean csv;

    /**
     * Returns a String array containing the header names for the table
     * @return a String array with the headers that will be used to render the table
     */
    protected abstract String[] getHeaders();

    /**
     * Build a DataTable object that contains all the data for the object. Note that you might want to sort the data
     * inside this method.
     * @return a populated (and optionally sorted) DataTable object ready to be rendered either as a rendered table
     * or as CSV
     */
    protected abstract DataTable buildDataTable();

    @Override
    protected Object doExecute() throws Exception {

        DataTable dataTable = buildDataTable();

        String[] headers = getHeaders();

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
