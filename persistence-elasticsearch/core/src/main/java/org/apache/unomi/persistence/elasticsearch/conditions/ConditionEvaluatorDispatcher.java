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
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entry point for condition evaluation. Will dispatch to all evaluators.
 */
public class ConditionEvaluatorDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(ConditionEvaluatorDispatcher.class.getName());

    private BundleContext bundleContext;
    private Map<String, ConditionEvaluator> evaluators = new ConcurrentHashMap<>();
    private Map<Long, List<String>> evaluatorsByBundle = new ConcurrentHashMap<>();

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void addEvaluator(String name, long bundleId, ConditionEvaluator evaluator) {
        evaluators.put(name, evaluator);
        if (!evaluatorsByBundle.containsKey(bundleId)) {
            evaluatorsByBundle.put(bundleId, new ArrayList<String>());
        }
        evaluatorsByBundle.get(bundleId).add(name);
    }

    public void removeEvaluators(long bundleId) {
        if (evaluatorsByBundle.containsKey(bundleId)) {
            for (String s : evaluatorsByBundle.get(bundleId)) {
                evaluators.remove(s);
            }
            evaluatorsByBundle.remove(bundleId);
        }
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
            Condition contextualCondition = ConditionContextHelper.getContextualCondition(condition, context);
            if (contextualCondition != null) {
                return evaluator.eval(contextualCondition, item, context, this);
            } else {
                return true;
            }
        }

        // if no matching
        return false;
    }
}
