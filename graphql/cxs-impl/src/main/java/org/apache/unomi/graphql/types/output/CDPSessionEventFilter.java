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

import static org.apache.unomi.graphql.types.output.CDPSessionEventFilter.TYPE_NAME;

@GraphQLName(TYPE_NAME)
public class CDPSessionEventFilter {

    public static final String TYPE_NAME = "CDP_SessionEventFilter";

    @GraphQLField
    private CDPSessionState state_equals;

    @GraphQLField
    private String unomi_sessionId_equals;

    @GraphQLField
    private String unomi_scope_equals;

    public CDPSessionState getState_equals() {
        return state_equals;
    }

    public CDPSessionEventFilter setState_equals(CDPSessionState state_equals) {
        this.state_equals = state_equals;
        return this;
    }

    public String getUnomi_sessionId_equals() {
        return unomi_sessionId_equals;
    }

    public CDPSessionEventFilter setUnomi_sessionId_equals(String unomi_sessionId_equals) {
        this.unomi_sessionId_equals = unomi_sessionId_equals;
        return this;
    }

    public String getUnomi_scope_equals() {
        return unomi_scope_equals;
    }

    public CDPSessionEventFilter setUnomi_scope_equals(String unomi_scope_equals) {
        this.unomi_scope_equals = unomi_scope_equals;
        return this;
    }

}
