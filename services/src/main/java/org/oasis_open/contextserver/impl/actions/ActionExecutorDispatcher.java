package org.oasis_open.contextserver.impl.actions;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;
import org.mvel2.MVEL;
import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.actions.ActionExecutor;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by toto on 27/06/14.
 */
public class ActionExecutorDispatcher {

    private BundleContext bundleContext;

    public ActionExecutorDispatcher() {

    }

    public static Action getContextualAction(Action action, Event event) {
        if (!hasContextualParameter(action.getParameterValues())) {
            return action;
        }

        Map<String, Object> values = parseMap(event, action.getParameterValues());
        Action n = new Action(action.getActionType());
        n.setParameterValues(values);
        return n;
    }

    private static Map<String, Object> parseMap(Event event, Map<String, Object> map) {
        Map<String, Object> values = new HashMap<String, Object>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                String s = (String) value;
                try {
                    if (s.startsWith("userProperty::")) {
                        value = PropertyUtils.getProperty(event.getUser(), "properties." + StringUtils.substringAfter(s, "userProperty::"));
                    } else if (s.startsWith("simpleUserProperty::")) {
                        value = event.getUser().getProperty(StringUtils.substringAfter(s, "simpleUserProperty::"));
                    } else if (s.startsWith("sessionProperty::")) {
                        value = PropertyUtils.getProperty(event.getSession(), "properties." + StringUtils.substringAfter(s, "sessionProperty::"));
                    } else if (s.startsWith("simpleSessionProperty::")) {
                        value = event.getSession().getProperty(StringUtils.substringAfter(s, "simpleSessionProperty::"));
                    } else if (s.startsWith("eventProperty::")) {
                        value = PropertyUtils.getProperty(event, StringUtils.substringAfter(s, "eventProperty::"));
                    } else if (s.startsWith("simpleEventProperty::")) {
                        value = event.getProperty(StringUtils.substringAfter(s, "simpleEventProperty::"));
                    } else if (s.startsWith("script::")) {
                        Map<String, Object> ctx = new HashMap<String, Object>();
                        ctx.put("event", event);
                        ctx.put("session", event.getSession());
                        ctx.put("user", event.getUser());
                        value = MVEL.eval(StringUtils.substringAfter(s, "script::"), ctx);
                    }
                } catch (UnsupportedOperationException e) {
                    throw e;
                } catch (Exception e) {
                    throw new UnsupportedOperationException(e);
                }
            } else if (value instanceof Map) {
                value = parseMap(event, (Map<String, Object>) value);
            }
            values.put(entry.getKey(), value);
        }
        return values;
    }

    private static boolean hasContextualParameter(Map<String, Object> values) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                String s = (String) value;
                if (s.startsWith("eventProperty::") || s.startsWith("userProperty::") || s.startsWith("sessionProperty::") || s.startsWith("script::")) {
                    return true;
                }
            } else if (value instanceof Map) {
                if (hasContextualParameter((Map<String, Object>) value)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public boolean execute(Action action, Event event) {
        Collection<ServiceReference<ActionExecutor>> matchingActionExecutorReferences;
        if (action.getActionType().getServiceFilter() == null) {
            throw new UnsupportedOperationException("No service defined for : " + action.getActionType());
        }
        try {
            matchingActionExecutorReferences = bundleContext.getServiceReferences(ActionExecutor.class, action.getActionType().getServiceFilter());
        } catch (InvalidSyntaxException e) {
            e.printStackTrace();
            return false;
        }
        boolean changed = false;
        for (ServiceReference<ActionExecutor> actionExecutorReference : matchingActionExecutorReferences) {
            ActionExecutor actionExecutor = bundleContext.getService(actionExecutorReference);
            changed |= actionExecutor.execute(getContextualAction(action, event), event);
        }
        return changed;
    }

}
