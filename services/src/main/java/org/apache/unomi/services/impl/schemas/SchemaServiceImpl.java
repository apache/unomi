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

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.apache.unomi.api.schema.JSONSchemaExtension;
import org.apache.unomi.api.schema.JSONSchemaEntity;
import org.apache.unomi.api.schema.json.JSONSchema;
import org.apache.unomi.api.schema.json.JSONTypeFactory;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.services.SchemaService;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SchemaServiceImpl implements SchemaService {

    private static final String URI = "https://json-schema.org/draft/2019-09/schema";

    private static final Logger logger = LoggerFactory.getLogger(SchemaServiceImpl.class.getName());

    private final Map<String, JSONSchema> predefinedJsonSchemaById = new HashMap<>();

    private Map<String, JSONSchema> schemasById = new HashMap<>();

    private Map<String, JSONSchemaExtension> extensionById = new HashMap<>();

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
        PartialList<JSONSchemaEntity> items = persistenceService.getAllItems(JSONSchemaEntity.class, offset, size, sortBy);
        List<Metadata> details = new LinkedList<>();
        for (JSONSchemaEntity definition : items.getList()) {
            details.add(definition.getMetadata());
        }
        return new PartialList<>(details, items.getOffset(), items.getPageSize(), items.getTotalSize(), items.getTotalSizeRelation());
    }

    @Override
    public boolean isValid(JsonNode jsonNode, String schemaId) {
        String schemaAsString;
        JsonSchema jsonSchema = null;
        try {
            JSONSchema validationSchema = schemasById.get(schemaId);
            if (validationSchema != null) {
                schemaAsString = objectMapper.writeValueAsString(schemasById.get(schemaId).getSchemaTree());
                jsonSchema = jsonSchemaFactory.getSchema(schemaAsString);
            } else {
                logger.warn("No schema found for {}", schemaId);
            }
        } catch (JsonProcessingException e) {
            logger.error("Failed to process json schema", e);
        }

        if (jsonSchema != null) {

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
        if (predefinedJsonSchemaById.get(jsonSchema.getSchemaNode().get("$id").asText()) == null) {
            persistenceService.save(buildJSONSchemaEntity(schema));
            JSONSchema localSchema = buildJSONSchema(jsonSchema);
            schemasById.put(jsonSchema.getSchemaNode().get("$id").asText(), localSchema);
            findExtensionAndUpdateSchema(jsonSchema.getSchemaNode().get("$id").asText());
        } else {
            logger.error("Can not store a JSON Schema which have the id of a schema provided by Unomi");
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

        predefinedJsonSchemaById.put(jsonSchema.getSchemaNode().get("$id").asText(), localJsonSchema);
        schemasById.put(jsonSchema.getSchemaNode().get("$id").asText(), localJsonSchema);
    }

    @Override
    public boolean deleteSchema(String schemaId) {
        schemasById.remove(schemaId);
        return persistenceService.remove(schemaId, JSONSchemaEntity.class);
    }

    @Override
    public boolean deleteSchema(InputStream schemaStream) {
        JsonNode schemaNode = jsonSchemaFactory.getSchema(schemaStream).getSchemaNode();
        return deleteSchema(schemaNode.get("$id").asText());
    }

    @Override
    public void saveExtension(InputStream extensionStream) throws IOException {
        saveExtension(IOUtils.toString(extensionStream));
    }

    @Override
    public void saveExtension(String extension) throws IOException {
        JSONSchemaExtension jsonSchemaExtension = buildExtension(extension);
        persistenceService.save(jsonSchemaExtension);
        extensionById.put(jsonSchemaExtension.getId(), jsonSchemaExtension);
        findAndUpdateSchemaWithExtension(jsonSchemaExtension.getSchemaId(), jsonSchemaExtension.getId());
    }

    @Override
    public boolean deleteExtension(InputStream extensionStream) throws IOException {
        JsonNode jsonNode = objectMapper.readTree(extensionStream);
        return deleteExtension(jsonNode.get("id").asText());
    }

    @Override
    public boolean deleteExtension(String extensionId) {
        extensionById.remove(extensionId);
        return persistenceService.remove(extensionId, JSONSchemaExtension.class);
    }

    @Override
    public PartialList<Metadata> getJsonSchemaExtensionsMetadatas(int offset, int size, String sortBy) {
        PartialList<JSONSchemaExtension> items = persistenceService.getAllItems(JSONSchemaExtension.class, offset, size, sortBy);
        List<Metadata> details = new LinkedList<>();
        for (JSONSchemaExtension definition : items.getList()) {
            details.add(definition.getMetadata());
        }
        return new PartialList<>(details, items.getOffset(), items.getPageSize(), items.getTotalSize(), items.getTotalSizeRelation());
    }

    private JSONSchemaExtension buildExtension(String extension) throws JsonProcessingException {
        JsonNode jsonNode = objectMapper.readTree(extension);
        JSONSchemaExtension jsonSchemaExtension = new JSONSchemaExtension();
        jsonSchemaExtension.setId(jsonNode.get("id").asText());
        jsonSchemaExtension.setSchemaId(jsonNode.get("schemaId").asText());
        jsonSchemaExtension.setExtension(jsonNode.get("extension").toString());
        jsonSchemaExtension.setPriority(jsonNode.get("priority").asDouble());
        Metadata metadata = new Metadata();
        metadata.setId(jsonNode.get("id").asText());
        metadata.setDescription(jsonNode.get("description").asText());
        metadata.setName(jsonNode.get("name").asText());
        jsonSchemaExtension.setMetadata(metadata);
        return jsonSchemaExtension;
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

    private JSONSchemaEntity buildJSONSchemaEntity(String schema) {
        JsonNode schemaNode = jsonSchemaFactory.getSchema(schema).getSchemaNode();
        return new JSONSchemaEntity(schemaNode.get("$id").asText(), schema, schemaNode.at("/self/target").asText());
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

    private void findExtensionAndUpdateSchema(String schemaId) {
        try {
            String schemaAsString = objectMapper.writeValueAsString(schemasById.get(schemaId).getSchemaTree());
            if (Objects.nonNull(schemaAsString)) {
                extensionById.entrySet().stream().filter(entry -> entry.getValue().getSchemaId().equals(schemaId))
                        .forEach(entry -> findAndUpdateSchemaWithExtension(schemaId, entry.getValue().getId()));
            }
        } catch (JsonProcessingException e) {
            logger.error("Error when merging extensions into schema {}", schemaId, e);
        }
    }

    private void findAndUpdateSchemaWithExtension(String schemaId, String extensionId) {
        try {
            JSONSchema schema = schemasById.get(schemaId);
            if (Objects.nonNull(schema)) {
                String schemaAsString = objectMapper.writeValueAsString(schemasById.get(schemaId).getSchemaTree());
                JsonNode mergedSchema = mergeIntoSchema(objectMapper.readTree(schemaAsString),
                        objectMapper.readTree(extensionById.get(extensionId).getExtension()));
                schemasById.put(mergedSchema.get("$id").asText(), buildJSONSchema(jsonSchemaFactory.getSchema(mergedSchema)));
            }
        } catch (JsonProcessingException e) {
            logger.error("Error when merging extension {} into schema {}", extensionId, schemaId, e);
        }
    }

    private JsonNode mergeIntoSchema(JsonNode schemaNode, JsonNode extension) {
        extension.fields().forEachRemaining((entry) -> {
            String path = JsonPointer.SEPARATOR + entry.getKey();
            JsonNode targetNode = schemaNode.at(path);
            JsonNode nodeToAdd = entry.getValue();
            if (targetNode.isArray()) {
                handleArray((ArrayNode) targetNode, nodeToAdd);
            } else if (targetNode.isObject() && nodeToAdd.isObject()) {
                mergeIntoSchema(targetNode, nodeToAdd);
            } else if (targetNode instanceof MissingNode) {
                ((ObjectNode) schemaNode).set(path.replace("/", ""), nodeToAdd);
            }
        });
        return schemaNode;
    }

    private JsonNode handleArray(ArrayNode targetArray, JsonNode valueToAdd) {
        if (valueToAdd.isArray()) {
            for (JsonNode singleValue : valueToAdd) {
                addToArray(targetArray, singleValue);
            }
        } else {
            addToArray(targetArray, valueToAdd);
        }
        return targetArray;
    }

    private void addToArray(ArrayNode targetedArray, JsonNode value) {
        boolean isPresent = false;
        for (JsonNode target : targetedArray) {
            isPresent = isPresent || target.equals(value);
        }
        if (!isPresent) {
            targetedArray.add(value);
        }
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
        schemasById.putAll(predefinedJsonSchemaById);
        persistenceService.getAllItems(JSONSchemaEntity.class).forEach(
                jsonSchema -> schemasById.put(jsonSchema.getId(), buildJSONSchema(jsonSchemaFactory.getSchema(jsonSchema.getSchema()))));
    }

    private void refreshJSONSchemasExtensions() {
        extensionById = new HashMap<>();
        persistenceService.getAllItems(JSONSchemaExtension.class).forEach(extension -> {
            extensionById.put(extension.getId(), extension);
            findAndUpdateSchemaWithExtension(extension.getSchemaId(), extension.getId());
        });
    }

    private void initializeTimers() {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                refreshJSONSchemas();
                refreshJSONSchemasExtensions();
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
        logger.info("Schema service initialized.");
    }

    public void destroy() {
        scheduledFuture.cancel(true);
        logger.info("Schema service shutdown.");
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }
}
