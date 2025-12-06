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
package org.apache.unomi.persistence.spi.conditions;

import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.scripting.ScriptExecutor;

import java.util.Map;

/**
 * SPI for translating the high-level {@code pastEventCondition} into a concrete, persistence-friendly
 * {@link Condition} so it can be executed efficiently by the underlying store (Elasticsearch, OpenSearch, ...).
 *
 * <p>Why this exists</p>
 * <ul>
 *   <li>{@code pastEventCondition} is a composite logical filter (event type, time window, constraints, counts).
 *   The most efficient query structure varies by operator and by persistence technology.</li>
 *   <li>This SPI decouples Unomi's condition model from storage-specific optimizations, letting each persistence
 *   module build the best executable event condition.</li>
 * </ul>
 *
 * <p>How it is used at runtime</p>
 * <ul>
 *   <li>Implementations are provided by persistence modules and injected where needed.</li>
 *   <li>
 *     In {@code org.apache.unomi.plugins.baseplugin.conditions.PastEventConditionEvaluator}, evaluation follows two paths:
 *     <ol>
 *       <li>If the condition has a {@code generatedPropertyKey}, the evaluator reads a precomputed count from the
 *       {@link org.apache.unomi.api.Profile} system properties (no persistence query).</li>
 *       <li>Otherwise (legacy/fallback), it calls
 *       {@link #getEventCondition(Condition, Map, String, DefinitionsService, ScriptExecutor)} to build an event-level
 *       condition and uses {@code PersistenceService#queryCount} to compute the count against the event index.</li>
 *     </ol>
 *   </li>
 *   <li>Finally, the evaluator calls {@link #getStrategyFromOperator(String)} to interpret the operator and decide if
 *   the result means "events occurred within bounds" or "no events occurred".</li>
 * </ul>
 *
 * @see org.apache.unomi.plugins.baseplugin.conditions.PastEventConditionEvaluator
 */
public interface PastEventConditionPersistenceQueryBuilder {

    /**
     * Derives the execution strategy from the operator provided by the {@code pastEventCondition}.
     * Implementations typically use this to switch between strategies like "exists" vs "count",
     * or inclusion vs exclusion logic, depending on what the underlying engine can do most
     * efficiently.
     *
     * @param operator the operator string coming from the condition parameters (e.g. "equals",
     *                 "greaterThan", custom operator, etc.)
     * @return {@code true} or {@code false} depending on the chosen strategy; the meaning is
     *         implementation-specific and documented by the persistence module
     */
    boolean getStrategyFromOperator(String operator);

    /**
     * Builds a persistence-friendly {@link Condition} that represents the event filtering part of a
     * {@code pastEventCondition}. The returned condition will be used by the persistence layer to
     * query historical events for a given profile.
     *
     * @param condition           the original high-level {@code pastEventCondition}
     * @param context             additional context values to resolve dynamic parameters and scripts
     * @param profileId           the target profile identifier for which past events are evaluated
     * @param definitionsService  service to resolve Unomi condition and type definitions when needed
     * @param scriptExecutor      executor to evaluate scripted/dynamic parameters if present
     * @return a concrete {@link Condition} targeting events, suitable for direct execution by the
     *         underlying persistence engine
     */
    Condition getEventCondition(Condition condition, Map<String, Object> context, String profileId,
                                DefinitionsService definitionsService, ScriptExecutor scriptExecutor);
}
