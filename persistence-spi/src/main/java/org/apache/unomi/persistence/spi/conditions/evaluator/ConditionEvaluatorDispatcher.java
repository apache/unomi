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
package org.apache.unomi.persistence.spi.conditions.evaluator;

import org.apache.unomi.api.Item;
import org.apache.unomi.api.conditions.Condition;

import java.util.Map;

/**
 * Central entry point for evaluating {@link Condition} instances against a given {@link Item}.
 * <p>
 * The dispatcher locates the appropriate {@link ConditionEvaluator} based on the condition type's
 * configured evaluator identifier, then delegates evaluation to that evaluator. Implementations
 * typically:
 * <ul>
 *   <li>Resolve a parent condition first when the condition type defines a {@code parentCondition}
 *   (merging the current condition's parameter values into the context and evaluating the parent).</li>
 *   <li>Contextualize dynamic parameters (scripts/placeholders) via
 *   {@code ConditionContextHelper} and a {@code ScriptExecutor} before delegation.</li>
 *   <li>Wrap evaluation with metrics and handle missing evaluators defensively.</li>
 * </ul>
 * <p>
 * Evaluators are registered as OSGi services under a {@code conditionEvaluatorId}; the dispatcher
 * implementation maintains a map of these evaluators and dispatches accordingly.
 * <p>
 * See {@code ConditionEvaluatorDispatcherImpl} for the reference implementation and
 * {@code PastEventConditionEvaluator} for a typical evaluator.
 *
 * @see org.apache.unomi.persistence.spi.conditions.evaluator.impl.ConditionEvaluatorDispatcherImpl
 * @see org.apache.unomi.plugins.baseplugin.conditions.PastEventConditionEvaluator
 */
public interface ConditionEvaluatorDispatcher {

    /**
     * Evaluates the provided {@link Condition} on the given {@link Item} using an empty context.
     * This is a convenience overload equivalent to calling
     * {@link #eval(Condition, Item, Map)} with an empty map.
     *
     * @param condition the condition to evaluate
     * @param item      the target item (e.g., Profile, Event, Session)
     * @return {@code true} if the condition matches, {@code false} otherwise
     */
    boolean eval(Condition condition, Item item);

    /**
     * Evaluates the provided {@link Condition} on the given {@link Item} using the supplied
     * execution context. Implementations may enrich the context with parameter values when a
     * parent condition is present and will contextualize dynamic parameters before delegating
     * to the appropriate {@link ConditionEvaluator}.
     *
     * @param condition the condition to evaluate
     * @param item      the target item (e.g., Profile, Event, Session)
     * @param context   additional context values available during evaluation (may be mutated by the implementation)
     * @return {@code true} if the condition matches, {@code false} otherwise
     */
    boolean eval(Condition condition, Item item, Map<String, Object> context);
}