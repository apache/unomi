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
package org.apache.unomi.graphql.converters;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.lists.UserList;

public interface UserListConverter {

    static UserList convertToUnomiList(final org.apache.unomi.lists.UserList userList) {
        final UserList result = new UserList();

        result.setItemId(userList.getItemId());
        result.setItemType(userList.getItemType());
        result.setMetadata(userList.getMetadata());
        result.setScope(userList.getScope());
        result.setVersion(userList.getVersion());

        return result;
    }

    static UserList convertToUnomiList(final Metadata metadata) {
        final UserList result = new UserList();

        result.setItemId(metadata.getId());
        result.setItemType(UserList.ITEM_TYPE);
        result.setMetadata(metadata);
        result.setScope(metadata.getScope());

        return result;
    }
}
