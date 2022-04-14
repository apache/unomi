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

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.unomi.api.schema.UnomiJSONSchema;
import org.apache.unomi.api.services.SchemaRegistry;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Class to tests the JSON schema features
 */

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class JSONSchemaIT extends BaseIT {
    private final static String JSONSCHEMA_URL = "/cxs/jsonSchema";
    private static final int DEFAULT_TRYING_TIMEOUT = 2000;
    private static final int DEFAULT_TRYING_TRIES = 30;

    @Inject
    @Filter(timeout = 600000)
    protected SchemaRegistry schemaRegistry;

    @Inject
    @Filter(timeout = 600000)
    protected PersistenceService persistenceService;

    @Before
    public void setUp() throws InterruptedException {
        keepTrying("Couldn't find json schema endpoint", () -> get(JSONSCHEMA_URL, List.class), Objects::nonNull, DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);
    }

    @After
    public void tearDown() {
        schemaRegistry.deleteSchema("https://unomi.apache.org/schemas/json/events/test-event-type/1-0-0");
    }

    @Test
    public void testGetJsonSchemasMetadatas() throws InterruptedException {
        List jsonSchemas = get(JSONSCHEMA_URL, List.class);
        assertTrue("JSON schema list should be empty", jsonSchemas.isEmpty());

        post(JSONSCHEMA_URL, "schemas/events/test-event-type.json", ContentType.TEXT_PLAIN);

        refreshPersistence();
        jsonSchemas = keepTrying("Couldn't find json schemas", () -> get(JSONSCHEMA_URL, List.class), (list) -> !list.isEmpty(),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        assertFalse("JSON schema list should not be empty", jsonSchemas.isEmpty());
        assertEquals("JSON schema list should not be empty", 1, jsonSchemas.size());
    }

    @Test
    public void testSaveNewValidJSONSchema() throws InterruptedException {

        assertTrue("JSON schema list should be empty", persistenceService.getAllItems(UnomiJSONSchema.class).isEmpty());

        CloseableHttpResponse response = post(JSONSCHEMA_URL, "schemas/events/test-event-type.json", ContentType.TEXT_PLAIN);

        assertEquals("Invalid response code", 200, response.getStatusLine().getStatusCode());
        refreshPersistence();
        List jsonSchemas = keepTrying("Couldn't find json schemas", () -> get(JSONSCHEMA_URL, List.class), (list) -> !list.isEmpty(),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        assertFalse("JSON schema list should not be empty", jsonSchemas.isEmpty());
    }

    @Test
    public void testDeleteJSONSchema() throws InterruptedException {
        assertTrue("JSON schema list should be empty", persistenceService.getAllItems(UnomiJSONSchema.class).isEmpty());

        post(JSONSCHEMA_URL, "schemas/events/test-event-type.json", ContentType.TEXT_PLAIN);

        refreshPersistence();
        keepTrying("Couldn't find json schemas", () -> get(JSONSCHEMA_URL, List.class), (list) -> !list.isEmpty(), DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);

        String encodedString = Base64.getEncoder()
                .encodeToString("https://unomi.apache.org/schemas/json/events/test-event-type/1-0-0".getBytes());
        CloseableHttpResponse response = delete(JSONSCHEMA_URL + "/" + encodedString);
        assertEquals("Invalid response code", 204, response.getStatusLine().getStatusCode());

        refreshPersistence();
        List jsonSchemas = keepTrying("wait for empty list of schemas", () -> get(JSONSCHEMA_URL, List.class), List::isEmpty,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        assertTrue("JSON schema list should be empty", jsonSchemas.isEmpty());
    }

    @Test
    public void testSaveNewInvalidJSONSchema() throws IOException {
        assertTrue("JSON schema list should be empty", persistenceService.getAllItems(UnomiJSONSchema.class).isEmpty());
        try (CloseableHttpResponse response = post(JSONSCHEMA_URL, "schemas/events/test-invalid.json", ContentType.TEXT_PLAIN)) {
            assertEquals("Save should have failed", 500, response.getStatusLine().getStatusCode());
        }
    }
}
