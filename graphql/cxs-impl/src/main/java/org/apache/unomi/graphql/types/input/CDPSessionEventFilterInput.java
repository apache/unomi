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
import org.apache.unomi.graphql.types.output.CDPSessionState;

import static org.apache.unomi.graphql.types.input.CDPSessionEventFilterInput.TYPE_NAME;

@GraphQLName(TYPE_NAME)
public class CDPSessionEventFilterInput implements EventFilterInputMarker {

    public static final String TYPE_NAME = "CDP_SessionEventFilterInput";

    @GraphQLField
    private CDPSessionState state_equals;

    @GraphQLField
    private String unomi_sessionId_equals;

    @GraphQLField
    private String unomi_scope_equals;

    public CDPSessionEventFilterInput(
            final @GraphQLName("state_equals") CDPSessionState state_equals,
            final @GraphQLName("unomi_sessionId_equals") String unomi_sessionId_equals,
            final @GraphQLName("unomi_scope_equals") String unomi_scope_equals) {
        this.state_equals = state_equals;
        this.unomi_sessionId_equals = unomi_sessionId_equals;
        this.unomi_scope_equals = unomi_scope_equals;
    }

    public CDPSessionState getState_equals() {
        return state_equals;
    }

    public String getUnomi_sessionId_equals() {
        return unomi_sessionId_equals;
    }

    public String getUnomi_scope_equals() {
        return unomi_scope_equals;
    }
}
