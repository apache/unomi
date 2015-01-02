package org.oasis_open.contextserver.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by toto on 27/06/14.
 */
public class PageViewEventConditionESQueryBuilder implements ConditionESQueryBuilder {

    public PageViewEventConditionESQueryBuilder() {
    }

    public FilterBuilder buildFilter(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        List<FilterBuilder> l = new ArrayList<FilterBuilder>();
        l.add(FilterBuilders.termFilter("eventType", "view"));
        if (condition.getParameterValues().get("url") != null && !"".equals(condition.getParameterValues().get("url"))) {
            l.add(FilterBuilders.termFilter("target.properties.pageInfo.destinationURL", (String) condition.getParameterValues().get("url")));
        }
        if (condition.getParameterValues().get("pagePath") != null && !"".equals(condition.getParameterValues().get("pagePath"))) {
            l.add(FilterBuilders.termFilter("target.properties.pageInfo.pagePath", (String) condition.getParameterValues().get("pagePath")));
        }
        if (condition.getParameterValues().get("language") != null && !"".equals(condition.getParameterValues().get("language"))) {
            l.add(FilterBuilders.termFilter("target.properties.pageInfo.language", (String) condition.getParameterValues().get("language")));
        }
        if (l.size() > 1) {
            return FilterBuilders.andFilter(l.toArray(new FilterBuilder[l.size()]));
        } else {
            return l.get(0);
        }
    }
}
