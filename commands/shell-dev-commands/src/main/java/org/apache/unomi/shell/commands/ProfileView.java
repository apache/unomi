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

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;

@Command(scope = "unomi", name = "profile-view", description = "This command will dump a profile as a JSON string")
@Service
public class ProfileView implements Action {

    @Reference
    ProfileService profileService;

    @Argument(index = 0, name = "profile", description = "The identifier for the profile", required = true, multiValued = false)
    String profileIdentifier;

    public Object execute() throws Exception {
        Profile profile = profileService.load(profileIdentifier);
        if (profile == null) {
            System.out.println("Couldn't find a profile with id=" + profileIdentifier);
            return null;
        }
        String jsonProfile = CustomObjectMapper.getObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(profile);
        System.out.println(jsonProfile);
        return null;
    }
}
