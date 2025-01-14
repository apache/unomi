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

/**
 * Integration tests for tenants security monitoring functionality.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class TenantSecurityMonitoringIT extends BaseIT {

    @Test
    public void testSecurityAlerts() throws Exception {
        // Create tenants with monitoring config
        Tenant tenant = createTenantWithMonitoringConfig();

        // Set up alert handler
        TestAlertHandler alertHandler = new TestAlertHandler();
        securityMonitoringService.registerAlertHandler(
            tenant.getItemId(),
            alertHandler,
            EnumSet.allOf(AlertType.class)
        );

        // Generate security violation
        generateSecurityViolation(tenant);

        // Verify alert was generated
        await().atMost(5, TimeUnit.SECONDS).until(() ->
            !alertHandler.getReceivedAlerts().isEmpty());

        SecurityAlert alert = alertHandler.getReceivedAlerts().get(0);
        Assert.assertEquals(AlertType.SECURITY_VIOLATION, alert.getType());
        Assert.assertEquals(tenant.getItemId(), alert.getTenantId());
    }

    @Test
    public void testSecurityMetrics() throws Exception {
        Tenant tenant = createTenantWithMonitoringConfig();

        // Generate some security events
        generateSecurityEvents(tenant);

        // Get current metrics
        SecurityMetrics metrics = securityMonitoringService.getCurrentMetrics(
            tenant.getItemId()
        );

        Assert.assertNotNull(metrics);
        Assert.assertTrue(metrics.getAuthenticationFailureRate() < 0.1);
        Assert.assertTrue(metrics.getAverageResponseTime() < 1000);
    }
}
