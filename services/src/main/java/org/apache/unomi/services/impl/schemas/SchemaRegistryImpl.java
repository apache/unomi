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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.*;
import com.networknt.schema.uri.URIFetcher;
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.SchemaType;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.services.SchemaRegistry;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class SchemaRegistryImpl implements SchemaRegistry, SynchronousBundleListener {

    private static final String URI = "https://json-schema.org/draft/2019-09/schema";

    private static final Logger logger = LoggerFactory.getLogger(SchemaRegistryImpl.class.getName());

    private final Map<Long, List<SchemaType>> schemaTypesByBundle = new HashMap<>();
    private final Map<String, SchemaType> schemaTypesById = new HashMap<>();

    private final Map<String, JsonSchema> jsonSchemasById = new LinkedHashMap<>();

    private final Map<String, Long> bundleIdBySchemaId = new HashMap<>();

    private BundleContext bundleContext;

    private ProfileService profileService;

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
            try {
                // this is a workaround to get the JsonNode from the object, maybe there is a better way (more efficient) to do this ?
                StringWriter stringWriter = new StringWriter();
                CustomObjectMapper.getObjectMapper().writeValue(stringWriter, object);
                JsonNode jsonNode = CustomObjectMapper.getObjectMapper().readTree(stringWriter.toString());
                Set<ValidationMessage> validationMessages = jsonSchema.validate(jsonNode);
                if (validationMessages == null || validationMessages.isEmpty()) {
                    return true;
                }
                for (ValidationMessage validationMessage : validationMessages) {
                    logger.error("Error validating object against schema {}: {}", schemaId, validationMessage);
                }
                return false;
            } catch (IOException e) {
                logger.error("Error validating object with schema {}", schemaId, e);
            }
        }
        return false;
    }

    @Override
    public List<SchemaType> getTargetSchemas(String target) {
        return schemaTypesById.values().stream().filter(schemaType -> schemaType.getTarget().equals(target)).collect(Collectors.toList());
    }

    @Override
    public SchemaType getSchema(String schemaId) {
        return schemaTypesById.get(schemaId);
    }

    private void loadPredefinedSchemas(BundleContext bundleContext) {
        Enumeration<URL> predefinedSchemas = bundleContext.getBundle().findEntries("META-INF/cxs/schemas", "*.json", true);
        if (predefinedSchemas == null) {
            return;
        }

        ObjectMapper objectMapper = new ObjectMapper();

        List<SchemaType> schemaTypes = this.schemaTypesByBundle.get(bundleContext.getBundle().getBundleId());

        while (predefinedSchemas.hasMoreElements()) {
            URL predefinedSchemaURL = predefinedSchemas.nextElement();
            logger.debug("Found predefined JSON schema at " + predefinedSchemaURL + ", loading... ");

            try {
                JsonMetaSchema jsonMetaSchema = JsonMetaSchema.builder(URI, JsonMetaSchema.getV201909())
                        .addKeyword(new PropertyTypeKeyword(profileService, this)).build();
                JsonSchemaFactory jsonSchemaFactory = JsonSchemaFactory.builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909))
                        .addMetaSchema(jsonMetaSchema)
                        .defaultMetaSchemaURI(URI)
                        .uriFetcher(getBundleUriFetcher(bundleContext), "https", "http").build();
                InputStream schemaInputStream = predefinedSchemaURL.openStream();
                JsonSchema jsonSchema = jsonSchemaFactory.getSchema(schemaInputStream);
                String schemaId = jsonSchema.getSchemaNode().get("$id").asText();
                jsonSchemasById.put(schemaId, jsonSchema);
                bundleIdBySchemaId.put(schemaId, bundleContext.getBundle().getBundleId());
                SchemaType schemaType = new SchemaType();
                schemaType.setPluginId(bundleContext.getBundle().getBundleId());
                schemaType.setSchemaId(schemaId);
                Map<String, Object> schemaTree = (Map<String, Object>) objectMapper.treeToValue(jsonSchema.getSchemaNode(), Map.class);
                schemaType.setSchemaTree(schemaTree);
                String[] splitPath = predefinedSchemaURL.getPath().split("/");
                if (splitPath.length > 5) {
                    String target = splitPath[4];
                    if (StringUtils.isNotBlank(target)) {
                        schemaType.setTarget(target);
                    }
                }
                schemaTypes.add(schemaType);
                schemaTypesById.put(schemaId, schemaType);
                schemaInputStream.close();
            } catch (Exception e) {
                logger.error("Error while loading schema definition " + predefinedSchemaURL, e);
            }
        }

    }

    private URIFetcher getBundleUriFetcher(BundleContext bundleContext) {
        return uri -> {
            logger.debug("Fetching schema {}", uri);
            Long bundleId = bundleIdBySchemaId.get(uri.toString());
            if (bundleId == null) {
                logger.error("Couldn't find bundle for schema {}", uri);
                return null;
            }
            String uriPath = uri.getPath().substring("/schemas/json".length());
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
        List<SchemaType> schemaTypes = schemaTypesByBundle.remove(bundleContext.getBundle().getBundleId());
        if (schemaTypes != null) {
            for (SchemaType schemaType : schemaTypes) {
                jsonSchemasById.remove(schemaType.getSchemaId());
                bundleIdBySchemaId.remove(schemaType.getSchemaId());
                schemaTypesById.remove(schemaType.getSchemaId());
            }
        }
    }

    protected JsonSchema getJsonSchema(String schemaId) {
        return jsonSchemasById.get(schemaId);
    }

}
