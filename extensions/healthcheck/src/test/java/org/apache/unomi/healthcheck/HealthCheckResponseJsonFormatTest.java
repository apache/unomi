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
package org.apache.unomi.healthcheck;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the JSON wire format of {@link HealthCheckResponse} to the 3.0 shape:
 * {@code name}, {@code status} (enum name), {@code collectingTime}, and optional {@code data}
 * (omitted when empty). Array order is alphabetical by name, as in 3.0's servlet.
 */
class HealthCheckResponseJsonFormatTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Test
    void liveWithoutDataOmitsDataField() throws Exception {
        JsonNode node = mapper.readTree(mapper.writeValueAsString(HealthCheckResponse.live("karaf")));
        assertEquals("karaf", node.get("name").asText());
        assertEquals("LIVE", node.get("status").asText());
        assertTrue(node.get("collectingTime").isNumber());
        assertFalse(node.has("data"));
        assertEquals(3, node.size());
    }

    @Test
    void timeoutUsesErrorCauseLike30() throws Exception {
        HealthCheckProvider provider = new HealthCheckProvider() {
            @Override
            public String name() {
                return "persistence";
            }

            @Override
            public HealthCheckResponse execute() {
                return HealthCheckResponse.live(name());
            }
        };
        JsonNode node = mapper.readTree(mapper.writeValueAsString(provider.timeout()));
        assertEquals("persistence", node.get("name").asText());
        assertEquals("ERROR", node.get("status").asText());
        assertEquals("timeout", node.get("data").get("error.cause").asText());
        assertFalse(node.get("data").has("error"));
    }

    @Test
    void errorUsesErrorFieldLikeProviderFailures() throws Exception {
        JsonNode node = mapper.readTree(mapper.writeValueAsString(
                HealthCheckResponse.named("elasticsearch").error().withData("error", "connection refused").build()));
        assertEquals("ERROR", node.get("status").asText());
        assertEquals("connection refused", node.get("data").get("error").asText());
    }

    @Test
    void arrayIsSortedByNameLike30Servlet() throws Exception {
        List<HealthCheckResponse> checks = new ArrayList<>();
        checks.add(HealthCheckResponse.live("karaf"));
        checks.add(HealthCheckResponse.down("cluster"));
        checks.add(HealthCheckResponse.live("elasticsearch"));
        checks.add(HealthCheckResponse.down("unomi"));
        checks.add(HealthCheckResponse.down("persistence"));
        checks.sort(Comparator.comparing(HealthCheckResponse::getName));

        JsonNode array = mapper.readTree(mapper.writeValueAsString(checks));
        assertEquals(5, array.size());
        assertEquals("cluster", array.get(0).get("name").asText());
        assertEquals("elasticsearch", array.get(1).get("name").asText());
        assertEquals("karaf", array.get(2).get("name").asText());
        assertEquals("persistence", array.get(3).get("name").asText());
        assertEquals("unomi", array.get(4).get("name").asText());
    }
}
