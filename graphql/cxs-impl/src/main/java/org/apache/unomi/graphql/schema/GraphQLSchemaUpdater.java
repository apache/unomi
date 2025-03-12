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
package org.apache.unomi.graphql.schema;

import graphql.GraphQL;
import graphql.execution.SubscriptionExecutionStrategy;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLSchema;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.graphql.fetchers.event.UnomiEventPublisher;
import org.apache.unomi.graphql.providers.*;
import org.apache.unomi.graphql.types.output.*;
import org.apache.unomi.schema.api.SchemaService;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;

@Component(service = GraphQLSchemaUpdater.class)
public class GraphQLSchemaUpdater {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphQLSchemaUpdater.class);

    public @interface SchemaConfig {

        int schema_update_delay() default 0;

    }

    private final List<GraphQLQueryProvider> queryProviders = new CopyOnWriteArrayList<>();

    private final List<GraphQLMutationProvider> mutationProviders = new CopyOnWriteArrayList<>();

    private final List<GraphQLSubscriptionProvider> subscriptionProviders = new CopyOnWriteArrayList<>();

    private final List<GraphQLExtensionsProvider> extensionsProviders = new CopyOnWriteArrayList<>();

    private final List<GraphQLAdditionalTypesProvider> additionalTypesProviders = new CopyOnWriteArrayList<>();

    private final List<GraphQLTypeFunctionProvider> typeFunctionProviders = new CopyOnWriteArrayList<>();

    private List<GraphQLFieldVisibilityProvider> fieldVisibilityProviders = new CopyOnWriteArrayList<>();

    private GraphQLCodeRegistryProvider codeRegistryProvider;

    private UnomiEventPublisher eventPublisher;

    private GraphQL graphQL;

    private ProfileService profileService;

    private SchemaService schemaService;

    private CDPEventInterfaceRegister eventInterfaceRegister;

    private CDPProfileInterfaceRegister profilesInterfaceRegister;

    private CDPPropertyInterfaceRegister propertyInterfaceRegister;

    private ExecutionContextManager contextManager;

    private ScheduledExecutorService executorService;

    private ScheduledFuture<?> updateFuture;

    private boolean isActivated;

    private int schemaUpdateDelay;

    // Add tenant schema cache
    private final ConcurrentMap<String, GraphQL> tenantSchemas = new ConcurrentHashMap<>();

    @Activate
    public void activate(final SchemaConfig config) {
        this.isActivated = true;
        this.schemaUpdateDelay = config.schema_update_delay();

        if (config.schema_update_delay() != 0) {
            this.executorService = Executors.newSingleThreadScheduledExecutor();
        }

        updateSchema();
    }

    @Deactivate
    public void deactivate() {
        this.isActivated = false;

        if (executorService != null) {
            executorService.shutdown();
        }
    }

    @Reference
    public void setEventPublisher(UnomiEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Reference
    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Reference
    public void setSchemaService(SchemaService schemaService) {
        this.schemaService = schemaService;
    }

    @Reference
    public void setEventInterfaceRegister(CDPEventInterfaceRegister eventInterfaceRegister) {
        this.eventInterfaceRegister = eventInterfaceRegister;
    }

    @Reference
    public void setProfilesInterfaceRegister(CDPProfileInterfaceRegister profilesInterfaceRegister) {
        this.profilesInterfaceRegister = profilesInterfaceRegister;
    }

    @Reference
    public void setPropertiesInterfaceRegister(CDPPropertyInterfaceRegister propertyInterfaceRegister) {
        this.propertyInterfaceRegister = propertyInterfaceRegister;
    }

    @Reference
    public void setContextManager(ExecutionContextManager contextManager) {
        this.contextManager = contextManager;
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    public void bindProvider(GraphQLProvider provider) {
        if (provider instanceof GraphQLQueryProvider) {
            queryProviders.add((GraphQLQueryProvider) provider);
        }
        if (provider instanceof GraphQLMutationProvider) {
            mutationProviders.add((GraphQLMutationProvider) provider);
        }
        if (provider instanceof GraphQLSubscriptionProvider) {
            subscriptionProviders.add((GraphQLSubscriptionProvider) provider);
        }
        if (provider instanceof GraphQLAdditionalTypesProvider) {
            additionalTypesProviders.add((GraphQLAdditionalTypesProvider) provider);
        }
        if (provider instanceof GraphQLFieldVisibilityProvider) {
            fieldVisibilityProviders.add((GraphQLFieldVisibilityProvider) provider);
        }
        if (provider instanceof GraphQLCodeRegistryProvider) {
            codeRegistryProvider = (GraphQLCodeRegistryProvider) provider;
        }
        updateSchema();
    }

    public void unbindProvider(GraphQLProvider provider) {
        if (provider instanceof GraphQLQueryProvider) {
            queryProviders.remove(provider);
        }
        if (provider instanceof GraphQLMutationProvider) {
            mutationProviders.remove(provider);
        }
        if (provider instanceof GraphQLSubscriptionProvider) {
            subscriptionProviders.remove(provider);
        }
        if (provider instanceof GraphQLAdditionalTypesProvider) {
            additionalTypesProviders.remove(provider);
        }
        if (provider instanceof GraphQLFieldVisibilityProvider) {
            fieldVisibilityProviders.remove(provider);
        }
        if (provider instanceof GraphQLCodeRegistryProvider) {
            codeRegistryProvider = GraphQLCodeRegistry::newCodeRegistry;
        }
        updateSchema();
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    public void bindQueryProvider(GraphQLQueryProvider provider) {
        queryProviders.add(provider);

        updateSchema();
    }

    public void unbindQueryProvider(GraphQLQueryProvider provider) {
        queryProviders.remove(provider);

        updateSchema();
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    public void bindMutationProvider(GraphQLMutationProvider provider) {
        mutationProviders.add(provider);

        updateSchema();
    }

    public void unbindMutationProvider(GraphQLMutationProvider provider) {
        mutationProviders.remove(provider);

        updateSchema();
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    public void bindSubscriptionProvider(GraphQLSubscriptionProvider provider) {
        subscriptionProviders.add(provider);

        updateSchema();
    }

    public void unbindSubscriptionProvider(GraphQLSubscriptionProvider provider) {
        subscriptionProviders.remove(provider);

        updateSchema();
    }


    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    public void bindFieldVisibilityProvider(GraphQLFieldVisibilityProvider provider) {
        fieldVisibilityProviders.add(provider);

        updateSchema();
    }

    public void unbindFieldVisibilityProvider(GraphQLFieldVisibilityProvider provider) {
        fieldVisibilityProviders.remove(provider);

        updateSchema();
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    public void bindExtensionsProvider(GraphQLExtensionsProvider provider) {
        extensionsProviders.add(provider);

        updateSchema();
    }

    public void unbindExtensionsProvider(GraphQLExtensionsProvider provider) {
        extensionsProviders.remove(provider);

        updateSchema();
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    public void bindAdditionalTypes(GraphQLAdditionalTypesProvider provider) {
        additionalTypesProviders.add(provider);

        updateSchema();
    }

    public void unbindAdditionalTypes(GraphQLAdditionalTypesProvider provider) {
        additionalTypesProviders.remove(provider);

        updateSchema();
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    public void bindTypeFunctionProviders(GraphQLTypeFunctionProvider typeFunctionProvider) {
        this.typeFunctionProviders.add(typeFunctionProvider);

        updateSchema();
    }

    public void unbindTypeFunctionProviders(GraphQLTypeFunctionProvider typeFunctionProvider) {
        typeFunctionProviders.remove(typeFunctionProvider);

        updateSchema();
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY)
    public void bindCodeRegistryProvider(GraphQLCodeRegistryProvider codeRegistryProvider) {
        this.codeRegistryProvider = codeRegistryProvider;

        updateSchema();
    }

    public void unbindCodeRegistryProvider(GraphQLCodeRegistryProvider codeRegistryProvider) {
        this.codeRegistryProvider = null;

        updateSchema();
    }

    public void updateSchema() {
        if (!isActivated) {
            return;
        }

        if (schemaUpdateDelay == 0) {
            doUpdateSchema();
        } else {
            if (updateFuture != null) {
                updateFuture.cancel(true);
            }

            updateFuture = executorService.scheduleWithFixedDelay(this::doUpdateSchema, 0, schemaUpdateDelay, TimeUnit.SECONDS);
        }
    }

    private void doUpdateSchema() {
        try {
            // Update the default system schema
            contextManager.executeAsSystem(() -> {
                final GraphQLSchema graphQLSchema = createGraphQLSchema();

                this.graphQL = GraphQL.newGraphQL(graphQLSchema)
                        .subscriptionExecutionStrategy(new SubscriptionExecutionStrategy())
                        .build();
                return null;
            });
            
            // Clear tenant schemas cache to force recreation on next request
            tenantSchemas.clear();
        } catch (Exception e) {
            LOGGER.error("Error executing GraphQL schema update as system subject", e);
        }
    }

    /**
     * Get the GraphQL instance for a specific tenant
     * @param tenantId The tenant ID
     * @return GraphQL instance configured for the tenant
     */
    public GraphQL getGraphQLForTenant(String tenantId) {
        if (tenantId == null) {
            // Fall back to system schema for null tenant
            return getGraphQL();
        }
        
        return tenantSchemas.computeIfAbsent(tenantId, this::createGraphQLForTenant);
    }
    
    /**
     * Create a tenant-specific GraphQL instance
     * @param tenantId The tenant ID
     * @return GraphQL instance for the tenant
     */
    private GraphQL createGraphQLForTenant(String tenantId) {
        try {
            return contextManager.executeAsTenant(tenantId, () -> {
                LOGGER.info("Creating GraphQL schema for tenant: {}", tenantId);
                final GraphQLSchema graphQLSchema = createGraphQLSchemaForTenant(tenantId);
                return GraphQL.newGraphQL(graphQLSchema)
                        .subscriptionExecutionStrategy(new SubscriptionExecutionStrategy())
                        .build();
            });
        } catch (Exception e) {
            LOGGER.error("Error creating GraphQL schema for tenant: " + tenantId, e);
            // Fall back to system schema if tenant schema creation fails
            return getGraphQL();
        }
    }
    
    /**
     * Invalidate the schema for a specific tenant
     * @param tenantId The tenant ID to invalidate
     */
    public void invalidateTenantSchema(String tenantId) {
        if (tenantId != null) {
            tenantSchemas.remove(tenantId);
            LOGGER.debug("Invalidated GraphQL schema for tenant: {}", tenantId);
        }
    }

    /**
     * Get the default GraphQL instance (system tenant)
     */
    public GraphQL getGraphQL() {
        return graphQL;
    }

    @SuppressWarnings("unchecked")
    private GraphQLSchema createGraphQLSchema() {
        final GraphQLSchemaProvider schemaProvider = GraphQLSchemaProvider.create(profileService, schemaService)
                .typeFunctionProviders(typeFunctionProviders)
                .extensionsProviders(extensionsProviders)
                .additionalTypesProviders(additionalTypesProviders)
                .queryProviders(queryProviders)
                .mutationProviders(mutationProviders)
                .subscriptionProviders(subscriptionProviders)
                .eventPublisher(eventPublisher)
                .codeRegistryProvider(codeRegistryProvider)
                .fieldVisibilityProviders(fieldVisibilityProviders)
                .build();

        final GraphQLSchema schema = schemaProvider.createSchema();

        registerInterfaces(schemaProvider);

        return schema;
    }
    
    /**
     * Create a tenant-specific GraphQL schema
     * @param tenantId The tenant ID
     * @return GraphQL schema for the tenant
     */
    @SuppressWarnings("unchecked")
    private GraphQLSchema createGraphQLSchemaForTenant(String tenantId) {
        final GraphQLSchemaProvider schemaProvider = GraphQLSchemaProvider.create(profileService, schemaService)
                .typeFunctionProviders(typeFunctionProviders)
                .extensionsProviders(extensionsProviders)
                .additionalTypesProviders(additionalTypesProviders)
                .queryProviders(queryProviders)
                .mutationProviders(mutationProviders)
                .subscriptionProviders(subscriptionProviders)
                .eventPublisher(eventPublisher)
                .codeRegistryProvider(codeRegistryProvider)
                .fieldVisibilityProviders(fieldVisibilityProviders)
                .tenantId(tenantId)  // Pass tenant ID to schema provider
                .build();

        final GraphQLSchema schema = schemaProvider.createSchemaForTenant(tenantId);

        registerInterfaces(schemaProvider);

        return schema;
    }
    
    /**
     * Register interfaces for the schema provider
     */
    private void registerInterfaces(GraphQLSchemaProvider schemaProvider) {
        profilesInterfaceRegister.register(CDPProfile.class);
        profilesInterfaceRegister.register(CDPPersona.class);

        if (schemaProvider.getAdditionalTypes() != null && !schemaProvider.getAdditionalTypes().isEmpty()) {
            schemaProvider.getAdditionalTypes().forEach(additionalType -> {
                if (CDPEventInterface.class.isAssignableFrom(additionalType)) {
                    eventInterfaceRegister.register((Class<? extends CDPEventInterface>) additionalType);
                }

                if (CDPProfileInterface.class.isAssignableFrom(additionalType)) {
                    profilesInterfaceRegister.register((Class<? extends CDPProfileInterface>) additionalType);
                }

                if (CDPPropertyInterface.class.isAssignableFrom(additionalType)) {
                    propertyInterfaceRegister.register((Class<? extends CDPPropertyInterface>) additionalType);
                }
            });
        }
    }

}
