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
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.graphql.CDPGraphQLConstants;

import java.util.LinkedHashMap;
import java.util.List;

import static org.apache.unomi.graphql.types.input.CDPListsUpdateEventInput.TYPE_NAME;

@GraphQLName(TYPE_NAME)
public class CDPListsUpdateEventInput extends BaseProfileEventProcessor {

    public static final String TYPE_NAME = "CDP_ListsUpdateEventInput";

    public static final String EVENT_NAME = CDPGraphQLConstants.CDP_LIST_UPDATE_EVENT_NAME;

    @GraphQLField
    private List<String> joinLists;

    @GraphQLField
    private List<String> leaveLists;

    public CDPListsUpdateEventInput(final @GraphQLName("joinLists") List<String> joinLists,
                                    final @GraphQLName("leaveLists") List<String> leaveLists) {
        this.joinLists = joinLists;
        this.leaveLists = leaveLists;
    }

    public List<String> getJoinLists() {
        return joinLists;
    }

    public List<String> getLeaveLists() {
        return leaveLists;
    }

    @Override
    public Event buildEvent(LinkedHashMap<String, Object> eventInputAsMap, DataFetchingEnvironment environment) {
        final Profile profile = loadProfile(eventInputAsMap, environment);

        if (profile == null) {
            return null;
        }

        return eventBuilder(EVENT_NAME, profile)
                .setProperty("joinLists", joinLists)
                .setProperty("leaveLists", leaveLists)
                .setPersistent(true)
                .build();
    }

    @Override
    public String getFieldName() {
        return EVENT_NAME;
    }
}
