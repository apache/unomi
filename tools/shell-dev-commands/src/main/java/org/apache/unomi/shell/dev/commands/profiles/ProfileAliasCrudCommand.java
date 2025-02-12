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
package org.apache.unomi.shell.dev.commands.profiles;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.ProfileAlias;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.shell.dev.services.BaseCrudCommand;
import org.apache.unomi.shell.dev.services.CrudCommand;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A command to perform CRUD operations on profile aliases
 */
@Component(service = CrudCommand.class, immediate = true)
public class ProfileAliasCrudCommand extends BaseCrudCommand {

    private static final ObjectMapper OBJECT_MAPPER = new CustomObjectMapper();
    private static final List<String> PROPERTY_NAMES = List.of(
        "itemId", "profileID", "clientID", "creationTime", "modifiedTime"
    );

    @Reference
    private ProfileService profileService;

    @Override
    public String getObjectType() {
        return "profilealias";
    }

    @Override
    public String[] getHeaders() {
        return new String[]{"ID", "Profile ID", "Client ID", "Created", "Modified"};
    }

    @Override
    protected PartialList<?> getItems(Query query) {
        // Since there's no direct method to search all aliases, we'll use findProfileAliases with null profileId
        return profileService.findProfileAliases(null, query.getOffset(), query.getLimit(), query.getSortby());
    }

    @Override
    protected Comparable[] buildRow(Object item) {
        ProfileAlias alias = (ProfileAlias) item;
        return new Comparable[]{
            alias.getItemId(),
            alias.getProfileID(),
            alias.getClientID(),
            alias.getCreationTime() != null ? alias.getCreationTime().toString() : "",
            alias.getModifiedTime() != null ? alias.getModifiedTime().toString() : ""
        };
    }

    @Override
    public Map<String, Object> read(String id) {
        // Since there's no direct method to get a single alias, we'll need to search for it
        // We'll use findProfileAliases with a small limit since we know the ID
        PartialList<ProfileAlias> aliases = profileService.findProfileAliases(null, 0, 1, null);
        ProfileAlias alias = aliases.getList().stream()
            .filter(a -> a.getItemId().equals(id))
            .findFirst()
            .orElse(null);

        if (alias == null) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(alias, Map.class);
    }

    @Override
    public String create(Map<String, Object> properties) {
        ProfileAlias alias = OBJECT_MAPPER.convertValue(properties, ProfileAlias.class);
        // Set creation and modification time if not provided
        if (alias.getCreationTime() == null) {
            alias.setCreationTime(new Date());
        }
        if (alias.getModifiedTime() == null) {
            alias.setModifiedTime(alias.getCreationTime());
        }

        // Add the alias to the profile
        profileService.addAliasToProfile(alias.getProfileID(), alias.getItemId(), alias.getClientID());
        return alias.getItemId();
    }

    @Override
    public void update(String id, Map<String, Object> properties) {
        // First check if the alias exists
        if (read(id) == null) {
            return;
        }

        // Remove the old alias and add the new one
        ProfileAlias oldAlias = OBJECT_MAPPER.convertValue(read(id), ProfileAlias.class);
        profileService.removeAliasFromProfile(oldAlias.getProfileID(), oldAlias.getItemId(), oldAlias.getClientID());

        ProfileAlias updatedAlias = OBJECT_MAPPER.convertValue(properties, ProfileAlias.class);
        updatedAlias.setItemId(id);
        updatedAlias.setModifiedTime(new Date());
        profileService.addAliasToProfile(updatedAlias.getProfileID(), updatedAlias.getItemId(), updatedAlias.getClientID());
    }

    @Override
    public void delete(String id) {
        // First check if the alias exists
        Map<String, Object> aliasData = read(id);
        if (aliasData != null) {
            ProfileAlias alias = OBJECT_MAPPER.convertValue(aliasData, ProfileAlias.class);
            profileService.removeAliasFromProfile(alias.getProfileID(), alias.getItemId(), alias.getClientID());
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
            "- itemId: The unique identifier of the alias",
            "- profileID: The identifier of the profile this alias belongs to",
            "- clientID: The identifier of the client that created this alias",
            "",
            "Optional properties:",
            "- creationTime: The creation timestamp (ISO-8601 format)",
            "- modifiedTime: The last modification timestamp (ISO-8601 format)"
        );
    }
}
