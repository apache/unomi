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

package org.apache.unomi.graphql.types.input;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;

import java.util.List;
import java.util.Map;

@GraphQLName("CDP_ListsUpdateEventFilterInput")
public class CDPListsUpdateEventFilterInput implements EventFilterInputMarker {

    @GraphQLField
    private List<String> joinLists_contains;

    @GraphQLField
    private List<String> leaveLists_contains;

    public CDPListsUpdateEventFilterInput(final @GraphQLName("joinLists_contains") List<String> joinLists_contains,
                                          final @GraphQLName("leaveLists_contains") List<String> leaveLists_contains) {
        this.joinLists_contains = joinLists_contains;
        this.leaveLists_contains = leaveLists_contains;
    }

    public static CDPListsUpdateEventFilterInput fromMap(final Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        final List<String> joinLists = (List<String>) map.get("joinLists_contains");
        final List<String> leaveLists = (List<String>) map.get("leaveLists_contains");
        return new CDPListsUpdateEventFilterInput(joinLists, leaveLists);
    }

    public List<String> getJoinLists_contains() {
        return joinLists_contains;
    }

    public List<String> getLeaveLists_contains() {
        return leaveLists_contains;
    }
}
