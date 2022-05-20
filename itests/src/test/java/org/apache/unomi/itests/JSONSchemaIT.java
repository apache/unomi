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
import org.apache.unomi.api.Event;
import org.apache.unomi.schema.api.JsonSchemaWrapper;
import org.apache.unomi.schema.api.SchemaService;
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

import static org.junit.Assert.*;

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
    protected SchemaService schemaService;

    @Before
    public void setUp() throws InterruptedException {
        keepTrying("Couldn't find json schema endpoint", () -> get(JSONSCHEMA_URL, List.class), Objects::nonNull, DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);
    }

    @After
    public void tearDown() throws InterruptedException {
        removeItems(JsonSchemaWrapper.class, Event.class);
        // ensure all schemas have been cleaned from schemaService.
        keepTrying("Couldn't find json schemas",
                () -> schemaService.getInstalledJsonSchemaIds(),
                (list) -> (!list.contains("https://unomi.apache.org/schemas/json/events/dummy/1-0-0") &&
                        !list.contains("https://unomi.apache.org/schemas/json/events/dummy/properties/1-0-0")),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @Test
    public void testValidation_SaveDeleteSchemas() throws InterruptedException, IOException {
        // check that event is not valid at first
        assertFalse(schemaService.isValid(resourceAsString("schemas/event-dummy-valid.json"), "https://unomi.apache.org/schemas/json/events/dummy/1-0-0"));

        // Push schemas
        schemaService.saveSchema(resourceAsString("schemas/schema-dummy.json"));
        schemaService.saveSchema(resourceAsString("schemas/schema-dummy-properties.json"));
        keepTrying("Couldn't find json schemas",
                () -> get(JSONSCHEMA_URL, List.class),
                (list) -> (list.contains("https://unomi.apache.org/schemas/json/events/dummy/1-0-0") &&
                        list.contains("https://unomi.apache.org/schemas/json/events/dummy/properties/1-0-0")),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // event should be valid now
        keepTrying("Event should be valid",
                () -> schemaService.isValid(resourceAsString("schemas/event-dummy-valid.json"), "https://unomi.apache.org/schemas/json/events/dummy/1-0-0"),
                isValid -> isValid,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // Test multiple invalid event:
        // unevaluated property at root:
        assertFalse(schemaService.isValid(resourceAsString("schemas/event-dummy-invalid-1.json"), "https://unomi.apache.org/schemas/json/events/dummy/1-0-0"));
        // unevaluated property in properties:
        assertFalse(schemaService.isValid(resourceAsString("schemas/event-dummy-invalid-2.json"), "https://unomi.apache.org/schemas/json/events/dummy/1-0-0"));
        // bad type number but should be string:
        assertFalse(schemaService.isValid(resourceAsString("schemas/event-dummy-invalid-3.json"), "https://unomi.apache.org/schemas/json/events/dummy/1-0-0"));

        // remove one of the schema:
        assertTrue(schemaService.deleteSchema("https://unomi.apache.org/schemas/json/events/dummy/properties/1-0-0"));
        keepTrying("Schema should have been deleted",
                () -> schemaService.getInstalledJsonSchemaIds(),
                (list) -> !list.contains("https://unomi.apache.org/schemas/json/events/dummy/properties/1-0-0"),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // event should be invalid now that one of the schema have been deleted -> this is validating cache is correctly flushed
        keepTrying("Event should be invalid since of the schema have been deleted",
                () -> schemaService.isValid(resourceAsString("schemas/event-dummy-valid.json"), "https://unomi.apache.org/schemas/json/events/dummy/1-0-0"),
                isValid -> !isValid,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @Test
    public void testValidation_UpdateSchema() throws InterruptedException, IOException {
        // check that event is not valid at first
        assertFalse(schemaService.isValid(resourceAsString("schemas/event-dummy-valid.json"), "https://unomi.apache.org/schemas/json/events/dummy/1-0-0"));

        // Push schemas
        schemaService.saveSchema(resourceAsString("schemas/schema-dummy.json"));
        schemaService.saveSchema(resourceAsString("schemas/schema-dummy-properties.json"));
        keepTrying("Couldn't find json schemas",
                () -> get(JSONSCHEMA_URL, List.class),
                (list) -> (list.contains("https://unomi.apache.org/schemas/json/events/dummy/1-0-0") &&
                        list.contains("https://unomi.apache.org/schemas/json/events/dummy/properties/1-0-0")),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // event should be valid now
        keepTrying("Event should be valid",
                () -> schemaService.isValid(resourceAsString("schemas/event-dummy-valid.json"), "https://unomi.apache.org/schemas/json/events/dummy/1-0-0"),
                isValid -> isValid,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // Test the invalid event, that use the new prop "invalidPropName" in properties:
        assertFalse(schemaService.isValid(resourceAsString("schemas/event-dummy-invalid-2.json"), "https://unomi.apache.org/schemas/json/events/dummy/1-0-0"));

        // update the schema to allow "invalidPropName":
        schemaService.saveSchema(resourceAsString("schemas/schema-dummy-properties-updated.json"));
        keepTrying("schema should be updated by refresh 1sec",
                () -> schemaService.getSchema("https://unomi.apache.org/schemas/json/events/dummy/properties/1-0-0"),
                schema -> (schema != null && schema.getSchema().contains("invalidPropName")),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // "invalidPropName" should be valid and allowed now
        keepTrying("Event should be valid since of the schema have been updated",
                () -> schemaService.isValid(resourceAsString("schemas/event-dummy-invalid-2.json"), "https://unomi.apache.org/schemas/json/events/dummy/1-0-0"),
                isValid -> isValid,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @Test
    public void testEndPoint_GetInstalledJsonSchemas() throws InterruptedException {
        List<String> jsonSchemas = get(JSONSCHEMA_URL, List.class);
        assertFalse("JSON schema list should not be empty, it should contains predefined Unomi schemas", jsonSchemas.isEmpty());
    }

    @Test
    public void testEndPoint_SaveDelete() throws InterruptedException, IOException {
        assertNull(schemaService.getSchema("https://unomi.apache.org/schemas/json/events/dummy/1-0-0"));

        // Post schema using REST call
        try(CloseableHttpResponse response = post(JSONSCHEMA_URL, "schemas/schema-dummy.json", ContentType.TEXT_PLAIN)) {
            assertEquals("Invalid response code", 200, response.getStatusLine().getStatusCode());
        }

        // See schema is available
        keepTrying("Schema should have been created", () -> schemaService.getSchema("https://unomi.apache.org/schemas/json/events/dummy/1-0-0"),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // Delete Schema using REST call
        String encodedString = Base64.getEncoder()
                .encodeToString("https://unomi.apache.org/schemas/json/events/dummy/1-0-0".getBytes());
        CloseableHttpResponse response = delete(JSONSCHEMA_URL + "/" + encodedString);
        assertEquals("Invalid response code", 204, response.getStatusLine().getStatusCode());

        waitForNullValue("Schema should have been deleted",
                () -> schemaService.getSchema("https://unomi.apache.org/schemas/json/events/dummy/1-0-0"),
                DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);
    }

    @Test
    public void testSaveFail_PredefinedJSONSchema() throws IOException {
        try (CloseableHttpResponse response = post(JSONSCHEMA_URL, "schemas/schema-predefined.json", ContentType.TEXT_PLAIN)) {
            assertEquals("Unable to save schema", 400, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testSaveFail_NewInvalidJSONSchema() throws IOException {
        try (CloseableHttpResponse response = post(JSONSCHEMA_URL, "schemas/schema-invalid.json", ContentType.TEXT_PLAIN)) {
            assertEquals("Unable to save schema", 400, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testSaveFail_SchemaWithInvalidName() throws IOException {
        try (CloseableHttpResponse response = post(JSONSCHEMA_URL, "schemas/schema-invalid-name.json", ContentType.TEXT_PLAIN)) {
            assertEquals("Unable to save schema", 400, response.getStatusLine().getStatusCode());
        }
    }
}
