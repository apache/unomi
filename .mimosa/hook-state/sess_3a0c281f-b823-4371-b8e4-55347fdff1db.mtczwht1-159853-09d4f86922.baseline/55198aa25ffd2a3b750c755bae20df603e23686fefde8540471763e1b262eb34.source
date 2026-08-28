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
import org.apache.unomi.api.Event;

import java.util.List;

@GraphQLName("CDP_Object")
@GraphQLDescription("Objects are representations of anything users interact with. For example: a web page, a product or another person.")
public class CDPObject {

    private Event event;

    public CDPObject(Event event) {
        this.event = event;
    }

    @GraphQLID
    @GraphQLField
    @GraphQLNonNull
    public String uri() {
        return String.format("%s:%s", event.getItemType(), event.getItemId());
    }

    @GraphQLField
    public String scheme() {
        return event.getItemType();
    }

    @GraphQLField
    public String path() {
        return event.getItemId();
    }

    @GraphQLField
    public List<CDPTopic> topics(final DataFetchingEnvironment environment) {
        return null;
    }

}
