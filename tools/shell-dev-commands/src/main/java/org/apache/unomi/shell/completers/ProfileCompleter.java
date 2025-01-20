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
package org.apache.unomi.shell.completers;

import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Completer;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.completers.StringsCompleter;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.ProfileService;

import java.util.List;

@Service
public class ProfileCompleter implements Completer {

    private static final int DEFAULT_LIMIT = 50;

    @Reference
    private ProfileService profileService;

    @Reference
    private DefinitionsService definitionsService;

    @Override
    public int complete(Session session, CommandLine commandLine, List<String> candidates) {
        StringsCompleter delegate = new StringsCompleter();
        
        try {
            // Create query matching the profile-list command
            Query query = new Query();
            query.setSortby("systemProperties.lastUpdated:desc,properties.lastVisit:desc");
            query.setLimit(DEFAULT_LIMIT);
            Condition matchAllCondition = new Condition(definitionsService.getConditionType("matchAllCondition"));
            query.setCondition(matchAllCondition);
            
            // Get the latest profiles
            PartialList<Profile> profiles = profileService.search(query, Profile.class);
            
            // Add profile IDs to completer
            for (Profile profile : profiles.getList()) {
                delegate.getStrings().add(profile.getItemId());
            }
        } catch (Exception e) {
            // Log error or handle exception
        }
        
        return delegate.complete(session, commandLine, candidates);
    }
} 