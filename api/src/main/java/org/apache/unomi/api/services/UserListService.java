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

package org.apache.unomi.api.services;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.lists.UserList;

import java.util.List;

/**
 * Created by amidani on 24/03/2017.
 */
public interface UserListService {
    /**
     *
     * @return a {@link List} of {@link UserList}
     */
    List<UserList> getAllUserLists();

    /**
     * Retrieves user lists metadatas, ordered according to the specified {@code sortBy} String and and paged: only {@code size} of them are retrieved, starting with the {@code
     * offset}-th one.
     *
     * @param offset zero or a positive integer specifying the position of the first element in the total ordered collection of matching elements
     * @param size   a positive integer specifying how many matching elements should be retrieved or {@code -1} if all of them should be retrieved
     * @param sortBy an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering elements according to the property order in the
     *               String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally followed by
     *               a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @return a {@link PartialList} of {@link UserList} metadata
     */
    public PartialList<Metadata> getUserListMetadatas(int offset, int size, String sortBy);

}
