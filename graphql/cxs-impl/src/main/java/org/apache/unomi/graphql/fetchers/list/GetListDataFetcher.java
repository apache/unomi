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
package org.apache.unomi.graphql.fetchers.list;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.graphql.converters.UserListConverter;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.output.CDPList;
import org.apache.unomi.lists.UserList;
import org.apache.unomi.services.UserListService;

public class GetListDataFetcher implements DataFetcher<CDPList> {

    private final String listId;

    public GetListDataFetcher(final String listId) {
        this.listId = listId;
    }

    @Override
    public CDPList get(final DataFetchingEnvironment environment) throws Exception {
        final ServiceManager serviceManager = environment.getContext();

        final UserList userList = serviceManager.getService(UserListService.class).load(listId);

        return userList != null ? new CDPList(UserListConverter.convertToUnomiList(userList)) : null;
    }

}
