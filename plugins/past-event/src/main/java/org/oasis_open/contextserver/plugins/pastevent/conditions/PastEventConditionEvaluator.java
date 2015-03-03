package org.oasis_open.contextserver.plugins.pastevent.conditions;

import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.Item;
import org.oasis_open.contextserver.api.Profile;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.services.DefinitionsService;
import org.oasis_open.contextserver.persistence.elasticsearch.conditions.ConditionEvaluator;
import org.oasis_open.contextserver.persistence.elasticsearch.conditions.ConditionEvaluatorDispatcher;
import org.oasis_open.contextserver.persistence.spi.PersistenceService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PastEventConditionEvaluator implements ConditionEvaluator {

    private PersistenceService persistenceService;

    private DefinitionsService definitionsService;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    @Override
    public boolean eval(Condition condition, Item item, Map<String, Object> context, ConditionEvaluatorDispatcher dispatcher) {

        final Map<String, Object> parameters = condition.getParameterValues();

        Condition eventCondition = (Condition) parameters.get("eventCondition");

        long count;

        if (parameters.containsKey("generatedPropertyKey")) {
            String key = (String) parameters.get("generatedPropertyKey");
            Profile profile = (Profile) item;
            Number l = (Number) profile.getProperty(key);
            count = l != null ? l.longValue() : 0L;
        } else {
            if (eventCondition == null) {
                throw new IllegalArgumentException("No eventCondition");
            }

            List<Condition> l = new ArrayList<Condition>();
            Condition andCondition = new Condition();
            andCondition.setConditionType(definitionsService.getConditionType("booleanCondition"));
            andCondition.setParameter("operator", "and");
            andCondition.setParameter("subConditions", l);

            l.add(eventCondition);

            Condition profileCondition = new Condition();
            profileCondition.setConditionType(definitionsService.getConditionType("sessionPropertyCondition"));
            profileCondition.setParameter("propertyName", "profileId");
            profileCondition.setParameter("comparisonOperator", "equals");
            profileCondition.setParameter("propertyValue", item.getItemId());
            l.add(profileCondition);

            Integer numberOfDays = (Integer) condition.getParameterValues().get("numberOfDays");
            if (numberOfDays != null) {
                Condition numberOfDaysCondition = new Condition();
                numberOfDaysCondition.setConditionType(definitionsService.getConditionType("sessionPropertyCondition"));
                numberOfDaysCondition.setParameter("propertyName", "timeStamp");
                numberOfDaysCondition.setParameter("comparisonOperator", "greaterThan");
                numberOfDaysCondition.setParameter("propertyValueDateExpr", "now-" + numberOfDays + "d");
                l.add(numberOfDaysCondition);
            }
            count = persistenceService.queryCount(andCondition, Event.ITEM_TYPE);
        }

        Integer minimumEventCount = !parameters.containsKey("minimumEventCount")  ? 0 : (Integer) parameters.get("minimumEventCount");
        Integer maximumEventCount = !parameters.containsKey("maximumEventCount")  ? Integer.MAX_VALUE : (Integer) parameters.get("maximumEventCount");

        return (minimumEventCount == 0 && maximumEventCount == Integer.MAX_VALUE && count > 0) ||
                (count > minimumEventCount && count < maximumEventCount);
    }
}
