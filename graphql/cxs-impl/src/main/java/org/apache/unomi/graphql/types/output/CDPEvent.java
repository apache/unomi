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
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.utils.DateUtils;

import java.time.OffsetDateTime;
import java.util.List;

@GraphQLName("CDP_Event")
public class CDPEvent {

    private Event event;

    public CDPEvent() {
    }

    public CDPEvent(Event event) {
        this.event = event;
    }

    @GraphQLID
    @GraphQLField
    @GraphQLPrettify
    @GraphQLNonNull
    public String getId() {
        return event.getItemId();
    }

    @GraphQLField
    @GraphQLPrettify
    public CDPSource getSource() {
        return event.getSource() != null ? new CDPSource(event.getSource().getItemId()) : null;
    }

    @GraphQLField
    @GraphQLPrettify
    public CDPClient getClient() {
        return CDPClient.DEFAULT;
    }

    @GraphQLField
    @GraphQLPrettify
    @GraphQLNonNull
    public CDPProfileID getProfileID() {
        return new CDPProfileID(event.getProfileId());
    }

    @GraphQLField
    @GraphQLPrettify
    @GraphQLNonNull
    public CDPProfile getProfile(final DataFetchingEnvironment environment) {
        Profile profile = event.getProfile();
        if (profile == null) {
            final ServiceManager serviceManager = environment.getContext();
            profile = serviceManager.getProfileService().load(event.getProfileId());
        }
        return profile == null ? null : new CDPProfile(profile);
    }

    @GraphQLField
    @GraphQLPrettify
    @GraphQLNonNull
    public CDPObject getObject() {
        final String uri = String.format("%s:%s", event.getItemType(), event.getItemId());
        return new CDPObject(uri, event.getItemType(), event.getItemId(), null);
    }

    @GraphQLField
    @GraphQLPrettify
    public CDPGeoPoint getLocation() {
        return null;
    }

    @GraphQLField
    @GraphQLPrettify
    public OffsetDateTime getTimeStamp() {
        return DateUtils.toOffsetDateTime(event.getTimeStamp());
    }

    @GraphQLField
    @GraphQLPrettify
    public List<CDPTopic> getTopics() {
        return null;
    }

    public CDPEventProperties getProperties() {
        return new CDPEventProperties(event.getProperties());
    }
}
