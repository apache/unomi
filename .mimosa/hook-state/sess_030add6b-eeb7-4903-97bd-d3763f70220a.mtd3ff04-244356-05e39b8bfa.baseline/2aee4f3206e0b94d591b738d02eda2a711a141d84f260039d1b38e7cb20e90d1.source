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

import java.time.OffsetDateTime;
import java.util.Map;

import static org.apache.unomi.graphql.types.input.CDPConsentUpdateEventFilterInput.TYPE_NAME;

@GraphQLName(TYPE_NAME)
public class CDPConsentUpdateEventFilterInput implements EventFilterInputMarker {

    public static final String TYPE_NAME = "CDP_ConsentUpdateEventFilterInput";

    @GraphQLField
    private String type_equals;

    @GraphQLField
    private String status_equals;

    @GraphQLField
    private OffsetDateTime lastUpdate_equals;

    @GraphQLField
    private OffsetDateTime lastUpdate_lt;

    @GraphQLField
    private OffsetDateTime lastUpdate_lte;

    @GraphQLField
    private OffsetDateTime lastUpdate_gt;

    @GraphQLField
    private OffsetDateTime lastUpdate_gte;

    @GraphQLField
    private OffsetDateTime expiration_equals;

    @GraphQLField
    private OffsetDateTime expiration_lt;

    @GraphQLField
    private OffsetDateTime expiration_lte;

    @GraphQLField
    private OffsetDateTime expiration_gt;

    @GraphQLField
    private OffsetDateTime expiration_gte;

    public CDPConsentUpdateEventFilterInput(
            final @GraphQLName("type_equals") String type_equals,
            final @GraphQLName("status_equals") String status_equals,
            final @GraphQLName("lastUpdate_equals") OffsetDateTime lastUpdate_equals,
            final @GraphQLName("lastUpdate_lt") OffsetDateTime lastUpdate_lt,
            final @GraphQLName("lastUpdate_lte") OffsetDateTime lastUpdate_lte,
            final @GraphQLName("lastUpdate_gt") OffsetDateTime lastUpdate_gt,
            final @GraphQLName("lastUpdate_gte") OffsetDateTime lastUpdate_gte,
            final @GraphQLName("expiration_equals") OffsetDateTime expiration_equals,
            final @GraphQLName("expiration_lt") OffsetDateTime expiration_lt,
            final @GraphQLName("expiration_lte") OffsetDateTime expiration_lte,
            final @GraphQLName("expiration_gt") OffsetDateTime expiration_gt,
            final @GraphQLName("expiration_gte") OffsetDateTime expiration_gte) {
        this.type_equals = type_equals;
        this.status_equals = status_equals;
        this.lastUpdate_equals = lastUpdate_equals;
        this.lastUpdate_lt = lastUpdate_lt;
        this.lastUpdate_lte = lastUpdate_lte;
        this.lastUpdate_gt = lastUpdate_gt;
        this.lastUpdate_gte = lastUpdate_gte;
        this.expiration_equals = expiration_equals;
        this.expiration_lt = expiration_lt;
        this.expiration_lte = expiration_lte;
        this.expiration_gt = expiration_gt;
        this.expiration_gte = expiration_gte;
    }

    public static CDPConsentUpdateEventFilterInput fromMap(final Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        final String typeEq = (String) map.get("type_equals");
        final String statusEq = (String) map.get("status_equals");
        final OffsetDateTime updateEq = (OffsetDateTime) map.get("lastUpdate_equals");
        final OffsetDateTime updateLt = (OffsetDateTime) map.get("lastUpdate_lt");
        final OffsetDateTime updateLte = (OffsetDateTime) map.get("lastUpdate_lte");
        final OffsetDateTime updateGt = (OffsetDateTime) map.get("lastUpdate_gt");
        final OffsetDateTime updateGte = (OffsetDateTime) map.get("lastUpdate_gte");
        final OffsetDateTime expiryEq = (OffsetDateTime) map.get("expiration_equals");
        final OffsetDateTime expiryLt = (OffsetDateTime) map.get("expiration_lt");
        final OffsetDateTime expiryLte = (OffsetDateTime) map.get("expiration_lte");
        final OffsetDateTime expiryGt = (OffsetDateTime) map.get("expiration_gt");
        final OffsetDateTime expiryGte = (OffsetDateTime) map.get("expiration_gte");
        return new CDPConsentUpdateEventFilterInput(typeEq, statusEq, updateEq, updateLt, updateLte, updateGt, updateGte, expiryEq, expiryLt, expiryLte, expiryGt, expiryGte);
    }

    public String getType_equals() {
        return type_equals;
    }

    public String getStatus_equals() {
        return status_equals;
    }

    public OffsetDateTime getLastUpdate_equals() {
        return lastUpdate_equals;
    }

    public OffsetDateTime getLastUpdate_lt() {
        return lastUpdate_lt;
    }

    public OffsetDateTime getLastUpdate_lte() {
        return lastUpdate_lte;
    }

    public OffsetDateTime getLastUpdate_gt() {
        return lastUpdate_gt;
    }

    public OffsetDateTime getLastUpdate_gte() {
        return lastUpdate_gte;
    }

    public OffsetDateTime getExpiration_equals() {
        return expiration_equals;
    }

    public OffsetDateTime getExpiration_lt() {
        return expiration_lt;
    }

    public OffsetDateTime getExpiration_lte() {
        return expiration_lte;
    }

    public OffsetDateTime getExpiration_gt() {
        return expiration_gt;
    }

    public OffsetDateTime getExpiration_gte() {
        return expiration_gte;
    }
}
