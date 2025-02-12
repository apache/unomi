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
package org.apache.unomi.shell.dev.commands.campaigns;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.campaigns.events.CampaignEvent;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.GoalsService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.shell.dev.services.BaseCrudCommand;
import org.apache.unomi.shell.dev.services.CrudCommand;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A command to perform CRUD operations on campaign events
 */
@Component(service = CrudCommand.class, immediate = true)
public class CampaignEventCrudCommand extends BaseCrudCommand {

    private static final ObjectMapper OBJECT_MAPPER = new CustomObjectMapper();
    private static final List<String> PROPERTY_NAMES = List.of(
        "itemId", "name", "description", "scope", "campaignId", "eventDate", "cost", "currency", "timezone"
    );

    @Reference
    private GoalsService goalsService;

    @Override
    public String getObjectType() {
        return "campaignevent";
    }

    @Override
    public String[] getHeaders() {
        return new String[]{"ID", "Name", "Description", "Campaign ID", "Event Date", "Cost", "Currency", "Timezone"};
    }

    @Override
    protected PartialList<?> getItems(Query query) {
        return goalsService.getEvents(query);
    }

    @Override
    protected Comparable[] buildRow(Object item) {
        CampaignEvent event = (CampaignEvent) item;
        return new Comparable[]{
            event.getItemId(),
            event.getMetadata().getName(),
            event.getMetadata().getDescription(),
            event.getCampaignId(),
            event.getEventDate() != null ? event.getEventDate().toString() : "",
            event.getCost() != null ? event.getCost().toString() : "",
            event.getCurrency(),
            event.getTimezone()
        };
    }

    @Override
    public Map<String, Object> read(String id) {
        // There's no direct method to get a single campaign event, so we need to query for it
        Query query = new Query();
        Condition condition = new Condition();
        condition.setConditionType(definitionsService.getConditionType("matchAllCondition"));
        condition.setParameter("operator", "and");
        condition.setParameter("subConditions", new ArrayList<>());
        query.setCondition(condition);

        PartialList<CampaignEvent> events = goalsService.getEvents(query);
        CampaignEvent event = events.getList().stream()
            .filter(e -> e.getItemId().equals(id))
            .findFirst()
            .orElse(null);

        if (event == null) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(event, Map.class);
    }

    @Override
    public String create(Map<String, Object> properties) {
        CampaignEvent event = OBJECT_MAPPER.convertValue(properties, CampaignEvent.class);
        goalsService.setCampaignEvent(event);
        return event.getItemId();
    }

    @Override
    public void update(String id, Map<String, Object> properties) {
        // First check if the event exists
        if (read(id) == null) {
            return;
        }

        CampaignEvent updatedEvent = OBJECT_MAPPER.convertValue(properties, CampaignEvent.class);
        updatedEvent.setItemId(id);
        goalsService.setCampaignEvent(updatedEvent);
    }

    @Override
    public void delete(String id) {
        // First check if the event exists
        if (read(id) != null) {
            goalsService.removeCampaignEvent(id);
        }
    }

    @Override
    public List<String> completePropertyNames(String prefix) {
        return PROPERTY_NAMES.stream()
            .filter(name -> name.startsWith(prefix))
            .collect(Collectors.toList());
    }

    @Override
    public String getPropertiesHelp() {
        return String.join("\n",
            "Required properties:",
            "- itemId: The unique identifier of the campaign event",
            "- name: The name of the campaign event",
            "- description: The description of the campaign event",
            "- campaignId: The ID of the campaign this event belongs to",
            "",
            "Optional properties:",
            "- scope: The scope of the campaign event (defaults to systemscope)",
            "- eventDate: The date of the event (ISO-8601 format)",
            "- cost: The cost associated with this event",
            "- currency: The currency for the event cost",
            "- timezone: The timezone for the event"
        );
    }
}
