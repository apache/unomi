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
package org.apache.unomi.graphql.commands;

import graphql.language.InputObjectTypeDefinition;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.graphql.types.input.CDPConsentUpdateEventInput;
import org.apache.unomi.graphql.types.input.CDPEventInput;
import org.apache.unomi.graphql.types.input.CDPEventProcessor;
import org.apache.unomi.graphql.types.input.CDPListsUpdateEventInput;
import org.apache.unomi.graphql.types.input.CDPSessionEventInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class ProcessEventsCommand extends BaseCommand<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessEventsCommand.class.getName());

    private static final List<String> STATIC_FIELDS = new ArrayList<>();

    private final List<CDPEventInput> eventInputs;

    private final List<LinkedHashMap<String, Object>> eventsAsMap;

    private final DataFetchingEnvironment environment;

    private final List<GraphQLInputObjectField> fieldDefinitions;

    private final AtomicInteger processedEventsQty = new AtomicInteger();

    static {
        STATIC_FIELDS.add(CDPConsentUpdateEventInput.EVENT_NAME);
        STATIC_FIELDS.add(CDPListsUpdateEventInput.EVENT_NAME);
        STATIC_FIELDS.add(CDPSessionEventInput.EVENT_NAME);
    }

    private ProcessEventsCommand(final Builder builder) {
        super(builder);

        this.environment = builder.environment;
        this.eventInputs = builder.eventInputs;

        this.eventsAsMap = environment.getArgument("events");

        final GraphQLInputObjectType objectType =
                (GraphQLInputObjectType) environment.getGraphQLSchema().getType(CDPEventInput.TYPE_NAME);

        this.fieldDefinitions = objectType.getFieldDefinitions();
    }

    @Override
    public Integer execute() {
        for (int i = 0; i < eventInputs.size(); i++) {
            final CDPEventInput eventInput = eventInputs.get(i);

            final LinkedHashMap<String, Object> eventInputAsMap = eventsAsMap.get(i);

            processStaticFields(eventInput, eventInputAsMap);

            processDynamicFields(eventInputAsMap);
        }

        return processedEventsQty.get();
    }

    private void processStaticFields(
            final CDPEventInput eventInput, final LinkedHashMap<String, Object> eventInputAsMap) {
        final List<CDPEventProcessor> eventProcessors = new ArrayList<>();
        eventProcessors.add(eventInput.getCdp_consentUpdateEvent());
        eventProcessors.add(eventInput.getCdp_listUpdateEvent());
        eventProcessors.add(eventInput.getCdp_sessionEvent());

        eventProcessors.stream()
                .filter(Objects::nonNull)
                .forEach(eventProcessor -> {
                    try {
                        final Event event = eventProcessor.buildEvent(eventInputAsMap, environment);

                        if (event != null) {
                            processEvent(event);
                        }

                    } catch (Exception e) {
                        LOG.debug("Process field {} is failed", eventProcessor.getFieldName(), e);
                    }
                });
    }

    private void processDynamicFields(final LinkedHashMap<String, Object> eventInputAsMap) {
        fieldDefinitions.forEach(fieldDefinition -> {
            if (!STATIC_FIELDS.contains(fieldDefinition.getName())) {
                try {
                    processField(fieldDefinition, eventInputAsMap);
                } catch (Exception e) {
                    LOG.debug("Process field {} is failed", fieldDefinition, e);
                }
            }
        });
    }

    private boolean processField(
            final GraphQLInputObjectField fieldDefinition,
            final LinkedHashMap<String, Object> eventInputAsMap) throws Exception {
        if (!eventInputAsMap.containsKey(fieldDefinition.getName())) {
            return false;
        }

        if (fieldDefinition.getType() instanceof GraphQLInputObjectType) {
            final GraphQLInputObjectType inputObjectType = (GraphQLInputObjectType) fieldDefinition.getType();

            final InputObjectTypeDefinition typeDefinition = inputObjectType.getDefinition();

            if (typeDefinition != null && typeDefinition.getAdditionalData().containsKey("clazz")) {
                final String className = typeDefinition.getAdditionalData().get("clazz");

                buildAndProcessEvent(className, eventInputAsMap);

                return true;
            }
        }

        return false;
    }

    private void buildAndProcessEvent(final String className, final LinkedHashMap<String, Object> eventInputAsMap) throws Exception {
        final Constructor<?> constructor = Class.forName(className).getConstructor();
        final Object instance = constructor.newInstance();

        if (instance instanceof CDPEventProcessor) {
            final Event event = ((CDPEventProcessor) instance).buildEvent(eventInputAsMap, environment);

            if (event != null) {
                processEvent(event);
            }
        }
    }

    private void processEvent(final Event event) {
        int eventCode = serviceManager.getEventService().send(event);

        if (eventCode == EventService.PROFILE_UPDATED) {
            serviceManager.getProfileService().save(event.getProfile());
        }
        processedEventsQty.incrementAndGet();
    }

    public static Builder create(final List<CDPEventInput> eventInputs, final DataFetchingEnvironment environment) {
        return new Builder(eventInputs, environment);
    }


    public static final class Builder extends BaseCommand.Builder<Builder> {

        private final List<CDPEventInput> eventInputs;

        private final DataFetchingEnvironment environment;

        public Builder(final List<CDPEventInput> eventInputs, final DataFetchingEnvironment environment) {
            this.eventInputs = eventInputs;
            this.environment = environment;
        }

        private void validate() {
            final List<LinkedHashMap<String, Object>> events = environment.getArgument("events");

            if (events == null || events.isEmpty()) {
                throw new IllegalArgumentException("The \"events\" variable can not be null or empty");
            }

            events.forEach(eventInput -> {
                Objects.requireNonNull(eventInput.get("cdp_objectID"), "The \"cdp_objectID\" field can not be null");
                Objects.requireNonNull(eventInput.get("cdp_profileID"), "The \"cdp_profileID\" field can not be null");
            });
        }

        public ProcessEventsCommand build() {
            validate();

            return new ProcessEventsCommand(this);
        }

    }
}
