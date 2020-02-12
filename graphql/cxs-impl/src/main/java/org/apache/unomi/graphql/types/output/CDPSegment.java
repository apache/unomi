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
package org.apache.unomi.graphql.types.output;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;

@GraphQLName("CDP_Segment")
public class CDPSegment {
    @GraphQLField
    public String id;
    @GraphQLField
    public CDPView view;
    @GraphQLField
    public String name;
    @GraphQLField
    public CDPSegmentCondition condition;

    private CDPSegment(Builder builder) {
        this.id = builder.id;
        this.view = builder.view;
        this.name = builder.name;
        this.condition = builder.condition;
    }

    public static Builder create() {
        return new Builder();
    }

    public static class Builder {

        private String id;
        private CDPView view;
        private String name;
        private CDPSegmentCondition condition;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder view(CDPView view) {
            this.view = view;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder condition(CDPSegmentCondition condition) {
            this.condition = condition;
            return this;
        }

        public CDPSegment build() {
            return new CDPSegment(this);
        }
    }
}
