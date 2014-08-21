package org.oasis_open.wemi.context.server.impl.conditions.initializers;

import org.oasis_open.wemi.context.server.api.conditions.initializers.ChoiceListInitializer;
import org.oasis_open.wemi.context.server.api.conditions.initializers.ChoiceListValue;
import org.oasis_open.wemi.context.server.api.services.EventService;

import java.util.ArrayList;
import java.util.List;

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
        // @todo retrieve event types from persistence backend.
        // List<String> eventTypeIds = eventService.getEventTypes();
        List<String> eventTypeIds = new ArrayList<String>();
        eventTypeIds.add("view");
        eventTypeIds.add("login");
        eventTypeIds.add("sessionCreated");
        eventTypeIds.add("userUpdated");
        for (String eventProperty : eventTypeIds) {
            String resourceKey = "EVENT_TYPE_" + eventProperty.toUpperCase().replaceAll("\\.", "_") + "_LABEL";
            choiceListValues.add(new ChoiceListValue(eventProperty, resourceKey));
        }
        return choiceListValues;
    }
}
