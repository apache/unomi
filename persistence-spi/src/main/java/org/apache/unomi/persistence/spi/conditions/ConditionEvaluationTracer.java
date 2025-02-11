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

/**
 * Interface for tracing condition evaluations
 */
public interface ConditionEvaluationTracer {
    /**
     * Called when a condition evaluation starts
     * @param condition the condition being evaluated
     * @param explanation explanation of what is being evaluated
     */
    void startEvaluation(Condition condition, String explanation);

    /**
     * Called when a condition evaluation ends
     * @param condition the condition that was evaluated
     * @param result the result of the evaluation
     * @param explanation explanation of the result
     */
    void endEvaluation(Condition condition, boolean result, String explanation);

    /**
     * Called to log intermediate steps in condition evaluation
     * @param condition the condition being evaluated
     * @param message the trace message
     */
    void trace(Condition condition, String message);
} 