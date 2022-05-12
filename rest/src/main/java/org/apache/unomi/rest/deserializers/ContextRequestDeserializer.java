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
import org.apache.unomi.api.Item;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.services.PersonalizationService;
import org.apache.unomi.rest.exception.InvalidRequestException;
import org.apache.unomi.schema.api.SchemaService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Custom deserializer for ContextRequest that do validate the object using JSON Schema
 */
public class ContextRequestDeserializer extends StdDeserializer<ContextRequest> {

    private final SchemaService schemaService;

    public ContextRequestDeserializer(SchemaService schemaRegistry) {
        this(null, schemaRegistry);
    }

    public ContextRequestDeserializer(Class<ContextRequest> vc, SchemaService schemaRegistry) {
        super(vc);
        this.schemaService = schemaRegistry;
    }

    @Override
    public ContextRequest deserialize(JsonParser jsonParser, DeserializationContext context) throws IOException, JsonProcessingException {
        JsonNode node = jsonParser.getCodec().readTree(jsonParser);
        // Validate schema on it
        if (!schemaService.isValid(node.toString(), "https://unomi.apache.org/schemas/json/contextrequest/1-0-0")) {
            throw new InvalidRequestException("Invalid Context request object", "Invalid received data");
        }
        ContextRequest cr = new ContextRequest();
        if (node.get("source") != null) {
            cr.setSource(jsonParser.getCodec().treeToValue(node.get("source"), Item.class));
        }

        if (node.get("requireSegments") != null) {
            cr.setRequireSegments(node.get("requireSegments").booleanValue());
        }
        final JsonNode requiredProfileProperties = node.get("requiredProfileProperties");
        if (requiredProfileProperties instanceof ArrayNode) {
            List<String> profileProperties = new ArrayList<>();
            requiredProfileProperties.elements().forEachRemaining(el -> profileProperties.add(el.textValue()));
            cr.setRequiredProfileProperties(profileProperties);
        }
        final JsonNode requiredSessionPropertiesNode = node.get("requiredSessionProperties");
        if (requiredSessionPropertiesNode instanceof ArrayNode) {
            List<String> requiredSessionProperties = new ArrayList<>();
            requiredSessionPropertiesNode.elements().forEachRemaining(el -> requiredSessionProperties.add(el.textValue()));
            cr.setRequiredSessionProperties(requiredSessionProperties);
        }
        if (node.get("requireScores") != null) {
            cr.setRequireScores(node.get("requireScores").booleanValue());
        }
        final JsonNode eventsNode = node.get("events");
        if (eventsNode instanceof ArrayNode) {
            ArrayNode events = (ArrayNode) eventsNode;
            List<Event> filteredEvents = new ArrayList<>();
            for (JsonNode event : events) {
                if (schemaService.isValid(event.toString(), "https://unomi.apache.org/schemas/json/events/" + event.get("eventType").textValue() + "/1-0-0")) {
                    filteredEvents.add(jsonParser.getCodec().treeToValue(event, Event.class));
                }
            }
            cr.setEvents(filteredEvents);
        }
        final JsonNode filtersNode = node.get("filters");
        if (filtersNode instanceof ArrayNode) {
            ArrayNode filters = (ArrayNode) filtersNode;
            List<PersonalizationService.PersonalizedContent> f = new ArrayList<>();
            filters.elements().forEachRemaining(el -> {
                try {
                    f.add(jsonParser.getCodec().treeToValue(el, PersonalizationService.PersonalizedContent.class));
                } catch (JsonProcessingException e) {
                    // Unable to deserialize, ignore the entry
                }
            });
            cr.setFilters(f);
        }
        final JsonNode personalizationsNode = node.get("personalizations");
        if (personalizationsNode instanceof ArrayNode) {
            ArrayNode personalizations = (ArrayNode) personalizationsNode;
            List<PersonalizationService.PersonalizationRequest> p = new ArrayList<>();
            personalizations.elements().forEachRemaining(el -> {
                try {
                    p.add(jsonParser.getCodec().treeToValue(el, PersonalizationService.PersonalizationRequest.class));
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
}