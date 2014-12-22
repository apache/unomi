package org.oasis_open.contextserver.impl.actions;

import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.actions.ActionExecutor;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.services.DefinitionsService;
import org.oasis_open.contextserver.impl.services.ParserHelper;
import org.oasis_open.contextserver.persistence.spi.PersistenceService;

import java.util.ArrayList;

/**
 * Created by toto on 02/09/14.
 */
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
        final Condition profileEventCondition = (Condition) action.getParameterValues().get("eventCondition");

        Condition andCondition = new Condition(definitionsService.getConditionType("andCondition"));
        ArrayList<Condition> conditions = new ArrayList<Condition>();

        Condition eventCondition = (Condition) profileEventCondition.getParameterValues().get("eventCondition");
        ParserHelper.resolveConditionType(definitionsService, eventCondition);
        conditions.add(eventCondition);

        Condition c = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        c.getParameterValues().put("propertyName","profileId");
        c.getParameterValues().put("comparisonOperator","equals");
        c.getParameterValues().put("propertyValue",event.getProfileId());
        conditions.add(c);

        if (profileEventCondition.getParameterValues().get("numberOfDays") != null) {
            int i = (Integer) profileEventCondition.getParameterValues().get("numberOfDays");

            Condition timeCondition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
            timeCondition.getParameterValues().put("propertyName","timeStamp");
            timeCondition.getParameterValues().put("comparisonOperator","greaterThan");
            timeCondition.getParameterValues().put("propertyValue","now-"+i+"d");

            conditions.add(timeCondition);
        }

        andCondition.getParameterValues().put("subConditions", conditions);

        long count = persistenceService.queryCount(andCondition, Event.ITEM_TYPE);

        event.getProfile().setProperty((String) eventCondition.getParameterValues().get("generatedPropertyKey"), count+1);

        return true;
    }
}
