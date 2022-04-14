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
import graphql.annotations.annotationTypes.GraphQLID;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;

import static org.apache.unomi.graphql.types.input.CDPProfileAliasInput.TYPE_NAME;

@GraphQLName(TYPE_NAME)
public class CDPProfileAliasInput {

    public static final String TYPE_NAME = "CDP_ProfileAliasInput";

    @GraphQLID
    @GraphQLField
    @GraphQLNonNull
    private final String alias;

    @GraphQLField
    @GraphQLNonNull
    private final CDPProfileIDInput profileID;

    public CDPProfileAliasInput(@GraphQLID @GraphQLNonNull @GraphQLName("alias") String alias,
                                @GraphQLNonNull @GraphQLName("profileID") CDPProfileIDInput profileID) {
        this.alias = alias;
        this.profileID = profileID;
    }

    public String getAlias() {
        return alias;
    }

    public CDPProfileIDInput getProfileID() {
        return profileID;
    }
}
