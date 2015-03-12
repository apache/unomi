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
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ConditionESQueryBuilderDispatcher {

    private BundleContext bundleContext;

    public ConditionESQueryBuilderDispatcher() {
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
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
        String queryBuilderFilter = condition.getConditionType().getQueryBuilderFilter();
        if (queryBuilderFilter == null && condition.getConditionType().getParentCondition() != null) {
            context.putAll(condition.getParameterValues());
            return buildFilter(condition.getConditionType().getParentCondition(), context);
        }

        if (queryBuilderFilter == null) {
            throw new UnsupportedOperationException("No query builder defined for : " + condition.getConditionTypeId());
        }
        Collection<ServiceReference<ConditionESQueryBuilder>> matchingQueryBuilderReferences = null;
        try {
            matchingQueryBuilderReferences = bundleContext.getServiceReferences(ConditionESQueryBuilder.class, queryBuilderFilter);
        } catch (InvalidSyntaxException e) {
            e.printStackTrace();
        }
        // despite multiple references possible, we will only execute the first one
        for (ServiceReference<ConditionESQueryBuilder> queryBuilderServiceReference : matchingQueryBuilderReferences) {
            ConditionESQueryBuilder queryBuilder = bundleContext.getService(queryBuilderServiceReference);
            Condition contextualCondition = ConditionContextHelper.getContextualCondition(condition, context);
            if (contextualCondition != null) {
                return queryBuilder.buildFilter(contextualCondition, context, this);
            }
        }
        // if no matching
        return FilterBuilders.matchAllFilter();
    }


}
