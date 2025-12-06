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

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.persistence.elasticsearch.ConditionESQueryBuilder;
import org.apache.unomi.persistence.elasticsearch.ConditionESQueryBuilderDispatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SourceEventPropertyConditionESQueryBuilder implements ConditionESQueryBuilder {

    public SourceEventPropertyConditionESQueryBuilder() {
    }

    private void appendFilterIfPropExist(List<Query> queries, Condition condition, String prop) {
        final Object parameter = condition.getParameter(prop);
        if (parameter != null && !"".equals(parameter)) {
            queries.add(Query.of(q -> q.term(t -> t.field("source." + prop).value(v -> v.stringValue((String) parameter)))));
        }
    }

    public Query buildQuery(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        List<Query> queries = new ArrayList<>();
        for (String prop : new String[] { "id", "path", "scope", "type" }) {
            appendFilterIfPropExist(queries, condition, prop);
        }

        if (queries.isEmpty()) {
            return null;
        } else if (queries.size() == 1) {
            return queries.get(0);
        } else {
            BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
            for (Query queryBuilder : queries) {
                boolQueryBuilder.must(queryBuilder);
            }
            return Query.of(q -> q.bool(boolQueryBuilder.build()));
        }
    }
}
