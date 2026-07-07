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
 * Provides read-only per-tenant usage metrics for operators and upstream control planes.
 * Unomi does not enforce quotas; callers use these metrics to apply limits upstream.
 */
public interface TenantUsageService {

    String DEFAULT_PERIOD = "24h";

    /**
     * Returns cached usage for the tenant, refreshing on demand when no snapshot exists yet.
     *
     * @param tenantId tenant identifier
     * @param period reporting window label (currently only {@value #DEFAULT_PERIOD} is supported)
     * @return usage snapshot, or {@code null} if the tenant does not exist
     */
    TenantUsage getUsage(String tenantId, String period);

    /**
     * Records one authenticated REST request for the tenant (in-memory counter).
     */
    void recordRestRequest(String tenantId);
}
