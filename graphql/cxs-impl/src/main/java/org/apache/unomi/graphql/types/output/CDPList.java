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
import graphql.annotations.annotationTypes.GraphQLPrettify;
import org.apache.unomi.api.lists.UserList;
import org.apache.unomi.graphql.fetchers.list.ListProfileConnectionDataFetcher;

@GraphQLName("CDP_List")
public class CDPList {

    private UserList userList;

    public CDPList(final UserList userList) {
        this.userList = userList;
    }

    @GraphQLID
    @GraphQLField
    @GraphQLNonNull
    @GraphQLName("ID")
    public String getId() {
        return userList.getItemId();
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLPrettify
    public CDPView getView() {
        return userList.getScope() != null ? new CDPView(userList.getScope()) : null;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLPrettify
    public String name() {
        return userList.getMetadata() != null ? userList.getMetadata().getName() : null;
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
