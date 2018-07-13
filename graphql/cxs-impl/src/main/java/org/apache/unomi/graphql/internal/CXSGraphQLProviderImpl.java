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
package org.apache.unomi.graphql.internal;

import graphql.annotations.processor.GraphQLAnnotationsComponent;
import graphql.annotations.processor.ProcessingElementsContainer;
import graphql.schema.*;
import graphql.servlet.GraphQLMutationProvider;
import graphql.servlet.GraphQLQueryProvider;
import graphql.servlet.GraphQLTypesProvider;
import org.apache.unomi.graphql.*;
import org.apache.unomi.graphql.builders.CXSEventBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static graphql.Scalars.GraphQLInt;
import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;

public class CXSGraphQLProviderImpl implements CXSGraphQLProvider, GraphQLQueryProvider, GraphQLTypesProvider, GraphQLMutationProvider {

    private static final Logger logger = LoggerFactory.getLogger(CXSGraphQLProviderImpl.class.getName());

    private CXSProviderManager cxsProviderManager;
    private GraphQLAnnotationsComponent annotationsComponent;
    private ProcessingElementsContainer container;
    private CXSEventBuilders cxsEventBuilders;
    private Map<String,GraphQLType> typeRegistry;

    private Map<String,CXSEventType> eventTypes = new TreeMap<>();

    public CXSGraphQLProviderImpl(GraphQLAnnotationsComponent annotationsComponent) {
        this.annotationsComponent = annotationsComponent;
        container = annotationsComponent.createContainer();
        typeRegistry = container.getTypeRegistry();
        cxsEventBuilders = new CXSEventBuilders(annotationsComponent, container, eventTypes);
        updateGraphQLTypes();
    }

    @Override
    public Map<String, CXSEventType> getEventTypes() {
        return eventTypes;
    }

    public CXSProviderManager getCxsProviderManager() {
        return cxsProviderManager;
    }

    public void updateGraphQLTypes() {

        Map<String,GraphQLType> typeRegistry = container.getTypeRegistry();

        typeRegistry.put(PageInfo.class.getName(), annotationsComponent.getOutputTypeProcessor().getOutputTypeOrRef(PageInfo.class, container));

        typeRegistry.put(CXSGeoPoint.class.getName(), annotationsComponent.getOutputTypeProcessor().getOutputTypeOrRef(CXSGeoPoint.class, container));
        typeRegistry.put(CXSSetPropertyType.class.getName(),annotationsComponent.getOutputTypeProcessor().getOutputTypeOrRef(CXSSetPropertyType.class, container));
        typeRegistry.put(CXSEventType.class.getName(), annotationsComponent.getOutputTypeProcessor().getOutputTypeOrRef(CXSEventType.class, container));

        typeRegistry.put(CXSGeoDistanceInput.class.getName(), annotationsComponent.getInputTypeProcessor().getInputTypeOrRef(CXSGeoDistanceInput.class, container));
        typeRegistry.put(CXSDateFilterInput.class.getName(), annotationsComponent.getInputTypeProcessor().getInputTypeOrRef(CXSDateFilterInput.class, container));
        typeRegistry.put(CXSEventTypeInput.class.getName(), annotationsComponent.getInputTypeProcessor().getInputTypeOrRef(CXSEventTypeInput.class, container));
        typeRegistry.put(CXSOrderByInput.class.getName(), annotationsComponent.getInputTypeProcessor().getInputTypeOrRef(CXSOrderByInput.class, container));

        typeRegistry.put("CXS_Query", annotationsComponent.getOutputTypeProcessor().getOutputTypeOrRef(CXSQuery.class, container));
        typeRegistry.put("CXS_Mutation", annotationsComponent.getOutputTypeProcessor().getOutputTypeOrRef(CXSMutation.class, container));
        // typeRegistry.put("CXS_Query", buildCXSQueryOutputType());
        // typeRegistry.put("CXS_Mutation", buildCXSMutationOutputType());

        cxsEventBuilders.updateTypes();

    }

    private GraphQLObjectType.Builder getBuilderFromAnnotatedClass(Class annotatedClass) {
        return GraphQLObjectType.newObject()
                .name(annotatedClass.getName())
                .fields(((GraphQLObjectType) annotationsComponent.getOutputTypeProcessor().getOutputTypeOrRef(annotatedClass, container)).getFieldDefinitions());
    }

    private GraphQLOutputType getOutputTypeFromRegistry(String typeName) {
        return (GraphQLOutputType) typeRegistry.get(typeName);
    }

    private GraphQLInputObjectType getInputTypeFromRegistry(String typeName) {
        return (GraphQLInputObjectType) typeRegistry.get(typeName);
    }

    public void setCxsProviderManager(CXSProviderManager cxsProviderManager) {
        this.cxsProviderManager = cxsProviderManager;
    }

    @Override
    public Collection<GraphQLFieldDefinition> getQueries() {
        List<GraphQLFieldDefinition> fieldDefinitions = new ArrayList<GraphQLFieldDefinition>();
        fieldDefinitions.add(newFieldDefinition()
                .type(getOutputTypeFromRegistry("CXS_Query"))
                .name("cxs")
                .description("Root field for all CXS queries")
                .dataFetcher(new DataFetcher() {
                    public Object get(DataFetchingEnvironment environment) {
                        Map<String,Object> map = environment.getContext();
                        return map.keySet();
                    }
                }).build());
        return fieldDefinitions;
    }

    @Override
    public Collection<GraphQLType> getTypes() {
        return new ArrayList<>();
    }

    @Override
    public Collection<GraphQLFieldDefinition> getMutations() {
        List<GraphQLFieldDefinition> fieldDefinitions = new ArrayList<GraphQLFieldDefinition>();
        fieldDefinitions.add(newFieldDefinition()
                .type(getOutputTypeFromRegistry("CXS_Mutation"))
                .name("cxs")
                .description("Root field for all CXS mutations")
                .dataFetcher(new DataFetcher<Object>() {
                    @Override
                    public Object get(DataFetchingEnvironment environment) {
                        Object contextObject = environment.getContext();
                        return contextObject;
                    }
                }).build());
        return fieldDefinitions;
    }

    private GraphQLOutputType buildCXSQueryOutputType() {
        return newObject()
                .name("CXS_Query")
                .description("Root CXS query type")
                .field(newFieldDefinition()
                        .type(new GraphQLList(getOutputTypeFromRegistry(CXSEventType.class.getName())))
                        .name("getEventTypes")
                        .description("Retrieves the list of all the declared CXS event types in the Apache Unomi server")
                )
                .field(newFieldDefinition()
                        .type(new GraphQLList(getOutputTypeFromRegistry("CXS_Event")))
                        .name("getEvent")
                        .description("Retrieves a specific event")
                )
                .field(newFieldDefinition()
                        .type(new GraphQLList(getOutputTypeFromRegistry("CXS_EventConnection")))
                        .name("findEvents")
                        .argument(newArgument()
                                .name("filter")
                                .type(getInputTypeFromRegistry("CXS_EventFilterInput"))
                        )
                        .argument(newArgument()
                                .name("orderBy")
                                .type(getInputTypeFromRegistry(CXSOrderByInput.class.getName()))
                        )
                        .argument(newArgument()
                                .name("first")
                                .type(GraphQLInt)
                                .description("Number of objects to retrieve starting at the after cursor position")
                        )
                        .argument(newArgument()
                                .name("after")
                                .type(GraphQLString)
                                .description("Starting cursor location to retrieve the object from")
                        )
                        .argument(newArgument()
                                .name("last")
                                .type(GraphQLInt)
                                .description("Number of objects to retrieve end at the before cursor position")
                        )
                        .argument(newArgument()
                                .name("before")
                                .type(GraphQLString)
                                .description("End cursor location to retrieve the object from")
                        )
                        .description("Retrieves the events that match the specified filters")
                )
                .field(newFieldDefinition()
                        .type(getOutputTypeFromRegistry("CXS_Segment"))
                        .name("getSegment")
                        .argument(newArgument()
                                .name("segmentId")
                                .type(GraphQLString)
                                .description("Unique identifier for the segment")
                        )
                )
                /*
                .field(newFieldDefinition()
                        .type(new GraphQLList(registeredOutputTypes.get("CXS_ProfileConnection")))
                        .name("findProfiles")
                        .argument(newArgument()
                                .name("filter")
                                .type(registeredInputTypes.get("CXS_ProfileFilterInput"))
                        )
                        .argument(newArgument()
                                .name("orderBy")
                                .type(registeredInputTypes.get(CXSOrderByInput.class.getName()))
                        )
                        .argument(newArgument()
                                .name("first")
                                .type(GraphQLInt)
                                .description("Number of objects to retrieve starting at the after cursor position")
                        )
                        .argument(newArgument()
                                .name("after")
                                .type(GraphQLString)
                                .description("Starting cursor location to retrieve the object from")
                        )
                        .argument(newArgument()
                                .name("last")
                                .type(GraphQLInt)
                                .description("Number of objects to retrieve end at the before cursor position")
                        )
                        .argument(newArgument()
                                .name("before")
                                .type(GraphQLString)
                                .description("End cursor location to retrieve the object from")
                        )
                        .description("Retrieves the profiles that match the specified profiles")
                )
                */
                .build();
    }

    private GraphQLOutputType buildCXSMutationOutputType() {
        return newObject()
                .name("CXS_Mutation")
                .description("Root CXS mutation type")
                .field(newFieldDefinition()
                        .type(getOutputTypeFromRegistry(CXSEventType.class.getName()))
                        .name("createOrUpdateEventType")
                        .argument(newArgument()
                                .name("eventType")
                                .type(getInputTypeFromRegistry(CXSEventTypeInput.class.getName()))
                        )
                        .description("Create or updates a CXS event type in the Apache Unomi server")
                        .dataFetcher(new DataFetcher<CXSEventType>() {
                            @Override
                            public CXSEventType get(DataFetchingEnvironment environment) {
                                Map<String,Object> arguments = environment.getArguments();
                                CXSEventType cxsEventType = new CXSEventType();
                                if (arguments.containsKey("eventType")) {
                                    Map<String,Object> eventTypeArguments = (Map<String,Object>) arguments.get("eventType");
                                    if (eventTypeArguments.containsKey("typeName")) {
                                        cxsEventType.id = (String) eventTypeArguments.get("typeName");
                                        cxsEventType.typeName = (String) eventTypeArguments.get("typeName");
                                    }
                                    cxsEventType.properties = new ArrayList<>();
                                    if (eventTypeArguments.containsKey("properties")) {
                                        List<Map<String, Object>> properties = (List<Map<String, Object>>) eventTypeArguments.get("properties");
                                        for (Map<String, Object> propertyTypeMap : properties) {
                                            CXSPropertyType cxsPropertyType = getPropertyType(propertyTypeMap);
                                            if (cxsPropertyType != null) {
                                                cxsEventType.properties.add(cxsPropertyType);
                                            }
                                        }
                                    }
                                }
                                eventTypes.put(cxsEventType.typeName, cxsEventType);
                                updateGraphQLTypes();
                                if (cxsProviderManager != null) {
                                    cxsProviderManager.refreshProviders();
                                }
                                return cxsEventType;
                            }
                        })
                )
                .field(newFieldDefinition()
                        .name("processEvents")
                        .description("Processes events sent to the Context Server")
                        .argument(newArgument()
                                .name("events")
                                .type(new GraphQLList(getInputTypeFromRegistry("CXS_EventInput"))))
                        .type(GraphQLInt)
                )
                .build();
    }

    private CXSPropertyType getPropertyType(Map<String, Object> propertyTypeMap) {
        if (propertyTypeMap.size() > 1) {
            logger.error("Only one property type is allowed for each property !");
            return null;
        }
        CXSPropertyType propertyType = null;
        if (propertyTypeMap.containsKey("identifier")) {
            propertyType = getIdentifierPropertyType(propertyTypeMap);
        } else if (propertyTypeMap.containsKey("string")) {
            propertyType = getStringPropertyType(propertyTypeMap);
        } else if (propertyTypeMap.containsKey("set")) {
            propertyType = getSetPropertyType(propertyTypeMap);
        }
        return propertyType;
    }

    private CXSPropertyType getSetPropertyType(Map<String, Object> propertyTypeMap) {
        CXSSetPropertyType cxsSetPropertyType = new CXSSetPropertyType();
        Map<String,Object> setPropertyTypeMap = (Map<String,Object>) propertyTypeMap.get("set");
        populateCommonProperties(setPropertyTypeMap, cxsSetPropertyType);
        if (setPropertyTypeMap.containsKey("properties")) {
            List<Map<String,Object>> propertyList = (List<Map<String,Object>>) setPropertyTypeMap.get("properties");
            List<CXSPropertyType> setProperties = new ArrayList<>();
            for (Map<String,Object> setProperty : propertyList) {
                CXSPropertyType subPropertyType = getPropertyType(setProperty);
                if (subPropertyType != null) {
                    setProperties.add(subPropertyType);
                }
            }
            cxsSetPropertyType.properties = setProperties;
        }
        return cxsSetPropertyType;
    }

    private CXSPropertyType getStringPropertyType(Map<String, Object> propertyTypeMap) {
        CXSStringPropertyType cxsStringPropertyType = new CXSStringPropertyType();
        Map<String,Object> stringPropertyTypeMap = (Map<String,Object>) propertyTypeMap.get("string");
        populateCommonProperties(stringPropertyTypeMap, cxsStringPropertyType);
        return cxsStringPropertyType;
    }

    private CXSPropertyType getIdentifierPropertyType(Map<String, Object> propertyTypeMap) {
        CXSIdentifierPropertyType cxsIdentifierPropertyType = new CXSIdentifierPropertyType();
        Map<String,Object> identifierPropertyTypeMap = (Map<String,Object>) propertyTypeMap.get("identifier");
        populateCommonProperties(identifierPropertyTypeMap, cxsIdentifierPropertyType);
        return cxsIdentifierPropertyType;
    }

    private void populateCommonProperties(Map<String, Object> propertyTypeMap, CXSPropertyType cxsPropertyType) {
        if (propertyTypeMap == null || propertyTypeMap.size() == 0) {
            return;
        }
        if (propertyTypeMap.containsKey("id")) {
            cxsPropertyType.id = (String) propertyTypeMap.get("id");
        }
        if (propertyTypeMap.containsKey("name")) {
            cxsPropertyType.name = (String) propertyTypeMap.get("name");
        }
    }


}
