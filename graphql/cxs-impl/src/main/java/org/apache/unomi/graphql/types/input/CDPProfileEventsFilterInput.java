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

@GraphQLName("CDP_ProfileEventsFilterInput")
public class CDPProfileEventsFilterInput {

    @GraphQLField
    private List<CDPProfileEventsFilterInput> and = new ArrayList<>();

    @GraphQLField
    private List<CDPProfileEventsFilterInput> or = new ArrayList<>();

    @GraphQLField
    private CDPProfileEventsFilterInput not;

    @GraphQLField
    private Integer minimalCount;

    @GraphQLField
    private Integer maximalCount;

    @GraphQLField
    private CDPEventFilterInput eventFilter;

    public CDPProfileEventsFilterInput() {
    }

    public CDPProfileEventsFilterInput(@GraphQLName("and") List<CDPProfileEventsFilterInput> and,
                                       @GraphQLName("or") List<CDPProfileEventsFilterInput> or,
                                       @GraphQLName("not") CDPProfileEventsFilterInput not,
                                       @GraphQLName("minimalCount") Integer minimalCount,
                                       @GraphQLName("maximalCount") Integer maximalCount,
                                       @GraphQLName("eventFilter") CDPEventFilterInput eventFilter) {
        this.and = and;
        this.or = or;
        this.not = not;
        this.minimalCount = minimalCount;
        this.maximalCount = maximalCount;
        this.eventFilter = eventFilter;
    }

    public List<CDPProfileEventsFilterInput> getAnd() {
        return and;
    }

    public CDPProfileEventsFilterInput setAnd(List<CDPProfileEventsFilterInput> and) {
        this.and = and;
        return this;
    }

    public List<CDPProfileEventsFilterInput> getOr() {
        return or;
    }

    public CDPProfileEventsFilterInput setOr(List<CDPProfileEventsFilterInput> or) {
        this.or = or;
        return this;
    }

    public CDPProfileEventsFilterInput getNot() {
        return not;
    }

    public CDPProfileEventsFilterInput setNot(CDPProfileEventsFilterInput not) {
        this.not = not;
        return this;
    }

    public Integer getMinimalCount() {
        return minimalCount;
    }

    public CDPProfileEventsFilterInput setMinimalCount(Integer minimalCount) {
        this.minimalCount = minimalCount;
        return this;
    }

    public Integer getMaximalCount() {
        return maximalCount;
    }

    public CDPProfileEventsFilterInput setMaximalCount(Integer maximalCount) {
        this.maximalCount = maximalCount;
        return this;
    }

    public CDPEventFilterInput getEventFilter() {
        return eventFilter;
    }

    public CDPProfileEventsFilterInput setEventFilter(CDPEventFilterInput eventFilter) {
        this.eventFilter = eventFilter;
        return this;
    }
}
