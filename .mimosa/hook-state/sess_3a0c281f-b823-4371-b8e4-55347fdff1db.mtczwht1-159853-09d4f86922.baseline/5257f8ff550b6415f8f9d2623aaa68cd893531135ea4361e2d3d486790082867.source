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
package org.apache.unomi.tracing.api;

import java.util.Collection;

/**
 * Interface for tracing request processing and internal operations.
 * This provides a generic tracing mechanism that can be used to track various operations
 * including but not limited to:
 * - Condition evaluations
 * - Authentication
 * - Event validation
 * - Event processing
 * - Rules evaluation
 * - Action execution
 */
public interface RequestTracer {
    /**
     * Start tracing a new operation
     * @param operationType the type of operation being traced (e.g. "condition-evaluation", "authentication", "event-validation")
     * @param description description of what is being traced
     * @param context additional context information for the operation
     */
    void startOperation(String operationType, String description, Object context);

    /**
     * End the current operation with a result
     * @param result the result of the operation
     * @param description explanation of the result. The implementation should include timing information.
     */
    void endOperation(Object result, String description);

    /**
     * Add a trace message to the current operation
     * @param message the trace message
     * @param context additional context information for the trace
     */
    void trace(String message, Object context);

    /**
     * Add validation information to the current operation
     * @param validationMessages collection of validation messages/errors
     * @param schemaId the ID of the schema that was used for validation
     */
    void addValidationInfo(Collection<?> validationMessages, String schemaId);

    /**
     * Get the root trace node
     * @return the root trace node or null if tracing is not enabled
     */
    TraceNode getTraceNode();

    /**
     * Check if tracing is enabled
     * @return true if tracing is enabled
     */
    boolean isEnabled();

    /**
     * Enable or disable tracing
     * @param enabled true to enable tracing, false to disable
     */
    void setEnabled(boolean enabled);

    /**
     * Reset the tracer, clearing all stored traces
     */
    void reset();
} 