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
import org.apache.unomi.persistence.spi.conditions.ConditionContextHelper;
import org.apache.unomi.scripting.ScriptExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Elasticsearch query builder for boolean conditions.
 */
public class BooleanConditionESQueryBuilder implements ConditionESQueryBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(BooleanConditionESQueryBuilder.class.getName());

    private ScriptExecutor scriptExecutor;

    public void setScriptExecutor(ScriptExecutor scriptExecutor) {
        this.scriptExecutor = scriptExecutor;
    }

    @Override
    public Query buildQuery(Condition condition, Map<String, Object> context,
                            ConditionESQueryBuilderDispatcher dispatcher) {
        boolean isAndOperator = "and".equalsIgnoreCase((String) condition.getParameter("operator"));
        @SuppressWarnings("unchecked")
        List<Condition> conditions = (List<Condition>) condition.getParameter("subConditions");

        // An empty list matches everything, for AND and OR alike, as in 2.x and 3.x. Callers rely on it,
        // for example EventService.searchEvents with no event types.
        if (conditions == null || conditions.isEmpty()) {
            return Query.of(q -> q.matchAll(m -> m));
        }

        // Omit branches whose optional parameter is unset (UNOMI-1004 for OR; UNOMI-982 for AND).
        ConditionContextHelper.ResolvedBooleanSubConditions resolved =
                ConditionContextHelper.resolveBooleanSubConditions(conditions, context, scriptExecutor);
        if (resolved.hasUnresolvable() && isAndOperator) {
            return Query.of(q -> q.matchNone(m -> m));
        }
        List<Condition> active = resolved.getActive();

        if (active.isEmpty()) {
            return isAndOperator
                    ? Query.of(q -> q.matchAll(m -> m))
                    : Query.of(q -> q.matchNone(m -> m));
        }

        if (active.size() == 1) {
            return dispatcher.buildFilter(active.get(0), context);
        }

        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
        for (Condition sub : active) {
            if (isAndOperator) {
                Query andFilter = dispatcher.buildFilter(sub, context);
                if (andFilter != null) {
                    if (andFilter.isRange()) {
                        boolQueryBuilder.filter(andFilter);
                    } else {
                        boolQueryBuilder.must(andFilter);
                    }
                } else {
                    LOGGER.warn("Null filter for boolean AND sub condition. See debug log level for more information");
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Null filter for boolean AND sub condition {}", sub);
                    }
                }
            } else {
                Query orFilter = dispatcher.buildFilter(sub, context);
                if (orFilter != null) {
                    boolQueryBuilder.should(orFilter);
                } else {
                    LOGGER.warn("Null filter for boolean OR sub condition. See debug log level for more information");
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Null filter for boolean OR sub condition {}", sub);
                    }
                }
            }
        }

        return Query.of(q -> q.bool(boolQueryBuilder.build()));
    }
}
