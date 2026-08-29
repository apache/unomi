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

@GraphQLName("CDP_ProfileEventsFilterInput")
public class CDPProfileEventsFilterInput {

    @GraphQLField
    @GraphQLName("and")
    private List<CDPProfileEventsFilterInput> and;

    @GraphQLField
    @GraphQLName("or")
    private List<CDPProfileEventsFilterInput> or;

    @GraphQLField
    private CDPProfileEventsFilterInput not;

    @GraphQLField
    private Integer minimalCount;

    @GraphQLField
    private Integer maximalCount;

    @GraphQLField
    private CDPEventFilterInput eventFilter;

    public CDPProfileEventsFilterInput(final @GraphQLName("and") List<CDPProfileEventsFilterInput> and,
                                       final @GraphQLName("or") List<CDPProfileEventsFilterInput> or,
                                       final @GraphQLName("not") CDPProfileEventsFilterInput not,
                                       final @GraphQLName("minimalCount") Integer minimalCount,
                                       final @GraphQLName("maximalCount") Integer maximalCount,
                                       final @GraphQLName("eventFilter") CDPEventFilterInput eventFilter) {
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

    public List<CDPProfileEventsFilterInput> getOr() {
        return or;
    }

    public CDPProfileEventsFilterInput getNot() {
        return not;
    }

    public Integer getMinimalCount() {
        return minimalCount;
    }

    public Integer getMaximalCount() {
        return maximalCount;
    }

    public CDPEventFilterInput getEventFilter() {
        return eventFilter;
    }
}
