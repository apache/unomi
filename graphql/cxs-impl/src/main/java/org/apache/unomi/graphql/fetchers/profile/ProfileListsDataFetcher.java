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

package org.apache.unomi.graphql.fetchers.profile;

import com.google.common.collect.Iterables;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.lists.UserList;
import org.apache.unomi.api.services.UserListService;
import org.apache.unomi.graphql.fetchers.BaseDataFetcher;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.output.CDPList;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProfileListsDataFetcher extends BaseDataFetcher<List<CDPList>> {

    private final Profile profile;

    private final List<String> viewIds;

    public ProfileListsDataFetcher(Profile profile, List<String> viewIds) {
        this.profile = profile;
        this.viewIds = viewIds;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<CDPList> get(DataFetchingEnvironment environment) throws Exception {
        final ServiceManager serviceManager = environment.getContext();
        final UserListService userListService = serviceManager.getUserListService();
        final Map<String, Object> systemProperties = profile.getSystemProperties();
        if (systemProperties == null) {
            return null;
        }

        final List<String> listIds = (List<String>) systemProperties.get("lists");
        if (listIds == null) {
            return null;
        }

        Stream<String> stream = listIds.stream();

        final List<UserList> userLists = userListService.getAllUserLists();

        if (viewIds != null) {
            stream = stream.filter(viewIds::contains);
        }

        return stream.map(viewId -> {
            final UserList list = Iterables.tryFind(userLists, userList -> viewId.equals(userList.getItemId())).orNull();
            return list != null ? new CDPList(list) : null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }
}
