package org.oasis_open.contextserver.plugins.trackedevent.conditions;

import org.oasis_open.contextserver.api.Item;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.conditions.ConditionType;
import org.oasis_open.contextserver.api.services.DefinitionsService;
import org.oasis_open.contextserver.persistence.elasticsearch.conditions.ConditionEvaluator;
import org.oasis_open.contextserver.persistence.elasticsearch.conditions.ConditionEvaluatorDispatcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SourceEventPropertyConditionEvaluator implements ConditionEvaluator {
    private DefinitionsService definitionsService;

    private Map<String,String> mappedProperties;

    public SourceEventPropertyConditionEvaluator() {
        mappedProperties = new HashMap<>();
        mappedProperties.put("id", "itemId");
        mappedProperties.put("path", "properties.pageInfo.pagePath");
        mappedProperties.put("type", "itemType");
        mappedProperties.put("scope", "scope");
    }

    private void appendConditionIfPropExist(List<Condition> conditions, Condition condition, String prop, ConditionType propConditionType) {
        if (condition.getParameterValues().get(prop) != null && !"".equals(condition.getParameterValues().get(prop))) {
            Condition propCondition = new Condition(propConditionType);
            propCondition.setParameter("comparisonOperator", "equals");
            propCondition.setParameter("propertyName",mappedProperties.get(prop));
            propCondition.setParameter("propertyValue", condition.getParameterValues().get(prop));
            conditions.add(propCondition);
        }
    }

    @Override
    public boolean eval(Condition condition, Item item, Map<String, Object> context, ConditionEvaluatorDispatcher dispatcher) {
        Condition andCondition = new Condition(definitionsService.getConditionType("booleanCondition"));
        andCondition.setParameter("operator", "and");
        ArrayList<Condition> conditions = new ArrayList<Condition>();

        for (String prop : mappedProperties.keySet()){
            appendConditionIfPropExist(conditions, condition, prop, definitionsService.getConditionType("eventPropertyCondition"));
        }

        if(conditions.size() > 0){
            andCondition.setParameter("subConditions", conditions);
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