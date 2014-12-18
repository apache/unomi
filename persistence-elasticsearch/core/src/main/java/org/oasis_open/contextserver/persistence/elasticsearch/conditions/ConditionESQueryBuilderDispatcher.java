package org.oasis_open.contextserver.persistence.elasticsearch.conditions;

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

/**
 * Created by loom on 26.06.14.
 */
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
        if (condition.getConditionType().getParentCondition() != null) {
            context.putAll(condition.getParameterValues());
            return buildFilter(condition.getConditionType().getParentCondition(), context);
        }

        Collection<ServiceReference<ConditionESQueryBuilder>> matchingQueryBuilderReferences = null;
        if (condition.getConditionType().getQueryBuilderFilter() == null) {
            throw new UnsupportedOperationException("No query builder defined for : " + condition.getConditionTypeId());
        }
        try {
            matchingQueryBuilderReferences = bundleContext.getServiceReferences(ConditionESQueryBuilder.class, condition.getConditionType().getQueryBuilderFilter());
        } catch (InvalidSyntaxException e) {
            e.printStackTrace();
        }
        // despite multiple references possible, we will only execute the first one
        for (ServiceReference<ConditionESQueryBuilder> queryBuilderServiceReference : matchingQueryBuilderReferences) {
            ConditionESQueryBuilder queryBuilder = bundleContext.getService(queryBuilderServiceReference);
            return queryBuilder.buildFilter(ConditionContextHelper.getContextualCondition(condition, context), context, this);
        }
        // if no matching
        return FilterBuilders.matchAllFilter();
    }


}
