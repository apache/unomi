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
package org.apache.unomi.api.tenants;

/**
 * Chooses the tenant (or system context) for a JAAS-authenticated admin request (UNOMI-1008).
 * Call only after authentication has succeeded, so a missing tenant never changes the answer
 * given for wrong credentials.
 */
public final class AdminTenantSelection {

    /**
     * Result of resolving the tenant header and the configured admin default.
     */
    public enum Kind {
        /** Use {@code createContext(tenantId)}. */
        TENANT,
        /** Use the system execution context. */
        SYSTEM,
        /** The header names a tenant that does not exist. */
        UNKNOWN_TENANT,
        /** The header asks for {@code system} without a full administrator. */
        FORBIDDEN_SYSTEM,
        /** No header, and the configured default tenant does not exist. */
        MISSING_DEFAULT
    }

    private final Kind kind;
    private final String tenantId;

    private AdminTenantSelection(Kind kind, String tenantId) {
        this.kind = kind;
        this.tenantId = tenantId;
    }

    /**
     * @return how the caller should set the execution context
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * Tenant id for {@link Kind#TENANT}, {@link Kind#UNKNOWN_TENANT} and {@link Kind#MISSING_DEFAULT}.
     *
     * @return the tenant id, or {@code null} for {@link Kind#SYSTEM} / {@link Kind#FORBIDDEN_SYSTEM}
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Resolves the tenant for an authenticated admin request.
     *
     * @param headerTenantId        value of {@code X-Unomi-Tenant-Id}, or {@code null}/blank when omitted
     * @param adminDefaultTenantId  configured default when the header is omitted (already normalized)
     * @param tenantService         loads tenants by id
     * @param fullAdministrator     {@code true} when the subject holds {@code ROLE_UNOMI_ADMIN}
     * @return the selection; never {@code null}
     */
    public static AdminTenantSelection select(String headerTenantId, String adminDefaultTenantId,
                                              TenantService tenantService, boolean fullAdministrator) {
        if (headerTenantId != null && !headerTenantId.trim().isEmpty()) {
            String id = headerTenantId.trim();
            if (TenantService.SYSTEM_TENANT.equals(id)) {
                if (!fullAdministrator) {
                    return new AdminTenantSelection(Kind.FORBIDDEN_SYSTEM, null);
                }
                return new AdminTenantSelection(Kind.SYSTEM, null);
            }
            Tenant tenant = tenantService.getTenant(id);
            if (tenant == null) {
                return new AdminTenantSelection(Kind.UNKNOWN_TENANT, id);
            }
            return new AdminTenantSelection(Kind.TENANT, id);
        }

        String defaultId = normalizeDefaultTenantId(adminDefaultTenantId);
        Tenant tenant = tenantService.getTenant(defaultId);
        if (tenant == null) {
            return new AdminTenantSelection(Kind.MISSING_DEFAULT, defaultId);
        }
        return new AdminTenantSelection(Kind.TENANT, defaultId);
    }

    /**
     * Normalizes the configured admin default tenant id. Blank values and {@code system} fall back
     * to {@link AdminTenantConfig#DEFAULT_ADMIN_TENANT_ID}, because the system context must be
     * chosen only with an explicit {@code X-Unomi-Tenant-Id: system} header.
     *
     * @param configured the raw configuration value; may be {@code null}
     * @return a usable tenant id
     */
    public static String normalizeDefaultTenantId(String configured) {
        if (configured == null || configured.trim().isEmpty()) {
            return AdminTenantConfig.DEFAULT_ADMIN_TENANT_ID;
        }
        String trimmed = configured.trim();
        if (TenantService.SYSTEM_TENANT.equals(trimmed)) {
            return AdminTenantConfig.DEFAULT_ADMIN_TENANT_ID;
        }
        return trimmed;
    }

    /**
     * Short message suitable for a 400/403 response body.
     *
     * @return a human-readable explanation
     */
    public String clientMessage() {
        switch (kind) {
            case UNKNOWN_TENANT:
                return "Unknown tenant: " + tenantId;
            case MISSING_DEFAULT:
                return "Admin default tenant does not exist: " + tenantId;
            case FORBIDDEN_SYSTEM:
                return "The system tenant requires a full administrator and X-Unomi-Tenant-Id: system";
            case TENANT:
            case SYSTEM:
                return "";
            default:
                throw new IllegalStateException("Unexpected kind: " + kind);
        }
    }
}
