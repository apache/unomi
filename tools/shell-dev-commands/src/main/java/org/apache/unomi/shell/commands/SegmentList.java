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
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.common.DataTable;

import java.util.ArrayList;

@Command(scope = "unomi", name = "segment-list", description = "This will list all the segments present in the Apache Unomi Context Server")
@Service
public class SegmentList extends ListCommandSupport {

    @Reference
    SegmentService segmentService;

    @Argument(index = 0, name = "maxEntries", description = "The maximum number of entries to retrieve (defaults to 100)", required = false, multiValued = false)
    int maxEntries = 100;

    @Override
    protected String[] getHeaders() {
        return new String[] {
                "Enabled",
                "Hidden",
                "Id",
                "Scope",
                "Name",
                "System tags"
        };
    }

    @Override
    protected DataTable buildDataTable() {
        PartialList<Metadata> segmentMetadatas = segmentService.getSegmentMetadatas(0, maxEntries, null);

        DataTable dataTable = new DataTable();
        for (Metadata metadata : segmentMetadatas.getList()) {
            ArrayList<Comparable> rowData = new ArrayList<>();
            rowData.add(metadata.isEnabled() ? "x" : "");
            rowData.add(metadata.isHidden() ? "x" : "");
            rowData.add(metadata.getId());
            rowData.add(metadata.getScope());
            rowData.add(metadata.getName());
            rowData.add(StringUtils.join(metadata.getSystemTags(), ","));
            dataTable.addRow(rowData.toArray(new Comparable[rowData.size()]));
        }

        dataTable.sort(new DataTable.SortCriteria(4, DataTable.SortOrder.ASCENDING));
        return dataTable;
    }

}
