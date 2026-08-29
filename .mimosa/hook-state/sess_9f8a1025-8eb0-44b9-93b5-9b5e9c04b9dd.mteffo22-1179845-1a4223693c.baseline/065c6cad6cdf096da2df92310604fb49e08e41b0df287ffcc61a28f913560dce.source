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

@GraphQLName("CDP_SegmentFilterInput")
public class CDPSegmentFilterInput {

    @GraphQLField
    @GraphQLName("and")
    private List<CDPSegmentFilterInput> andFilters;

    @GraphQLField
    @GraphQLName("or")
    private List<CDPSegmentFilterInput> orFilters;

    @GraphQLField
    @GraphQLName("view_equals")
    private String viewEquals;

    @GraphQLField
    @GraphQLName("view_regexp")
    private String viewRegexp;

    @GraphQLField
    @GraphQLName("name_equals")
    private String nameEquals;

    @GraphQLField
    @GraphQLName("name_regexp")
    private String nameRegexp;

    public CDPSegmentFilterInput(
            final @GraphQLName("and") List<CDPSegmentFilterInput> andFilters,
            final @GraphQLName("or") List<CDPSegmentFilterInput> orFilters,
            final @GraphQLName("view_equals") String viewEquals,
            final @GraphQLName("view_regexp") String viewRegexp,
            final @GraphQLName("name_equals") String nameEquals,
            final @GraphQLName("name_regexp") String nameRegexp) {
        this.andFilters = andFilters;
        this.orFilters = orFilters;
        this.viewEquals = viewEquals;
        this.viewRegexp = viewRegexp;
        this.nameEquals = nameEquals;
        this.nameRegexp = nameRegexp;
    }

    public List<CDPSegmentFilterInput> getAndFilters() {
        return andFilters;
    }

    public List<CDPSegmentFilterInput> getOrFilters() {
        return orFilters;
    }

    public String getViewEquals() {
        return viewEquals;
    }

    public String getViewRegexp() {
        return viewRegexp;
    }

    public String getNameEquals() {
        return nameEquals;
    }

    public String getNameRegexp() {
        return nameRegexp;
    }
}
