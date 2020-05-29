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

package org.apache.unomi.graphql.servlet.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.unomi.graphql.utils.GraphQLObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GraphQLMessage {

    private String id;

    private String type;

    private Map<String, Object> payload;

    public static final String TYPE_CONNECTION_INIT = "connection_init"; // client->server
    public static final String TYPE_CONNECTION_ACK = "connection_ack"; // server->client
    public static final String TYPE_CONNECTION_ERROR = "connection_error"; // server->client
    public static final String TYPE_CONNECTION_KEEP_ALIVE = "ka"; // server->client
    public static final String TYPE_CONNECTION_TERMINATE = "connection_terminate"; // client->server

    public static final String GQL_START = "start";
    public static final String GQL_DATA = "data";
    public static final String GQL_ERROR = "error";
    public static final String GQL_COMPLETE = "complete";
    public static final String GQL_STOP = "stop";

    private GraphQLMessage(final Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.payload = builder.payload;
    }

    public static GraphQLMessage fromJson(String textMessage) {
        try {
            final JsonNode node = GraphQLObjectMapper.getInstance().readTree(textMessage);
            if (node == null || node.isMissingNode()) {
                return null;
            }
            final String id = node.path("id").asText(null);
            final Builder builder = GraphQLMessage.create(id);
            final String type = node.path("type").asText(null);
            if (type != null) {
                builder.type(type);
            }
            final JsonNode payloadNode = node.path("payload");
            if (!payloadNode.isMissingNode()) {
                Map<String, Object> payload = GraphQLObjectMapper.getInstance().convertValue(payloadNode, Map.class);
                payload.forEach(builder::field);
            }
            return builder.build();
        } catch (IOException e) {
            return null;
        }
    }

    public static GraphQLMessage connectionAck(final String id) {
        return GraphQLMessage.create(id)
                .type(TYPE_CONNECTION_ACK)
                .build();
    }

    public static GraphQLMessage complete(final String id) {
        return GraphQLMessage.create(id)
                .type(GQL_COMPLETE)
                .build();
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        try {
            return GraphQLObjectMapper.getInstance().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public static Builder create(final String id) {
        return new Builder(id);
    }

    public static class Builder {

        private String id;
        private String type;
        private Map<String, Object> payload = new HashMap<>();

        public Builder(String id) {
            this.id = id;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder errors(final List<?> errors) {
            payload.put("errors", errors);
            if (errors != null && !errors.isEmpty()) {
                type = GQL_ERROR;
            }
            return this;
        }

        public Builder data(final Object data) {
            payload.put("data", data);
            if (data != null && !GQL_ERROR.equals(type)) {
                type = GQL_DATA;
            }
            return this;
        }

        public Builder field(final String name, final Object value) {
            payload.put(name, value);
            return this;
        }

        public GraphQLMessage build() {
            return new GraphQLMessage(this);
        }
    }
}

