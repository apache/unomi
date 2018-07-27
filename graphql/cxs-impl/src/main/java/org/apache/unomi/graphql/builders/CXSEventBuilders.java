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
import org.apache.unomi.graphql.*;
import org.apache.unomi.graphql.propertytypes.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static graphql.Scalars.*;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLInputObjectField.newInputObjectField;
import static graphql.schema.GraphQLInputObjectType.newInputObject;
import static graphql.schema.GraphQLObjectType.newObject;

public class CXSEventBuilders implements CXSBuilder {

    private GraphQLAnnotationsComponent annotationsComponent;
    private ProcessingElementsContainer container;
    private Map<String,CXSEventType> eventTypes;
    private Map<String,GraphQLType> typeRegistry;

    public CXSEventBuilders(GraphQLAnnotationsComponent annotationsComponent,
                            ProcessingElementsContainer container,
                            Map<String, CXSEventType> eventTypes) {
        this.annotationsComponent = annotationsComponent;
        this.container = container;
        this.eventTypes = eventTypes;
        this.typeRegistry = container.getTypeRegistry();
    }

    @Override
    public void updateTypes() {
        Map<String,GraphQLType> typeRegistry = container.getTypeRegistry();
        typeRegistry.put("CXS_EventInput", buildCXSEventInputType());
        typeRegistry.put("CXS_EventOccurrenceFilterInput", annotationsComponent.getInputTypeProcessor().getInputTypeOrRef(CXSEventOccurrenceFilterInput.class, container));
        typeRegistry.put("CXS_EventPropertiesFilterInput", buildCXSEventPropertiesFilterInput());
        typeRegistry.put("CXS_EventFilterInput", buildCXSEventFilterInputType());
        typeRegistry.put("CXS_EventProperties", buildCXSEventPropertiesOutputType());
        typeRegistry.put("CXS_Event", buildCXSEventOutputType());
        typeRegistry.put("CXS_EventEdge", buildCXSEventEdgeOutputType());
        typeRegistry.put("CXS_EventConnection", buildCXSEventConnectionOutputType());
    }

    private GraphQLOutputType buildCXSEventEdgeOutputType() {
        return newObject()
                .name("CXS_EventEdge")
                .description("The Relay edge type for the CXS_Event output type")
                .field(newFieldDefinition()
                        .name("node")
                        .type((GraphQLOutputType) typeRegistry.get("CXS_Event"))
                )
                .field(newFieldDefinition()
                        .name("cursor")
                        .type(GraphQLString)
                )
                .build();
    }

    private GraphQLOutputType buildCXSEventConnectionOutputType() {
        return newObject()
                .name("CXS_EventConnection")
                .description("The Relay connection type for the CXS_Event output type")
                .field(newFieldDefinition()
                        .name("edges")
                        .type(new GraphQLList(typeRegistry.get("CXS_EventEdge")))
                )
                .field(newFieldDefinition()
                        .name("pageInfo")
                        .type(new GraphQLList(typeRegistry.get(PageInfo.class.getName())))
                )
                .build();
    }

    private GraphQLInputType buildCXSEventPropertiesFilterInput() {
        GraphQLInputObjectType.Builder cxsEventPropertiesFilterInput = newInputObject()
                .name("CXS_EventPropertiesFilterInput")
                .description("Filter conditions for each event types and built-in properties");

        generateEventPropertiesFilters(cxsEventPropertiesFilterInput);
        generateEventTypesFilters(cxsEventPropertiesFilterInput);

        return cxsEventPropertiesFilterInput.build();
    }


    private void generateEventPropertiesFilters(GraphQLInputObjectType.Builder cxsEventPropertiesFilterInput) {
        addIdentityFilters("id", cxsEventPropertiesFilterInput);
        addIdentityFilters("sourceId", cxsEventPropertiesFilterInput);
        addIdentityFilters("clientId", cxsEventPropertiesFilterInput);
        addIdentityFilters("profileId", cxsEventPropertiesFilterInput);
        addDistanceFilters("location", cxsEventPropertiesFilterInput);
        addDateFilters("timestamp", cxsEventPropertiesFilterInput);
    }

    private void generateEventTypesFilters(GraphQLInputObjectType.Builder cxsEventPropertiesFilterInput) {
        for (Map.Entry<String,CXSEventType> eventTypeEntry : eventTypes.entrySet()) {
            addSetFilters(eventTypeEntry.getKey(), eventTypeEntry.getValue().getProperties(), cxsEventPropertiesFilterInput);
        }
    }

    private void addSetFilters(String eventTypeName, List<CXSPropertyType> properties, GraphQLInputObjectType.Builder inputTypeBuilder) {
        GraphQLInputObjectType.Builder eventTypeFilterInput = newInputObject()
                .name(eventTypeName + "FilterInput")
                .description("Auto-generated filter input type for event type " + eventTypeName);

        for (CXSPropertyType cxsPropertyType : properties) {
            if (cxsPropertyType instanceof CXSIdentifierPropertyType) {
                addIdentityFilters(cxsPropertyType.getName(), eventTypeFilterInput);
            } else if (cxsPropertyType instanceof CXSStringPropertyType) {
                addStringFilters(cxsPropertyType.getName(), eventTypeFilterInput);
            } else if (cxsPropertyType instanceof CXSBooleanPropertyType) {
                addBooleanFilters(cxsPropertyType.getName(), eventTypeFilterInput);
            } else if (cxsPropertyType instanceof CXSIntPropertyType) {
                addIntegerFilters(cxsPropertyType.getName(), eventTypeFilterInput);
            } else if (cxsPropertyType instanceof CXSFloatPropertyType) {
                addFloatFilters(cxsPropertyType.getName(), eventTypeFilterInput);
            } else if (cxsPropertyType instanceof CXSGeoPointPropertyType) {
                addDistanceFilters(cxsPropertyType.getName(), eventTypeFilterInput);
            } else if (cxsPropertyType instanceof CXSDatePropertyType) {
                addDateFilters(cxsPropertyType.getName(), eventTypeFilterInput);
            } else if (cxsPropertyType instanceof CXSSetPropertyType) {
                addSetFilters(cxsPropertyType.getName(), ((CXSSetPropertyType) cxsPropertyType).getProperties(), eventTypeFilterInput);
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
                .type((GraphQLInputType) typeRegistry.get("CXS_GeoDistanceInput"))
        );
    }

    private void addDateFilters(String propertyName, GraphQLInputObjectType.Builder inputTypeBuilder) {
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_between")
                .type((GraphQLInputType) typeRegistry.get("CXS_DateFilterInput"))
        );
    }

    private GraphQLInputType buildCXSEventFilterInputType() {
        GraphQLInputObjectType.Builder cxsEventFilterInputType = newInputObject()
                .name("CXS_EventFilterInput")
                .description("Filter conditions for each event types and built-in properties")
                .field(newInputObjectField()
                        .name("and")
                        .type(new GraphQLList(new GraphQLTypeReference("CXS_EventFilterInput")))
                )
                .field(newInputObjectField()
                        .name("or")
                        .type(new GraphQLList(new GraphQLTypeReference("CXS_EventFilterInput")))
                )
                .field(newInputObjectField()
                        .name("properties")
                        .type((GraphQLInputType) typeRegistry.get("CXS_EventPropertiesFilterInput"))
                )
                .field(newInputObjectField()
                        .name("properties_or")
                        .type((GraphQLInputType) typeRegistry.get("CXS_EventPropertiesFilterInput"))
                )
                .field(newInputObjectField()
                        .name("eventOccurrence")
                        .type((GraphQLInputType) typeRegistry.get("CXS_EventOccurrenceFilterInput"))
                );
        return cxsEventFilterInputType.build();
    }

    private GraphQLInputType buildCXSEventInputType() {
        GraphQLInputObjectType.Builder cxsEventInputType = CXSBuildersUtils.getInputBuilderFromAnnotatedClass(annotationsComponent, container, "CXS_EventInput", CXSEventInput.class)
                .description("The event input object to send events to the Context Server");

        for (Map.Entry<String,CXSEventType> cxsEventTypeEntry : eventTypes.entrySet()) {
            CXSEventType cxsEventType = cxsEventTypeEntry.getValue();
            cxsEventInputType.field(newInputObjectField()
                    .name(cxsEventTypeEntry.getKey())
                    .type(buildCXSEventTypeInputProperty(cxsEventType.getTypeName(), cxsEventType.getProperties()))
            );
        }

        return cxsEventInputType.build();

    }

    private GraphQLInputType buildCXSEventTypeInputProperty(String typeName, List<CXSPropertyType> propertyTypes) {
        String eventTypeName = typeName.substring(0, 1).toUpperCase() + typeName.substring(1) + "EventTypeInput";
        GraphQLInputObjectType.Builder eventInputType = newInputObject()
                .name(eventTypeName)
                .description("Event type object for event type " + typeName);

        for (CXSPropertyType cxsEventPropertyType : propertyTypes) {
            GraphQLInputType eventPropertyInputType = null;
            if (cxsEventPropertyType instanceof CXSIdentifierPropertyType) {
                eventPropertyInputType = GraphQLID;
            } else if (cxsEventPropertyType instanceof CXSStringPropertyType) {
                eventPropertyInputType = GraphQLString;
            } else if (cxsEventPropertyType instanceof CXSIntPropertyType) {
                eventPropertyInputType = GraphQLInt;
            } else if (cxsEventPropertyType instanceof CXSFloatPropertyType) {
                eventPropertyInputType = GraphQLFloat;
            } else if (cxsEventPropertyType instanceof CXSBooleanPropertyType) {
                eventPropertyInputType = GraphQLBoolean;
            } else if (cxsEventPropertyType instanceof CXSDatePropertyType) {
                eventPropertyInputType = GraphQLString;
            } else if (cxsEventPropertyType instanceof CXSGeoPointPropertyType) {
                eventPropertyInputType = (GraphQLInputType) typeRegistry.get("CXS_GeoPoint");
            } else if (cxsEventPropertyType instanceof CXSSetPropertyType) {
                eventPropertyInputType = buildCXSEventTypeInputProperty(cxsEventPropertyType.getName(), ((CXSSetPropertyType)cxsEventPropertyType).getProperties());
            }
            eventInputType
                    .field(newInputObjectField()
                            .type(eventPropertyInputType)
                            .name(cxsEventPropertyType.getName())
                    );
        }

        return eventInputType.build();
    }

    private GraphQLOutputType buildCXSEventOutputType() {
        return newObject()
                .name("CXS_Event")
                .description("An event is generated by user interacting with the Context Server")
                .field(newFieldDefinition()
                        .type(GraphQLID)
                        .name("id")
                        .description("A unique identifier for the event")
                        .dataFetcher(new DataFetcher() {
                            public Object get(DataFetchingEnvironment environment) {
                                CXSEvent CXSEvent = environment.getSource();
                                return CXSEvent.getId();
                            }
                        })
                )
                .field(newFieldDefinition()
                        .type(GraphQLString)
                        .name("eventType")
                        .description("An identifier for the event type")
                        .dataFetcher(new DataFetcher() {
                            public Object get(DataFetchingEnvironment environment) {
                                CXSEvent CXSEvent = environment.getSource();
                                return CXSEvent.getEventType();
                            }
                        })
                )
                .field(newFieldDefinition()
                        .type(GraphQLLong)
                        .name("timestamp")
                        .description("The difference, measured in milliseconds, between the current time and midnight, January 1, 1970 UTC.")
                        .dataFetcher(new DataFetcher() {
                            public Object get(DataFetchingEnvironment environment) {
                                CXSEvent CXSEvent = environment.getSource();
                                return CXSEvent.getTimeStamp();
                            }
                        }))
                .field(newFieldDefinition()
                        .type(GraphQLString)
                        .name("subject")
                        .description("The entity that has fired the event (using the profile)")
                        .dataFetcher(new DataFetcher() {
                            public Object get(DataFetchingEnvironment environment) {
                                CXSEvent CXSEvent = environment.getSource();
                                return CXSEvent.getSubject();
                            }
                        }))
                .field(newFieldDefinition()
                        .type(GraphQLString)
                        .name("object")
                        .description("The object on which the event was fired.")
                        .dataFetcher(new DataFetcher() {
                            public Object get(DataFetchingEnvironment environment) {
                                CXSEvent CXSEvent = environment.getSource();
                                return CXSEvent.getObject();
                            }
                        })
                )
                .field(newFieldDefinition()
                        .type((GraphQLOutputType) typeRegistry.get("CXS_GeoPoint"))
                        .name("location")
                        .description("The geo-point location where the event was fired.")
                        .dataFetcher(new DataFetcher() {
                            public Object get(DataFetchingEnvironment environment) {
                                CXSEvent CXSEvent = environment.getSource();
                                return CXSEvent.getLocation();
                            }
                        })
                )
                .field(newFieldDefinition()
                        .type(new GraphQLList(typeRegistry.get("CXS_EventProperties")))
                        .name("properties")
                        .description("Generic properties for the event")
                        .dataFetcher(new DataFetcher() {
                            public Object get(DataFetchingEnvironment environment) {
                                CXSEvent CXSEvent = environment.getSource();
                                return new ArrayList<Map.Entry<Object,Object>>(CXSEvent.getProperties().getProperties().entrySet());
                            }
                        })
                )
                .build();
    }

    private GraphQLOutputType buildCXSEventPropertiesOutputType() {
        GraphQLObjectType.Builder eventPropertiesOutputType = CXSBuildersUtils.getOutputBuilderFromAnnotatedClass(annotationsComponent, container, "CXS_EventProperties", CXSEventProperties.class)
                .description("All possible properties of an event");

        for (Map.Entry<String,CXSEventType> cxsEventTypeEntry : eventTypes.entrySet()) {
            CXSEventType cxsEventType = cxsEventTypeEntry.getValue();
            eventPropertiesOutputType
                    .field(newFieldDefinition()
                            .type(buildEventOutputType(cxsEventType.getTypeName(), cxsEventType.getProperties()))
                            .name(cxsEventTypeEntry.getKey())
                    );
        }

        return eventPropertiesOutputType.build();
    }

    private GraphQLOutputType buildEventOutputType(String typeName, List<CXSPropertyType> propertyTypes) {
        String eventTypeName = typeName.substring(0, 1).toUpperCase() + typeName.substring(1) + "EventType";
        GraphQLObjectType.Builder eventOutputType = newObject()
                .name(eventTypeName)
                .description("Event type object for event type " + typeName);

        for (CXSPropertyType cxsEventPropertyType : propertyTypes) {
            GraphQLOutputType eventPropertyOutputType = null;
            if (cxsEventPropertyType instanceof CXSIdentifierPropertyType) {
                eventPropertyOutputType = GraphQLID;
            } else if (cxsEventPropertyType instanceof CXSStringPropertyType) {
                eventPropertyOutputType = GraphQLString;
            } else if (cxsEventPropertyType instanceof CXSIntPropertyType) {
                eventPropertyOutputType = GraphQLInt;
            } else if (cxsEventPropertyType instanceof CXSFloatPropertyType) {
                eventPropertyOutputType = GraphQLFloat;
            } else if (cxsEventPropertyType instanceof CXSBooleanPropertyType) {
                eventPropertyOutputType = GraphQLBoolean;
            } else if (cxsEventPropertyType instanceof CXSDatePropertyType) {
                eventPropertyOutputType = GraphQLString;
            } else if (cxsEventPropertyType instanceof CXSGeoPointPropertyType) {
                eventPropertyOutputType = (GraphQLOutputType) typeRegistry.get("CXS_GeoPoint");
            } else if (cxsEventPropertyType instanceof CXSSetPropertyType) {
                eventPropertyOutputType = buildEventOutputType(cxsEventPropertyType.getName(), ((CXSSetPropertyType)cxsEventPropertyType).getProperties());
            }
            eventOutputType
                    .field(newFieldDefinition()
                            .type(eventPropertyOutputType)
                            .name(cxsEventPropertyType.getName())
                    );
        }


        return eventOutputType.build();
    }

}
