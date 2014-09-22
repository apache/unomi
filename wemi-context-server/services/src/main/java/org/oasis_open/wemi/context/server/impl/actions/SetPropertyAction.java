package org.oasis_open.wemi.context.server.impl.actions;

import org.mvel2.MVEL;
import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.actions.Action;
import org.oasis_open.wemi.context.server.api.actions.ActionExecutor;

import javax.script.*;
import java.io.Serializable;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
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
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            propertyValue = format.format(event.getTimeStamp());
        } else if (action.getParameterValues().containsKey("script")) {
            Map<String,Object> ctx = new HashMap<String,Object>();
            ctx.put("session", event.getSession());
            ctx.put("user", event.getUser());
            propertyValue = MVEL.eval((String)action.getParameterValues().get("script"),ctx);
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
