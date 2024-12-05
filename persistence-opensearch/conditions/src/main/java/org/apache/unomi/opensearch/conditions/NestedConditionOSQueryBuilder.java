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

package org.apache.unomi.opensearch.conditions;

import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.persistence.opensearch.conditions.ConditionOSQueryBuilder;
import org.apache.unomi.persistence.opensearch.conditions.ConditionOSQueryBuilderDispatcher;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.Query;

import java.util.Map;

public class NestedConditionOSQueryBuilder implements ConditionOSQueryBuilder {
    @Override
    public Query buildQuery(Condition condition, Map<String, Object> context, ConditionOSQueryBuilderDispatcher dispatcher) {
        String path = (String) condition.getParameter("path");
        Condition subCondition = (Condition) condition.getParameter("subCondition");

        if (subCondition == null || path == null) {
            throw new IllegalArgumentException("Impossible to build Nested query, subCondition and path properties should be provided");
        }

        Query nestedQuery = dispatcher.buildFilter(subCondition, context);
        if (nestedQuery != null) {
            return Query.of(q->q.nested(n->n.path(path).query(nestedQuery).scoreMode(ChildScoreMode.Avg)));
        } else {
            throw new IllegalArgumentException("Impossible to build Nested query due to subCondition filter null");
        }
    }
}
