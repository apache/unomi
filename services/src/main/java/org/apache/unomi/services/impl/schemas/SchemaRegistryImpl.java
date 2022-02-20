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
import com.networknt.schema.*;
import com.networknt.schema.uri.URIFetcher;
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.schema.json.JSONSchema;
import org.apache.unomi.api.schema.json.JSONTypeFactory;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.services.SchemaRegistry;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SchemaRegistryImpl implements SchemaRegistry, SynchronousBundleListener {

    private static final String URI = "https://json-schema.org/draft/2019-09/schema";

    private static final Logger logger = LoggerFactory.getLogger(SchemaRegistryImpl.class.getName());

    private final Map<Long, List<JSONSchema>> schemaTypesByBundle = new HashMap<>();
    private final Map<String, JSONSchema> schemaTypesById = new HashMap<>();

    private final Map<String, JsonSchema> jsonSchemasById = new LinkedHashMap<>();

    private final Map<String, Long> bundleIdBySchemaId = new HashMap<>();

    private BundleContext bundleContext;

    private ProfileService profileService;

    private JsonSchemaFactory jsonSchemaFactory;

    ObjectMapper objectMapper = new ObjectMapper();

    Pattern uriPathPattern = Pattern.compile("/schemas/json(.*)/\\d-\\d-\\d");

    public void bundleChanged(BundleEvent event) {
        switch (event.getType()) {
            case BundleEvent.STARTED:
                processBundleStartup(event.getBundle().getBundleContext());
                break;
            case BundleEvent.STOPPING:
                processBundleStop(event.getBundle().getBundleContext());
                break;
        }
    }

    public void init() {

        JsonMetaSchema jsonMetaSchema = JsonMetaSchema.builder(URI, JsonMetaSchema.getV201909())
                .addKeyword(new PropertyTypeKeyword(profileService, this))
                .addKeyword(new NonValidationKeyword("self"))
                .build();
        jsonSchemaFactory = JsonSchemaFactory.builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909))
                .addMetaSchema(jsonMetaSchema)
                .defaultMetaSchemaURI(URI)
                .uriFetcher(getBundleUriFetcher(bundleContext), "https", "http").build();

        processBundleStartup(bundleContext);

        // process already started bundles
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null && bundle.getBundleId() != bundleContext.getBundle().getBundleId()) {
                processBundleStartup(bundle.getBundleContext());
            }
        }

        bundleContext.addBundleListener(this);
        logger.info("Schema registry initialized.");
    }

    public void destroy() {
        bundleContext.removeBundleListener(this);
        logger.info("Schema registry shutdown.");
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public boolean isValid(Object object, String schemaId) {
        JsonSchema jsonSchema = jsonSchemasById.get(schemaId);
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
    public List<JSONSchema> getTargetSchemas(String target) {
        return schemaTypesById.values().stream().filter(jsonSchema -> jsonSchema.getTarget() != null && jsonSchema.getTarget().equals(target)).collect(Collectors.toList());
    }

    @Override
    public JSONSchema getSchema(String schemaId) {
        return schemaTypesById.get(schemaId);
    }

    private void loadPredefinedSchemas(BundleContext bundleContext) {
        Enumeration<URL> predefinedSchemas = bundleContext.getBundle().findEntries("META-INF/cxs/schemas", "*.json", true);
        if (predefinedSchemas == null) {
            return;
        }

        List<JSONSchema> jsonSchemas = this.schemaTypesByBundle.get(bundleContext.getBundle().getBundleId());

        while (predefinedSchemas.hasMoreElements()) {
            URL predefinedSchemaURL = predefinedSchemas.nextElement();
            logger.debug("Found predefined JSON schema at " + predefinedSchemaURL + ", loading... ");

            try (InputStream schemaInputStream = predefinedSchemaURL.openStream()) {
                JsonSchema jsonSchema = jsonSchemaFactory.getSchema(schemaInputStream);
                String schemaTarget = null;
                String[] splitPath = predefinedSchemaURL.getPath().split("/");
                if (splitPath.length > 5) {
                    String target = splitPath[4];
                    if (StringUtils.isNotBlank(target)) {
                        schemaTarget = target;
                    }
                }
                registerSchema(bundleContext.getBundle().getBundleId(), jsonSchemas, schemaTarget, jsonSchema);
            } catch (Exception e) {
                logger.error("Error while loading schema definition " + predefinedSchemaURL, e);
            }
        }

    }

    public String registerSchema(String target, InputStream jsonSchemaInputStream) {
        JsonSchema jsonSchema = jsonSchemaFactory.getSchema(jsonSchemaInputStream);
        try {
            return registerSchema(null, null, target, jsonSchema);
        } catch (JsonProcessingException e) {
            logger.error("Error registering JSON schema", e);
            return null;
        }
    }

    public boolean unregisterSchema(String target, String schemaId) {
        jsonSchemasById.remove(schemaId);
        schemaTypesById.remove(schemaId);
        return true;
    }

    private String registerSchema(Long bundleId, List<JSONSchema> jsonSchemas, String target, JsonSchema jsonSchema) throws JsonProcessingException {
        String schemaId = jsonSchema.getSchemaNode().get("$id").asText();
        jsonSchemasById.put(schemaId, jsonSchema);
        if (bundleContext != null) {
            bundleIdBySchemaId.put(schemaId, bundleId);
        }
        Map<String, Object> schemaTree = (Map<String, Object>) objectMapper.treeToValue(jsonSchema.getSchemaNode(), Map.class);
        JSONSchema unomiJsonSchema = new JSONSchema(schemaTree, new JSONTypeFactory(this), this);
        if (bundleId != null) {
            unomiJsonSchema.setPluginId(bundleId);
        }
        unomiJsonSchema.setSchemaId(schemaId);
        unomiJsonSchema.setTarget(target);
        if (jsonSchemas != null) {
            jsonSchemas.add(unomiJsonSchema);
        }
        schemaTypesById.put(schemaId, unomiJsonSchema);
        return schemaId;
    }

    private URIFetcher getBundleUriFetcher(BundleContext bundleContext) {
        return uri -> {
            logger.debug("Fetching schema {}", uri);
            Long bundleId = bundleIdBySchemaId.get(uri.toString());
            if (bundleId == null) {
                logger.error("Couldn't find bundle for schema {}", uri);
                return null;
            }
            Matcher uriPathMatcher = uriPathPattern.matcher(uri.getPath());
            String uriPath = uri.getPath();
            if (uriPathMatcher.matches()) {
                uriPath = uriPathMatcher.group(1) + ".json";
            }
            URL schemaURL = bundleContext.getBundle(bundleId).getResource("META-INF/cxs/schemas" + uriPath);
            if (schemaURL != null) {
                return schemaURL.openStream();
            } else {
                logger.error("Couldn't find resource {} in bundle {}", "META-INF/cxs/schemas" + uriPath, bundleId);
                return null;
            }
        };
    }

    private void processBundleStartup(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        schemaTypesByBundle.put(bundleContext.getBundle().getBundleId(), new ArrayList<>());
        loadPredefinedSchemas(bundleContext);
    }

    private void processBundleStop(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        List<JSONSchema> JSONSchemas = schemaTypesByBundle.remove(bundleContext.getBundle().getBundleId());
        if (JSONSchemas != null) {
            for (JSONSchema JSONSchema : JSONSchemas) {
                jsonSchemasById.remove(JSONSchema.getSchemaId());
                bundleIdBySchemaId.remove(JSONSchema.getSchemaId());
                schemaTypesById.remove(JSONSchema.getSchemaId());
            }
        }
    }

    protected JsonSchema getJsonSchema(String schemaId) {
        return jsonSchemasById.get(schemaId);
    }

}
