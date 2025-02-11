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
package org.apache.unomi.services.impl;

import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluationTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test implementation of ConditionEvaluationTracer that supports indentation for nested conditions
 */
public class TestConditionEvaluationTracer implements ConditionEvaluationTracer {
    private static final Logger logger = LoggerFactory.getLogger(TestConditionEvaluationTracer.class);
    private final List<String> traces = new ArrayList<>();
    private final AtomicInteger indentLevel = new AtomicInteger(0);
    private final boolean logToConsole;

    public TestConditionEvaluationTracer(boolean logToConsole) {
        this.logToConsole = logToConsole;
    }

    private String indent() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indentLevel.get(); i++) {
            sb.append("  ");
        }
        return sb.toString();
    }

    @Override
    public void startEvaluation(Condition condition, String explanation) {
        String message = indent() + "Starting evaluation of condition " + condition.getConditionTypeId() + " - " + explanation;
        traces.add(message);
        if (logToConsole) {
            logger.info(message);
        }
        indentLevel.incrementAndGet();
    }

    @Override
    public void endEvaluation(Condition condition, boolean result, String explanation) {
        indentLevel.decrementAndGet();
        String message = indent() + "Finished evaluation of condition " + condition.getConditionTypeId() + " - result=" + result + " - " + explanation;
        traces.add(message);
        if (logToConsole) {
            logger.info(message);
        }
    }

    @Override
    public void trace(Condition condition, String message) {
        String formattedMessage = indent() + "Condition " + condition.getConditionTypeId() + " - " + message;
        traces.add(formattedMessage);
        if (logToConsole) {
            logger.info(formattedMessage);
        }
    }

    /**
     * Get all traces collected so far
     * @return List of trace messages
     */
    public List<String> getTraces() {
        return new ArrayList<>(traces);
    }

    /**
     * Clear all collected traces
     */
    public void clear() {
        traces.clear();
        indentLevel.set(0);
    }
} 