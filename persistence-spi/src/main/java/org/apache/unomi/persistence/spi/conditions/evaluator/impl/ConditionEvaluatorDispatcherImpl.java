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

package org.apache.unomi.persistence.spi.conditions.evaluator.impl;

import org.apache.unomi.api.Item;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.metrics.MetricAdapter;
import org.apache.unomi.metrics.MetricsService;
import org.apache.unomi.persistence.spi.conditions.ConditionContextHelper;
import org.apache.unomi.persistence.spi.conditions.evaluator.ConditionEvaluator;
import org.apache.unomi.persistence.spi.conditions.evaluator.ConditionEvaluatorDispatcher;
import org.apache.unomi.scripting.ScriptExecutor;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.unomi.tracing.api.TracerService;
import org.apache.unomi.tracing.api.RequestTracer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entry point for condition evaluation. Will dispatch to all evaluators.
 */

@Component(service = ConditionEvaluatorDispatcher.class)
public class ConditionEvaluatorDispatcherImpl
        implements ConditionEvaluatorDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConditionEvaluatorDispatcherImpl.class.getName());

    private Map<String, ConditionEvaluator> evaluators = new ConcurrentHashMap<>();

    private MetricsService metricsService;
    private ScriptExecutor scriptExecutor;
    private TracerService tracerService;

    public ConditionEvaluatorDispatcherImpl() {
    }

    @Reference
    public void setMetricsService(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Reference
    public void setScriptExecutor(ScriptExecutor scriptExecutor) {
        this.scriptExecutor = scriptExecutor;
    }

    @Reference(service = ConditionEvaluator.class, cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    public void bindEvaluator(ConditionEvaluator evaluator, Map<String, Object> props) {
        evaluators.put((String) props.get("conditionEvaluatorId"), evaluator);
    }

    public void unbindEvaluator(ConditionEvaluator evaluator, Map<String, Object> props) {
        evaluators.remove((String) props.get("conditionEvaluatorId"));
    }

    @Reference
    public void setTracerService(TracerService tracerService) {
        this.tracerService = tracerService;
    }

    @Override
    public void addEvaluator(String name, ConditionEvaluator evaluator) {
        evaluators.put(name, evaluator);
    }

    @Override
    public void removeEvaluator(String name) {
        evaluators.remove(name);
    }

    @Override
    public boolean eval(Condition condition, Item item) {
        return eval(condition, item, new HashMap<String, Object>());
    }

    @Override
    public boolean eval(Condition condition, Item item, Map<String, Object> context) {
        if (condition == null) {
            throw new UnsupportedOperationException("Null condition passed for item : " + item);
        }
        if (condition.getConditionType() == null) {
            LOGGER.debug("Condition type is null for condition typeID={}, returning false gracefully", condition.getConditionTypeId());
            RequestTracer tracer = null;
            if (tracerService != null && tracerService.isTracingEnabled()) {
                tracer = tracerService.getCurrentTracer();
                tracer.endOperation(false, "Condition type not resolved");
            }
            return false;
        }

        RequestTracer tracer = null;
        if (tracerService != null && tracerService.isTracingEnabled()) {
            tracer = tracerService.getCurrentTracer();
            tracer.startOperation("condition-evaluation",
                "Evaluating condition: " + condition.getConditionTypeId(), condition);
        }

        try {
            String conditionEvaluatorKey = condition.getConditionType().getConditionEvaluator();
            if (condition.getConditionType().getParentCondition() != null) {
                context.putAll(condition.getParameterValues());
                boolean result = eval(condition.getConditionType().getParentCondition(), item, context);
                if (tracer != null) {
                    tracer.endOperation(result, "Parent condition evaluation completed");
                }
                return result;
            }

            if (conditionEvaluatorKey == null) {
                throw new UnsupportedOperationException("No evaluator defined for : " + condition.getConditionTypeId());
            }

            if (evaluators.containsKey(conditionEvaluatorKey)) {
                ConditionEvaluator evaluator = evaluators.get(conditionEvaluatorKey);
                final ConditionEvaluatorDispatcher dispatcher = this;
                try {
                    boolean result = new MetricAdapter<Boolean>(metricsService, this.getClass().getName() + ".conditions." + conditionEvaluatorKey) {
                        @Override
                        public Boolean execute(Object... args) throws Exception {
                            Condition contextualCondition = ConditionContextHelper.getContextualCondition(condition, context, scriptExecutor);
                            if (contextualCondition != null) {
                                return evaluator.eval(contextualCondition, item, context, dispatcher);
                            } else {
                                return true;
                            }
                        }
                    }.runWithTimer();

                    if (tracer != null) {
                        tracer.endOperation(result, "Condition evaluation completed");
                    }
                    return result;
                } catch (Exception e) {
                    LOGGER.error("Error executing condition evaluator with key={}", conditionEvaluatorKey, e);
                    if (tracer != null) {
                        tracer.endOperation(false, "Error during condition evaluation: " + e.getMessage());
                    }
                    return false;
                }
            } else {
                LOGGER.error("Couldn't find evaluator with key={}", conditionEvaluatorKey);
                if (tracer != null) {
                    tracer.endOperation(false, "No evaluator found for condition type: " + conditionEvaluatorKey);
                }
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("Error during condition evaluation", e);
            if (tracer != null) {
                tracer.endOperation(false, "Error during condition evaluation: " + e.getMessage());
            }
            return false;
        }
    }

}
