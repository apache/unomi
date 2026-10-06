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
package org.apache.unomi.persistence.elasticsearch;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.stream.JsonGenerator;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ElasticSearchScopePurgeQueryTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    public void buildScopePurgeQueryRequiresBothScopeAndTenantTerms() throws Exception {
        Query query = ElasticSearchPersistenceServiceImpl.buildScopePurgeQuery("drop_scope", "tenant_a");
        JsonNode json = OBJECT_MAPPER.readTree(toJson(query));

        JsonNode must = json.path("bool").path("must");
        assertTrue("Purge query must be a bool query with must clauses: " + json, must.isArray());
        assertEquals(2, must.size());

        Set<String> fields = new HashSet<>();
        for (JsonNode clause : must) {
            JsonNode term = clause.path("term");
            assertTrue("Each must clause should be a term query: " + json, term.isObject() && term.size() == 1);
            fields.add(term.fieldNames().next());
        }
        assertEquals(Set.of("scope", "tenantId"), fields);
        assertEquals("drop_scope", termValue(must, "scope"));
        assertEquals("tenant_a", termValue(must, "tenantId"));
    }

    @Test
    public void buildScopePurgeQueryFoldsScopeAndTenant() throws Exception {
        Query query = ElasticSearchPersistenceServiceImpl.buildScopePurgeQuery("Drop_Scopé", "Tenant_A");
        JsonNode must = OBJECT_MAPPER.readTree(toJson(query)).path("bool").path("must");

        assertEquals("drop_scope", termValue(must, "scope"));
        assertEquals("tenant_a", termValue(must, "tenantId"));
    }

    @Test
    public void buildScopePurgeQueryRejectsBlankScopeOrTenant() {
        assertIllegalArgument(() -> ElasticSearchPersistenceServiceImpl.buildScopePurgeQuery("  ", "tenant_a"));
        assertIllegalArgument(() -> ElasticSearchPersistenceServiceImpl.buildScopePurgeQuery(null, "tenant_a"));
        assertIllegalArgument(() -> ElasticSearchPersistenceServiceImpl.buildScopePurgeQuery("drop_scope", " "));
        assertIllegalArgument(() -> ElasticSearchPersistenceServiceImpl.buildScopePurgeQuery("drop_scope", null));
    }

    private static String termValue(JsonNode must, String field) {
        for (JsonNode clause : must) {
            JsonNode term = clause.path("term").path(field);
            if (term.has("value")) {
                return term.get("value").asText();
            }
            if (term.isTextual()) {
                return term.asText();
            }
        }
        fail("No term for field " + field);
        return null;
    }

    private static String toJson(Query query) {
        JacksonJsonpMapper mapper = new JacksonJsonpMapper();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator generator = mapper.jsonProvider().createGenerator(out);
        query.serialize(generator, mapper);
        generator.close();
        return out.toString(StandardCharsets.UTF_8);
    }

    private static void assertIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("required"));
        }
    }
}
