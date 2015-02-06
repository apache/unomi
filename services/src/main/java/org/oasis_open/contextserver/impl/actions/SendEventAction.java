package org.oasis_open.contextserver.impl.actions;

import org.apache.commons.beanutils.BeanUtils;
import org.oasis_open.contextserver.api.*;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.actions.ActionExecutor;
import org.oasis_open.contextserver.api.services.EventService;
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
        Map<String, Object> eventProperties = (Map<String, Object>) action.getParameterValues().get("eventProperties");
        Item target = (Item) action.getParameterValues().get("eventTarget");
//        String type = (String) target.get("type");

//            Item targetItem = new CustomItem();
//            BeanUtils.populate(targetItem, target);

        Event subEvent = new Event(eventType, event.getSession(), event.getProfile(), event.getScope(), event, target, event.getTimeStamp());
        subEvent.getAttributes().putAll(event.getAttributes());
        subEvent.getProperties().putAll(eventProperties);

        eventService.send(subEvent);

        return false;
    }
}
