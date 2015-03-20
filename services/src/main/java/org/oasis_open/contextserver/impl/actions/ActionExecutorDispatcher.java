package org.oasis_open.contextserver.impl.actions;

/*
 * #%L
 * context-server-services
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;
import org.mvel2.MVEL;
import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.actions.ActionExecutor;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ActionExecutorDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(ActionExecutorDispatcher.class.getName());

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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseMap(Event event, Map<String, Object> map) {
        Map<String, Object> values = new HashMap<String, Object>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                String s = (String) value;
                try {
                    if (s.startsWith("profileProperty::")) {
                        value = PropertyUtils.getProperty(event.getProfile(), "properties." + StringUtils.substringAfter(s, "profileProperty::"));
                    } else if (s.startsWith("simpleProfileProperty::")) {
                        value = event.getProfile().getProperty(StringUtils.substringAfter(s, "simpleProfileProperty::"));
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
                        ctx.put("profile", event.getProfile());
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

    @SuppressWarnings("unchecked")
    private static boolean hasContextualParameter(Map<String, Object> values) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                String s = (String) value;
                if (s.startsWith("eventProperty::") ||
                        s.startsWith("profileProperty::") ||
                        s.startsWith("sessionProperty::") ||
                        s.startsWith("simpleEventProperty::") ||
                        s.startsWith("simpleProfileProperty::") ||
                        s.startsWith("simpleSessionProperty::") ||
                        s.startsWith("script::")) {
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
            logger.error("Invalid filter",e);
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
