package org.oasis_open.contextserver.impl.actions;

import org.apache.commons.beanutils.BeanUtils;
import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.actions.ActionExecutor;

import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

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
        try {
            if (Boolean.TRUE.equals(action.getParameterValues().get("storeInSession"))) {
                if (propertyValue != null && !propertyValue.equals(BeanUtils.getProperty(event.getSession(), propertyName))) {
                    BeanUtils.setProperty(event.getSession(), propertyName, propertyValue);
                    modified = true;
                }
            } else {
                if (propertyValue != null && !propertyValue.equals(BeanUtils.getProperty(event.getProfile(), propertyName))) {
                    BeanUtils.setProperty(event.getProfile(), propertyName, propertyValue);
                    modified = true;
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return modified;
    }

}
