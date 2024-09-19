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
package org.apache.unomi.graphql.fetchers;

import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Topic;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.TopicService;
import org.apache.unomi.graphql.condition.factories.TopicConditionFactory;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.input.CDPOrderByInput;
import org.apache.unomi.graphql.types.input.CDPTopicFilterInput;
import org.apache.unomi.graphql.types.output.CDPPageInfo;
import org.apache.unomi.graphql.types.output.CDPTopicConnection;
import org.apache.unomi.graphql.types.output.CDPTopicEdge;

import java.util.List;
import java.util.stream.Collectors;

public class FindTopicsConnectionDataFetcher extends BaseConnectionDataFetcher<CDPTopicConnection> {

    private final CDPTopicFilterInput filterInput;

    private final List<CDPOrderByInput> orderByInput;

    public FindTopicsConnectionDataFetcher(
            final CDPTopicFilterInput filterInput, final List<CDPOrderByInput> orderByInput) {
        this.filterInput = filterInput;
        this.orderByInput = orderByInput;
    }

    @Override
    public CDPTopicConnection get(DataFetchingEnvironment environment) throws Exception {
        final ServiceManager serviceManager = environment.getContext();

        final TopicService topicService = serviceManager.getService(TopicService.class);

        final ConnectionParams params = parseConnectionParams(environment);

        final Query query = buildQuery(createCondition(environment), orderByInput, params);

        final PartialList<Topic> topicPartialList = topicService.search(query);

        final List<CDPTopicEdge> edges = topicPartialList.getList().stream().map(CDPTopicEdge::new).collect(Collectors.toList());

        return new CDPTopicConnection(topicPartialList.getTotalSize(), edges, new CDPPageInfo());
    }

    private Condition createCondition(final DataFetchingEnvironment environment) {
        return TopicConditionFactory.get(environment).filterInputCondition(filterInput, environment.getArgument("filter"));
    }

}
