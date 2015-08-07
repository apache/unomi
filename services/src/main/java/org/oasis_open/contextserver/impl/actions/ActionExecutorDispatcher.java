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
import org.mvel2.ParserConfiguration;
import org.mvel2.ParserContext;
import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.actions.ActionExecutor;
import org.oasis_open.contextserver.api.services.EventService;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ActionExecutorDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(ActionExecutorDispatcher.class.getName());

    private BundleContext bundleContext;

    private Map<String, ActionExecutor> executors = new ConcurrentHashMap<>();
    private Map<Long, List<String>> executorsByBundle = new ConcurrentHashMap<>();
    private Map<String,Serializable> mvelExpressions = new ConcurrentHashMap<>();


    public ActionExecutorDispatcher() {

    }


    public void addExecutor(String name, long bundleId, ActionExecutor evaluator) {
        executors.put(name, evaluator);
        if (!executorsByBundle.containsKey(bundleId)) {
            executorsByBundle.put(bundleId, new ArrayList<String>());
        }
        executorsByBundle.get(bundleId).add(name);
    }

    public void removeExecutors(long bundleId) {
        if (executorsByBundle.containsKey(bundleId)) {
            for (String s : executorsByBundle.get(bundleId)) {
                executors.remove(s);
            }
            executorsByBundle.remove(bundleId);
        }
    }


    public Action getContextualAction(Action action, Event event) {
        if (!hasContextualParameter(action.getParameterValues())) {
            return action;
        }

        Map<String, Object> values = parseMap(event, action.getParameterValues());
        Action n = new Action(action.getActionType());
        n.setParameterValues(values);
        return n;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMap(Event event, Map<String, Object> map) {
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
                        String script = StringUtils.substringAfter(s, "script::");
                        if (!mvelExpressions.containsKey(script)) {
                            ParserConfiguration parserConfiguration = new ParserConfiguration();
                            parserConfiguration.setClassLoader(getClass().getClassLoader());
                            mvelExpressions.put(script,MVEL.compileExpression(script, new ParserContext(parserConfiguration)));
                        }
                        Map<String, Object> ctx = new HashMap<String, Object>();
                        ctx.put("event", event);
                        ctx.put("session", event.getSession());
                        ctx.put("profile", event.getProfile());
                        value = MVEL.executeExpression(mvelExpressions.get(script), ctx);
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
    private boolean hasContextualParameter(Map<String, Object> values) {
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

    public int execute(Action action, Event event) {
        String actionKey = action.getActionType().getActionExecutor();
        if (actionKey == null) {
            throw new UnsupportedOperationException("No service defined for : " + action.getActionType());
        }

        if (executors.containsKey(actionKey)) {
            ActionExecutor actionExecutor = executors.get(actionKey);
            return actionExecutor.execute(getContextualAction(action, event), event);
        }
        return EventService.NO_CHANGE;
    }

}
