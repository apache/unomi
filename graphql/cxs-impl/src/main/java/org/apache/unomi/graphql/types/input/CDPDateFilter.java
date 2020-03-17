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

@GraphQLName("CDP_DateFilter")
public class CDPDateFilter {

    @GraphQLField
    private long after;

    @GraphQLField
    private boolean includeAfter;

    @GraphQLField
    private long before;

    @GraphQLField
    private boolean includeBefore;

    public CDPDateFilter(
            final @GraphQLName("after") long after,
            final @GraphQLName("includeAfter") boolean includeAfter,
            final @GraphQLName("before") long before,
            final @GraphQLName("includeBefore") boolean includeBefore) {
        this.after = after;
        this.includeAfter = includeAfter;
        this.before = before;
        this.includeBefore = includeBefore;
    }

    public long getAfter() {
        return after;
    }

    public void setAfter(long after) {
        this.after = after;
    }

    public boolean isIncludeAfter() {
        return includeAfter;
    }

    public void setIncludeAfter(boolean includeAfter) {
        this.includeAfter = includeAfter;
    }

    public long getBefore() {
        return before;
    }

    public void setBefore(long before) {
        this.before = before;
    }

    public boolean isIncludeBefore() {
        return includeBefore;
    }

    public void setIncludeBefore(boolean includeBefore) {
        this.includeBefore = includeBefore;
    }

}
