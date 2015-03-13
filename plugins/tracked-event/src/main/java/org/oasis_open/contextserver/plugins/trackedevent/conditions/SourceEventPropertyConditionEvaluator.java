package org.oasis_open.contextserver.plugins.trackedevent.conditions;

/*
 * #%L
 * Context Server Plugin - Provides conditions for events that need to be tracked
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
    private static final Map<String,String> MAPPED_PROPERTIES;
    static {
        MAPPED_PROPERTIES = new HashMap<>(4);
        MAPPED_PROPERTIES.put("id", "itemId");
        MAPPED_PROPERTIES.put("path", "properties.pageInfo.pagePath");
        MAPPED_PROPERTIES.put("type", "itemType");
        MAPPED_PROPERTIES.put("scope", "scope");
    }

    private DefinitionsService definitionsService;

    private void appendConditionIfPropExist(List<Condition> conditions, Condition condition, String prop, ConditionType propConditionType) {
        if (condition.getParameterValues().get(prop) != null && !"".equals(condition.getParameterValues().get(prop))) {
            Condition propCondition = new Condition(propConditionType);
            propCondition.setParameter("comparisonOperator", "equals");
            propCondition.setParameter("propertyName",MAPPED_PROPERTIES.get(prop));
            propCondition.setParameter("propertyValue", condition.getParameterValues().get(prop));
            conditions.add(propCondition);
        }
    }

    @Override
    public boolean eval(Condition condition, Item item, Map<String, Object> context, ConditionEvaluatorDispatcher dispatcher) {
        Condition andCondition = new Condition(definitionsService.getConditionType("booleanCondition"));
        andCondition.setParameter("operator", "and");
        ArrayList<Condition> conditions = new ArrayList<Condition>();

        for (String prop : MAPPED_PROPERTIES.keySet()){
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