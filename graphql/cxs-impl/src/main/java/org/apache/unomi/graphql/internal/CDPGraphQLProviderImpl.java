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
import org.apache.unomi.graphql.CDPGraphQLProvider;
import org.apache.unomi.graphql.CDPMutation;
import org.apache.unomi.graphql.CDPProviderManager;
import org.apache.unomi.graphql.CDPQuery;
import org.apache.unomi.graphql.builders.CDPEventBuilders;
import org.apache.unomi.graphql.propertytypes.CDPSetPropertyType;
import org.apache.unomi.graphql.types.input.CDPDateFilter;
import org.apache.unomi.graphql.types.input.CDPEventTypeInput;
import org.apache.unomi.graphql.types.input.CDPGeoDistanceInput;
import org.apache.unomi.graphql.types.input.CDPOrderByInput;
import org.apache.unomi.graphql.types.output.CDPEventType;
import org.apache.unomi.graphql.types.output.CDPGeoPoint;
import org.apache.unomi.graphql.types.output.PageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static graphql.Scalars.GraphQLInt;
import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;

public class CDPGraphQLProviderImpl implements CDPGraphQLProvider, GraphQLQueryProvider, GraphQLTypesProvider, GraphQLMutationProvider {

    private static final Logger logger = LoggerFactory.getLogger(CDPGraphQLProviderImpl.class.getName());

    private CDPProviderManager cdpProviderManager;
    private GraphQLAnnotationsComponent annotationsComponent;
    private ProcessingElementsContainer container;
    private CDPEventBuilders cdpEventBuilders;
    private Map<String,GraphQLType> typeRegistry;

    private Map<String, CDPEventType> eventTypes = new TreeMap<>();

    public CDPGraphQLProviderImpl(GraphQLAnnotationsComponent annotationsComponent) {
        this.annotationsComponent = annotationsComponent;
        container = annotationsComponent.createContainer();
        container.setInputPrefix("");
        container.setInputSuffix("Input");
        typeRegistry = container.getTypeRegistry();
        cdpEventBuilders = new CDPEventBuilders(annotationsComponent, container, eventTypes);
        updateGraphQLTypes();
    }

    @Override
    public Map<String, CDPEventType> getEventTypes() {
        return eventTypes;
    }

    public CDPProviderManager getCdpProviderManager() {
        return cdpProviderManager;
    }

    public void updateGraphQLTypes() {
        typeRegistry.clear();
        typeRegistry.put(PageInfo.class.getName(), annotationsComponent.getOutputTypeProcessor().getOutputTypeOrRef(PageInfo.class, container));

        typeRegistry.put("CDP_GeoPoint", annotationsComponent.getOutputTypeProcessor().getOutputTypeOrRef(CDPGeoPoint.class, container));
        typeRegistry.put("CDP_SetPropertyType",annotationsComponent.getOutputTypeProcessor().getOutputTypeOrRef(CDPSetPropertyType.class, container));
        typeRegistry.put("CDP_EventType", annotationsComponent.getOutputTypeProcessor().getOutputTypeOrRef(CDPEventType.class, container));

        typeRegistry.put("CDP_GeoDistanceInput", annotationsComponent.getInputTypeProcessor().getInputTypeOrRef(CDPGeoDistanceInput.class, container));
        typeRegistry.put("CDP_DateFilterInput", annotationsComponent.getInputTypeProcessor().getInputTypeOrRef(CDPDateFilter.class, container));
        typeRegistry.put("CDP_EventTypeInput", annotationsComponent.getInputTypeProcessor().getInputTypeOrRef(CDPEventTypeInput.class, container));
        typeRegistry.put("CDP_OrderByInput", annotationsComponent.getInputTypeProcessor().getInputTypeOrRef(CDPOrderByInput.class, container));

        cdpEventBuilders.updateTypes();

        typeRegistry.put("CDP_Query", annotationsComponent.getOutputTypeProcessor().getOutputTypeOrRef(CDPQuery.class, container));
        typeRegistry.put("CDP_Mutation", annotationsComponent.getOutputTypeProcessor().getOutputTypeOrRef(CDPMutation.class, container));

    }


    private GraphQLOutputType getOutputTypeFromRegistry(String typeName) {
        return (GraphQLOutputType) typeRegistry.get(typeName);
    }

    private GraphQLInputObjectType getInputTypeFromRegistry(String typeName) {
        return (GraphQLInputObjectType) typeRegistry.get(typeName);
    }

    public void setCdpProviderManager(CDPProviderManager cdpProviderManager) {
        this.cdpProviderManager = cdpProviderManager;
    }

    @Override
    public Collection<GraphQLFieldDefinition> getQueries() {
        List<GraphQLFieldDefinition> fieldDefinitions = new ArrayList<GraphQLFieldDefinition>();
        final CDPGraphQLProvider cdpGraphQLProvider = this;
        fieldDefinitions.add(newFieldDefinition()
                .type(getOutputTypeFromRegistry("CDP_Query"))
                .name("cdp")
                .description("Root field for all CDP queries")
                .dataFetcher(new DataFetcher<CDPGraphQLProvider>() {
                    public CDPGraphQLProvider get(DataFetchingEnvironment environment) {
                        return cdpGraphQLProvider;
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
        final CDPGraphQLProvider cdpGraphQLProvider = this;
        fieldDefinitions.add(newFieldDefinition()
                .type(getOutputTypeFromRegistry("CDP_Mutation"))
                .name("cdp")
                .description("Root field for all CDP mutations")
                .dataFetcher(new DataFetcher<CDPGraphQLProvider>() {
                    @Override
                    public CDPGraphQLProvider get(DataFetchingEnvironment environment) {
                        return cdpGraphQLProvider;
                    }
                }).build());
        return fieldDefinitions;
    }

    private GraphQLOutputType buildCDPQueryOutputType() {
        return newObject()
                .name("CDP_Query")
                .description("Root CDP query type")
                .field(newFieldDefinition()
                        .type(new GraphQLList(getOutputTypeFromRegistry("CDP_EventType")))
                        .name("getEventTypes")
                        .description("Retrieves the list of all the declared CDP event types in the Apache Unomi server")
                )
                .field(newFieldDefinition()
                        .type(new GraphQLList(getOutputTypeFromRegistry("CDP_Event")))
                        .name("getEvent")
                        .description("Retrieves a specific event")
                )
                .field(newFieldDefinition()
                        .type(new GraphQLList(getOutputTypeFromRegistry("CDP_EventConnection")))
                        .name("findEvents")
                        .argument(newArgument()
                                .name("filter")
                                .type(getInputTypeFromRegistry("CDP_EventFilterInput"))
                        )
                        .argument(newArgument()
                                .name("orderBy")
                                .type(getInputTypeFromRegistry("CDP_OrderByInput"))
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
                        .type(getOutputTypeFromRegistry("CDP_Segment"))
                        .name("getSegment")
                        .argument(newArgument()
                                .name("segmentId")
                                .type(GraphQLString)
                                .description("Unique identifier for the segment")
                        )
                )
                /*
                .field(newFieldDefinition()
                        .type(new GraphQLList(registeredOutputTypes.get("CDP_ProfileConnection")))
                        .name("findProfiles")
                        .argument(newArgument()
                                .name("filter")
                                .type(registeredInputTypes.get("CDP_ProfileFilterInput"))
                        )
                        .argument(newArgument()
                                .name("orderBy")
                                .type(registeredInputTypes.get(CDPOrderByInput.class.getName()))
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
