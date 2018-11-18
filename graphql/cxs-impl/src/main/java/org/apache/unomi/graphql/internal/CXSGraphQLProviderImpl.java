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
import org.apache.unomi.graphql.CXSGraphQLProvider;
import org.apache.unomi.graphql.CXSMutation;
import org.apache.unomi.graphql.CXSProviderManager;
import org.apache.unomi.graphql.CXSQuery;
import org.apache.unomi.graphql.builders.CXSEventBuilders;
import org.apache.unomi.graphql.propertytypes.CXSSetPropertyType;
import org.apache.unomi.graphql.types.input.CXSDateFilter;
import org.apache.unomi.graphql.types.input.CXSEventTypeInput;
import org.apache.unomi.graphql.types.input.CXSGeoDistanceInput;
import org.apache.unomi.graphql.types.input.CXSOrderByInput;
import org.apache.unomi.graphql.types.output.CXSEventType;
import org.apache.unomi.graphql.types.output.CXSGeoPoint;
import org.apache.unomi.graphql.types.output.PageInfo;
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

    private Map<String, CXSEventType> eventTypes = new TreeMap<>();

    public CXSGraphQLProviderImpl(GraphQLAnnotationsComponent annotationsComponent) {
        this.annotationsComponent = annotationsComponent;
        container = annotationsComponent.createContainer();
        container.setInputPrefix("");
        container.setInputSuffix("Input");
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
        typeRegistry.clear();
        typeRegistry.put(PageInfo.class.getName(), annotationsComponent.getOutputTypeProcessor().getOutputTypeOrRef(PageInfo.class, container));

        typeRegistry.put("CXS_GeoPoint", annotationsComponent.getOutputTypeProcessor().getOutputTypeOrRef(CXSGeoPoint.class, container));
        typeRegistry.put("CXS_SetPropertyType",annotationsComponent.getOutputTypeProcessor().getOutputTypeOrRef(CXSSetPropertyType.class, container));
        typeRegistry.put("CXS_EventType", annotationsComponent.getOutputTypeProcessor().getOutputTypeOrRef(CXSEventType.class, container));

        typeRegistry.put("CXS_GeoDistanceInput", annotationsComponent.getInputTypeProcessor().getInputTypeOrRef(CXSGeoDistanceInput.class, container));
        typeRegistry.put("CXS_DateFilterInput", annotationsComponent.getInputTypeProcessor().getInputTypeOrRef(CXSDateFilter.class, container));
        typeRegistry.put("CXS_EventTypeInput", annotationsComponent.getInputTypeProcessor().getInputTypeOrRef(CXSEventTypeInput.class, container));
        typeRegistry.put("CXS_OrderByInput", annotationsComponent.getInputTypeProcessor().getInputTypeOrRef(CXSOrderByInput.class, container));

        cxsEventBuilders.updateTypes();

        typeRegistry.put("CXS_Query", annotationsComponent.getOutputTypeProcessor().getOutputTypeOrRef(CXSQuery.class, container));
        typeRegistry.put("CXS_Mutation", annotationsComponent.getOutputTypeProcessor().getOutputTypeOrRef(CXSMutation.class, container));

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
        final CXSGraphQLProvider cxsGraphQLProvider = this;
        fieldDefinitions.add(newFieldDefinition()
                .type(getOutputTypeFromRegistry("CXS_Query"))
                .name("cxs")
                .description("Root field for all CXS queries")
                .dataFetcher(new DataFetcher<CXSGraphQLProvider>() {
                    public CXSGraphQLProvider get(DataFetchingEnvironment environment) {
                        return cxsGraphQLProvider;
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
                        .type(new GraphQLList(getOutputTypeFromRegistry("CXS_EventType")))
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
                                .type(getInputTypeFromRegistry("CXS_OrderByInput"))
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
