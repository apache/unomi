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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Lightweight no-op tracer for unit tests. Does not depend on the tracing modules
 * (added in UNOMI-873); sufficient for {@link TestConditionEvaluators}.
 */
public class TestRequestTracer {
    private static final Logger logger = LoggerFactory.getLogger(TestRequestTracer.class);

    private final List<String> traces = Collections.synchronizedList(new ArrayList<>());
    private final boolean logToConsole;
    private volatile boolean enabled;

    public TestRequestTracer(boolean logToConsole) {
        this.logToConsole = logToConsole;
        this.enabled = logToConsole;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void reset() {
        traces.clear();
    }

    public List<String> getTraces() {
        return Collections.unmodifiableList(new ArrayList<>(traces));
    }

    public void clear() {
        traces.clear();
    }

    public void startOperation(String operationType, String message, Condition condition) {
        if (!enabled) {
            return;
        }
        record("START " + operationType + ": " + message, condition);
    }

    public void endOperation(boolean result, String message) {
        if (!enabled) {
            return;
        }
        record("END result=" + result + ": " + message, null);
    }

    public void trace(String message, Condition condition) {
        if (!enabled) {
            return;
        }
        record(message, condition);
    }

    public void trace(Condition condition, String message) {
        trace(message, condition);
    }

    public void startEvaluation(Condition condition, String message) {
        Objects.requireNonNull(condition, "Condition cannot be null");
        if (enabled) {
            record("Starting evaluation: " + message, condition);
        }
    }

    public void endEvaluation(Condition condition, boolean result, String message) {
        Objects.requireNonNull(condition, "Condition cannot be null");
        if (enabled) {
            record("Evaluation completed: " + message + " - Result: " + result, condition);
        }
    }

    private void record(String message, Condition condition) {
        String line = condition != null ? message + " [" + condition.getConditionTypeId() + "]" : message;
        traces.add(line);
        if (logToConsole) {
            logger.debug(line);
        }
    }
}
