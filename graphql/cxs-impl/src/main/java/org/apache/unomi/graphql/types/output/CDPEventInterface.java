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
import graphql.annotations.annotationTypes.GraphQLTypeResolver;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.GeoPoint;
import org.apache.unomi.api.Profile;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.resolvers.CDPEventInterfaceResolver;
import org.apache.unomi.graphql.utils.DateUtils;

import java.time.OffsetDateTime;
import java.util.List;

import static org.apache.unomi.graphql.types.output.CDPEventInterface.TYPE_NAME;

@GraphQLName(TYPE_NAME)
@GraphQLTypeResolver(CDPEventInterfaceResolver.class)
public interface CDPEventInterface {

    String TYPE_NAME = "CDP_EventInterface";

    Event getEvent();

    default Object getProperty(final String propertyName) {
        return getEvent() != null ? getEvent().getProperty(propertyName) : null;
    }

    @GraphQLID
    @GraphQLNonNull
    @GraphQLField
    default String id(final DataFetchingEnvironment environment) {
        return getEvent().getItemId();
    }

    @GraphQLField
    default CDPSource сdp_source(final DataFetchingEnvironment environment) {
        return null;
    }

    @GraphQLField
    default CDPClient cdp_client(final DataFetchingEnvironment environment) {
        return CDPClient.DEFAULT;
    }

    @GraphQLField
    @GraphQLNonNull
    default CDPProfileID cdp_profileID(final DataFetchingEnvironment environment) {
        return new CDPProfileID(getEvent().getProfileId());
    }

    @GraphQLField
    @GraphQLNonNull
    default CDPProfile cdp_profile(final DataFetchingEnvironment environment) {
        if (getEvent().getProfile() != null) {
            return new CDPProfile(getEvent().getProfile());
        } else if (getEvent().getProfileId() != null) {
            ServiceManager serviceManager = environment.getContext();

            Profile profile = serviceManager.getProfileService().load(getEvent().getProfileId());

            return new CDPProfile(profile);
        } else {
            return null;
        }
    }

    @GraphQLField
    default CDPObject cdp_object(final DataFetchingEnvironment environment) {
        return new CDPObject(getEvent());
    }

    @GraphQLField
    default GeoPoint cdp_location(final DataFetchingEnvironment environment) {
        return null;
    }

    @GraphQLField
    default OffsetDateTime cdp_timestamp(final DataFetchingEnvironment environment) {
        return DateUtils.toOffsetDateTime(getEvent().getTimeStamp());
    }

    @GraphQLField
    default List<CDPTopic> cdp_topics(final DataFetchingEnvironment environment) {
        return null;
    }

}
