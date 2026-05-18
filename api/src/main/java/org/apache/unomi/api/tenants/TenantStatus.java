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
 * Enumeration of possible tenant statuses.
 * This enum defines the various states a tenant can be in within the system.
 */
public enum TenantStatus {
    /**
     * Tenant is active and fully operational
     */
    ACTIVE,

    /**
     * Tenant is disabled and cannot perform any operations
     */
    DISABLED,

    /**
     * Tenant is temporarily suspended, typically due to policy violations or maintenance
     */
    SUSPENDED,

    /**
     * Tenant is created but waiting for activation process to complete
     */
    PENDING_ACTIVATION,

    /**
     * Tenant is undergoing scheduled maintenance
     */
    MAINTENANCE
}
