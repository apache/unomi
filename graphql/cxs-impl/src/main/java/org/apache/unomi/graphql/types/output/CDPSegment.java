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
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.graphql.conditionparsers.SegmentConditionParser;

import static org.apache.unomi.graphql.types.output.CDPSegment.TYPE_NAME;

@GraphQLName(TYPE_NAME)
public class CDPSegment {

    public static final String TYPE_NAME = "CDP_Segment";

    @GraphQLField
    private String id;

    @GraphQLField
    private CDPView view;

    @GraphQLField
    private String name;

    @GraphQLField
    private Object filter;

    private Segment segment;

    public CDPSegment(Segment segment) {
        this.segment = segment;
    }

    public String getId() {
        return segment.getItemId();
    }

    public CDPView getView() {
        return new CDPView(segment.getScope());
    }

    public String getName() {
        if (segment.getMetadata() != null) {
            this.name = segment.getMetadata().getName();
        }
        return name;
    }

    public Object getFilter() {
        return new SegmentConditionParser(segment.getCondition()).parse();
    }
}
