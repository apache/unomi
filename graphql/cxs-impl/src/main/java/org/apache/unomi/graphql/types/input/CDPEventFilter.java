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

@GraphQLName("CDP_EventFilter")
public class CDPEventFilter {

    @GraphQLField
    @GraphQLName("and")
    private List<CDPEventFilter> andFilters;

    @GraphQLField
    @GraphQLName("or")
    private List<CDPEventFilter> orFilters;

    public CDPEventFilter() {
    }

    public CDPEventFilter(
            final @GraphQLName("and") List<CDPEventFilter> andFilters,
            final @GraphQLName("or") List<CDPEventFilter> orFilters) {
        this.andFilters = andFilters;
        this.orFilters = orFilters;
    }

    public List<CDPEventFilter> getAndFilters() {
        return andFilters;
    }

    public void setAndFilters(List<CDPEventFilter> andFilters) {
        this.andFilters = andFilters;
    }

    public List<CDPEventFilter> getOrFilters() {
        return orFilters;
    }

    public void setOrFilters(List<CDPEventFilter> orFilters) {
        this.orFilters = orFilters;
    }

}
