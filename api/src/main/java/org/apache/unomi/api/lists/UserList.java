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

package org.apache.unomi.api.lists;

import org.apache.unomi.api.Item;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.MetadataItem;

/**
 * Created by amidani on 24/03/2017.
 */
public class UserList extends MetadataItem{

    /**
     * The Segment ITEM_TYPE.
     *
     * @see Item for a discussion of ITEM_TYPE
     */
    public static final String ITEM_TYPE = "userList";

    private static final long serialVersionUID = -1384533444875433496L;

    /**
     * Instantiates a new UserList.
     */
    public UserList() {}

    /**
     * Instantiates a new UserList with the specified metadata.
     *
     * @param metadata the metadata
     */
    public UserList(Metadata metadata) {
        super(metadata);
    }

}
