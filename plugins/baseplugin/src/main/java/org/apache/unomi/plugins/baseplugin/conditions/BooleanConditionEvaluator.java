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

import java.util.List;
import java.util.Map;

/**
 * Evaluator for AND and OR conditions.
 */
public class BooleanConditionEvaluator implements ConditionEvaluator {

    @Override
    public boolean eval(Condition condition, Item item, Map<String, Object> context,
            ConditionEvaluatorDispatcher dispatcher) {
        boolean isAnd = "and".equalsIgnoreCase((String) condition.getParameter("operator"));
        @SuppressWarnings("unchecked")
        List<Condition> conditions = (List<Condition>) condition.getParameter("subConditions");
        for (Condition sub : conditions) {
            boolean eval = dispatcher.eval(sub, item, context);
            if (!eval && isAnd) {
                // And
                return false;
            } else if (eval && !isAnd) {
                // Or
                return true;
            }
        }
        return isAnd;
    }
}
