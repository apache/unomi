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
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.common.DataTable;

import java.util.ArrayList;
import java.util.List;

@Command(scope = "unomi", name = "event-search", description = "This commands search for profile events of a certain type by last timestamp in the Apache Unomi Context Server")
@Service
public class EventSearch extends ListCommandSupport  {
    @Reference
    private EventService eventService;

    @Reference
    DefinitionsService definitionsService;

    @Argument(index = 0, name = "profile", description = "The identifier for the profile", required = true, multiValued = false)
    String profileIdentifier;

    @Argument(index = 1, name = "eventType", description = "The type of the event", required = false, multiValued = false)
    String eventTypeId;

    @Argument(index = 2, name = "maxEntries", description = "The maximum number of entries to retrieve (defaults to 100)", required = false, multiValued = false)
    int maxEntries = 100;

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
        Condition booleanCondition = new Condition(definitionsService.getConditionType("booleanCondition"));
        booleanCondition.setParameter("operator", "and");
        List<Condition> subConditions = new ArrayList<>();
        if (profileIdentifier != null) {
            Condition eventProfileIdCondition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
            eventProfileIdCondition.setParameter("propertyName", "profileId");
            eventProfileIdCondition.setParameter("comparisonOperator", "equals");
            eventProfileIdCondition.setParameter("propertyValue", profileIdentifier);
            subConditions.add(eventProfileIdCondition);
        }
        if (eventTypeId != null) {
            Condition eventTypeIdCondition = new Condition(definitionsService.getConditionType("eventTypeCondition"));
            eventTypeIdCondition.setParameter("eventTypeId", eventTypeId);
            subConditions.add(eventTypeIdCondition);
        }
        booleanCondition.setParameter("subConditions", subConditions);
        PartialList<Event> lastEvents = eventService.searchEvents(booleanCondition, 0, maxEntries);
        DataTable dataTable = new DataTable();
        for (Event event : lastEvents.getList()) {
            ArrayList<Comparable> rowData = new ArrayList<>();
            rowData.add(event.getItemId());
            rowData.add(event.getEventType());
            rowData.add(event.getSessionId());
            rowData.add(event.getProfileId());
            rowData.add(event.getTimeStamp().toString());
            rowData.add(event.getSourceId());
            rowData.add(Boolean.toString(event.isPersistent()));
            dataTable.addRow(rowData.toArray(new Comparable[rowData.size()]));
        }
        return dataTable;
    }
}
