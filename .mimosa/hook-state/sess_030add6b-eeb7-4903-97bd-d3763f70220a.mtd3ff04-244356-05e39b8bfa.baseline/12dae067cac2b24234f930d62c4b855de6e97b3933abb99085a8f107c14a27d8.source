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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the shipped default of the profile cookie's HttpOnly flag.
 * <p>
 * Binding a public caller to the profile its cookie names only holds while that cookie cannot be
 * read from page script, so the shipped default is part of the fix rather than a preference. The
 * two files below are the ones an operator actually gets, which is why they are read here instead
 * of asserting on {@code WebConfig}'s field default.
 */
class ShippedProfileCookieConfigTest {

    /**
     * The two files under test double as the fingerprint of the repository root: a directory
     * holding both is the Unomi checkout rather than a nested copy or a same-named ancestor.
     */
    private static final String WEB_CFG_PATH = "web-servlets/src/main/resources/org.apache.unomi.web.cfg";
    private static final String SYSTEM_PROPERTIES_PATH = "package/src/main/resources/etc/custom.system.properties";

    @Test
    void profileCookieHttpOnly_defaultsToTrue() throws Exception {
        String webCfg = Files.readString(repoFile(WEB_CFG_PATH));
        assertTrue(webCfg.matches("(?s).*profileIdCookieHttpOnly=\\$\\{[^}]*:-true}.*"),
                "profileId cookie HttpOnly should default to true in " + WEB_CFG_PATH);

        String systemProperties = Files.readString(repoFile(SYSTEM_PROPERTIES_PATH));
        assertTrue(systemProperties.matches("(?s).*org\\.apache\\.unomi\\.profile\\.cookie\\.httpOnly=\\$\\{[^}]*:-true}.*"),
                "profile cookie HttpOnly should default to true in " + SYSTEM_PROPERTIES_PATH);
    }

    private static Path repoFile(String relativePath) throws IOException {
        Path candidate = Paths.get("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve(WEB_CFG_PATH))
                    && Files.isRegularFile(candidate.resolve(SYSTEM_PROPERTIES_PATH))) {
                return candidate.resolve(relativePath);
            }
            candidate = candidate.getParent();
        }
        throw new IOException("could not locate the Unomi repository root from " + Paths.get("").toAbsolutePath());
    }
}
