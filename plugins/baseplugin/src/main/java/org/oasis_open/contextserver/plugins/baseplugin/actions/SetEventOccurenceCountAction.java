package org.oasis_open.contextserver.plugins.baseplugin.actions;

/*
 * #%L
 * context-server-services
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

import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.actions.ActionExecutor;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.services.DefinitionsService;
import org.oasis_open.contextserver.persistence.spi.PersistenceService;

import java.util.ArrayList;

public class SetEventOccurenceCountAction implements ActionExecutor {
    private DefinitionsService definitionsService;

    private PersistenceService persistenceService;

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Override
    public boolean execute(Action action, Event event) {
        final Condition pastEventCondition = (Condition) action.getParameterValues().get("pastEventCondition");

        Condition andCondition = new Condition(definitionsService.getConditionType("booleanCondition"));
        andCondition.setParameter("operator", "and");
        ArrayList<Condition> conditions = new ArrayList<Condition>();

        Condition eventCondition = (Condition) pastEventCondition.getParameter("eventCondition");
        definitionsService.resolveConditionType(eventCondition);
        conditions.add(eventCondition);

        Condition c = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        c.setParameter("propertyName","profileId");
        c.setParameter("comparisonOperator", "equals");
        c.setParameter("propertyValue",event.getProfileId());
        conditions.add(c);

        if (pastEventCondition.getParameter("numberOfDays") != null) {
            int i = (Integer) pastEventCondition.getParameter("numberOfDays");

            Condition timeCondition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
            timeCondition.setParameter("propertyName","timeStamp");
            timeCondition.setParameter("comparisonOperator","greaterThan");
            timeCondition.setParameter("propertyValueDateExpr","now-"+i+"d");

            conditions.add(timeCondition);
        }

        andCondition.setParameter("subConditions", conditions);

        long count = persistenceService.queryCount(andCondition, Event.ITEM_TYPE);

        event.getProfile().setProperty((String) pastEventCondition.getParameter("generatedPropertyKey"), count + 1);

        return true;
    }
}
