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
package org.apache.unomi.persistence.opensearch;

import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenSearchScopePurgeQueryTest {

    @Test
    public void buildScopePurgeQueryRequiresBothScopeAndTenantTerms() {
        Query query = OpenSearchPersistenceServiceImpl.buildScopePurgeQuery("drop_scope", "tenant_a");

        assertEquals(Map.of("scope", "drop_scope", "tenantId", "tenant_a"), mustTerms(query));
    }

    @Test
    public void buildScopePurgeQueryFoldsScopeAndTenant() {
        Query query = OpenSearchPersistenceServiceImpl.buildScopePurgeQuery("Drop_Scopé", "Tenant_A");

        assertEquals(Map.of("scope", "drop_scope", "tenantId", "tenant_a"), mustTerms(query));
    }

    @Test
    public void buildScopePurgeQueryRejectsBlankScopeOrTenant() {
        assertRequired("scope", () -> OpenSearchPersistenceServiceImpl.buildScopePurgeQuery("  ", "tenant_a"));
        assertRequired("scope", () -> OpenSearchPersistenceServiceImpl.buildScopePurgeQuery(null, "tenant_a"));
        assertRequired("tenant", () -> OpenSearchPersistenceServiceImpl.buildScopePurgeQuery("drop_scope", " "));
        assertRequired("tenant", () -> OpenSearchPersistenceServiceImpl.buildScopePurgeQuery("drop_scope", null));
    }

    private static Map<String, String> mustTerms(Query query) {
        assertTrue(query.isBool(), "Purge query must be a bool query: " + query);
        List<Query> must = query.bool().must();
        assertEquals(2, must.size());
        assertTrue(query.bool().should().isEmpty() && query.bool().mustNot().isEmpty());

        Map<String, String> terms = new HashMap<>();
        for (Query clause : must) {
            assertTrue(clause.isTerm(), "Each must clause should be a term query: " + query);
            TermQuery term = clause.term();
            terms.put(term.field(), term.value().stringValue());
        }
        return terms;
    }

    private static void assertRequired(String what, Runnable action) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, action::run);
        assertTrue(e.getMessage().contains(what) && e.getMessage().contains("required"), e.getMessage());
    }
}
