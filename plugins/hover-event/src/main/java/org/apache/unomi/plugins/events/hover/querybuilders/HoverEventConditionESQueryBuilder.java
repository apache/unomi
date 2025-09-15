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

package org.apache.unomi.plugins.events.hover.querybuilders;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.persistence.elasticsearch.ConditionESQueryBuilder;
import org.apache.unomi.persistence.elasticsearch.ConditionESQueryBuilderDispatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Condition builder for hover event types
 */
public class HoverEventConditionESQueryBuilder implements ConditionESQueryBuilder {

    public HoverEventConditionESQueryBuilder() {
    }

    public Query buildQuery(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        List<Query> queries = new ArrayList<>();
        queries.add(QueryBuilders.term(builder -> builder.field("eventType").value("hover")));
        String targetId = (String) condition.getParameter("targetId");
        String targetPath = (String) condition.getParameter("targetPath");

        if (targetId != null && !targetId.trim().isEmpty()) {
            queries.add(QueryBuilders.term(builder -> builder.field("target.itemId").value(targetId)));
        } else if (targetPath != null && targetPath.trim().length() > 0) {
            queries.add(QueryBuilders.term(builder -> builder.field("target.properties.pageInfo.pagePath").value(targetPath)));
        } else {
            queries.add(QueryBuilders.term(builder -> builder.field("target.itemId").value("")));
        }
        return QueryBuilders.bool().must(queries).build()._toQuery();
    }
}
