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
package org.apache.unomi.api.services;

import org.apache.unomi.api.ExecutionContext;

import java.util.function.Supplier;

/**
 * Service interface for managing execution contexts in Unomi.
 */
public interface ExecutionContextManager {

    /**
     * Gets the current execution context.
     * @return the current execution context
     */
    ExecutionContext getCurrentContext();

    /**
     * Sets the current execution context.
     * @param context the context to set as current
     */
    void setCurrentContext(ExecutionContext context);

    /**
     * Executes an operation as the system user.
     * @param operation the operation to execute
     * @param <T> the return type of the operation
     * @return the result of the operation
     */
    <T> T executeAsSystem(Supplier<T> operation);

    /**
     * Executes an operation as the system user without return value.
     * @param operation the operation to execute
     */
    void executeAsSystem(Runnable operation);

    /**
     * Executes an operation as a specific tenant.
     * This method creates a tenant context, executes the operation, and ensures proper cleanup.
     * @param tenantId the ID of the tenant to execute as
     * @param operation the operation to execute
     * @param <T> the return type of the operation
     * @return the result of the operation
     */
    <T> T executeAsTenant(String tenantId, Supplier<T> operation);

    /**
     * Executes an operation as a specific tenant without return value.
     * This method creates a tenant context, executes the operation, and ensures proper cleanup.
     * @param tenantId the ID of the tenant to execute as
     * @param operation the operation to execute
     */
    void executeAsTenant(String tenantId, Runnable operation);

    /**
     * Creates a new execution context for the given tenant.
     * @param tenantId the tenant ID
     * @return the created execution context
     */
    ExecutionContext createContext(String tenantId);
}
