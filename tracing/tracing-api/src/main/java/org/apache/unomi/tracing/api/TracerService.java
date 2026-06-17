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

/**
 * Service for managing request tracing throughout the application.
 * This service provides access to tracers that can monitor various operations
 * including condition evaluations, authentication, event processing, etc.
 */
public interface TracerService {
    /**
     * Get the current request tracer
     * @return the current request tracer
     */
    RequestTracer getCurrentTracer();

    /**
     * Enable tracing for the current request
     */
    void enableTracing();

    /**
     * Disable tracing for the current request
     */
    void disableTracing();

    /**
     * Check if tracing is enabled for the current request
     * @return true if tracing is enabled
     */
    boolean isTracingEnabled();

    /**
     * Get the root trace node for the current request
     * @return the root trace node or null if tracing is not enabled
     */
    TraceNode getTraceNode();
} 