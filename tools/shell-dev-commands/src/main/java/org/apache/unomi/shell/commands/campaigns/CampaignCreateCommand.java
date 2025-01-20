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
package org.apache.unomi.shell.commands.campaigns;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.campaigns.Campaign;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.shell.commands.BaseCommand;
import org.apache.unomi.shell.completers.GoalCompleter;

import java.text.SimpleDateFormat;

/**
 * Creates a new campaign
 */
@Command(scope = "unomi", name = "campaign-create", description = "Creates a new campaign")
@Service
public class CampaignCreateCommand extends BaseCommand {

    @Reference
    private DefinitionsService definitionsService;

    @Argument(index = 0, name = "campaignId", description = "The identifier of the campaign", required = true)
    String campaignId;

    @Argument(index = 1, name = "name", description = "The name of the campaign", required = true)
    String name;

    @Option(name = "--description", description = "The description of the campaign", required = false)
    String description;

    @Option(name = "--scope", description = "The scope of the campaign", required = false)
    String scope = "systemsite";

    @Option(name = "--primary-goal", description = "The primary goal of the campaign", required = false)
    @Completion(GoalCompleter.class)
    String primaryGoal;

    @Option(name = "--start-date", description = "The start date of the campaign (yyyy-MM-dd)", required = false)
    String startDate;

    @Option(name = "--end-date", description = "The end date of the campaign (yyyy-MM-dd)", required = false)
    String endDate;

    @Option(name = "--cost", description = "The cost of the campaign", required = false)
    Double cost;

    @Option(name = "--currency", description = "The currency for the campaign cost", required = false)
    String currency = "USD";

    @Option(name = "--disabled", description = "Whether the campaign should be disabled", required = false)
    boolean disabled = false;

    @Override
    public Object execute() throws Exception {
        Campaign campaign = new Campaign(new Metadata(campaignId, name, description, scope));
        campaign.getMetadata().setEnabled(!disabled);

        if (primaryGoal != null) {
            campaign.setPrimaryGoal(primaryGoal);
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        if (startDate != null) {
            campaign.setStartDate(dateFormat.parse(startDate));
        }
        if (endDate != null) {
            campaign.setEndDate(dateFormat.parse(endDate));
        }

        if (cost != null) {
            campaign.setCost(cost);
            campaign.setCurrency(currency);
        }

        persistenceService.save(campaign);
        System.out.println("Campaign '" + campaignId + "' created successfully.");
        return null;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }
}
