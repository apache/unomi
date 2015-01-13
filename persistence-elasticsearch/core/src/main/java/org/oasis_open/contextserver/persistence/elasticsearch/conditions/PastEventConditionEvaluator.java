package org.oasis_open.contextserver.persistence.elasticsearch.conditions;

import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.Item;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.services.DefinitionsService;
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

        List<Condition> l = new ArrayList<Condition>();
        Condition andCondition = new Condition();
        andCondition.setConditionType(definitionsService.getConditionType("booleanCondition"));
        andCondition.getParameterValues().put("operator", "and");
        andCondition.getParameterValues().put("subConditions", l);

        l.add(eventCondition);

        Condition profileCondition = new Condition();
        profileCondition.setConditionType(definitionsService.getConditionType("sessionPropertyCondition"));
        profileCondition.getParameterValues().put("propertyName", "profileId");
        profileCondition.getParameterValues().put("comparisonOperator","equals");
        profileCondition.getParameterValues().put("propertyValue", item.getItemId());
        l.add(profileCondition);

        Integer numberOfDays = (Integer) condition.getParameterValues().get("numberOfDays");
        if (numberOfDays != null) {
            Condition numberOfDaysCondition = new Condition();
            profileCondition.setConditionType(definitionsService.getConditionType("sessionPropertyCondition"));
            profileCondition.getParameterValues().put("propertyName", "timeStamp");
            profileCondition.getParameterValues().put("comparisonOperator", "greaterThan");
            profileCondition.getParameterValues().put("propertyValue", "now-"+numberOfDays+"d");
            l.add(numberOfDaysCondition);
        }

        Integer minimumEventCount = !parameters.containsKey("minimumEventCount") || "".equals(parameters.get("minimumEventCount")) ? 0 : Integer.parseInt((String) parameters.get("minimumEventCount"));
        Integer maximumEventCount = !parameters.containsKey("maximumEventCount") || "".equals(parameters.get("maximumEventCount")) ? Integer.MAX_VALUE : Integer.parseInt((String) parameters.get("maximumEventCount"));

        long count = persistenceService.queryCount(andCondition, Event.ITEM_TYPE);
        return (minimumEventCount == 0 && maximumEventCount == Integer.MAX_VALUE && count > 0) ||
                (count > minimumEventCount && count < maximumEventCount);
    }
}
