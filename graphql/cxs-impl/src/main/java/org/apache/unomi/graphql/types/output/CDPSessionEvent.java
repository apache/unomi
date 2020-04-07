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

import static org.apache.unomi.graphql.types.output.CDPSessionEvent.TYPE_NAME;

@GraphQLName(TYPE_NAME)
public class CDPSessionEvent extends CDPEventInterface {

    public static final String TYPE_NAME = "CDP_SessionEvent";

    @GraphQLField
    private CDPSessionState state;

    public CDPSessionEvent(
            final @GraphQLID @GraphQLNonNull @GraphQLName("id") String id,
            final @GraphQLName("cdp_source") CDPSource cdp_source,
            final @GraphQLName("cdp_client") CDPClient cdp_client,
            final @GraphQLNonNull @GraphQLName("cdp_profileID") CDPProfileID cdp_profileID,
            final @GraphQLNonNull @GraphQLName("cdp_profile") CDPProfile cdp_profile,
            final @GraphQLNonNull @GraphQLName("cdp_object") CDPObject cdp_object,
            final @GraphQLName("cdp_location") CDPGeoPoint cdp_location,
            final @GraphQLName("cdp_timestamp") OffsetDateTime cdp_timestamp,
            final @GraphQLName("cdp_topics") List<CDPTopic> cdp_topics,
            final @GraphQLName("state") CDPSessionState state) {
        super(id, cdp_source, cdp_client, cdp_profileID, cdp_profile, cdp_object, cdp_location, cdp_timestamp, cdp_topics);

        this.state = state;
    }

    public CDPSessionState getState() {
        return state;
    }

    public CDPSessionEvent setState(CDPSessionState state) {
        this.state = state;
        return this;
    }

}
