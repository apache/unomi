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
 * limitations under the License
 */
package org.apache.unomi.itests.migration;

import org.apache.unomi.itests.BaseIT;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.fail;

public class MigrationIT  extends BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationIT.class);
    private static final String FAILING_SCRIPT_NAME = "migrate-11.0.0-01-failingMigration.groovy";
    private static final String SUCCESS_SCRIPT_NAME = "migrate-11.0.0-01-successMigration.groovy";
    private static final String FAILING_SCRIPT_RESOURCE = "migration/" + FAILING_SCRIPT_NAME;
    private static final String SUCCESS_SCRIPT_RESOURCE = "migration/" + SUCCESS_SCRIPT_NAME;

    @Test
    public void checkMigrationRecoverySystem() throws Exception {

        String karafData = super.karafData();
        LOGGER.info("Karaf data directory: {}", karafData);

        Path scriptsDirectory = Paths.get(karafData, "migration", "scripts");
        Path failingScriptFsPath = Paths.get(karafData, "migration", "scripts", FAILING_SCRIPT_NAME);
        Path successScriptFsPath = Paths.get(karafData, "migration", "scripts", SUCCESS_SCRIPT_NAME);

        try {
            Files.createDirectories(scriptsDirectory);

            Files.write(failingScriptFsPath, bundleResourceAsString(FAILING_SCRIPT_RESOURCE).getBytes(StandardCharsets.UTF_8));
            try {
                executeCommand("unomi:migrate 10.0.0 true");
                fail("Migration should have failed and crashed by Exception throwing");
            } catch (Exception e) {
                // this is expected, the script fail at step 3
            }
            Files.deleteIfExists(failingScriptFsPath);

            Files.write(successScriptFsPath, bundleResourceAsString(SUCCESS_SCRIPT_RESOURCE).getBytes(StandardCharsets.UTF_8));
            String successResult = executeCommand("unomi:migrate 10.0.0 true");
            System.out.println("Success recovered from failing migration result:");
            System.out.println(successResult);
            // step 1 and 2 should not be contains, they passed on first attempt.
            // Only step 3, 4 and 5 should be performed.
            Assert.assertFalse(successResult.contains("inside step 1"));
            Assert.assertFalse(successResult.contains("inside step 2"));
            Assert.assertTrue(successResult.contains("inside step 3"));
            Assert.assertTrue(successResult.contains("inside step 4"));
            Assert.assertTrue(successResult.contains("inside step 5"));
            Files.deleteIfExists(successScriptFsPath);
        } finally {
            Files.deleteIfExists(failingScriptFsPath);
            Files.deleteIfExists(successScriptFsPath);
        }
    }
}
