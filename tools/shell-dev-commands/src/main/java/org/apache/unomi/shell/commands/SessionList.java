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

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.common.DataTable;

import java.util.ArrayList;

@Command(scope = "unomi", name = "session-list", description = "This commands lists the latest sessions updated in the Apache Unomi Context Server")
@Service
public class SessionList extends ListCommandSupport {

    @Reference
    ProfileService profileService;

    @Reference
    DefinitionsService definitionsService;

    @Argument(index = 0, name = "maxEntries", description = "The maximum number of entries to retrieve (defaults to 100)", required = false, multiValued = false)
    int maxEntries = 100;

    @java.lang.Override
    protected String[] getHeaders() {
        return new String[] {
                "ID",
                "Scope",
                "Last event",
                "Duration",
                "Profile",
                "Timestamp"
        };
    }

    @java.lang.Override
    protected DataTable buildDataTable() {
        Query query = new Query();
        query.setSortby("lastEventDate:desc");
        query.setLimit(maxEntries);
        Condition matchAllCondition = new Condition(definitionsService.getConditionType("matchAllCondition"));
        query.setCondition(matchAllCondition);
        PartialList<Session> lastModifiedProfiles = profileService.searchSessions(query);
        DataTable dataTable = new DataTable();
        for (Session session : lastModifiedProfiles.getList()) {
            ArrayList<Comparable> rowData = new ArrayList<>();
            rowData.add(session.getItemId());
            rowData.add(session.getScope());
            rowData.add(session.getLastEventDate());
            rowData.add(session.getDuration());
            rowData.add(session.getProfileId());
            rowData.add(session.getTimeStamp());
            dataTable.addRow(rowData.toArray(new Comparable[rowData.size()]));
        }
        return dataTable;
    }
}
