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
package org.apache.unomi.rest.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ensures the distribution no longer ships a known working default admin/health password.
 */
class ShippedAdminPasswordConfigTest {

    @Test
    void usersProperties_hasNoKnownDefaultPasswordFallback() throws Exception {
        Path users = packageEtc("users.properties");
        String content = Files.readString(users);

        assertFalse(content.contains(":-karaf"), "users.properties must not default the karaf password to 'karaf'");
        assertFalse(content.contains(":-health"), "users.properties must not default the health password to 'health'");
        assertTrue(content.contains("${org.apache.unomi.security.root.password}"));
        assertTrue(content.contains("${org.apache.unomi.healthcheck.password}"));
    }

    @Test
    void customSystemProperties_requiresRootPasswordEnvWithoutKnownDefault() throws Exception {
        Path props = packageEtc("custom.system.properties");
        List<String> securityLines = Files.readAllLines(props).stream()
                .filter(line -> line.contains("org.apache.unomi.security.root.password")
                        || line.contains("org.apache.unomi.healthcheck.password"))
                .collect(Collectors.toList());

        assertFalse(securityLines.isEmpty());
        for (String line : securityLines) {
            assertFalse(line.contains(":-karaf"), "root password must not fall back to karaf: " + line);
            assertFalse(line.contains(":-health"), "health password must not fall back to health: " + line);
        }
    }

    @Test
    void profileCookieHttpOnly_defaultsToTrue() throws Exception {
        Path webCfg = Paths.get("..", "web-servlets", "src", "main", "resources", "org.apache.unomi.web.cfg")
                .toAbsolutePath().normalize();
        String content = Files.readString(webCfg);
        assertTrue(content.contains(":-true") || content.contains("HttpOnly:-true")
                        || content.matches("(?s).*profileIdCookieHttpOnly=\\$\\{[^}]*:-true\\}.*"),
                "profileId cookie HttpOnly should default to true");
    }

    @Test
    void setenv_failsFastWhenRootOrHealthPasswordMissing() throws Exception {
        Path setenv = Paths.get("..", "package", "src", "main", "resources", "bin", "setenv")
                .toAbsolutePath().normalize();
        String content = Files.readString(setenv);
        assertTrue(content.contains("UNOMI_ROOT_PASSWORD"),
                "setenv must reference UNOMI_ROOT_PASSWORD");
        assertTrue(content.contains("UNOMI_HEALTHCHECK_PASSWORD"),
                "setenv must reference UNOMI_HEALTHCHECK_PASSWORD");
        assertTrue(content.contains("UNOMI_SKIP_ROOT_PASSWORD_CHECK"),
                "setenv must document/escape-hatch UNOMI_SKIP_ROOT_PASSWORD_CHECK");
        assertTrue(content.contains("UNOMI_SKIP_HEALTHCHECK_PASSWORD_CHECK"),
                "setenv must document/escape-hatch UNOMI_SKIP_HEALTHCHECK_PASSWORD_CHECK");
        assertTrue(content.contains("does not ship a known default"),
                "setenv error message should explain that no known default is shipped");
        assertTrue(content.contains("exit 1"),
                "setenv must exit when a password is missing");
    }

    @Test
    void entrypoint_failsFastWhenRootOrHealthPasswordMissing() throws Exception {
        Path entrypoint = Paths.get("..", "docker", "src", "main", "docker", "entrypoint.sh")
                .toAbsolutePath().normalize();
        String content = Files.readString(entrypoint);
        assertTrue(content.contains("UNOMI_ROOT_PASSWORD"),
                "entrypoint must reference UNOMI_ROOT_PASSWORD");
        assertTrue(content.contains("UNOMI_HEALTHCHECK_PASSWORD"),
                "entrypoint must reference UNOMI_HEALTHCHECK_PASSWORD");
        assertTrue(content.contains("UNOMI_SKIP_HEALTHCHECK_PASSWORD_CHECK"),
                "entrypoint must document/escape-hatch UNOMI_SKIP_HEALTHCHECK_PASSWORD_CHECK");
        assertTrue(content.contains("exit 1"),
                "entrypoint must exit when a password is missing");
    }

    @Test
    void setenvBat_failsFastWhenRootOrHealthPasswordMissing() throws Exception {
        Path setenvBat = Paths.get("..", "package", "src", "main", "resources", "bin", "setenv.bat")
                .toAbsolutePath().normalize();
        String content = Files.readString(setenvBat);
        assertTrue(content.contains("UNOMI_ROOT_PASSWORD"),
                "setenv.bat must reference UNOMI_ROOT_PASSWORD");
        assertTrue(content.contains("UNOMI_HEALTHCHECK_PASSWORD"),
                "setenv.bat must reference UNOMI_HEALTHCHECK_PASSWORD");
        assertTrue(content.contains("UNOMI_SKIP_HEALTHCHECK_PASSWORD_CHECK"),
                "setenv.bat must document/escape-hatch UNOMI_SKIP_HEALTHCHECK_PASSWORD_CHECK");
        assertTrue(content.contains("exit /b 1"),
                "setenv.bat must exit when a password is missing");
    }

    private static Path packageEtc(String fileName) {
        return Paths.get("..", "package", "src", "main", "resources", "etc", fileName)
                .toAbsolutePath().normalize();
    }
}
