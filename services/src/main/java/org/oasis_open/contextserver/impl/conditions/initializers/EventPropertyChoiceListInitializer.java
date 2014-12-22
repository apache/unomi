package org.oasis_open.contextserver.impl.conditions.initializers;

import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListInitializer;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListValue;
import org.oasis_open.contextserver.api.services.EventService;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class EventPropertyChoiceListInitializer implements ChoiceListInitializer {

    EventService eventService;

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    public List<ChoiceListValue> getValues(Object context) {
        List<ChoiceListValue> choiceListValues = new ArrayList<ChoiceListValue>();
        List<String> eventProperties = eventService.getEventProperties();
        for (String eventProperty : eventProperties) {
            String resourceKey = "PROFILE_" + eventProperty.toUpperCase().replaceAll("\\.", "_") + "_LABEL";
            choiceListValues.add(new ChoiceListValue(eventProperty, resourceKey));
        }
        return choiceListValues;
    }
}
