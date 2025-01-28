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
package org.apache.unomi.shell.dev.commands.sessions;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.shell.dev.services.BaseCrudCommand;
import org.apache.unomi.shell.dev.services.CrudCommand;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A command to perform CRUD operations on sessions
 */
@Component(service = CrudCommand.class, immediate = true)
public class SessionCrudCommand extends BaseCrudCommand {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> PROPERTY_NAMES = List.of(
        "itemId", "profileId", "properties", "systemProperties", "timeStamp", "scope", "lastEventDate", "size", "duration", "originEventTypes", "originEventIds"
    );

    @Reference
    private ProfileService profileService;

    @Override
    public String getObjectType() {
        return "session";
    }

    @Override
    public String[] getHeaders() {
        return new String[]{"ID", "Profile ID", "Scope", "Time Stamp", "Last Event", "Duration", "Size"};
    }

    @Override
    protected PartialList<?> getItems(Query query) {
        return profileService.searchSessions(query);
    }

    @Override
    protected Comparable[] buildRow(Object item) {
        Session session = (Session) item;
        return new Comparable[]{
            session.getItemId(),
            session.getProfileId(),
            session.getScope(),
            session.getTimeStamp() != null ? session.getTimeStamp().toString() : "",
            session.getLastEventDate() != null ? session.getLastEventDate().toString() : "",
            String.valueOf(session.getDuration()),
            String.valueOf(session.getSize())
        };
    }

    @Override
    public Map<String, Object> read(String id) {
        Session session = profileService.loadSession(id, null);
        if (session == null) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(session, Map.class);
    }

    @Override
    public String create(Map<String, Object> properties) {
        Session session = OBJECT_MAPPER.convertValue(properties, Session.class);
        profileService.saveSession(session);
        return session.getItemId();
    }

    @Override
    public void update(String id, Map<String, Object> properties) {
        // First check if the session exists
        if (read(id) == null) {
            return;
        }

        Session updatedSession = OBJECT_MAPPER.convertValue(properties, Session.class);
        updatedSession.setItemId(id);
        profileService.saveSession(updatedSession);
    }

    @Override
    public void delete(String id) {
        // First check if the session exists
        if (read(id) != null) {
            profileService.deleteSession(id);
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
            "- itemId: The unique identifier of the session",
            "- profileId: The identifier of the associated profile",
            "- timeStamp: The session creation timestamp (ISO-8601 format)",
            "- scope: The scope of the session",
            "",
            "Optional properties:",
            "- properties: A map of custom properties for the session",
            "- systemProperties: A map of system properties for internal use",
            "- lastEventDate: The date of the last event in the session (ISO-8601 format)",
            "- size: The size of the session",
            "- duration: The duration of the session in milliseconds",
            "- originEventTypes: List of event types that caused the session creation",
            "- originEventIds: List of event IDs that caused the session creation"
        );
    }
}
