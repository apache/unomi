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
import org.apache.unomi.tracing.impl.LoggingRequestTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Test implementation of RequestTracer that extends LoggingRequestTracer to provide test-specific
 * functionality for verification while leveraging the base logging and indentation features.
 *
 * <p>Features:
 * <ul>
 *   <li>Thread-safe trace collection</li>
 *   <li>Optional console logging</li>
 *   <li>Support for condition evaluation tracing</li>
 *   <li>Proper null handling</li>
 *   <li>Immutable trace history access</li>
 *   <li>Hierarchical indentation for better readability</li>
 * </ul>
 */
public class TestRequestTracer extends LoggingRequestTracer {
    private static final Logger logger = LoggerFactory.getLogger(TestRequestTracer.class);
    
    private final List<String> traces;
    private final boolean logToConsole;

    /**
     * Creates a new TestRequestTracer instance.
     *
     * @param logToConsole if true, all traces will also be logged to the console via SLF4J
     */
    public TestRequestTracer(boolean logToConsole) {
        super(true);
        this.traces = Collections.synchronizedList(new ArrayList<>());
        this.logToConsole = logToConsole;
    }

    @Override
    protected void logMessage(String message, Object context) {
        String formattedMessage = formatMessage(message, context);
        String indentedMessage = indent(formattedMessage);
        traces.add(indentedMessage);
        
        if (logToConsole) {
            logger.info(indentedMessage);
        }
    }

    @Override
    public void reset() {
        traces.clear();
        super.reset();
    }

    /**
     * Get an immutable copy of all traces collected so far.
     * The traces include proper indentation to visualize the operation hierarchy.
     *
     * @return Unmodifiable list of trace messages
     */
    public List<String> getTraces() {
        return Collections.unmodifiableList(new ArrayList<>(traces));
    }

    /**
     * Clear all collected traces and reset the current trace node.
     */
    public void clear() {
        traces.clear();
        reset();
    }

    /**
     * Start tracing a condition evaluation.
     *
     * @param condition the condition being evaluated
     * @param message description of what's being evaluated
     * @throws NullPointerException if condition is null
     */
    public void startEvaluation(Condition condition, String message) {
        Objects.requireNonNull(condition, "Condition cannot be null");
        if (isEnabled()) {
            super.trace("Starting evaluation: " + message, condition);
            indentLevel.set(indentLevel.get() + 1);
        }
    }

    /**
     * End tracing a condition evaluation.
     *
     * @param condition the condition that was evaluated
     * @param result the evaluation result
     * @param message description of the evaluation outcome
     * @throws NullPointerException if condition is null
     */
    public void endEvaluation(Condition condition, boolean result, String message) {
        Objects.requireNonNull(condition, "Condition cannot be null");
        if (isEnabled()) {
            indentLevel.set(Math.max(0, indentLevel.get() - 1));
            super.trace("Evaluation completed: " + message + " - Result: " + result, condition);
        }
    }

    /**
     * Add a trace message for a condition.
     *
     * @param condition the condition being traced
     * @param message the trace message
     * @throws NullPointerException if condition is null
     */
    public void trace(Condition condition, String message) {
        Objects.requireNonNull(condition, "Condition cannot be null");
        if (isEnabled()) {
            super.trace(message, condition);
        }
    }
}