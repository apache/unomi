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
package org.apache.unomi.shell.dev.commands.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.shell.dev.services.BaseCrudCommand;
import org.apache.unomi.shell.dev.services.CrudCommand;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component(service = CrudCommand.class, immediate = true)
public class EventCrudCommand extends BaseCrudCommand {

    private static final ObjectMapper OBJECT_MAPPER = new CustomObjectMapper();
    private static final List<String> PROPERTY_NAMES = List.of(
        "itemId", "eventType", "scope", "source", "target", "properties", "persistent"
    );
    private static final List<String> EVENT_TYPES = List.of(
        "view", "form", "login", "sessionCreated", "sessionReassigned", "profileUpdated", "incrementTrait",
        "modifyConsent", "updateProperties", "identify", "impersonate", "matching"
    );

    @Reference
    private EventService eventService;

    @Override
    public String getObjectType() {
        return "event";
    }

    @Override
    public String[] getHeaders() {
        return new String[] {
            "Identifier",
            "Event Type",
            "Scope",
            "Source ID",
            "Target ID",
            "Profile ID",
            "Session ID",
            "Timestamp"
        };
    }

    @Override
    protected String getSortBy() {
        return "timeStamp:desc";
    }

    @Override
    protected PartialList<?> getItems(Query query) {
        return eventService.search(query);
    }

    @Override
    protected Comparable[] buildRow(Object item) {
        Event event = (Event) item;
        ArrayList<Comparable> rowData = new ArrayList<>();
        rowData.add(event.getItemId());
        rowData.add(event.getEventType());
        rowData.add(event.getScope());
        rowData.add(event.getSource() != null ? event.getSource().getItemId() : "");
        rowData.add(event.getTarget() != null ? event.getTarget().getItemId() : "");
        rowData.add(event.getProfileId());
        rowData.add(event.getSessionId());
        rowData.add(event.getTimeStamp());
        return rowData.toArray(new Comparable[0]);
    }

    @Override
    public String create(Map<String, Object> properties) {
        Event event = OBJECT_MAPPER.convertValue(properties, Event.class);
        eventService.send(event);
        return event.getItemId();
    }

    @Override
    public Map<String, Object> read(String id) {
        Event event = eventService.getEvent(id);
        if (event == null) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(event, Map.class);
    }

    @Override
    public void update(String id, Map<String, Object> properties) {
        properties.put("itemId", id);
        Event event = OBJECT_MAPPER.convertValue(properties, Event.class);
        eventService.send(event);
    }

    @Override
    public void delete(String id) {
        eventService.deleteEvent(id);
    }

    @Override
    public String getPropertiesHelp() {
        return String.join("\n",
            "Required properties:",
            "- itemId: Event ID (string)",
            "- eventType: Type of event",
            "- scope: Event scope",
            "- source: Source object (Item)",
            "- target: Target object (Item)",
            "",
            "Optional properties:",
            "- properties: Map of event properties",
            "- persistent: Whether event should be persisted (boolean)",
            "",
            "Common event types:",
            "- view: Page/content view",
            "- form: Form submission",
            "- login: User login",
            "- sessionCreated: New session",
            "- sessionReassigned: Session reassigned",
            "- profileUpdated: Profile update",
            "- incrementTrait: Increment profile trait",
            "- modifyConsent: Consent change",
            "- updateProperties: Property update",
            "- identify: Profile identification",
            "- impersonate: Profile impersonation",
            "- matching: Profile matching"
        );
    }

    @Override
    public List<String> completePropertyNames(String prefix) {
        return PROPERTY_NAMES.stream()
                .filter(name -> name.startsWith(prefix))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> completePropertyValue(String propertyName, String prefix) {
        if ("eventType".equals(propertyName)) {
            return EVENT_TYPES.stream()
                    .filter(type -> type.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

}
