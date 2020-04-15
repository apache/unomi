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

import graphql.Scalars;
import graphql.annotations.AnnotationsSchemaCreator;
import graphql.annotations.processor.GraphQLAnnotations;
import graphql.annotations.processor.ProcessingElementsContainer;
import graphql.language.InputObjectTypeDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.graphql.CDPGraphQLConstants;
import org.apache.unomi.graphql.fetchers.CustomerPropertyDataFetcher;
import org.apache.unomi.graphql.fetchers.DynamicFieldDataFetcher;
import org.apache.unomi.graphql.function.DateTimeFunction;
import org.apache.unomi.graphql.providers.GraphQLAdditionalTypesProvider;
import org.apache.unomi.graphql.providers.GraphQLCodeRegistryProvider;
import org.apache.unomi.graphql.providers.GraphQLExtensionsProvider;
import org.apache.unomi.graphql.providers.GraphQLMutationProvider;
import org.apache.unomi.graphql.providers.GraphQLProcessEventsProvider;
import org.apache.unomi.graphql.providers.GraphQLQueryProvider;
import org.apache.unomi.graphql.providers.GraphQLTypeFunctionProvider;
import org.apache.unomi.graphql.types.RootMutation;
import org.apache.unomi.graphql.types.RootQuery;
import org.apache.unomi.graphql.types.input.CDPEventInput;
import org.apache.unomi.graphql.types.input.CDPPersonaInput;
import org.apache.unomi.graphql.types.input.CDPProfilePropertiesFilterInput;
import org.apache.unomi.graphql.types.input.CDPProfileUpdateEventInput;
import org.apache.unomi.graphql.types.output.CDPPersona;
import org.apache.unomi.graphql.types.output.CDPProfile;
import org.apache.unomi.graphql.utils.ReflectionUtil;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GraphQLSchemaProvider {

    private final ProfileService profileService;

    private final List<GraphQLTypeFunctionProvider> typeFunctionProviders;

    private final List<GraphQLExtensionsProvider> extensionsProviders;

    private final List<GraphQLAdditionalTypesProvider> additionalTypesProviders;

    private final List<GraphQLQueryProvider> queryProviders;

    private final List<GraphQLMutationProvider> mutationProviders;

    private final List<GraphQLProcessEventsProvider> eventsProviders;

    private final GraphQLCodeRegistryProvider codeRegistryProvider;

    private GraphQLAnnotations graphQLAnnotations;

    private Set<Class<?>> additionalTypes = new HashSet<>();

    private GraphQLSchemaProvider(final Builder builder) {
        this.profileService = builder.profileService;

        this.typeFunctionProviders = builder.typeFunctionProviders;
        this.extensionsProviders = builder.extensionsProviders;
        this.additionalTypesProviders = builder.additionalTypesProviders;
        this.queryProviders = builder.queryProviders;
        this.mutationProviders = builder.mutationProviders;
        this.eventsProviders = builder.eventsProviders;
        this.codeRegistryProvider = builder.codeRegistryProvider;
    }

    public GraphQLSchema createSchema() {
        this.graphQLAnnotations = new GraphQLAnnotations();

        registerTypeFunctions();

        configureElementsContainer();

        registerDynamicFields();

        registerExtensions();

        registerAdditionalTypes();

        transformQuery();

        transformMutations();

        configureCodeRegister();

        final AnnotationsSchemaCreator.Builder annotationsSchema = AnnotationsSchemaCreator.newAnnotationsSchema();

        if (additionalTypes != null) {
            annotationsSchema.additionalTypes(additionalTypes);
        }

        return annotationsSchema
                .query(RootQuery.class)
                .mutation(RootMutation.class)
                .setAnnotationsProcessor(graphQLAnnotations)
                .build();
    }

    public Set<Class<?>> getAdditionalTypes() {
        return additionalTypes;
    }

    private void registerTypeFunctions() {
        if (typeFunctionProviders != null && !typeFunctionProviders.isEmpty()) {
            for (GraphQLTypeFunctionProvider provider : typeFunctionProviders) {
                if (provider != null && provider.getTypeFunctions() != null) {
                    provider.getTypeFunctions().forEach(graphQLAnnotations::registerTypeFunction);
                }
            }
        }
    }

    private void registerDynamicFields() {
        final Collection<PropertyType> propertyTypes = profileService.getTargetPropertyTypes("profiles");

        registerDynamicInputFilterFields(CDPProfilePropertiesFilterInput.TYPE_NAME, CDPProfilePropertiesFilterInput.class, propertyTypes);
        registerDynamicInputFields(CDPProfileUpdateEventInput.TYPE_NAME, CDPProfileUpdateEventInput.class, propertyTypes);

        // Profile
        registerDynamicOutputFields(CDPProfile.TYPE_NAME, CDPProfile.class, CustomerPropertyDataFetcher.class, propertyTypes);

        // Persona
        registerDynamicInputFields(CDPPersonaInput.TYPE_NAME, CDPPersonaInput.class, propertyTypes);
        registerDynamicOutputFields(CDPPersona.TYPE_NAME, CDPPersona.class, CustomerPropertyDataFetcher.class, propertyTypes);

        // Events
        createEventInputTypes();
    }

    private void registerDynamicInputFilterFields(final String typeName,
                                                  final Class<?> annotatedClass,
                                                  final Collection<PropertyType> propertyTypes) {
        final GraphQLInputObjectType originalObject = getInputObjectType(annotatedClass);

        final List<GraphQLInputObjectField> inputObjectFields =
                PropertyFilterUtils.buildInputPropertyFilters(propertyTypes);

        final GraphQLInputObjectType transformedObjectType =
                originalObject.transform(builder -> inputObjectFields.forEach(builder::field));

        registerInTypeRegistry(typeName, transformedObjectType);
    }

    private void registerDynamicOutputFields(final String graphQLTypeName,
                                             final Class<?> annotatedClass,
                                             final Class<? extends DynamicFieldDataFetcher> fetcherClass,
                                             final Collection<PropertyType> propertyTypes) {
        final GraphQLCodeRegistry.Builder codeRegisterBuilder = graphQLAnnotations.getContainer().getCodeRegistryBuilder();

        final List<GraphQLFieldDefinition> fieldDefinitions = propertyTypes.stream().map(propertyType -> {
            final String propertyName = PropertyNameTranslator.translateFromUnomiToGraphQL(propertyType.getItemId());

            try {
                final DataFetcher dataFetcher = fetcherClass.getConstructor(String.class).newInstance(propertyName);
                codeRegisterBuilder.dataFetcher(FieldCoordinates.coordinates(graphQLTypeName, propertyName), dataFetcher);
            } catch (Exception e) {
                throw new RuntimeException(String.format("Error creating a data fetcher with class %s for field %s", fetcherClass.getName(), propertyName), e);
            }

            return GraphQLFieldDefinition.newFieldDefinition()
                    .type((GraphQLOutputType) convert(propertyType.getValueTypeId()))
                    .name(propertyName).build();
        }).collect(Collectors.toList());

        final GraphQLObjectType transformedObjectType = graphQLAnnotations.object(annotatedClass)
                .transform(builder -> fieldDefinitions.forEach(builder::field));

        registerInTypeRegistry(graphQLTypeName, transformedObjectType);
    }

    private GraphQLInputObjectType registerDynamicInputFields(final String graphQLTypeName,
                                                              final Class<?> clazz,
                                                              final Collection<PropertyType> propertyTypes) {

        final List<GraphQLInputObjectField> fieldDefinitions = propertyTypes.stream()
                .map(propertyType -> GraphQLInputObjectField.newInputObjectField()
                        .type((GraphQLInputType) convert(propertyType.getValueTypeId()))
                        .name(PropertyNameTranslator.translateFromUnomiToGraphQL(propertyType.getItemId()))
                        .build())
                .collect(Collectors.toList());

        final GraphQLInputObjectType transformedObjectType = getInputObjectType(clazz)
                .transform(builder -> fieldDefinitions.forEach(builder::field));

        registerInTypeRegistry(graphQLTypeName, transformedObjectType);

        return transformedObjectType;
    }

    private void createEventInputTypes() {

        final GraphQLInputObjectType.Builder builder = GraphQLInputObjectType.newInputObject()
                .name(CDPEventInput.TYPE_NAME)
                .fields(getInputObjectType(CDPEventInput.class).getFieldDefinitions());

        if (!eventsProviders.isEmpty()) {
            eventsProviders.forEach(eventProvider -> {
                if (eventProvider.getProcessEvents() != null) {
                    eventProvider.getProcessEvents().forEach(eventProcessorClass -> {
                        final String typeName = ReflectionUtil.getTypeName(eventProcessorClass);

                        final GraphQLInputObjectType.Builder eventInput = GraphQLInputObjectType.newInputObject()
                                .name(typeName)
                                .fields(getInputObjectType(eventProcessorClass).getFieldDefinitions())
                                .definition(InputObjectTypeDefinition.newInputObjectDefinition()
                                        .additionalData(CDPGraphQLConstants.EVENT_PROCESSOR_CLASS, eventProcessorClass.getName()).build()
                                );

                        builder.field(GraphQLInputObjectField.newInputObjectField()
                                .name(generateFieldName(typeName))
                                .type(eventInput)
                                .build());
                    });
                }
            });
        }

        registerInTypeRegistry(CDPEventInput.TYPE_NAME, builder.build());
    }

    private String generateFieldName(final String typeName) {
        final int index = typeName.indexOf("_");

        char[] chars = typeName.substring(index + 1).toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);

        final String eventName = typeName.substring(0, index).toLowerCase() + "_" + new String(chars);

        return eventName.replace("Input", "");
    }

    private void registerInTypeRegistry(final String name, final GraphQLType type) {
        graphQLAnnotations.getContainer().getTypeRegistry().put(name, type);
    }

    private void configureElementsContainer() {
        final ProcessingElementsContainer container = graphQLAnnotations.getContainer();

        container.setInputPrefix("");
        container.setInputSuffix("");
    }

    private void registerExtensions() {
        if (extensionsProviders != null && !extensionsProviders.isEmpty()) {
            for (GraphQLExtensionsProvider extensionsProvider : extensionsProviders) {
                if (extensionsProvider.getExtensions() != null) {
                    extensionsProvider.getExtensions().forEach(graphQLAnnotations::registerTypeExtension);
                }
            }
        }
    }

    private void transformQuery() {
        final Map<String, GraphQLType> typeRegistry = graphQLAnnotations.getContainer().getTypeRegistry();

        if (queryProviders != null && !queryProviders.isEmpty()) {
            for (GraphQLQueryProvider queryProvider : queryProviders) {
                final Set<GraphQLFieldDefinition> queries = queryProvider.getQueries(graphQLAnnotations);

                if (queries != null) {
                    final GraphQLObjectType transformedType = graphQLAnnotations.object(RootQuery.class)
                            .transform(builder -> queries.forEach(builder::field));
                    typeRegistry.put(RootQuery.TYPE_NAME, transformedType);
                }
            }
        }
    }

    private void transformMutations() {
        final Map<String, GraphQLType> typeRegistry = graphQLAnnotations.getContainer().getTypeRegistry();

        if (mutationProviders != null && !mutationProviders.isEmpty()) {
            for (GraphQLMutationProvider mutationProvider : mutationProviders) {
                final Set<GraphQLFieldDefinition> mutations = mutationProvider.getMutations(graphQLAnnotations);

                if (mutations != null) {
                    final GraphQLObjectType transformedMutationType = graphQLAnnotations.object(RootMutation.class)
                            .transform(builder -> mutations.forEach(builder::field));
                    typeRegistry.put(RootMutation.TYPE_NAME, transformedMutationType);
                }
            }
        }
    }

    private void registerAdditionalTypes() {
        if (additionalTypesProviders == null || additionalTypesProviders.isEmpty()) {
            return;
        }

        for (final GraphQLAdditionalTypesProvider typesProvider : additionalTypesProviders) {
            if (typesProvider.getAdditionalTypes() != null) {
                additionalTypes.addAll(typesProvider.getAdditionalTypes());
            }
        }
    }

    private void configureCodeRegister() {
        if (codeRegistryProvider != null) {
            graphQLAnnotations.getContainer().setCodeRegistryBuilder(
                    codeRegistryProvider.getCodeRegistry(
                            graphQLAnnotations.getContainer().getCodeRegistryBuilder().build()
                    ));
        }
    }

    private GraphQLType convert(final String type) {
        switch (type) {
            case "integer":
                return Scalars.GraphQLInt;
            case "float":
                return Scalars.GraphQLFloat;
            case "set":
                return null; // TODO
            case "date":
                return DateTimeFunction.DATE_TIME_SCALAR;
            case "boolean":
                return Scalars.GraphQLBoolean;
            default: {
                return Scalars.GraphQLString;
            }
        }
    }

    public GraphQLInputObjectType getInputObjectType(final Class<?> annotatedClass) {
        return (GraphQLInputObjectType) graphQLAnnotations.getObjectHandler().getTypeRetriever()
                .getGraphQLType(annotatedClass, graphQLAnnotations.getContainer(), true);
    }

    public static Builder create(final ProfileService profileService) {
        return new Builder(profileService);
    }

    static class Builder {

        final ProfileService profileService;

        List<GraphQLTypeFunctionProvider> typeFunctionProviders;

        List<GraphQLExtensionsProvider> extensionsProviders;

        List<GraphQLAdditionalTypesProvider> additionalTypesProviders;

        List<GraphQLQueryProvider> queryProviders;

        List<GraphQLMutationProvider> mutationProviders;

        List<GraphQLProcessEventsProvider> eventsProviders;

        GraphQLCodeRegistryProvider codeRegistryProvider;

        private Builder(final ProfileService profileService) {
            this.profileService = profileService;
        }

        public Builder typeFunctionProviders(List<GraphQLTypeFunctionProvider> typeFunctionProviders) {
            this.typeFunctionProviders = typeFunctionProviders;
            return this;
        }

        public Builder extensionsProviders(List<GraphQLExtensionsProvider> extensionsProviders) {
            this.extensionsProviders = extensionsProviders;
            return this;
        }

        public Builder additionalTypesProviders(List<GraphQLAdditionalTypesProvider> additionalTypesProviders) {
            this.additionalTypesProviders = additionalTypesProviders;
            return this;
        }

        public Builder queryProviders(List<GraphQLQueryProvider> queryProviders) {
            this.queryProviders = queryProviders;
            return this;
        }

        public Builder mutationProviders(List<GraphQLMutationProvider> mutationProviders) {
            this.mutationProviders = mutationProviders;
            return this;
        }

        public Builder eventsProviders(List<GraphQLProcessEventsProvider> eventsProviders) {
            this.eventsProviders = eventsProviders;
            return this;
        }

        public Builder codeRegistryProvider(GraphQLCodeRegistryProvider codeRegistryProvider) {
            this.codeRegistryProvider = codeRegistryProvider;
            return this;
        }

        public GraphQLSchemaProvider build() {
            return new GraphQLSchemaProvider(this);
        }

    }

}
