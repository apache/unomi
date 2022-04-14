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

package org.apache.unomi.services.impl.schemas;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.NonValidationKeyword;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.uri.URIFetcher;
import org.apache.commons.io.IOUtils;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.schema.UnomiJSONSchema;
import org.apache.unomi.api.schema.json.JSONSchema;
import org.apache.unomi.api.schema.json.JSONTypeFactory;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.services.SchemaRegistry;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SchemaRegistryImpl implements SchemaRegistry {

    private static final String URI = "https://json-schema.org/draft/2019-09/schema";

    private static final Logger logger = LoggerFactory.getLogger(SchemaRegistryImpl.class.getName());

    private final Map<String, JSONSchema> predefinedUnomiJSONSchemaById = new HashMap<>();

    private Map<String, JSONSchema> schemasById = new HashMap<>();

    private BundleContext bundleContext;

    private ProfileService profileService;

    private PersistenceService persistenceService;

    private SchedulerService schedulerService;

    private JsonSchemaFactory jsonSchemaFactory;

    ObjectMapper objectMapper = new ObjectMapper();

    private ScheduledFuture<?> scheduledFuture;

    private Integer jsonSchemaRefreshInterval = 1000;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setSchedulerService(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    public void setJsonSchemaRefreshInterval(Integer jsonSchemaRefreshInterval) {
        this.jsonSchemaRefreshInterval = jsonSchemaRefreshInterval;
    }

    @Override
    public PartialList<Metadata> getJsonSchemaMetadatas(int offset, int size, String sortBy) {
        PartialList<UnomiJSONSchema> items = persistenceService.getAllItems(UnomiJSONSchema.class, offset, size, sortBy);
        List<Metadata> details = new LinkedList<>();
        for (UnomiJSONSchema definition : items.getList()) {
            details.add(definition.getMetadata());
        }
        return new PartialList<>(details, items.getOffset(), items.getPageSize(), items.getTotalSize(), items.getTotalSizeRelation());
    }

    @Override
    public boolean isValid(Object object, String schemaId) {
        String schemaAsString;
        JsonSchema jsonSchema = null;
        try {
            JSONSchema validationSchema = schemasById.get(schemaId);
            if (validationSchema != null){
                schemaAsString = objectMapper.writeValueAsString(schemasById.get(schemaId).getSchemaTree());
                jsonSchema = jsonSchemaFactory.getSchema(schemaAsString);
            } else {
                logger.warn("No schema found for {}", schemaId);
            }
        } catch (JsonProcessingException e) {
            logger.error("Failed to process json schema", e);
        }

        if (jsonSchema != null) {
            JsonNode jsonNode = CustomObjectMapper.getObjectMapper().convertValue(object, JsonNode.class);
            Set<ValidationMessage> validationMessages = jsonSchema.validate(jsonNode);
            if (validationMessages == null || validationMessages.isEmpty()) {
                return true;
            }
            for (ValidationMessage validationMessage : validationMessages) {
                logger.error("Error validating object against schema {}: {}", schemaId, validationMessage);
            }
            return false;
        }
        return false;
    }

    @Override
    public List<JSONSchema> getSchemasByTarget(String target) {
        return schemasById.values().stream().filter(jsonSchema -> jsonSchema.getTarget() != null && jsonSchema.getTarget().equals(target))
                .collect(Collectors.toList());

    }

    @Override
    public void saveSchema(String schema) {
        JsonSchema jsonSchema = jsonSchemaFactory.getSchema(schema);
        if (predefinedUnomiJSONSchemaById.get(jsonSchema.getSchemaNode().get("$id").asText()) == null) {
            persistenceService.save(buildUnomiJsonSchema(schema));
            JSONSchema localSchema = buildJSONSchema(jsonSchema);
            schemasById.put(jsonSchema.getSchemaNode().get("$id").asText(), localSchema);
        } else {
            logger.error("Can not store a JSON Schema which have the id of a schema preovided by Unomi");
        }
    }

    @Override
    public void saveSchema(InputStream schemaStream) throws IOException {
        saveSchema(IOUtils.toString(schemaStream));
    }

    @Override
    public void loadPredefinedSchema(InputStream schemaStream) {
        JsonSchema jsonSchema = jsonSchemaFactory.getSchema(schemaStream);
        JSONSchema localJsonSchema = buildJSONSchema(jsonSchema);

        predefinedUnomiJSONSchemaById.put(jsonSchema.getSchemaNode().get("$id").asText(), localJsonSchema);
        schemasById.put(jsonSchema.getSchemaNode().get("$id").asText(), localJsonSchema);
    }

    @Override
    public boolean deleteSchema(String schemaId) {
        schemasById.remove(schemaId);
        return persistenceService.remove(schemaId, UnomiJSONSchema.class);
    }

    @Override
    public boolean deleteSchema(InputStream schemaStream) {
        JsonNode schemaNode = jsonSchemaFactory.getSchema(schemaStream).getSchemaNode();
        return deleteSchema(schemaNode.get("$id").asText());
    }

    @Override
    public JSONSchema getSchema(String schemaId) {
        return schemasById.get(schemaId);
    }

    private JSONSchema buildJSONSchema(JsonSchema jsonSchema) {
        return Optional.of(jsonSchema).map(jsonSchemaToProcess -> {
            try {
                return (Map<String, Object>) objectMapper.treeToValue(jsonSchemaToProcess.getSchemaNode(), Map.class);
            } catch (JsonProcessingException e) {
                logger.error("Failed to process Json object, e");
            }
            return Collections.<String, Object>emptyMap();
        }).map(jsonSchemaToProcess -> {
            JSONSchema schema = new JSONSchema(jsonSchemaToProcess, new JSONTypeFactory(this));
            schema.setPluginId(bundleContext.getBundle().getBundleId());
            return schema;
        }).get();
    }

    private UnomiJSONSchema buildUnomiJsonSchema(String schema) {
        JsonNode schemaNode = jsonSchemaFactory.getSchema(schema).getSchemaNode();
        return new UnomiJSONSchema(schemaNode.get("$id").asText(), schema, schemaNode.at("/self/target").asText());
    }

    public JsonSchema getJsonSchema(String schemaId) {
        String schemaAsString = null;
        try {
            schemaAsString = objectMapper.writeValueAsString(schemasById.get(schemaId).getSchemaTree());
        } catch (JsonProcessingException e) {
            logger.error("Failed to process json schema", e);
        }
        return jsonSchemaFactory.getSchema(schemaAsString);
    }

    private URIFetcher getUriFetcher() {
        return uri -> {
            logger.debug("Fetching schema {}", uri);
            String schemaAsString = null;
            try {
                schemaAsString = objectMapper.writeValueAsString(schemasById.get(uri.toString()).getSchemaTree());
            } catch (JsonProcessingException e) {
                logger.error("Failed to process json schema", e);
            }
            JsonSchema schema = jsonSchemaFactory.getSchema(schemaAsString);
            if (schema == null) {
                logger.error("Couldn't find schema {}", uri);
                return null;
            }
            return IOUtils.toInputStream(schema.getSchemaNode().asText());
        };
    }

    private void refreshJSONSchemas() {
        schemasById = new HashMap<>();
        schemasById.putAll(predefinedUnomiJSONSchemaById);
        persistenceService.getAllItems(UnomiJSONSchema.class).forEach(
                jsonSchema -> schemasById.put(jsonSchema.getId(), buildJSONSchema(jsonSchemaFactory.getSchema(jsonSchema.getSchema()))));
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
                .addKeyword(new UnomiPropertyTypeKeyword(profileService, this)).addKeyword(new NonValidationKeyword("self")).build();
        jsonSchemaFactory = JsonSchemaFactory.builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909))
                .addMetaSchema(jsonMetaSchema).defaultMetaSchemaURI(URI).uriFetcher(getUriFetcher(), "https", "http").build();

        initializeTimers();
        logger.info("Schema registry initialized.");
    }

    public void destroy() {
        scheduledFuture.cancel(true);
        logger.info("Schema registry shutdown.");
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }
}
