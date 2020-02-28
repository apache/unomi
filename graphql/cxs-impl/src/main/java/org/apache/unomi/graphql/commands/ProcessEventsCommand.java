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
import org.apache.unomi.graphql.schema.GraphQLSchemaUpdater;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.input.CDPEventInput;
import org.apache.unomi.graphql.types.input.CDPEventProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class ProcessEventsCommand extends BaseCommand<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessEventsCommand.class.getName());

    private final DataFetchingEnvironment environment;

    private ProcessEventsCommand(final Builder builder) {
        super(builder);

        this.environment = builder.environment;
    }

    @Override
    public Integer execute() {
        final ServiceManager serviceManager = environment.getContext();

        final GraphQLSchemaUpdater schemaProvider = serviceManager.getGraphQLSchemaUpdater();

        final List<LinkedHashMap<String, Object>> events = environment.getArgument("events");

        final List<GraphQLInputObjectField> fieldDefinitions = schemaProvider.getInputObjectType(CDPEventInput.class).getFieldDefinitions();

        final AtomicInteger atomicInteger = new AtomicInteger();

        for (final LinkedHashMap<String, Object> envCdpEventInput : events) {
            fieldDefinitions.forEach(fieldDefinition -> {
                try {
                    if (processField(fieldDefinition, envCdpEventInput)) {
                        atomicInteger.incrementAndGet();
                    }
                } catch (Exception e) {
                    LOG.debug("Process field {} is failed", fieldDefinition, e);
                }
            });
        }

        return atomicInteger.get();
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

                buildEvent(className, eventInputAsMap);

                return true;
            }
        }

        return false;
    }

    private void buildEvent(final String className, final LinkedHashMap<String, Object> eventInputAsMap) throws Exception {
        final Constructor<?> constructor = Class.forName(className).getConstructor();
        final Object instance = constructor.newInstance();

        if (instance instanceof CDPEventProcessor) {
            final Event event = ((CDPEventProcessor) instance).buildEvent(eventInputAsMap, environment);

            int eventCode = serviceManager.getEventService().send(event);

            if (eventCode == EventService.PROFILE_UPDATED) {
                serviceManager.getProfileService().save(event.getProfile());
            }
        }
    }

    public static Builder create(final DataFetchingEnvironment environment) {
        return new Builder(environment);
    }


    public static final class Builder extends BaseCommand.Builder<Builder> {

        private final DataFetchingEnvironment environment;

        public Builder(final DataFetchingEnvironment environment) {
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
