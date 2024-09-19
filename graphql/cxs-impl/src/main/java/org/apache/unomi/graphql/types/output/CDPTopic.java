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
import graphql.annotations.annotationTypes.GraphQLID;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.Topic;

import static org.apache.unomi.graphql.types.output.CDPTopic.TYPE_NAME;

@GraphQLName(TYPE_NAME)
@GraphQLDescription("Topics represent the core entities of the business that is using the Customer Data Platform. The Customer Data Platform aims to find correlation between profiles and the topics. When such correlations are identified, it is called Interests.")
public class CDPTopic {

    public static final String TYPE_NAME = "CDP_Topic";

    private final Topic topic;

    public CDPTopic() {
        this(null);
    }

    public CDPTopic(Topic topic) {
        this.topic = topic;
    }

    @GraphQLID
    @GraphQLField
    @GraphQLNonNull
    public String id(final DataFetchingEnvironment environment) {
        return topic != null ? topic.getTopicId() : null;
    }

    @GraphQLField
    @GraphQLNonNull
    public String name(final DataFetchingEnvironment environment) {
        return topic != null ? topic.getName() : null;
    }

    @GraphQLField
    @GraphQLNonNull
    public CDPView view(final DataFetchingEnvironment environment) {
        return topic != null ? new CDPView(topic.getScope()) : null;
    }

}
