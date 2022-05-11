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
import com.networknt.schema.*;
import com.networknt.schema.uri.URIFetcher;
import org.apache.commons.io.IOUtils;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.schema.api.JsonSchemaWrapper;
import org.apache.unomi.schema.api.SchemaService;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SchemaServiceImpl implements SchemaService {

    private static final String URI = "https://json-schema.org/draft/2019-09/schema";

    private static final Logger logger = LoggerFactory.getLogger(SchemaServiceImpl.class.getName());

    ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, JsonSchemaWrapper> predefinedUnomiJSONSchemaById = new HashMap<>();
    private Map<String, JsonSchemaWrapper> schemasById = new HashMap<>();

    private Integer jsonSchemaRefreshInterval = 1000;
    private ScheduledFuture<?> scheduledFuture;

    private BundleContext bundleContext;
    private PersistenceService persistenceService;
    private SchedulerService schedulerService;
    private JsonSchemaFactory jsonSchemaFactory;


    @Override
    public PartialList<Metadata> getJsonSchemaMetadatas(int offset, int size, String sortBy) {
        PartialList<JsonSchemaWrapper> items = persistenceService.getAllItems(JsonSchemaWrapper.class, offset, size, sortBy);
        List<Metadata> details = new LinkedList<>();
        for (JsonSchemaWrapper definition : items.getList()) {
            details.add(definition.getMetadata());
        }
        return new PartialList<>(details, items.getOffset(), items.getPageSize(), items.getTotalSize(), items.getTotalSizeRelation());
    }

    @Override
    public boolean isValid(String data, String schemaId) {
        JsonSchema jsonSchema = null;
        JsonNode jsonNode = null;

        try {
            jsonNode = objectMapper.readTree(data);
            jsonSchema = jsonSchemaFactory.getSchema(new URI(schemaId));
        } catch (Exception e) {
            logger.error("Failed to process data to validate because {} - Set SchemaServiceImpl at DEBUG level for more detail ", e.getMessage());
            logger.debug("full error",e);
            return false;
        }

        if (jsonNode == null) {
            logger.warn("No data to validate");
            return false;
        }

        if (jsonSchema == null) {
            logger.warn("No schema found for {}", schemaId);
            return false;
        }

        Set<ValidationMessage> validationMessages = jsonSchema.validate(jsonNode);
        if (validationMessages == null || validationMessages.isEmpty()) {
            return true;
        }
        for (ValidationMessage validationMessage : validationMessages) {
            logger.error("Error validating object against schema {}: {}", schemaId, validationMessage);
        }
        return false;
    }

    @Override
    public List<JsonSchemaWrapper> getSchemasByTarget(String target) {
        return schemasById.values().stream().filter(jsonSchemaWrapper -> jsonSchemaWrapper.getTarget() != null && jsonSchemaWrapper.getTarget().equals(target))
                .collect(Collectors.toList());
    }

    @Override
    public void saveSchema(String schema) {
        JsonSchema jsonSchema = jsonSchemaFactory.getSchema(schema);
        JsonNode schemaNode = jsonSchema.getSchemaNode();
        String id = schemaNode.get("$id").asText();

        if (!predefinedUnomiJSONSchemaById.containsKey(id)) {
            String target = schemaNode.at("/self/target").asText();
            String name = schemaNode.at("/self/name").asText();

            if ("events".equals(target) && !name.matches("[_A-Za-z][_0-9A-Za-z]*")) {
                throw new IllegalArgumentException(
                        "The \"/self/name\" value should match the following regular expression [_A-Za-z][_0-9A-Za-z]* for the Json schema on events");
            }

            JsonSchemaWrapper jsonSchemaWrapper = new JsonSchemaWrapper(id, schema, target);
            persistenceService.save(jsonSchemaWrapper);
            schemasById.put(id, jsonSchemaWrapper);
        } else {
            // TODO we should crash error in case save failed, so that caller can react on save fail and get the reason why using Exceptions
            logger.error("Trying to save a Json Schema that is using the ID of an existing Json Schema provided by Unomi is forbidden");
        }
    }

    @Override
    public boolean deleteSchema(String schemaId) {
        // forbidden to delete predefined Unomi schemas
        if (!predefinedUnomiJSONSchemaById.containsKey(schemaId)) {
            schemasById.remove(schemaId);
            return persistenceService.remove(schemaId, JsonSchemaWrapper.class);
        }
        return false;
    }

    @Override
    public void loadPredefinedSchema(InputStream schemaStream) throws IOException {
        String jsonSchema = IOUtils.toString(schemaStream);

        // check that schema is valid and get the id
        JsonNode schemaNode = jsonSchemaFactory.getSchema(jsonSchema).getSchemaNode();
        String schemaId = schemaNode.get("$id").asText();
        String target = schemaNode.at("/self/target").asText();
        JsonSchemaWrapper jsonSchemaWrapper = new JsonSchemaWrapper(schemaId, jsonSchema, target);

        predefinedUnomiJSONSchemaById.put(schemaId, jsonSchemaWrapper);
        schemasById.put(schemaId, jsonSchemaWrapper);
    }

    @Override
    public boolean unloadPredefinedSchema(InputStream schemaStream) {
        JsonNode schemaNode = jsonSchemaFactory.getSchema(schemaStream).getSchemaNode();
        String schemaId = schemaNode.get("$id").asText();

        return predefinedUnomiJSONSchemaById.remove(schemaId) != null && schemasById.remove(schemaId) != null;
    }

    @Override
    public JsonSchemaWrapper getSchema(String schemaId) {
        return schemasById.get(schemaId);
    }

    private URIFetcher getUriFetcher() {
        return uri -> {
            logger.debug("Fetching schema {}", uri);
            JsonSchemaWrapper jsonSchemaWrapper = schemasById.get(uri.toString());
            if (jsonSchemaWrapper == null) {
                logger.error("Couldn't find schema {}", uri);
                return null;
            }
            return IOUtils.toInputStream(jsonSchemaWrapper.getSchema());
        };
    }

    private void refreshJSONSchemas() {
        schemasById = new HashMap<>();
        schemasById.putAll(predefinedUnomiJSONSchemaById);

        persistenceService.getAllItems(JsonSchemaWrapper.class).forEach(
                JsonSchemaWrapper -> schemasById.put(JsonSchemaWrapper.getId(), JsonSchemaWrapper));
    }

    private void initializeTimers() {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                refreshJSONSchemas();
            }
        };
        scheduledFuture = schedulerService.getScheduleExecutorService()
                .scheduleWithFixedDelay(task, 0, jsonSchemaRefreshInterval, TimeUnit.MILLISECONDS);
    }

    public void init() {
        JsonMetaSchema jsonMetaSchema = JsonMetaSchema.builder(URI, JsonMetaSchema.getV201909())
                .addKeyword(new NonValidationKeyword("self"))
                .build();

        jsonSchemaFactory = JsonSchemaFactory.builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909))
                .addMetaSchema(jsonMetaSchema)
                .defaultMetaSchemaURI(URI)
                .uriFetcher(getUriFetcher(), "https", "http")
                .build();

        initializeTimers();
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

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }
}
