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
package org.apache.unomi.shell.migration;

import org.apache.karaf.shell.api.console.Session;
import org.apache.unomi.shell.migration.utils.ConsoleUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Service uses to aggregate different configuration needed by the migrations
 * Source of config:
 * - file system in OSGI config file: org.apache.unomi.migration.cfg
 * - user interactions in the console during the migration process
 */
@Component(immediate = true, service = MigrationConfig.class, configurationPid = {"org.apache.unomi.migration"})
public class MigrationConfig {

    public static final String CONFIG_ES_ADDRESS = "esAddress";
    public static final String CONFIG_ES_LOGIN = "esLogin";
    public static final String CONFIG_ES_PASSWORD = "esPassword";
    public static final String CONFIG_TRUST_ALL_CERTIFICATES = "httpClient.trustAllCertificates";
    public static final String INDEX_PREFIX = "indexPrefix";
    public static final String NUMBER_OF_SHARDS = "number_of_shards";
    public static final String NUMBER_OF_REPLICAS = "number_of_replicas";
    public static final String TOTAL_FIELDS_LIMIT = "mapping.total_fields.limit";
    public static final String MAX_DOC_VALUE_FIELDS_SEARCH = "max_docvalue_fields_search";

    private static final Map<String, MigrationConfigProperty> configProperties;
    static {
        Map<String, MigrationConfigProperty> m = new HashMap<>();
        m.put(CONFIG_ES_ADDRESS, new MigrationConfigProperty("Enter ElasticSearch TARGET address (default: http://localhost:9200): ", "http://localhost:9200"));
        m.put(CONFIG_ES_LOGIN, new MigrationConfigProperty("Enter ElasticSearch TARGET login (default: none): ", ""));
        m.put(CONFIG_ES_PASSWORD, new MigrationConfigProperty("Enter ElasticSearch TARGET password (default: none): ", ""));
        m.put(CONFIG_TRUST_ALL_CERTIFICATES, new MigrationConfigProperty("We need to initialize a HttpClient, do we need to trust all certificates ?", null));
        m.put(INDEX_PREFIX, new MigrationConfigProperty("Enter ElasticSearch Unomi indices prefix (default: context): ", "context"));
        m.put(NUMBER_OF_SHARDS, new MigrationConfigProperty("Enter ElasticSearch index mapping configuration: number_of_shards (default: 3): ", "3"));
        m.put(NUMBER_OF_REPLICAS, new MigrationConfigProperty("Enter ElasticSearch index mapping configuration: number_of_replicas (default: 0): ", "0"));
        m.put(TOTAL_FIELDS_LIMIT, new MigrationConfigProperty("Enter ElasticSearch index mapping configuration: mapping.total_fields.limit (default: 1000): ", "1000"));
        m.put(MAX_DOC_VALUE_FIELDS_SEARCH, new MigrationConfigProperty("Enter ElasticSearch index mapping configuration: max_docvalue_fields_search (default: 1000): ", "1000"));
        configProperties = Collections.unmodifiableMap(m);
    }

    Map<String, String> initialConfig = new HashMap<>();
    Map<String, String> computeConfig = new HashMap<>();

    @Activate
    @Modified
    public void modified(Map<String, String> config) {
        initialConfig = config;
        reset();
    }

    /**
     * Used reset user choices to initial file system config (useful at the beginning of each new migrate session)
     */
    public void reset() {
        computeConfig.clear();
        computeConfig.putAll(initialConfig);
    }

    public String getString(String name, Session session) throws IOException {
        if (computeConfig.containsKey(name)) {
            return computeConfig.get(name);
        }
        if (configProperties.containsKey(name)) {
            MigrationConfigProperty migrateConfigProperty = configProperties.get(name);
            String answer = ConsoleUtils.askUserWithDefaultAnswer(session, migrateConfigProperty.getDescription(), migrateConfigProperty.getDefaultValue());
            computeConfig.put(name, answer);
            return answer;
        }
        return null;
    }

    public boolean getBoolean(String name, Session session) throws IOException {
        if (computeConfig.containsKey(name)) {
            return Boolean.parseBoolean(computeConfig.get(name));
        }
        if (configProperties.containsKey(name)) {
            MigrationConfigProperty migrateConfigProperty = configProperties.get(name);
            boolean answer = ConsoleUtils.askUserWithAuthorizedAnswer(session, migrateConfigProperty.getDescription(), Arrays.asList("yes", "no")).equalsIgnoreCase("yes");
            computeConfig.put(name, answer ? "true" : "false");
            return answer;
        }
        return false;
    }
}
