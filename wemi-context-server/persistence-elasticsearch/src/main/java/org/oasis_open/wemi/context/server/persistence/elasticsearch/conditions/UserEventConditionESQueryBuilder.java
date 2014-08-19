package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.RangeFilterBuilder;
import org.oasis_open.wemi.context.server.api.Session;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by toto on 11/08/14.
 */
public class UserEventConditionESQueryBuilder implements ESQueryBuilder {
    public UserEventConditionESQueryBuilder() {
    }

    public FilterBuilder buildFilter(Condition condition, ConditionESQueryBuilderDispatcher dispatcher) {
        String numberOfDays = (String) condition.getParameterValues().get("numberOfDays");
        String occursIn = (String) condition.getParameterValues().get("eventOccurIn");
        final List<Condition> subConditions = (List<Condition>) condition.getParameterValues().get("subConditions");

        Session targetSession = (Session) condition.getParameterValues().get("target");
        if (targetSession == null) {
            List<FilterBuilder> l = new ArrayList<FilterBuilder>();
            for (Condition sub : subConditions) {
                if (numberOfDays != null) {
                    l.add(FilterBuilders.rangeFilter("properties." + (String) sub.getParameterValues().get("generatedPropertyKey"))
                            .gt("now-" + numberOfDays + "d")
                            .lt("now"));
                } else {
                    l.add(FilterBuilders.existsFilter("properties." + (String) sub.getParameterValues().get("generatedPropertyKey")));
                }
            }
            return FilterBuilders.andFilter(l.toArray(new FilterBuilder[l.size()]));
        } else {
            List<FilterBuilder> l = new ArrayList<FilterBuilder>();
            for (Condition sub : subConditions) {
                l.add(dispatcher.buildFilter(sub));
            }
            if (occursIn != null && occursIn.equals("session")) {
                l.add(FilterBuilders.termFilter("sessionId", targetSession.getItemId()));
            } else {
                l.add(FilterBuilders.termFilter("userId", targetSession.getUserId()));
            }
            if (numberOfDays != null && !numberOfDays.equals("")) {
                l.add(FilterBuilders.rangeFilter("timeStamp")
                        .gt("now-" + numberOfDays + "d")
                        .lt("now"));
            }
            return FilterBuilders.andFilter(l.toArray(new FilterBuilder[l.size()]));
        }
    }
}
