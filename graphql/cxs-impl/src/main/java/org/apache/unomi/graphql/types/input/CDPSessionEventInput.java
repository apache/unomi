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
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.graphql.types.output.CDPSessionState;

import java.util.LinkedHashMap;

import static org.apache.unomi.graphql.types.input.CDPSessionEventInput.TYPE_NAME_INTERNAL;

@GraphQLName(TYPE_NAME_INTERNAL)
public class CDPSessionEventInput extends BaseProfileEventProcessor {

    public static final String TYPE_NAME_INTERNAL = "CDP_SessionEvent";

    public static final String TYPE_NAME = TYPE_NAME_INTERNAL + "Input";

    public static final String EVENT_NAME = "cdp_sessionEvent";

    @GraphQLField
    private CDPSessionState state;

    @GraphQLField
    private String unomi_sessionId;

    @GraphQLField
    private String unomi_scope;

    public CDPSessionEventInput(
            final @GraphQLName("state") CDPSessionState state,
            final @GraphQLName("unomi_sessionId") String unomi_sessionId,
            final @GraphQLName("unomi_scope") String unomi_scope) {
        this.state = state;
        this.unomi_sessionId = unomi_sessionId;
        this.unomi_scope = unomi_scope;
    }

    public CDPSessionState getState() {
        return state;
    }

    public String getUnomi_sessionId() {
        return unomi_sessionId;
    }

    public String getUnomi_scope() {
        return unomi_scope;
    }

    @Override
    public Event buildEvent(LinkedHashMap<String, Object> eventInputAsMap, DataFetchingEnvironment environment) {
        final Profile profile = loadProfile(eventInputAsMap, environment);

        if (profile == null) {
            return null;
        }

        return eventBuilder(EVENT_NAME, profile)
                .setPersistent(true)
                .setProperty("state", state.name())
                .setProperty("sessionId", unomi_sessionId)
                .setProperty("scope", unomi_scope)
                .build();
    }

    @Override
    public String getFieldName() {
        return EVENT_NAME;
    }

}
