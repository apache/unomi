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
import org.apache.unomi.shell.commands.BaseCommand;
import org.apache.unomi.shell.completers.CampaignCompleter;

/**
 * Deletes a campaign
 */
@Command(scope = "unomi", name = "campaign-delete", description = "Deletes a campaign")
@Service
public class CampaignDeleteCommand extends BaseCommand {

    @Reference
    Session session;

    @Argument(index = 0, name = "campaignId", description = "The identifier of the campaign", required = true)
    @Completion(CampaignCompleter.class)
    String campaignId;

    @Option(name = "--force", description = "Skip confirmation", required = false)
    boolean force = false;

    @Override
    public Object execute() throws Exception {
        Campaign campaign = persistenceService.load(campaignId, Campaign.class);
        if (campaign == null) {
            System.err.println("Campaign with id '" + campaignId + "' not found.");
            return null;
        }

        if (!force && !confirm(session, "Are you sure you want to delete campaign '" + campaignId + "'?")) {
            System.out.println("Campaign deletion cancelled");
            return null;
        }

        persistenceService.remove(campaignId, Campaign.class);
        System.out.println("Campaign '" + campaignId + "' deleted successfully.");
        return null;
    }
} 