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
import org.apache.unomi.api.Event;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.common.DataTable;

import java.util.ArrayList;

@Command(scope = "unomi", name = "event-list", description = "This commands lists the latest events updated in the Apache Unomi Context Server")
@Service
public class EventList extends ListCommandSupport {

    @Reference
    private EventService eventService;

    @Reference
    DefinitionsService definitionsService;

    @Argument(index = 0, name = "maxEntries", description = "The maximum number of entries to retrieve (defaults to 100)", required = false, multiValued = false)
    int maxEntries = 100;

    @Argument(index = 1, name = "eventType", description = "If specified, will filter the event list by the given event type", required = false, multiValued = false)
    String eventType = null;

    String[] columnHeaders = new String[] {
            "ID",
            "Type",
            "Session",
            "Profile",
            "Timestamp",
            "Scope",
            "Persistent"
    };

    @Override
    protected String[] getHeaders() {
        return columnHeaders;
    }

    @Override
    protected DataTable buildDataTable() {
        Condition condition = new Condition(definitionsService.getConditionType("matchAllCondition"));
        if (eventType != null) {
            condition = new Condition(definitionsService.getConditionType("eventTypeCondition"));
            condition.setParameter("eventTypeId", eventType);
        }
        Query query = new Query();
        query.setLimit(maxEntries);
        query.setCondition(condition);
        query.setSortby("timeStamp:desc");
        PartialList<Event> lastEvents = eventService.search(query);
        DataTable dataTable = new DataTable();
        for (Event event : lastEvents.getList()) {
            ArrayList<Comparable> rowData = new ArrayList<>();
            rowData.add(event.getItemId());
            rowData.add(event.getEventType());
            rowData.add(event.getSessionId());
            rowData.add(event.getProfileId());
            rowData.add(event.getTimeStamp().toString());
            rowData.add(event.getScope());
            rowData.add(Boolean.toString(event.isPersistent()));
            dataTable.addRow(rowData.toArray(new Comparable[rowData.size()]));
        }
        return dataTable;
    }
}
