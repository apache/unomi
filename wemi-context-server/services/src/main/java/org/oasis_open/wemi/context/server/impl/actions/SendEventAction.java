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
            Map<String,Object> v = (Map<String, Object>) entry.getValue();
            if (v.containsKey("value")) {
                Object propertyValue = v.get("value");
                subEvent.setProperty(entry.getKey(), propertyValue);
            } else if (v.containsKey("script")) {
                Map<String, Object> ctx = new HashMap<String, Object>();
                ctx.put("event", event);
                ctx.put("session", event.getSession());
                ctx.put("user", event.getUser());
                Object propertyValue = MVEL.eval((String) v.get("script"), ctx);
                subEvent.setProperty(entry.getKey(), propertyValue);
            }
        }
        eventService.send(subEvent);
        return false;
    }
}
