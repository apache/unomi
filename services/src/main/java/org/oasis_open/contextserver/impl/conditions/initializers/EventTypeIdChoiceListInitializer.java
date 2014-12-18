package org.oasis_open.contextserver.impl.conditions.initializers;

import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListInitializer;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListValue;
import org.oasis_open.contextserver.api.services.EventService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by loom on 21.08.14.
 */
public class EventTypeIdChoiceListInitializer implements ChoiceListInitializer {

    EventService eventService;

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    public List<ChoiceListValue> getValues(Object context) {
        List<ChoiceListValue> choiceListValues = new ArrayList<ChoiceListValue>();
        Set<String> eventTypeIds = eventService.getEventTypeIds();
        for (String eventProperty : eventTypeIds) {
            String resourceKey = "EVENT_TYPE_" + eventProperty.toUpperCase().replaceAll("\\.", "_") + "_LABEL";
            choiceListValues.add(new ChoiceListValue(eventProperty, resourceKey));
        }
        return choiceListValues;
    }
}
