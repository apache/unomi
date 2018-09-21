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
import org.apache.unomi.api.Session;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;

@Command(scope = "unomi", name = "session-view", description = "This command will dump a session as a JSON string")
public class SessionViewCommand extends OsgiCommandSupport {
    private ProfileService profileService;

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Argument(index = 0, name = "session", description = "The identifier for the session", required = true, multiValued = false)
    String sessionIdentifier;

    @Override
    protected Object doExecute() throws Exception {
        Session session = profileService.loadSession(sessionIdentifier, null);
        if (session == null) {
            System.out.println("Couldn't find a session with id=" + sessionIdentifier);
            return null;
        }
        String jsonSession = CustomObjectMapper.getObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(session);
        System.out.println(jsonSession);
        return null;
    }
}
