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

import java.util.ArrayList;
import java.util.List;

@GraphQLName("CDP_ProfilePropertiesFilterInput")
public class CDPProfilePropertiesFilterInput {

    @GraphQLField
    private List<CDPProfilePropertiesFilterInput> and = new ArrayList<>();

    @GraphQLField
    private List<CDPProfilePropertiesFilterInput> or = new ArrayList<>();

    public CDPProfilePropertiesFilterInput() {
    }

    public CDPProfilePropertiesFilterInput(@GraphQLName("and") List<CDPProfilePropertiesFilterInput> and,
                                           @GraphQLName("or") List<CDPProfilePropertiesFilterInput> or) {
        this.and = and;
        this.or = or;
    }

    public List<CDPProfilePropertiesFilterInput> getAnd() {
        return and;
    }

    public CDPProfilePropertiesFilterInput setAnd(List<CDPProfilePropertiesFilterInput> and) {
        this.and = and;
        return this;
    }

    public List<CDPProfilePropertiesFilterInput> getOr() {
        return or;
    }

    public CDPProfilePropertiesFilterInput setOr(List<CDPProfilePropertiesFilterInput> or) {
        this.or = or;
        return this;
    }
}
