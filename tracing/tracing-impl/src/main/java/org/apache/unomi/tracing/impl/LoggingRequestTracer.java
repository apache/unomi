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
package org.apache.unomi.tracing.impl;

import org.apache.unomi.api.conditions.Condition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Implementation of RequestTracer that extends DefaultRequestTracer to leverage its tree structure
 * while also logging operations to SLF4J with proper indentation.
 */
public class LoggingRequestTracer extends DefaultRequestTracer {
    private static final Logger logger = LoggerFactory.getLogger(LoggingRequestTracer.class);
    protected static final String INDENT_STRING = "  "; // Two spaces per level
    
    protected final ThreadLocal<Integer> indentLevel;

    public LoggingRequestTracer(boolean enabled) {
        setEnabled(enabled);
        this.indentLevel = ThreadLocal.withInitial(() -> 0);
    }

    @Override
    public void startOperation(String operationType, String description, Object context) {
        super.startOperation(operationType, description, context);
        if (!isEnabled()) {
            return;
        }
        
        logMessage(formatStartOperation(operationType, description), context);
        indentLevel.set(indentLevel.get() + 1);
    }

    @Override
    public void endOperation(Object result, String description) {
        if (isEnabled()) {
            indentLevel.set(Math.max(0, indentLevel.get() - 1));
            logMessage(formatEndOperation(description, result), result);
        }
        super.endOperation(result, description);
    }

    @Override
    public void trace(String message, Object context) {
        super.trace(message, context);
        if (!isEnabled()) {
            return;
        }
        
        logMessage(message, context);
    }

    @Override
    public void reset() {
        super.reset();
        indentLevel.set(0);
    }

    /**
     * Format a message for operation start.
     *
     * @param operationType the type of operation
     * @param description the operation description
     * @return formatted message
     */
    protected String formatStartOperation(String operationType, String description) {
        return String.format("Started operation: %s - %s",
            Objects.toString(operationType, "null"),
            Objects.toString(description, "null"));
    }

    /**
     * Format a message for operation end.
     *
     * @param description the operation description
     * @param result the operation result
     * @return formatted message
     */
    protected String formatEndOperation(String description, Object result) {
        return String.format("Ended operation: %s - Result: %s",
            Objects.toString(description, "null"),
            Objects.toString(result, "null"));
    }

    /**
     * Format a trace message with optional context.
     *
     * @param message the base message
     * @param context optional context object
     * @return formatted message
     */
    public String formatMessage(String message, Object context) {
        if (context instanceof Condition) {
            Condition condition = (Condition) context;
            if (message.startsWith("Starting ")) {
                return String.format("Starting %s of condition %s - %s", 
                    message.substring(9), condition.getConditionTypeId(), message);
            } else {
                return String.format("Condition %s - %s", condition.getConditionTypeId(), message);
            }
        } else if (context != null) {
            return String.format("%s - Context: %s",
                Objects.toString(message, "null"),
                Objects.toString(context, "null"));
        }
        return Objects.toString(message, "null");
    }

    /**
     * Add indentation to a message based on the current nesting level.
     *
     * @param message the message to indent
     * @return the indented message
     */
    public String indent(String message) {
        if (message == null) {
            return "";
        }
        
        int level = indentLevel.get();
        if (level <= 0) {
            return message;
        }

        StringBuilder indentation = new StringBuilder(level * INDENT_STRING.length());
        for (int i = 0; i < level; i++) {
            indentation.append(INDENT_STRING);
        }
        return indentation.append(message).toString();
    }

    /**
     * Log a message with proper formatting and indentation.
     *
     * @param message the message to log
     * @param context optional context object
     */
    protected void logMessage(String message, Object context) {
        String formattedMessage = formatMessage(message, context);
        String indentedMessage = indent(formattedMessage);
        logger.debug(indentedMessage);
    }
} 