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

import java.util.List;

import static org.apache.unomi.graphql.types.input.CDPTopicFilterInput.TYPE_NAME;

@GraphQLName(TYPE_NAME)
public class CDPTopicFilterInput {

    public static final String TYPE_NAME = "CDP_TopicFilterInput";

    @GraphQLField
    private List<CDPTopicFilterInput> and;

    @GraphQLField
    private List<CDPTopicFilterInput> or;

    @GraphQLID
    @GraphQLField
    private String id_equals;

    @GraphQLField
    private String view_equals;

    @GraphQLField
    private String name_equals;

    public CDPTopicFilterInput(
            final @GraphQLName("and") List<CDPTopicFilterInput> and,
            final @GraphQLName("or") List<CDPTopicFilterInput> or,
            final @GraphQLName("id_equals") @GraphQLID String id_equals,
            final @GraphQLName("view_equals") String view_equals,
            final @GraphQLName("name_equals") String name_equals) {
        this.and = and;
        this.or = or;
        this.id_equals = id_equals;
        this.view_equals = view_equals;
        this.name_equals = name_equals;
    }

    public List<CDPTopicFilterInput> getAnd() {
        return and;
    }

    public List<CDPTopicFilterInput> getOr() {
        return or;
    }

    public String getId_equals() {
        return id_equals;
    }

    public String getView_equals() {
        return view_equals;
    }

    public String getName_equals() {
        return name_equals;
    }

}
