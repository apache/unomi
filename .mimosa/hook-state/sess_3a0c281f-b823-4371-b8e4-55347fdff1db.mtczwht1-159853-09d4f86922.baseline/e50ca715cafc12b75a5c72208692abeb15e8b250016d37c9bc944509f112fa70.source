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

import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.ProfileAlias;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.graphql.condition.factories.ProfileAliasConditionFactory;
import org.apache.unomi.graphql.fetchers.BaseConnectionDataFetcher;
import org.apache.unomi.graphql.fetchers.ConnectionParams;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.input.CDPOrderByInput;
import org.apache.unomi.graphql.types.input.CDPProfileAliasFilterInput;
import org.apache.unomi.graphql.types.output.CDPPageInfo;
import org.apache.unomi.graphql.types.output.CDPProfileAliasConnection;
import org.apache.unomi.graphql.types.output.CDPProfileAliasEdge;
import org.apache.unomi.persistence.spi.PersistenceService;

import java.util.List;
import java.util.stream.Collectors;

public class FindProfileAliasConnectionDataFetcher extends BaseConnectionDataFetcher<CDPProfileAliasConnection> {

    private final CDPProfileAliasFilterInput filterInput;

    private final List<CDPOrderByInput> orderByInput;

    public FindProfileAliasConnectionDataFetcher(
            final CDPProfileAliasFilterInput filterInput, final List<CDPOrderByInput> orderByInput) {
        this.filterInput = filterInput;
        this.orderByInput = orderByInput;
    }

    @Override
    public CDPProfileAliasConnection get(DataFetchingEnvironment environment) throws Exception {
        final ServiceManager serviceManager = environment.getContext();

        final PersistenceService persistenceService = serviceManager.getService(PersistenceService.class);

        final ConnectionParams params = parseConnectionParams(environment);

        final Query query = buildQuery(createCondition(environment), orderByInput, params);

        final PartialList<ProfileAlias> partialList = persistenceService.query(
                query.getCondition(), query.getSortby(), ProfileAlias.class, query.getOffset(), query.getLimit());

        final List<CDPProfileAliasEdge> edges = partialList.getList().stream().map(CDPProfileAliasEdge::new).collect(Collectors.toList());

        return new CDPProfileAliasConnection(edges, new CDPPageInfo(), partialList.getTotalSize());
    }

    private Condition createCondition(final DataFetchingEnvironment environment) {
        return ProfileAliasConditionFactory.get(environment).filterInputCondition(filterInput, environment.getArgument("filter"));
    }
}
