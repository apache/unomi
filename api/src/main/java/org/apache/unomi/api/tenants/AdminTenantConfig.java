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
 * Tenant used for JAAS-authenticated admin requests that omit {@code X-Unomi-Tenant-Id}
 * (UNOMI-1008). The default is {@value #DEFAULT_ADMIN_TENANT_ID}, which is also the default
 * tenant id of a 3.0→4.0 migration; deployments that migrated into another tenant configure it.
 */
public interface AdminTenantConfig {

    /** Tenant id used when the admin default setting is missing or invalid. */
    String DEFAULT_ADMIN_TENANT_ID = "default";

    /**
     * Tenant id applied when a JAAS admin request has no {@code X-Unomi-Tenant-Id} header.
     *
     * @return a non-blank tenant id; never {@link TenantService#SYSTEM_TENANT}
     */
    String getAdminDefaultTenantId();
}
