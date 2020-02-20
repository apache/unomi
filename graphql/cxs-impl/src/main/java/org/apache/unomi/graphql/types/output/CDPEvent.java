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
import graphql.annotations.annotationTypes.GraphQLPrettify;
import org.apache.unomi.api.Event;

@GraphQLName("CDP_Event")
public class CDPEvent {

    private String id;
    private String eventType;
    private long timeStamp;
    private String subject;
    private String object;
    private CDPEventProperties properties = new CDPEventProperties();
    private CDPGeoPoint location;

    public CDPEvent() {
    }

    public CDPEvent(Event event) {
        id = event.getItemId();
        eventType = event.getEventType();
        timeStamp = event.getTimeStamp() != null ? event.getTimeStamp().getTime() : 0;
        subject = event.getScope(); //TODO: ?
//        object = event.getObject();
        properties = new CDPEventProperties(event.getProperties());
    }

    @GraphQLField
    @GraphQLPrettify
    public String getId() {
        return id;
    }

    @GraphQLField
    @GraphQLPrettify
    public String getEventType() {
        return eventType;
    }

    @GraphQLField
    @GraphQLPrettify
    public long getTimeStamp() {
        return timeStamp;
    }

    @GraphQLField
    @GraphQLPrettify
    public String getSubject() {
        return subject;
    }

    @GraphQLField
    @GraphQLPrettify
    public String getObject() {
        return object;
    }

    @GraphQLField
    @GraphQLPrettify
    public CDPEventProperties getProperties() {
        return properties;
    }

    @GraphQLField
    @GraphQLPrettify
    public CDPGeoPoint getLocation() {
        return location;
    }
}
