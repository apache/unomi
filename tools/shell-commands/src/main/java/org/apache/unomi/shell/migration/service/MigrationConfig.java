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

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Service uses to provide configuration information for the migration
 */
@Component(immediate = true, service = MigrationConfig.class, configurationPid = {"org.apache.unomi.migration"}, configurationPolicy = ConfigurationPolicy.REQUIRE)
public class MigrationConfig {

    public static final String CONFIG_ES_ADDRESS = "esAddress";
    public static final String CONFIG_ES_ADDRESSES = "esAddresses";
    public static final String CONFIG_ES_SSL_ENABLED = "esSSLEnabled";
    public static final String CONFIG_ES_LOGIN = "esLogin";
    public static final String CONFIG_ES_PASSWORD = "esPassword";
    public static final String CONFIG_TRUST_ALL_CERTIFICATES = "httpClient.trustAllCertificates";
    public static final String INDEX_PREFIX = "indexPrefix";
    public static final String NUMBER_OF_SHARDS = "number_of_shards";
    public static final String NUMBER_OF_REPLICAS = "number_of_replicas";
    public static final String TOTAL_FIELDS_LIMIT = "mapping.total_fields.limit";
    public static final String MAX_DOC_VALUE_FIELDS_SEARCH = "max_docvalue_fields_search";
    public static final String MONTHLY_NUMBER_OF_SHARDS = "monthlyIndex." + NUMBER_OF_SHARDS;
    public static final String MONTHLY_NUMBER_OF_REPLICAS = "monthlyIndex." + NUMBER_OF_REPLICAS;
    public static final String MONTHLY_TOTAL_FIELDS_LIMIT = "monthlyIndex." + TOTAL_FIELDS_LIMIT;
    public static final String MONTHLY_MAX_DOC_VALUE_FIELDS_SEARCH = "monthlyIndex." + MAX_DOC_VALUE_FIELDS_SEARCH;
    public static final String MIGRATION_HISTORY_RECOVER = "recoverFromHistory";
    public static final String ROLLOVER_MAX_AGE = "rolloverMaxAge";
    public static final String ROLLOVER_MAX_SIZE = "rolloverMaxSize";
    public static final String ROLLOVER_MAX_DOCS = "rolloverMaxDocs";
    public static final String SEARCH_ENGINE = "searchEngine";
    protected static final Map<String, MigrationConfigProperty> configProperties;
    static {
        Map<String, MigrationConfigProperty> m = new HashMap<>();
        m.put(SEARCH_ENGINE, new MigrationConfigProperty("Enter search engine to use (default: elasticsearch): ", "elasticsearch"));
        m.put(CONFIG_ES_ADDRESSES, new MigrationConfigProperty("Enter search engine TARGET address (default: localhost:9200): ", "localhost:9200"));
        m.put(CONFIG_ES_SSL_ENABLED, new MigrationConfigProperty("Should the search engine TARGET connection be established using SSL (https) protocol ? (yes/no)", null));
        m.put(CONFIG_ES_LOGIN, new MigrationConfigProperty("Enter search engine TARGET login (default: none): ", ""));
        m.put(CONFIG_ES_PASSWORD, new MigrationConfigProperty("Enter search engine TARGET password (default: none): ", ""));
        m.put(CONFIG_TRUST_ALL_CERTIFICATES, new MigrationConfigProperty("We need to initialize a HttpClient, do we need to trust all certificates ? (yes/no)", null));
        m.put(INDEX_PREFIX, new MigrationConfigProperty("Enter search engine Unomi indices prefix (default: context): ", "context"));
        m.put(NUMBER_OF_SHARDS, new MigrationConfigProperty("Enter search engine index mapping configuration: number_of_shards (default: 5): ", "5"));
        m.put(NUMBER_OF_REPLICAS, new MigrationConfigProperty("Enter search engine index mapping configuration: number_of_replicas (default: 0): ", "0"));
        m.put(TOTAL_FIELDS_LIMIT, new MigrationConfigProperty("Enter search engine index mapping configuration: mapping.total_fields.limit (default: 1000): ", "1000"));
        m.put(MAX_DOC_VALUE_FIELDS_SEARCH, new MigrationConfigProperty("Enter search engine index mapping configuration: max_docvalue_fields_search (default: 1000): ", "1000"));
        m.put(MONTHLY_NUMBER_OF_SHARDS, new MigrationConfigProperty("Enter search engine monthly index (event, session) mapping configuration: number_of_shards (default: 5): ", "5"));
        m.put(MONTHLY_NUMBER_OF_REPLICAS, new MigrationConfigProperty("Enter search engine monthly index (event, session) mapping configuration: number_of_replicas (default: 0): ", "0"));
        m.put(MONTHLY_TOTAL_FIELDS_LIMIT, new MigrationConfigProperty("Enter search engine monthly index (event, session) mapping configuration: mapping.total_fields.limit (default: 1000): ", "1000"));
        m.put(MONTHLY_MAX_DOC_VALUE_FIELDS_SEARCH, new MigrationConfigProperty("Enter search engine monthly index (event, session) mapping configuration: max_docvalue_fields_search (default: 1000): ", "1000"));
        m.put(MIGRATION_HISTORY_RECOVER, new MigrationConfigProperty("We found an existing migration attempt, should we restart from it ? (this will avoid redoing steps already completed successfully) (yes/no)", null));
        m.put(ROLLOVER_MAX_AGE, new MigrationConfigProperty("Enter search engine index rollover configuration: max_age (default: null): ", null));
        m.put(ROLLOVER_MAX_SIZE, new MigrationConfigProperty("Enter search engine index rollover configuration: max_size (default: 30gb): ", "30gb"));
        m.put(ROLLOVER_MAX_DOCS, new MigrationConfigProperty("Enter search engine index rollover configuration: max_docs (default: null): ", null));

        configProperties = Collections.unmodifiableMap(m);
    }

    private Map<String, String> config = new HashMap<>();

    @Activate
    @Modified
    public void modified(Map<String, String> config) {
        this.config = config;
    }

    protected Map<String, String> getConfig() {
        return this.config;
    }
}
