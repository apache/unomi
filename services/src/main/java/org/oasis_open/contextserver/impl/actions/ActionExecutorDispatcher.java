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

package org.oasis_open.contextserver.impl.actions;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

public class ActionExecutorDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(ActionExecutorDispatcher.class.getName());
    private static final String VALUE_NAME_SEPARATOR = "::";

    private BundleContext bundleContext;

    private Map<String, ActionExecutor> executors = new ConcurrentHashMap<>();
    private Map<Long, List<String>> executorsByBundle = new ConcurrentHashMap<>();
    private final Map<String, Serializable> mvelExpressions = new ConcurrentHashMap<>();
    private final Map<String, ValueExtractor> valueExtractors = new HashMap<>(11);

    private interface ValueExtractor {
        Object extract(String valueAsString, Event event) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException;
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
                if (!mvelExpressions.containsKey(valueAsString)) {
                    ParserConfiguration parserConfiguration = new ParserConfiguration();
                    parserConfiguration.setClassLoader(getClass().getClassLoader());
                    mvelExpressions.put(valueAsString, MVEL.compileExpression(valueAsString, new ParserContext(parserConfiguration)));
                }
                Map<String, Object> ctx = new HashMap<>();
                ctx.put("event", event);
                ctx.put("session", event.getSession());
                ctx.put("profile", event.getProfile());
                return MVEL.executeExpression(mvelExpressions.get(valueAsString), ctx);
            }
        });
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
        Map<String, Object> values = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                String s = (String) value;
                try {
                    // check if we have special values
                    if (s.contains(VALUE_NAME_SEPARATOR)) {
                        final String valueType = StringUtils.substringBefore(s, VALUE_NAME_SEPARATOR);
                        final String valueAsString = StringUtils.substringAfter(s, VALUE_NAME_SEPARATOR);
                        final ValueExtractor extractor = valueExtractors.get(valueType);
                        if (extractor != null) {
                            value = extractor.extract(valueAsString, event);
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
