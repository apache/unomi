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
import org.apache.karaf.shell.api.console.Session;
import org.apache.unomi.api.campaigns.Campaign;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.shell.commands.BaseCommand;
import org.apache.unomi.shell.completers.CampaignCompleter;
import org.apache.unomi.shell.completers.GoalCompleter;

import java.text.SimpleDateFormat;

/**
 * Updates an existing campaign
 */
@Command(scope = "unomi", name = "campaign-update", description = "Updates an existing campaign")
@Service
public class CampaignUpdateCommand extends BaseCommand {

    @Reference
    private DefinitionsService definitionsService;

    @Reference
    Session session;

    @Argument(index = 0, name = "campaignId", description = "The identifier of the campaign", required = true)
    @Completion(CampaignCompleter.class)
    String campaignId;

    @Option(name = "--name", description = "The name of the campaign")
    String name;

    @Option(name = "--description", description = "The description of the campaign")
    String description;

    @Option(name = "--scope", description = "The scope of the campaign")
    String scope;

    @Option(name = "--primary-goal", description = "The primary goal of the campaign")
    @Completion(GoalCompleter.class)
    String primaryGoal;

    @Option(name = "--start-date", description = "The start date of the campaign (yyyy-MM-dd)")
    String startDate;

    @Option(name = "--end-date", description = "The end date of the campaign (yyyy-MM-dd)")
    String endDate;

    @Option(name = "--cost", description = "The cost of the campaign")
    Double cost;

    @Option(name = "--currency", description = "The currency for the campaign cost")
    String currency;

    @Option(name = "--enabled", description = "Enable the campaign")
    boolean enabled = false;

    @Option(name = "--disabled", description = "Disable the campaign")
    boolean disabled = false;

    @Override
    public Object execute() throws Exception {
        Campaign campaign = persistenceService.load(campaignId, Campaign.class);
        if (campaign == null) {
            System.err.println("Campaign with id '" + campaignId + "' not found.");
            return null;
        }

        boolean modified = false;

        if (name != null) {
            campaign.getMetadata().setName(name);
            modified = true;
        }

        if (description != null) {
            campaign.getMetadata().setDescription(description);
            modified = true;
        }

        if (scope != null) {
            campaign.getMetadata().setScope(scope);
            modified = true;
        }

        if (primaryGoal != null) {
            campaign.setPrimaryGoal(primaryGoal);
            modified = true;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        if (startDate != null) {
            campaign.setStartDate(dateFormat.parse(startDate));
            modified = true;
        }

        if (endDate != null) {
            campaign.setEndDate(dateFormat.parse(endDate));
            modified = true;
        }

        if (cost != null) {
            campaign.setCost(cost);
            modified = true;
        }

        if (currency != null) {
            campaign.setCurrency(currency);
            modified = true;
        }

        if (enabled && disabled) {
            System.err.println("Cannot specify both --enabled and --disabled");
            return null;
        }

        if (enabled || disabled) {
            campaign.getMetadata().setEnabled(enabled);
            modified = true;
        }

        if (!modified) {
            System.out.println("No changes specified.");
            return null;
        }

        if (!confirm(session, "Update campaign '" + campaignId + "'?")) {
            System.out.println("Campaign update cancelled");
            return null;
        }

        persistenceService.save(campaign);
        System.out.println("Campaign '" + campaignId + "' updated successfully.");
        return null;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }
} 