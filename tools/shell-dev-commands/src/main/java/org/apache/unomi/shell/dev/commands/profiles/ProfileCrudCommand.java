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
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.shell.dev.services.BaseCrudCommand;
import org.apache.unomi.shell.dev.services.CrudCommand;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component(service = CrudCommand.class, immediate = true)
public class ProfileCrudCommand extends BaseCrudCommand {

    private static final ObjectMapper OBJECT_MAPPER = new CustomObjectMapper();
    private static final List<String> PROPERTY_NAMES = Arrays.asList(
        "itemId",
        "properties.firstName",
        "properties.lastName",
        "properties.email",
        "segments",
        "scores",
        "consents",
        "systemProperties.lastUpdated"
    );

    private static final String[] TABLE_HEADERS = {
        "Identifier",
        "First Name",
        "Last Name",
        "Email",
        "Segments",
        "Last Updated",
        "Tenant"
    };

    @Reference
    private ProfileService profileService;

    @Override
    public String getObjectType() {
        return "profile";
    }

    @Override
    public String[] getHeaders() {
        return TABLE_HEADERS;
    }

    @Override
    protected String getSortBy() {
        return "systemProperties.lastUpdated:desc,properties.lastVisit:desc";
    }

    @Override
    protected PartialList<?> getItems(Query query) {
        return profileService.search(query, Profile.class);
    }

    @Override
    protected Comparable[] buildRow(Object item) {
        Profile profile = (Profile) item;
        Map<String, Object> systemProperties = profile.getSystemProperties();
        return new Comparable[] {
            profile.getItemId(),
            (String) profile.getProperty("firstName"),
            (String) profile.getProperty("lastName"),
            (String) profile.getProperty("email"),
            profile.getSegments().size(),
            systemProperties != null ? (Comparable) systemProperties.get("lastUpdated") : null,
            profile.getScope()
        };
    }

    @Override
    public String create(Map<String, Object> properties) {
        Profile profile = new Profile();
        profile.setProperties(properties);
        profileService.save(profile);
        return profile.getItemId();
    }

    @Override
    public Map<String, Object> read(String id) {
        Profile profile = profileService.load(id);
        return profile != null ? OBJECT_MAPPER.convertValue(profile, Map.class) : null;
    }

    @Override
    public void update(String id, Map<String, Object> properties) {
        Profile profile = profileService.load(id);
        if (profile == null) {
            throw new IllegalArgumentException("Profile not found with ID: " + id);
        }
        profile.setProperties(properties);
        profileService.save(profile);
    }

    @Override
    public void delete(String id) {
        profileService.delete(id, false);
    }

    @Override
    public String getPropertiesHelp() {
        return "Required properties:\n" +
               "  - itemId: Unique identifier for the profile\n" +
               "\n" +
               "Optional properties:\n" +
               "  - properties.firstName: First name\n" +
               "  - properties.lastName: Last name\n" +
               "  - properties.email: Email address\n" +
               "  - properties.phoneNumber: Phone number\n" +
               "  - properties.address: Address\n" +
               "  - properties.company: Company name\n" +
               "  - properties.jobTitle: Job title\n" +
               "  - consents: Map of consent IDs to consent status\n" +
               "  - scores: Map of scoring plan IDs to scores";
    }

    @Override
    public List<String> completePropertyNames(String prefix) {
        return PROPERTY_NAMES.stream()
            .filter(name -> name.startsWith(prefix))
            .collect(Collectors.toList());
    }
}
