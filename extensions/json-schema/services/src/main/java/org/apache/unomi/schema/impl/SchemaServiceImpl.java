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

package org.apache.unomi.schema.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.Item;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.schema.api.JsonSchemaWrapper;
import org.apache.unomi.schema.api.SchemaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class SchemaServiceImpl implements SchemaService {

    private static final String URI = "https://json-schema.org/draft/2019-09/schema";

    private static final Logger logger = LoggerFactory.getLogger(SchemaServiceImpl.class.getName());
    private static final String TARGET_EVENTS = "events";

    ObjectMapper objectMapper = new ObjectMapper();

    /**
     *  Schemas provided by Unomi runtime bundles in /META-INF/cxs/schemas/...
     */
    private final ConcurrentMap<String, JsonSchemaWrapper> predefinedUnomiJSONSchemaById = new ConcurrentHashMap<>();
    /**
     * All Unomi schemas indexed by URI
     */
    private ConcurrentMap<String, JsonSchemaWrapper> schemasById = new ConcurrentHashMap<>();
    /**
     * Available extensions indexed by key:schema URI to be extended, value: list of schema extension URIs
     */
    private ConcurrentMap<String, Set<String>> extensions = new ConcurrentHashMap<>();

    private Integer jsonSchemaRefreshInterval = 1000;
    private ScheduledFuture<?> scheduledFuture;

    private PersistenceService persistenceService;
    private JsonSchemaFactory jsonSchemaFactory;

    // TODO UNOMI-572: when fixing UNOMI-572 please remove the usage of the custom ScheduledExecutorService and re-introduce the Unomi Scheduler Service
    private ScheduledExecutorService scheduler;
    //private SchedulerService schedulerService;


    @Override
    public boolean isValid(String data, String schemaId) {
        JsonSchema jsonSchema;
        JsonNode jsonNode;

        try {
            jsonNode = objectMapper.readTree(data);
            jsonSchema = jsonSchemaFactory.getSchema(new URI(schemaId));
        } catch (Exception e) {
            logger.debug("Schema validation failed", e);
            return false;
        }

        if (jsonNode == null) {
            logger.debug("Schema validation failed because: no data to validate");
            return false;
        }

        if (jsonSchema == null) {
            logger.debug("Schema validation failed because: Schema not found {}", schemaId);
            return false;
        }

        Set<ValidationMessage> validationMessages;
        try {
            validationMessages = jsonSchema.validate(jsonNode);
        } catch (Exception e) {
            logger.debug("Schema validation failed", e);
            return false;
        }

        if (validationMessages == null || validationMessages.isEmpty()) {
            return true;
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("Schema validation found {} errors while validating against schema: {}", validationMessages.size(), schemaId);
                for (ValidationMessage validationMessage : validationMessages) {
                    logger.debug("Validation error: {}", validationMessage);
                }
            }
            return false;
        }
    }

    @Override
    public boolean isEventValid(String event, String eventType) {
        JsonSchemaWrapper eventSchema = getSchemaForEventType(eventType);
        if (eventSchema != null) {
            return isValid(event, eventSchema.getItemId());
        }

        // Event schema not found
        return false;
    }

    @Override
    public JsonSchemaWrapper getSchema(String schemaId) {
        return schemasById.get(schemaId);
    }

    @Override
    public Set<String> getInstalledJsonSchemaIds() {
        return schemasById.keySet();
    }

    @Override
    public List<JsonSchemaWrapper> getSchemasByTarget(String target) {
        return schemasById.values().stream()
                .filter(jsonSchemaWrapper -> jsonSchemaWrapper.getTarget() != null && jsonSchemaWrapper.getTarget().equals(target))
                .collect(Collectors.toList());
    }

    @Override
    public JsonSchemaWrapper getSchemaForEventType(String eventType) {
        if (StringUtils.isEmpty(eventType)) {
            return null;
        }

        return schemasById.values().stream()
                .filter(jsonSchemaWrapper ->
                        jsonSchemaWrapper.getTarget() != null &&
                        jsonSchemaWrapper.getTarget().equals(TARGET_EVENTS) &&
                        jsonSchemaWrapper.getName() != null &&
                        jsonSchemaWrapper.getName().equals(eventType))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void saveSchema(String schema) {
        JsonSchemaWrapper jsonSchemaWrapper = buildJsonSchemaWrapper(schema);
        if (!predefinedUnomiJSONSchemaById.containsKey(jsonSchemaWrapper.getItemId())) {
            persistenceService.save(jsonSchemaWrapper);
        } else {
            throw new IllegalArgumentException("Trying to save a Json Schema that is using the ID of an existing Json Schema provided by Unomi is forbidden");
        }
    }

    @Override
    public boolean deleteSchema(String schemaId) {
        // forbidden to delete predefined Unomi schemas
        if (!predefinedUnomiJSONSchemaById.containsKey(schemaId)) {
            // remove persisted schema
            return persistenceService.remove(schemaId, JsonSchemaWrapper.class);
        }
        return false;
    }

    @Override
    public void loadPredefinedSchema(InputStream schemaStream) throws IOException {
        String schema = IOUtils.toString(schemaStream);
        JsonSchemaWrapper jsonSchemaWrapper = buildJsonSchemaWrapper(schema);
        predefinedUnomiJSONSchemaById.put(jsonSchemaWrapper.getItemId(), jsonSchemaWrapper);
    }

    @Override
    public boolean unloadPredefinedSchema(InputStream schemaStream) {
        JsonNode schemaNode = jsonSchemaFactory.getSchema(schemaStream).getSchemaNode();
        String schemaId = schemaNode.get("$id").asText();
        return predefinedUnomiJSONSchemaById.remove(schemaId) != null;
    }

    private JsonSchemaWrapper buildJsonSchemaWrapper(String schema) {
        JsonSchema jsonSchema = jsonSchemaFactory.getSchema(schema);
        JsonNode schemaNode = jsonSchema.getSchemaNode();

        String schemaId = schemaNode.get("$id").asText();
        String target = schemaNode.at("/self/target").asText();
        String name = schemaNode.at("/self/name").asText();
        String extendsSchemaId = schemaNode.at("/self/extends").asText();

        if (TARGET_EVENTS.equals(target) && !name.matches("[_A-Za-z][_0-9A-Za-z]*")) {
            throw new IllegalArgumentException(
                    "The \"/self/name\" value should match the following regular expression [_A-Za-z][_0-9A-Za-z]* for the Json schema on events");
        }

        return new JsonSchemaWrapper(schemaId, schema, target, name, extendsSchemaId, new Date());
    }

    private void refreshJSONSchemas() {
        // use local variable to avoid concurrency issues.
        Map<String, JsonSchemaWrapper> schemasByIdReloaded = new HashMap<>();
        schemasByIdReloaded.putAll(predefinedUnomiJSONSchemaById);
        schemasByIdReloaded.putAll(persistenceService.getAllItems(JsonSchemaWrapper.class).stream().collect(Collectors.toMap(Item::getItemId, s -> s)));

        // flush cache if size is different (can be new schema or deleted schemas)
        boolean changes = schemasByIdReloaded.size() != schemasById.size();
        // check for modifications
        if (!changes) {
            for (JsonSchemaWrapper reloadedSchema : schemasByIdReloaded.values()) {
                JsonSchemaWrapper oldSchema = schemasById.get(reloadedSchema.getItemId());
                if (oldSchema == null || !oldSchema.getTimeStamp().equals(reloadedSchema.getTimeStamp())) {
                    changes = true;
                    break;
                }
            }
        }

        if (changes) {
            schemasById = new ConcurrentHashMap<>(schemasByIdReloaded);

            initExtensions(schemasByIdReloaded);
            initJsonSchemaFactory();
        }
    }

    private void initExtensions(Map<String, JsonSchemaWrapper> schemas) {
        Map<String, Set<String>> extensionsReloaded = new HashMap<>();
        // lookup extensions
        List<JsonSchemaWrapper> schemaExtensions = schemas.values()
                .stream()
                .filter(jsonSchemaWrapper -> StringUtils.isNotBlank(jsonSchemaWrapper.getExtendsSchemaId()))
                .collect(Collectors.toList());

        // build new in RAM extensions map
        for (JsonSchemaWrapper extension : schemaExtensions) {
            String extendedSchemaId = extension.getExtendsSchemaId();
            if (!extension.getItemId().equals(extendedSchemaId)) {
                if (!extensionsReloaded.containsKey(extendedSchemaId)) {
                    extensionsReloaded.put(extendedSchemaId, new HashSet<>());
                }
                extensionsReloaded.get(extendedSchemaId).add(extension.getItemId());
            } else {
                logger.warn("A schema cannot extends himself, please fix your schema definition for schema: {}", extendedSchemaId);
            }
        }

        extensions = new ConcurrentHashMap<>(extensionsReloaded);
    }

    private String generateExtendedSchema(String id, String schema) throws JsonProcessingException {
        Set<String> extensionIds = extensions.get(id);
        if (extensionIds != null && extensionIds.size() > 0) {
            // This schema need to be extends !
            ObjectNode jsonSchema = (ObjectNode) objectMapper.readTree(schema);
            ArrayNode allOf;
            if (jsonSchema.at("/allOf") instanceof MissingNode) {
                allOf = objectMapper.createArrayNode();
            } else if (jsonSchema.at("/allOf") instanceof ArrayNode){
                allOf = (ArrayNode) jsonSchema.at("/allOf");
            } else {
                logger.warn("Cannot extends schema allOf property, it should be an Array, please fix your schema definition for schema: {}", id);
                return schema;
            }

            // Add each extension URIs as new ref in the allOf
            for (String extensionId : extensionIds) {
                ObjectNode newAllOf = objectMapper.createObjectNode();
                newAllOf.put("$ref", extensionId);
                allOf.add(newAllOf);
            }

            // generate new extended schema as String
            jsonSchema.putArray("allOf").addAll(allOf);
            return objectMapper.writeValueAsString(jsonSchema);
        }
        return schema;
    }

    private void initPersistenceIndex() {
        if (persistenceService.createIndex(JsonSchemaWrapper.ITEM_TYPE)) {
            logger.info("{} index created", JsonSchemaWrapper.ITEM_TYPE);
        } else {
            logger.info("{} index already exists", JsonSchemaWrapper.ITEM_TYPE);
        }
    }

    private void initTimers() {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                try {
                    refreshJSONSchemas();
                } catch (Exception e) {
                    logger.error("Error while refreshing JSON Schemas", e);
                }
            }
        };
        scheduledFuture = scheduler.scheduleWithFixedDelay(task, 0, jsonSchemaRefreshInterval, TimeUnit.MILLISECONDS);
    }

    private void initJsonSchemaFactory() {
        jsonSchemaFactory = JsonSchemaFactory.builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909))
                .addMetaSchema(JsonMetaSchema.builder(URI, JsonMetaSchema.getV201909())
                        .addKeyword(new NonValidationKeyword("self"))
                        .build())
                .defaultMetaSchemaURI(URI)
                .uriFetcher(uri -> {
                    logger.debug("Fetching schema {}", uri);
                    String schemaId = uri.toString();
                    JsonSchemaWrapper jsonSchemaWrapper = getSchema(schemaId);
                    if (jsonSchemaWrapper == null) {
                        logger.error("Couldn't find schema {}", uri);
                        return null;
                    }

                    String schema = jsonSchemaWrapper.getSchema();
                    // Check if schema need to be extended
                    schema = generateExtendedSchema(schemaId, schema);

                    return IOUtils.toInputStream(schema);
                }, "https", "http")
                .build();
    }

    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        initPersistenceIndex();
        initJsonSchemaFactory();
        initTimers();
        logger.info("Schema service initialized.");
    }

    public void destroy() {
        scheduledFuture.cancel(true);
        if (scheduler != null) {
            scheduler.shutdown();
        }
        logger.info("Schema service shutdown.");
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setJsonSchemaRefreshInterval(Integer jsonSchemaRefreshInterval) {
        this.jsonSchemaRefreshInterval = jsonSchemaRefreshInterval;
    }
}
