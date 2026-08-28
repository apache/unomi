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
import org.apache.unomi.api.lists.UserList;
import org.apache.unomi.graphql.fetchers.list.ListProfileConnectionDataFetcher;

import static org.apache.unomi.graphql.types.output.CDPList.TYPE_NAME;

@GraphQLName(TYPE_NAME)
@GraphQLDescription("Lists are explicitly created and named in the Customer Data Platform. Profiles may then be added to a list, and later opt out if desired.")
public class CDPList {

    public static final String TYPE_NAME = "CDP_List";

    private UserList userList;

    public CDPList(final UserList userList) {
        this.userList = userList;
    }

    @GraphQLID
    @GraphQLField
    @GraphQLNonNull
    public String id() {
        return userList.getItemId();
    }

    @GraphQLField
    @GraphQLNonNull
    public CDPView view() {
        return userList.getScope() != null ? new CDPView(userList.getScope()) : null;
    }

    @GraphQLField
    @GraphQLNonNull
    public String name() {
        return userList.getMetadata() != null ? userList.getMetadata().getName() : null;
    }

    @GraphQLField
    public CDPProfileConnection active(
            final @GraphQLName("first") Integer first,
            final @GraphQLName("after") String after,
            final @GraphQLName("last") Integer last,
            final @GraphQLName("before") String before,
            final DataFetchingEnvironment environment) throws Exception {
        return new ListProfileConnectionDataFetcher().get(environment);
    }

    @GraphQLField
    public CDPProfileConnection inactive(
            @GraphQLName("first") Integer first,
            @GraphQLName("after") String after,
            @GraphQLName("last") Integer last,
            @GraphQLName("before") String before) {
        return new CDPProfileConnection();
    }

}
