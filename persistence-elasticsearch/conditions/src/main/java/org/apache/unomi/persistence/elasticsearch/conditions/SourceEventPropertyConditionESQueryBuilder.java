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

package org.apache.unomi.persistence.elasticsearch.conditions;

import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.persistence.elasticsearch.ConditionESQueryBuilder;
import org.apache.unomi.persistence.elasticsearch.ConditionESQueryBuilderDispatcher;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SourceEventPropertyConditionESQueryBuilder implements ConditionESQueryBuilder {

    public SourceEventPropertyConditionESQueryBuilder() {
    }

    private void appendFilderIfPropExist(List<QueryBuilder> queryBuilders, Condition condition, String prop){
        final Object parameter = condition.getParameter(prop);
        if (parameter != null && !"".equals(parameter)) {
            queryBuilders.add(QueryBuilders.termQuery("source." + prop, (String) parameter));
        }
    }

    public QueryBuilder buildQuery(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        List<QueryBuilder> queryBuilders = new ArrayList<QueryBuilder>();
        for (String prop : new String[]{"id", "path", "scope", "type"}){
            appendFilderIfPropExist(queryBuilders, condition, prop);
        }

        if (queryBuilders.size() >= 1) {
            if (queryBuilders.size() == 1) {
                return queryBuilders.get(0);
            } else {
                BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
                for (QueryBuilder queryBuilder : queryBuilders) {
                    boolQueryBuilder.must(queryBuilder);
                }
                return boolQueryBuilder;
            }
        } else {
            return null;
        }
    }
}
