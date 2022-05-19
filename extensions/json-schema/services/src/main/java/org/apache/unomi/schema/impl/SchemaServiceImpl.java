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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.*;
import com.networknt.schema.uri.URIFetcher;
import org.apache.commons.io.IOUtils;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.schema.api.JsonSchemaWrapper;
import org.apache.unomi.schema.api.SchemaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SchemaServiceImpl implements SchemaService {

    private static final String URI = "https://json-schema.org/draft/2019-09/schema";

    private static final Logger logger = LoggerFactory.getLogger(SchemaServiceImpl.class.getName());

    ObjectMapper objectMapper = new ObjectMapper();

    private final ConcurrentMap<String, JsonSchemaWrapper> predefinedUnomiJSONSchemaById = new ConcurrentHashMap<>();
    private ConcurrentMap<String, JsonSchemaWrapper> schemasById = new ConcurrentHashMap<>();

    private final JsonMetaSchema jsonMetaSchema = JsonMetaSchema.builder(URI, JsonMetaSchema.getV201909())
            .addKeyword(new NonValidationKeyword("self"))
            .build();
    private final URIFetcher jsonSchemaURIFetcher = uri -> {
        logger.debug("Fetching schema {}", uri);
        JsonSchemaWrapper jsonSchemaWrapper = schemasById.get(uri.toString());
        if (jsonSchemaWrapper == null) {
            logger.error("Couldn't find schema {}", uri);
            return null;
        }

        return IOUtils.toInputStream(jsonSchemaWrapper.getSchema());
    };

    private Integer jsonSchemaRefreshInterval = 1000;
    private ScheduledFuture<?> scheduledFuture;

    private PersistenceService persistenceService;
    private SchedulerService schedulerService;
    private JsonSchemaFactory jsonSchemaFactory;

    @Override
    public boolean isValid(String data, String schemaId) {
        JsonSchema jsonSchema;
        JsonNode jsonNode;

        try {
            jsonNode = objectMapper.readTree(data);
            jsonSchema = jsonSchemaFactory.getSchema(new URI(schemaId));
        } catch (Exception e) {
            logger.error("Schema validation failed because: {} - Set SchemaServiceImpl at DEBUG level for more detail ", e.getMessage());
            logger.debug("full error",e);
            return false;
        }

        if (jsonNode == null) {
            // no data to validate
            return false;
        }

        if (jsonSchema == null) {
            logger.warn("Schema validation failed because: Schema not found {}", schemaId);
            return false;
        }

        Set<ValidationMessage> validationMessages;
        try {
            validationMessages = jsonSchema.validate(jsonNode);
        } catch (Exception e) {
            logger.error("Schema validation failed because: {} - Set SchemaServiceImpl at DEBUG level for more detail ", e.getMessage());
            logger.debug("full error",e);
            return false;
        }

        if (validationMessages == null || validationMessages.isEmpty()) {
            return true;
        } else {
            logger.error("Schema validation found {} errors while validating against schema: {}  - Set SchemaServiceImpl at DEBUG level for more detail ", validationMessages.size(), schemaId);
            if (logger.isDebugEnabled()) {
                for (ValidationMessage validationMessage : validationMessages) {
                    logger.debug("Validation error: {}", validationMessage);
                }
            }
            return false;
        }
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
        return schemasById.values().stream().filter(jsonSchemaWrapper -> jsonSchemaWrapper.getTarget() != null && jsonSchemaWrapper.getTarget().equals(target))
                .collect(Collectors.toList());
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
        String extendsSchema = schemaNode.at("/self/extends").asText();

        if ("events".equals(target) && !name.matches("[_A-Za-z][_0-9A-Za-z]*")) {
            throw new IllegalArgumentException(
                    "The \"/self/name\" value should match the following regular expression [_A-Za-z][_0-9A-Za-z]* for the Json schema on events");
        }

        return new JsonSchemaWrapper(schemaId, schema, target, extendsSchema, new Date());
    }

    private void refreshJSONSchemas() {
        // use local variable to avoid concurrency issues.
        ConcurrentMap<String, JsonSchemaWrapper> schemasByIdReloaded = new ConcurrentHashMap<>();
        schemasByIdReloaded.putAll(predefinedUnomiJSONSchemaById);
        schemasByIdReloaded.putAll(persistenceService.getAllItems(JsonSchemaWrapper.class).stream().collect(Collectors.toMap(Item::getItemId, s -> s)));

        // flush cache if size is different (can be new schema or deleted schemas)
        boolean flushCache = schemasByIdReloaded.size() != schemasById.size();
        // check dates
        if (!flushCache) {
            for (JsonSchemaWrapper reloadedSchema : schemasByIdReloaded.values()) {
                JsonSchemaWrapper oldSchema = schemasById.get(reloadedSchema.getItemId());
                if (oldSchema == null || !oldSchema.getTimeStamp().equals(reloadedSchema.getTimeStamp())) {
                    flushCache = true;
                    break;
                }
            }
        }

        if (flushCache) {
            initJsonSchemaFactory();
        }
        schemasById = schemasByIdReloaded;
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
                refreshJSONSchemas();
            }
        };
        scheduledFuture = schedulerService.getScheduleExecutorService()
                .scheduleWithFixedDelay(task, 0, jsonSchemaRefreshInterval, TimeUnit.MILLISECONDS);
    }

    private void initJsonSchemaFactory() {
        jsonSchemaFactory = JsonSchemaFactory.builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909))
                .addMetaSchema(jsonMetaSchema)
                .defaultMetaSchemaURI(URI)
                .uriFetcher(jsonSchemaURIFetcher, "https", "http")
                .build();
    }

    public void init() {
        initPersistenceIndex();
        initJsonSchemaFactory();
        initTimers();
        logger.info("Schema service initialized.");
    }

    public void destroy() {
        scheduledFuture.cancel(true);
        logger.info("Schema service shutdown.");
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setSchedulerService(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    public void setJsonSchemaRefreshInterval(Integer jsonSchemaRefreshInterval) {
        this.jsonSchemaRefreshInterval = jsonSchemaRefreshInterval;
    }
}
