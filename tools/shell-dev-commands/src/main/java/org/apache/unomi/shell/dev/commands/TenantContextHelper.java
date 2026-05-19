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
package org.apache.unomi.shell.dev.commands;

import org.apache.karaf.shell.api.console.Session;
import org.apache.unomi.api.services.ExecutionContextManager;

/**
 * Utility class for managing tenant context in Karaf shell sessions.
 * Provides centralized access to session-based tenant storage and execution context initialization.
 */
public final class TenantContextHelper {

    /**
     * Session key for storing the current tenant ID in the Karaf shell session.
     */
    public static final String SESSION_TENANT_ID_KEY = "unomi.tenantId";

    private TenantContextHelper() {
        // Utility class - prevent instantiation
    }

    /**
     * Initialize the execution context from the Karaf shell session.
     * Retrieves the tenant ID from the session and sets it in the execution context.
     * If no tenant is set in the session, defaults to "system" context.
     *
     * @param session the Karaf shell session
     * @param executionContextManager the execution context manager
     */
    public static void initializeExecutionContext(Session session, ExecutionContextManager executionContextManager) {
        String tenantId = getTenantId(session);
        if (tenantId != null) {
            executionContextManager.setCurrentContext(executionContextManager.createContext(tenantId));
        } else {
            // Default to system context if no tenant is set
            executionContextManager.setCurrentContext(executionContextManager.createContext("system"));
        }
    }

    /**
     * Get the tenant ID from the Karaf shell session.
     *
     * @param session the Karaf shell session
     * @return the tenant ID stored in the session, or null if not set
     */
    public static String getTenantId(Session session) {
        return (String) session.get(SESSION_TENANT_ID_KEY);
    }

    /**
     * Set the tenant ID in the Karaf shell session.
     *
     * @param session the Karaf shell session
     * @param tenantId the tenant ID to store
     */
    public static void setTenantId(Session session, String tenantId) {
        session.put(SESSION_TENANT_ID_KEY, tenantId);
    }
}
