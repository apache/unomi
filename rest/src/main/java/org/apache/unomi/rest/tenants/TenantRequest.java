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
package org.apache.unomi.rest.tenants;

import java.util.Map;

/**
 * REST request payload for creating or updating a {@link org.apache.unomi.api.tenants.Tenant}.
 * Carries the desired tenant id and an open properties map that maps to
 * tenant configuration fields accepted by {@link org.apache.unomi.api.tenants.TenantService}.
 */
public class TenantRequest {
    private String requestedId;
    private Map<String, Object> properties;

    /**
     * Returns the requested tenant identifier.
     *
     * @return the requested tenant identifier
     */
    public String getRequestedId() {
        return requestedId;
    }

    /**
     * Sets the requested tenant identifier.
     *
     * @param requestedId the requested tenant identifier
     */
    public void setRequestedId(String requestedId) {
        this.requestedId = requestedId;
    }

    /**
     * Returns the tenant properties.
     *
     * @return the tenant properties
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * Sets the tenant properties.
     *
     * @param properties the tenant properties
     */
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }
}