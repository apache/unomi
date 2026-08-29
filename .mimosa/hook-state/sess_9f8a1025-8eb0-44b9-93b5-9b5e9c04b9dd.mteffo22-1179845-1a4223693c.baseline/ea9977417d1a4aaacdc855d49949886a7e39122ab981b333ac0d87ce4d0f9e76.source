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
package org.apache.unomi.api.security;

import java.security.Principal;
import java.util.Objects;

/**
 * A Principal that represents a tenant's identity in the system.
 * This is used to explicitly identify which tenant a Subject belongs to,
 * separate from any roles or user identity the Subject may have.
 */
public class TenantPrincipal implements Principal {
    private final String tenantId;

    /**
     * Creates a new TenantPrincipal for the specified tenant.
     *
     * @param tenantId the ID of the tenant this principal represents
     */
    public TenantPrincipal(String tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }
        this.tenantId = tenantId;
    }

    /**
     * Gets the tenant ID associated with this principal.
     * This is equivalent to getName() but more semantically clear.
     *
     * @return the tenant ID
     */
    public String getTenantId() {
        return tenantId;
    }

    @Override
    public String getName() {
        return tenantId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantPrincipal that = (TenantPrincipal) o;
        return Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId);
    }

    @Override
    public String toString() {
        return "TenantPrincipal[" + tenantId + "]";
    }
} 