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

import org.apache.unomi.api.ExecutionContext;

/**
 * Identifiers of the Camel routes built from recurrent import/export configurations.
 * <p>A configuration identifier is only unique within its tenant, while Camel route identifiers are
 * global, so a route is identified by {@code <tenantId>:<configId>}. Tenant identifiers cannot
 * contain a colon, which keeps the configuration identifier recoverable whatever it contains.</p>
 */
public final class RouteIds {

    private static final char SEPARATOR = ':';

    private RouteIds() {
    }

    /**
     * Returns the identifier of the route of a configuration.
     *
     * @param tenantId the tenant owning the configuration, the system tenant when {@code null}
     * @param configId the configuration identifier
     * @return the Camel route identifier
     */
    public static String of(String tenantId, String configId) {
        return (tenantId != null ? tenantId : ExecutionContext.SYSTEM_TENANT) + SEPARATOR + configId;
    }

    /**
     * Returns the identifier of the configuration a route was built from.
     *
     * @param routeId a Camel route identifier
     * @return the configuration identifier, or the route identifier itself for a route that is not
     * built from a configuration
     */
    public static String configId(String routeId) {
        int separator = routeId.indexOf(SEPARATOR);
        return separator < 0 ? routeId : routeId.substring(separator + 1);
    }
}
