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
package org.apache.unomi.shell.migration.actions;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.karaf.shell.api.console.Session;
import org.apache.unomi.shell.migration.utils.ConsoleUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.apache.unomi.shell.migration.actions.Migrate.MIGRATION_FS_ROOT_FOLDER;
import static org.apache.unomi.shell.migration.actions.MigrationConfig.MIGRATION_HISTORY_RECOVER;

/**
 * This class allow for keeping track of the migration steps by persisting the steps and there state on the FileSystem,
 * allowing for a migration to be able to restart from a failure in case it happens.
 */
public class MigrationHistory {

    private static final Path MIGRATION_FS_HISTORY_FILE = Paths.get(System.getProperty( "karaf.data" ), MIGRATION_FS_ROOT_FOLDER, "history.json");

    private enum MigrationStepState {
        STARTED,
        COMPLETED
    }

    public MigrationHistory(Session session, MigrationConfig migrationConfig) {
        this.session = session;
        this.migrationConfig = migrationConfig;
        this.objectMapper = new ObjectMapper();

    }

    private final Session session;
    private final MigrationConfig migrationConfig;
    private final ObjectMapper objectMapper;

    private Map<String, MigrationStepState> history = new HashMap<>();

    /**
     * Try to recover from a previous run
     * I case we found an existing history we will ask if we want to recover or if we want to restart from the beginning
     * (it is also configurable using the conf: recoverFromHistory)
     */
    protected void tryRecover() throws IOException {
        if (Files.exists(MIGRATION_FS_HISTORY_FILE)) {
            if (migrationConfig.getBoolean(MIGRATION_HISTORY_RECOVER, session)) {
                history = objectMapper.readValue(MIGRATION_FS_HISTORY_FILE.toFile(), new TypeReference<Map<String, MigrationStepState>>() {});
            } else {
                clean();
            }
        }
    }

    /**
     * this method allow for migration step execution:
     * - in case the history already contains the given stepKey as COMPLETED, then the step won't be executed
     * - in case the history doesn't contain the given stepKey, then the step will be executed
     * Also this method is keeping track of the history by persisting it on the FileSystem.
     *
     * @param stepKey the key of the given step
     * @param step the step to be performed
     * @throws IOException
     */
    public void performMigrationStep(String stepKey, MigrationStep step) throws Exception {
        if (step == null || stepKey == null) {
            throw new IllegalArgumentException("Migration step and/or key cannot be null");
        }

        // check if step already exists in history:
        MigrationStepState stepState = history.get(stepKey);
        if (stepState != MigrationStepState.COMPLETED) {
            updateStep(stepKey, MigrationStepState.STARTED);
            step.execute();
            updateStep(stepKey, MigrationStepState.COMPLETED);
        } else {
            ConsoleUtils.printMessage(session, "Migration step: " + stepKey + " already completed in previous run");
        }
    }

    /**
     * Clean history from FileSystem
     * @throws IOException
     */
    protected void clean() throws IOException {
        Files.deleteIfExists(MIGRATION_FS_HISTORY_FILE);
    }

    private void updateStep(String stepKey, MigrationStepState stepState) throws IOException {
        ConsoleUtils.printMessage(session, "Migration step: " + stepKey + " reach: " + stepState);
        history.put(stepKey, stepState);
        objectMapper.writeValue(MIGRATION_FS_HISTORY_FILE.toFile(), history);
    }

    /**
     * A simple migration step to be performed
     */
    public interface MigrationStep {
        /**
         * Do you migration a safe and unitary way, so that in case this step fail it can be re-executed safely
         */
        void execute() throws Exception;
    }
}
