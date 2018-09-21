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

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;

@Command(scope = "unomi", name = "event-view", description = "This command will dump an Event as a JSON object")
public class EventViewCommand extends OsgiCommandSupport {

    private EventService eventService;
    private DefinitionsService definitionsService;

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    @Argument(index = 0, name = "event", description = "The identifier for the event", required = true, multiValued = false)
    String eventIdentifier;

    @Override
    protected Object doExecute() throws Exception {

        Condition eventCondition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        eventCondition.setParameter("propertyName", "itemId");
        eventCondition.setParameter("comparisonOperator", "equals");
        eventCondition.setParameter("propertyValue", eventIdentifier);

        PartialList<Event> matchingEvents = eventService.searchEvents(eventCondition, 0, 10);
        if (matchingEvents == null || matchingEvents.getTotalSize() != 1) {
            System.out.println("Couldn't find a single event with id=" + eventIdentifier + ". Maybe it wasn't a persistent event ?");
            return null;
        }
        String jsonEvent = CustomObjectMapper.getObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(matchingEvents.get(0));
        System.out.println(jsonEvent);
        return null;
    }
}
