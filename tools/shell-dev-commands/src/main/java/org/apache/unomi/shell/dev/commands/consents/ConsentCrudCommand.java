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
package org.apache.unomi.shell.dev.commands.consents;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.api.Consent;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.shell.dev.services.BaseCrudCommand;
import org.apache.unomi.shell.dev.services.CrudCommand;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A command to perform CRUD operations on consents
 */
@Component(service = CrudCommand.class, immediate = true)
public class ConsentCrudCommand extends BaseCrudCommand {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private static final List<String> PROPERTY_NAMES = List.of(
        "profileId", "scope", "typeIdentifier", "status", "statusDate", "revokeDate"
    );

    @Reference
    private ProfileService profileService;

    @Override
    public String getObjectType() {
        return "consent";
    }

    @Override
    public String[] getHeaders() {
        return new String[]{"Profile ID", "Scope", "Type", "Status", "Status Date", "Revoke Date"};
    }

    @Override
    protected PartialList<?> getItems(Query query) {
        // Since consents are stored within profiles, we need to get all profiles and extract their consents
        PartialList<Profile> profiles = profileService.search(query, Profile.class);
        List<Map<String, Object>> consents = new ArrayList<>();

        for (Profile profile : profiles.getList()) {
            Map<String, Object> profileProperties = profile.getProperties();
            if (profileProperties.containsKey("consents")) {
                @SuppressWarnings("unchecked")
                Map<String, Consent> profileConsents = (Map<String, Consent>) profileProperties.get("consents");
                for (Map.Entry<String, Consent> entry : profileConsents.entrySet()) {
                    Map<String, Object> consentMap = entry.getValue().toMap(DATE_FORMAT);
                    consentMap.put("profileId", profile.getItemId());
                    consents.add(consentMap);
                }
            }
        }

        return new PartialList<Map<String, Object>>(consents, profiles.getOffset(), profiles.getPageSize(), profiles.getTotalSize(), PartialList.Relation.EQUAL);
    }

    @Override
    protected Comparable[] buildRow(Object item) {
        @SuppressWarnings("unchecked")
        Map<String, Object> consentMap = (Map<String, Object>) item;
        return new Comparable[]{
            String.valueOf(consentMap.get("profileId")),
            String.valueOf(consentMap.get("scope")),
            String.valueOf(consentMap.get("typeIdentifier")),
            String.valueOf(consentMap.get("status")),
            String.valueOf(consentMap.get("statusDate")),
            String.valueOf(consentMap.get("revokeDate"))
        };
    }

    @Override
    public Map<String, Object> read(String id) {
        // The ID format is expected to be "profileId:typeIdentifier"
        String[] parts = id.split(":");
        if (parts.length != 2) {
            return null;
        }

        String profileId = parts[0];
        String typeIdentifier = parts[1];

        Profile profile = profileService.load(profileId);
        if (profile == null) {
            return null;
        }

        Map<String, Object> profileProperties = profile.getProperties();
        if (!profileProperties.containsKey("consents")) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Consent> consents = (Map<String, Consent>) profileProperties.get("consents");
        Consent consent = consents.get(typeIdentifier);

        if (consent == null) {
            return null;
        }

        Map<String, Object> consentMap = consent.toMap(DATE_FORMAT);
        consentMap.put("profileId", profileId);
        return consentMap;
    }

    @Override
    public String create(Map<String, Object> properties) {
        String profileId = (String) properties.remove("profileId");
        if (profileId == null) {
            return null;
        }

        Profile profile = profileService.load(profileId);
        if (profile == null) {
            return null;
        }

        try {
            Consent consent = new Consent(properties, DATE_FORMAT);

            Map<String, Object> profileProperties = profile.getProperties();
            @SuppressWarnings("unchecked")
            Map<String, Consent> consents = profileProperties.containsKey("consents") ?
                (Map<String, Consent>) profileProperties.get("consents") : new HashMap<>();

            consents.put(consent.getTypeIdentifier(), consent);
            profileProperties.put("consents", consents);
            profile.setProperties(profileProperties);

            profileService.save(profile);
            return profileId + ":" + consent.getTypeIdentifier();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void update(String id, Map<String, Object> properties) {
        // The ID format is expected to be "profileId:typeIdentifier"
        String[] parts = id.split(":");
        if (parts.length != 2) {
            return;
        }

        String profileId = parts[0];
        String typeIdentifier = parts[1];

        Profile profile = profileService.load(profileId);
        if (profile == null) {
            return;
        }

        try {
            Consent consent = new Consent(properties, DATE_FORMAT);

            Map<String, Object> profileProperties = profile.getProperties();
            @SuppressWarnings("unchecked")
            Map<String, Consent> consents = profileProperties.containsKey("consents") ?
                (Map<String, Consent>) profileProperties.get("consents") : new HashMap<>();

            consents.put(typeIdentifier, consent);
            profileProperties.put("consents", consents);
            profile.setProperties(profileProperties);

            profileService.save(profile);
        } catch (Exception e) {
            // Handle error
        }
    }

    @Override
    public void delete(String id) {
        // The ID format is expected to be "profileId:typeIdentifier"
        String[] parts = id.split(":");
        if (parts.length != 2) {
            return;
        }

        String profileId = parts[0];
        String typeIdentifier = parts[1];

        Profile profile = profileService.load(profileId);
        if (profile == null) {
            return;
        }

        Map<String, Object> profileProperties = profile.getProperties();
        if (profileProperties.containsKey("consents")) {
            @SuppressWarnings("unchecked")
            Map<String, Consent> consents = (Map<String, Consent>) profileProperties.get("consents");
            consents.remove(typeIdentifier);
            profileProperties.put("consents", consents);
            profile.setProperties(profileProperties);
            profileService.save(profile);
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
            "- profileId: The identifier of the profile this consent belongs to",
            "- typeIdentifier: The unique identifier for the consent type",
            "- scope: The scope for this consent",
            "- status: The consent status (GRANTED, DENIED, or REVOKED)",
            "",
            "Optional properties:",
            "- statusDate: The date from which this consent applies (ISO-8601 format)",
            "- revokeDate: The date at which this consent will expire (ISO-8601 format)"
        );
    }
}
