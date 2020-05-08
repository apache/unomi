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
package org.apache.unomi.graphql.fetchers.event;

import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.graphql.condition.factories.EventConditionFactory;
import org.apache.unomi.graphql.fetchers.BaseDataFetcher;
import org.apache.unomi.graphql.types.input.CDPEventFilterInput;
import org.apache.unomi.graphql.types.output.CDPEventInterface;
import org.apache.unomi.graphql.utils.GraphQLObjectMapper;
import org.reactivestreams.Publisher;

import java.util.Map;

public class EventListenerSubscriptionFetcher extends BaseDataFetcher<Publisher<CDPEventInterface>> {

    private UnomiEventPublisher eventPublisher;

    public EventListenerSubscriptionFetcher(UnomiEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Publisher<CDPEventInterface> get(DataFetchingEnvironment environment) throws Exception {
        Map<String, Object> filterAsMap = environment.getArgument("filter");
        if (filterAsMap == null) {
            return eventPublisher.createPublisher();
        } else {
            final CDPEventFilterInput filterInput = GraphQLObjectMapper.getInstance().convertValue(filterAsMap, CDPEventFilterInput.class);
            final Condition filterCondition = EventConditionFactory.get(environment).eventFilterInputCondition(filterInput, filterAsMap);

            return eventPublisher.createPublisher(filterCondition);
        }
    }
}
