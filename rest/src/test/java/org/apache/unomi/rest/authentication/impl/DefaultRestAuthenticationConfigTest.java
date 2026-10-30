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
package org.apache.unomi.rest.authentication.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * UNOMI-1008: the admin default tenant is configurable, but can never be blank or {@code system}.
 */
class DefaultRestAuthenticationConfigTest {

    @Test
    void usesConfiguredAdminDefaultTenant() {
        assertEquals("acme", adminDefaultTenantFor(" acme "));
    }

    @Test
    void blankOrSystemFallsBackToDefault() {
        assertEquals("default", adminDefaultTenantFor(""));
        assertEquals("default", adminDefaultTenantFor(null));
        assertEquals("default", adminDefaultTenantFor(" system "));
    }

    @Test
    void reconfigurationReplacesThePreviousValue() {
        DefaultRestAuthenticationConfig restConfig = new DefaultRestAuthenticationConfig();
        restConfig.modified(config("acme"));
        restConfig.modified(config("system"));

        assertEquals("default", restConfig.getAdminDefaultTenantId());
    }

    private static String adminDefaultTenantFor(String configured) {
        DefaultRestAuthenticationConfig restConfig = new DefaultRestAuthenticationConfig();
        restConfig.modified(config(configured));
        return restConfig.getAdminDefaultTenantId();
    }

    private static DefaultRestAuthenticationConfig.Config config(String adminDefaultTenantId) {
        DefaultRestAuthenticationConfig.Config config = mock(DefaultRestAuthenticationConfig.Config.class);
        when(config.default_tenant_id()).thenReturn(adminDefaultTenantId);
        return config;
    }
}
