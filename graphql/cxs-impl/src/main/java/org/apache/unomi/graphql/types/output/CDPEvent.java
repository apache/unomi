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
import org.apache.unomi.api.Event;
import org.apache.unomi.graphql.utils.DateUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@GraphQLName("CDP_Event")
public class CDPEvent {

    @GraphQLID
    @GraphQLField
    @GraphQLNonNull
    private String id;

    @GraphQLField
    private CDPSource source;

    @GraphQLField
    private CDPClient client;

    @GraphQLField
    @GraphQLNonNull
    private CDPProfileID profileID;

    @GraphQLField
    @GraphQLNonNull
    private CDPProfile profile;

    @GraphQLField
    @GraphQLNonNull
    private CDPObject object;

    @GraphQLField
    private CDPGeoPoint location;

    @GraphQLField
    private OffsetDateTime timeStamp;

    @GraphQLField
    private List<CDPTopic> topics = new ArrayList<CDPTopic>();

    // TODO: properties is not part of the spec
    private CDPEventProperties properties;

    public CDPEvent() {
    }

    public CDPEvent(Event event) {
        id = event.getItemId();
        source = new CDPSource(event.getSource() != null ? event.getSource().getItemId() : "");
        client = CDPClient.DEFAULT;
        profileID = new CDPProfileID(event.getProfileId());
        profile = new CDPProfile(event.getProfile());
        final String uri = String.format("%s:%s", event.getItemType(), event.getItemId());
        object = new CDPObject(uri, event.getItemType(), event.getItemId(), null);
        timeStamp = DateUtils.toOffsetDateTime(event.getTimeStamp());
//        TODO: implement after unomi supports it
//        location
//        topics

        properties = new CDPEventProperties(event.getProperties());
    }

    public String getId() {
        return id;
    }

    public CDPSource getSource() {
        return source;
    }

    public CDPClient getClient() {
        return client;
    }

    public CDPProfileID getProfileID() {
        return profileID;
    }

    public CDPProfile getProfile() {
        return profile;
    }

    public CDPObject getObject() {
        return object;
    }

    public CDPGeoPoint getLocation() {
        return location;
    }

    public OffsetDateTime getTimeStamp() {
        return timeStamp;
    }

    public List<CDPTopic> getTopics() {
        return topics;
    }

    public CDPEventProperties getProperties() {
        return properties;
    }
}
