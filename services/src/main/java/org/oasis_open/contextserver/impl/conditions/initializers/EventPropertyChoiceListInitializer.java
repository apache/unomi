package org.oasis_open.contextserver.impl.conditions.initializers;

import org.oasis_open.contextserver.api.EventProperty;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListInitializer;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListValue;
import org.oasis_open.contextserver.api.services.EventService;

import java.util.ArrayList;
import java.util.List;

/**
 * Initializer for the set of available event properties.
 */
public class EventPropertyChoiceListInitializer implements ChoiceListInitializer {

    EventService eventService;

    public List<ChoiceListValue> getValues(Object context) {
        List<EventProperty> eventProperties = eventService.getEventProperties();
        List<ChoiceListValue> choiceListValues = new ArrayList<>(eventProperties.size());
        for (EventProperty eventProperty : eventProperties) {
            String resourceKey = "EVENT_" + eventProperty.getId().toUpperCase().replaceAll("\\.", "_") + "_LABEL";
            choiceListValues.add(new PropertyTypeChoiceListValue(eventProperty.getId(), resourceKey, eventProperty.getValueType()));
        }
        return choiceListValues;
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }
}
