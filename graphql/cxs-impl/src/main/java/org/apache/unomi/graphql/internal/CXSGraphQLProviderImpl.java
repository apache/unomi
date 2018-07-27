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
import org.apache.unomi.graphql.propertytypes.CXSSetPropertyType;
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
                /*
                .dataFetcher(new DataFetcher() {
                    public Object get(DataFetchingEnvironment environment) {
                        Map<String,Object> map = environment.getContext();
                        return map.keySet();
                    }
                })*/.build());
        return fieldDefinitions;
    }

    @Override
    public Collection<GraphQLType> getTypes() {
        return new ArrayList<>();
    }

    @Override
    public Collection<GraphQLFieldDefinition> getMutations() {
        List<GraphQLFieldDefinition> fieldDefinitions = new ArrayList<GraphQLFieldDefinition>();
        final CXSGraphQLProvider cxsGraphQLProvider = this;
        fieldDefinitions.add(newFieldDefinition()
                .type(getOutputTypeFromRegistry("CXS_Mutation"))
                .name("cxs")
                .description("Root field for all CXS mutations")
                .dataFetcher(new DataFetcher<CXSGraphQLProvider>() {
                    @Override
                    public CXSGraphQLProvider get(DataFetchingEnvironment environment) {
                        return cxsGraphQLProvider;
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

}
