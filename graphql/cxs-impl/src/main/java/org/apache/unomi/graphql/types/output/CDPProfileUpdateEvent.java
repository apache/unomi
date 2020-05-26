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

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLName;
import org.apache.unomi.api.Event;

import static org.apache.unomi.graphql.types.output.CDPProfileUpdateEvent.TYPE_NAME;

@GraphQLName(TYPE_NAME)
@GraphQLDescription("Profiles are crated and updated through this event type. This event is part of the standard and MUST be available for any implementation of the specification.")
public class CDPProfileUpdateEvent implements CDPEventInterface {

    public static final String TYPE_NAME = "CDP_ProfileUpdateEvent";

    private final Event event;

    public CDPProfileUpdateEvent(Event event) {
        this.event = event;
    }

    @Override
    public Event getEvent() {
        return event;
    }

}
