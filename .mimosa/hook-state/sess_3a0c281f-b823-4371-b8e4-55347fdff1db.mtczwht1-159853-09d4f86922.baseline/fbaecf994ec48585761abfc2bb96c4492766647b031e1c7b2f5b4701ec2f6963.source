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
    private static final String NESTED_STEP_SCRIPT_NAME = "migrate-12.0.0-01-nestedStepPitfall.groovy";
    private static final String NESTED_STEP_SCRIPT_RESOURCE = "migration/" + NESTED_STEP_SCRIPT_NAME;

    @Test
    public void checkMigrationRecoverySystem() throws Exception {

        String karafData = super.karafData();
        LOGGER.info("Karaf data directory: {}", karafData);

        Path scriptsDirectory = Paths.get(karafData, "migration", "scripts");
        Path historyFsPath = Paths.get(karafData, "migration", "history.json");
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
            Files.deleteIfExists(historyFsPath);
        }
    }

    /**
     * Regression test for UNOMI-943: a step registered inside another step's closure
     * is never (re-)registered once the outer step is already marked COMPLETED in history.
     * We simulate that prior state directly by seeding history.json, then verify that the
     * nested step never runs while a sibling top-level step still does.
     */
    @Test
    public void checkNestedMigrationStepPitfall() throws Exception {
        String karafData = super.karafData();
        Path scriptFsPath = Paths.get(karafData, "migration", "scripts", NESTED_STEP_SCRIPT_NAME);
        Path historyFsPath = Paths.get(karafData, "migration", "history.json");

        try {
            Files.createDirectories(scriptFsPath.getParent());
            Files.write(scriptFsPath, bundleResourceAsString(NESTED_STEP_SCRIPT_RESOURCE).getBytes(StandardCharsets.UTF_8));

            // Simulate "outer-step" already COMPLETED from a previous run, before "nested-step" existed.
            Files.write(historyFsPath, "{\"outer-step\":\"COMPLETED\"}".getBytes(StandardCharsets.UTF_8));

            String result = executeCommand("unomi:migrate 11.0.0 true");
            System.out.println("Nested migration step pitfall result:");
            System.out.println(result);

            // Outer step is already COMPLETED, so its closure -- including the nested step
            // registration -- is never re-entered. This is the bug UNOMI-943 fixed by hoisting.
            Assert.assertFalse(result.contains("inside outer-step"));
            Assert.assertFalse(result.contains("inside nested-step"));
            // A sibling top-level step is unaffected and still runs.
            Assert.assertTrue(result.contains("inside sibling-step"));
        } finally {
            Files.deleteIfExists(scriptFsPath);
            Files.deleteIfExists(historyFsPath);
        }
    }
}
