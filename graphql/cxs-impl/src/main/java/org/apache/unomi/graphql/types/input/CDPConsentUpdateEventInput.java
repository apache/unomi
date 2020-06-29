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
package org.apache.unomi.graphql.types.input;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;

import static org.apache.unomi.graphql.types.input.CDPConsentUpdateEventInput.TYPE_NAME;

@GraphQLName(TYPE_NAME)
public class CDPConsentUpdateEventInput extends BaseProfileEventProcessor {

    public static final String TYPE_NAME = "CDP_ConsentUpdateEventInput";

    public static final String EVENT_NAME = "cdp_consentUpdateEvent";

    @GraphQLField
    @GraphQLNonNull
    private String type;

    @GraphQLField
    private String status;

    @GraphQLField
    private OffsetDateTime lastUpdate;

    @GraphQLField
    private OffsetDateTime expiration;

    public CDPConsentUpdateEventInput(
            final @GraphQLNonNull @GraphQLName("type") String type,
            final @GraphQLName("status") String status,
            final @GraphQLName("lastUpdate") OffsetDateTime lastUpdate,
            final @GraphQLName("expiration") OffsetDateTime expiration) {
        this.type = type;
        this.status = status;
        this.lastUpdate = lastUpdate;
        this.expiration = expiration;

    }

    public String getType() {
        return type;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getLastUpdate() {
        return lastUpdate;
    }

    public OffsetDateTime getExpiration() {
        return expiration;
    }

    @Override
    public Event buildEvent(LinkedHashMap<String, Object> eventInputAsMap, DataFetchingEnvironment environment) {
        final Profile profile = loadProfile(eventInputAsMap, environment);

        if (profile == null) {
            return null;
        }

        return eventBuilder(EVENT_NAME, profile)
                .setPersistent(true)
                .setProperty("type", type)
                .setProperty("status", status)
                .setProperty("lastUpdate", lastUpdate)
                .setProperty("expiration", expiration)
                .build();
    }

    @Override
    public String getFieldName() {
        return EVENT_NAME;
    }

}
