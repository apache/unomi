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
package org.apache.unomi.itests.security;

import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;

/**
 * Integration tests for tenants security audit functionality.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class TenantSecurityAuditIT extends BaseIT {

    @Test
    public void testSecurityEventLogging() throws Exception {
        // Create tenants with audit config
        Tenant tenant = createTenantWithAuditConfig();

        // Generate some security events
        generateSecurityEvents(tenant);

        // Verify events were logged
        List<SecurityEvent> events = securityAuditService.getSecurityEvents(
            tenant.getItemId(),
            System.currentTimeMillis() - 3600000,
            System.currentTimeMillis(),
            null
        );

        Assert.assertFalse(events.isEmpty());
        Assert.assertTrue(events.stream()
            .anyMatch(e -> e.getEventType() == SecurityEventType.AUTHENTICATION_SUCCESS));
    }

    @Test
    public void testComplianceReporting() throws Exception {
        Tenant tenant = createTenantWithComplianceConfig();

        // Generate some compliance-related events
        generateComplianceEvents(tenant);

        // Generate compliance report
        ComplianceReport report = securityAuditService.generateComplianceReport(
            tenant.getItemId(),
            ComplianceReportType.GDPR
        );

        Assert.assertNotNull(report);
        Assert.assertTrue(report.isCompliant());
        Assert.assertTrue(report.getViolations().isEmpty());
    }
}
