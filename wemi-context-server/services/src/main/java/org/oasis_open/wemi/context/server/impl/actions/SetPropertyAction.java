package org.oasis_open.wemi.context.server.impl.actions;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.actions.Action;
import org.oasis_open.wemi.context.server.api.actions.ActionExecutor;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Created by toto on 26/06/14.
 */
public class SetPropertyAction implements ActionExecutor {
    public SetPropertyAction() {
    }

    public String getActionId() {
        return "setPropertyAction";
    }

    public boolean execute(Action action, Event event) {
        String propertyValue = (String) action.getParameterValues().get("setPropertyValue");
        if (propertyValue.equals("now")) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            propertyValue = format.format(event.getTimeStamp());
        }
        if (Boolean.TRUE.equals(action.getParameterValues().get("storeInSession"))) {
            event.getSession().setProperty(
                    (String) action.getParameterValues().get("setPropertyName"),
                    propertyValue);
        } else {
            event.getUser().setProperty(
                    (String) action.getParameterValues().get("setPropertyName"),
                    propertyValue);
        }
        return true;
    }

}
