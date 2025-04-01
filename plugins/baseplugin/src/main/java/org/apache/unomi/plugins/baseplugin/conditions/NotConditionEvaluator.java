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
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluator;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluatorDispatcher;
import org.apache.unomi.tracing.api.TracerService;
import org.apache.unomi.tracing.api.RequestTracer;

import java.util.Map;

/**
 * Evaluator for NOT condition.
 */
public class NotConditionEvaluator implements ConditionEvaluator {
    private TracerService tracerService;

    public void setTracerService(TracerService tracerService) {
        this.tracerService = tracerService;
    }

    @Override
    public boolean eval(Condition condition, Item item, Map<String, Object> context, ConditionEvaluatorDispatcher dispatcher) {
        RequestTracer tracer = null;
        if (tracerService != null && tracerService.isTracingEnabled()) {
            tracer = tracerService.getCurrentTracer();
            tracer.startOperation("not", 
                "Evaluating NOT condition", condition);
        }

        try {
            Condition subCondition = (Condition) condition.getParameter("subCondition");
            if (subCondition == null) {
                if (tracer != null) {
                    tracer.endOperation(false, "No subcondition found");
                }
                return false;
            }

            boolean result = !dispatcher.eval(subCondition, item, context);

            if (tracer != null) {
                tracer.endOperation(result, "NOT condition evaluation completed");
            }
            return result;
        } catch (Exception e) {
            if (tracer != null) {
                tracer.endOperation(false, "Error during NOT condition evaluation: " + e.getMessage());
            }
            throw e;
        }
    }
}
