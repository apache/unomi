package org.oasis_open.contextserver.plugins.pastevent.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.Profile;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.services.DefinitionsService;
import org.oasis_open.contextserver.persistence.elasticsearch.conditions.ConditionESQueryBuilder;
import org.oasis_open.contextserver.persistence.elasticsearch.conditions.ConditionESQueryBuilderDispatcher;
import org.oasis_open.contextserver.persistence.spi.PersistenceService;
import org.oasis_open.contextserver.persistence.spi.aggregate.TermsAggregate;

import java.util.*;

public class PastEventConditionESQueryBuilder implements ConditionESQueryBuilder {
    private DefinitionsService definitionsService;
    private PersistenceService persistenceService;

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public FilterBuilder buildFilter(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        Condition eventCondition = (Condition) condition.getParameter("eventCondition");
        if (eventCondition == null) {
            throw new IllegalArgumentException("No eventCondition");
        }
        List<Condition> l = new ArrayList<Condition>();
        Condition andCondition = new Condition();
        andCondition.setConditionType(definitionsService.getConditionType("booleanCondition"));
        andCondition.setParameter("operator", "and");
        andCondition.setParameter("subConditions", l);

        l.add(eventCondition);

        Integer numberOfDays = (Integer) condition.getParameterValues().get("numberOfDays");
        if (numberOfDays != null) {
            Condition numberOfDaysCondition = new Condition();
            numberOfDaysCondition.setConditionType(definitionsService.getConditionType("sessionPropertyCondition"));
            numberOfDaysCondition.setParameter("propertyName", "timeStamp");
            numberOfDaysCondition.setParameter("comparisonOperator", "greaterThan");
            numberOfDaysCondition.setParameter("propertyValueDateExpr", "now-" + numberOfDays + "d");
            l.add(numberOfDaysCondition);
        }
        //todo : Check behaviour with important number of profiles
        Set<String> ids = new HashSet<String>();
        Integer minimumEventCount = condition.getParameter("minimumEventCount") == null ? 0 : (Integer) condition.getParameter("minimumEventCount");
        Integer maximumEventCount = condition.getParameter("maximumEventCount") == null  ? Integer.MAX_VALUE : (Integer) condition.getParameter("maximumEventCount");

        Map<String, Long> res = persistenceService.aggregateQuery(andCondition, new TermsAggregate("profileId"), Event.ITEM_TYPE);
        for (Map.Entry<String, Long> entry : res.entrySet()) {
            if (!entry.getKey().startsWith("_")) {
                if (entry.getValue() >= minimumEventCount && entry.getValue() <= maximumEventCount) {
                    ids.add(entry.getKey());
                }
            }
        }

        return FilterBuilders.idsFilter(Profile.ITEM_TYPE).addIds(ids.toArray(new String[ids.size()]));

//        Map<String, Object> parameters = condition.getParameterValues();
//
//        Integer minimumEventCount = parameters.get("minimumEventCount") == null ? 0 : (Integer) parameters.get("minimumEventCount");
//        Integer maximumEventCount = parameters.get("maximumEventCount") == null  ? Integer.MAX_VALUE : (Integer) parameters.get("maximumEventCount");
//
//        if (minimumEventCount  > 0 && maximumEventCount < Integer.MAX_VALUE) {
//            return FilterBuilders.rangeFilter("properties." + parameters.get("generatedPropertyKey"))
//                    .gte(minimumEventCount).lte(maximumEventCount);
//        } else {
//            return FilterBuilders.existsFilter("properties." + parameters.get("generatedPropertyKey"));
//        }
    }
}
