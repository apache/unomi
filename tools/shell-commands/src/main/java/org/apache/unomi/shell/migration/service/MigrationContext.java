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
package org.apache.unomi.shell.migration.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.karaf.shell.api.console.Session;
import org.jline.reader.LineReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.apache.unomi.shell.migration.service.MigrationConfig.*;
import static org.apache.unomi.shell.migration.service.MigrationServiceImpl.MIGRATION_FS_ROOT_FOLDER;

/**
 * This class is instantiated for each migration process, it contains useful methods to handle the current migration lifecycle.
 *
 * This class allow for keeping track of the migration steps by persisting the steps and there state on the FileSystem,
 * allowing for a migration to be able to restart from a failure in case it happens.
 *
 * This class allow also for logging the migration informations depending on the current context:
 * - if executed in karaf shell using a karaf shell session, then the logging will be done in the shell console
 * - if executed outside karaf shell using OSGI service direct, then the logging will be done using classical logger systems
 *
 * This class allow also to do a best effort on missing configuration information, by prompting questions in the karaf shell
 * (not supported in case direct OSGI service usage)
 */
public class MigrationContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationContext.class);

    private static final Path MIGRATION_FS_HISTORY_FILE = Paths.get(System.getProperty( "karaf.data" ), MIGRATION_FS_ROOT_FOLDER, "history.json");

    private enum MigrationStepState {
        STARTED,
        COMPLETED
    }

    protected MigrationContext(Session session, MigrationConfig migrationConfig) {
        this.session = session;
        this.migrationConfig = migrationConfig;
        this.objectMapper = new ObjectMapper();
    }

    private final Session session;
    private final MigrationConfig migrationConfig;
    private final ObjectMapper objectMapper;
    private CloseableHttpClient httpClient;

    private Map<String, MigrationStepState> history = new HashMap<>();
    private Map<String, String> userConfig = new HashMap<>();
    private Boolean logToLogger = true;

    public void setLogToLogger(Boolean logToLogger) {
        this.logToLogger = logToLogger;
    }

    /**
     * Try to recover from a previous run
     * I case we found an existing history we will ask if we want to recover or if we want to restart from the beginning
     * (it is also configurable using the conf: recoverFromHistory)
     */
    protected void tryRecoverFromHistory() throws IOException {
        if (Files.exists(MIGRATION_FS_HISTORY_FILE)) {
            if (getConfigBoolean(MIGRATION_HISTORY_RECOVER)) {
                history = objectMapper.readValue(MIGRATION_FS_HISTORY_FILE.toFile(), new TypeReference<Map<String, MigrationStepState>>() {});
            } else {
                cleanHistory();
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
            updateHistoryStep(stepKey, MigrationStepState.STARTED);
            step.execute();
            updateHistoryStep(stepKey, MigrationStepState.COMPLETED);
        } else {
            printMessage("Migration step: " + stepKey + " already completed in previous run");
        }
    }

    /**
     * Clean history from FileSystem
     * @throws IOException
     */
    protected void cleanHistory() throws IOException {
        Files.deleteIfExists(MIGRATION_FS_HISTORY_FILE);
    }

    /**
     * This will ask a question to the user and return the default answer if the user does not answer.
     *
     * @param msg               String message to ask
     * @param defaultAnswer     String default answer
     * @return the user's answer
     * @throws IOException if there was a problem reading input from the console
     */
    public String askUserWithDefaultAnswer(String msg, String defaultAnswer) throws IOException {
        String answer = promptMessageToUser(msg);
        if (StringUtils.isBlank(answer)) {
            return defaultAnswer;
        }
        return answer;
    }

    /**
     * This method allow you to ask a question to the user.
     * The answer is controlled before being return so the question will be ask until the user enter one the authorized answer
     *
     * @param msg               String message to ask
     * @param authorizedAnswer  Array of possible answer, all answer must be in lower case
     * @return the user answer
     * @throws IOException if there was an error retrieving an answer from the user on the console
     */
    public String askUserWithAuthorizedAnswer(String msg, List<String> authorizedAnswer) throws IOException {
        String answer;
        do {
            answer = promptMessageToUser(msg);
        } while (!authorizedAnswer.contains(answer.toLowerCase()));
        return answer;
    }

    /**
     * This method allow you to prompt a message to the user.
     * No control is done on the answer provided by the user.
     *
     * @param msg       String message to prompt
     * @return the user answer
     */
    public String promptMessageToUser(String msg) {
        if (session == null) {
            throw new IllegalStateException("Cannot prompt message: " + msg + " to user. " +
                    "(In case you are using the migration tool out of Karaf shell context, please check the migration configuration: org.apache.unomi.migration.cfg)");
        }
        LineReader reader = (LineReader) session.get(".jline.reader");
        return reader.readLine(msg, null);
    }

    /**
     * Print a message in the console.
     * @param msg the message to print out with a newline
     */
    public void printMessage(String msg) {
        if (session == null) {
            LOGGER.info("{}: {}", new Date(), msg);
        } else {
            PrintStream writer = session.getConsole();
            writer.printf("%s: %s%n",new Date(), msg);
            if (logToLogger) {
                LOGGER.info(msg);
            }
        }
    }

    /**
     * Print an exception along with a message in the console.
     * @param msg the message to print out with a newline
     * @param t the exception to dump in the shell console after the message
     */
    public void printException(String msg, Throwable t) {
        if (session == null) {
            LOGGER.error("{}", msg, t);
        } else {
            PrintStream writer = session.getConsole();
            writer.println(msg);
            t.printStackTrace(writer);
            if (logToLogger) {
                LOGGER.error(msg, t);
            }
        }
    }

    /**
     * Same as above without stacktrace
     * @param msg the message to print out with a newline
     */
    public void printException(String msg) {
        if (session == null) {
            LOGGER.error("{}", msg);
        } else {
            PrintStream writer = session.getConsole();
            writer.println(msg);
            if (logToLogger) {
                LOGGER.error(msg);
            }
        }
    }

    /**
     * Get config for property name, in case the property doesn't exist on file system config file
     * Best effort will be made to prompt question in karaf shell to get the needed information
     *
     * @param name the name of the property
     * @return the value of the property
     * @throws IOException
     */
    public String getConfigString(String name) throws IOException {
        // special handling for esAddress that need to be built
        if (CONFIG_ES_ADDRESS.equals(name)) {
            String esAddresses = getConfigString(CONFIG_ES_ADDRESSES);
            boolean sslEnabled = getConfigBoolean(CONFIG_ES_SSL_ENABLED);
            return (sslEnabled ? "https://" : "http://") + esAddresses.split(",")[0].trim();
        }

        if (migrationConfig.getConfig().containsKey(name)) {
            return migrationConfig.getConfig().get(name);
        }
        if (userConfig.containsKey(name)) {
            return userConfig.get(name);
        }
        if (configProperties.containsKey(name)) {
            MigrationConfigProperty migrateConfigProperty = configProperties.get(name);
            String answer = askUserWithDefaultAnswer(migrateConfigProperty.getDescription(), migrateConfigProperty.getDefaultValue());
            userConfig.put(name, answer);
            return answer;
        }
        return null;
    }

    /**
     * Get config for property name, in case the property doesn't exist on file system config file
     * Best effort will be made to prompt question in karaf shell to get the needed information
     *
     * @param name the name of the property
     * @return the value of the property
     * @throws IOException
     */
    public boolean getConfigBoolean(String name) throws IOException {
        if (migrationConfig.getConfig().containsKey(name)) {
            return Boolean.parseBoolean(migrationConfig.getConfig().get(name));
        }
        if (userConfig.containsKey(name)) {
            return Boolean.parseBoolean(userConfig.get(name));
        }
        if (configProperties.containsKey(name)) {
            MigrationConfigProperty migrateConfigProperty = configProperties.get(name);
            boolean answer = askUserWithAuthorizedAnswer(migrateConfigProperty.getDescription(), Arrays.asList("yes", "no")).equalsIgnoreCase("yes");
            userConfig.put(name, answer ? "true" : "false");
            return answer;
        }
        return false;
    }

    /**
     * This HTTP client is configured to be used for ElasticSearch requests to be able to perform migrations requests.
     * @return the http client.
     */
    public CloseableHttpClient getHttpClient() {
        return httpClient;
    }


    private void updateHistoryStep(String stepKey, MigrationStepState stepState) throws IOException {
        printMessage("Migration step: " + stepKey + " reach: " + stepState);
        history.put(stepKey, stepState);
        objectMapper.writeValue(MIGRATION_FS_HISTORY_FILE.toFile(), history);
    }

    protected void setHttpClient(CloseableHttpClient httpClient) {
        this.httpClient = httpClient;
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
