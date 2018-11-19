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
package org.apache.unomi.graphql.builders;

import graphql.annotations.processor.GraphQLAnnotationsComponent;
import graphql.annotations.processor.ProcessingElementsContainer;
import graphql.schema.*;
import org.apache.unomi.graphql.propertytypes.*;
import org.apache.unomi.graphql.types.input.CDPEventInput;
import org.apache.unomi.graphql.types.input.CDPEventOccurrenceFilterInput;
import org.apache.unomi.graphql.types.output.CDPEvent;
import org.apache.unomi.graphql.types.output.CDPEventProperties;
import org.apache.unomi.graphql.types.output.CDPEventType;
import org.apache.unomi.graphql.types.output.PageInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static graphql.Scalars.*;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLInputObjectField.newInputObjectField;
import static graphql.schema.GraphQLInputObjectType.newInputObject;
import static graphql.schema.GraphQLObjectType.newObject;

public class CDPEventBuilders implements CDPBuilder {

    private GraphQLAnnotationsComponent annotationsComponent;
    private ProcessingElementsContainer container;
    private Map<String, CDPEventType> eventTypes;
    private Map<String,GraphQLType> typeRegistry;

    public CDPEventBuilders(GraphQLAnnotationsComponent annotationsComponent,
                            ProcessingElementsContainer container,
                            Map<String, CDPEventType> eventTypes) {
        this.annotationsComponent = annotationsComponent;
        this.container = container;
        this.eventTypes = eventTypes;
        this.typeRegistry = container.getTypeRegistry();
    }

    @Override
    public void updateTypes() {
        Map<String,GraphQLType> typeRegistry = container.getTypeRegistry();
        typeRegistry.put("CDP_EventInput", buildCDPEventInputType());
        typeRegistry.put("CDP_EventOccurrenceFilterInput", annotationsComponent.getInputTypeProcessor().getInputTypeOrRef(CDPEventOccurrenceFilterInput.class, container));
        typeRegistry.put("CDP_EventPropertiesFilterInput", buildCDPEventPropertiesFilterInput());
        typeRegistry.put("CDP_EventFilterInput", buildCDPEventFilterInputType());
        typeRegistry.put("CDP_EventProperties", buildCDPEventPropertiesOutputType());
        typeRegistry.put("CDP_Event", buildCDPEventOutputType());
        typeRegistry.put("CDP_EventEdge", buildCDPEventEdgeOutputType());
        typeRegistry.put("CDP_EventConnection", buildCDPEventConnectionOutputType());
    }

    private GraphQLOutputType buildCDPEventEdgeOutputType() {
        return newObject()
                .name("CDP_EventEdge")
                .description("The Relay edge type for the CDP_Event output type")
                .field(newFieldDefinition()
                        .name("node")
                        .type((GraphQLOutputType) typeRegistry.get("CDP_Event"))
                )
                .field(newFieldDefinition()
                        .name("cursor")
                        .type(GraphQLString)
                )
                .build();
    }

    private GraphQLOutputType buildCDPEventConnectionOutputType() {
        return newObject()
                .name("CDP_EventConnection")
                .description("The Relay connection type for the CDP_Event output type")
                .field(newFieldDefinition()
                        .name("edges")
                        .type(new GraphQLList(typeRegistry.get("CDP_EventEdge")))
                )
                .field(newFieldDefinition()
                        .name("pageInfo")
                        .type(new GraphQLList(typeRegistry.get(PageInfo.class.getName())))
                )
                .build();
    }

    private GraphQLInputType buildCDPEventPropertiesFilterInput() {
        GraphQLInputObjectType.Builder cdpEventPropertiesFilterInput = newInputObject()
                .name("CDP_EventPropertiesFilterInput")
                .description("Filter conditions for each event types and built-in properties");

        generateEventPropertiesFilters(cdpEventPropertiesFilterInput);
        generateEventTypesFilters(cdpEventPropertiesFilterInput);

        return cdpEventPropertiesFilterInput.build();
    }


    private void generateEventPropertiesFilters(GraphQLInputObjectType.Builder cdpEventPropertiesFilterInput) {
        addIdentityFilters("id", cdpEventPropertiesFilterInput);
        addIdentityFilters("sourceId", cdpEventPropertiesFilterInput);
        addIdentityFilters("clientId", cdpEventPropertiesFilterInput);
        addIdentityFilters("profileId", cdpEventPropertiesFilterInput);
        addDistanceFilters("location", cdpEventPropertiesFilterInput);
        addDateFilters("timestamp", cdpEventPropertiesFilterInput);
    }

    private void generateEventTypesFilters(GraphQLInputObjectType.Builder cdpEventPropertiesFilterInput) {
        for (Map.Entry<String, CDPEventType> eventTypeEntry : eventTypes.entrySet()) {
            addSetFilters(eventTypeEntry.getKey(), eventTypeEntry.getValue().getProperties(), cdpEventPropertiesFilterInput);
        }
    }

    private void addSetFilters(String eventTypeName, List<CDPPropertyType> properties, GraphQLInputObjectType.Builder inputTypeBuilder) {
        GraphQLInputObjectType.Builder eventTypeFilterInput = newInputObject()
                .name(eventTypeName + "FilterInput")
                .description("Auto-generated filter input type for event type " + eventTypeName);

        for (CDPPropertyType cdpPropertyType : properties) {
            if (cdpPropertyType instanceof CDPIdentifierPropertyType) {
                addIdentityFilters(cdpPropertyType.getName(), eventTypeFilterInput);
            } else if (cdpPropertyType instanceof CDPStringPropertyType) {
                addStringFilters(cdpPropertyType.getName(), eventTypeFilterInput);
            } else if (cdpPropertyType instanceof CDPBooleanPropertyType) {
                addBooleanFilters(cdpPropertyType.getName(), eventTypeFilterInput);
            } else if (cdpPropertyType instanceof CDPIntPropertyType) {
                addIntegerFilters(cdpPropertyType.getName(), eventTypeFilterInput);
            } else if (cdpPropertyType instanceof CDPFloatPropertyType) {
                addFloatFilters(cdpPropertyType.getName(), eventTypeFilterInput);
            } else if (cdpPropertyType instanceof CDPGeoPointPropertyType) {
                addDistanceFilters(cdpPropertyType.getName(), eventTypeFilterInput);
            } else if (cdpPropertyType instanceof CDPDatePropertyType) {
                addDateFilters(cdpPropertyType.getName(), eventTypeFilterInput);
            } else if (cdpPropertyType instanceof CDPSetPropertyType) {
                addSetFilters(cdpPropertyType.getName(), ((CDPSetPropertyType) cdpPropertyType).getProperties(), eventTypeFilterInput);
            }
        }

        typeRegistry.put(eventTypeName + "FilterInput", eventTypeFilterInput.build());

        inputTypeBuilder.field(newInputObjectField()
                .name(eventTypeName)
                .type((GraphQLInputType) typeRegistry.get(eventTypeName + "FilterInput"))
        );

    }

    private void addIdentityFilters(String propertyName, GraphQLInputObjectType.Builder inputTypeBuilder) {
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_equals")
                .type(GraphQLString)
        );
    }

    private void addStringFilters(String propertyName, GraphQLInputObjectType.Builder inputTypeBuilder) {
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_equals")
                .type(GraphQLString)
        );
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_regexp")
                .type(GraphQLString)
        );
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_startsWith")
                .type(GraphQLString)
        );
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_contains")
                .type(new GraphQLList(GraphQLString))
        );
    }

    private void addBooleanFilters(String propertyName, GraphQLInputObjectType.Builder inputTypeBuilder) {
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_equals")
                .type(GraphQLBoolean)
        );
    }

    private void addIntegerFilters(String propertyName, GraphQLInputObjectType.Builder inputTypeBuilder) {
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_equals")
                .type(GraphQLInt)
        );
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_gt")
                .type(GraphQLInt)
        );
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_gte")
                .type(GraphQLInt)
        );
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_lt")
                .type(GraphQLInt)
        );
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_lte")
                .type(GraphQLInt)
        );
    }

    private void addFloatFilters(String propertyName, GraphQLInputObjectType.Builder inputTypeBuilder) {
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_equals")
                .type(GraphQLFloat)
        );
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_gt")
                .type(GraphQLFloat)
        );
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_gte")
                .type(GraphQLFloat)
        );
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_lt")
                .type(GraphQLFloat)
        );
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_lte")
                .type(GraphQLFloat)
        );
    }

    private void addDistanceFilters(String propertyName, GraphQLInputObjectType.Builder inputTypeBuilder) {
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_distance")
                .type((GraphQLInputType) typeRegistry.get("CDP_GeoDistanceInput"))
        );
    }

    private void addDateFilters(String propertyName, GraphQLInputObjectType.Builder inputTypeBuilder) {
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_between")
                .type((GraphQLInputType) typeRegistry.get("CDP_DateFilterInput"))
        );
    }

    private GraphQLInputType buildCDPEventFilterInputType() {
        GraphQLInputObjectType.Builder cdpEventFilterInputType = newInputObject()
                .name("CDP_EventFilterInput")
                .description("Filter conditions for each event types and built-in properties")
                .field(newInputObjectField()
                        .name("and")
                        .type(new GraphQLList(new GraphQLTypeReference("CDP_EventFilterInput")))
                )
                .field(newInputObjectField()
                        .name("or")
                        .type(new GraphQLList(new GraphQLTypeReference("CDP_EventFilterInput")))
                )
                .field(newInputObjectField()
                        .name("properties")
                        .type((GraphQLInputType) typeRegistry.get("CDP_EventPropertiesFilterInput"))
                )
                .field(newInputObjectField()
                        .name("properties_or")
                        .type((GraphQLInputType) typeRegistry.get("CDP_EventPropertiesFilterInput"))
                )
                .field(newInputObjectField()
                        .name("eventOccurrence")
                        .type((GraphQLInputType) typeRegistry.get("CDP_EventOccurrenceFilterInput"))
                );
        return cdpEventFilterInputType.build();
    }

    private GraphQLInputType buildCDPEventInputType() {
        GraphQLInputObjectType.Builder cdpEventInputType = CDPBuildersUtils.getInputBuilderFromAnnotatedClass(annotationsComponent, container, "CDP_EventInput", CDPEventInput.class)
                .description("The event input object to send events to the Context Server");

        for (Map.Entry<String, CDPEventType> cdpEventTypeEntry : eventTypes.entrySet()) {
            CDPEventType cdpEventType = cdpEventTypeEntry.getValue();
            cdpEventInputType.field(newInputObjectField()
                    .name(cdpEventTypeEntry.getKey())
                    .type(buildCDPEventTypeInputProperty(cdpEventType.getTypeName(), cdpEventType.getProperties()))
            );
        }

        return cdpEventInputType.build();

    }

    private GraphQLInputType buildCDPEventTypeInputProperty(String typeName, List<CDPPropertyType> propertyTypes) {
        String eventTypeName = typeName.substring(0, 1).toUpperCase() + typeName.substring(1) + "EventTypeInput";
        GraphQLInputObjectType.Builder eventInputType = newInputObject()
                .name(eventTypeName)
                .description("Event type object for event type " + typeName);

        for (CDPPropertyType cdpEventPropertyType : propertyTypes) {
            GraphQLInputType eventPropertyInputType = null;
            if (cdpEventPropertyType instanceof CDPIdentifierPropertyType) {
                eventPropertyInputType = GraphQLID;
            } else if (cdpEventPropertyType instanceof CDPStringPropertyType) {
                eventPropertyInputType = GraphQLString;
            } else if (cdpEventPropertyType instanceof CDPIntPropertyType) {
                eventPropertyInputType = GraphQLInt;
            } else if (cdpEventPropertyType instanceof CDPFloatPropertyType) {
                eventPropertyInputType = GraphQLFloat;
            } else if (cdpEventPropertyType instanceof CDPBooleanPropertyType) {
                eventPropertyInputType = GraphQLBoolean;
            } else if (cdpEventPropertyType instanceof CDPDatePropertyType) {
                eventPropertyInputType = GraphQLString;
            } else if (cdpEventPropertyType instanceof CDPGeoPointPropertyType) {
                eventPropertyInputType = (GraphQLInputType) typeRegistry.get("CDP_GeoPoint");
            } else if (cdpEventPropertyType instanceof CDPSetPropertyType) {
                eventPropertyInputType = buildCDPEventTypeInputProperty(cdpEventPropertyType.getName(), ((CDPSetPropertyType)cdpEventPropertyType).getProperties());
            }
            eventInputType
                    .field(newInputObjectField()
                            .type(eventPropertyInputType)
                            .name(cdpEventPropertyType.getName())
                    );
        }

        return eventInputType.build();
    }

    private GraphQLOutputType buildCDPEventOutputType() {
        return newObject()
                .name("CDP_Event")
                .description("An event is generated by user interacting with the Context Server")
                .field(newFieldDefinition()
                        .type(GraphQLID)
                        .name("id")
                        .description("A unique identifier for the event")
                        .dataFetcher(new DataFetcher() {
                            public Object get(DataFetchingEnvironment environment) {
                                CDPEvent cdpEvent = environment.getSource();
                                return cdpEvent.getId();
                            }
                        })
                )
                .field(newFieldDefinition()
                        .type(GraphQLString)
                        .name("eventType")
                        .description("An identifier for the event type")
                        .dataFetcher(new DataFetcher() {
                            public Object get(DataFetchingEnvironment environment) {
                                CDPEvent cdpEvent = environment.getSource();
                                return cdpEvent.getEventType();
                            }
                        })
                )
                .field(newFieldDefinition()
                        .type(GraphQLLong)
                        .name("timestamp")
                        .description("The difference, measured in milliseconds, between the current time and midnight, January 1, 1970 UTC.")
                        .dataFetcher(new DataFetcher() {
                            public Object get(DataFetchingEnvironment environment) {
                                CDPEvent cdpEvent = environment.getSource();
                                return cdpEvent.getTimeStamp();
                            }
                        }))
                .field(newFieldDefinition()
                        .type(GraphQLString)
                        .name("subject")
                        .description("The entity that has fired the event (using the profile)")
                        .dataFetcher(new DataFetcher() {
                            public Object get(DataFetchingEnvironment environment) {
                                CDPEvent cdpEvent = environment.getSource();
                                return cdpEvent.getSubject();
                            }
                        }))
                .field(newFieldDefinition()
                        .type(GraphQLString)
                        .name("object")
                        .description("The object on which the event was fired.")
                        .dataFetcher(new DataFetcher() {
                            public Object get(DataFetchingEnvironment environment) {
                                CDPEvent cdpEvent = environment.getSource();
                                return cdpEvent.getObject();
                            }
                        })
                )
                .field(newFieldDefinition()
                        .type((GraphQLOutputType) typeRegistry.get("CDP_GeoPoint"))
                        .name("location")
                        .description("The geo-point location where the event was fired.")
                        .dataFetcher(new DataFetcher() {
                            public Object get(DataFetchingEnvironment environment) {
                                CDPEvent cdpEvent = environment.getSource();
                                return cdpEvent.getLocation();
                            }
                        })
                )
                .field(newFieldDefinition()
                        .type(new GraphQLList(typeRegistry.get("CDP_EventProperties")))
                        .name("properties")
                        .description("Generic properties for the event")
                        .dataFetcher(new DataFetcher() {
                            public Object get(DataFetchingEnvironment environment) {
                                CDPEvent cdpEvent = environment.getSource();
                                return new ArrayList<Map.Entry<Object,Object>>(cdpEvent.getProperties().getProperties().entrySet());
                            }
                        })
                )
                .build();
    }

    private GraphQLOutputType buildCDPEventPropertiesOutputType() {
        GraphQLObjectType.Builder eventPropertiesOutputType = CDPBuildersUtils.getOutputBuilderFromAnnotatedClass(annotationsComponent, container, "CDP_EventProperties", CDPEventProperties.class)
                .description("All possible properties of an event");

        for (Map.Entry<String, CDPEventType> cdpEventTypeEntry : eventTypes.entrySet()) {
            CDPEventType cdpEventType = cdpEventTypeEntry.getValue();
            eventPropertiesOutputType
                    .field(newFieldDefinition()
                            .type(buildEventOutputType(cdpEventType.getTypeName(), cdpEventType.getProperties()))
                            .name(cdpEventTypeEntry.getKey())
                    );
        }

        return eventPropertiesOutputType.build();
    }

    private GraphQLOutputType buildEventOutputType(String typeName, List<CDPPropertyType> propertyTypes) {
        String eventTypeName = typeName.substring(0, 1).toUpperCase() + typeName.substring(1) + "EventType";
        GraphQLObjectType.Builder eventOutputType = newObject()
                .name(eventTypeName)
                .description("Event type object for event type " + typeName);

        for (CDPPropertyType cdpEventPropertyType : propertyTypes) {
            GraphQLOutputType eventPropertyOutputType = null;
            if (cdpEventPropertyType instanceof CDPIdentifierPropertyType) {
                eventPropertyOutputType = GraphQLID;
            } else if (cdpEventPropertyType instanceof CDPStringPropertyType) {
                eventPropertyOutputType = GraphQLString;
            } else if (cdpEventPropertyType instanceof CDPIntPropertyType) {
                eventPropertyOutputType = GraphQLInt;
            } else if (cdpEventPropertyType instanceof CDPFloatPropertyType) {
                eventPropertyOutputType = GraphQLFloat;
            } else if (cdpEventPropertyType instanceof CDPBooleanPropertyType) {
                eventPropertyOutputType = GraphQLBoolean;
            } else if (cdpEventPropertyType instanceof CDPDatePropertyType) {
                eventPropertyOutputType = GraphQLString;
            } else if (cdpEventPropertyType instanceof CDPGeoPointPropertyType) {
                eventPropertyOutputType = (GraphQLOutputType) typeRegistry.get("CDP_GeoPoint");
            } else if (cdpEventPropertyType instanceof CDPSetPropertyType) {
                eventPropertyOutputType = buildEventOutputType(cdpEventPropertyType.getName(), ((CDPSetPropertyType)cdpEventPropertyType).getProperties());
            }
            eventOutputType
                    .field(newFieldDefinition()
                            .type(eventPropertyOutputType)
                            .name(cdpEventPropertyType.getName())
                    );
        }


        return eventOutputType.build();
    }

}
