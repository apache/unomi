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
import org.apache.unomi.api.ConsentStatus;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.graphql.utils.DateUtils;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.apache.unomi.graphql.types.input.CDPConsentUpdateEventInput.TYPE_NAME_INTERNAL;

@GraphQLName(TYPE_NAME_INTERNAL)
public class CDPConsentUpdateEventInput extends BaseProfileEventProcessor {

    public static final String TYPE_NAME_INTERNAL = "CDP_ConsentUpdateEvent";

    public static final String TYPE_NAME = TYPE_NAME_INTERNAL + "Input";

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
    @SuppressWarnings("unchecked")
    public Event buildEvent(LinkedHashMap<String, Object> eventInputAsMap, DataFetchingEnvironment environment) {
        final Profile profile = loadProfile(eventInputAsMap, environment);

        if (profile == null) {
            return null;
        }

        final LinkedHashMap<String, Object> fieldsMap = (LinkedHashMap<String, Object>) eventInputAsMap.get(EVENT_NAME);

        profile.getConsents().forEach((k, v) -> {
            if (k.endsWith("/" + fieldsMap.get("type").toString())) {
                if (fieldsMap.get("status") != null) {
                    v.setStatus(ConsentStatus.valueOf(fieldsMap.get("status").toString()));
                }
                if (fieldsMap.get("lastUpdate") != null) {
                    v.setStatusDate(DateUtils.toDate((OffsetDateTime) fieldsMap.get("lastUpdate")));
                }
                if (fieldsMap.get("expiration") != null) {
                    v.setRevokeDate(DateUtils.toDate((OffsetDateTime) fieldsMap.get("expiration")));
                }
            }
        });

        final Map<String, Object> propertiesToUpdate = new HashMap<>();
        propertiesToUpdate.put("consents", profile.getConsents());

        return eventBuilder(profile)
                .setPropertiesToUpdate(propertiesToUpdate)
                .build();
    }

    @Override
    public String getFieldName() {
        return EVENT_NAME;
    }

}
