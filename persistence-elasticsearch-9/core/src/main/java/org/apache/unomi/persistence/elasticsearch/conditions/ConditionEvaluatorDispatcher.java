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

package org.apache.unomi.persistence.elasticsearch.conditions;

import org.apache.unomi.api.Item;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.metrics.MetricAdapter;
import org.apache.unomi.metrics.MetricsService;
import org.apache.unomi.scripting.ScriptExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entry point for condition evaluation. Will dispatch to all evaluators.
 */
public class ConditionEvaluatorDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConditionEvaluatorDispatcher.class.getName());

    private Map<String, ConditionEvaluator> evaluators = new ConcurrentHashMap<>();

    private MetricsService metricsService;
    private ScriptExecutor scriptExecutor;

    public void setMetricsService(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    public void setScriptExecutor(ScriptExecutor scriptExecutor) {
        this.scriptExecutor = scriptExecutor;
    }

    public void addEvaluator(String name, ConditionEvaluator evaluator) {
        evaluators.put(name, evaluator);
    }

    public void removeEvaluator(String name) {
        evaluators.remove(name);
    }

    public boolean eval(Condition condition, Item item) {
        return eval(condition, item, new HashMap<String, Object>());
    }

    public boolean eval(Condition condition, Item item, Map<String, Object> context) {
        String conditionEvaluatorKey = condition.getConditionType().getConditionEvaluator();
        if (condition.getConditionType().getParentCondition() != null) {
            context.putAll(condition.getParameterValues());
            return eval(condition.getConditionType().getParentCondition(), item, context);
        }

        if (conditionEvaluatorKey == null) {
            throw new UnsupportedOperationException("No evaluator defined for : " + condition.getConditionTypeId());
        }

        if (evaluators.containsKey(conditionEvaluatorKey)) {
            ConditionEvaluator evaluator = evaluators.get(conditionEvaluatorKey);
            final ConditionEvaluatorDispatcher dispatcher = this;
            try {
                return new MetricAdapter<Boolean>(metricsService, this.getClass().getName() + ".conditions." + conditionEvaluatorKey) {
                    @Override
                    public Boolean execute(Object... args) throws Exception {
                       // Condition contextualCondition = ConditionContextHelper.getContextualCondition(condition, context, scriptExecutor);
                       // if (contextualCondition != null) {
                            //return evaluator.eval(contextualCondition, item, context, dispatcher);
                        //} else {
                            return true;
                       // }
                    }
                }.runWithTimer();
            } catch (Exception e) {
                LOGGER.error("Error executing condition evaluator with key={}", conditionEvaluatorKey, e);
            }
        }

        // if no matching
        return false;
    }
}
