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
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import org.apache.unomi.api.EventType;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.services.EventTypeRegistry;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.graphql.CDPGraphQLConstants;
import org.apache.unomi.graphql.converters.UnomiToGraphQLConverter;
import org.apache.unomi.graphql.fetchers.CustomEventOrSetPropertyDataFetcher;
import org.apache.unomi.graphql.fetchers.CustomerPropertyDataFetcher;
import org.apache.unomi.graphql.fetchers.DynamicFieldDataFetcher;
import org.apache.unomi.graphql.fetchers.event.EventListenerSubscriptionFetcher;
import org.apache.unomi.graphql.fetchers.event.UnomiEventPublisher;
import org.apache.unomi.graphql.providers.GraphQLAdditionalTypesProvider;
import org.apache.unomi.graphql.providers.GraphQLCodeRegistryProvider;
import org.apache.unomi.graphql.providers.GraphQLExtensionsProvider;
import org.apache.unomi.graphql.providers.GraphQLFieldVisibilityProvider;
import org.apache.unomi.graphql.providers.GraphQLMutationProvider;
import org.apache.unomi.graphql.providers.GraphQLQueryProvider;
import org.apache.unomi.graphql.providers.GraphQLSubscriptionProvider;
import org.apache.unomi.graphql.providers.GraphQLTypeFunctionProvider;
import org.apache.unomi.graphql.types.input.CDPEventFilterInput;
import org.apache.unomi.graphql.types.input.CDPEventInput;
import org.apache.unomi.graphql.types.input.CDPEventProcessor;
import org.apache.unomi.graphql.types.input.CDPPersonaInput;
import org.apache.unomi.graphql.types.input.CDPProfilePropertiesFilterInput;
import org.apache.unomi.graphql.types.input.CDPProfileUpdateEventFilterInput;
import org.apache.unomi.graphql.types.input.CDPProfileUpdateEventInput;
import org.apache.unomi.graphql.types.input.CDPUnomiEventInput;
import org.apache.unomi.graphql.types.input.EventFilterInputMarker;
import org.apache.unomi.graphql.types.output.CDPEventInterface;
import org.apache.unomi.graphql.types.output.CDPPersona;
import org.apache.unomi.graphql.types.output.CDPProfile;
import org.apache.unomi.graphql.types.output.RootMutation;
import org.apache.unomi.graphql.types.output.RootQuery;
import org.apache.unomi.graphql.utils.ReflectionUtil;
import org.apache.unomi.graphql.utils.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static graphql.schema.FieldCoordinates.coordinates;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;

public class GraphQLSchemaProvider {

    private final ProfileService profileService;

    private final EventTypeRegistry eventTypeRegistry;

    private final List<GraphQLTypeFunctionProvider> typeFunctionProviders;

    private final List<GraphQLExtensionsProvider> extensionsProviders;

    private final List<GraphQLAdditionalTypesProvider> additionalTypesProviders;

    private final List<GraphQLQueryProvider> queryProviders;

    private final List<GraphQLMutationProvider> mutationProviders;

    private final List<GraphQLSubscriptionProvider> subscriptionProviders;

    private final GraphQLFieldVisibilityProvider fieldVisibilityProvider;

    private final GraphQLCodeRegistryProvider codeRegistryProvider;

    private final UnomiEventPublisher eventPublisher;

    private GraphQLAnnotations graphQLAnnotations;

    private Set<Class<?>> additionalTypes = new HashSet<>();

    private GraphQLSchemaProvider(final Builder builder) {
        this.profileService = builder.profileService;
        this.eventTypeRegistry = builder.eventTypeRegistry;
        this.eventPublisher = builder.eventPublisher;
        this.typeFunctionProviders = builder.typeFunctionProviders;
        this.extensionsProviders = builder.extensionsProviders;
        this.additionalTypesProviders = builder.additionalTypesProviders;
        this.queryProviders = builder.queryProviders;
        this.mutationProviders = builder.mutationProviders;
        this.subscriptionProviders = builder.subscriptionProviders;
        this.codeRegistryProvider = builder.codeRegistryProvider;
        this.fieldVisibilityProvider = builder.fieldVisibilityProvider;
    }

    public GraphQLSchema createSchema() {
        this.graphQLAnnotations = new GraphQLAnnotations();

        final GraphQLSchema.Builder schemaBuilder = GraphQLSchema.newSchema();

        registerTypeFunctions();

        configureElementsContainer();

        registerDynamicFields(schemaBuilder);

        registerExtensions();

        registerAdditionalTypes();

        transformQuery();

        transformMutations();

        configureFieldVisibility();

        configureCodeRegister();

        final AnnotationsSchemaCreator.Builder annotationsSchema = AnnotationsSchemaCreator.newAnnotationsSchema();

        if (additionalTypes != null) {
            annotationsSchema.additionalTypes(additionalTypes);
        }

        createSubscriptionSchema(schemaBuilder);

        return annotationsSchema
                .setGraphQLSchemaBuilder(schemaBuilder)
                .query(RootQuery.class)
                .mutation(RootMutation.class)
                .setAnnotationsProcessor(graphQLAnnotations)
                .build();
    }

    private void createSubscriptionSchema(final GraphQLSchema.Builder schemaBuilder) {
        final GraphQLInputObjectType eventFilterInputType = (GraphQLInputObjectType) getFromTypeRegistry(CDPEventFilterInput.TYPE_NAME);
        final GraphQLInterfaceType eventInterfaceType = (GraphQLInterfaceType) getFromTypeRegistry(CDPEventInterface.TYPE_NAME);

        // creating subscriptions dynamically because annotations
        // doesn't seem to be able to handle data fetchers returning publishers
        GraphQLObjectType cdpSubscription = newObject()
                .name("CDP_Subscription")
                .field(newFieldDefinition()
                        .argument(newArgument()
                                .name("filter")
                                .type(eventFilterInputType)
                                .build())
                        .name("eventListener")
                        .type(eventInterfaceType)
                        .build())
                .build();

        // register data fetcher in annotations instead of codeRegistry
        // because annotations will overwrite it on schema build
        graphQLAnnotations.getContainer().getCodeRegistryBuilder()
                .dataFetcher(
                        coordinates("CDP_Subscription", "eventListener"),
                        new EventListenerSubscriptionFetcher(eventPublisher));

        if (subscriptionProviders != null && !subscriptionProviders.isEmpty()) {
            for (GraphQLSubscriptionProvider subscriptionProvider : subscriptionProviders) {
                final Set<GraphQLFieldDefinition> subscriptions = subscriptionProvider.getSubscriptions(graphQLAnnotations);

                if (subscriptions != null) {
                    cdpSubscription = cdpSubscription.transform(builder -> subscriptions.forEach(builder::field));
                }
            }
        }

        schemaBuilder.subscription(cdpSubscription);
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

    private void registerDynamicFields(GraphQLSchema.Builder schemaBuilder) {
        if (additionalTypesProviders != null && !additionalTypesProviders.isEmpty()) {
            for (final GraphQLAdditionalTypesProvider typeProvider : additionalTypesProviders) {
                if (typeProvider != null && typeProvider.getAdditionalInputTypes() != null) {
                    typeProvider.getAdditionalInputTypes().forEach(additionalType -> {
                        final String typeName = ReflectionUtil.resolveTypeName(additionalType);

                        final GraphQLInputObjectType.Builder builder = GraphQLInputObjectType.newInputObject()
                                .name(typeName)
                                .fields(getInputObjectType(additionalType).getFieldDefinitions());

                        registerInTypeRegistry(typeName, builder.build());
                    });
                }
            }
        }

        final Collection<PropertyType> propertyTypes = profileService.getTargetPropertyTypes("profiles");

        // Profile
        registerDynamicInputFilterFields(CDPProfilePropertiesFilterInput.TYPE_NAME, CDPProfilePropertiesFilterInput.class, propertyTypes);
        registerDynamicInputFilterFields(CDPProfileUpdateEventFilterInput.TYPE_NAME, CDPProfileUpdateEventFilterInput.class, propertyTypes);
        registerDynamicInputFields(CDPProfileUpdateEventInput.TYPE_NAME, CDPProfileUpdateEventInput.class, propertyTypes);
        registerDynamicEventFilterInputFields();

        // Profile
        registerDynamicOutputFields(CDPProfile.TYPE_NAME, CDPProfile.class, CustomerPropertyDataFetcher.class, propertyTypes);

        // Persona
        registerDynamicInputFields(CDPPersonaInput.TYPE_NAME, CDPPersonaInput.class, propertyTypes);
        registerDynamicOutputFields(CDPPersona.TYPE_NAME, CDPPersona.class, CustomerPropertyDataFetcher.class, propertyTypes);

        // Events
        registerDynamicUnomiInputEvents(schemaBuilder);
        registerDynamicUnomiOutputEvents(schemaBuilder);
        registerDynamicEventInputFields();
    }

    private void registerDynamicUnomiInputEvents(GraphQLSchema.Builder schemaBuilder) {
        final Collection<EventType> unomiEventTypes = eventTypeRegistry.getAll();

        if (!unomiEventTypes.isEmpty()) {
            for (EventType unomiEventType : unomiEventTypes) {
                final String typeName = UnomiToGraphQLConverter.convertEventType(unomiEventType.getType()) + "Input";

                final GraphQLInputObjectType objectType;
                if (!graphQLAnnotations.getContainer().getTypeRegistry().containsKey(typeName)) {
                    objectType = createDynamicEventInputType(unomiEventType);
                } else {
                    objectType = (GraphQLInputObjectType) getFromTypeRegistry(typeName);
                    registerDynamicInputFields(typeName, objectType, unomiEventType.getPropertyTypes());
                }

                if (objectType != null) {
                    schemaBuilder.additionalType(objectType);
                }
            }
        }
    }

    private void registerDynamicUnomiOutputEvents(GraphQLSchema.Builder schemaBuilder) {
        final Collection<EventType> unomiEventTypes = eventTypeRegistry.getAll();

        if (!unomiEventTypes.isEmpty()) {
            final GraphQLCodeRegistry.Builder codeRegisterBuilder = graphQLAnnotations.getContainer().getCodeRegistryBuilder();

            for (EventType unomiEventType : unomiEventTypes) {
                final String typeName = UnomiToGraphQLConverter.convertEventType(unomiEventType.getType());

                final GraphQLObjectType objectType;
                if (!graphQLAnnotations.getContainer().getTypeRegistry().containsKey(typeName)) {
                    objectType = createDynamicEventOutputType(unomiEventType, codeRegisterBuilder);
                } else {
                    objectType = (GraphQLObjectType) getFromTypeRegistry(typeName);
                    registerDynamicOutputFields(typeName, objectType, CustomerPropertyDataFetcher.class, unomiEventType.getPropertyTypes());
                }

                if (objectType != null) {
                    schemaBuilder.additionalType(objectType);
                }
            }
        }
    }

    private void registerDynamicInputFilterFields(final String typeName,
                                                  final Class<?> annotatedClass,
                                                  final Collection<PropertyType> propertyTypes) {
        final GraphQLInputObjectType originalObject = getInputObjectType(annotatedClass);

        final List<GraphQLInputObjectField> inputObjectFields =
                PropertyFilterUtils.buildInputPropertyFilters(propertyTypes, graphQLAnnotations);

        final GraphQLInputObjectType transformedObjectType =
                originalObject.transform(builder -> inputObjectFields.forEach(builder::field));

        registerInTypeRegistry(typeName, transformedObjectType);
    }

    private void registerDynamicOutputFields(final String graphQLTypeName,
                                             final Class<?> annotatedClass,
                                             final Class<? extends DynamicFieldDataFetcher> fetcherClass,
                                             final Collection<PropertyType> propertyTypes) {
        final GraphQLObjectType objectType = graphQLAnnotations.object(annotatedClass);
        registerDynamicOutputFields(graphQLTypeName, objectType, fetcherClass, propertyTypes);
    }

    private void registerDynamicOutputFields(final String graphQLTypeName,
                                             final GraphQLObjectType graphQLObjectType,
                                             final Class<? extends DynamicFieldDataFetcher> fetcherClass,
                                             final Collection<PropertyType> propertyTypes) {
        final GraphQLCodeRegistry.Builder codeRegisterBuilder = graphQLAnnotations.getContainer().getCodeRegistryBuilder();

        final List<GraphQLFieldDefinition> fieldDefinitions = new ArrayList<>();

        propertyTypes.forEach(propertyType -> {
            final String propertyName = PropertyNameTranslator.translateFromUnomiToGraphQL(propertyType.getItemId());

            if ("set".equals(propertyType.getValueTypeId())) {
                final String typeName = StringUtils.capitalize(propertyName);

                if (!graphQLAnnotations.getContainer().getTypeRegistry().containsKey(typeName)) {
                    GraphQLObjectType objectType = createDynamicSetOutputType(propertyType, codeRegisterBuilder, null);
                    if (objectType != null) {
                        fieldDefinitions.add(GraphQLFieldDefinition.newFieldDefinition()
                                .type(objectType)
                                .name(propertyName).build());
                    }
                } else {
                    fieldDefinitions.add(GraphQLFieldDefinition.newFieldDefinition()
                            .type((GraphQLObjectType) getFromTypeRegistry(typeName))
                            .name(propertyName).build());
                }
            } else {
                fieldDefinitions.add(GraphQLFieldDefinition.newFieldDefinition()
                        .type((GraphQLOutputType) UnomiToGraphQLConverter.convertPropertyType(propertyType.getValueTypeId()))
                        .name(propertyName).build());
            }

            try {
                final DataFetcher dataFetcher = fetcherClass.getConstructor(String.class, String.class).newInstance(propertyName, propertyType.getValueTypeId());
                codeRegisterBuilder.dataFetcher(FieldCoordinates.coordinates(graphQLTypeName, propertyName), dataFetcher);
            } catch (Exception e) {
                throw new RuntimeException(String.format("Error creating a data fetcher with class %s for field %s", fetcherClass.getName(), propertyName), e);
            }
        });

        if (!fieldDefinitions.isEmpty()) {
            final GraphQLObjectType transformedObjectType = graphQLObjectType.transform(builder -> fieldDefinitions.forEach(builder::field));

            registerInTypeRegistry(graphQLTypeName, transformedObjectType);
        }
    }

    private GraphQLObjectType createDynamicSetOutputType(
            final PropertyType propertyType, final GraphQLCodeRegistry.Builder codeRegisterBuilder, final String parentName) {

        return createDynamicOutputType(parentName != null ? parentName : propertyType.getItemId(), propertyType.getChildPropertyTypes(), null, codeRegisterBuilder);
    }

    private GraphQLObjectType createDynamicEventOutputType(
            final EventType eventType, final GraphQLCodeRegistry.Builder codeRegisterBuilder) {

        final Set<Class> interfaces = new HashSet<>();
        interfaces.add(CDPEventInterface.class);
        return createDynamicOutputType(UnomiToGraphQLConverter.convertEventType(eventType.getType()), eventType.getPropertyTypes(), interfaces, codeRegisterBuilder);
    }

    private GraphQLObjectType createDynamicOutputType(final String name, final Set<PropertyType> propertyTypes, final Set<Class> interfaces, final GraphQLCodeRegistry.Builder codeRegisterBuilder) {
        final String typeName = StringUtils.capitalize(PropertyNameTranslator.translateFromUnomiToGraphQL(name));

        final GraphQLObjectType.Builder dynamicTypeBuilder = GraphQLObjectType.newObject()
                .name(typeName);

        final List<GraphQLFieldDefinition> fieldDefinitions = new ArrayList<>();

        if (interfaces != null && !interfaces.isEmpty()) {
            for (Class anInterface : interfaces) {
                final GraphQLInterfaceType graphQLInterface = (GraphQLInterfaceType) getOutputType(anInterface);
                if (graphQLInterface != null) {
                    dynamicTypeBuilder.withInterface(graphQLInterface);
                    graphQLInterface.getFieldDefinitions().forEach(fieldDefinition -> {
                        fieldDefinitions.add(fieldDefinition);

                        final String propertyName = fieldDefinition.getName();
                        final DataFetcher dataFetcher = new CustomEventOrSetPropertyDataFetcher(propertyName);
                        codeRegisterBuilder.dataFetcher(FieldCoordinates.coordinates(typeName, propertyName), dataFetcher);
                    });
                }
            }
        }

        if (propertyTypes != null && !propertyTypes.isEmpty()) {
            propertyTypes.forEach(childPropertyType -> {
                final boolean isSet = "set".equals(childPropertyType.getValueTypeId());
                String childPropertyName = PropertyNameTranslator.translateFromUnomiToGraphQL(childPropertyType.getItemId());

                GraphQLOutputType objectType = null;
                if (isSet) {
                    objectType = createDynamicSetOutputType(childPropertyType, codeRegisterBuilder, typeName + "_" + childPropertyName);
                } else {
                    objectType = (GraphQLOutputType) UnomiToGraphQLConverter.convertPropertyType(childPropertyType.getValueTypeId());
                }

                if (objectType != null) {
                    fieldDefinitions.add(GraphQLFieldDefinition.newFieldDefinition()
                            .name(childPropertyName)
                            .type(objectType)
                            .build());

                    codeRegisterBuilder.dataFetcher(FieldCoordinates.coordinates(typeName, childPropertyName),
                            new CustomEventOrSetPropertyDataFetcher(childPropertyName));
                }
            });
        }

        if (!fieldDefinitions.isEmpty()) {
            fieldDefinitions.forEach(dynamicTypeBuilder::field);
            final GraphQLObjectType objectType = dynamicTypeBuilder.build();
            registerInTypeRegistry(typeName, objectType);
            return objectType;
        }

        return null;
    }

    private GraphQLInputObjectType createDynamicEventInputType(final EventType eventType) {
        return createDynamicInputType(UnomiToGraphQLConverter.convertEventType(eventType.getType()), eventType.getPropertyTypes(), true);
    }

    private GraphQLInputObjectType createDynamicSetInputType(final PropertyType propertyType, final String parentName) {
        return createDynamicInputType(parentName != null ? parentName : propertyType.getItemId(), propertyType.getChildPropertyTypes(), false);
    }

    private GraphQLInputObjectType createDynamicInputType(final String name, final Set<PropertyType> propertyTypes, final boolean isEvent) {
        final String typeName = StringUtils.capitalize(PropertyNameTranslator.translateFromUnomiToGraphQL(name)) + "Input";

        final GraphQLInputObjectType.Builder dynamicTypeBuilder = GraphQLInputObjectType.newInputObject()
                .name(typeName);

        if (isEvent) {
            dynamicTypeBuilder.definition(InputObjectTypeDefinition.newInputObjectDefinition()
                    .additionalData(CDPGraphQLConstants.EVENT_PROCESSOR_CLASS, CDPUnomiEventInput.class.getName()).build()
            );
        }

        final List<GraphQLInputObjectField> fieldDefinitions = new ArrayList<>();

        if (propertyTypes != null && !propertyTypes.isEmpty()) {
            propertyTypes.forEach(childPropertyType -> {
                final boolean isSet = "set".equals(childPropertyType.getValueTypeId());
                String childPropertyName = PropertyNameTranslator.translateFromUnomiToGraphQL(childPropertyType.getItemId());

                GraphQLInputType objectType;
                if (isSet) {
                    objectType = createDynamicSetInputType(childPropertyType, typeName + "_" + childPropertyName);
                } else {
                    objectType = (GraphQLInputType) UnomiToGraphQLConverter.convertPropertyType(childPropertyType.getValueTypeId());
                }

                if (objectType != null) {
                    fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                            .name(childPropertyName)
                            .type(objectType)
                            .build());
                }
            });
        }

        if (!fieldDefinitions.isEmpty()) {
            fieldDefinitions.forEach(dynamicTypeBuilder::field);
            final GraphQLInputObjectType objectType = dynamicTypeBuilder.build();
            registerInTypeRegistry(typeName, objectType);
            return objectType;
        } else {
            return null;
        }
    }

    private void registerDynamicInputFields(final String graphQLTypeName,
                                            final Class<?> clazz,
                                            final Collection<PropertyType> propertyTypes) {
        final GraphQLInputObjectType inputObjectType = getInputObjectType(clazz);
        registerDynamicInputFields(graphQLTypeName, inputObjectType, propertyTypes);
    }

    private void registerDynamicInputFields(final String graphQLTypeName,
                                            final GraphQLInputObjectType graphQLInputObjectType,
                                            final Collection<PropertyType> propertyTypes) {
        final List<GraphQLInputObjectField> fieldDefinitions = new ArrayList<>();

        propertyTypes.forEach(propertyType -> {
            final String propertyName = PropertyNameTranslator.translateFromUnomiToGraphQL(propertyType.getItemId());

            if ("set".equals(propertyType.getValueTypeId())) {
                final String typeName = StringUtils.capitalize(propertyName) + "Input";

                if (!graphQLAnnotations.getContainer().getTypeRegistry().containsKey(typeName)) {
                    final GraphQLInputObjectType inputType = createDynamicSetInputType(propertyType, null);
                    if (inputType != null) {
                        fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                                .name(propertyName)
                                .type(inputType)
                                .build());
                    }
                } else {
                    fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                            .name(propertyName)
                            .type((GraphQLInputType) getFromTypeRegistry(typeName))
                            .build());
                }
            } else {
                fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                        .type((GraphQLInputType) UnomiToGraphQLConverter.convertPropertyType(propertyType.getValueTypeId()))
                        .name(propertyName)
                        .build());
            }
        });

        if (!fieldDefinitions.isEmpty()) {
            final GraphQLInputObjectType transformedObjectType = graphQLInputObjectType
                    .transform(builder -> fieldDefinitions.forEach(builder::field));

            registerInTypeRegistry(graphQLTypeName, transformedObjectType);
        }
    }

    private void registerDynamicEventInputFields() {
        final GraphQLInputObjectType.Builder builder = GraphQLInputObjectType.newInputObject()
                .name(CDPEventInput.TYPE_NAME)
                .fields(getInputObjectType(CDPEventInput.class).getFieldDefinitions());


        if (!additionalTypesProviders.isEmpty()) {
            additionalTypesProviders.forEach(provider -> {
                if (provider != null && provider.getAdditionalInputTypes() != null) {
                    provider.getAdditionalInputTypes().stream()
                            .filter(CDPEventProcessor.class::isAssignableFrom)
                            .forEach(additionalType -> {
                                final String typeName = ReflectionUtil.resolveTypeName(additionalType);

                                final GraphQLInputObjectType.Builder eventInput = GraphQLInputObjectType.newInputObject()
                                        .name(typeName)
                                        .fields(getInputObjectType(additionalType).getFieldDefinitions())
                                        .definition(InputObjectTypeDefinition.newInputObjectDefinition()
                                                .additionalData(CDPGraphQLConstants.EVENT_PROCESSOR_CLASS, additionalType.getName()).build()
                                        );

                                builder.field(GraphQLInputObjectField.newInputObjectField()
                                        .name(generateFieldName(typeName))
                                        .type(eventInput)
                                        .build());
                            });
                }
            });
        }

        // now add all unomi defined event types
        final Collection<EventType> unomiEventTypes = eventTypeRegistry.getAll();
        unomiEventTypes.forEach(eventType -> {
            final String typeName = UnomiToGraphQLConverter.convertEventType(eventType.getType());

            builder.field(GraphQLInputObjectField.newInputObjectField()
                    .name(StringUtils.decapitalize(typeName))
                    .type((GraphQLInputType) getFromTypeRegistry(typeName + "Input"))
                    .build());
        });

        registerInTypeRegistry(CDPEventInput.TYPE_NAME, builder.build());
    }

    private void registerDynamicEventFilterInputFields() {
        final List<GraphQLInputObjectField> fieldDefinitions = new ArrayList<>();

        final List<String> registeredFieldNames = getInputObjectType(CDPEventFilterInput.class).getFieldDefinitions()
                .stream()
                .map(GraphQLInputObjectField::getName)
                .collect(Collectors.toList());

        if (!additionalTypesProviders.isEmpty()) {
            additionalTypesProviders.forEach(provider -> {
                if (provider != null && provider.getAdditionalInputTypes() != null) {
                    provider.getAdditionalInputTypes().stream()
                            .filter(EventFilterInputMarker.class::isAssignableFrom)
                            .forEach(additionalType -> {
                                final String typeName = ReflectionUtil.resolveTypeName(additionalType);

                                final String fieldName = generateFieldName(typeName.replace("FilterInput", ""));

                                if (!registeredFieldNames.contains(fieldName)) {
                                    fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                                            .type(getInputObjectType(additionalType))
                                            .name(fieldName)
                                            .build());
                                }
                            });
                }
            });
        }

        final GraphQLInputObjectType transformedObjectType = getInputObjectType(CDPEventFilterInput.class)
                .transform(builder -> fieldDefinitions.forEach(builder::field));

        registerInTypeRegistry(CDPEventFilterInput.TYPE_NAME, transformedObjectType);
    }

    private String generateFieldName(final String typeName) {
        final int index = typeName.indexOf("_");

        char[] chars = typeName.substring(index + 1).toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);

        final String eventName = typeName.substring(0, index).toLowerCase() + "_" + new String(chars);

        return eventName.replace("Filter", "")
                .replace("Input", "");
    }

    private void registerInTypeRegistry(final String name, final GraphQLType type) {
        graphQLAnnotations.getContainer().getTypeRegistry().put(name, type);
    }

    private GraphQLType getFromTypeRegistry(final String name) {
        return graphQLAnnotations.getContainer().getTypeRegistry().get(name);
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
            if (typesProvider.getAdditionalOutputTypes() != null) {
                additionalTypes.addAll(typesProvider.getAdditionalOutputTypes());
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

    private void configureFieldVisibility() {
        if (fieldVisibilityProvider != null) {
            graphQLAnnotations.getContainer().getCodeRegistryBuilder()
                    .fieldVisibility(fieldVisibilityProvider.getGraphQLFieldVisibility());
        }
    }

    public GraphQLInputObjectType getInputObjectType(final Class<?> annotatedClass) {
        return (GraphQLInputObjectType) graphQLAnnotations.getObjectHandler().getTypeRetriever()
                .getGraphQLType(annotatedClass, graphQLAnnotations.getContainer(), true);
    }

    public GraphQLType getOutputType(final Class<?> annotatedClass) {
        return graphQLAnnotations.getObjectHandler().getTypeRetriever()
                .getGraphQLType(annotatedClass, graphQLAnnotations.getContainer(), false);
    }

    public static Builder create(final ProfileService profileService, final EventTypeRegistry eventTypeRegistry) {
        return new Builder(profileService, eventTypeRegistry);
    }

    static class Builder {

        final ProfileService profileService;

        final EventTypeRegistry eventTypeRegistry;

        List<GraphQLTypeFunctionProvider> typeFunctionProviders;

        List<GraphQLExtensionsProvider> extensionsProviders;

        List<GraphQLAdditionalTypesProvider> additionalTypesProviders;

        List<GraphQLQueryProvider> queryProviders;

        List<GraphQLMutationProvider> mutationProviders;

        List<GraphQLSubscriptionProvider> subscriptionProviders;

        GraphQLFieldVisibilityProvider fieldVisibilityProvider;

        GraphQLCodeRegistryProvider codeRegistryProvider;

        UnomiEventPublisher eventPublisher;

        private Builder(final ProfileService profileService, final EventTypeRegistry eventTypeRegistry) {
            this.profileService = profileService;
            this.eventTypeRegistry = eventTypeRegistry;
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

        public Builder subscriptionProviders(List<GraphQLSubscriptionProvider> subscriptionProviders) {
            this.subscriptionProviders = subscriptionProviders;
            return this;
        }

        public Builder codeRegistryProvider(GraphQLCodeRegistryProvider codeRegistryProvider) {
            this.codeRegistryProvider = codeRegistryProvider;
            return this;
        }

        public Builder eventPublisher(UnomiEventPublisher eventPublisher) {
            this.eventPublisher = eventPublisher;
            return this;
        }

        public Builder fieldVisibilityProvider(GraphQLFieldVisibilityProvider fieldVisibilityProvider) {
            this.fieldVisibilityProvider = fieldVisibilityProvider;
            return this;
        }

        void validate() {
            Objects.requireNonNull(profileService, "Profile service can not be null");
        }

        public GraphQLSchemaProvider build() {
            validate();

            return new GraphQLSchemaProvider(this);
        }

    }

}
