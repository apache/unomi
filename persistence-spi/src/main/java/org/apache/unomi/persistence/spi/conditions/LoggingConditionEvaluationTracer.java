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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of ConditionEvaluationTracer that logs to SLF4J
 */
public class LoggingConditionEvaluationTracer implements ConditionEvaluationTracer {
    private static final Logger logger = LoggerFactory.getLogger(LoggingConditionEvaluationTracer.class);
    private final boolean enabled;

    public LoggingConditionEvaluationTracer(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void startEvaluation(Condition condition, String explanation) {
        if (!enabled) return;
        logger.debug("Starting evaluation of condition {} - {}", condition.getConditionTypeId(), explanation);
    }

    @Override
    public void endEvaluation(Condition condition, boolean result, String explanation) {
        if (!enabled) return;
        logger.debug("Finished evaluation of condition {} - result={} - {}", condition.getConditionTypeId(), result, explanation);
    }

    @Override
    public void trace(Condition condition, String message) {
        if (!enabled) return;
        logger.debug("Condition {} - {}", condition.getConditionTypeId(), message);
    }
} 