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
 * limitations under the License
 */
package org.apache.unomi.itests;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.unomi.itests.persistence.PersistenceITCapabilities;
import org.apache.unomi.itests.persistence.SearchBackendIT;
import org.apache.unomi.shell.migration.utils.HttpUtils;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Verifies that Unomi correctly wires up the event index's rollover lifecycle on both Elasticsearch (ILM)
 * and OpenSearch (ISM) - see UNOMI-946/947.
 * <p>
 * This deliberately does not wait for an actual rollover to happen. ILM/ISM evaluate their managed indices
 * on a periodic background sweep (minute-granularity), and on OpenSearch a job_interval change only takes
 * effect after the currently scheduled sweep completes - which can add 5+ minutes of pure scheduling
 * latency before the engine even starts evaluating a newly-managed index, on top of however long the
 * rollover action itself then takes. Whether ILM/ISM actually execute a rollover once its conditions are
 * met is the search engine's own, already-tested responsibility; what Unomi needs to guarantee is that the
 * index/template/policy setup is correct so that engine can do its job, so this test asserts on that setup
 * directly instead of waiting on it.
 * <p>
 * The write index is resolved dynamically via the alias's {@code is_write_index} flag rather than assumed
 * to be {@code context-event-000001}: the IT suite runs with a deliberately low rollover.maxDocs=300 (see
 * BaseIT) shared by the whole PerSuite container, so by the time this test runs - often after hundreds of
 * other tests have created events - the index may have already rolled over for real. On OpenSearch, once
 * that happens ISM has nothing left to transition to (the rollover policy's single state has no further
 * transitions) and marks that now-rolled-over index's management as completed/disabled, which would make a
 * hardcoded context-event-000001 check fail even though the rollover machinery worked correctly.
 * <p>
 * Non-search providers (e.g. PostgreSQL) leave {@link PersistenceITCapabilities.IndexRolloverApi#NONE}
 * so this test is {@link Assume Assumed} skipped (not failed). Kept in {@link CorePersistenceITs}
 * / {@link AllITs} for maximum suite coverage across backends.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
@Category(SearchBackendIT.class)
public class RolloverIT extends BaseIT {

    private static final String EVENT_ALIAS = "context-event";
    private static final String POLICY_ID = "context-unomi-rollover-policy";
    private static final long EXPECTED_MAX_DOCS = 300;

    @Test
    public void testEventIndexRolloverIsProperlyConfigured() throws Exception {
        PersistenceITCapabilities caps = persistenceCapabilities();
        Assume.assumeTrue(
                "Index rollover requires an HTTP rollover API (provider="
                        + getPersistenceBackend().providerId() + ")",
                caps.indexRolloverApi().isPresent());
        Assume.assumeTrue(
                "Index rollover assertions require httpAdminApi (provider="
                        + getPersistenceBackend().providerId() + ")",
                caps.httpAdminApi());

        try (CloseableHttpClient client = createSearchEngineHttpClient()) {
            String writeIndex = resolveCurrentWriteIndex(client);
            JsonNode indexRoot = getJson(client, "/" + writeIndex + "/_settings?flat_settings=true").get(writeIndex);
            assertTrue("Expected the event write index " + writeIndex + " to already exist", indexRoot != null);
            JsonNode settings = indexRoot.get("settings");

            switch (caps.indexRolloverApi()) {
                case STATE_MANAGEMENT:
                    assertStateManagementRolloverConfigured(client, settings, writeIndex);
                    break;
                case LIFECYCLE:
                    assertLifecycleRolloverConfigured(client, settings, writeIndex);
                    break;
                case NONE:
                    fail("unreachable: indexRolloverApi was assumed present");
                    break;
                default: {
                    PersistenceITCapabilities.IndexRolloverApi unexpected = caps.indexRolloverApi();
                    throw new IllegalStateException("Unhandled IndexRolloverApi: " + unexpected);
                }
            }
        }
    }

    // Resolves the index currently holding the event alias's write pointer, rather than assuming it is
    // still context-event-000001 - see the class javadoc for why that assumption doesn't hold on a full
    // suite run. Mirrors the is_write_index alias lookup OpenSearchPersistenceServiceImpl itself uses for
    // session rollover indices.
    private String resolveCurrentWriteIndex(CloseableHttpClient client) throws IOException {
        JsonNode aliasInfo = getJson(client, "/_alias/" + EVENT_ALIAS);
        Iterator<Map.Entry<String, JsonNode>> indices = aliasInfo.fields();
        while (indices.hasNext()) {
            Map.Entry<String, JsonNode> entry = indices.next();
            JsonNode isWriteIndex = entry.getValue().path("aliases").path(EVENT_ALIAS).path("is_write_index");
            if (isWriteIndex.asBoolean(false)) {
                return entry.getKey();
            }
        }
        throw new AssertionError("Could not find a write index for alias " + EVENT_ALIAS);
    }

    private void assertStateManagementRolloverConfigured(CloseableHttpClient client, JsonNode settings, String writeIndex) throws IOException {
        assertEquals("event index should reference the Unomi rollover policy",
                POLICY_ID, text(settings, "index.plugins.index_state_management.policy_id"));
        assertEquals("event index rollover_alias should be the event write alias",
                EVENT_ALIAS, text(settings, "index.plugins.index_state_management.rollover_alias"));

        // The index setting above is accepted by OpenSearch but is inert on its own (verified empirically:
        // GET _plugins/_ism/explain reported total_managed_indices: 0 despite the setting being present) -
        // ISM only actually manages an index via ism_template pattern-matching or an explicit Add Policy
        // call, so confirm the real attachment here too, not just the setting.
        JsonNode explain = getJson(client, "/_plugins/_ism/explain/" + writeIndex);
        assertTrue("ISM should actually be managing the event index, not just have an inert policy_id setting",
                explain.get("total_managed_indices").asInt() >= 1);
        JsonNode explainIndex = explain.get(writeIndex);
        assertEquals(POLICY_ID, text(explainIndex, "policy_id"));
        assertTrue("ISM management should be enabled for the event index", explainIndex.get("enabled").asBoolean());

        JsonNode rolloverAction = getJson(client, "/_plugins/_ism/policies/" + POLICY_ID)
                .get("policy").get("states").get(0).get("actions").get(0).get("rollover");
        assertEquals(EXPECTED_MAX_DOCS, rolloverAction.get("min_doc_count").asLong());
    }

    private void assertLifecycleRolloverConfigured(CloseableHttpClient client, JsonNode settings, String writeIndex) throws IOException {
        assertEquals("event index should reference the Unomi rollover policy",
                POLICY_ID, text(settings, "index.lifecycle.name"));
        assertEquals("event index rollover_alias should be the event write alias",
                EVENT_ALIAS, text(settings, "index.lifecycle.rollover_alias"));

        JsonNode explainIndex = getJson(client, "/" + writeIndex + "/_ilm/explain").get("indices").get(writeIndex);
        assertTrue("ILM should actually be managing the event index", explainIndex.get("managed").asBoolean());
        assertEquals(POLICY_ID, text(explainIndex, "policy"));

        JsonNode rolloverAction = getJson(client, "/_ilm/policy/" + POLICY_ID)
                .get(POLICY_ID).get("policy").get("phases").get("hot").get("actions").get("rollover");
        assertEquals(EXPECTED_MAX_DOCS, rolloverAction.get("max_docs").asLong());
    }

    private JsonNode getJson(CloseableHttpClient client, String path) throws IOException {
        String body = HttpUtils.executeGetRequest(client, getSearchEngineBaseUrl() + path, null);
        return getObjectMapper().readTree(body);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null ? value.asText() : null;
    }
}
