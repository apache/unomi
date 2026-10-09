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
package org.apache.unomi.router.api;

/**
 * Facade for the Apache Camel runtime used by the Unomi Router extension.
 * Implementations manage dynamic routes for profile import (from sources such as Kafka or files)
 * and profile export (collection and producer pipelines), and expose a minimal API so callers do not
 * depend on Camel types unless they choose to.
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Removing obsolete Camel route definitions when configurations change or are deleted</li>
 *   <li>Rebuilding import reader routes after an {@link org.apache.unomi.router.api.ImportConfiguration} update</li>
 *   <li>Rebuilding export reader routes after an {@link org.apache.unomi.router.api.ExportConfiguration} update</li>
 *   <li>Optional Camel tracing for troubleshooting route execution</li>
 * </ul>
 *
 * <p>Typical usage:
 * <ul>
 *   <li>Management services call update methods when import/export configuration documents change</li>
 *   <li>Cleanup paths call {@link #killExistingRoute(String, String, boolean)} to drop the route of a tenant's
 *       removed config; Camel route ids are built by {@link RouteIds}</li>
 * </ul>
 *
 * @since 1.0
 */
public interface IRouterCamelContext {

    /**
     * Stops and removes the Camel route of a tenant's import/export configuration, if it is currently
     * registered in the context. A configuration of the same id in another tenant keeps its route.
     *
     * @param tenantId  tenant owning the configuration
     * @param configId  identifier of the import/export configuration whose route is removed
     * @param fireEvent when {@code true}, signals that router lifecycle events may be emitted; the concrete
     *                  implementation defines whether events are fired (reserved hook for observability)
     * @throws Exception if Camel fails to remove the route definition
     */
    void killExistingRoute(String tenantId, String configId, boolean fireEvent) throws Exception;

    /**
     * Refreshes the profile import reader route for the given configuration: removes the existing route of that
     * tenant's configuration, loads the {@link org.apache.unomi.router.api.ImportConfiguration}, and—for recurrent
     * configs—registers a new route built from current settings. Must be called in the tenant's execution context.
     *
     * @param tenantId  tenant owning the configuration
     * @param configId  identifier of the import configuration whose reader route should be updated
     * @param fireEvent when {@code true}, signals that router lifecycle events may be emitted after the update
     * @throws Exception if route removal or registration fails
     */
    void updateProfileImportReaderRoute(String tenantId, String configId, boolean fireEvent) throws Exception;

    /**
     * Refreshes the profile export reader (collect) route for the given configuration: removes the existing route
     * of that tenant's configuration, loads the {@link org.apache.unomi.router.api.ExportConfiguration}, and—for
     * recurrent configs—registers a new collect route built from current settings. Must be called in the tenant's
     * execution context.
     *
     * @param tenantId  tenant owning the configuration
     * @param configId  identifier of the export configuration whose reader route should be updated
     * @param fireEvent when {@code true}, signals that router lifecycle events may be emitted after the update
     * @throws Exception if route removal or registration fails
     */
    void updateProfileExportReaderRoute(String tenantId, String configId, boolean fireEvent) throws Exception;

    /**
     * Enables or disables Camel route tracing on the underlying {@code CamelContext} for debugging (message flow,
     * exchanges). Intended for diagnostics in development or incident analysis; may have performance impact when on.
     *
     * @param tracing {@code true} to enable Camel tracing, {@code false} to disable
     */
    void setTracing(boolean tracing);

    /**
     * Returns the underlying Camel context instance.
     * The API uses {@link Object} so consumers of this module are not required to depend on Camel at compile time.
     * Callers that ship Camel may cast to {@code org.apache.camel.CamelContext}.
     *
     * @return the Camel context instance, or {@code null} if not initialized or not exposed
     */
    default Object getCamelContext() {
        return null;
    }
}
