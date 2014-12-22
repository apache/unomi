package org.oasis_open.contextserver.impl.actions;

import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.actions.ActionExecutor;

import java.text.SimpleDateFormat;
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
        Object propertyValue = action.getParameterValues().get("setPropertyValue");
        if (propertyValue != null && propertyValue.equals("now")) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            propertyValue = format.format(event.getTimeStamp());
        }
        boolean modified = false;
        String propertyName = (String) action.getParameterValues().get("setPropertyName");
        if (Boolean.TRUE.equals(action.getParameterValues().get("storeInSession"))) {
            if (propertyValue != null && !propertyValue.equals(event.getSession().getProperty(propertyName))) {
                event.getSession().setProperty(propertyName, propertyValue);
                modified = true;
            }
        } else {
            if (propertyValue != null && !propertyValue.equals(event.getProfile().getProperty(propertyName))) {
                event.getProfile().setProperty(propertyName, propertyValue);
                modified = true;
            }
        }
        return modified;
    }

}
