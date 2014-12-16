package org.oasis_open.wemi.context.server.impl.actions;

import org.apache.commons.beanutils.BeanUtils;
import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.EventTarget;
import org.oasis_open.wemi.context.server.api.actions.Action;
import org.oasis_open.wemi.context.server.api.actions.ActionExecutor;
import org.oasis_open.wemi.context.server.api.services.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public class SendEventAction implements ActionExecutor {
    private static final Logger logger = LoggerFactory.getLogger(SendEventAction.class.getName());

    private EventService eventService;

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    @Override
    public boolean execute(Action action, Event event) {
        String eventType = (String) action.getParameterValues().get("eventType");
        Map<String,Object> eventProperties = (Map<String,Object>) action.getParameterValues().get("eventProperties");
        Map<String,Object> target = (Map<String,Object>) action.getParameterValues().get("eventTarget");

        EventTarget eventTarget = new EventTarget();
        try {
            BeanUtils.populate(eventTarget, target);
        } catch (Exception e) {
            logger.error("Cannot fill event",e);
            return false;
        }
        Event subEvent = new Event(eventType, event.getSession(), event.getUser(), event.getSource(), eventTarget, event.getTimeStamp());
        subEvent.getAttributes().putAll(event.getAttributes());
        for (Map.Entry<String, Object> entry : eventProperties.entrySet()) {
            Object propertyValue = entry.getValue();
            subEvent.setProperty(entry.getKey(), propertyValue);
        }
        eventService.send(subEvent);
        return false;
    }
}
