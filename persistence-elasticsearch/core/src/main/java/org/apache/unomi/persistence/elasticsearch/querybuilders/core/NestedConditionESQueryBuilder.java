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

package org.apache.unomi.persistence.elasticsearch.querybuilders.core;

import org.apache.lucene.search.join.ScoreMode;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.persistence.elasticsearch.ConditionESQueryBuilder;
import org.apache.unomi.persistence.elasticsearch.ConditionESQueryBuilderDispatcher;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import java.util.Map;

public class NestedConditionESQueryBuilder implements ConditionESQueryBuilder {
    @Override
    public QueryBuilder buildQuery(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        String path = (String) condition.getParameter("path");
        Condition subCondition = (Condition) condition.getParameter("subCondition");

        if (subCondition == null || path == null) {
            throw new IllegalArgumentException("Impossible to build Nested query, subCondition and path properties should be provided");
        }

        QueryBuilder nestedQueryBuilder = dispatcher.buildFilter(subCondition, context);
        if (nestedQueryBuilder != null) {
            return QueryBuilders.nestedQuery(path, nestedQueryBuilder, ScoreMode.Avg);
        } else {
            throw new IllegalArgumentException("Impossible to build Nested query due to subCondition filter null");
        }
    }
}
