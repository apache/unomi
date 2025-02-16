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
import org.apache.unomi.tracing.api.RequestTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Test implementation of RequestTracer that provides test-specific functionality for verification
 */
public class TestRequestTracer implements RequestTracer {
    private static final Logger logger = LoggerFactory.getLogger(TestRequestTracer.class);
    private final List<String> traces = new ArrayList<>();
    private final boolean logToConsole;
    private boolean enabled;

    public TestRequestTracer(boolean logToConsole) {
        this.logToConsole = logToConsole;
        this.enabled = true;
    }

    @Override
    public void startOperation(String operationType, String description, Object context) {
        if (isEnabled()) {
            String message = "Started operation: " + operationType + " - " + description;
            trace(message, context);
        }
    }

    @Override
    public void endOperation(Object result, String description) {
        if (isEnabled()) {
            String message = "Ended operation: " + description + " - Result: " + result;
            trace(message, result);
        }
    }

    @Override
    public void trace(String message, Object context) {
        if (isEnabled()) {
            String traceMessage = context != null ? message + " - Context: " + context : message;
            traces.add(traceMessage);
            if (logToConsole) {
                logger.info(traceMessage);
            }
        }
    }

    @Override
    public void addValidationInfo(Collection<?> validationMessages, String schemaId) {
        if (isEnabled()) {
            String message = "Validation for schema " + schemaId + ": " + validationMessages;
            trace(message, validationMessages);
        }
    }

    @Override
    public String getTraceAsJson() {
        return String.join("\n", traces);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void reset() {
        traces.clear();
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
        reset();
    }

    // Additional methods for condition evaluation tracing
    public void startEvaluation(Condition condition, String message) {
        if (isEnabled()) {
            trace("Starting evaluation: " + message, condition);
        }
    }

    public void endEvaluation(Condition condition, boolean result, String message) {
        if (isEnabled()) {
            trace("Evaluation completed: " + message + " - Result: " + result, condition);
        }
    }

    public void trace(Condition condition, String message) {
        if (isEnabled()) {
            trace(message, condition);
        }
    }
}