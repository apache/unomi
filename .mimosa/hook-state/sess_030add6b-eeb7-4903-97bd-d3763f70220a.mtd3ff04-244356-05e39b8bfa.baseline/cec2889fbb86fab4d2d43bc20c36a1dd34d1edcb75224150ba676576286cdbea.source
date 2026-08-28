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

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.graphql.condition.parsers.SegmentConditionParser;

import static org.apache.unomi.graphql.types.output.CDPSegment.TYPE_NAME;

@GraphQLName(TYPE_NAME)
@GraphQLDescription("Segments are similar to lists in that profiles may be in the segment, or not. However, where profiles are explicitly added to lists, they are dynamically resolved to segments based on the filter defined in the segment.")
public class CDPSegment {

    public static final String TYPE_NAME = "CDP_Segment";

    private Segment segment;

    public CDPSegment(Segment segment) {
        this.segment = segment;
    }

    public Segment getSegment() {
        return segment;
    }

    @GraphQLField
    public String id() {
        return segment != null ? segment.getItemId() : null;
    }

    @GraphQLField
    public CDPView view() {
        if (segment == null) {
            return null;
        }

        return segment.getScope() != null ? new CDPView(segment.getScope()) : null;
    }

    @GraphQLField
    public String name() {
        if (segment != null && segment.getMetadata() != null) {
            return segment.getMetadata().getName();
        }

        return null;
    }

    @GraphQLField
    public Object filter(final DataFetchingEnvironment environment) {
        return new SegmentConditionParser(segment.getCondition(), environment).parse();
    }

}
