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

import static org.apache.unomi.graphql.types.input.UnomiSegmentInput.TYPE_NAME;

@GraphQLName(TYPE_NAME)
public class UnomiSegmentInput implements BaseSegmentInput {

    public static final String TYPE_NAME = "Unomi_SegmentInput";

    @GraphQLID
    @GraphQLField
    private String id;

    @GraphQLID
    @GraphQLField
    @GraphQLNonNull
    private String view;

    @GraphQLField
    @GraphQLNonNull
    private String name;

    @GraphQLField
    @GraphQLNonNull
    private Object condition;

    public UnomiSegmentInput(
            final @GraphQLID @GraphQLName("id") String id,
            final @GraphQLID @GraphQLNonNull @GraphQLName("view") String view,
            final @GraphQLNonNull @GraphQLName("name") String name,
            final @GraphQLNonNull @GraphQLName("condition") Object condition) {
        this.id = id;
        this.view = view;
        this.name = name;
        this.condition = condition;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getView() {
        return view;
    }

    @Override
    public String getName() {
        return name;
    }

    public Object getCondition() {
        return condition;
    }

}
