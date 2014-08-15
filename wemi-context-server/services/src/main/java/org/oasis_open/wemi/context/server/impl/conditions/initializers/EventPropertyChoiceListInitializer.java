package org.oasis_open.wemi.context.server.impl.conditions.initializers;

import org.oasis_open.wemi.context.server.api.conditions.initializers.ChoiceListInitializer;
import org.oasis_open.wemi.context.server.api.conditions.initializers.ChoiceListValue;
import org.oasis_open.wemi.context.server.api.services.EventService;

import java.util.ArrayList;
import java.util.List;

/**
 * @todo we should integrate resource bundles for property names (and possibly add descriptions to choice list values)
 */
public class EventPropertyChoiceListInitializer implements ChoiceListInitializer {

    EventService eventService;

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    public List<ChoiceListValue> getValues(Object context) {
        List<ChoiceListValue> choiceListValues = new ArrayList<ChoiceListValue>();
        List<String> userProperties = eventService.getEventProperties();
        for (String userProperty : userProperties) {
            choiceListValues.add(new ChoiceListValue(userProperty, userProperty));
        }
        return choiceListValues;
    }
}
