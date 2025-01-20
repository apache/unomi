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
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.table.ShellTable;
import org.apache.unomi.api.campaigns.Campaign;
import org.apache.unomi.shell.commands.BaseCommand;
import org.apache.unomi.shell.completers.CampaignCompleter;

/**
 * Shows details about a specific campaign
 */
@Command(scope = "unomi", name = "campaign-show", description = "Shows details about a specific campaign")
@Service
public class CampaignShowCommand extends BaseCommand {

    @Argument(index = 0, name = "campaignId", description = "The identifier of the campaign", required = true)
    @Completion(CampaignCompleter.class)
    String campaignId;

    @Override
    public Object execute() throws Exception {
        Campaign campaign = persistenceService.load(campaignId, Campaign.class);
        if (campaign == null) {
            System.err.println("Campaign with id '" + campaignId + "' not found.");
            return null;
        }

        ShellTable table = new ShellTable();
        table.column("Property");
        table.column("Value");

        table.addRow().addContent("ID", campaign.getItemId());
        table.addRow().addContent("Name", campaign.getMetadata().getName());
        table.addRow().addContent("Description", campaign.getMetadata().getDescription());
        table.addRow().addContent("Scope", campaign.getMetadata().getScope());
        table.addRow().addContent("Start Date", campaign.getStartDate() != null ? campaign.getStartDate().toString() : "");
        table.addRow().addContent("End Date", campaign.getEndDate() != null ? campaign.getEndDate().toString() : "");
        table.addRow().addContent("Enabled", campaign.getMetadata().isEnabled());
        table.addRow().addContent("Primary Goal", campaign.getPrimaryGoal());
        table.addRow().addContent("Cost", campaign.getCost());
        table.addRow().addContent("Currency", campaign.getCurrency());

        table.print(System.out);
        return null;
    }
} 