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

import graphql.annotations.annotationTypes.GraphQLDataFetcher;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLID;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.apache.unomi.graphql.fetchers.list.ListProfileConnectionDataFetcher;

@GraphQLName("CDP_List")
public class CDPList {

    @GraphQLID
    @GraphQLField
    @GraphQLNonNull
    @GraphQLName("ID")
    private String id;

    @GraphQLField
    @GraphQLNonNull
    private CDPView view;

    @GraphQLField
    @GraphQLNonNull
    private String name;

    public CDPList(final String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public CDPView getView() {
        return view;
    }

    public String name() {
        return name;
    }

    @GraphQLField
    @GraphQLDataFetcher(value = ListProfileConnectionDataFetcher.class, args = {ListProfileConnectionDataFetcher.ACTIVE})
    public CDPProfileConnection active(
            @GraphQLName("first") Integer first,
            @GraphQLName("after") String after,
            @GraphQLName("last") Integer last,
            @GraphQLName("before") String before
    ) {
        return null;
    }

    @GraphQLField
    @GraphQLDataFetcher(value = ListProfileConnectionDataFetcher.class, args = {ListProfileConnectionDataFetcher.INACTIVE})
    public CDPProfileConnection inactive(
            @GraphQLName("first") Integer first,
            @GraphQLName("after") String after,
            @GraphQLName("last") Integer last,
            @GraphQLName("before") String before
    ) {
        return null;
    }

}
