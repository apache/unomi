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
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.graphql.condition.factories.ConditionFactory;
import org.apache.unomi.graphql.converters.UserListConverter;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.services.UserListService;

import java.util.List;
import java.util.stream.Collectors;

import static org.apache.unomi.graphql.types.output.CDPListsUpdateEvent.TYPE_NAME;

@GraphQLName(TYPE_NAME)
@GraphQLDescription("Standard Event to update profile membership for specified lists.")
public class CDPListsUpdateEvent implements CDPEventInterface {

    public static final String TYPE_NAME = "CDP_ListsUpdateEvent";

    private final Event event;

    public CDPListsUpdateEvent(final Event event) {
        this.event = event;
    }

    @Override
    public Event getEvent() {
        return event;
    }

    @GraphQLField
    public List<CDPList> joinLists(final DataFetchingEnvironment environment) {
        return createResult("joinLists", environment);
    }

    @GraphQLField
    public List<CDPList> leaveLists(final DataFetchingEnvironment environment) {
        return createResult("leaveLists", environment);
    }

    @SuppressWarnings("unchecked")
    private List<CDPList> createResult(final String propertyName, final DataFetchingEnvironment environment) {
        final List<String> listIds = (List<String>) getProperty(propertyName);

        if (listIds == null || listIds.isEmpty()) {
            return null;
        }

        final ServiceManager serviceManager = environment.getContext();

        final ConditionFactory factory = new ConditionFactory("userListPropertyCondition", environment);

        final Query query = new Query();
        query.setCondition(factory.propertiesCondition("itemId", "in", listIds));

        final PartialList<Metadata> partialList = serviceManager.getService(UserListService.class).getListMetadatas(query);

        if (partialList == null || partialList.getList() == null) {
            return null;
        }

        return partialList.getList().stream()
                .map(UserListConverter::convertToUnomiList)
                .map(CDPList::new)
                .collect(Collectors.toList());
    }

}
