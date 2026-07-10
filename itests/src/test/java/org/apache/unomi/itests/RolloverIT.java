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
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Session;
import org.apache.unomi.shell.migration.utils.HttpUtils;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import java.util.Date;

/**
 * Verifies that the event index actually rolls over once it crosses the configured
 * rollover.maxDocs threshold, on both Elasticsearch (ILM) and OpenSearch (ISM) - see UNOMI-946/947.
 * <p>
 * The IT config sets rollover.maxDocs=300 for both engines. The ILM/ISM background sweep that checks
 * this threshold defaults to 10 minutes (ES) / 5 minutes (OS), so this test temporarily lowers the
 * check interval to make the assertion feasible within a normal test timeout.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class RolloverIT extends BaseIT {

    private static final int EVENTS_TO_CREATE = 320;
    private static final String PROFILE_ID = "rollover-it-profile";
    private static final String SESSION_ID = "rollover-it-session";
    private static final String EVENT_INDEX_PATTERN = "context-event-*";

    @After
    public void tearDown() throws Exception {
        TestUtils.removeAllEvents(definitionsService, persistenceService);
        TestUtils.removeAllSessions(definitionsService, persistenceService);
        TestUtils.removeAllProfiles(definitionsService, persistenceService);
        setPolicyCheckInterval(null);
    }

    @Test
    public void testEventIndexRollsOverPastMaxDocsThreshold() throws Exception {
        setPolicyCheckInterval(SEARCH_ENGINE_OPENSEARCH.equals(searchEngine) ? "1" : "1s");

        Profile profile = new Profile(PROFILE_ID);
        persistenceService.save(profile);
        Session session = new Session(SESSION_ID, profile, new Date(), "rollover-it-scope");
        persistenceService.save(session);

        for (int i = 0; i < EVENTS_TO_CREATE; i++) {
            persistenceService.save(
                    new Event("rollover-it-event-" + i, "view", session, profile, "rollover-it-scope", null, null, new Date()));
        }

        // ISM's job interval is minute-granularity (vs ILM's second-granularity), so OpenSearch needs a
        // much longer allowance to notice the crossed threshold and complete the rollover.
        int retries = SEARCH_ENGINE_OPENSEARCH.equals(searchEngine) ? 45 : 15;
        keepTrying("Event index did not roll over to a second index after crossing the rollover.maxDocs threshold",
                this::countEventRolloverIndices, count -> count >= 2, 2000, retries);
    }

    private int countEventRolloverIndices() {
        try (CloseableHttpClient client = createSearchEngineHttpClient()) {
            String indicesJson = HttpUtils.executeGetRequest(client,
                    getSearchEngineBaseUrl() + "/_cat/indices/" + EVENT_INDEX_PATTERN + "?h=index&format=json", null);
            if (indicesJson == null || indicesJson.isBlank()) {
                return 0;
            }
            JsonNode indices = getObjectMapper().readTree(indicesJson);
            return indices.isArray() ? indices.size() : 0;
        } catch (Exception e) {
            throw new RuntimeException("Failed to count rollover indices matching " + EVENT_INDEX_PATTERN, e);
        }
    }

    private void setPolicyCheckInterval(String value) throws Exception {
        try (CloseableHttpClient client = createSearchEngineHttpClient()) {
            String settingKey = SEARCH_ENGINE_OPENSEARCH.equals(searchEngine)
                    ? "plugins.index_state_management.job_interval"
                    : "indices.lifecycle.poll_interval";
            String settingValue;
            if (value == null) {
                settingValue = "null";
            } else if (SEARCH_ENGINE_OPENSEARCH.equals(searchEngine)) {
                // job_interval is a plain integer number of minutes, not a quoted time value like poll_interval.
                settingValue = value;
            } else {
                settingValue = "\"" + value + "\"";
            }
            String settingsBody = "{\"persistent\": {\"" + settingKey + "\": " + settingValue + "}}";
            HttpUtils.executePutRequest(client, getSearchEngineBaseUrl() + "/_cluster/settings", settingsBody, null);
        }
    }
}
