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

import java.time.OffsetDateTime;

import static org.apache.unomi.graphql.types.output.CDPConsentUpdateEventFilter.TYPE_NAME;

@GraphQLName(TYPE_NAME)
public class CDPConsentUpdateEventFilter {

    public static final String TYPE_NAME = "CDP_ConsentUpdateEventFilter";

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

    public String getType_equals() {
        return type_equals;
    }

    public CDPConsentUpdateEventFilter setType_equals(String type_equals) {
        this.type_equals = type_equals;
        return this;
    }

    public String getStatus_equals() {
        return status_equals;
    }

    public CDPConsentUpdateEventFilter setStatus_equals(String status_equals) {
        this.status_equals = status_equals;
        return this;
    }

    public OffsetDateTime getLastUpdate_equals() {
        return lastUpdate_equals;
    }

    public CDPConsentUpdateEventFilter setLastUpdate_equals(OffsetDateTime lastUpdate_equals) {
        this.lastUpdate_equals = lastUpdate_equals;
        return this;
    }

    public OffsetDateTime getLastUpdate_lt() {
        return lastUpdate_lt;
    }

    public CDPConsentUpdateEventFilter setLastUpdate_lt(OffsetDateTime lastUpdate_lt) {
        this.lastUpdate_lt = lastUpdate_lt;
        return this;
    }

    public OffsetDateTime getLastUpdate_lte() {
        return lastUpdate_lte;
    }

    public CDPConsentUpdateEventFilter setLastUpdate_lte(OffsetDateTime lastUpdate_lte) {
        this.lastUpdate_lte = lastUpdate_lte;
        return this;
    }

    public OffsetDateTime getLastUpdate_gt() {
        return lastUpdate_gt;
    }

    public CDPConsentUpdateEventFilter setLastUpdate_gt(OffsetDateTime lastUpdate_gt) {
        this.lastUpdate_gt = lastUpdate_gt;
        return this;
    }

    public OffsetDateTime getLastUpdate_gte() {
        return lastUpdate_gte;
    }

    public CDPConsentUpdateEventFilter setLastUpdate_gte(OffsetDateTime lastUpdate_gte) {
        this.lastUpdate_gte = lastUpdate_gte;
        return this;
    }

    public OffsetDateTime getExpiration_equals() {
        return expiration_equals;
    }

    public CDPConsentUpdateEventFilter setExpiration_equals(OffsetDateTime expiration_equals) {
        this.expiration_equals = expiration_equals;
        return this;
    }

    public OffsetDateTime getExpiration_lt() {
        return expiration_lt;
    }

    public CDPConsentUpdateEventFilter setExpiration_lt(OffsetDateTime expiration_lt) {
        this.expiration_lt = expiration_lt;
        return this;
    }

    public OffsetDateTime getExpiration_lte() {
        return expiration_lte;
    }

    public CDPConsentUpdateEventFilter setExpiration_lte(OffsetDateTime expiration_lte) {
        this.expiration_lte = expiration_lte;
        return this;
    }

    public OffsetDateTime getExpiration_gt() {
        return expiration_gt;
    }

    public CDPConsentUpdateEventFilter setExpiration_gt(OffsetDateTime expiration_gt) {
        this.expiration_gt = expiration_gt;
        return this;
    }

    public OffsetDateTime getExpiration_gte() {
        return expiration_gte;
    }

    public CDPConsentUpdateEventFilter setExpiration_gte(OffsetDateTime expiration_gte) {
        this.expiration_gte = expiration_gte;
        return this;
    }

}
