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

package org.apache.unomi.persistence.elasticsearch;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.persistence.spi.conditions.ConditionContextHelper;
import org.apache.unomi.scripting.ScriptExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConditionESQueryBuilderDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConditionESQueryBuilderDispatcher.class.getName());

    private Map<String, ConditionESQueryBuilder> queryBuilders = new ConcurrentHashMap<>();
    private ScriptExecutor scriptExecutor;

    public ConditionESQueryBuilderDispatcher() {
    }

    public void setScriptExecutor(ScriptExecutor scriptExecutor) {
        this.scriptExecutor = scriptExecutor;
    }

    public void addQueryBuilder(String name, ConditionESQueryBuilder evaluator) {
        queryBuilders.put(name, evaluator);
    }

    public void removeQueryBuilder(String name) {
        queryBuilders.remove(name);
    }


    public String getQuery(Condition condition) {
        return "{\"query\": " + getQueryBuilder(condition).toString() + "}";
    }

    public Query getQueryBuilder(Condition condition) {
        Query.Builder qb = new Query.Builder();
        return qb.bool(b -> b.must(Query.of(q -> q.matchAll(m -> m))).filter(buildFilter(condition))).build();

    }

    public Query buildFilter(Condition condition) {
        return buildFilter(condition, new HashMap<>());
    }

    public Query buildFilter(Condition condition, Map<String, Object> context) {
        if (condition == null || condition.getConditionType() == null) {
            throw new IllegalArgumentException("Condition is null or doesn't have type, impossible to build filter");
        }

        String queryBuilderKey = condition.getConditionType().getQueryBuilder();
        if (queryBuilderKey == null && condition.getConditionType().getParentCondition() != null) {
            context.putAll(condition.getParameterValues());
            return buildFilter(condition.getConditionType().getParentCondition(), context);
        }

        if (queryBuilderKey == null) {
            throw new UnsupportedOperationException("No query builder defined for : " + condition.getConditionTypeId());
        }

        if (queryBuilders.containsKey(queryBuilderKey)) {
            ConditionESQueryBuilder queryBuilder = queryBuilders.get(queryBuilderKey);
            Condition contextualCondition = ConditionContextHelper.getContextualCondition(condition, context, scriptExecutor);
            if (contextualCondition != null) {
                return queryBuilder.buildQuery(contextualCondition, context, this);
            }
        } else {
            // if no matching
            LOGGER.warn("No matching query builder. See debug log level for more information");
            LOGGER.debug("No matching query builder for condition {} and context {}", condition, context);
        }

        return Query.of(q -> q.matchAll(m -> m));
    }

    public long count(Condition condition) {
        return count(condition, new HashMap<>());
    }

    public long count(Condition condition, Map<String, Object> context) {
        if (condition == null || condition.getConditionType() == null) {
            throw new IllegalArgumentException("Condition is null or doesn't have type, impossible to build filter");
        }

        String queryBuilderKey = condition.getConditionType().getQueryBuilder();
        if (queryBuilderKey == null && condition.getConditionType().getParentCondition() != null) {
            context.putAll(condition.getParameterValues());
            return count(condition.getConditionType().getParentCondition(), context);
        }

        if (queryBuilderKey == null) {
            throw new UnsupportedOperationException("No query builder defined for : " + condition.getConditionTypeId());
        }

        if (queryBuilders.containsKey(queryBuilderKey)) {
            ConditionESQueryBuilder queryBuilder = queryBuilders.get(queryBuilderKey);
            Condition contextualCondition = ConditionContextHelper.getContextualCondition(condition, context, scriptExecutor);
            if (contextualCondition != null) {
                return queryBuilder.count(contextualCondition, context, this);
            }
        }

        // if no matching
        LOGGER.warn("No matching query builder. See debug log level for more information");
        LOGGER.debug("No matching query builder for condition {} and context {}", condition, context);
        throw new UnsupportedOperationException();
    }
}
