/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.unomi.services.actions;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionDispatcher;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.scripting.ScriptExecutor;
import org.apache.unomi.metrics.MetricAdapter;
import org.apache.unomi.metrics.MetricsService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ActionExecutorDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(ActionExecutorDispatcher.class.getName());
    private static final String VALUE_NAME_SEPARATOR = "::";
    private static final String PLACEHOLDER_PREFIX = "${";
    private static final String PLACEHOLDER_SUFFIX = "}";
    private final Map<String, ValueExtractor> valueExtractors = new HashMap<>(11);
    private Map<String, ActionExecutor> executors = new ConcurrentHashMap<>();
    private MetricsService metricsService;
    private Map<String, ActionDispatcher> actionDispatchers = new ConcurrentHashMap<>();
    private BundleContext bundleContext;
    private ScriptExecutor scriptExecutor;

    public void setMetricsService(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setScriptExecutor(ScriptExecutor scriptExecutor) {
        this.scriptExecutor = scriptExecutor;
    }

    public ActionExecutorDispatcher() {
        valueExtractors.put("profileProperty", new ValueExtractor() {
            @Override
            public Object extract(String valueAsString, Event event) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
                return PropertyUtils.getProperty(event.getProfile(), "properties." + valueAsString);
            }
        });
        valueExtractors.put("simpleProfileProperty", new ValueExtractor() {
            @Override
            public Object extract(String valueAsString, Event event) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
                return event.getProfile().getProperty(valueAsString);
            }
        });
        valueExtractors.put("sessionProperty", new ValueExtractor() {
            @Override
            public Object extract(String valueAsString, Event event) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
                return PropertyUtils.getProperty(event.getSession(), "properties." + valueAsString);
            }
        });
        valueExtractors.put("simpleSessionProperty", new ValueExtractor() {
            @Override
            public Object extract(String valueAsString, Event event) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
                return event.getSession().getProperty(valueAsString);
            }
        });
        valueExtractors.put("eventProperty", new ValueExtractor() {
            @Override
            public Object extract(String valueAsString, Event event) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
                return PropertyUtils.getProperty(event, valueAsString);
            }
        });
        valueExtractors.put("simpleEventProperty", new ValueExtractor() {
            @Override
            public Object extract(String valueAsString, Event event) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
                return event.getProperty(valueAsString);
            }
        });
        valueExtractors.put("script", new ValueExtractor() {
            @Override
            public Object extract(String valueAsString, Event event) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
                return executeScript(valueAsString, event);
            }

        });
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
        Map<String, Object> values = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                String s = (String) value;
                try {
                    if (s.contains(PLACEHOLDER_PREFIX)) {
                        while (s.contains(PLACEHOLDER_PREFIX)) {
                            String substring = s.substring(s.indexOf(PLACEHOLDER_PREFIX) + 2, s.indexOf(PLACEHOLDER_SUFFIX));
                            Object v = extractValue(substring, event);
                            if (v != null) {
                                s = s.replace(PLACEHOLDER_PREFIX + substring + PLACEHOLDER_SUFFIX, v.toString());
                            } else {
                                break;
                            }
                        }
                        value = s;
                    } else {
                        // check if we have special values
                        if (s.contains(VALUE_NAME_SEPARATOR)) {
                            value = extractValue(s, event);
                        }
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

    private Object extractValue(String s, Event event) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Object value = null;

        String valueType = StringUtils.substringBefore(s, VALUE_NAME_SEPARATOR);
        String valueAsString = StringUtils.substringAfter(s, VALUE_NAME_SEPARATOR);
        ValueExtractor extractor = valueExtractors.get(valueType);
        if (extractor != null) {
            value = extractor.extract(valueAsString, event);
        }

        return value;
    }

    @SuppressWarnings("unchecked")
    private boolean hasContextualParameter(Map<String, Object> values) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                String s = (String) value;
                if (s.contains(VALUE_NAME_SEPARATOR) && valueExtractors.containsKey(StringUtils.substringBefore(s, VALUE_NAME_SEPARATOR))) {
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

    public int execute(Action action, Event event) {
        String actionKey = action.getActionType().getActionExecutor();
        if (actionKey == null) {
            throw new UnsupportedOperationException("No service defined for : " + action.getActionType());
        }

        int colonPos = actionKey.indexOf(":");
        if (colonPos > 0) {
            String actionPrefix = actionKey.substring(0, colonPos);
            String actionName = actionKey.substring(colonPos+1);
            ActionDispatcher actionDispatcher = actionDispatchers.get(actionPrefix);
            if (actionDispatcher == null) {
                logger.warn("Couldn't find any action dispatcher for prefix '{}', action {} won't execute !", actionPrefix, actionKey);
            }
            actionDispatcher.execute(action, event, actionName);
        } else if (executors.containsKey(actionKey)) {
            ActionExecutor actionExecutor = executors.get(actionKey);
            try {
                return new MetricAdapter<Integer>(metricsService, this.getClass().getName() + ".action." + actionKey) {
                    @Override
                    public Integer execute(Object... args) throws Exception {
                        return actionExecutor.execute(getContextualAction(action, event), event);
                    }
                }.runWithTimer();
            } catch (Exception e) {
                logger.error("Error executing action with key=" + actionKey, e);
            }
        }
        return EventService.NO_CHANGE;
    }

    private interface ValueExtractor {
        Object extract(String valueAsString, Event event) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException;
    }

    public void bindExecutor(ServiceReference<ActionExecutor> actionExecutorServiceReference) {
        ActionExecutor actionExecutor = bundleContext.getService(actionExecutorServiceReference);
        executors.put(actionExecutorServiceReference.getProperty("actionExecutorId").toString(), actionExecutor);
    }

    public void unbindExecutor(ServiceReference<ActionExecutor> actionExecutorServiceReference) {
        if (actionExecutorServiceReference == null) {
            return;
        }
        executors.remove(actionExecutorServiceReference.getProperty("actionExecutorId").toString());
    }

    public void bindDispatcher(ServiceReference<ActionDispatcher> actionDispatcherServiceReference) {
        ActionDispatcher actionDispatcher = bundleContext.getService(actionDispatcherServiceReference);
        actionDispatchers.put(actionDispatcher.getPrefix(), actionDispatcher);
    }

    public void unbindDispatcher(ServiceReference<ActionDispatcher> actionDispatcherServiceReference) {
        if (actionDispatcherServiceReference == null) {
            return;
        }
        ActionDispatcher actionDispatcher = bundleContext.getService(actionDispatcherServiceReference);
        if (actionDispatcher != null) {
            actionDispatchers.remove(actionDispatcher.getPrefix());
        }
    }

    protected Object executeScript(String script, Event event) {
        Map<String, Object> context = new HashMap<>();
        context.put("event", event);
        context.put("session", event.getSession());
        context.put("profile", event.getProfile());
        return scriptExecutor.execute(script, context);
    }

}
