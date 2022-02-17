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

import java.util.List;

import static org.apache.unomi.graphql.types.input.CDPProfileAliasFilterInput.TYPE_NAME;

@GraphQLName(TYPE_NAME)
public class CDPProfileAliasFilterInput {

    public static final String TYPE_NAME = "CDP_ProfileAliasFilterInput";

    @GraphQLField
    @GraphQLName("and")
    private final List<CDPProfileAliasFilterInput> and;

    @GraphQLField
    @GraphQLName("or")
    private final List<CDPProfileAliasFilterInput> or;

    @GraphQLField
    @GraphQLName("alias_equals")
    private final String alias_equals;

    @GraphQLField
    @GraphQLName("profileID_equals")
    private final String profileID_equals;

    @GraphQLField
    @GraphQLName("clientID_equals")
    private final String clientID_equals;

    public CDPProfileAliasFilterInput(
            @GraphQLName("and") List<CDPProfileAliasFilterInput> and,
            @GraphQLName("or") List<CDPProfileAliasFilterInput> or,
            @GraphQLName("alias_equals") String alias_equals,
            @GraphQLName("profileID_equals") String profileID_equals,
            @GraphQLName("clientID_equals") String clientID_equals) {
        this.and = and;
        this.or = or;
        this.alias_equals = alias_equals;
        this.profileID_equals = profileID_equals;
        this.clientID_equals = clientID_equals;
    }

    public List<CDPProfileAliasFilterInput> getAnd() {
        return and;
    }

    public List<CDPProfileAliasFilterInput> getOr() {
        return or;
    }

    public String getAlias_equals() {
        return alias_equals;
    }

    public String getProfileID_equals() {
        return profileID_equals;
    }

    public String getClientID_equals() {
        return clientID_equals;
    }
}
