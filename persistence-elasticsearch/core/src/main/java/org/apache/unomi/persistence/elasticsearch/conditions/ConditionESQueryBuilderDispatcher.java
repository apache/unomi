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
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConditionESQueryBuilderDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(ConditionESQueryBuilderDispatcher.class.getName());

    private Map<String, ConditionESQueryBuilder> queryBuilders = new ConcurrentHashMap<>();

    public ConditionESQueryBuilderDispatcher() {
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

    public QueryBuilder getQueryBuilder(Condition condition) {
        return QueryBuilders.boolQuery().must(QueryBuilders.matchAllQuery()).filter(buildFilter(condition));
    }

    public QueryBuilder buildFilter(Condition condition) {
        return buildFilter(condition, new HashMap<String, Object>());
    }

    public QueryBuilder buildFilter(Condition condition, Map<String, Object> context) {
        if(condition == null || condition.getConditionType() == null) {
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
            Condition contextualCondition = ConditionContextHelper.getContextualCondition(condition, context);
            if (contextualCondition != null) {
                return queryBuilder.buildQuery(contextualCondition, context, this);
            }
        }

        // if no matching
        if (logger.isDebugEnabled()) {
            logger.debug("No matching query builder for condition {} and context {}", condition, context);
        }
        return QueryBuilders.matchAllQuery();
    }

    public long count(Condition condition) {
        return count(condition, new HashMap<>());
    }

    public long count(Condition condition, Map<String, Object> context) {
        if(condition == null || condition.getConditionType() == null) {
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
            Condition contextualCondition = ConditionContextHelper.getContextualCondition(condition, context);
            if (contextualCondition != null) {
                return queryBuilder.count(contextualCondition, context, this);
            }
        }

        // if no matching
        if (logger.isDebugEnabled()) {
            logger.debug("No matching query builder for condition {} and context {}", condition, context);
        }
        throw new UnsupportedOperationException();
    }
}
