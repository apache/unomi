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
package org.apache.unomi.api.tenants.security;

import javax.servlet.http.HttpServletRequest;

/**
 * Service interface for managing tenants-level security operations and validations.
 * This service provides comprehensive security features including authentication,
 * authorization, rate limiting, and security auditing for tenants-specific operations.
 */
public interface TenantSecurityService {

    /**
     * Validates a request against all configured security measures for a tenants.
     *
     * @param request the HTTP request to validate
     * @param tenantId the ID of the tenants making the request
     * @return a SecurityValidationResult containing the validation outcome and any errors
     * @throws SecurityException if a critical security violation is detected
     */
    SecurityValidationResult validateRequest(HttpServletRequest request, String tenantId);

    /**
     * Configures security settings for a specific tenants.
     *
     * @param tenantId the ID of the tenants to configure
     * @param settings the security settings to apply
     * @throws ConfigurationException if the settings are invalid or cannot be applied
     */
    void configureSecuritySettings(String tenantId, SecuritySettings settings);

    /**
     * Generates a security audit report for a tenants within a specified time range.
     *
     * @param tenantId the ID of the tenants
     * @param startTime the start time for the audit period
     * @param endTime the end time for the audit period
     * @return a SecurityAuditReport containing security-related events and statistics
     */
    SecurityAuditReport generateSecurityAudit(String tenantId, long startTime, long endTime);
}
