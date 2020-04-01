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
package org.apache.unomi.graphql.fetchers.segment;

import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.graphql.fetchers.BaseDataFetcher;
import org.apache.unomi.graphql.types.output.CDPProfileFilter;

import java.util.Collections;
import java.util.List;

abstract class BaseSegmentContainsDataFetcher extends BaseDataFetcher<List<String>> {

    @SuppressWarnings("unchecked")
    final List<Condition> getSubConditions(final DataFetchingEnvironment environment) {
        final CDPProfileFilter source = environment.getSource();

        final Condition rootCondition = source.getSegment().getCondition();

        final List<Condition> subConditions = (List<Condition>) rootCondition.getParameter("subConditions");

        if (subConditions == null || subConditions.isEmpty()) {
            return Collections.emptyList();
        }

        return subConditions;
    }

}
