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
package org.apache.unomi.graphql.types.output;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.Event;
import org.apache.unomi.graphql.utils.DateUtils;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.apache.unomi.graphql.types.output.CDPConsentUpdateEvent.TYPE_NAME;

@GraphQLName(TYPE_NAME)
public class CDPConsentUpdateEvent implements CDPEventInterface {

    public static final String TYPE_NAME = "CDP_ConsentUpdateEvent";

    private final Event event;

    public CDPConsentUpdateEvent(Event event) {
        this.event = event;
    }

    @Override
    public Event getEvent() {
        return event;
    }

    @GraphQLField
    @GraphQLNonNull
    public String type(DataFetchingEnvironment environment) {
        final Object type = getEvent().getProperty("type");
        return type != null ? type.toString() : null;
    }

    @GraphQLField
    public String status(DataFetchingEnvironment environment) {
        final Object status = getEvent().getProperty("status");
        return status != null ? status.toString() : null;
    }

    @GraphQLField
    @SuppressWarnings("unchecked")
    public OffsetDateTime lastUpdate(DataFetchingEnvironment environment) {
        final Object lastUpdate = getEvent().getProperty("lastUpdate");
        return lastUpdate != null ? DateUtils.offsetDateTimeFromMap((Map<String, Object>) lastUpdate) : null;
    }

    @GraphQLField
    @SuppressWarnings("unchecked")
    public OffsetDateTime expiration(DataFetchingEnvironment environment) {
        final Object expiration = getEvent().getProperty("expiration");
        return expiration != null ? DateUtils.offsetDateTimeFromMap((Map<String, Object>) expiration) : null;
    }

}
