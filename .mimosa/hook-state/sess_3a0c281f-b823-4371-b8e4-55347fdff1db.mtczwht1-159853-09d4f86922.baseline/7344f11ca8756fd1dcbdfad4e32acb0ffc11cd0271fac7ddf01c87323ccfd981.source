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

package org.apache.unomi.plugins.baseplugin.conditions;

import org.apache.unomi.api.Item;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.persistence.spi.conditions.evaluator.ConditionEvaluator;
import org.apache.unomi.persistence.spi.conditions.evaluator.ConditionEvaluatorDispatcher;
import org.apache.unomi.tracing.api.RequestTracer;
import org.apache.unomi.tracing.api.TracerService;

import java.util.List;
import java.util.Map;

/** Evaluator for AND and OR conditions. */
public class BooleanConditionEvaluator implements ConditionEvaluator {
    private TracerService tracerService;

    public void setTracerService(TracerService tracerService) {
        this.tracerService = tracerService;
    }

    @Override
    public boolean eval(Condition condition, Item item, Map<String, Object> context,
            ConditionEvaluatorDispatcher dispatcher) {
        RequestTracer tracer = null;
        if (tracerService != null && tracerService.isTracingEnabled()) {
            tracer = tracerService.getCurrentTracer();
            tracer.startOperation("boolean",
                "Evaluating boolean condition with operator: " + condition.getParameter("operator"), condition);
        }

        try {
            boolean isAnd = "and".equalsIgnoreCase((String) condition.getParameter("operator"));
            Object subConditionsParam = condition.getParameter("subConditions");
            if (subConditionsParam != null && !(subConditionsParam instanceof List)) {
                throw new IllegalArgumentException("Parameter 'subConditions' of condition type '"
                    + condition.getConditionTypeId() + "' must be a List, got: " + subConditionsParam.getClass().getName());
            }
            @SuppressWarnings("unchecked")
            List<Condition> conditions = (List<Condition>) subConditionsParam;

            if (conditions == null || conditions.isEmpty()) {
                if (tracer != null) {
                    tracer.endOperation(isAnd, "No subconditions found, returning " + isAnd);
                }
                return isAnd;
            }

            if (tracer != null) {
                tracer.trace("Using " + (isAnd ? "AND" : "OR") + " operator for " + conditions.size() + " subconditions", condition);
            }

            for (Condition sub : conditions) {
                boolean eval = dispatcher.eval(sub, item, context);
                if (!eval && isAnd) {
                    if (tracer != null) {
                        tracer.endOperation(false, "AND condition failed on subcondition");
                    }
                    return false;
                } else if (eval && !isAnd) {
                    if (tracer != null) {
                        tracer.endOperation(true, "OR condition succeeded on subcondition");
                    }
                    return true;
                }
            }

            if (tracer != null) {
                tracer.endOperation(isAnd, "All subconditions processed, returning " + isAnd);
            }
            return isAnd;
        } catch (Exception e) {
            if (tracer != null) {
                tracer.endOperation(false, "Error during boolean condition evaluation: " + e.getMessage());
            }
            throw e;
        }
    }
}
