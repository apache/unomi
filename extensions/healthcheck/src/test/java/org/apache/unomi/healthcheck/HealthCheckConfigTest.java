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
package org.apache.unomi.healthcheck;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthCheckConfigTest {

    @Test
    void enabledParsesTrueAndFalse() {
        assertTrue(HealthCheckConfig.parseEnabled("true"));
        assertTrue(HealthCheckConfig.parseEnabled("TRUE"));
        assertFalse(HealthCheckConfig.parseEnabled("false"));
        assertTrue(HealthCheckConfig.parseEnabled(null));
        assertTrue(HealthCheckConfig.parseEnabled(""));
        assertFalse(HealthCheckConfig.parseEnabled("no"));
    }

    @Test
    void providersAreTrimmedDeduplicatedAndKeepCustomNames() {
        assertEquals(List.of("cluster", "elasticsearch", "myCustomCheck"),
                HealthCheckConfig.parseProviders("cluster, elasticsearch,, myCustomCheck, cluster"));
        assertEquals(List.of(), HealthCheckConfig.parseProviders(""));
        assertEquals(List.of("karaf", "opensearch"),
                HealthCheckConfig.parseProviders("karaf,opensearch"));
    }

    @Test
    void timeoutRejectsOutOfBoundsAndInvalid() {
        assertEquals(400, HealthCheckConfig.parseTimeout(null));
        assertEquals(250, HealthCheckConfig.parseTimeout("250"));
        assertEquals(400, HealthCheckConfig.parseTimeout("0"));
        assertEquals(400, HealthCheckConfig.parseTimeout("999999"));
        assertEquals(400, HealthCheckConfig.parseTimeout("nope"));
    }

    @Test
    void applyMapsEnvironmentStyleKeys() {
        HealthCheckConfig config = new HealthCheckConfig();
        config.apply(Map.of(
                HealthCheckConfig.ENABLED, "false",
                HealthCheckConfig.PROVIDERS, "cluster,custom,persistence",
                HealthCheckConfig.TIMEOUT, "1500"));

        assertFalse(config.isEnabled());
        assertEquals(List.of("cluster", "custom", "persistence"), config.getEnabledProviders());
        assertEquals(1500, config.getTimeout());
        assertEquals(HealthCheckConfig.DEFAULT_AUTH_REALM, config.get(HealthCheckConfig.CONFIG_AUTH_REALM));
    }

    @Test
    void minimalBackendCfgOnlyNeedsTheProviderList() {
        HealthCheckConfig config = new HealthCheckConfig();
        config.apply(Map.of(HealthCheckConfig.PROVIDERS, "cluster,postgresql,unomi,persistence"));

        assertTrue(config.isEnabled());
        assertEquals(List.of("cluster", "postgresql", "unomi", "persistence"), config.getEnabledProviders());
        assertEquals(HealthCheckConfig.DEFAULT_TIMEOUT_MS, config.getTimeout());
        assertEquals("karaf", config.get(HealthCheckConfig.CONFIG_AUTH_REALM));
    }
}
