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

@GraphQLName("CDP_Segment")
public class CDPSegment {
    @GraphQLField
    private String id;
    @GraphQLField
    private CDPView view;
    @GraphQLField
    private String name;
    @GraphQLField
    private CDPSegmentCondition condition;

    public CDPSegment(Segment segment) {
        id = segment.getItemId();
        view = new CDPView(segment.getScope());
        if (segment.getMetadata() != null) {
            name = segment.getMetadata().getName();
        }
        condition = new CDPSegmentCondition(segment.getCondition());
    }

    public String getId() {
        return id;
    }

    public CDPView getView() {
        return view;
    }

    public String getName() {
        return name;
    }

    public CDPSegmentCondition getCondition() {
        return condition;
    }
}
