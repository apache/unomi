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
package org.jahia.unomi.services;

import org.jahia.unomi.lists.UserList;
import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.PartialList;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.query.Query;

import java.util.Set;


/**
 * @author Christophe Laprun
 */
public interface UserListService {

    public PartialList<Metadata> getListMetadatas(int offset, int size, String sortBy);

    public PartialList<Metadata> getListMetadatas(Query query);

    UserList load(String listId);

    void save(UserList list);

    void delete(String listId);
}
