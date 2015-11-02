package org.oasis_open.contextserver.persistence.elasticsearch.conditions;

/*
 * #%L
 * context-server-persistence-elasticsearch-core
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.FilteredQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ConditionESQueryBuilderDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(ConditionESQueryBuilderDispatcher.class.getName());

    private BundleContext bundleContext;

    public ConditionESQueryBuilderDispatcher() {
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    private Map<String, ConditionESQueryBuilder> queryBuilders = new ConcurrentHashMap<>();
    private Map<Long, List<String>> queryBuildersByBundle = new ConcurrentHashMap<>();

    public void addQueryBuilder(String name, long bundleId, ConditionESQueryBuilder evaluator) {
        queryBuilders.put(name, evaluator);
        if (!queryBuildersByBundle.containsKey(bundleId)) {
            queryBuildersByBundle.put(bundleId, new ArrayList<String>());
        }
        queryBuildersByBundle.get(bundleId).add(name);
    }

    public void removeQueryBuilders(long bundleId) {
        if (queryBuildersByBundle.containsKey(bundleId)) {
            for (String s : queryBuildersByBundle.get(bundleId)) {
                queryBuilders.remove(s);
            }
            queryBuildersByBundle.remove(bundleId);
        }
    }

    public String getQuery(Condition condition) {
        return "{\"query\": " + getQueryBuilder(condition).toString() + "}";
    }

    public FilteredQueryBuilder getQueryBuilder(Condition condition) {
        return QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), buildFilter(condition));
    }

    public FilterBuilder buildFilter(Condition condition) {
        return buildFilter(condition, new HashMap<String, Object>());
    }

    public FilterBuilder buildFilter(Condition condition, Map<String, Object> context) {
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
                return queryBuilder.buildFilter(contextualCondition, context, this);
            }
        }

        // if no matching
        return FilterBuilders.matchAllFilter();
    }


}
