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
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.common.DataTable;

import java.util.ArrayList;

@Command(scope = "segment", name = "list", description = "This will list all the segments present in the Apache Unomi Context Server")
public class SegmentListCommand extends ListCommandSupport {

    private SegmentService segmentService;

    public void setSegmentService(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

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
        PartialList<Metadata> segmentMetadatas = segmentService.getSegmentMetadatas(0, -1, null);

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
