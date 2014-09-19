package org.oasis_open.wemi.context.server.impl.actions;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.actions.Action;
import org.oasis_open.wemi.context.server.api.actions.ActionExecutor;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.services.DefinitionsService;
import org.oasis_open.wemi.context.server.impl.services.ParserHelper;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;

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
        final Condition userEventCondition = (Condition) action.getParameterValues().get("eventCondition");

        Condition andCondition = new Condition(definitionsService.getConditionType("andCondition"));
        ArrayList<Condition> conditions = new ArrayList<Condition>();

        Condition eventCondition = (Condition) userEventCondition.getParameterValues().get("eventCondition");
        ParserHelper.resolveConditionType(definitionsService, eventCondition);
        conditions.add(eventCondition);

        Condition c = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        c.getParameterValues().put("propertyName","userId");
        c.getParameterValues().put("comparisonOperator","equals");
        c.getParameterValues().put("propertyValue",event.getUserId());
        conditions.add(c);

        if (userEventCondition.getParameterValues().get("numberOfDays") != null) {
            int i = Integer.parseInt((String) userEventCondition.getParameterValues().get("numberOfDays"));

            Condition timeCondition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
            timeCondition.getParameterValues().put("propertyName","timeStamp");
            timeCondition.getParameterValues().put("comparisonOperator","greaterThan");
            timeCondition.getParameterValues().put("propertyValue","now-"+i+"d");

            conditions.add(timeCondition);
        }

        andCondition.getParameterValues().put("subConditions", conditions);

        long count = persistenceService.queryCount(andCondition, Event.class);

        event.getUser().getProperties().put((String) eventCondition.getParameterValues().get("generatedPropertyKey"), count+1);

        return true;
    }
}
