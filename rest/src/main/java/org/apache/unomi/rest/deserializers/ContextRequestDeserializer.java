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
package org.apache.unomi.rest.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.unomi.api.ContextRequest;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.services.PersonalizationService;
import org.apache.unomi.api.services.SchemaRegistry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Custom deserializer for ContextRequest that do validate the object using JSon Schema
 */
public class ContextRequestDeserializer extends StdDeserializer<ContextRequest> {

    private final SchemaRegistry schemaRegistry;

    public ContextRequestDeserializer(SchemaRegistry schemaRegistry) {
        this(null,  schemaRegistry);
    }

    public ContextRequestDeserializer(Class<ContextRequest> vc, SchemaRegistry schemaRegistry) {
        super(vc);
        this.schemaRegistry = schemaRegistry;
    }

    @Override
    public ContextRequest deserialize(JsonParser jsonParser, DeserializationContext context) throws IOException, JsonProcessingException {
        JsonNode node = jsonParser.getCodec().readTree(jsonParser);
        // Validate schema on it
        if (schemaRegistry.isValid(node, "https://unomi.apache.org/schemas/json/contextrequest/1-0-0")) {
            ContextRequest cr = new ContextRequest();
            if (node.get("requiredSessionProperties") != null) {
                cr.setRequireSegments(node.get("requiredSessionProperties").booleanValue());
            }
            if (node.get("requiredProfileProperties") != null) {
                List<String> profileProperties = new ArrayList<>();
                node.get("requiredProfileProperties").elements().forEachRemaining(el -> profileProperties.add(el.textValue()));
                cr.setRequiredProfileProperties(profileProperties);
            }
            if (node.get("requiredSessionProperties") != null) {
                List<String> requiredSessionProperties = new ArrayList<>();
                node.get("requiredSessionProperties").elements().forEachRemaining(el -> requiredSessionProperties.add(el.textValue()));
                cr.setRequiredProfileProperties(requiredSessionProperties);
                cr.setRequireScores(node.get("requireScores").booleanValue());
            }
            if (node.get("events") != null) {
                ArrayNode events = (ArrayNode) node.get("events");
                List<Event> filteredEvents = new ArrayList<>();
                for (JsonNode event : events) {
                    if (schemaRegistry.isValid(event, "https://unomi.apache.org/schemas/json/events/" + event.get("eventType").textValue() + "/1-0-0")) {
                        filteredEvents.add(jsonParser.getCodec().treeToValue(event, Event.class));
                    }
                }
                cr.setEvents(filteredEvents);
            }
            if (node.get("filters") != null) {
                ArrayNode filters = (ArrayNode) node.get("filters");
                List<PersonalizationService.PersonalizedContent> f = new ArrayList<>();
                filters.elements().forEachRemaining(el -> {
                    try {
                        jsonParser.getCodec().treeToValue(el, PersonalizationService.PersonalizedContent.class);
                    } catch (JsonProcessingException e) {
                        // Unable to deserialize, ignore the entry
                    }
                });
                cr.setFilters(f);
            }
            if (node.get("personalizations") != null) {
                ArrayNode personalizations = (ArrayNode) node.get("personalizations");
                List<PersonalizationService.PersonalizationRequest> p = new ArrayList<>();
                personalizations.elements().forEachRemaining(el -> {
                    try {
                        jsonParser.getCodec().treeToValue(el, PersonalizationService.PersonalizationRequest.class);
                    } catch (JsonProcessingException e) {
                        // Unable to deserialize, ignore the entry
                    }
                });
                cr.setPersonalizations(p);
            }
            if (node.get("profileOverrides") != null) {
                cr.setProfileOverrides(jsonParser.getCodec().treeToValue(node.get("profileOverrides"), Profile.class));
            }
            if (node.get("sessionPropertiesOverrides") != null) {
                jsonParser.getCodec().treeToValue(node.get("sessionPropertiesOverrides"), Map.class);
            }
            if (node.get("sessionId") != null) {
                cr.setSessionId(node.get("sessionId").textValue());
            }
            if (node.get("profileId") != null) {
                cr.setProfileId(node.get("profileId").textValue());
            }
            if (node.get("clientId") != null) {
                cr.setClientId(node.get("clientId").textValue());
            }
            return cr;
        }
        throw new IOException("Unable to deserialize provided context request");
    }
}
/**
 * private Item source;
 *     private boolean requireSegments;
 *     private List<String> requiredProfileProperties;
 *     private List<String> requiredSessionProperties;
 *     private boolean requireScores;
 *     private List<Event> events;
 *     private List<PersonalizationService.PersonalizedContent> filters;
 *     private List<PersonalizationService.PersonalizationRequest> personalizations;
 *     private Profile profileOverrides;
 *     private Map<String, Object> sessionPropertiesOverrides;
 *
 *     @Pattern(regexp = ValidationPattern.TEXT_VALID_CHARACTERS_PATTERN)
 *     private String sessionId;
 *
 *     @Pattern(regexp = ValidationPattern.TEXT_VALID_CHARACTERS_PATTERN)
 *     private String profileId;
 *
 *     private String clientId;
 */