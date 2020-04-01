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
import graphql.annotations.annotationTypes.GraphQLPrettify;
import graphql.annotations.annotationTypes.GraphQLTypeResolver;
import org.apache.unomi.graphql.types.resolvers.CDPEventInterfaceResolver;

import java.time.OffsetDateTime;
import java.util.List;

@GraphQLName("CDP_EventInterface")
@GraphQLTypeResolver(CDPEventInterfaceResolver.class)
public abstract class CDPEventInterface {

    private String id;
    private CDPSource cdp_source;
    private CDPClient cdp_client;
    private CDPProfileID cdp_profileID;
    private CDPProfile cdp_profile;
    private CDPObject cdp_object;
    private CDPGeoPoint cdp_location;
    private OffsetDateTime cdp_timestamp;
    private List<CDPTopic> cdp_topics;

    public CDPEventInterface(
            @GraphQLID @GraphQLNonNull String id,
            @GraphQLName("cdp_source") CDPSource cdp_source,
            @GraphQLName("cdp_client") CDPClient cdp_client,
            @GraphQLNonNull @GraphQLName("cdp_profileID") CDPProfileID cdp_profileID,
            @GraphQLNonNull @GraphQLName("cdp_profile") CDPProfile cdp_profile,
            @GraphQLNonNull @GraphQLName("cdp_object") CDPObject cdp_object,
            @GraphQLName("cdp_location") CDPGeoPoint cdp_location,
            @GraphQLName("cdp_timestamp") OffsetDateTime cdp_timestamp,
            @GraphQLName("cdp_topics") List<CDPTopic> cdp_topics) {
        this.id = id;
        this.cdp_source = cdp_source;
        this.cdp_client = cdp_client;
        this.cdp_profileID = cdp_profileID;
        this.cdp_profile = cdp_profile;
        this.cdp_object = cdp_object;
        this.cdp_location = cdp_location;
        this.cdp_timestamp = cdp_timestamp;
        this.cdp_topics = cdp_topics;
    }

    @GraphQLID
    @GraphQLNonNull
    @GraphQLField
    @GraphQLPrettify
    public String getId() {
        return id;
    }

    @GraphQLField
    @GraphQLPrettify
    public CDPSource getCdp_source() {
        return cdp_source;
    }

    @GraphQLField
    @GraphQLPrettify
    public CDPClient getCdp_client() {
        return cdp_client;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLPrettify
    public CDPProfileID getCdp_profileID() {
        return cdp_profileID;
    }

    @GraphQLField
    @GraphQLPrettify
    @GraphQLNonNull
    public CDPProfile getCdp_profile() {
        return cdp_profile;
    }

    @GraphQLField
    @GraphQLPrettify
    public CDPObject getCdp_object() {
        return cdp_object;
    }

    @GraphQLField
    @GraphQLPrettify
    public CDPGeoPoint getCdp_location() {
        return cdp_location;
    }

    @GraphQLField
    @GraphQLPrettify
    public OffsetDateTime getCdp_timestamp() {
        return cdp_timestamp;
    }

    @GraphQLField
    @GraphQLPrettify
    public List<CDPTopic> getCdp_topics() {
        return cdp_topics;
    }

}
