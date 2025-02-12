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
import org.apache.unomi.api.campaigns.Campaign;
import org.apache.unomi.api.campaigns.CampaignDetail;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.GoalsService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.shell.dev.services.BaseCrudCommand;
import org.apache.unomi.shell.dev.services.CrudCommand;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A command to perform CRUD operations on campaigns
 */
@Component(service = CrudCommand.class, immediate = true)
public class CampaignCrudCommand extends BaseCrudCommand {

    private static final ObjectMapper OBJECT_MAPPER = new CustomObjectMapper();
    private static final List<String> PROPERTY_NAMES = List.of(
        "itemId", "name", "description", "scope", "startDate", "endDate", "cost", "currency", "primaryGoal", "goals", "entryCondition", "enabled"
    );

    @Reference
    private GoalsService goalsService;

    @Override
    public String getObjectType() {
        return "campaign";
    }

    @Override
    public String[] getHeaders() {
        return new String[]{"ID", "Name", "Description", "Start Date", "End Date", "Cost", "Currency", "Primary Goal", "Enabled"};
    }

    @Override
    protected PartialList<?> getItems(Query query) {
        return goalsService.getCampaignDetails(query);
    }

    @Override
    protected Comparable[] buildRow(Object item) {
        CampaignDetail detail = (CampaignDetail) item;
        Campaign campaign = detail.getCampaign();
        String primaryGoalName = "";
        if (campaign.getPrimaryGoal() != null) {
            // Get the goal details to get its name
            org.apache.unomi.api.goals.Goal primaryGoal = goalsService.getGoal(campaign.getPrimaryGoal());
            if (primaryGoal != null) {
                primaryGoalName = primaryGoal.getMetadata().getName();
            }
        }
        return new Comparable[]{
            campaign.getItemId(),
            campaign.getMetadata().getName(),
            campaign.getMetadata().getDescription(),
            campaign.getStartDate() != null ? campaign.getStartDate().toString() : "",
            campaign.getEndDate() != null ? campaign.getEndDate().toString() : "",
            campaign.getCost() != null ? campaign.getCost().toString() : "",
            campaign.getCurrency(),
            primaryGoalName,
            campaign.getMetadata().isEnabled()
        };
    }

    @Override
    public Map<String, Object> read(String id) {
        Campaign campaign = goalsService.getCampaign(id);
        if (campaign == null) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(campaign, Map.class);
    }

    @Override
    public String create(Map<String, Object> properties) {
        Campaign campaign = OBJECT_MAPPER.convertValue(properties, Campaign.class);
        goalsService.setCampaign(campaign);
        return campaign.getItemId();
    }

    @Override
    public void update(String id, Map<String, Object> properties) {
        Campaign existingCampaign = goalsService.getCampaign(id);
        if (existingCampaign == null) {
            return;
        }

        Campaign updatedCampaign = OBJECT_MAPPER.convertValue(properties, Campaign.class);
        updatedCampaign.setItemId(id);
        goalsService.setCampaign(updatedCampaign);
    }

    @Override
    public void delete(String id) {
        Campaign campaign = goalsService.getCampaign(id);
        if (campaign != null) {
            goalsService.removeCampaign(id);
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
            "- itemId: The unique identifier of the campaign",
            "- name: The name of the campaign",
            "- description: The description of the campaign",
            "",
            "Optional properties:",
            "- scope: The scope of the campaign (defaults to systemscope)",
            "- startDate: The start date of the campaign (ISO-8601 format)",
            "- endDate: The end date of the campaign (ISO-8601 format)",
            "- cost: The cost of the campaign",
            "- currency: The currency for the campaign cost",
            "- primaryGoal: The primary goal of the campaign",
            "- goals: List of goals associated with the campaign",
            "- entryCondition: The condition that determines when a visitor enters the campaign",
            "- enabled: Whether the campaign is enabled (true/false)"
        );
    }
}
