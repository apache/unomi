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

import graphql.Assert;
import org.apache.unomi.itests.BaseIT;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MigrationIT  extends BaseIT {
    protected static final Path BASE_DIRECTORIES = Paths.get(System.getProperty( "karaf.data" ), "migration", "scripts");
    private static final String FAILING_SCRIPT_NAME = "migrate-11.0.0-01-failingMigration.groovy";
    private static final String SUCCESS_SCRIPT_NAME = "migrate-11.0.0-01-successMigration.groovy";
    private static final String FAILING_SCRIPT_RESOURCE = "migration/" + FAILING_SCRIPT_NAME;
    private static final String SUCCESS_SCRIPT_RESOURCE = "migration/" + SUCCESS_SCRIPT_NAME;
    protected static final Path FAILING_SCRIPT_FS_PATH = Paths.get(System.getProperty( "karaf.data" ), "migration", "scripts", FAILING_SCRIPT_NAME);
    protected static final Path SUCCESS_SCRIPT_FS_PATH = Paths.get(System.getProperty( "karaf.data" ), "migration", "scripts", SUCCESS_SCRIPT_NAME);

    @Test
    public void checkMigrationRecoverySystem() throws Exception {
        try {
            Files.createDirectories(BASE_DIRECTORIES);

            Files.write(FAILING_SCRIPT_FS_PATH, bundleResourceAsString(FAILING_SCRIPT_RESOURCE).getBytes(StandardCharsets.UTF_8));
            String failingResult = executeCommand("unomi:migrate 10.0.0 true");
            System.out.println("Intentional failing migration result:");
            System.out.println(failingResult);
            // step 4 and 5 should not be contains, step 3 is failing
            // Only step 1, 2 and 3 should be performed.
            Assert.assertTrue(failingResult.contains("inside step 1"));
            Assert.assertTrue(failingResult.contains("inside step 2"));
            Assert.assertTrue(failingResult.contains("inside step 3"));
            Assert.assertTrue(!failingResult.contains("inside step 4"));
            Assert.assertTrue(!failingResult.contains("inside step 5"));
            Files.deleteIfExists(FAILING_SCRIPT_FS_PATH);

            Files.write(SUCCESS_SCRIPT_FS_PATH, bundleResourceAsString(SUCCESS_SCRIPT_RESOURCE).getBytes(StandardCharsets.UTF_8));
            String successResult = executeCommand("unomi:migrate 10.0.0 true");
            System.out.println("Success recovered from failing migration result:");
            System.out.println(successResult);
            // step 1 and 2 should not be contains, they passed on first attempt.
            // Only step 3, 4 and 5 should be performed.
            Assert.assertTrue(!successResult.contains("inside step 1"));
            Assert.assertTrue(!successResult.contains("inside step 2"));
            Assert.assertTrue(successResult.contains("inside step 3"));
            Assert.assertTrue(successResult.contains("inside step 4"));
            Assert.assertTrue(successResult.contains("inside step 5"));
            Files.deleteIfExists(SUCCESS_SCRIPT_FS_PATH);
        } finally {
            Files.deleteIfExists(FAILING_SCRIPT_FS_PATH);
            Files.deleteIfExists(SUCCESS_SCRIPT_FS_PATH);
        }
    }
}
