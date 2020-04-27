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

import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.graphql.condition.factories.ConditionFactory;
import org.apache.unomi.graphql.fetchers.BaseConnectionDataFetcher;
import org.apache.unomi.graphql.fetchers.ConnectionParams;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.input.CDPListFilterInput;
import org.apache.unomi.graphql.types.input.CDPOrderByInput;
import org.apache.unomi.graphql.types.output.CDPListConnection;

import java.util.ArrayList;
import java.util.List;

public class ListConnectionDataFetcher extends BaseConnectionDataFetcher<CDPListConnection> {

    private final CDPListFilterInput filterInput;

    private final List<CDPOrderByInput> orderByInput;

    public ListConnectionDataFetcher(final CDPListFilterInput filterInput, final List<CDPOrderByInput> orderByInput) {
        this.filterInput = filterInput;
        this.orderByInput = orderByInput;
    }

    @Override
    public CDPListConnection get(final DataFetchingEnvironment environment) throws Exception {
        final ServiceManager serviceManager = environment.getContext();

        final ConnectionParams params = parseConnectionParams(environment);

        final Query query = buildQuery(createCondition(environment), orderByInput, params);

        final PartialList<Metadata> metadataPartialList = serviceManager.getUserListServiceExt().getListMetadatas(query);

        return new CDPListConnection(metadataPartialList);
    }

    private Condition createCondition(final DataFetchingEnvironment environment) {
        final ConditionFactory factory = new ConditionFactory("userListPropertyCondition", environment);

        return createFilterCondition(factory, filterInput);
    }

    private Condition createFilterCondition(
            final ConditionFactory factory, final CDPListFilterInput filterInput) {
        final List<Condition> conditions = new ArrayList<>();

        if (filterInput.getName_equals() != null) {
            conditions.add(factory.propertyCondition("metadata.name", filterInput.getName_equals()));
        }

        if (filterInput.getView_equals() != null) {
            conditions.add(factory.propertyCondition("metadata.scope", filterInput.getView_equals()));
        }

        if (filterInput.getAnd() != null && !filterInput.getAnd().isEmpty()) {
            conditions.add(
                    factory.filtersToCondition(filterInput.getAnd(), cdpListFilterInput -> createFilterCondition(factory, cdpListFilterInput), "and"));
        }

        if (filterInput.getOr() != null && !filterInput.getOr().isEmpty()) {
            conditions.add(
                    factory.filtersToCondition(filterInput.getOr(), cdpListFilterInput -> createFilterCondition(factory, cdpListFilterInput), "or"));
        }

        return factory.booleanCondition("and", conditions);
    }

}
