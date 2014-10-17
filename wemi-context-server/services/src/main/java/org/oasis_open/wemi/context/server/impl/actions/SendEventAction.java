package org.oasis_open.wemi.context.server.impl.actions;

import org.mvel2.MVEL;
import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.actions.Action;
import org.oasis_open.wemi.context.server.api.actions.ActionExecutor;
import org.oasis_open.wemi.context.server.api.services.EventService;

import java.util.HashMap;
import java.util.Map;

public class SendEventAction implements ActionExecutor {

    private EventService eventService;

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    @Override
    public boolean execute(Action action, Event event) {
        String eventType = (String) action.getParameterValues().get("eventType");
        Map<String,Object> eventProperties = (Map<String,Object>) action.getParameterValues().get("eventProperties");
        Event subEvent = new Event(eventType, event.getSession(), event.getUser(), event.getTimeStamp());
        subEvent.getAttributes().putAll(event.getAttributes());
        for (Map.Entry<String, Object> entry : eventProperties.entrySet()) {
            Object propertyValue = entry.getValue();
            subEvent.setProperty(entry.getKey(), propertyValue);
        }
        eventService.send(subEvent);
        return false;
    }
}
