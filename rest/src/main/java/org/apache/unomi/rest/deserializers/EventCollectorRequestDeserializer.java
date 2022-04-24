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
import org.apache.unomi.api.Event;
import org.apache.unomi.api.EventsCollectorRequest;
import org.apache.unomi.api.services.SchemaRegistry;
import org.apache.unomi.rest.validation.request.InvalidRequestException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom deserializer for EventCollectorRequest that do validate the events using JSon Schema
 */
public class EventCollectorRequestDeserializer extends StdDeserializer<EventsCollectorRequest> {

    private final SchemaRegistry schemaRegistry;

    public EventCollectorRequestDeserializer(SchemaRegistry schemaRegistry) {
        this(null, schemaRegistry);
    }

    public EventCollectorRequestDeserializer(Class<EventsCollectorRequest> vc, SchemaRegistry schemaRegistry) {
        super(vc);
        this.schemaRegistry = schemaRegistry;
    }

    @Override
    public EventsCollectorRequest deserialize(JsonParser jsonParser, DeserializationContext context) throws IOException, JsonProcessingException {
        JsonNode node = jsonParser.getCodec().readTree(jsonParser);
        if (!schemaRegistry.isValid(node, "https://unomi.apache.org/schemas/json/eventscollectorrequest/1-0-0")) {
            throw new InvalidRequestException("Invalid received data", "Invalid received data");
        }

        // Validate schema on each event
        List<Event> filteredEvents = new ArrayList<>();
        final JsonNode eventsNode = node.get("events");
        if (eventsNode instanceof ArrayNode) {
            for (JsonNode event : eventsNode) {
                if (schemaRegistry.isValid(event, "https://unomi.apache.org/schemas/json/events/" + event.get("eventType").textValue() + "/1-0-0")) {
                    filteredEvents.add(jsonParser.getCodec().treeToValue(event, Event.class));
                }
            }
        }
        EventsCollectorRequest eventsCollectorRequest = new EventsCollectorRequest();
        eventsCollectorRequest.setSessionId(node.get("sessionId").textValue());
        eventsCollectorRequest.setEvents(filteredEvents);
        return eventsCollectorRequest;
    }
}
