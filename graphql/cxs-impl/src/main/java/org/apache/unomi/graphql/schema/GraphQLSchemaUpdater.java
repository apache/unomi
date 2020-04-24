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
import graphql.schema.GraphQLSchema;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.graphql.providers.GraphQLAdditionalTypesProvider;
import org.apache.unomi.graphql.providers.GraphQLCodeRegistryProvider;
import org.apache.unomi.graphql.providers.GraphQLExtensionsProvider;
import org.apache.unomi.graphql.providers.GraphQLMutationProvider;
import org.apache.unomi.graphql.providers.GraphQLQueryProvider;
import org.apache.unomi.graphql.providers.GraphQLTypeFunctionProvider;
import org.apache.unomi.graphql.types.output.CDPEventInterface;
import org.apache.unomi.graphql.types.output.CDPPersona;
import org.apache.unomi.graphql.types.output.CDPProfile;
import org.apache.unomi.graphql.types.output.CDPProfileInterface;
import org.apache.unomi.graphql.types.output.CDPPropertyInterface;
import org.apache.unomi.graphql.types.output.property.CDPBooleanPropertyType;
import org.apache.unomi.graphql.types.output.property.CDPDatePropertyType;
import org.apache.unomi.graphql.types.output.property.CDPFloatPropertyType;
import org.apache.unomi.graphql.types.output.property.CDPGeoPointPropertyType;
import org.apache.unomi.graphql.types.output.property.CDPIdentifierPropertyType;
import org.apache.unomi.graphql.types.output.property.CDPIntPropertyType;
import org.apache.unomi.graphql.types.output.property.CDPSetPropertyType;
import org.apache.unomi.graphql.types.output.property.CDPStringPropertyType;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component(service = GraphQLSchemaUpdater.class)
public class GraphQLSchemaUpdater {

    public @interface SchemaConfig {

        int schema_update_delay() default 0;

    }

    private final List<GraphQLQueryProvider> queryProviders = new CopyOnWriteArrayList<>();

    private final List<GraphQLMutationProvider> mutationProviders = new CopyOnWriteArrayList<>();

    private final List<GraphQLExtensionsProvider> extensionsProviders = new CopyOnWriteArrayList<>();

    private final List<GraphQLAdditionalTypesProvider> additionalTypesProviders = new CopyOnWriteArrayList<>();

    private final List<GraphQLTypeFunctionProvider> typeFunctionProviders = new CopyOnWriteArrayList<>();

    private GraphQLCodeRegistryProvider codeRegistryProvider;

    private GraphQL graphQL;

    private ProfileService profileService;

    private CDPEventInterfaceRegister eventInterfaceRegister;

    private CDPProfileInterfaceRegister profilesInterfaceRegister;

    private CDPPropertyInterfaceRegister propertyInterfaceRegister;

    private ScheduledExecutorService executorService;

    private ScheduledFuture<?> updateFuture;

    private boolean isActivated;

    private int schemaUpdateDelay;

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
    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
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
        final GraphQLSchema graphQLSchema = createGraphQLSchema();

        this.graphQL = GraphQL.newGraphQL(graphQLSchema).build();
    }

    public GraphQL getGraphQL() {
        return graphQL;
    }

    @SuppressWarnings("unchecked")
    private GraphQLSchema createGraphQLSchema() {
        final GraphQLSchemaProvider schemaProvider = GraphQLSchemaProvider.create(profileService)
                .typeFunctionProviders(typeFunctionProviders)
                .extensionsProviders(extensionsProviders)
                .additionalTypesProviders(additionalTypesProviders)
                .queryProviders(queryProviders)
                .mutationProviders(mutationProviders)
                .codeRegistryProvider(codeRegistryProvider).build();

        final GraphQLSchema schema = schemaProvider.createSchema();

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

        return schema;
    }

}
