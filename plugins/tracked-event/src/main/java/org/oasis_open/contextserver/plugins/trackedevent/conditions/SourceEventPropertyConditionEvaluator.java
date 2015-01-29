package org.oasis_open.contextserver.plugins.trackedevent.conditions;

import org.oasis_open.contextserver.api.Item;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.conditions.ConditionType;
import org.oasis_open.contextserver.api.services.DefinitionsService;
import org.oasis_open.contextserver.persistence.elasticsearch.conditions.ConditionEvaluator;
import org.oasis_open.contextserver.persistence.elasticsearch.conditions.ConditionEvaluatorDispatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by kevan on 28/01/15.
 */
public class SourceEventPropertyConditionEvaluator implements ConditionEvaluator {
    private DefinitionsService definitionsService;

    public SourceEventPropertyConditionEvaluator() {
    }

    private void appendConditionIfPropExist(List<Condition> conditions, Condition condition, String prop, ConditionType propConditionType) {
        if (condition.getParameterValues().get(prop) != null && !"".equals(condition.getParameterValues().get(prop))) {
            Condition propCondition = new Condition(propConditionType);
            propCondition.getParameterValues().put("propertyName","source." + prop);
            propCondition.getParameterValues().put("comparisonOperator", "equals");
            propCondition.getParameterValues().put("propertyValue", condition.getParameterValues().get(prop));
            conditions.add(propCondition);
        }
    }

    @Override
    public boolean eval(Condition condition, Item item, Map<String, Object> context, ConditionEvaluatorDispatcher dispatcher) {
        Condition andCondition = new Condition(definitionsService.getConditionType("booleanCondition"));
        andCondition.getParameterValues().put("operator", "and");
        ArrayList<Condition> conditions = new ArrayList<Condition>();

        for (String prop : new String[]{"id", "path", "scope", "type"}){
            appendConditionIfPropExist(conditions, condition, prop, definitionsService.getConditionType("eventPropertyCondition"));
        }

        if(conditions.size() > 0){
            andCondition.getParameterValues().put("subConditions", conditions);
            return dispatcher.eval(andCondition, item);
        } else {
            return true;
        }
    }

    public DefinitionsService getDefinitionsService() {
        return definitionsService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }
}