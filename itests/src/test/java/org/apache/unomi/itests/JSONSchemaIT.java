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
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.Scope;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.ScopeService;
import org.apache.unomi.itests.tools.httpclient.HttpClientThatWaitsForUnomi;
import org.apache.unomi.schema.api.JsonSchemaWrapper;
import org.apache.unomi.schema.api.SchemaService;
import org.apache.unomi.schema.api.ValidationError;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;

import java.util.stream.Collectors;

/**
 * Class to tests the JSON schema features
 */

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class JSONSchemaIT extends BaseIT {
    private final static Logger LOGGER = LoggerFactory.getLogger(JSONSchemaIT.class);
    private final static String EVENT_COLLECTOR_URL = "/cxs/eventcollector";
    private final static String JSONSCHEMA_URL = "/cxs/jsonSchema";
    private static final int DEFAULT_TRYING_TIMEOUT = 2000;
    private static final int DEFAULT_TRYING_TRIES = 30;
    public static final String DUMMY_SCOPE = "dummy_scope";

    @Before
    public void setUp() throws InterruptedException {
        keepTrying("Couldn't find json schema endpoint", () -> get(JSONSCHEMA_URL, List.class), Objects::nonNull, DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);

        TestUtils.createScope(DUMMY_SCOPE, "Dummy scope", scopeService);
        keepTrying("Scope " + DUMMY_SCOPE + " not found in the required time", () -> scopeService.getScope(DUMMY_SCOPE),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @After
    public void tearDown() throws InterruptedException {
        removeItems(JsonSchemaWrapper.class, Event.class, Scope.class);
        // ensure all schemas have been cleaned from schemaService.
        keepTrying("Should not find json schemas anymore",
                () -> schemaService.getInstalledJsonSchemaIds(),
                (list) -> (!list.contains("https://vendor.test.com/schemas/json/events/dummy/1-0-0") &&
                        !list.contains("https://vendor.test.com/schemas/json/events/dummy/properties/1-0-0")),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @Test
    public void testValidation_SaveDeleteSchemas() throws InterruptedException, IOException {
        // check that event is not valid at first
        assertFalse(schemaService.isEventValid(resourceAsString("schemas/event-dummy-valid.json")));

        // Push schemas
        schemaService.saveSchema(resourceAsString("schemas/schema-dummy.json"));
        schemaService.saveSchema(resourceAsString("schemas/schema-dummy-properties.json"));
        keepTrying("Event should be valid", () -> schemaService
                        .isEventValid(resourceAsString("schemas/event-dummy-valid.json")),
                isValid -> isValid, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // Test multiple invalid event:
        // unevaluated property at root:
        assertFalse(schemaService.isEventValid(resourceAsString("schemas/event-dummy-invalid-1.json")));
        // unevaluated property in properties:
        assertFalse(schemaService.isEventValid(resourceAsString("schemas/event-dummy-invalid-2.json")));
        // bad type number but should be string:
        assertFalse(schemaService.isEventValid(resourceAsString("schemas/event-dummy-invalid-3.json")));

        // Event with unexisting scope:
        assertFalse(schemaService.isEventValid(resourceAsString("schemas/event-dummy-invalid-4.json")));

        // remove one of the schema:
        assertTrue(schemaService.deleteSchema("https://vendor.test.com/schemas/json/events/dummy/properties/1-0-0"));
        keepTrying("Event should be invalid since of the schema have been deleted", () -> schemaService
                        .isEventValid(resourceAsString("schemas/event-dummy-valid.json")),
                isValid -> !isValid, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @Test
    public void testValidation_UpdateSchema() throws InterruptedException, IOException {
        // check that event is not valid at first
        assertFalse(schemaService
                .isEventValid(resourceAsString("schemas/event-dummy-valid.json")));

        // Push schemas
        schemaService.saveSchema(resourceAsString("schemas/schema-dummy.json"));
        schemaService.saveSchema(resourceAsString("schemas/schema-dummy-properties.json"));
        keepTrying("Event should be valid", () -> schemaService
                        .isEventValid(resourceAsString("schemas/event-dummy-valid.json")),
                isValid -> isValid, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // Test the invalid event, that use the new prop "invalidPropName" in properties:
        assertFalse(schemaService.isEventValid(resourceAsString("schemas/event-dummy-invalid-2.json")));

        // update the schema to allow "invalidPropName":
        schemaService.saveSchema(resourceAsString("schemas/schema-dummy-properties-updated.json"));
        keepTrying("Event should be valid since of the schema have been updated", () -> schemaService
                        .isEventValid(resourceAsString("schemas/event-dummy-invalid-2.json")), isValid -> isValid, DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);
    }

    @Test
    public void testExtension_SaveDelete() throws InterruptedException, IOException {
        // Push base schemas
        schemaService.saveSchema(resourceAsString("schemas/schema-dummy.json"));
        schemaService.saveSchema(resourceAsString("schemas/schema-dummy-properties.json"));
        keepTrying("Event should be valid", () -> schemaService
                        .isEventValid(resourceAsString("schemas/event-dummy-valid.json")),
                isValid -> isValid, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // check that extended event is not valid at first
        assertFalse(schemaService.isEventValid(resourceAsString("schemas/event-dummy-extended.json")));

        // register both extensions (for root event and the properties level)
        schemaService.saveSchema(resourceAsString("schemas/schema-dummy-extension.json"));
        schemaService.saveSchema(resourceAsString("schemas/schema-dummy-properties-extension.json"));
        keepTrying("Extended event should be valid since of the extensions have been deployed", () -> schemaService
                        .isEventValid(resourceAsString("schemas/event-dummy-extended.json")),
                isValid -> isValid, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // delete one of the extension
        schemaService.deleteSchema("https://vendor.test.com/schemas/json/events/dummy/properties/extension/1-0-0");
        keepTrying("Extended event should be invalid again, one necessary extension have been removed", () -> schemaService
                        .isEventValid(resourceAsString("schemas/event-dummy-extended.json")),
                isValid -> !isValid, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @Test
    public void testExtension_Update() throws InterruptedException, IOException {
        // Push base schemas
        schemaService.saveSchema(resourceAsString("schemas/schema-dummy.json"));
        schemaService.saveSchema(resourceAsString("schemas/schema-dummy-properties.json"));
        keepTrying("Event should be valid", () -> schemaService
                        .isEventValid(resourceAsString("schemas/event-dummy-valid.json")),
                isValid -> isValid, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // check that extended event is not valid at first
        assertFalse(schemaService.isEventValid(resourceAsString("schemas/event-dummy-extended.json")));

        // register both extensions (for root event and the properties level)
        schemaService.saveSchema(resourceAsString("schemas/schema-dummy-extension.json"));
        schemaService.saveSchema(resourceAsString("schemas/schema-dummy-properties-extension.json"));
        keepTrying("Extended event should be valid since of the extensions have been deployed", () -> schemaService
                        .isEventValid(resourceAsString("schemas/event-dummy-extended.json")),
                isValid -> isValid, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // check that extended event 2 is not valid due to usage of unevaluatedProperty not bring by schemas or extensions
        assertFalse(schemaService.isEventValid(resourceAsString("schemas/event-dummy-extended-2.json")));

        // Update extensions to allow the extended event 2
        schemaService.saveSchema(resourceAsString("schemas/schema-dummy-properties-extension-2.json"));
        keepTrying("Extended event 2 should be valid since of the extensions have been updated", () -> schemaService
                        .isEventValid(resourceAsString("schemas/event-dummy-extended-2.json")), isValid -> isValid, DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);
    }

    @Test
    public void testEndPoint_GetInstalledJsonSchemas() {
        List<String> jsonSchemas = get(JSONSCHEMA_URL, List.class);
        assertFalse("JSON schema list should not be empty, it should contain predefined Unomi schemas", jsonSchemas.isEmpty());
    }

    @Test
    public void testEndPoint_GetJsonSchemasById() throws Exception {
        // Push base schemas
        schemaService.saveSchema(resourceAsString("schemas/schema-dummy.json"));
        schemaService.saveSchema(resourceAsString("schemas/schema-dummy-properties.json"));

        final String schemaId = "https://vendor.test.com/schemas/json/events/dummy/1-0-0";
        final HttpPost request = new HttpPost(getFullUrl(JSONSCHEMA_URL + "/query"));

        request.setEntity(new StringEntity(schemaId));

        keepTrying("Should return a schema when calling the endpoint", () -> {
            try (CloseableHttpResponse response = executeHttpRequest(request)) {
                return EntityUtils.toString(response.getEntity());
            } catch (IOException e) {
                LOGGER.error("Failed to get the json schema with the id: {}", schemaId);
            }
            return "";
        }, entity -> entity.contains("DummyEvent"), DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @Test
    public void testEndPoint_SaveDelete() throws Exception {
        assertNull(schemaService.getSchema("https://vendor.test.com/schemas/json/events/dummy/1-0-0"));

        // Post schema using REST call
        try (CloseableHttpResponse response = post(JSONSCHEMA_URL, "schemas/schema-dummy.json", ContentType.TEXT_PLAIN)) {
            assertEquals("Invalid response code", 200, response.getStatusLine().getStatusCode());
        }

        // See schema is available
        keepTrying("Schema should have been created",
                () -> schemaService.getSchema("https://vendor.test.com/schemas/json/events/dummy/1-0-0"), Objects::nonNull,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // Delete Schema using REST call
        final HttpPost request = new HttpPost(getFullUrl(JSONSCHEMA_URL + "/delete"));
        request.setEntity(new StringEntity("https://vendor.test.com/schemas/json/events/dummy/1-0-0"));
        CloseableHttpResponse response = executeHttpRequest(request);
        assertEquals("Invalid response code", 200, response.getStatusLine().getStatusCode());

        waitForNullValue("Schema should have been deleted",
                () -> schemaService.getSchema("https://vendor.test.com/schemas/json/events/dummy/1-0-0"), DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);
    }

    @Test
    public void testValidateEvents_valid() throws Exception {
        assertNull(schemaService.getSchema("https://vendor.test.com/schemas/json/events/flattened/1-0-0"));
        assertNull(schemaService.getSchema("https://vendor.test.com/schemas/json/events/flattened/properties/1-0-0"));
        assertNull(schemaService.getSchema("https://vendor.test.com/schemas/json/events/flattened/properties/interests/1-0-0"));

        // Test that at first the flattened event is not valid.
        assertFalse(schemaService.isEventValid(resourceAsString("schemas/event-flattened-valid.json")));

        // save schemas and check the event pass the validation
        schemaService.saveSchema(resourceAsString("schemas/schema-flattened.json"));
        schemaService.saveSchema(resourceAsString("schemas/schema-flattened-flattenedProperties.json"));
        schemaService.saveSchema(resourceAsString("schemas/schema-flattened-flattenedProperties-interests.json"));
        schemaService.saveSchema(resourceAsString("schemas/schema-flattened-properties.json"));

        StringBuilder listEvents = new StringBuilder("");
        listEvents
                .append("[")
                .append(resourceAsString("schemas/event-flattened-valid.json"))
                .append("]");

        keepTrying("No error should have been detected",
                () -> {
                    try {
                        return schemaService.validateEvents(listEvents.toString()).get("flattened").isEmpty();
                    } catch (Exception e) {
                        return false;
                    }
                },
                isValid -> isValid, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        StringBuilder listInvalidEvents = new StringBuilder("");
        listInvalidEvents
                .append("[")
                .append(resourceAsString("schemas/event-flattened-invalid-1.json")).append(",")
                .append(resourceAsString("schemas/event-flattened-invalid-2.json")).append(",")
                .append(resourceAsString("schemas/event-flattened-invalid-3.json")).append(",")
                .append(resourceAsString("schemas/event-flattened-invalid-3.json"))
                .append("]");
        Map<String, Set<ValidationError>> errors = schemaService.validateEvents(listInvalidEvents.toString());

        assertEquals(9, errors.get("flattened").size());
        // Verify that error on interests.football appear only once even if two events have the issue
        assertEquals(1, errors.get("flattened").stream().filter(validationError -> validationError.getError().startsWith("$.flattenedProperties.interests.football")).collect(Collectors.toList()).size());
    }


    @Test
    public void testFlattenedProperties() throws Exception {
        assertNull(schemaService.getSchema("https://vendor.test.com/schemas/json/events/flattened/1-0-0"));
        assertNull(schemaService.getSchema("https://vendor.test.com/schemas/json/events/flattened/properties/1-0-0"));
        assertNull(schemaService.getSchema("https://vendor.test.com/schemas/json/events/flattened/properties/interests/1-0-0"));

        // Test that at first the flattened event is not valid.
        assertFalse(schemaService.isEventValid(resourceAsString("schemas/event-flattened-valid.json")));

        // save schemas and check the event pass the validation
        schemaService.saveSchema(resourceAsString("schemas/schema-flattened.json"));
        schemaService.saveSchema(resourceAsString("schemas/schema-flattened-flattenedProperties.json"));
        schemaService.saveSchema(resourceAsString("schemas/schema-flattened-flattenedProperties-interests.json"));
        schemaService.saveSchema(resourceAsString("schemas/schema-flattened-properties.json"));
        keepTrying("Event should be valid now",
                () -> schemaService.isEventValid(resourceAsString("schemas/event-flattened-valid.json")),
                isValid -> isValid, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        // insure event is correctly indexed when send to /cxs/eventCollector
        Event event = sendEventAndWaitItsIndexed("schemas/event-flattened-valid.json");
        Map<String, Integer> interests = (Map<String, Integer>) event.getFlattenedProperties().get("interests");
        assertEquals(15, interests.get("cars").intValue());
        assertEquals(59, interests.get("football").intValue());
        assertEquals(2, interests.size());

        // check that range query is not working on flattened props:
        Condition condition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        condition.setParameter("propertyName", "flattenedProperties.interests.cars");
        condition.setParameter("comparisonOperator", "greaterThan");
        condition.setParameter("propertyValueInteger", 2);
        assertNull(persistenceService.query(condition, null, Event.class, 0, -1));

        // check that term query is working on flattened props:
        condition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        condition.setParameter("propertyName", "flattenedProperties.interests.cars");
        condition.setParameter("comparisonOperator", "equals");
        condition.setParameter("propertyValueInteger", 15);
        List<Event> events = persistenceService.query(condition, null, Event.class, 0, -1).getList();
        assertEquals(1, events.size());
        assertEquals(events.get(0).getItemId(), event.getItemId());

        // Bonus: Check that other invalid flattened events are correctly rejected by schema service:
        assertFalse(schemaService.isEventValid(resourceAsString("schemas/event-flattened-invalid-1.json")));
        assertFalse(schemaService.isEventValid(resourceAsString("schemas/event-flattened-invalid-2.json")));
        assertFalse(schemaService.isEventValid(resourceAsString("schemas/event-flattened-invalid-3.json")));
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

    private Event sendEventAndWaitItsIndexed(String eventResourcePath) throws Exception {
        // build event collector request
        String eventMarker = UUID.randomUUID().toString();
        HashMap<String, String> eventReplacements = new HashMap<>();
        eventReplacements.put("EVENT_MARKER", eventMarker);
        String event = getValidatedBundleJSON(eventResourcePath, eventReplacements);
        HashMap<String, String> eventRequestReplacements = new HashMap<>();
        eventRequestReplacements.put("EVENTS", event);
        String eventRequest = getValidatedBundleJSON("schemas/event-request.json", eventRequestReplacements);

        // send event
        eventCollectorPost(eventRequest);

        // wait for the event to be indexed
        Condition condition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        condition.setParameter("propertyName", "properties.marker.keyword");
        condition.setParameter("comparisonOperator", "equals");
        condition.setParameter("propertyValue", eventMarker);
        List<Event> events = keepTrying("The event should have been persisted",
                () -> persistenceService.query(condition, null, Event.class), results -> results.size() == 1,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        return events.get(0);
    }

    private void eventCollectorPost(String eventCollectorRequest) throws Exception {
        HttpPost request = new HttpPost(getFullUrl(EVENT_COLLECTOR_URL));
        request.addHeader("Content-Type", "application/json");
        request.setEntity(new StringEntity(eventCollectorRequest, ContentType.create("application/json")));
        CloseableHttpResponse response;
        try {
            response = HttpClientThatWaitsForUnomi.doRequest(request, 200);
        } catch (Exception e) {
            fail("Something went wrong with the request to Unomi that is unexpected: " + e.getMessage());
            return;
        }
        assertEquals("Invalid response code", 200, response.getStatusLine().getStatusCode());
    }
}
