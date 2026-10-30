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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the chain UNOMI_HEALTHCHECK_* environment variable → org.apache.unomi.healthcheck.* system
 * property (custom.system.properties) → healthcheck.* key in both shipped cfg files (UNOMI-1007).
 */
class HealthCheckEnvironmentMappingTest {

    private static final String CUSTOM_SYSTEM_PROPERTIES = "package/src/main/resources/etc/custom.system.properties";
    private static final List<String> CFG_FILES = List.of(
            "extensions/healthcheck/src/main/resources/org.apache.unomi.healthcheck-elasticsearch.cfg",
            "extensions/healthcheck/src/main/resources/org.apache.unomi.healthcheck-opensearch.cfg");

    @ParameterizedTest
    @CsvSource({"ENABLED,enabled", "PROVIDERS,providers", "TIMEOUT,timeout"})
    void environmentVariableReachesTheHealthCheckConfiguration(String variable, String key) throws IOException {
        Path root = locateRepoRoot();
        String systemProperty = "org.apache.unomi.healthcheck." + key;

        String mapping = systemProperty + "=${env:UNOMI_HEALTHCHECK_" + variable + ":-";
        assertTrue(Files.readAllLines(root.resolve(CUSTOM_SYSTEM_PROPERTIES)).stream().anyMatch(l -> l.startsWith(mapping)),
                CUSTOM_SYSTEM_PROPERTIES + " should map UNOMI_HEALTHCHECK_" + variable + " to " + systemProperty);

        String cfgKey = "healthcheck." + key;
        for (String cfg : CFG_FILES) {
            assertTrue(Files.readAllLines(root.resolve(cfg)).stream()
                            .map(l -> l.replace(" ", ""))
                            .anyMatch(l -> l.startsWith(cfgKey + "=${" + systemProperty + ":-")),
                    cfg + " should resolve " + cfgKey + " from " + systemProperty);
        }
    }

    private static Path locateRepoRoot() {
        Path start = Paths.get("").toAbsolutePath().normalize();
        for (Path candidate = start; candidate != null; candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve(CUSTOM_SYSTEM_PROPERTIES))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not locate the Unomi repository root from " + start);
    }
}
