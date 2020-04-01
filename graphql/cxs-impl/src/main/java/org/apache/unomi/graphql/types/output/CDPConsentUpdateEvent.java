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
import graphql.annotations.annotationTypes.GraphQLID;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;

import java.time.OffsetDateTime;
import java.util.List;

import static org.apache.unomi.graphql.types.output.CDPConsentUpdateEvent.TYPE_NAME;

@GraphQLName(TYPE_NAME)
public class CDPConsentUpdateEvent extends CDPEventInterface {

    public static final String TYPE_NAME = "CDP_ConsentUpdateEvent";

    @GraphQLField
    @GraphQLNonNull
    private String type;

    @GraphQLField
    private String status;

    @GraphQLField
    private OffsetDateTime lastUpdate;

    @GraphQLField
    private OffsetDateTime expiration;

    public CDPConsentUpdateEvent(
            final @GraphQLID @GraphQLNonNull String id,
            final @GraphQLName("cdp_source") CDPSource cdp_source,
            final @GraphQLName("cdp_client") CDPClient cdp_client,
            final @GraphQLNonNull @GraphQLName("cdp_profileID") CDPProfileID cdp_profileID,
            final @GraphQLNonNull @GraphQLName("cdp_profile") CDPProfile cdp_profile,
            final @GraphQLNonNull @GraphQLName("cdp_object") CDPObject cdp_object,
            final @GraphQLName("cdp_location") CDPGeoPoint cdp_location,
            final @GraphQLName("cdp_timestamp") OffsetDateTime cdp_timestamp,
            final @GraphQLName("cdp_topics") List<CDPTopic> cdp_topics,
            final @GraphQLNonNull @GraphQLName("type") String type,
            final @GraphQLName("status") String status,
            final @GraphQLName("lastUpdate") OffsetDateTime lastUpdate,
            final @GraphQLName("expiration") OffsetDateTime expiration) {
        super(id, cdp_source, cdp_client, cdp_profileID, cdp_profile, cdp_object, cdp_location, cdp_timestamp, cdp_topics);

        this.type = type;
        this.status = status;
        this.lastUpdate = lastUpdate;
        this.expiration = expiration;
    }

    public String getType() {
        return type;
    }

    public CDPConsentUpdateEvent setType(String type) {
        this.type = type;
        return this;
    }

    public String getStatus() {
        return status;
    }

    public CDPConsentUpdateEvent setStatus(String status) {
        this.status = status;
        return this;
    }

    public OffsetDateTime getLastUpdate() {
        return lastUpdate;
    }

    public CDPConsentUpdateEvent setLastUpdate(OffsetDateTime lastUpdate) {
        this.lastUpdate = lastUpdate;
        return this;
    }

    public OffsetDateTime getExpiration() {
        return expiration;
    }

    public CDPConsentUpdateEvent setExpiration(OffsetDateTime expiration) {
        this.expiration = expiration;
        return this;
    }

}
