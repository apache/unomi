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
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.security.SecurityServiceConfiguration;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.services.ScopeService;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.api.services.cache.CacheableTypeConfig;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.schema.api.JsonSchemaWrapper;
import org.apache.unomi.schema.api.SchemaService;
import org.apache.unomi.schema.api.ValidationError;
import org.apache.unomi.schema.api.ValidationException;
import org.apache.unomi.schema.keyword.ScopeKeyword;
import org.apache.unomi.tracing.api.RequestTracer;
import org.apache.unomi.tracing.api.TracerService;
import org.apache.unomi.services.common.cache.AbstractMultiTypeCachingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.tasks.ScheduledTask;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.apache.unomi.api.tenants.TenantService.SYSTEM_TENANT;

/**
 * Implementation of the SchemaService using the AbstractMultiTypeCachingService
 */
public class SchemaServiceImpl extends AbstractMultiTypeCachingService implements SchemaService {

    private static final String URI = "https://json-schema.org/draft/2019-09/schema";

    private static final Logger LOGGER = LoggerFactory.getLogger(SchemaServiceImpl.class.getName());
    private static final String TARGET_EVENTS = "events";

    private static final String GENERIC_ERROR_KEY = "error";

    ObjectMapper objectMapper = new ObjectMapper();

    /**
     * All Unomi schemas indexed by tenant ID and then by URI
     */
    private Map<String, Map<String, JsonSchemaWrapper>> schemasByTenantId = new ConcurrentHashMap<>();
    /**
     * Available extensions indexed by tenant ID, then by schema URI to be extended, then list of schema extension URIs
     */
    private Map<String, Map<String, Set<String>>> extensionsByTenant = new ConcurrentHashMap<>();

    private Integer jsonSchemaRefreshInterval = 1000;

    private ScopeService scopeService;
    private TracerService tracerService;

    // Map to store tenant-specific JsonSchemaFactory instances
    private final ConcurrentMap<String, JsonSchemaFactory> tenantJsonSchemaFactories = new ConcurrentHashMap<>();

    @Override
    protected Set<CacheableTypeConfig<?>> getTypeConfigs() {
        Set<CacheableTypeConfig<?>> configs = new HashSet<>();

        // Track extension changes per tenant for efficient processing
        ConcurrentMap<String, Boolean> tenantExtensionChanges = new ConcurrentHashMap<>();

        // JsonSchemaWrapper configuration with both tenant-specific and global callbacks
        configs.add(CacheableTypeConfig.<JsonSchemaWrapper>builder(JsonSchemaWrapper.class,
                        JsonSchemaWrapper.ITEM_TYPE,
                        "schemas")
                .withInheritFromSystemTenant(true)
                .withPredefinedItems(true)
                .withRequiresRefresh(true)
                .withRefreshInterval(jsonSchemaRefreshInterval)
                .withIdExtractor(JsonSchemaWrapper::getItemId)
                // Add stream processor for JsonSchemaWrapper
                .withStreamProcessor((bundleContext, url, inputStream) -> {
                    try {
                        // Use the same logic as loadPredefinedSchema
                        String schema = IOUtils.toString(inputStream);
                        JsonSchemaWrapper jsonSchemaWrapper = buildJsonSchemaWrapper(schema);
                        jsonSchemaWrapper.setTenantId(SYSTEM_TENANT);
                        return jsonSchemaWrapper;
                    } catch (IOException e) {
                        LOGGER.error("Error processing schema from {}", url, e);
                        return null;
                    }
                })
                .withBundleItemProcessor((bundleContext, jsonSchemaWrapper) -> {
                    contextManager.executeAsSystem(() -> {
                        persistenceService.save(jsonSchemaWrapper);
                    });
                })
                // Efficient tenant-specific processing
                .withTenantRefreshCallback((tenantId, oldTenantState, newTenantState) -> {
                    // Process tenant-specific changes efficiently
                    boolean tenantChanges = !oldTenantState.equals(newTenantState);

                    if (tenantChanges) {
                        LOGGER.debug("Schema changes detected for tenant: {}", tenantId);

                        // Track that this tenant had changes (for global callback)
                        tenantExtensionChanges.put(tenantId, true);

                        // Refresh specific tenant JsonSchemaFactory
                        tenantJsonSchemaFactories.remove(tenantId);
                        tenantJsonSchemaFactories.put(tenantId, createJsonSchemaFactory());
                    }
                })
                // Global callback for cross-tenant operations like extensions
                .withPostRefreshCallback((oldState, newState) -> {
                    // Only process global changes if any tenant had changes
                    if (!tenantExtensionChanges.isEmpty()) {
                        // Initialize extensions - this needs the complete view of all schemas
                        initExtensions(newState);

                        // Log the affected tenants
                        LOGGER.debug("Schema changes processed for tenants: {}",
                                String.join(", ", tenantExtensionChanges.keySet()));

                        // Clear the change tracker for next time
                        tenantExtensionChanges.clear();
                    }
                })
                .build());

        return configs;
    }

    @Override
    public boolean isValid(String data, String schemaId) {
        try {
            JsonNode jsonNode = parseData(data);
            JsonSchema jsonSchema = getJsonSchema(schemaId);
            return validate(jsonNode, jsonSchema).isEmpty();
        } catch (ValidationException e) {
            LOGGER.warn("{}", e.getMessage(), e);
        }
        return false;
    }

    @Override
    public boolean isEventValid(String event, String eventType) {
        return isEventValid(event);
    }

    @Override
    public boolean isEventValid(String event) {
        try {
            return validateEvent(event).isEmpty();
        } catch (ValidationException e) {
            LOGGER.warn("{}", e.getMessage(), e);
        }
        return false;
    }

    @Override
    public Set<ValidationError> validateEvent(String event) throws ValidationException {
        return validateEvents("[" + event + "]").values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    @Override
    public Map<String, Set<ValidationError>> validateEvents(String events) throws ValidationException {
        Map<String, Set<ValidationError>> errorsPerEventType = new HashMap<>();
        JsonNode eventsNodes = parseData(events);
        eventsNodes.forEach(event -> {
            String eventType = null;
            try {
                eventType = extractEventType(event);
                JsonSchemaWrapper eventSchema = getSchemaForEventType(eventType);
                JsonSchema jsonSchema = getJsonSchema(eventSchema.getItemId());

                Set<ValidationError> errors = validate(event, jsonSchema);
                if (!errors.isEmpty()) {
                    if (errorsPerEventType.containsKey(eventType)) {
                        errorsPerEventType.get(eventType).addAll(errors);
                    } else {
                        errorsPerEventType.put(eventType, errors);
                    }
                }
            } catch (ValidationException e) {
                LOGGER.warn("An error occurred during the validation of your event - switch to DEBUG log level for more information.");
                LOGGER.debug("Validation error : {}", e.getMessage());
                Set<ValidationError> errors = buildCustomErrorMessage(e.getMessage());
                String eventTypeOrErrorKey = eventType != null ? eventType : GENERIC_ERROR_KEY;
                if (errorsPerEventType.containsKey(eventTypeOrErrorKey)) {
                    errorsPerEventType.get(eventTypeOrErrorKey).addAll(errors);
                } else {
                    errorsPerEventType.put(eventTypeOrErrorKey, errors);
                }
            }
        });
        return errorsPerEventType;
    }

    private Set<ValidationError> buildCustomErrorMessage(String errorMessage) {
        ValidationError error = new ValidationError(errorMessage);
        Set<ValidationError> errors = new HashSet<>();
        errors.add(error);
        return errors;
    }

    @Override
    public JsonSchemaWrapper getSchema(String schemaId) {
        // Try current tenant first
        String currentTenant = contextManager.getCurrentContext().getTenantId();
        if (currentTenant != null) {
            Map<String, JsonSchemaWrapper> tenantSchemas = schemasByTenantId.get(currentTenant);
            if (tenantSchemas != null) {
                JsonSchemaWrapper schema = tenantSchemas.get(schemaId);
                if (schema != null) {
                    return schema;
                }
            }
        }

        // If not found and not in system tenant, try system tenant
        if (!SYSTEM_TENANT.equals(currentTenant)) {
            Map<String, JsonSchemaWrapper> systemTenantSchemas = schemasByTenantId.get(SYSTEM_TENANT);
            if (systemTenantSchemas != null) {
                return systemTenantSchemas.get(schemaId);
            }
        }

        return null;
    }

    @Override
    public Set<String> getInstalledJsonSchemaIds() {
        Set<String> schemaIds = new HashSet<>();

        // Add current tenant schemas
        String currentTenant = contextManager.getCurrentContext().getTenantId();
        if (currentTenant != null) {
            Map<String, JsonSchemaWrapper> tenantSchemas = schemasByTenantId.get(currentTenant);
            if (tenantSchemas != null) {
                schemaIds.addAll(tenantSchemas.keySet());
            }
        }

        // Add system tenant schemas if not already in system tenant
        if (!SYSTEM_TENANT.equals(currentTenant)) {
            Map<String, JsonSchemaWrapper> systemSchemas = schemasByTenantId.get(SYSTEM_TENANT);
            if (systemSchemas != null) {
                schemaIds.addAll(systemSchemas.keySet());
            }
        }

        return schemaIds;
    }

    @Override
    public List<JsonSchemaWrapper> getSchemasByTarget(String target) {
        List<JsonSchemaWrapper> results = new ArrayList<>();

        // Get current tenant ID
        String currentTenant = contextManager.getCurrentContext().getTenantId();

        // First get schemas from current tenant
        if (currentTenant != null) {
            Map<String, JsonSchemaWrapper> tenantSchemas = schemasByTenantId.get(currentTenant);
            if (tenantSchemas != null) {
                List<JsonSchemaWrapper> currentTenantSchemas = tenantSchemas.values().stream()
                        .filter(jsonSchemaWrapper ->
                                jsonSchemaWrapper.getTarget() != null &&
                                jsonSchemaWrapper.getTarget().equals(target))
                        .collect(Collectors.toList());
                results.addAll(currentTenantSchemas);
            }
        }

        // If not in system tenant, also include system tenant schemas
        if (!SYSTEM_TENANT.equals(currentTenant)) {
            Map<String, JsonSchemaWrapper> systemSchemas = schemasByTenantId.get(SYSTEM_TENANT);
            if (systemSchemas != null) {
                List<JsonSchemaWrapper> systemTenantSchemas = systemSchemas.values().stream()
                        .filter(jsonSchemaWrapper ->
                                jsonSchemaWrapper.getTarget() != null &&
                                jsonSchemaWrapper.getTarget().equals(target))
                        .collect(Collectors.toList());
                results.addAll(systemTenantSchemas);
            }
        }

        return results;
    }

    @Override
    public JsonSchemaWrapper getSchemaForEventType(String eventType) throws ValidationException {
        if (StringUtils.isEmpty(eventType)) {
            throw new ValidationException("eventType missing");
        }

        // Get current tenant ID
        String currentTenant = contextManager.getCurrentContext().getTenantId();

        // First check in current tenant
        if (currentTenant != null) {
            Map<String, JsonSchemaWrapper> tenantSchemas = schemasByTenantId.get(currentTenant);
            if (tenantSchemas != null) {
                Optional<JsonSchemaWrapper> schema = tenantSchemas.values().stream()
                        .filter(jsonSchemaWrapper ->
                                jsonSchemaWrapper.getTarget() != null &&
                                        jsonSchemaWrapper.getTarget().equals(TARGET_EVENTS) &&
                                        jsonSchemaWrapper.getName() != null &&
                                        jsonSchemaWrapper.getName().equals(eventType))
                        .findFirst();

                if (schema.isPresent()) {
                    return schema.get();
                }
            }
        }

        // If not found and not in system tenant, try system tenant
        if (!SYSTEM_TENANT.equals(currentTenant)) {
            Map<String, JsonSchemaWrapper> systemTenantSchemas = schemasByTenantId.get(SYSTEM_TENANT);
            if (systemTenantSchemas != null) {
                Optional<JsonSchemaWrapper> schema = systemTenantSchemas.values().stream()
                        .filter(jsonSchemaWrapper ->
                                jsonSchemaWrapper.getTarget() != null &&
                                        jsonSchemaWrapper.getTarget().equals(TARGET_EVENTS) &&
                                        jsonSchemaWrapper.getName() != null &&
                                        jsonSchemaWrapper.getName().equals(eventType))
                        .findFirst();

                if (schema.isPresent()) {
                    return schema.get();
                }
            }
        }

        throw new ValidationException("Schema not found for event type: " + eventType);
    }

    @Override
    public void saveSchema(String schema) {
        contextManager.getCurrentContext().validateAccess(SecurityServiceConfiguration.PERMISSION_SCHEMA_WRITE);
        JsonSchemaWrapper jsonSchemaWrapper = buildJsonSchemaWrapper(schema);
        String currentTenant = contextManager.getCurrentContext().getTenantId();
        jsonSchemaWrapper.setTenantId(currentTenant);
        persistenceService.save(jsonSchemaWrapper);
    }

    @Override
    public boolean deleteSchema(String schemaId) {
        contextManager.getCurrentContext().validateAccess(SecurityServiceConfiguration.PERMISSION_SCHEMA_DELETE);
        final String tenantId = contextManager.getCurrentContext().getTenantId();
        // remove persisted schema
        if (schemasByTenantId.containsKey(tenantId)) {
            schemasByTenantId.get(tenantId).remove(schemaId);
        }
        return persistenceService.remove(schemaId, JsonSchemaWrapper.class);
    }

    private Set<ValidationError> validate(JsonNode jsonNode, JsonSchema jsonSchema) throws ValidationException {
        try {
            Set<ValidationMessage> validationMessages = jsonSchema.validate(jsonNode);

            if (!validationMessages.isEmpty()) {
                LOGGER.warn("Schema validation found {} errors while validating against schema: {}", validationMessages.size(), jsonSchema.getCurrentUri());
                for (ValidationMessage validationMessage : validationMessages) {
                    LOGGER.warn("Validation error: {}", validationMessage);
                }
            }

            // Add validation info to trace if tracing is enabled
            if (tracerService != null) {
                RequestTracer tracer = tracerService.getCurrentTracer();
                if (tracer != null && tracer.isEnabled()) {
                    tracer.addValidationInfo(validationMessages, jsonSchema.getCurrentUri().toString());
                }
            }

            return validationMessages != null ?
                    validationMessages.stream()
                            .map(validationMessage -> new ValidationError(validationMessage.getMessage()))
                            .collect(Collectors.toSet()) :
                    Collections.emptySet();
        } catch (Exception e) {
            LOGGER.debug("Unexpected error while validating schema :", e);
            throw new ValidationException("Unexpected error while validating", e);
        }
    }

    private JsonNode parseData(String data) throws ValidationException {
        if (StringUtils.isEmpty(data)) {
            throw new ValidationException("Empty data, nothing to validate");
        }
        try {
            return objectMapper.readTree(data);
        } catch (Exception e) {
            throw new ValidationException("Unexpected error while parsing the data to validate", e);
        }
    }

    private JsonSchema getJsonSchema(String schemaId) throws ValidationException {
        try {
            // Get current tenant ID
            String currentTenant = contextManager.getCurrentContext().getTenantId();
            // Get or create JsonSchemaFactory for this tenant
            JsonSchemaFactory factory = tenantJsonSchemaFactories.computeIfAbsent(currentTenant,
                k -> createJsonSchemaFactory());

            JsonSchema jsonSchema = factory.getSchema(new URI(schemaId));
            if (jsonSchema != null) {
                return jsonSchema;
            } else {
                throw new ValidationException("Json schema not found: " + schemaId);
            }
        } catch (Exception e) {
            throw new ValidationException("Unexpected error while loading json schema: " + schemaId, e);
        }
    }

    private String extractEventType(JsonNode jsonEvent) throws ValidationException {
        if (jsonEvent.hasNonNull("eventType")) {
            String eventType = jsonEvent.get("eventType").textValue();
            if (StringUtils.isNotBlank(eventType)) {
                return eventType;
            }
        }
        throw new ValidationException("eventType property is either missing/empty/invalid in event source");
    }

    private JsonSchemaWrapper buildJsonSchemaWrapper(String schema) {
        // Get current tenant ID and its factory
        String currentTenant = contextManager.getCurrentContext().getTenantId();
        JsonSchemaFactory factory = tenantJsonSchemaFactories.computeIfAbsent(currentTenant,
            k -> createJsonSchemaFactory());

        JsonSchema jsonSchema = factory.getSchema(schema);
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

    public void refreshJSONSchemas() {
        Map<String, Map<String, JsonSchemaWrapper>> schemasByTenantIdReloaded = new HashMap<>();

        // Get all tenants using system context
        contextManager.executeAsSystem(() -> {

            ConcurrentMap<String, JsonSchemaWrapper> systemSchemas = new ConcurrentHashMap<>();
            // First get system tenant schemas
            contextManager.executeAsTenant(SYSTEM_TENANT, () -> {
                List<JsonSchemaWrapper> systemTenantSchemas = persistenceService.getAllItems(JsonSchemaWrapper.class);
                systemSchemas.putAll(systemTenantSchemas.stream()
                        .collect(Collectors.toMap(Item::getItemId, s -> s)));
                schemasByTenantIdReloaded.put(SYSTEM_TENANT, systemSchemas);
            });

            // Get all tenants using TenantService (which implicitly uses system tenant)
            List<Tenant> allTenants = tenantService.getAllTenants();

            // Then process each tenant's schemas with its own context
            for (Tenant tenant : allTenants) {
                String tenantId = tenant.getItemId();
                contextManager.executeAsTenant(tenantId, () -> {
                    List<JsonSchemaWrapper> tenantSchemas = persistenceService.getAllItems(JsonSchemaWrapper.class);
                    if (!tenantSchemas.isEmpty()) {
                        Map<String, JsonSchemaWrapper> tenantSchemasMap = schemasByTenantIdReloaded.computeIfAbsent(tenantId,  k -> new ConcurrentHashMap<>());
                        Map<String, JsonSchemaWrapper> additionalTenantSchemasMap = new ConcurrentHashMap<>(
                                tenantSchemas.stream().collect(Collectors.toMap(Item::getItemId, s -> s)));
                        tenantSchemasMap.putAll(additionalTenantSchemasMap);
                        schemasByTenantIdReloaded.put(tenantId, tenantSchemasMap);
                    }
                });
            }

            // Check for changes
            boolean changes = !schemasByTenantIdReloaded.equals(schemasByTenantId);

            if (changes) {
                schemasByTenantId = new ConcurrentHashMap<>(schemasByTenantIdReloaded);
                initExtensions(schemasByTenantIdReloaded);

                // Reset and recreate all JsonSchemaFactory instances
                tenantJsonSchemaFactories.clear();
                initJsonSchemaFactory();
            }
        });
    }

    private void initExtensions(Map<String, Map<String, JsonSchemaWrapper>> schemas) {
        Map<String, Map<String, Set<String>>> extensionsByTenantReloaded = new HashMap<>();

        // Process extensions for each tenant
        for (Map.Entry<String, Map<String, JsonSchemaWrapper>> tenantEntry : schemas.entrySet()) {
            String tenantId = tenantEntry.getKey();

            // Find schema extensions in this tenant
            List<JsonSchemaWrapper> schemaExtensions = tenantEntry.getValue().values().stream()
                    .filter(jsonSchemaWrapper -> StringUtils.isNotBlank(jsonSchemaWrapper.getExtendsSchemaId()))
                    .collect(Collectors.toList());

            // Process extensions for this tenant
            if (!schemaExtensions.isEmpty()) {
                ConcurrentMap<String, Set<String>> tenantExtensions = new ConcurrentHashMap<>();

                for (JsonSchemaWrapper extension : schemaExtensions) {
                    String extendedSchemaId = extension.getExtendsSchemaId();
                    if (!extension.getItemId().equals(extendedSchemaId)) {
                        tenantExtensions.computeIfAbsent(extendedSchemaId, k -> new HashSet<>())
                                .add(extension.getItemId());
                    } else {
                        LOGGER.warn("A schema cannot extends himself, please fix your schema definition for schema: {}", extendedSchemaId);
                    }
                }

                if (!tenantExtensions.isEmpty()) {
                    extensionsByTenantReloaded.put(tenantId, tenantExtensions);
                }
            }
        }

        extensionsByTenant = new ConcurrentHashMap<>(extensionsByTenantReloaded);
    }

    private String generateExtendedSchema(String id, String schema) throws JsonProcessingException {
        // Get current tenant ID
        String currentTenant = contextManager.getCurrentContext().getTenantId();

        // First look for extensions in current tenant
        Set<String> extensionIds = new HashSet<>();
        if (currentTenant != null) {
            Map<String, Set<String>> tenantExtensions = extensionsByTenant.get(currentTenant);
            if (tenantExtensions != null && tenantExtensions.containsKey(id)) {
                extensionIds.addAll(tenantExtensions.get(id));
            }
        }

        // If not in system tenant, also look for extensions in system tenant
        if (!SYSTEM_TENANT.equals(currentTenant)) {
            Map<String, Set<String>> systemExtensions = extensionsByTenant.get(SYSTEM_TENANT);
            if (systemExtensions != null && systemExtensions.containsKey(id)) {
                extensionIds.addAll(systemExtensions.get(id));
            }
        }

        // Process all found extensions
        if (!extensionIds.isEmpty()) {
            // This schema needs to be extended!
            ObjectNode jsonSchema = (ObjectNode) objectMapper.readTree(schema);
            ArrayNode allOf;
            if (jsonSchema.at("/allOf") instanceof MissingNode) {
                allOf = objectMapper.createArrayNode();
            } else if (jsonSchema.at("/allOf") instanceof ArrayNode) {
                allOf = (ArrayNode) jsonSchema.at("/allOf");
            } else {
                LOGGER.warn("Cannot extends schema allOf property, it should be an Array, please fix your schema definition for schema: {}", id);
                return schema;
            }

            // Add each extension URI as new ref in the allOf
            for (String extensionId : extensionIds) {
                ObjectNode newAllOf = objectMapper.createObjectNode();
                newAllOf.put("$ref", extensionId);
                allOf.add(newAllOf);
            }

            // Generate new extended schema as String
            jsonSchema.putArray("allOf").addAll(allOf);
            return objectMapper.writeValueAsString(jsonSchema);
        }
        return schema;
    }

    private void initJsonSchemaFactory() {
        // Get all tenants
        Set<String> tenants = new HashSet<>();
        tenantService.getAllTenants().forEach(tenant -> tenants.add(tenant.getItemId()));
        tenants.add(SYSTEM_TENANT);

        // Create JsonSchemaFactory for each tenant
        for (String tenantId : tenants) {
            tenantJsonSchemaFactories.put(tenantId, createJsonSchemaFactory());
        }
    }

    private JsonSchemaFactory createJsonSchemaFactory() {
        return JsonSchemaFactory.builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909))
                .addMetaSchema(JsonMetaSchema.builder(URI, JsonMetaSchema.getV201909())
                        .addKeyword(new ScopeKeyword(scopeService))
                        .addKeyword(new NonValidationKeyword("self"))
                        .build())
                .defaultMetaSchemaURI(URI)
                .uriFetcher(uri -> {
                    LOGGER.debug("Fetching schema {}", uri);
                    String schemaId = uri.toString();
                    JsonSchemaWrapper jsonSchemaWrapper = getSchema(schemaId);
                    if (jsonSchemaWrapper == null) {
                        LOGGER.error("Couldn't find schema {}", uri);
                        return null;
                    }

                    String schema = jsonSchemaWrapper.getSchema();
                    // Check if schema need to be extended
                    schema = generateExtendedSchema(schemaId, schema);

                    return IOUtils.toInputStream(schema);
                }, "https", "http")
                .build();
    }

    public void postConstruct() {
        super.postConstruct();
        initJsonSchemaFactory();
        LOGGER.info("Schema service initialized.");
    }

    public void preDestroy() {
        super.preDestroy();
        LOGGER.info("Schema service shutdown.");
    }

    public void setScopeService(ScopeService scopeService) {
        this.scopeService = scopeService;
    }


    public void setJsonSchemaRefreshInterval(Integer jsonSchemaRefreshInterval) {
        this.jsonSchemaRefreshInterval = jsonSchemaRefreshInterval;
    }

    public void setTracerService(TracerService tracerService) {
        this.tracerService = tracerService;
    }

}
