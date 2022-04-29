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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.unomi.api.schema.JSONSchemaExtension;
import org.apache.unomi.api.schema.JSONSchemaEntity;
import org.apache.unomi.api.schema.json.JSONSchema;
import org.apache.unomi.api.services.SchemaService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
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
    private final static String JSONSCHEMAEXTENSION_URL = "/cxs/jsonSchemaExtension";
    private static final int DEFAULT_TRYING_TIMEOUT = 2000;
    private static final int DEFAULT_TRYING_TRIES = 30;

    @Inject
    @Filter(timeout = 600000)
    protected SchemaService schemaService;

    @Inject
    @Filter(timeout = 600000)
    protected PersistenceService persistenceService;

    @Before
    public void setUp() throws InterruptedException {
        keepTrying("Couldn't find json schema endpoint", () -> get(JSONSCHEMA_URL, List.class), Objects::nonNull, DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);
        keepTrying("Couldn't find json schema extension endpoint", () -> get(JSONSCHEMAEXTENSION_URL, List.class), Objects::nonNull,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @After
    public void tearDown() {
        schemaService.deleteSchema("https://unomi.apache.org/schemas/json/events/test-event-for-extension/1-0-0");
        schemaService.deleteExtension("extension-test-event-1");
    }

    @Test
    public void testGetJsonSchemasMetadatas() throws InterruptedException {
        List jsonSchemas = get(JSONSCHEMA_URL, List.class);
        assertTrue("JSON schema list should be empty", jsonSchemas.isEmpty());

        post(JSONSCHEMA_URL, "schemas/events/test-event-for-extension.json", ContentType.TEXT_PLAIN);

        jsonSchemas = keepTrying("Couldn't find json schemas", () -> get(JSONSCHEMA_URL, List.class), (list) -> !list.isEmpty(),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        assertFalse("JSON schema list should not be empty", jsonSchemas.isEmpty());
        assertEquals("JSON schema list should not be empty", 1, jsonSchemas.size());
    }

    @Test
    public void testSaveNewValidJSONSchema() throws InterruptedException {

        assertTrue("JSON schema list should be empty", persistenceService.getAllItems(JSONSchemaEntity.class).isEmpty());

        CloseableHttpResponse response = post(JSONSCHEMA_URL, "schemas/events/test-event-for-extension.json", ContentType.TEXT_PLAIN);

        assertEquals("Invalid response code", 200, response.getStatusLine().getStatusCode());
        List jsonSchemas = keepTrying("Couldn't find json schemas", () -> get(JSONSCHEMA_URL, List.class), (list) -> !list.isEmpty(),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        assertFalse("JSON schema list should not be empty", jsonSchemas.isEmpty());
    }

    @Test
    public void testDeleteJSONSchema() throws InterruptedException {
        assertTrue("JSON schema list should be empty", persistenceService.getAllItems(JSONSchemaEntity.class).isEmpty());

        post(JSONSCHEMA_URL, "schemas/events/test-event-for-extension.json", ContentType.TEXT_PLAIN);

        keepTrying("Couldn't find json schemas", () -> get(JSONSCHEMA_URL, List.class), (list) -> !list.isEmpty(), DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);

        String encodedString = Base64.getEncoder()
                .encodeToString("https://unomi.apache.org/schemas/json/events/test-event-for-extension/1-0-0".getBytes());
        CloseableHttpResponse response = delete(JSONSCHEMA_URL + "/" + encodedString);
        assertEquals("Invalid response code", 204, response.getStatusLine().getStatusCode());

        List jsonSchemas = keepTrying("wait for empty list of schemas", () -> get(JSONSCHEMA_URL, List.class), List::isEmpty,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        assertTrue("JSON schema list should be empty", jsonSchemas.isEmpty());
    }

    @Test
    public void testSaveNewInvalidJSONSchema() throws IOException {
        assertTrue("JSON schema list should be empty", persistenceService.getAllItems(JSONSchemaEntity.class).isEmpty());
        try (CloseableHttpResponse response = post(JSONSCHEMA_URL, "schemas/events/test-invalid.json", ContentType.TEXT_PLAIN)) {
            assertEquals("Save should have failed", 500, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testGetJsonSchemaExtensionsMetadatas() throws InterruptedException {
        List jsonSchemaExtensions = get(JSONSCHEMAEXTENSION_URL, List.class);
        assertTrue("JSON schema extension list should be empty", jsonSchemaExtensions.isEmpty());

        post(JSONSCHEMAEXTENSION_URL, "schemas/extension/extension-test-event-example.json", ContentType.APPLICATION_JSON);

        jsonSchemaExtensions = keepTrying("Couldn't find json extensions", () -> get(JSONSCHEMAEXTENSION_URL, List.class),
                (list) -> !list.isEmpty(), DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        assertFalse("JSON schema list should not be empty", jsonSchemaExtensions.isEmpty());
        assertEquals("JSON schema list should not be empty", 1, jsonSchemaExtensions.size());
    }

    @Test
    public void testSaveNewJSONSchemaExtension() throws InterruptedException {

        assertTrue("JSON schema list should be empty", persistenceService.getAllItems(JSONSchemaExtension.class).isEmpty());

        CloseableHttpResponse response = post(JSONSCHEMAEXTENSION_URL, "schemas/extension/extension-test-event-example.json",
                ContentType.APPLICATION_JSON);

        assertEquals("Invalid response code", 200, response.getStatusLine().getStatusCode());

        keepTrying("Couldn't find json schemas extensions", () -> get(JSONSCHEMAEXTENSION_URL, List.class), (list) -> !list.isEmpty(),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @Test
    public void testDeleteJSONSchemaExtension() throws InterruptedException {
        assertTrue("JSON schema list should be empty", persistenceService.getAllItems(JSONSchemaExtension.class).isEmpty());

        post(JSONSCHEMAEXTENSION_URL, "schemas/extension/extension-test-event-example.json", ContentType.APPLICATION_JSON);

        keepTrying("Couldn't find json schemas extension", () -> get(JSONSCHEMAEXTENSION_URL, List.class), (list) -> !list.isEmpty(),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        CloseableHttpResponse response = delete(JSONSCHEMAEXTENSION_URL + "/extension-test-event-1");
        assertEquals("Invalid response code", 204, response.getStatusLine().getStatusCode());

        keepTrying("wait for empty list of schemas extensions", () -> get(JSONSCHEMA_URL, List.class), List::isEmpty,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @Test
    public void testIfSchemaIsExtendedCorrectly() throws InterruptedException, JsonProcessingException {
        List jsonSchemaExtensions = get(JSONSCHEMAEXTENSION_URL, List.class);
        List jsonSchemas = get(JSONSCHEMA_URL, List.class);

        assertTrue("JSON schema extension list should be empty", jsonSchemaExtensions.isEmpty());
        assertTrue("JSON schema list should be empty", jsonSchemas.isEmpty());

        post(JSONSCHEMA_URL, "schemas/events/test-event-for-extension.json", ContentType.APPLICATION_JSON);

        keepTrying("Couldn't find json schemas", () -> get(JSONSCHEMA_URL, List.class), (list) -> !list.isEmpty(), DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);

        CloseableHttpResponse response = post(JSONSCHEMAEXTENSION_URL, "schemas/extension/extension-test-event-example.json",
                ContentType.APPLICATION_JSON);

        assertEquals("Invalid response code", 200, response.getStatusLine().getStatusCode());

        keepTrying("Couldn't find json schemas extensions", () -> get(JSONSCHEMAEXTENSION_URL, List.class), (list) -> !list.isEmpty(),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        JSONSchema schema = keepTrying("Couldn't find json schemas",
                () -> schemaService.getSchema("https://unomi.apache.org/schemas/json/events/test-event-for-extension/1-0-0"),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        assertEquals("Invalid number for property allOf", 2, schema.getAllOf().size());

        String jsonNodeAsString = CustomObjectMapper.getObjectMapper().writeValueAsString(schema.getSchemaTree());
        JsonNode jsonNode = CustomObjectMapper.getObjectMapper().readTree(jsonNodeAsString);
        assertEquals("Invalid number for property allOf", 100, jsonNode.at("/properties/properties/properties/floatProperty/maximum").asInt());
        assertEquals("Invalid value for property allOf", "Extension of float property",
                jsonNode.at("/properties/properties/properties/floatProperty/description").asText());
        assertEquals("Invalid number for property", 50, jsonNode.at("/properties/properties/properties/stringProperty/maxLength").asInt());
        assertEquals("Invalid value for property", "string", jsonNode.at("/properties/properties/properties/stringProperty/type").asText());
        assertEquals("Invalid value for property", "Initial description",
                jsonNode.at("/properties/properties/properties/stringProperty/description").asText());
        assertEquals("Invalid value for property", "A new element", jsonNode.at("/properties/properties/properties/newProperty/description").asText());
    }
}
