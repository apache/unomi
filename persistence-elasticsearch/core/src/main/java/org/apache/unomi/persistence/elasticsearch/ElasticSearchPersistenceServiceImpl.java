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
package org.apache.unomi.persistence.elasticsearch;

import co.elastic.clients.elasticsearch._helpers.bulk.BulkIngester;
import co.elastic.clients.elasticsearch._helpers.bulk.BulkListener;
import co.elastic.clients.elasticsearch._types.*;
import co.elastic.clients.elasticsearch._types.aggregations.*;
import co.elastic.clients.elasticsearch._types.analysis.CustomAnalyzer;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.UpdateAction;
import co.elastic.clients.elasticsearch.core.bulk.UpdateOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.elasticsearch.ilm.*;
import co.elastic.clients.elasticsearch.indices.*;
import co.elastic.clients.elasticsearch.indices.Alias;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexTemplateRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.RefreshRequest;
import co.elastic.clients.elasticsearch.indices.get_alias.IndexAliases;
import co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord;
import co.elastic.clients.elasticsearch.indices.put_index_template.IndexTemplateMapping;
import co.elastic.clients.elasticsearch.tasks.GetTasksRequest;
import co.elastic.clients.elasticsearch.tasks.GetTasksResponse;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.transport.BackoffPolicy;
import co.elastic.clients.transport.endpoints.BooleanResponse;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.rest_client.RestClientOptions;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.stream.JsonGenerator;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.log4j.Level;
import org.apache.unomi.api.*;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.DateRange;
import org.apache.unomi.api.query.IpRange;
import org.apache.unomi.api.query.NumericRange;
import org.apache.unomi.api.security.SecurityServiceConfiguration;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.tenants.TenantTransformationListener;
import org.apache.unomi.metrics.MetricAdapter;
import org.apache.unomi.metrics.MetricsService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.aggregate.*;
import org.apache.unomi.persistence.spi.aggregate.DateRangeAggregate;
import org.apache.unomi.persistence.spi.aggregate.IpRangeAggregate;
import org.apache.unomi.persistence.spi.conditions.ConditionContextHelper;
import org.apache.unomi.persistence.spi.conditions.evaluator.ConditionEvaluatorDispatcher;
import org.apache.unomi.persistence.spi.config.ConfigurationUpdateHelper;
import org.elasticsearch.client.*;

import org.osgi.framework.*;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.apache.unomi.api.tenants.TenantService.SYSTEM_TENANT;

@SuppressWarnings("rawtypes")
public class ElasticSearchPersistenceServiceImpl implements PersistenceService, SynchronousBundleListener, ManagedService {

    public static final String SEQ_NO = "seq_no";
    public static final String PRIMARY_TERM = "primary_term";

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchPersistenceServiceImpl.class.getName());
    private static final String ROLLOVER_LIFECYCLE_NAME = "unomi-rollover-policy";

    private volatile boolean shuttingDown = false;
    private boolean throwExceptions = false;
    private ElasticsearchClient esClient;
    private BulkIngester bulkIngester;
    private String elasticSearchAddresses;
    private final List<String> elasticSearchAddressList = new ArrayList<>();
    private String indexPrefix;
    private String numberOfShards;
    private String numberOfReplicas;
    private String indexMappingTotalFieldsLimit;
    private String indexMaxDocValueFieldsSearch;
    private String[] fatalIllegalStateErrors;
    private BundleContext bundleContext;
    private final Map<String, String> mappings = new HashMap<String, String>();
    private ConditionEvaluatorDispatcher conditionEvaluatorDispatcher;
    private ConditionESQueryBuilderDispatcher conditionESQueryBuilderDispatcher;
    private Map<String, String> routingByType;

    private Integer defaultQueryLimit = 10;
    private final Integer removeByQueryTimeoutInMinutes = 10;
    private Integer taskWaitingTimeout = 3600000;
    private Integer taskWaitingPollingInterval = 1000;

    private String bulkProcessorConcurrentRequests = "1";
    private String bulkProcessorBulkActions = "1000";
    private Long bulkProcessorBulkSize = 5L;
    private Long bulkProcessorFlushIntervalInSeconds = 5L;
    private String bulkProcessorBackoffPolicy = "exponential";

    // Rollover configuration
    private String sessionLatestIndex;
    private List<String> rolloverIndices;
    private String rolloverMaxSize;
    private String rolloverMaxAge;
    private String rolloverMaxDocs;
    private String rolloverIndexNumberOfShards;
    private String rolloverIndexNumberOfReplicas;
    private String rolloverIndexMappingTotalFieldsLimit;
    private String rolloverIndexMaxDocValueFieldsSearch;

    private String minimalElasticSearchVersion = "9.0.3";
    private String maximalElasticSearchVersion = "10.0.0";

    // authentication props
    private String username;
    private String password;
    private boolean sslEnable = false;
    private boolean sslTrustAllCertificates = false;

    private int aggregateQueryBucketSize = 5000;

    private MetricsService metricsService;
    private boolean useBatchingForSave = false;
    private boolean useBatchingForUpdate = true;
    private String logLevelRestClient = "ERROR";
    private boolean alwaysOverwrite = true;
    private boolean aggQueryThrowOnMissingDocs = false;
    private Integer aggQueryMaxResponseSizeHttp = null;
    private Integer clientSocketTimeout = null;
    private Map<String, Refresh> itemTypeToRefreshPolicy = new HashMap<>();

    private final Map<String, Map<String, Map<String, Object>>> knownMappings = new HashMap<>();

    private static final Map<String, String> itemTypeIndexNameMap = new HashMap<>();
    private static final Collection<String> systemItems = Arrays.asList("actionType", "campaign", "campaignevent", "goal", "userList",
            "propertyType", "scope", "conditionType", "rule", "scoring", "segment", "groovyAction", "topic", "patch", "jsonSchema",
            "importConfig", "exportConfig", "rulestats");

    static {
        for (String systemItem : systemItems) {
            itemTypeIndexNameMap.put(systemItem, "systemItems");
        }

        itemTypeIndexNameMap.put("profile", "profile");
        itemTypeIndexNameMap.put("persona", "profile");
    }

    private volatile ExecutionContextManager contextManager = null;
    private List<TenantTransformationListener> transformationListeners = new CopyOnWriteArrayList<>();

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setElasticSearchAddresses(String elasticSearchAddresses) {
        this.elasticSearchAddresses = elasticSearchAddresses;
        String[] elasticSearchAddressesArray = elasticSearchAddresses.split(",");
        elasticSearchAddressList.clear();
        for (String elasticSearchAddress : elasticSearchAddressesArray) {
            elasticSearchAddressList.add(elasticSearchAddress.trim());
        }
    }

    public void setItemTypeToRefreshPolicy(String itemTypeToRefreshPolicy) throws IOException {
        if (!itemTypeToRefreshPolicy.isEmpty()) {
            this.itemTypeToRefreshPolicy = new ObjectMapper().readValue(itemTypeToRefreshPolicy,
                    new TypeReference<HashMap<String, Refresh>>() {
                    });
        }
    }

    public void setFatalIllegalStateErrors(String fatalIllegalStateErrors) {
        this.fatalIllegalStateErrors = Arrays.stream(fatalIllegalStateErrors.split(",")).map(i -> i.trim()).filter(i -> !i.isEmpty())
                .toArray(String[]::new);
    }

    public void setAggQueryMaxResponseSizeHttp(String aggQueryMaxResponseSizeHttp) {
        if (StringUtils.isNumeric(aggQueryMaxResponseSizeHttp)) {
            this.aggQueryMaxResponseSizeHttp = Integer.parseInt(aggQueryMaxResponseSizeHttp);
        }
    }

    public void setIndexPrefix(String indexPrefix) {
        this.indexPrefix = indexPrefix;
    }

    public void setNumberOfShards(String numberOfShards) {
        this.numberOfShards = numberOfShards;
    }

    public void setNumberOfReplicas(String numberOfReplicas) {
        this.numberOfReplicas = numberOfReplicas;
    }

    public void setIndexMappingTotalFieldsLimit(String indexMappingTotalFieldsLimit) {
        this.indexMappingTotalFieldsLimit = indexMappingTotalFieldsLimit;
    }

    public void setIndexMaxDocValueFieldsSearch(String indexMaxDocValueFieldsSearch) {
        this.indexMaxDocValueFieldsSearch = indexMaxDocValueFieldsSearch;
    }

    public void setDefaultQueryLimit(Integer defaultQueryLimit) {
        this.defaultQueryLimit = defaultQueryLimit;
    }

    public void setRoutingByType(Map<String, String> routingByType) {
        this.routingByType = routingByType;
    }

    public void setConditionEvaluatorDispatcher(ConditionEvaluatorDispatcher conditionEvaluatorDispatcher) {
        this.conditionEvaluatorDispatcher = conditionEvaluatorDispatcher;
    }

    public void setConditionESQueryBuilderDispatcher(ConditionESQueryBuilderDispatcher conditionESQueryBuilderDispatcher) {
        this.conditionESQueryBuilderDispatcher = conditionESQueryBuilderDispatcher;
    }

    public void setBulkProcessorConcurrentRequests(String bulkProcessorConcurrentRequests) {
        this.bulkProcessorConcurrentRequests = bulkProcessorConcurrentRequests;
    }

    public void setBulkProcessorBulkActions(String bulkProcessorBulkActions) {
        this.bulkProcessorBulkActions = bulkProcessorBulkActions;
    }

    public void setBulkProcessorBulkSize(Long bulkProcessorBulkSize) {
        this.bulkProcessorBulkSize = bulkProcessorBulkSize;
    }

    public void setBulkProcessorFlushIntervalInSeconds(Long bulkProcessorFlushIntervalInSeconds) {
        this.bulkProcessorFlushIntervalInSeconds = bulkProcessorFlushIntervalInSeconds;
    }

    public void setBulkProcessorBackoffPolicy(String bulkProcessorBackoffPolicy) {
        this.bulkProcessorBackoffPolicy = bulkProcessorBackoffPolicy;
    }

    public void setRolloverIndices(String rolloverIndices) {
        this.rolloverIndices = StringUtils.isNotEmpty(rolloverIndices) ? Arrays.asList(rolloverIndices.split(",").clone()) : null;
    }

    public void setRolloverMaxSize(String rolloverMaxSize) {
        this.rolloverMaxSize = rolloverMaxSize;
    }

    public void setRolloverMaxAge(String rolloverMaxAge) {
        this.rolloverMaxAge = rolloverMaxAge;
    }

    public void setRolloverMaxDocs(String rolloverMaxDocs) {
        this.rolloverMaxDocs = rolloverMaxDocs;
    }

    public void setRolloverIndexNumberOfShards(String rolloverIndexNumberOfShards) {
        this.rolloverIndexNumberOfShards = rolloverIndexNumberOfShards;
    }

    public void setRolloverIndexNumberOfReplicas(String rolloverIndexNumberOfReplicas) {
        this.rolloverIndexNumberOfReplicas = rolloverIndexNumberOfReplicas;
    }

    public void setRolloverIndexMappingTotalFieldsLimit(String rolloverIndexMappingTotalFieldsLimit) {
        this.rolloverIndexMappingTotalFieldsLimit = rolloverIndexMappingTotalFieldsLimit;
    }

    public void setRolloverIndexMaxDocValueFieldsSearch(String rolloverIndexMaxDocValueFieldsSearch) {
        this.rolloverIndexMaxDocValueFieldsSearch = rolloverIndexMaxDocValueFieldsSearch;
    }

    public void setMinimalElasticSearchVersion(String minimalElasticSearchVersion) {
        this.minimalElasticSearchVersion = minimalElasticSearchVersion;
    }

    public void setMaximalElasticSearchVersion(String maximalElasticSearchVersion) {
        this.maximalElasticSearchVersion = maximalElasticSearchVersion;
    }

    public void setAggregateQueryBucketSize(int aggregateQueryBucketSize) {
        this.aggregateQueryBucketSize = aggregateQueryBucketSize;
    }

    public void setClientSocketTimeout(String clientSocketTimeout) {
        if (StringUtils.isNumeric(clientSocketTimeout)) {
            this.clientSocketTimeout = Integer.parseInt(clientSocketTimeout);
        }
    }

    public void setMetricsService(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    public void setUseBatchingForSave(boolean useBatchingForSave) {
        this.useBatchingForSave = useBatchingForSave;
    }

    public void setUseBatchingForUpdate(boolean useBatchingForUpdate) {
        this.useBatchingForUpdate = useBatchingForUpdate;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setSslEnable(boolean sslEnable) {
        this.sslEnable = sslEnable;
    }

    public void setSslTrustAllCertificates(boolean sslTrustAllCertificates) {
        this.sslTrustAllCertificates = sslTrustAllCertificates;
    }

    public void setAggQueryThrowOnMissingDocs(boolean aggQueryThrowOnMissingDocs) {
        this.aggQueryThrowOnMissingDocs = aggQueryThrowOnMissingDocs;
    }

    public void setThrowExceptions(boolean throwExceptions) {
        this.throwExceptions = throwExceptions;
    }

    public void setAlwaysOverwrite(boolean alwaysOverwrite) {
        this.alwaysOverwrite = alwaysOverwrite;
    }

    public void setLogLevelRestClient(String logLevelRestClient) {
        this.logLevelRestClient = logLevelRestClient;
    }

    public void setTaskWaitingTimeout(String taskWaitingTimeout) {
        if (StringUtils.isNumeric(taskWaitingTimeout)) {
            this.taskWaitingTimeout = Integer.parseInt(taskWaitingTimeout);
        }
    }

    public void setTaskWaitingPollingInterval(String taskWaitingPollingInterval) {
        if (StringUtils.isNumeric(taskWaitingPollingInterval)) {
            this.taskWaitingPollingInterval = Integer.parseInt(taskWaitingPollingInterval);
        }
    }

    /**
     * Check if the current cluster version is in the expected range
     *
     * @return true if the version of the current elasticsearch is not in the expected range
     */
    private boolean versionIsNotCompatible() throws IOException {
        InfoResponse info = esClient.info();
        String currentVersion = info.version().number();

        return compareVersions(currentVersion, minimalElasticSearchVersion) < 0
                || compareVersions(currentVersion, maximalElasticSearchVersion) >= 0;
    }

    /**
     * Compare to semantic versions
     *
     * @param version1 First version
     * @param version2 Second vrsion
     * @return positive if version1 > version2, 0 if equals, negative if version1 < version2
     */
    private static int compareVersions(String version1, String version2) {
        String[] parts1 = version1.split("\\.");
        String[] parts2 = version2.split("\\.");

        int length = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < length; i++) {
            int part1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int part2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;

            if (part1 != part2) {
                return part1 - part2;
            }
        }

        return 0;
    }

    public void bindContextManager(ExecutionContextManager contextManager) {
        this.contextManager = contextManager;
        LOGGER.info("ExecutionContextManager bound");
    }

    public void unbindContextManager(ExecutionContextManager contextManager) {
        if (this.contextManager == contextManager) {
            this.contextManager = null;
            LOGGER.info("ExecutionContextManager unbound");
        }
    }

    private String getTenantId() {
        if (contextManager == null) {
            return SYSTEM_TENANT;
        }
        ExecutionContext context = contextManager.getCurrentContext();
        if (context == null || context.getTenantId() == null) {
            return SYSTEM_TENANT;
        }
        return context.getTenantId();
    }

    private String validateTenantAndGetId(String permission) {
        String tenantId = getTenantId();
        if (contextManager != null && contextManager.getCurrentContext() != null) {
            contextManager.getCurrentContext().validateAccess(permission);
        }
        return tenantId;
    }

    public void start() throws Exception {

        // Work around to avoid ES Logs regarding the deprecated [ignore_throttled] parameter
        try {
            Level lvl = Level.toLevel(logLevelRestClient, Level.ERROR);
            //TODO ensure this is necessary
            org.apache.log4j.Logger.getLogger("org.elasticsearch.client.RestClient").setLevel(lvl);
        } catch (Exception e) {
            // Never fail because of the set of the logger
        }

        // on startup
        new InClassLoaderExecute<>(null, null, this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            public Object execute(Object... args) throws Exception {

                buildClient();

                if (versionIsNotCompatible()) {
                    throw new Exception(
                            "ElasticSearch version is not within [" + minimalElasticSearchVersion + "," + maximalElasticSearchVersion
                                    + "), aborting startup !");
                }

                registerRolloverLifecyclePolicy();

                loadPredefinedMappings(bundleContext, false);
                loadPainlessScripts(bundleContext);

                // load predefined mappings and condition dispatchers of any bundles that were started before this one.
                for (Bundle existingBundle : bundleContext.getBundles()) {
                    if (existingBundle.getBundleContext() != null) {
                        loadPredefinedMappings(existingBundle.getBundleContext(), false);
                        loadPainlessScripts(existingBundle.getBundleContext());
                    }
                }

                // Wait for green
                LOGGER.info("Waiting for GREEN cluster status...");
                esClient.cluster().health(builder -> builder.waitForStatus(HealthStatus.Green));
                LOGGER.info("Cluster status is GREEN");

                // We keep in memory the latest available session index to be able to load session using direct GET access on ES
                if (isItemTypeRollingOver(Session.ITEM_TYPE)) {
                    LOGGER.info("Sessions are using rollover indices, loading latest session index available ...");
                    GetAliasResponse getAliasResponse = esClient.indices().getAlias(builder -> builder.name(getIndex(Session.ITEM_TYPE)));
                    Map<String, IndexAliases> aliases = getAliasResponse.aliases();
                    if (!aliases.isEmpty()) {
                        sessionLatestIndex = new TreeSet<>(aliases.keySet()).last();
                        LOGGER.info("Latest available session index found is: {}", sessionLatestIndex);
                    } else {
                        throw new IllegalStateException("No index found for sessions");
                    }
                }

                return true;
            }
        }.executeInClassLoader();

        bundleContext.addBundleListener(this);

        LOGGER.info("{} service started successfully.", this.getClass().getName());
    }

    private List<HttpHost> getHosts() {
        List<HttpHost> hosts = new ArrayList<>();
        for (String elasticSearchAddress : elasticSearchAddressList) {
            String[] elasticSearchAddressParts = elasticSearchAddress.split(":");
            String elasticSearchHostName = elasticSearchAddressParts[0];
            int elasticSearchPort = Integer.parseInt(elasticSearchAddressParts[1]);
            hosts.add(new HttpHost(elasticSearchHostName, elasticSearchPort, sslEnable ? "https" : "http"));
        }
        return hosts;
    }

    private void buildClient() throws NoSuchFieldException, IllegalAccessException {
        ElasticsearchClientFactory.ClientBuilder esClienBuilder = ElasticsearchClientFactory.builder();

        if (sslTrustAllCertificates) {
            final SSLContext sslContext;
            try {
                sslContext = SSLContext.getInstance("SSL");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
            try {
                sslContext.init(null, new TrustManager[] { new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                } }, new SecureRandom());
                esClienBuilder.sslContext(sslContext);
            } catch (KeyManagementException e) {
                LOGGER.error("Error creating SSL Context for trust all certificates", e);
            }
        }

        esClient = esClienBuilder.hosts(getHosts()).socketTimeout(clientSocketTimeout)
                .usernameAndPassword(username, password).build();

        buildBulkIngester();
        LOGGER.info("Connecting to ElasticSearch persistence backend using index prefix {}...", indexPrefix);
    }

    public BulkIngester buildBulkIngester() {
        if (bulkIngester != null) {
            return bulkIngester;
        }
        BulkListener<String> listener = new BulkListener<String>() {
            @Override public void beforeBulk(long executionId, BulkRequest request, List<String> strings) {
                LOGGER.debug("Before Bulk");
            }

            @Override public void afterBulk(long executionId, BulkRequest request, List<String> strings, BulkResponse response) {
                LOGGER.debug("After Bulk");
            }

            @Override public void afterBulk(long executionId, BulkRequest request, List<String> strings, Throwable failure) {
                LOGGER.error("After Bulk (failure)", failure);

            }
        };

        BulkIngester.Builder ingesterBuilder = new BulkIngester.Builder().client(esClient).maxOperations(100)
                .flushInterval(1, TimeUnit.SECONDS).listener(listener);

        if (bulkProcessorConcurrentRequests != null) {
            int concurrentRequests = Integer.parseInt(bulkProcessorConcurrentRequests);
            if (concurrentRequests > 1) {
                ingesterBuilder.maxConcurrentRequests(concurrentRequests);
            }
        }
        if (bulkProcessorBulkActions != null) {
            int bulkActions = Integer.parseInt(bulkProcessorBulkActions);
            ingesterBuilder.maxOperations(bulkActions);
        }
        if (bulkProcessorBulkSize != null) {
            // Default is 5MB
            ingesterBuilder.maxSize(bulkProcessorBulkSize * 1024 * 1024);
        }

        if (bulkProcessorFlushIntervalInSeconds != null) {
            ingesterBuilder.flushInterval(bulkProcessorFlushIntervalInSeconds, TimeUnit.SECONDS);
        } else {
            // in ElasticSearch this defaults to null, but we would like to set a value to 5 seconds by default
            ingesterBuilder.flushInterval(5, TimeUnit.SECONDS);
        }
        if (bulkProcessorBackoffPolicy != null) {
            String backoffPolicyStr = bulkProcessorBackoffPolicy;
            if (backoffPolicyStr != null && backoffPolicyStr.length() > 0) {
                backoffPolicyStr = backoffPolicyStr.toLowerCase();
                if ("nobackoff".equals(backoffPolicyStr)) {
                    ingesterBuilder.backoffPolicy(BackoffPolicy.noBackoff());
                } else if (backoffPolicyStr.startsWith("constant(")) {
                    int paramStartPos = backoffPolicyStr.indexOf("constant(" + "constant(".length());
                    int paramEndPos = backoffPolicyStr.indexOf(")", paramStartPos);
                    int paramSeparatorPos = backoffPolicyStr.indexOf(",", paramStartPos);
                    Long delay = Long.valueOf(backoffPolicyStr.substring(paramStartPos, paramSeparatorPos));

                    int maxNumberOfRetries = Integer.parseInt(backoffPolicyStr.substring(paramSeparatorPos + 1, paramEndPos));
                    // Delay is in ms
                    ingesterBuilder.backoffPolicy(BackoffPolicy.constantBackoff(delay != null ? delay : 5000, maxNumberOfRetries));
                } else if (backoffPolicyStr.startsWith("exponential")) {
                    if (!backoffPolicyStr.contains("(")) {
                        ingesterBuilder.backoffPolicy(BackoffPolicy.exponentialBackoff());
                    } else {
                        // we detected parameters, must process them.
                        int paramStartPos = backoffPolicyStr.indexOf("exponential(" + "exponential(".length());
                        int paramEndPos = backoffPolicyStr.indexOf(")", paramStartPos);
                        int paramSeparatorPos = backoffPolicyStr.indexOf(",", paramStartPos);
                        Long delay = Long.valueOf(backoffPolicyStr.substring(paramStartPos, paramSeparatorPos));
                        int maxNumberOfRetries = Integer.parseInt(backoffPolicyStr.substring(paramSeparatorPos + 1, paramEndPos));
                        ingesterBuilder.backoffPolicy(BackoffPolicy.exponentialBackoff(delay != null ? delay : 5000, maxNumberOfRetries));
                    }
                }
            }
        }

        bulkIngester = ingesterBuilder.build();
        return bulkIngester;
    }

    public void stop() {
        new InClassLoaderExecute<>(null, null, this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Object execute(Object... args) throws IOException {
                LOGGER.info("Closing ElasticSearch persistence backend...");
                if (esClient != null) {
                    esClient.close();
                }
                return null;
            }
        }.catchingExecuteInClassLoader(true);

        bundleContext.removeBundleListener(this);
    }

    public void bindConditionESQueryBuilder(ServiceReference<ConditionESQueryBuilder> conditionESQueryBuilderServiceReference) {
        ConditionESQueryBuilder conditionESQueryBuilder = bundleContext.getService(conditionESQueryBuilderServiceReference);
        conditionESQueryBuilderDispatcher.addQueryBuilder(conditionESQueryBuilderServiceReference.getProperty("queryBuilderId").toString(),
                conditionESQueryBuilder);
    }

    public void unbindConditionESQueryBuilder(ServiceReference<ConditionESQueryBuilder> conditionESQueryBuilderServiceReference) {
        if (conditionESQueryBuilderServiceReference == null) {
            return;
        }
        conditionESQueryBuilderDispatcher.removeQueryBuilder(
                conditionESQueryBuilderServiceReference.getProperty("queryBuilderId").toString());
    }

    @Override
    public void bundleChanged(BundleEvent event) {
        if (event.getType() == BundleEvent.STARTING) {
            if (contextManager != null) {
                contextManager.executeAsSystem(() -> {
                    loadPredefinedMappings(event.getBundle().getBundleContext(), true);
                    loadPainlessScripts(event.getBundle().getBundleContext());
                });
            } else {
                // If security service is not available, execute directly as operations won't be validated
                loadPredefinedMappings(event.getBundle().getBundleContext(), true);
                loadPainlessScripts(event.getBundle().getBundleContext());
            }
        }
    }

    @Override
    public void updated(Dictionary<String, ?> properties) {
        Map<String, ConfigurationUpdateHelper.PropertyMapping> propertyMappings = new HashMap<>();

        // Boolean properties
        propertyMappings.put("throwExceptions", ConfigurationUpdateHelper.booleanProperty(this::setThrowExceptions));
        propertyMappings.put("alwaysOverwrite", ConfigurationUpdateHelper.booleanProperty(this::setAlwaysOverwrite));
        propertyMappings.put("useBatchingForSave", ConfigurationUpdateHelper.booleanProperty(this::setUseBatchingForSave));
        propertyMappings.put("useBatchingForUpdate", ConfigurationUpdateHelper.booleanProperty(this::setUseBatchingForUpdate));
        propertyMappings.put("aggQueryThrowOnMissingDocs", ConfigurationUpdateHelper.booleanProperty(this::setAggQueryThrowOnMissingDocs));

        // String properties
        propertyMappings.put("logLevelRestClient", ConfigurationUpdateHelper.stringProperty(this::setLogLevelRestClient));
        propertyMappings.put("clientSocketTimeout", ConfigurationUpdateHelper.stringProperty(this::setClientSocketTimeout));
        propertyMappings.put("taskWaitingTimeout", ConfigurationUpdateHelper.stringProperty(this::setTaskWaitingTimeout));
        propertyMappings.put("taskWaitingPollingInterval", ConfigurationUpdateHelper.stringProperty(this::setTaskWaitingPollingInterval));
        propertyMappings.put("aggQueryMaxResponseSizeHttp", ConfigurationUpdateHelper.stringProperty(this::setAggQueryMaxResponseSizeHttp));

        // Integer properties
        propertyMappings.put("aggregateQueryBucketSize", ConfigurationUpdateHelper.integerProperty(this::setAggregateQueryBucketSize));

        // Custom property for itemTypeToRefreshPolicy with IOException handling
        propertyMappings.put("itemTypeToRefreshPolicy", ConfigurationUpdateHelper.customProperty((value, logger) -> {
            try {
                setItemTypeToRefreshPolicy(value.toString());
            } catch (IOException e) {
                logger.warn("Error setting itemTypeToRefreshPolicy: {}", e.getMessage());
            }
        }));

        ConfigurationUpdateHelper.processConfigurationUpdates(properties, LOGGER, "ElasticSearch persistence", propertyMappings);
    }

    private void loadPredefinedMappings(BundleContext bundleContext, boolean forceUpdateMapping) {
        Enumeration<URL> predefinedMappings = bundleContext.getBundle().findEntries("META-INF/cxs/mappings", "*.json", true);
        if (predefinedMappings == null) {
            return;
        }
        while (predefinedMappings.hasMoreElements()) {
            URL predefinedMappingURL = predefinedMappings.nextElement();
            LOGGER.info("Found mapping at {}, loading... ", predefinedMappingURL);
            try {
                final String path = predefinedMappingURL.getPath();
                String name = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
                String mappingSource = loadMappingFile(predefinedMappingURL);

                mappings.put(name, mappingSource);

                if (!createIndex(name)) {
                    LOGGER.info("Found index for type {}", name);
                    if (forceUpdateMapping) {
                        LOGGER.info("Updating mapping for {}", name);
                        createMapping(name, mappingSource);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error while loading mapping definition {}", predefinedMappingURL, e);
            }
        }
    }

    private void loadPainlessScripts(BundleContext bundleContext) {
        Enumeration<URL> scriptsURL = bundleContext.getBundle().findEntries("META-INF/cxs/painless", "*.painless", true);
        if (scriptsURL == null) {
            return;
        }

        Map<String, String> scriptsById = new HashMap<>();
        while (scriptsURL.hasMoreElements()) {
            URL scriptURL = scriptsURL.nextElement();
            LOGGER.info("Found painless script at {}, loading... ", scriptURL);
            try (InputStream in = scriptURL.openStream()) {
                String script = IOUtils.toString(in, StandardCharsets.UTF_8);
                String scriptId = FilenameUtils.getBaseName(scriptURL.getPath());
                scriptsById.put(scriptId, script);
            } catch (Exception e) {
                LOGGER.error("Error while loading painless script {}", scriptURL, e);
            }

        }

        storeScripts(scriptsById);
    }

    private String loadMappingFile(URL predefinedMappingURL) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(predefinedMappingURL.openStream()));

        StringBuilder content = new StringBuilder();
        String l;
        while ((l = reader.readLine()) != null) {
            content.append(l);
        }
        return content.toString();
    }

    @Override public String getName() {
        return "elasticsearch";
    }

    @Override public <T extends Item> List<T> getAllItems(final Class<T> clazz) {
        return getAllItems(clazz, 0, -1, null).getList();
    }

    @Override public long getAllItemsCount(String itemType) {
        return queryCount(Query.of(q -> q.matchAll(m -> m)), itemType);
    }

    @Override public <T extends Item> PartialList<T> getAllItems(final Class<T> clazz, int offset, int size, String sortBy) {
        return getAllItems(clazz, offset, size, sortBy, null);
    }

    @Override public <T extends Item> PartialList<T> getAllItems(final Class<T> clazz, int offset, int size, String sortBy,
            String scrollTimeValidity) {
        long startTime = System.currentTimeMillis();
        try {
            return query(Query.of(q -> q.matchAll(m -> m)), sortBy, clazz, offset, size, null, scrollTimeValidity);
        } finally {
            if (metricsService != null && metricsService.isActivated()) {
                metricsService.updateTimer(this.getClass().getName() + ".getAllItems", startTime);
            }
        }
    }

    @Override public <T extends Item> T load(final String itemId, final Class<T> clazz) {
        return load(itemId, clazz, null);
    }

    @Override @Deprecated public <T extends Item> T load(final String itemId, final Date dateHint, final Class<T> clazz) {
        return load(itemId, clazz, null);
    }

    @Override @Deprecated public CustomItem loadCustomItem(final String itemId, final Date dateHint, String customItemType) {
        return load(itemId, CustomItem.class, customItemType);
    }

    @Override public CustomItem loadCustomItem(final String itemId, String customItemType) {
        return load(itemId, CustomItem.class, customItemType);
    }

    private <T extends Item> T load(final String itemId, final Class<T> clazz, final String customItemType) {
        if (StringUtils.isEmpty(itemId)) {
            return null;
        }

        return new InClassLoaderExecute<T>(metricsService, this.getClass().getName() + ".loadItem", this.bundleContext,
                this.fatalIllegalStateErrors, throwExceptions) {
            protected T execute(Object... args) throws Exception {
                try {
                    final String itemType = customItemType != null ? customItemType : Item.getItemType(clazz);
                    String documentId = getDocumentIDForItemType(itemId, itemType);

                    boolean sessionSpecialDirectAccess = sessionLatestIndex != null && Session.ITEM_TYPE.equals(itemType);
                    if (!sessionSpecialDirectAccess && isItemTypeRollingOver(itemType)) {
                        return new MetricAdapter<T>(metricsService, ".loadItemWithQuery") {
                            @Override public T execute(Object... args) throws Exception {
                                Query query = Query.of(q -> q.ids(builder -> builder.values(documentId)));
                                if (customItemType == null) {
                                    PartialList<T> r = query(query, null, clazz, 0, 1, null, null);
                                    if (r.size() > 0) {
                                        return r.get(0);
                                    }
                                } else {
                                    PartialList<CustomItem> r = query(query, null, customItemType, 0, 1, null, null);
                                    if (r.size() > 0) {
                                        return (T) r.get(0);
                                    }
                                }
                                return null;
                            }
                        }.execute();
                    } else {
                        // Special handling for session we check the latest available index directly to speed up session loading
                        GetRequest getRequest = GetRequest.of(
                                builder -> builder.index(sessionSpecialDirectAccess ? sessionLatestIndex : getIndex(itemType))
                                        .id(documentId));
                        GetResponse<T> response = esClient.get(getRequest, clazz);
                        if (response.found()) {
                            T value = response.source();
                            setMetadata(value, response.id(), response.version() != null ? response.version() : 0L,
                                    response.seqNo() != null ? response.seqNo() : 0L,
                                    response.primaryTerm() != null ? response.primaryTerm() : 0L, response.index());
                            return handleItemReverseTransformation(value);
                        } else {
                            return null;
                        }
                    }
                } catch (ElasticsearchException e) {
                    if (e.status() == 404 && e.getMessage() != null && e.getMessage().contains("index_not_found_exception")) {
                        // The index does not exist
                        return null;
                    }
                    return null;
                } catch (Exception ex) {
                    throw new Exception(
                            "Error loading itemType=" + clazz.getName() + " customItemType=" + customItemType + " itemId=" + itemId, ex);
                }
            }
        }.catchingExecuteInClassLoader(true);

    }

    private void setMetadata(Item item, String itemId, long version, long seqNo, long primaryTerm, String index) {
        if (item != null) {
            String strippedId = stripTenantFromDocumentId(itemId);
            if (!systemItems.contains(item.getItemType())) {
                // For non-system items, document ID format is: tenantId_itemId
                // The stripped ID is the itemId
                if (item.getItemId() == null) {
                    item.setItemId(strippedId);
                }
            } else {
                // For system items, document ID format is: tenantId_itemId_itemType
                // Extract the itemId by removing the itemType suffix from the document ID.
                // After migration 3.1.0-05, all system items should have:
                // - Document IDs with the itemType suffix (post-2.2.0 format)
                // - Correct itemIds in source (fixed by migration 3.1.0-05)
                // This simplified logic works because the migration normalizes the data.
                String itemTypeSuffix = "_" + item.getItemType().toLowerCase();
                if (strippedId != null && strippedId.endsWith(itemTypeSuffix)) {
                    // Document ID has the expected suffix format - extract itemId by removing the suffix
                    String extractedItemId = strippedId.substring(0, strippedId.length() - itemTypeSuffix.length());
                    item.setItemId(extractedItemId);
                } else {
                    // Document ID doesn't have the suffix (old data pre-2.2.0 migration, or edge case)
                    // Use source itemId if available and doesn't end with suffix (trustworthy),
                    // otherwise use strippedId as fallback
                    String sourceItemId = item.getItemId();
                    if (sourceItemId != null && !sourceItemId.endsWith(itemTypeSuffix)) {
                        // Source itemId exists and is trustworthy - keep it
                        // itemId is already set correctly, no need to change it
                    } else {
                        // No trustworthy source itemId - use strippedId as fallback
                        item.setItemId(strippedId);
                    }
                }
            }
            item.setVersion(version);
            item.setSystemMetadata(SEQ_NO, seqNo);
            item.setSystemMetadata(PRIMARY_TERM, primaryTerm);
            item.setSystemMetadata("index", index);
        }
    }

    @Override public boolean isConsistent(Item item) {
        return getRefreshPolicy(item.getItemType()) != Refresh.False;
    }

    @Override public boolean save(final Item item) {
        return save(item, useBatchingForSave, alwaysOverwrite);
    }

    @Override public boolean save(final Item item, final boolean useBatching) {
        return save(item, useBatching, alwaysOverwrite);
    }

    @Override public boolean save(final Item item, final Boolean useBatchingOption, final Boolean alwaysOverwriteOption) {
        String finalTenantId = validateTenantAndGetId(SecurityServiceConfiguration.PERMISSION_SAVE);
        item.setTenantId(finalTenantId);

        final boolean useBatching = useBatchingOption == null ? this.useBatchingForSave : useBatchingOption;
        final boolean alwaysOverwrite = alwaysOverwriteOption == null ? this.alwaysOverwrite : alwaysOverwriteOption;

        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".saveItem", this.bundleContext,
                this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws Exception {
                try {
                    // Add tenants-specific transformation before save
                    handleItemTransformation(item);

                    String source = ESCustomObjectMapper.getObjectMapper().writeValueAsString(item);
                    String itemType = item.getItemType();
                    if (item instanceof CustomItem) {
                        itemType = ((CustomItem) item).getCustomItemType();
                    }
                    String documentId = getDocumentIDForItemType(item.getItemId(), itemType);
                    String index = item.getSystemMetadata("index") != null ? (String) item.getSystemMetadata("index") : getIndex(itemType);

                    Long seqNo;
                    Long primaryTerm;
                    OpType opType = null;
                    String routing;
                    if (!alwaysOverwrite) {
                        seqNo = (Long) item.getSystemMetadata(SEQ_NO);
                        primaryTerm = (Long) item.getSystemMetadata(PRIMARY_TERM);
                        opType = seqNo == null && primaryTerm == null ? OpType.Create : null;
                    } else {
                        primaryTerm = null;
                        seqNo = null;
                    }

                    if (routingByType.containsKey(itemType)) {
                        routing = routingByType.get(itemType);
                    } else {
                        routing = null;
                    }

                    try {
                        if (bulkIngester == null || !useBatching) {
                            IndexRequest.Builder<Object> indexRequestBuilder = new IndexRequest.Builder<>().index(index).id(documentId)
                                    .document(item).ifSeqNo(seqNo).ifPrimaryTerm(primaryTerm).opType(opType).routing(routing)
                                    .refresh(getRefreshPolicy(itemType));
                            IndexResponse response = esClient.index(indexRequestBuilder.build());
                            String responseIndex = response.index();
                            String itemId = response.id();
                            setMetadata(item, itemId, response.version(), response.seqNo() != null ? response.seqNo() : 0L,
                                    response.primaryTerm() != null ? response.primaryTerm() : 0L, responseIndex);

                            // Special handling for session, in case of new session we check that a rollover happen or not to update the latest available index
                            if (Session.ITEM_TYPE.equals(itemType) && sessionLatestIndex != null && response.result().equals(Result.Created)
                                    && !responseIndex.equals(sessionLatestIndex)) {
                                sessionLatestIndex = responseIndex;
                            }
                        } else {
                            BulkOperation bulkOp;
                            if (opType == OpType.Create) {
                                bulkOp = BulkOperation.of(b -> b.create(
                                        c -> c.index(index).id(documentId).document(item).ifSeqNo(seqNo).ifPrimaryTerm(primaryTerm)
                                                .routing(routing)));
                            } else {
                                bulkOp = BulkOperation.of(b -> b.index(
                                        i -> i.index(index).id(documentId).document(item).ifSeqNo(seqNo).ifPrimaryTerm(primaryTerm)
                                                .routing(routing)));
                            }
                            bulkIngester.add(bulkOp);
                        }
                        logMetadataItemOperation("saved", item);
                    } catch (ElasticsearchException e) {
                        if (e.status() == 404 && e.getMessage() != null && e.getMessage().contains("index_not_found_exception")) {
                            LOGGER.error("Could not find index {}, could not register item type {} with id {} ", index, itemType,
                                    item.getItemId(), e);
                            return false;
                        }
                    }

                    // Add tenants metadata
                    addTenantMetadata(item, finalTenantId);

                    return true;
                } catch (IOException e) {
                    throw new Exception("Error saving item " + item, e);
                }
            }
        }.catchingExecuteInClassLoader(true);
        return Objects.requireNonNullElse(result, false);
    }

    @Override public boolean update(final Item item, final Date dateHint, final Class clazz, final String propertyName,
            final Object propertyValue) {
        return update(item, clazz, propertyName, propertyValue);
    }

    @Override public boolean update(final Item item, final Date dateHint, final Class clazz, final Map source) {
        return update(item, clazz, source);
    }

    @Override public boolean update(final Item item, final Date dateHint, final Class clazz, final Map source,
            final boolean alwaysOverwrite) {
        return update(item, clazz, source, alwaysOverwrite);
    }

    @Override public boolean update(final Item item, final Class clazz, final String propertyName, final Object propertyValue) {
        return update(item, clazz, Collections.singletonMap(propertyName, propertyValue), alwaysOverwrite);
    }

    @Override public boolean update(final Item item, final Class clazz, final Map source) {
        return update(item, clazz, source, alwaysOverwrite);
    }

    @Override
    //TODO type Class and Map
    public boolean update(final Item item, final Class clazz, final Map source, final boolean alwaysOverwrite) {
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".updateItem", this.bundleContext,
                this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws Exception {
                try {
                    handleItemTransformation(item);
                    // On suppose que cette méthode retourne un UpdateRequest<Object>
                    UpdateRequest<Object, ?> updateRequest = createUpdateRequest(clazz, item, source, alwaysOverwrite);

                    if (bulkIngester == null || !useBatchingForUpdate) {
                        UpdateResponse<Object> response = esClient.update(updateRequest, clazz);
                        setMetadata(item, response.id(), response.version(), response.seqNo() != null ? response.seqNo() : 0L,
                                response.primaryTerm() != null ? response.primaryTerm() : 0L, response.index());
                    } else {
                        BulkOperation bulkOp = BulkOperation.of(builder -> builder.update(
                                u -> u.index(updateRequest.index()).id(updateRequest.id()).action(b -> b.doc(updateRequest.doc()))
                                        .ifSeqNo(updateRequest.ifSeqNo()).ifPrimaryTerm(updateRequest.ifPrimaryTerm())
                                        .routing(updateRequest.routing())));
                        bulkIngester.add(bulkOp);
                    }
                    logMetadataItemOperation("updated", item);
                    return true;
                } catch (ElasticsearchException e) {
                    if (e.getMessage().contains("index_not_found_exception")) {
                        throw new Exception("No index found for itemType=" + clazz.getName() + " itemId=" + item.getItemId(), e);
                    }
                    throw e;
                }
            }
        }.catchingExecuteInClassLoader(true);
        return Objects.requireNonNullElse(result, false);
    }

    private UpdateRequest<Object, Object> createUpdateRequest(Class<?> clazz, Item item, Map<String, Object> source,
            boolean alwaysOverwrite) {
        String itemType = Item.getItemType(clazz);
        String documentId = getDocumentIDForItemType(item.getItemId(), itemType);
        String index = item.getSystemMetadata("index") != null ? (String) item.getSystemMetadata("index") : getIndex(itemType);

        UpdateRequest.Builder<Object, Object> builder = new UpdateRequest.Builder<>().index(index).id(documentId).doc(source);

        if (!alwaysOverwrite) {
            Long seqNo = (Long) item.getSystemMetadata(SEQ_NO);
            Long primaryTerm = (Long) item.getSystemMetadata(PRIMARY_TERM);

            if (seqNo != null && primaryTerm != null) {
                builder.ifSeqNo(seqNo);
                builder.ifPrimaryTerm(primaryTerm);
            }
        }

        return builder.build();
    }

    @Override public List<String> update(final Map<Item, Map> items, final Date dateHint, final Class clazz) {
        if (items.isEmpty())
            return new ArrayList<>();

        List<String> result = new InClassLoaderExecute<List<String>>(metricsService, this.getClass().getName() + ".updateItems",
                this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected List<String> execute(Object... args) throws Exception {
                long batchRequestStartTime = System.currentTimeMillis();

                List<BulkOperation> operations = new ArrayList<>();

                items.forEach((item, source) -> {
                    UpdateRequest updateRequest = createUpdateRequest(clazz, item, source, alwaysOverwrite);
                    BulkOperation bulkOp = BulkOperation.of(builder -> builder.update(
                            u -> u.index(updateRequest.index()).id(updateRequest.id()).action(b -> b.doc(updateRequest.doc()))
                                    .ifSeqNo(updateRequest.ifSeqNo()).ifPrimaryTerm(updateRequest.ifPrimaryTerm())
                                    .routing(updateRequest.routing())));
                    operations.add(bulkOp);
                });

                BulkRequest bulkRequest = new BulkRequest.Builder().operations(operations).build();
                BulkResponse bulkResponse = esClient.bulk(bulkRequest);
                LOGGER.debug("{} profiles updated with bulk segment in {}ms", bulkRequest.operations().size(),
                        System.currentTimeMillis() - batchRequestStartTime);

                List<String> failedItemsIds = new ArrayList<>();

                if (bulkResponse.items().stream().anyMatch(item -> item.error() != null)) {
                    bulkResponse.items().forEach(item -> {
                        if (item.error() != null) {
                            failedItemsIds.add(item.id());
                        }
                    });
                }
                return failedItemsIds;
            }
        }.catchingExecuteInClassLoader(true);

        return result;
    }

    @Override public boolean updateWithQueryAndScript(final Date dateHint, final Class<?> clazz, final String[] scripts,
            final Map<String, Object>[] scriptParams, final Condition[] conditions) {
        return updateWithQueryAndScript(clazz, scripts, scriptParams, conditions);
    }

    @Override public boolean updateWithQueryAndScript(final Class<?> clazz, final String[] scripts,
            final Map<String, Object>[] scriptParams, final Condition[] conditions) {
        Script[] builtScripts = new Script[scripts.length];
        for (int i = 0; i < scripts.length; i++) {
            Map<String, JsonData> jsonDataParams = scriptParams[i].entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> JsonData.of(entry.getValue())));
            int finalI = i;
            builtScripts[i] = Script.of(s -> s.lang(ScriptLanguage.Painless)
                    .source(ScriptSource.of(scriptSourceBuilder -> scriptSourceBuilder.scriptString(scripts[finalI])))
                    .params(jsonDataParams));
        }
        return updateWithQueryAndScript(new Class<?>[] { clazz }, builtScripts, conditions, true);
    }

    @Override public boolean updateWithQueryAndStoredScript(Date dateHint, Class<?> clazz, String[] scripts,
            Map<String, Object>[] scriptParams, Condition[] conditions) {
        return updateWithQueryAndStoredScript(new Class<?>[] { clazz }, scripts, scriptParams, conditions, true);
    }

    @Override public boolean updateWithQueryAndStoredScript(Class<?> clazz, String[] scripts, Map<String, Object>[] scriptParams,
            Condition[] conditions) {
        return updateWithQueryAndStoredScript(new Class<?>[] { clazz }, scripts, scriptParams, conditions, true);
    }

    @Override public boolean updateWithQueryAndStoredScript(Class<?>[] classes, String[] scripts, Map<String, Object>[] scriptParams,
            Condition[] conditions, boolean waitForComplete) {
        Script[] builtScripts = new Script[scripts.length];
        for (int i = 0; i < scripts.length; i++) {
            int finalI = i;
            Map<String, JsonData> jsonDataParams = scriptParams[i].entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> JsonData.of(entry.getValue())));
            builtScripts[i] = Script.of(s -> s.id(scripts[finalI]).params(jsonDataParams));
        }
        return updateWithQueryAndScript(classes, builtScripts, conditions, waitForComplete);
    }

    private boolean updateWithQueryAndScript(final Class<?>[] classes, final Script[] scripts, final Condition[] conditions,
            boolean waitForComplete) {
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".updateWithQueryAndScript",
                this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws Exception {
                String[] itemTypes = Arrays.stream(classes).map(Item::getItemType).toArray(String[]::new);
                String[] indices = Arrays.stream(itemTypes).map(itemType -> getIndexNameForQuery(itemType)).toArray(String[]::new);

                try {
                    for (int i = 0; i < scripts.length; i++) {
                        esClient.indices().refresh(r -> r.index(Arrays.asList(indices)));

                        Query query = conditionESQueryBuilderDispatcher.buildFilter(conditions[i]);
                        int finalI = i;

                        UpdateByQueryRequest updateByQueryRequest = UpdateByQueryRequest.of(
                                builder -> builder.index(List.of(indices)).conflicts(Conflicts.Proceed).waitForCompletion(false)
                                        .slices(Slices.of(s -> s.value(2))).script(scripts[finalI])
                                        .query(wrapWithTenantAndItemsTypeQuery(itemTypes, query, getTenantId())));

                        UpdateByQueryResponse response = esClient.updateByQuery(updateByQueryRequest);

                        if (response.task() == null) {
                            LOGGER.error("update with query and script: no response returned for query: {}", query);
                        } else if (waitForComplete) {
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug(
                                        "Waiting task [{}]: [{}] using query: [{}], polling every {}ms with a timeout configured to {}ms",
                                        response.task(), updateByQueryRequest, updateByQueryRequest.query(), taskWaitingPollingInterval,
                                        taskWaitingTimeout);
                            }
                            waitForTaskComplete(response.task());
                        } else {
                            LOGGER.debug("ES task started {}", response.task());
                        }
                    }
                    return true;
                } catch (ElasticsearchException e) {
                    if (e.status() == 404 && e.getMessage() != null && e.getMessage().contains("index_not_found_exception")) {
                        throw new Exception("No index found for itemTypes=" + String.join(",", itemTypes), e);
                    }
                    //TODO check the message
                    LOGGER.error("Error in the update script : {}\n{}", e.response().toString(), e.getMessage(), e);
                    throw new Exception("Error in the update script");
                }
            }
        }.catchingExecuteInClassLoader(true);
        if (result == null) {
            return false;
        } else {
            return result;
        }
    }

    private void waitForTaskComplete(String task) {
        long start = System.currentTimeMillis();
        new InClassLoaderExecute<Void>(metricsService, this.getClass().getName() + ".waitForTask", this.bundleContext,
                this.fatalIllegalStateErrors, throwExceptions) {
            protected Void execute(Object... args) throws Exception {

                while (true) {
                    GetTasksResponse tasksResponse = esClient.tasks().get(GetTasksRequest.of(builder -> builder.taskId(task)));
                    if (tasksResponse != null) {
                        long taskId = tasksResponse.task().id();
                        if (tasksResponse.completed()) {
                            if (LOGGER.isDebugEnabled()) {
                                long millis = tasksResponse.task().runningTimeInNanos() / 1_000_000;
                                long seconds = millis / 1000;
                                LOGGER.debug("Waiting task [{}]: Finished in {} {}", taskId, seconds >= 1 ? seconds : millis,
                                        seconds >= 1 ? "seconds" : "milliseconds");
                            }
                            break;
                        } else {
                            if ((start + taskWaitingTimeout) < System.currentTimeMillis()) {
                                LOGGER.error("Waiting task [{}]: Exceeded configured timeout ({}ms), aborting wait process", taskId,
                                        taskWaitingTimeout);
                                break;
                            }

                            try {
                                Thread.sleep(taskWaitingPollingInterval);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException("Waiting task [" + taskId + "]: interrupted");
                            }
                        }
                    } else {
                        LOGGER.error("Waiting task [{}]: No task found", task);
                        break;
                    }
                }
                return null;
            }
        }.catchingExecuteInClassLoader(true);
    }

    @Override public boolean storeScripts(Map<String, String> scripts) {
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".storeScripts", this.bundleContext,
                this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws Exception {
                boolean executedSuccessfully = true;

                for (Map.Entry<String, String> script : scripts.entrySet()) {
                    try {
                        // Construire la requête avec le nouveau client
                        PutScriptRequest putScriptRequest = PutScriptRequest.of(p -> p.id(script.getKey()).script(StoredScript.of(
                                s -> s.lang("painless").source(ScriptSource.of(builder -> builder.scriptString(script.getValue()))))));

                        // Exécuter la requête
                        PutScriptResponse response = esClient.putScript(putScriptRequest);

                        // Vérifier le résultat
                        boolean acknowledged = response.acknowledged();
                        executedSuccessfully &= acknowledged;

                        if (acknowledged) {
                            LOGGER.info("Successfully stored painless script: {}", script.getKey());
                        } else {
                            LOGGER.error("Failed to store painless script: {}", script.getKey());
                        }
                    } catch (Exception e) {
                        LOGGER.error("Exception while storing painless script: {}", script.getKey(), e);
                        executedSuccessfully = false;
                    }
                }
                return executedSuccessfully;
            }
        }.catchingExecuteInClassLoader(true);
        return Objects.requireNonNullElse(result, false);
    }

    public boolean updateWithScript(final Item item, final Date dateHint, final Class<?> clazz, final String script,
            final Map<String, Object> scriptParams) {
        return updateWithScript(item, clazz, script, scriptParams);
    }

    @Override public boolean updateWithScript(final Item item, final Class<?> clazz, final String script,
            final Map<String, Object> scriptParams) {
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".updateWithScript",
                this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws Exception {
                try {
                    String itemType = Item.getItemType(clazz);
                    String index = getIndex(itemType);
                    String documentId = getDocumentIDForItemType(item.getItemId(), itemType);

                    Map<String, JsonData> jsonDataParams = scriptParams.entrySet().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, entry -> JsonData.of(entry.getValue())));

                    Script actualScript = Script.of(s -> s.lang(ScriptLanguage.Painless)
                            .source(ScriptSource.of(scriptSourceBuilder -> scriptSourceBuilder.scriptString(script)))
                            .params(jsonDataParams));

                    if (bulkIngester != null) {
                        UpdateOperation.Builder updateOperation = new UpdateOperation.Builder<>().index(index).id(documentId)
                                .ifSeqNo((Long) item.getSystemMetadata(SEQ_NO)).ifPrimaryTerm((Long) item.getSystemMetadata(PRIMARY_TERM))
                                .action(UpdateAction.of(action -> action.script(actualScript)));

                        BulkOperation operation = BulkOperation.of(op -> op.update(updateOperation.build()));
                        bulkIngester.add(operation);

                    } else {

                        UpdateRequest updateRequest = new UpdateRequest.Builder<>().index(index).id(documentId)
                                .ifSeqNo((Long) item.getSystemMetadata(SEQ_NO)).ifPrimaryTerm((Long) item.getSystemMetadata(PRIMARY_TERM))
                                .script(actualScript).build();

                        UpdateResponse response = esClient.update(updateRequest, clazz);
                        setMetadata(item, response.id(), response.version(), response.seqNo(), response.primaryTerm(), response.index());
                    }

                    return true;
                } catch (ElasticsearchException e) {
                    if (e.status() == 404 && e.getMessage() != null && e.getMessage().contains("index_not_found_exception")) {
                        throw new Exception("No index found for itemType=" + clazz.getName() + "itemId=" + item.getItemId(), e);
                    }
                    throw new Exception("Error during update with script", e);
                }
            }
        }.catchingExecuteInClassLoader(true);
        return Objects.requireNonNullElse(result, false);
    }

    @Override public <T extends Item> boolean remove(final String itemId, final Class<T> clazz) {
        return remove(itemId, clazz, null);
    }

    @Override public boolean removeCustomItem(final String itemId, final String customItemType) {
        return remove(itemId, CustomItem.class, customItemType);
    }

    private <T extends Item> boolean remove(final String itemId, final Class<T> clazz, String customItemType) {
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".removeItem", this.bundleContext,
                this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws Exception {
                try {
                    String itemType = Item.getItemType(clazz);
                    if (customItemType != null) {
                        itemType = customItemType;
                    }
                    String documentId = getDocumentIDForItemType(itemId, itemType);
                    String index = getIndexNameForQuery(itemType);

                    esClient.delete(DeleteRequest.of(builder -> builder.index(index).id(documentId)));
                    if (MetadataItem.class.isAssignableFrom(clazz)) {
                        LOGGER.info("Item of type {} with ID {} has been removed",
                                customItemType != null ? customItemType : clazz.getSimpleName(), itemId);
                    }
                    return true;
                } catch (Exception e) {
                    throw new Exception("Cannot remove", e);
                }
            }
        }.catchingExecuteInClassLoader(true);
        return Objects.requireNonNullElse(result, false);
    }

    public <T extends Item> boolean removeByQuery(final Condition query, final Class<T> clazz) {
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".removeByQuery", this.bundleContext,
                this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws Exception {
                Query esQuery = conditionESQueryBuilderDispatcher.getQueryBuilder(query);
                return removeByQuery(esQuery, clazz);
            }
        }.catchingExecuteInClassLoader(true);
        return Objects.requireNonNullElse(result, false);
    }

    public <T extends Item> boolean removeByQuery(Query query, final Class<T> clazz) throws Exception {
        try {
            String itemType = Item.getItemType(clazz);
            LOGGER.debug("Remove item of type {} using a query", itemType);
            DeleteByQueryRequest deleteByQueryRequest = DeleteByQueryRequest.of(
                    builder -> builder.index(getIndexNameForQuery(itemType)).conflicts(Conflicts.Proceed)
                            .query(wrapWithTenantAndItemTypeQuery(itemType, query, getTenantId()))
                            .timeout(Time.of(t -> t.time(removeByQueryTimeoutInMinutes + "m"))).waitForCompletion(false));

            DeleteByQueryResponse deleteByQueryResponse = esClient.deleteByQuery(deleteByQueryRequest);

            String task = deleteByQueryResponse.task();
            if (task == null) {
                LOGGER.error("Remove by query: no response returned for query: {}", query);
                return false;
            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Waiting task [{}]: [{}] using query: [{}], polling every {}ms with a timeout configured to {}ms", task,
                        deleteByQueryRequest, deleteByQueryRequest.query(), taskWaitingPollingInterval, taskWaitingTimeout);
            }

            waitForTaskComplete(task);

            return true;
        } catch (Exception e) {
            throw new Exception("Cannot remove by query", e);
        }
    }

    public boolean indexTemplateExists(final String templateName) {
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".indexTemplateExists",
                this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws IOException {
                return esClient.indices().existsIndexTemplate(ExistsIndexTemplateRequest.of(builder -> builder.name(templateName))).value();
            }
        }.catchingExecuteInClassLoader(true);
        return Objects.requireNonNullElse(result, false);
    }

    public boolean removeIndexTemplate(final String templateName) {
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".removeIndexTemplate",
                this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws IOException {
                DeleteIndexTemplateRequest deleteIndexTemplateRequest = DeleteIndexTemplateRequest.of(
                        builder -> builder.name(templateName));
                return esClient.indices().deleteIndexTemplate(deleteIndexTemplateRequest).acknowledged();
            }
        }.catchingExecuteInClassLoader(true);
        return Objects.requireNonNullElse(result, false);
    }

    public void registerRolloverLifecyclePolicy() {
        new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".createLifecyclePolicy", this.bundleContext,
                this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws IOException {

                RolloverAction.Builder rolloverActionBuilder = new RolloverAction.Builder();
                if (StringUtils.isNotEmpty(rolloverMaxAge)) {
                    rolloverActionBuilder.maxAge(new Time.Builder().time(rolloverMaxAge).build());
                }
                if (StringUtils.isNotEmpty(rolloverMaxSize)) {
                    rolloverActionBuilder.maxSize(rolloverMaxSize);
                }
                if (StringUtils.isNotEmpty(rolloverMaxDocs)) {
                    rolloverActionBuilder.maxDocs(Long.parseLong(rolloverMaxDocs));
                }
                RolloverAction rolloverAction = rolloverActionBuilder.build();

                Phase hotPhase = new Phase.Builder().actions(new Actions.Builder().rollover(rolloverAction).build())
                        .minAge(new Time.Builder().time("0ms").build()).build();
                IlmPolicy ilmPolicy = new IlmPolicy.Builder().phases(new Phases.Builder().hot(hotPhase).build()).build();
                PutLifecycleRequest request = new PutLifecycleRequest.Builder().policy(ilmPolicy)
                        .name(indexPrefix + "-" + ROLLOVER_LIFECYCLE_NAME).build();
                PutLifecycleResponse response = esClient.ilm().putLifecycle(request);
                return response.acknowledged();
            }
        }.catchingExecuteInClassLoader(true);
    }

    public boolean createIndex(final String itemType) {
        LOGGER.debug("Create index {}", itemType);
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".createIndex", this.bundleContext,
                this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws IOException {
                String index = getIndex(itemType);
                BooleanResponse indexExists = esClient.indices().exists(ExistsRequest.of(builder -> builder.index(index)));
                if (!indexExists.value()) {
                    if (isItemTypeRollingOver(itemType)) {
                        internalCreateRolloverTemplate(itemType);
                        internalCreateRolloverIndex(index);
                    } else {
                        internalCreateIndex(index, mappings.get(itemType));
                    }
                }
                return !indexExists.value();
            }
        }.catchingExecuteInClassLoader(true);
        return Objects.requireNonNullElse(result, false);
    }

    public boolean removeIndex(final String itemType) {
        String index = getIndex(itemType);

        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".removeIndex", this.bundleContext,
                this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws IOException {
                boolean indexExists = esClient.indices().existsIndexTemplate(ExistsIndexTemplateRequest.of(builder -> builder.name(index)))
                        .value();
                if (indexExists) {
                    esClient.indices().delete(DeleteIndexRequest.of(builder -> builder.index(index)));
                }
                return indexExists;
            }
        }.catchingExecuteInClassLoader(true);
        return Objects.requireNonNullElse(result, false);
    }


    private void internalCreateRolloverTemplate(String itemName) throws IOException {
        if (!mappings.containsKey(itemName)) {
            LOGGER.warn("Couldn't find mapping for item {}, won't create rollover index template", itemName);
            return;
        }

        String rolloverAlias = buildRolloverAlias(itemName);
        String templateName = rolloverAlias + "-rollover-template";
        IndexSettingsAnalysis analysis = buildAnalysis();
        IndexSettings indexSettings = buildIndexSettings(rolloverAlias, analysis);
        IndexTemplateMapping templateMapping = buildTemplateMapping(itemName, indexSettings);

        PutIndexTemplateRequest request = PutIndexTemplateRequest.of(builder -> builder.name(templateName)
                .indexPatterns(Collections.singletonList(getRolloverIndexForQuery(itemName))).template(templateMapping).priority(1L));

        PutIndexTemplateResponse response = esClient.indices().putIndexTemplate(request);
        if (!response.acknowledged()) {
            throw new IOException("Failed to create index template " + templateName + " - not acknowledged");
        }

        // Verify template exists before proceeding - this ensures template is available for index creation
        int retries = 10;
        while (retries > 0) {
            boolean templateExists = esClient.indices().existsIndexTemplate(
                    ExistsIndexTemplateRequest.of(builder -> builder.name(templateName))).value();
            if (templateExists) {
                LOGGER.debug("Index template {} is now available", templateName);
                break;
            }
            retries--;
            if (retries > 0) {
                try {
                    Thread.sleep(100); // Wait 100ms before retrying
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for template " + templateName, e);
                }
            }
        }
        if (retries == 0) {
            throw new IOException("Index template " + templateName + " was not available after creation");
        }
    }

    private String buildRolloverAlias(String itemName) {
        return indexPrefix + "-" + itemName;
    }

    private IndexSettingsAnalysis buildAnalysis() {
        return IndexSettingsAnalysis.of(an -> an.analyzer("folding", analyserBuilder -> analyserBuilder.custom(
                CustomAnalyzer.of(customAnalyzer -> customAnalyzer.tokenizer("keyword").filter("lowercase", "asciifolding")))));
    }

    private IndexSettings buildIndexSettings(String rolloverAlias, IndexSettingsAnalysis analysis) {
        return IndexSettings.of(builder -> builder.index(
                indexBuilder -> indexBuilder.numberOfShards(rolloverIndexNumberOfShards).numberOfReplicas(rolloverIndexNumberOfReplicas)
                        .mapping(MappingLimitSettings.of(limitBuilder -> limitBuilder.totalFields(MappingLimitSettingsTotalFields.of(
                                totalFieldLimitBuilder -> totalFieldLimitBuilder.limit(rolloverIndexMappingTotalFieldsLimit)))))
                        .maxDocvalueFieldsSearch(Integer.valueOf(rolloverIndexMaxDocValueFieldsSearch)).lifecycle(
                                lifecycleBuilder -> lifecycleBuilder.name(indexPrefix + "-" + ROLLOVER_LIFECYCLE_NAME)
                                        .rolloverAlias(rolloverAlias))).analysis(analysis));
    }

    private IndexTemplateMapping buildTemplateMapping(String itemName, IndexSettings indexSettings) {
        return IndexTemplateMapping.of(templateMappingBuilder -> templateMappingBuilder.settings(indexSettings).mappings(
                mappingsBuilder -> mappingsBuilder.withJson(
                        new ByteArrayInputStream(mappings.get(itemName).getBytes(StandardCharsets.UTF_8)))));
    }

    private void internalCreateRolloverIndex(String indexName) throws IOException {
        String fullIndexName = indexName + "-000001";
        
        // Retry mechanism to ensure template is actually applied, not just that it exists
        // In fast-paced environments (8GB heap), cluster state may not be fully synchronized
        // even though template exists in metadata. We verify by checking index settings after creation.
        int maxRetries = 3;
        int retryCount = 0;
        long delayMs = 200;
        
        while (retryCount < maxRetries) {
            // Wait for cluster state to be ready
            esClient.cluster().health(builder -> builder.waitForStatus(HealthStatus.Green).timeout(t -> t.time("5s")));
            
            // Delay to allow cluster state to synchronize - increase delay on each retry
            try {
                Thread.sleep(delayMs * (retryCount + 1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for template propagation", e);
            }
            
            // Delete index if this is a retry (from previous failed attempt)
            if (retryCount > 0) {
                try {
                    BooleanResponse exists = esClient.indices().exists(ExistsRequest.of(builder -> builder.index(fullIndexName)));
                    if (exists.value()) {
                        esClient.indices().delete(DeleteIndexRequest.of(builder -> builder.index(fullIndexName)));
                        LOGGER.debug("Deleted index {} before retry {}", fullIndexName, retryCount);
                    }
                } catch (IOException e) {
                    LOGGER.warn("Failed to delete index {} before retry: {}", fullIndexName, e.getMessage());
                }
            }
            
            // Create index
            CreateIndexResponse createIndexResponse = esClient.indices().create(CreateIndexRequest.of(
                    builder -> builder.index(fullIndexName)
                            .aliases(indexName, Alias.of(aliasBuilder -> aliasBuilder.isWriteIndex(true)))));
            LOGGER.info("Index created: [{}], acknowledge: [{}], shards acknowledge: [{}]", createIndexResponse.index(),
                    createIndexResponse.acknowledged(), createIndexResponse.shardsAcknowledged());
            
            // Verify template was applied by checking for template-specific settings:
            // 1. Folding analyzer in analysis settings
            // 2. Dynamic templates in mappings
            // These are the key features we need from the template
            GetIndicesSettingsResponse settingsResponse = esClient.indices().getSettings(
                    GetIndicesSettingsRequest.of(builder -> builder.index(fullIndexName)));
            GetMappingResponse mappingResponse = esClient.indices().getMapping(
                    GetMappingRequest.of(builder -> builder.index(fullIndexName)));
            
            var indexSettings = settingsResponse.get(fullIndexName);
            var indexMapping = mappingResponse.get(fullIndexName);
            
            if (indexSettings == null || indexSettings.settings() == null || 
                indexSettings.settings().index() == null) {
                LOGGER.warn("Could not retrieve index settings for {} to verify template application. Retrying...", fullIndexName);
                retryCount++;
                if (retryCount < maxRetries) {
                    continue;
                } else {
                    throw new IOException("Could not retrieve index settings for " + fullIndexName + " after " + maxRetries + " attempts");
                }
            }
            
            if (indexMapping == null || indexMapping.mappings() == null) {
                LOGGER.warn("Could not retrieve index mappings for {} to verify template application. Retrying...", fullIndexName);
                retryCount++;
                if (retryCount < maxRetries) {
                    continue;
                } else {
                    throw new IOException("Could not retrieve index mappings for " + fullIndexName + " after " + maxRetries + " attempts");
                }
            }
            
            // Check for folding analyzer in analysis settings
            boolean hasFoldingAnalyzer = false;
            var analysis = indexSettings.settings().index().analysis();
            if (analysis != null && analysis.analyzer() != null) {
                var analyzer = analysis.analyzer().get("folding");
                if (analyzer != null) {
                    hasFoldingAnalyzer = true;
                }
            }
            
            // Check for dynamic templates in mappings
            boolean hasDynamicTemplates = false;
            var dynamicTemplates = indexMapping.mappings().dynamicTemplates();
            if (dynamicTemplates != null && !dynamicTemplates.isEmpty()) {
                hasDynamicTemplates = true;
            }
            
            if (hasFoldingAnalyzer && hasDynamicTemplates) {
                // Template was applied successfully
                LOGGER.debug("Template successfully applied to index {} - folding analyzer and dynamic templates present", fullIndexName);
                return;
            } else {
                // Template was not applied - will retry
                LOGGER.warn("Template not applied to index {} - folding analyzer: {}, dynamic templates: {}. Retrying...", 
                        fullIndexName, hasFoldingAnalyzer, hasDynamicTemplates);
                retryCount++;
                if (retryCount < maxRetries) {
                    continue;
                } else {
                    throw new IOException("Template was not applied to index " + fullIndexName + 
                            " after " + maxRetries + " attempts. Folding analyzer: " + hasFoldingAnalyzer + 
                            ", Dynamic templates: " + hasDynamicTemplates);
                }
            }
        }
    }

    private void internalCreateIndex(String indexName, String mappingSource) throws IOException {
        IndexSettings indexSettings = IndexSettings.of(builder -> builder.numberOfShards(numberOfShards).numberOfReplicas(numberOfReplicas)
                .mapping(MappingLimitSettings.of(limitBuilder -> limitBuilder.totalFields(MappingLimitSettingsTotalFields.of(
                        totalFieldLimitBuilder -> totalFieldLimitBuilder.limit(indexMappingTotalFieldsLimit)))))
                .maxDocvalueFieldsSearch(Integer.valueOf(indexMaxDocValueFieldsSearch)).analysis(buildAnalysis()));

        CreateIndexRequest.Builder createIndexRequestBuilder = new CreateIndexRequest.Builder();
        createIndexRequestBuilder.index(indexName).settings(indexSettings);
        if (mappingSource != null) {
            createIndexRequestBuilder.mappings(
                    mappingsBuilder -> mappingsBuilder.withJson(new ByteArrayInputStream(mappingSource.getBytes(StandardCharsets.UTF_8))));
        }
        CreateIndexResponse createIndexResponse = esClient.indices().create(createIndexRequestBuilder.build());

        LOGGER.info("Index created: [{}], acknowledge: [{}], shards acknowledge: [{}]", createIndexResponse.index(),
                createIndexResponse.acknowledged(), createIndexResponse.shardsAcknowledged());
    }

    @Override public void createMapping(String type, String source) {
        try {
            putMapping(source, getIndex(type));
        } catch (IOException ioe) {
            LOGGER.error("Error while creating mapping for type {} and source {}", type, source, ioe);
        }
    }

    public void setPropertyMapping(final PropertyType property, final String itemType) {
        try {
            Map<String, Map<String, Object>> mappings = getPropertiesMapping(itemType);
            if (mappings == null) {
                mappings = new HashMap<>();
            }
            Map<String, Object> subMappings = mappings.computeIfAbsent("properties", k -> new HashMap<>());
            Map<String, Object> subSubMappings = (Map<String, Object>) subMappings.computeIfAbsent("properties", k -> new HashMap<>());

            if (subSubMappings.containsKey(property.getItemId())) {
                LOGGER.warn("Mapping already exists for type {} and property {}", itemType, property.getItemId());
                return;
            }

            Map<String, Object> propertyMapping = createPropertyMapping(property);
            if (propertyMapping.isEmpty()) {
                return;
            }

            mergePropertiesMapping(subSubMappings, propertyMapping);

            Map<String, Object> mappingsWrapper = new HashMap<>();
            mappingsWrapper.put("properties", mappings);
            final String mappingsSource = ESCustomObjectMapper.getObjectMapper().writeValueAsString(mappingsWrapper);

            putMapping(mappingsSource, getIndex(itemType));
        } catch (IOException ioe) {
            LOGGER.error("Error while creating mapping for type {} and property {}", itemType, property.getValueTypeId(), ioe);
        }
    }

    private Map<String, Object> createPropertyMapping(final PropertyType property) {
        final String esType = convertValueTypeToESType(property.getValueTypeId());
        final HashMap<String, Object> definition = new HashMap<>();

        if (esType == null) {
            LOGGER.warn("No predefined type found for property[{}], no mapping will be created", property.getValueTypeId());
            return Collections.emptyMap();
        } else {
            definition.put("type", esType);
            if ("text".equals(esType)) {
                definition.put("analyzer", "folding");
                final Map<String, Object> fields = new HashMap<>();
                final Map<String, Object> keywordField = new HashMap<>();
                keywordField.put("type", "keyword");
                keywordField.put("ignore_above", 256);
                fields.put("keyword", keywordField);
                definition.put("fields", fields);
            }
        }

        if ("set".equals(property.getValueTypeId())) {
            Map<String, Object> childProperties = new HashMap<>();
            property.getChildPropertyTypes().forEach(childType -> {
                Map<String, Object> propertyMapping = createPropertyMapping(childType);
                if (!propertyMapping.isEmpty()) {
                    mergePropertiesMapping(childProperties, propertyMapping);
                }
            });
            definition.put("properties", childProperties);
        }

        return Collections.singletonMap(property.getItemId(), definition);
    }

    private String convertValueTypeToESType(String valueTypeId) {
        switch (valueTypeId) {
            case "set":
            case "json":
                return "object";
            case "boolean":
                return "boolean";
            case "geopoint":
                return "geo_point";
            case "integer":
                return "integer";
            case "long":
                return "long";
            case "float":
                return "float";
            case "date":
                return "date";
            case "string":
            case "id":
            case "email": // TODO Consider supporting email mapping in ES, right now will be map to text to avoid warning in logs
                return "text";
            default:
                return null;
        }
    }

    private void putMapping(final String source, final String indexName) throws IOException {
        new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".putMapping", this.bundleContext,
                this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws Exception {
                try {
                    PutMappingResponse putMappingResponse = esClient.indices().putMapping(PutMappingRequest.of(
                            builder -> builder.index(indexName)
                                    .withJson(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)))));
                    return putMappingResponse.acknowledged();
                } catch (Exception e) {
                    throw new Exception("Cannot create/update mapping", e);
                }
            }
        }.catchingExecuteInClassLoader(true);
    }

    @Override public Map<String, Map<String, Object>> getPropertiesMapping(final String itemType) {
        return new InClassLoaderExecute<Map<String, Map<String, Object>>>(metricsService,
                this.getClass().getName() + ".getPropertiesMapping", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            @SuppressWarnings("unchecked") protected Map<String, Map<String, Object>> execute(Object... args) throws Exception {
                // Get all mapping for current itemType
                GetMappingRequest getMappingsRequest = GetMappingRequest.of(r -> r.index(getIndexNameForQuery(itemType)));
                GetMappingResponse getMappingsResponse = esClient.indices().getMapping(getMappingsRequest);
                Map<String, IndexMappingRecord> mappings = getMappingsResponse.mappings();

                // create a list of Keys to get the mappings in chronological order
                Set<String> orderedKeys = new TreeSet<>(mappings.keySet());
                Map<String, Map<String, Object>> result = new HashMap<>();
                try {
                    for (String key : orderedKeys) {
                        if (mappings.containsKey(key)) {
                            TypeMapping typeMapping = mappings.get(key).mappings();
                            if (typeMapping == null || typeMapping.properties() == null)
                                continue;
                            Map<String, Property> properties = typeMapping.properties();
                            // Convert Property to Map<String, Object>
                            Map<String, Map<String, Object>> propertiesMap = new HashMap<>();
                            for (Map.Entry<String, Property> entry : properties.entrySet()) {
                                propertiesMap.put(entry.getKey(), propertyToMap(entry.getValue()));
                            }

                            for (Map.Entry<String, Map<String, Object>> entry : propertiesMap.entrySet()) {
                                if (result.containsKey(entry.getKey())) {
                                    Map<String, Object> subResult = result.get(entry.getKey());
                                    for (Map.Entry<String, Object> subentry : entry.getValue().entrySet()) {
                                        if (subResult.containsKey(subentry.getKey()) && subResult.get(subentry.getKey()) instanceof Map
                                                && subentry.getValue() instanceof Map) {
                                            mergePropertiesMapping((Map) subResult.get(subentry.getKey()), (Map) subentry.getValue());
                                        } else {
                                            subResult.put(subentry.getKey(), subentry.getValue());
                                        }
                                    }
                                } else {
                                    result.put(entry.getKey(), entry.getValue());
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    throw new Exception("Cannot get mapping for itemType=" + itemType, t);
                }
                return result;
            }
        }.catchingExecuteInClassLoader(true);
    }

    /**
     * Converts a Property into a generic Map<String, Object>
     * to maintain compatibility with the old code using getSourceAsMap().
     */
    @SuppressWarnings("unchecked") private Map<String, Object> propertyToMap(Property property) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            JsonpMapper mapper = esClient._transport().jsonpMapper();
            JsonGenerator generator = mapper.jsonProvider().createGenerator(baos);
            mapper.serialize(property, generator);
            generator.close();

            String json = baos.toString(StandardCharsets.UTF_8);
            ObjectMapper jackson = new ObjectMapper();
            return jackson.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private void mergePropertiesMapping(Map<String, Object> result, Map<String, Object> entry) {
        if (entry == null || entry.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> subentry : entry.entrySet()) {
            if (result.containsKey(subentry.getKey()) && result.get(subentry.getKey()) instanceof Map
                    && subentry.getValue() instanceof Map) {
                mergePropertiesMapping((Map) result.get(subentry.getKey()), (Map) subentry.getValue());
            } else {
                result.put(subentry.getKey(), subentry.getValue());
            }
        }
    }

    public Map<String, Object> getPropertyMapping(String property, String itemType) {
        Map<String, Map<String, Object>> mappings = knownMappings.get(itemType);
        Map<String, Object> result = getPropertyMapping(property, mappings);
        if (result == null) {
            mappings = getPropertiesMapping(itemType);
            knownMappings.put(itemType, mappings);
            result = getPropertyMapping(property, mappings);
        }
        return result;
    }

    private Map<String, Object> getPropertyMapping(String property, Map<String, Map<String, Object>> mappings) {
        Map<String, Object> propMapping = null;
        String[] properties = StringUtils.split(property, '.');
        for (int i = 0; i < properties.length && mappings != null; i++) {
            String s = properties[i];
            propMapping = mappings.get(s);
            if (i == properties.length - 1) {
                return propMapping;
            } else {
                mappings = propMapping != null ? ((Map<String, Map<String, Object>>) propMapping.get("properties")) : null;
            }
        }
        return null;
    }

    private String getPropertyNameWithData(String name, String itemType) {
        Map<String, Object> propertyMapping = getPropertyMapping(name, itemType);
        if (propertyMapping == null) {
            return null;
        }
        if ("text".equals(propertyMapping.get("type")) && propertyMapping.containsKey("fields") && ((Map) propertyMapping.get(
                "fields")).containsKey("keyword")) {
            name += ".keyword";
        }
        return name;
    }

    @Override public boolean isValidCondition(Condition condition, Item item) {
        try {
            conditionEvaluatorDispatcher.eval(condition, item);
            Query.of(q -> q.bool(builder -> builder.must(mustBuilder -> mustBuilder.ids(IdsQuery.of(ids -> ids.values(item.getItemId()))))
                    .must(conditionESQueryBuilderDispatcher.buildFilter(condition))));
        } catch (Exception e) {
            LOGGER.error("Failed to validate condition. See debug log level for more information");
            LOGGER.debug("Failed to validate condition, condition={}", condition, e);
            return false;
        }
        return true;
    }

    @Override public boolean testMatch(Condition query, Item item) {
        long startTime = System.currentTimeMillis();
        try {
            return conditionEvaluatorDispatcher.eval(query, item);
        } catch (UnsupportedOperationException e) {
            LOGGER.error("Eval not supported for query {}, attempting to continue with query builder", query, e);
        } finally {
            if (metricsService != null && metricsService.isActivated()) {
                metricsService.updateTimer(this.getClass().getName() + ".testMatchLocally", startTime);
            }
        }
        startTime = System.currentTimeMillis();
        try {
            final Class<? extends Item> clazz = item.getClass();
            String itemType = Item.getItemType(clazz);
            String documentId = getDocumentIDForItemType(item.getItemId(), itemType);

            Query esQuery = Query.of(q -> q.bool(
                    builder -> builder.must(mustBuilder -> mustBuilder.ids(IdsQuery.of(ids -> ids.values(documentId))))
                            .must(conditionESQueryBuilderDispatcher.buildFilter(query))));
            return queryCount(esQuery, itemType) > 0;
        } finally {
            if (metricsService != null && metricsService.isActivated()) {
                metricsService.updateTimer(this.getClass().getName() + ".testMatchInElasticSearch", startTime);
            }
        }
    }

    @Override public <T extends Item> List<T> query(final Condition query, String sortBy, final Class<T> clazz) {
        return query(query, sortBy, clazz, 0, -1).getList();
    }

    @Override public <T extends Item> PartialList<T> query(final Condition query, String sortBy, final Class<T> clazz, final int offset,
            final int size) {
        return query(conditionESQueryBuilderDispatcher.getQueryBuilder(query), sortBy, clazz, offset, size, null, null);
    }

    @Override public <T extends Item> PartialList<T> query(final Condition query, String sortBy, final Class<T> clazz, final int offset,
            final int size, final String scrollTimeValidity) {
        return query(conditionESQueryBuilderDispatcher.getQueryBuilder(query), sortBy, clazz, offset, size, null, scrollTimeValidity);
    }

    @Override public PartialList<CustomItem> queryCustomItem(final Condition query, String sortBy, final String customItemType,
            final int offset, final int size, final String scrollTimeValidity) {
        return query(conditionESQueryBuilderDispatcher.getQueryBuilder(query), sortBy, customItemType, offset, size, null,
                scrollTimeValidity);
    }

    @Override public <T extends Item> PartialList<T> queryFullText(final String fulltext, final Condition query, String sortBy,
            final Class<T> clazz, final int offset, final int size) {
        return query(Query.of(builder -> builder.bool(
                boolBuilder -> boolBuilder.must(mustBuilder -> mustBuilder.queryString(qsBuilder -> qsBuilder.query(fulltext)))
                        .filter(conditionESQueryBuilderDispatcher.getQueryBuilder(query)))), sortBy, clazz, offset, size, null, null);
    }

    @Override
    public <T extends Item> PartialList<T> rangeQuery(String fieldName, String from, String to, String sortBy, Class<T> clazz, int offset, int size) {
        return query(Query.of(q->q.range(r->r.untyped(v -> v.field(fieldName).gte(JsonData.of(from)).lt(JsonData.of(to))))), sortBy, clazz, offset, size, null, null);
    }

    @Override public <T extends Item> List<T> query(final String fieldName, final String fieldValue, String sortBy, final Class<T> clazz) {
        return query(fieldName, fieldValue, sortBy, clazz, 0, -1).getList();
    }

    @Override public <T extends Item> List<T> query(final String fieldName, final String[] fieldValues, String sortBy,
            final Class<T> clazz) {
        Query termQuery = Query.of(builder -> builder.terms(t -> t.field(fieldName).terms(TermsQueryField.of(
                termsBuilder -> termsBuilder.value(
                        Arrays.stream(fieldValues).map(fieldValue -> FieldValue.of(ConditionContextHelper.foldToASCII(fieldValue))).collect(Collectors.toUnmodifiableList()))))));
        return query(termQuery, sortBy, clazz, 0, -1, getRouting(fieldName, fieldValues, clazz), null).getList();
    }

    @Override public <T extends Item> PartialList<T> query(String fieldName, String fieldValue, String sortBy, Class<T> clazz, int offset,
            int size) {

        Query termQuery = Query.of(builder -> builder.terms(t -> t.field(fieldName).terms(TermsQueryField.of(
                termsBuilder -> termsBuilder.value(List.of(FieldValue.of(ConditionContextHelper.foldToASCII(fieldValue))))))));
        return query(termQuery, sortBy, clazz, offset, size, getRouting(fieldName, new String[] { fieldValue }, clazz), null);
    }

    @Override public <T extends Item> PartialList<T> queryFullText(String fieldName, String fieldValue, String fulltext, String sortBy,
            Class<T> clazz, int offset, int size) {
        Query query = Query.of(q -> q.bool(b -> b.must(Query.of(qs -> qs.queryString(qsq -> qsq.query(fulltext))))
                .must(Query.of(t -> t.term(term -> term.field(fieldName).value(fieldValue))))));
        return query(query, sortBy, clazz, offset, size, getRouting(fieldName, new String[] { fieldValue }, clazz), null);
    }

    @Override public <T extends Item> PartialList<T> queryFullText(String fulltext, String sortBy, Class<T> clazz, int offset, int size) {
        return query(Query.of(q -> q.queryString(qs -> qs.query(fulltext))), sortBy, clazz, offset, size, null, null);
    }

    @Override public long queryCount(Condition query, String itemType) {
        try {
            return conditionESQueryBuilderDispatcher.count(query);
        } catch (UnsupportedOperationException e) {
            try {
                Query filter = conditionESQueryBuilderDispatcher.buildFilter(query);

                if (filter.isIds()) {
                    return filter.ids().values().size();
                }
                return queryCount(filter, itemType);
            } catch (UnsupportedOperationException e1) {
                return -1;
            }
        }
    }

    private long queryCount(final Query query, final String itemType) {
        return new InClassLoaderExecute<Long>(metricsService, this.getClass().getName() + ".queryCount", this.bundleContext,
                this.fatalIllegalStateErrors, throwExceptions) {

            @Override protected Long execute(Object... args) throws IOException {
                CountRequest countRequest = CountRequest.of(
                        builder -> builder.index(getIndexNameForQuery(itemType)).query(wrapWithTenantAndItemTypeQuery(itemType, query, getTenantId())));
                return esClient.count(countRequest).count();
            }
        }.catchingExecuteInClassLoader(true);
    }

    private <T extends Item> PartialList<T> query(final Query query, final String sortBy, final Class<T> clazz, final int offset,
            final int size, final String[] routing, final String scrollTimeValidity) {
        return query(query, sortBy, clazz, null, offset, size, routing, scrollTimeValidity);
    }

    private PartialList<CustomItem> query(final Query query, final String sortBy, final String customItemType, final int offset,
            final int size, final String[] routing, final String scrollTimeValidity) {
        return query(query, sortBy, CustomItem.class, customItemType, offset, size, routing, scrollTimeValidity);
    }

    private <T extends Item> PartialList<T> query(final Query query, final String sortBy, final Class<T> clazz, final String customItemType,
            final int offset, final int size, final String[] routing, final String scrollTimeValidity) {
        return new InClassLoaderExecute<PartialList<T>>(metricsService, this.getClass().getName() + ".query", this.bundleContext,
                this.fatalIllegalStateErrors, throwExceptions) {

            @Override protected PartialList<T> execute(Object... args) throws Exception {
                List<T> results = new ArrayList<>();
                String scrollIdentifier = null;
                long totalHits = 0;
                PartialList.Relation totalHitsRelation = PartialList.Relation.EQUAL;
                try {
                    String itemType = customItemType != null ? customItemType : Item.getItemType(clazz);
                    int limit = size < 0 ? defaultQueryLimit : size;

                    SearchRequest.Builder searchRequest = new SearchRequest.Builder();
                    searchRequest.index(getIndexNameForQuery(itemType)).from(offset).size(limit)
                            .query(wrapWithTenantAndItemTypeQuery(itemType, query, getTenantId())).seqNoPrimaryTerm(true).source(src -> src.fetch(true));

                    Time keepAlive = Time.of(t -> t.time("1h"));

                    if (scrollTimeValidity != null) {
                        keepAlive = Time.of(t -> t.time(scrollTimeValidity.isBlank() ? "1h" : scrollTimeValidity));
                        searchRequest.scroll(keepAlive);
                    }

                    if (size == Integer.MIN_VALUE) {
                        searchRequest.size(defaultQueryLimit);
                    } else if (size != -1) {
                        searchRequest.size(size);
                    } else {
                        // size == -1, use scroll query to retrieve all the results
                        searchRequest.scroll(keepAlive);
                    }
                    if (routing != null) {
                        searchRequest.routing(String.join(",", routing));
                    }
                    if (sortBy != null) {
                        String[] sortByArray = sortBy.split(",");
                        for (String sortByElement : sortByArray) {
                            if (sortByElement.startsWith("geo:")) {
                                String[] elements = sortByElement.split(":");
                                GeoLocation location = GeoLocation.of(g -> g.latlon(
                                        latlon -> latlon.lat(Double.parseDouble(elements[2])).lon(Double.parseDouble(elements[3]))));

                                SortOrder order = (elements.length > 4 && "desc".equals(elements[4])) ? SortOrder.Desc : SortOrder.Asc;

                                GeoDistanceSort geoSort = GeoDistanceSort.of(g -> g.field(elements[1]).location(location)
                                        .unit(co.elastic.clients.elasticsearch._types.DistanceUnit.Kilometers).order(order));
                                searchRequest.sort(s -> s.geoDistance(geoSort));
                            } else {
                                String name = getPropertyNameWithData(StringUtils.substringBeforeLast(sortByElement, ":"), itemType);
                                if (name != null) {

                                    SortOrder sortOrder = sortByElement.endsWith(":desc") ? SortOrder.Desc : SortOrder.Asc;
                                    searchRequest.sort(s -> s.field(f -> f.field(name).order(sortOrder)));
                                }
                            }
                        }
                    }
                    searchRequest.version(true);
                    SearchResponse<T> response = esClient.search(searchRequest.build(), clazz);
                    if (size == -1) {
                        List<Hit<T>> hits = response.hits().hits();
                        String scrollId = response.scrollId();
                        // Scroll until no more hits are returned
                        while (!hits.isEmpty()) {
                            for (Hit<T> hit : hits) {
                                T value = hit.source();
                                setMetadata(value, hit.id(), hit.version(), hit.seqNo(), hit.primaryTerm(), hit.index());
                                results.add(handleItemReverseTransformation(value));
                            }

                            ScrollRequest scrollRequest = new ScrollRequest.Builder().scrollId(scrollId).scroll(keepAlive).build();

                            ScrollResponse<T> scrollResponse = esClient.scroll(scrollRequest, clazz);
                            hits = scrollResponse.hits().hits();
                            scrollId = scrollResponse.scrollId();
                        }

                        esClient.clearScroll(new ClearScrollRequest.Builder().scrollId(response.scrollId()).build());
                    } else {
                        totalHits = response.hits().total() != null ? response.hits().total().value() : 0;
                        totalHitsRelation = getTotalHitsRelation(response.hits().total());
                        scrollIdentifier = response.scrollId();
                        if (scrollIdentifier != null && totalHits == 0) {
                            ClearScrollRequest clearScrollRequest = new ClearScrollRequest.Builder().scrollId(scrollIdentifier).build();
                            esClient.clearScroll(clearScrollRequest);
                        }

                        for (Hit<T> hit : response.hits().hits()) {
                            T value = hit.source();
                            setMetadata(value, hit.id(), hit.version() != null ? hit.version() : 0L, hit.seqNo() != null ? hit.seqNo() : 0L,
                                    hit.primaryTerm() != null ? hit.primaryTerm() : 0L, hit.index());
                            results.add(handleItemReverseTransformation(value));
                        }
                    }
                } catch (Exception t) {
                    throw new Exception("Error loading itemType=" + clazz.getName() + " query=" + query + " sortBy=" + sortBy, t);
                }

                PartialList<T> result = new PartialList<>(results, offset, size, totalHits, totalHitsRelation);
                if (scrollIdentifier != null && totalHits != 0) {
                    result.setScrollIdentifier(scrollIdentifier);
                    result.setScrollTimeValidity(scrollTimeValidity);
                }
                return result;
            }
        }.catchingExecuteInClassLoader(true);
    }

    private PartialList.Relation getTotalHitsRelation(TotalHits totalHits) {
        return TotalHitsRelation.Gte.equals(totalHits.relation()) ?
                PartialList.Relation.GREATER_THAN_OR_EQUAL_TO :
                PartialList.Relation.EQUAL;
    }

    @Override public <T extends Item> PartialList<T> continueScrollQuery(final Class<T> clazz, final String scrollIdentifier,
            final String scrollTimeValidity) {
        String finalTenantId = validateTenantAndGetId(SecurityServiceConfiguration.PERMISSION_SCROLL_QUERY);
        return new InClassLoaderExecute<PartialList<T>>(metricsService, this.getClass().getName() + ".continueScrollQuery",
                this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {

            @Override protected PartialList<T> execute(Object... args) throws Exception {
                List<T> results = new ArrayList<>();
                long totalHits = 0;
                try {
                    Time keepAlive = Time.of(t -> t.time(scrollTimeValidity.isBlank() ? "10m" : scrollTimeValidity));

                    ScrollRequest scrollRequest = new ScrollRequest.Builder().scrollId(scrollIdentifier).scroll(keepAlive).build();

                    ScrollResponse<T> scrollResponse = esClient.scroll(scrollRequest, clazz);

                    if (scrollResponse.hits().hits().isEmpty()) {
                        ClearScrollRequest clearScrollRequest = new ClearScrollRequest.Builder().scrollId(scrollResponse.scrollId())
                                .build();
                        esClient.clearScroll(clearScrollRequest);
                    } else {
                        for (Hit<T> hit : scrollResponse.hits().hits()) {
                            T value = hit.source();
                            // add hit to results
                            String sourceTenantId = (String) value.getTenantId();
                            if (finalTenantId.equals(sourceTenantId)) {
                                setMetadata(value, hit.id(), hit.version() != null ? hit.version() : 0L, hit.seqNo() != null ? hit.seqNo() : 0L,
                                        hit.primaryTerm() != null ? hit.primaryTerm() : 0L, hit.index());
                                results.add(handleItemReverseTransformation(value));
                            }
                        }
                    }
                    if (scrollResponse.hits().total() != null) {
                        totalHits = scrollResponse.hits().total().value();
                    }
                    PartialList<T> result = new PartialList<T>(results, 0, scrollResponse.hits().hits().size(), totalHits,
                            getTotalHitsRelation(scrollResponse.hits().total()));
                    if (scrollIdentifier != null) {
                        result.setScrollIdentifier(scrollIdentifier);
                        result.setScrollTimeValidity(scrollTimeValidity);
                    }
                    return result;
                } catch (Exception t) {
                    throw new Exception(
                            "Error continuing scrolling query for itemType=" + clazz.getName() + " scrollIdentifier=" + scrollIdentifier
                                    + " scrollTimeValidity=" + scrollTimeValidity, t);
                }
            }
        }.catchingExecuteInClassLoader(true);
    }

    @Override public PartialList<CustomItem> continueCustomItemScrollQuery(final String customItemType, final String scrollIdentifier,
            final String scrollTimeValidity) {
        String finalTenantId = validateTenantAndGetId(SecurityServiceConfiguration.PERMISSION_SCROLL_QUERY);
        return new InClassLoaderExecute<PartialList<CustomItem>>(metricsService, this.getClass().getName() + ".continueScrollQuery",
                this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {

            @Override protected PartialList<CustomItem> execute(Object... args) throws Exception {
                List<CustomItem> results = new ArrayList<CustomItem>();
                long totalHits = 0;
                try {

                    Time keepAlive = Time.of(t -> t.time(scrollTimeValidity.isBlank() ? "10m" : scrollTimeValidity));

                    ScrollRequest scrollRequest = new ScrollRequest.Builder().scrollId(scrollIdentifier).scroll(keepAlive).build();

                    ScrollResponse<CustomItem> scrollResponse = esClient.scroll(scrollRequest, CustomItem.class);

                    if (scrollResponse.hits().hits().isEmpty()) {
                        ClearScrollRequest clearScrollRequest = new ClearScrollRequest.Builder().scrollId(scrollResponse.scrollId())
                                .build();
                        esClient.clearScroll(clearScrollRequest);
                    } else {
                        // Validate tenants for each result
                        for (Hit<CustomItem> hit : scrollResponse.hits().hits()) {
                            CustomItem value = hit.source();
                            String sourceTenantId = (String) value.getTenantId();
                            if (finalTenantId.equals(sourceTenantId)) {
                                // add hit to results
                                setMetadata(value, hit.id(), hit.version() != null ? hit.version() : 0L, hit.seqNo() != null ? hit.seqNo() : 0L,
                                        hit.primaryTerm() != null ? hit.primaryTerm() : 0L, hit.index());
                                results.add(handleItemReverseTransformation(value));
                            }
                        }
                    }

                    PartialList<CustomItem> result = new PartialList<CustomItem>(results, 0, scrollResponse.hits().hits().size(), totalHits,
                            getTotalHitsRelation(scrollResponse.hits().total()));
                    if (scrollIdentifier != null) {
                        result.setScrollIdentifier(scrollIdentifier);
                        result.setScrollTimeValidity(scrollTimeValidity);
                    }
                    return result;
                } catch (Exception t) {
                    throw new Exception(
                            "Error continuing scrolling query for itemType=" + customItemType + " scrollIdentifier=" + scrollIdentifier
                                    + " scrollTimeValidity=" + scrollTimeValidity, t);
                }
            }
        }.catchingExecuteInClassLoader(true);
    }

    /**
     * @deprecated As of version 1.3.0-incubating, use {@link #aggregateWithOptimizedQuery(Condition, BaseAggregate, String)} instead
     */
    @Deprecated @Override public Map<String, Long> aggregateQuery(Condition filter, BaseAggregate aggregate, String itemType) {
        return aggregateQuery(filter, aggregate, itemType, false, aggregateQueryBucketSize);
    }

    @Override public Map<String, Long> aggregateWithOptimizedQuery(Condition filter, BaseAggregate aggregate, String itemType) {
        return aggregateQuery(filter, aggregate, itemType, true, aggregateQueryBucketSize);
    }

    @Override public Map<String, Long> aggregateWithOptimizedQuery(Condition filter, BaseAggregate aggregate, String itemType, int size) {
        return aggregateQuery(filter, aggregate, itemType, true, size);
    }

    private Map<String, Long> aggregateQuery(final Condition filter, final BaseAggregate aggregate, final String itemType,
            final boolean optimizedQuery, int queryBucketSize) {
        String finalTenantId = validateTenantAndGetId(SecurityServiceConfiguration.PERMISSION_AGGREGATE);
        return new InClassLoaderExecute<Map<String, Long>>(metricsService, this.getClass().getName() + ".aggregateQuery",
                this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {

            @Override protected Map<String, Long> execute(Object... args) throws IOException {
                Map<String, Long> results = new LinkedHashMap<>();

                Map<String, Aggregation> aggregationsByType = new HashMap<>();
                if (aggregate != null) {
                    Aggregation bucketsAggregation = null;
                    String fieldName = aggregate.getField();
                    if (aggregate instanceof DateAggregate dateAggregate) {
                        DateHistogramAggregation.Builder dateHistogramBuilder = new DateHistogramAggregation.Builder().field(fieldName)
                                .calendarInterval(CalendarInterval.valueOf(dateAggregate.getIntervalByAlias(dateAggregate.getInterval())));
                        if (dateAggregate.getFormat() != null) {
                            dateHistogramBuilder.format(dateAggregate.getFormat());
                        }
                        bucketsAggregation = new Aggregation.Builder().dateHistogram(dateHistogramBuilder.build()).build();
                    } else if (aggregate instanceof NumericRangeAggregate numericRangeAggregate) {
                        List<AggregationRange> ranges = new ArrayList<>();
                        for (NumericRange numericRange : numericRangeAggregate.getRanges()) {
                            if (numericRange != null) {
                                ranges.add(AggregationRange.of(builder -> builder.from(numericRange.getFrom()).to(numericRange.getTo())
                                        .key(numericRange.getKey())));
                            }
                        }
                        RangeAggregation rangeAgg = new RangeAggregation.Builder().field(fieldName).ranges(ranges).build();
                        bucketsAggregation = new Aggregation.Builder().range(rangeAgg).build();
                    } else if (aggregate instanceof DateRangeAggregate dateRangeAggregate) {
                        List<DateRangeExpression> dateRanges = new ArrayList<>();
                        for (DateRange range : dateRangeAggregate.getDateRanges()) {
                            if (range != null) {
                                DateRangeExpression.Builder exprBuilder = new DateRangeExpression.Builder();
                                if (range.getKey() != null) {
                                    exprBuilder.key(range.getKey());
                                }
                                if (range.getFrom() != null) {
                                    exprBuilder.from(FieldDateMath.of(f -> f.expr(range.getFrom().toString())));
                                }
                                if (range.getTo() != null) {
                                    exprBuilder.to(FieldDateMath.of(f -> f.expr(range.getTo().toString())));
                                }
                                dateRanges.add(exprBuilder.build());
                            }
                        }
                        DateRangeAggregation.Builder dateRangeBuilder = new DateRangeAggregation.Builder().field(fieldName)
                                .ranges(dateRanges);

                        if (dateRangeAggregate.getFormat() != null) {
                            dateRangeBuilder.format(dateRangeAggregate.getFormat());
                        }

                        bucketsAggregation = new Aggregation.Builder().dateRange(dateRangeBuilder.build()).build();
                    } else if (aggregate instanceof IpRangeAggregate ipRangeAggregate) {
                        IpRangeAggregation.Builder ipRangeBuilder = new IpRangeAggregation.Builder().field(fieldName);
                        List<IpRangeAggregationRange> ranges = new ArrayList<>();
                        for (IpRange range : ipRangeAggregate.getRanges()) {
                            if (range != null) {
                                IpRangeAggregationRange.of(builder -> builder.from(range.getFrom()).to(range.getTo()));
                                ranges.add(IpRangeAggregationRange.of(builder -> builder.from(range.getFrom()).to(range.getTo())));
                            }
                        }
                        ipRangeBuilder.ranges(ranges);
                        bucketsAggregation = ipRangeBuilder.build()._toAggregation();
                    } else {
                        fieldName = getPropertyNameWithData(fieldName, itemType);
                        //default
                        if (fieldName != null) {
                            TermsAggregation.Builder termsAggBuilder = new TermsAggregation.Builder().field(fieldName)
                                    .size(queryBucketSize);
                            if (aggregate instanceof TermsAggregate termsAggregate) {

                                if (termsAggregate.getPartition() > -1 && termsAggregate.getNumPartitions() > -1) {
                                    termsAggBuilder.include(TermsInclude.of(ti -> ti.partition(
                                            pi -> pi.partition(termsAggregate.getPartition())
                                                    .numPartitions(termsAggregate.getNumPartitions()))));
                                }
                            }
                            bucketsAggregation = termsAggBuilder.build()._toAggregation();
                        }
                    }
                    if (bucketsAggregation != null) {
                        MissingAggregation missingAggregation = new MissingAggregation.Builder().field(fieldName).build();
                        aggregationsByType.put("buckets", bucketsAggregation);
                        aggregationsByType.put("missing", missingAggregation._toAggregation());
                    }
                }

                SearchRequest.Builder searchSourceBuilder = new SearchRequest.Builder();
                searchSourceBuilder.index(getIndexNameForQuery(itemType));
                searchSourceBuilder.size(0);
                searchSourceBuilder.query(
                        isItemTypeSharingIndex(itemType) ? getItemTypeQuery(itemType) : Query.of(q -> q.matchAll(m -> m)));

                // If the request is optimized then we don't need a global aggregation which is very slow and we can put the query with a
                // filter on range items in the query block so we don't retrieve all the document before filtering the whole
                if (optimizedQuery) {
                    searchSourceBuilder.aggregations(aggregationsByType);

                    if (filter != null) {
                        searchSourceBuilder.query(wrapWithTenantAndItemTypeQuery(itemType, conditionESQueryBuilderDispatcher.buildFilter(filter), finalTenantId));
                    }
                } else {
                    if (filter != null) {
                        Aggregation.Builder aggBuilder = new Aggregation.Builder();
                        aggBuilder.filter(wrapWithTenantAndItemTypeQuery(itemType, conditionESQueryBuilderDispatcher.buildFilter(filter), finalTenantId))
                                .aggregations(aggregationsByType);

                        aggregationsByType = Map.of("filter", aggBuilder.build());
                    }
                    Aggregation globalAgg = new Aggregation.Builder().global(new GlobalAggregation.Builder().build())
                            .aggregations(aggregationsByType).build();
                    searchSourceBuilder.aggregations(String.valueOf(globalAgg._kind()), globalAgg);
                }

                RequestOptions.Builder builder = RequestOptions.DEFAULT.toBuilder();

                RestClientOptions additionalOptions = null;
                if (aggQueryMaxResponseSizeHttp != null) {
                    HttpAsyncResponseConsumerFactory httpAsyncResponseConsumerFactory = new HttpAsyncResponseConsumerFactory.HeapBufferedResponseConsumerFactory(
                            aggQueryMaxResponseSizeHttp);
                    RequestOptions requestOptions = RequestOptions.DEFAULT.toBuilder()
                            .setHttpAsyncResponseConsumerFactory(httpAsyncResponseConsumerFactory).build();

                    additionalOptions = new RestClientOptions(requestOptions, true);
                }

                SearchResponse response;
                if (additionalOptions != null) {
                    ElasticsearchClient clientWithOptions = esClient.withTransportOptions(additionalOptions);
                    response = clientWithOptions.search(searchSourceBuilder.build());
                    clientWithOptions.close();
                } else {
                    response = esClient.search(searchSourceBuilder.build());
                }

                Map<String, Aggregate> aggregations = response.aggregations();

                if (aggregations != null) {
                    if (optimizedQuery) {
                        if (response.hits() != null) {
                            results.put("_filtered", response.hits().total().value());
                        }
                    } else {
                        GlobalAggregate globalAggregate = aggregations.get(Aggregation.Kind.Global.jsonValue()).global();
                        results.put("_all", globalAggregate.docCount());
                        aggregations = globalAggregate.aggregations();

                        if (aggregations.get(Aggregate.Kind.Filter.jsonValue()) != null) {
                            FilterAggregate filterAggregate = aggregations.get(Aggregation.Kind.Filter.jsonValue()).filter();
                            results.put("_filtered", filterAggregate.docCount());
                            aggregations = filterAggregate.aggregations();
                        }
                    }
                    if (aggregations.get("buckets") != null) {
                        if (aggQueryThrowOnMissingDocs) {
                            Aggregate agg = aggregations.get("buckets");
                            if (agg.isSterms()) {
                                StringTermsAggregate terms = aggregations.get("buckets").sterms();
                                if (terms.docCountErrorUpperBound() > 0 || terms.sumOtherDocCount() > 0) {
                                    throw new UnsupportedOperationException("Some docs are missing in aggregation query. docCountError is:"
                                            + terms.docCountErrorUpperBound() + " sumOfOtherDocCounts:" + terms.sumOtherDocCount());
                                }
                            }
                            // TODO check if needed for dTerms and lTerms
                        }

                        long totalDocCount = 0;
                        Aggregate bucketsAggregate = aggregations.get("buckets");
                        if (bucketsAggregate.isMultiTerms()) {
                            MultiTermsAggregate terms = aggregations.get("buckets").multiTerms();
                            for (MultiTermsBucket bucket : terms.buckets().array()) {
                                results.put(bucket.keyAsString(), bucket.docCount());
                                totalDocCount += bucket.docCount();
                            }
                        } else if (bucketsAggregate.isSterms()) {
                            StringTermsAggregate terms = bucketsAggregate.sterms();
                            for (StringTermsBucket bucket : terms.buckets().array()) {
                                results.put(bucket.key().stringValue(), bucket.docCount());
                                totalDocCount += bucket.docCount();
                            }
                        } else if (bucketsAggregate.isLterms()) {
                            LongTermsAggregate terms = bucketsAggregate.lterms();
                            for (LongTermsBucket bucket : terms.buckets().array()) {
                                results.put(bucket.keyAsString(), bucket.docCount());
                                totalDocCount += bucket.docCount();
                            }
                        } else if (bucketsAggregate.isDateHistogram()){
                            DateHistogramAggregate histogramAggregate = bucketsAggregate.dateHistogram();
                            for (DateHistogramBucket bucket : histogramAggregate.buckets().array()) {
                                results.put(bucket.keyAsString(), bucket.docCount());
                                totalDocCount += bucket.docCount();
                            }
                        }

                        MissingAggregate missing = aggregations.get("missing").missing();
                        if (missing.docCount() > 0) {
                            results.put("_missing", missing.docCount());
                            totalDocCount += missing.docCount();
                        }
                        if (response.hits() != null && TotalHitsRelation.Gte.equals(response.hits().total().relation())) {
                            results.put("_filtered", totalDocCount);
                        }
                    }
                }
                return results;
            }
        }.catchingExecuteInClassLoader(true);
    }

    private <T extends Item> String[] getRouting(String fieldName, String[] fieldValues, Class<T> clazz) {
        String itemType = Item.getItemType(clazz);
        String[] routing = null;
        if (routingByType.containsKey(itemType) && routingByType.get(itemType).equals(fieldName)) {
            routing = fieldValues;
        }
        return routing;
    }

    @Override public void refresh() {
        new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".refresh", this.bundleContext,
                this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) {
                try {
                    esClient.indices().refresh(new RefreshRequest.Builder().build());
                } catch (IOException e) {
                    LOGGER.error("Failed to refresh persistence for reason: {}. Set the log in DEBUG level for details", e.getMessage());
                    LOGGER.debug("Error on refresh: ", e);
                }
                return true;
            }
        }.catchingExecuteInClassLoader(true);
    }

    @Override public <T extends Item> void refreshIndex(Class<T> clazz, Date dateHint) {
        new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".refreshIndex", this.bundleContext,
                this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) {
                try {
                    esClient.indices().refresh(RefreshRequest.of(builder -> builder.index(getIndex(Item.getItemType(clazz)))));
                } catch (IOException e) {
                    LOGGER.error("Failed to refresh index for reason: {}. Set the log in DEBUG level for details", e.getMessage());
                    LOGGER.debug("Error on refresh: ", e);
                }
                return true;
            }
        }.catchingExecuteInClassLoader(true);
    }

    @Override public void purge(final Date date) {
        // nothing, this method is deprecated since 2.2.0
    }

    @Override public <T extends Item> void purgeTimeBasedItems(int existsNumberOfDays, Class<T> clazz) {
        new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".purgeTimeBasedItems", this.bundleContext,
                this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws Exception {
                String itemType = Item.getItemType(clazz);

                if (existsNumberOfDays > 0 && isItemTypeRollingOver(itemType)) {
                    // First we purge the documents
                    Query query = Query.of(builder -> builder.range(
                            RangeQuery.of(r -> r.term(term -> term.field("timeStamp").lte("now-" + existsNumberOfDays + "d")))));
                    removeByQuery(query, clazz);

                    // get count per index for those time based data
                    TreeMap<String, Long> countsPerIndex = new TreeMap<>();
                    GetIndexResponse getIndexResponse = esClient.indices()
                            .get(new GetIndexRequest.Builder().index(getIndexNameForQuery(itemType)).build());
                    Map<String, IndexState> indices = getIndexResponse.indices();

                    for (Map.Entry<String, IndexState> entry : indices.entrySet()) {
                        String indexName = entry.getKey();
                        // Filter out invalid index names (e.g., data stream backing indices with identifiers)
                        // Valid index names should not contain '/' characters
                        if (indexName.contains("/")) {
                            LOGGER.debug("Skipping invalid index name (likely data stream backing index): {}", indexName);
                            continue;
                        }
                        try {
                            CountRequest countRequest = new CountRequest.Builder().index(indexName).build();
                            countsPerIndex.put(indexName, esClient.count(countRequest).count());
                        } catch (Exception e) {
                            LOGGER.warn("Error counting documents in index {}: {}", indexName, e.getMessage());
                            // Skip this index if we can't count it
                            continue;
                        }
                    }

                    // Check for count=0 and remove them
                    if (!countsPerIndex.isEmpty()) {
                        // do not check the last index, because it's the one used to write documents
                        countsPerIndex.pollLastEntry();

                        for (Map.Entry<String, Long> indexCount : countsPerIndex.entrySet()) {
                            if (indexCount.getValue() == 0) {
                                try {
                                    // Verify the index exists before trying to delete it
                                    // This prevents errors when trying to delete aliases or invalid index names
                                    GetIndexRequest checkRequest = new GetIndexRequest.Builder().index(indexCount.getKey()).build();
                                    GetIndexResponse checkResponse = esClient.indices().get(checkRequest);
                                    if (checkResponse.indices().containsKey(indexCount.getKey())) {
                                        esClient.indices().delete(new DeleteIndexRequest.Builder().index(indexCount.getKey()).build());
                                    } else {
                                        LOGGER.debug("Index {} does not exist, skipping deletion", indexCount.getKey());
                                    }
                                } catch (Exception e) {
                                    // Log but don't fail - index might have been deleted already or might be an alias
                                    LOGGER.debug("Could not delete index {} (may not exist or may be an alias): {}", indexCount.getKey(), e.getMessage());
                                }
                            }
                        }
                    }
                }

                return true;
            }
        }.catchingExecuteInClassLoader(true);
    }

    @Override public void purge(final String scope) {
        LOGGER.debug("Purge scope {}", scope);
        String finalTenantId = validateTenantAndGetId(SecurityServiceConfiguration.PERMISSION_PURGE);
        new InClassLoaderExecute<Void>(metricsService, this.getClass().getName() + ".purgeWithScope", this.bundleContext,
                this.fatalIllegalStateErrors, throwExceptions) {
            @Override protected Void execute(Object... args) throws IOException {
                Query query = TermQuery.of(builder -> builder.field("scope").value(scope).field("tenantId").value(ConditionContextHelper.foldToASCII(finalTenantId)))._toQuery();

                List<BulkOperation> operations = new ArrayList<>();

                Time keepAlive = Time.of(t -> t.time("1h"));

                SearchRequest searchRequest = SearchRequest.of(
                        s -> s.index(getAllIndexForQuery()).scroll(keepAlive).size(100).query(query).source(src -> src.fetch(true)));
                SearchResponse<JsonData> searchResponse = esClient.search(searchRequest, JsonData.class);

                List<Hit<JsonData>> hits = searchResponse.hits().hits();
                String scrollId = searchResponse.scrollId();
                // Scroll until no more hits are returned
                while (!hits.isEmpty()) {
                    for (Hit<JsonData> hit : searchResponse.hits().hits()) {
                        // add hit to bulk delete
                        operations.add(BulkOperation.of(builder -> builder.delete(d -> d.index(hit.index()).id(hit.id()))));
                    }

                    ScrollRequest scrollRequest = new ScrollRequest.Builder().scrollId(scrollId).scroll(keepAlive).build();
                    ScrollResponse<JsonData> scrollResponse = esClient.scroll(scrollRequest, JsonData.class);
                    hits = scrollResponse.hits().hits();
                    scrollId = scrollResponse.scrollId();
                    // If we have no more hits, exit
                    if (hits.isEmpty()) {
                        ClearScrollRequest clearScrollRequest = new ClearScrollRequest.Builder().scrollId(scrollId).build();
                        esClient.clearScroll(clearScrollRequest);
                    }
                }

                // we're done with the scrolling, delete now
                if (!operations.isEmpty()) {
                    BulkResponse bulkResponse = esClient.bulk(b -> b.operations(operations));
                    if (bulkResponse.errors()) {
                        bulkResponse.items().forEach(item -> {
                            if (item.error() != null) {
                                LOGGER.warn("Couldn't delete item {} from scope {}: {}", item.id(), scope, item.error().reason());
                            }
                        });
                    }
                }
                return null;
            }
        }.catchingExecuteInClassLoader(true);
    }

    @Override public Map<String, Double> getSingleValuesMetrics(final Condition condition, final String[] metrics, final String field,
            final String itemType) {
        return new InClassLoaderExecute<Map<String, Double>>(metricsService, this.getClass().getName() + ".getSingleValuesMetrics",
                this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {

            @Override protected Map<String, Double> execute(Object... args) throws IOException {
                Map<String, Double> results = new LinkedHashMap<String, Double>();

                Map<String, Aggregation> subAggs = new HashMap<>();
                if (metrics != null) {
                    for (String metric : metrics) {
                        switch (metric) {
                            case "sum":
                                subAggs.put("sum", AggregationBuilders.sum().field(field).build()._toAggregation());
                                break;
                            case "avg":
                                subAggs.put("avg", AggregationBuilders.avg().field(field).build()._toAggregation());
                                break;
                            case "min":
                                subAggs.put("min", AggregationBuilders.min().field(field).build()._toAggregation());
                                break;
                            case "max":
                                subAggs.put("max", AggregationBuilders.max().field(field).build()._toAggregation());
                                break;
                            case "card":
                                subAggs.put("card", AggregationBuilders.cardinality().field(field).build()._toAggregation());
                                break;
                            case "count":
                                subAggs.put("count", Aggregation.of(a -> a.valueCount(vc -> vc.field(field))));
                                break;
                        }
                    }
                }

                Aggregation filterAggregation = Aggregation.of(
                        a -> a.filter(conditionESQueryBuilderDispatcher.buildFilter(condition)).aggregations(subAggs));
                SearchRequest searchRequest = SearchRequest.of(
                        s -> s.index(getIndexNameForQuery(itemType)).size(0).source(builder -> builder.fetch(true))
                                .query(isItemTypeSharingIndex(itemType) ? getItemTypeQuery(itemType) : Query.of(q -> q.matchAll(m -> m)))
                                .aggregations("metrics", filterAggregation));

                SearchResponse<Void> searchResponse = esClient.search(searchRequest);

                Map<String, Aggregate> aggregationResult = searchResponse.aggregations();

                if (aggregationResult != null) {
                    Aggregate metricsAgg = aggregationResult.get("metrics");
                    if (metricsAgg != null && metricsAgg.isFilter()) {
                        Map<String, Aggregate> subAggsResult = metricsAgg.filter().aggregations();
                        for (Map.Entry<String, Aggregate> entry : subAggsResult.entrySet()) {
                            String name = entry.getKey();
                            Double value = getAggregeValue(entry);

                            if (value != null) {
                                results.put("_" + name, value);
                            }
                        }
                    }
                }
                return results;
            }
        }.catchingExecuteInClassLoader(true);
    }

    private static Double getAggregeValue(Map.Entry<String, Aggregate> entry) {
        Aggregate agg = entry.getValue();

        Double value = null;
        if (agg.isSum()) {
            value = agg.sum().value();
        } else if (agg.isAvg()) {
            value = agg.avg().value();
        } else if (agg.isMin()) {
            value = agg.min().value();
        } else if (agg.isMax()) {
            value = agg.max().value();
        } else if (agg.isCardinality()) {
            value = (double) agg.cardinality().value();
        } else if (agg.isValueCount()) {
            value = agg.valueCount().value();
        }
        return value;
    }

    private String getConfig(Map<String, String> settings, String key, String defaultValue) {
        if (settings != null && settings.get(key) != null) {
            return settings.get(key);
        }
        return defaultValue;
    }

    public abstract static class InClassLoaderExecute<T> {

        private final String timerName;
        private final MetricsService metricsService;
        private final BundleContext bundleContext;
        private final String[] fatalIllegalStateErrors; // Errors that if occur - stop the application
        private final boolean throwExceptions;

        public InClassLoaderExecute(MetricsService metricsService, String timerName, BundleContext bundleContext,
                String[] fatalIllegalStateErrors, boolean throwExceptions) {
            this.timerName = timerName;
            this.metricsService = metricsService;
            this.bundleContext = bundleContext;
            this.fatalIllegalStateErrors = fatalIllegalStateErrors;
            this.throwExceptions = throwExceptions;
        }

        protected abstract T execute(Object... args) throws Exception;

        public T executeInClassLoader(Object... args) throws Exception {

            long startTime = System.currentTimeMillis();
            ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
                return execute(args);
            } finally {
                if (metricsService != null && metricsService.isActivated()) {
                    metricsService.updateTimer(timerName, startTime);
                }
                Thread.currentThread().setContextClassLoader(tccl);
            }
        }

        public T catchingExecuteInClassLoader(boolean logError, Object... args) {
            try {
                return executeInClassLoader(timerName, args);
            } catch (Throwable t) {
                Throwable tTemp = t;
                // Go over the stack trace and check if there were any fatal state errors
                while (tTemp != null) {
                    if (tTemp instanceof IllegalStateException && Arrays.stream(this.fatalIllegalStateErrors)
                            .anyMatch(tTemp.getMessage()::contains)) {
                        handleFatalStateError(); // Stop application
                        return null;
                    }
                    tTemp = tTemp.getCause();
                }
                handleError(t, logError);
            }
            return null;
        }

        private void handleError(Throwable t, boolean logError) {
            if (logError) {
                LOGGER.error("Error while executing in class loader", t);
            }
            if (throwExceptions) {
                throw new RuntimeException(t);
            }
        }

        private void handleFatalStateError() {
            LOGGER.error("Fatal state error occurred - stopping application");
            try {
                this.bundleContext.getBundle(0).stop();
            } catch (Throwable tInner) { // Stopping system bundle failed - force exit
                System.exit(-1);
            }
        }
    }

    private String getAllIndexForQuery() {
        return indexPrefix + "*";
    }

    private String getIndexNameForQuery(String itemType) {
        return isItemTypeRollingOver(itemType) ? getRolloverIndexForQuery(itemType) : getIndex(itemType);
    }

    private String getRolloverIndexForQuery(String itemType) {
        return indexPrefix + "-" + itemType.toLowerCase() + "-*";
    }

    private String getIndex(String itemType) {
        return (indexPrefix + "-" + getIndexNameForItemType(itemType)).toLowerCase();
    }

    private String getIndexNameForItemType(String itemType) {
        return itemTypeIndexNameMap.getOrDefault(itemType, itemType);
    }

    private String getDocumentIDForItemType(String itemId, String itemType) {
        String tenantId = getTenantId();
        String baseId = systemItems.contains(itemType) ? (itemId + "_" + itemType.toLowerCase()) : itemId;
        return tenantId + "_" + baseId;
    }

    private String stripTenantFromDocumentId(String documentId) {
        if (documentId == null) {
            return null;
        }
        String tenantId = getTenantId();
        if (documentId.startsWith(tenantId + "_")) {
            return documentId.substring(tenantId.length() + 1);
        } else if (documentId.startsWith(SYSTEM_TENANT + "_")) {
            return documentId.substring(SYSTEM_TENANT.length() + 1);
        }
        return documentId;
    }

    private Query wrapWithTenantAndItemTypeQuery(String itemType, Query originalQuery, String tenantId) {
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        // Add tenants filter
        if (tenantId != null) {
            boolQuery.must(q->q.term(t->t.field("tenantId").value(ConditionContextHelper.foldToASCII(tenantId))));
        }

        // Add item type filter if needed
        if (isItemTypeSharingIndex(itemType)) {
            boolQuery.must(getItemTypeQuery(itemType));
        }

        // Add original query
        if (originalQuery != null) {
            boolQuery.must(originalQuery);
        }

        return Query.of(builder -> builder.bool(boolQuery.build()));
    }

    private Query wrapWithTenantAndItemsTypeQuery(String[] itemTypes, Query originalQuery, String tenantId) {
        if (itemTypes.length == 1) {
            return wrapWithTenantAndItemTypeQuery(itemTypes[0], originalQuery, tenantId);
        }

        if (Arrays.stream(itemTypes).anyMatch(this::isItemTypeSharingIndex)) {
            BoolQuery.Builder itemTypeQuery = new BoolQuery.Builder();
            itemTypeQuery.minimumShouldMatch("1");

            for (String itemType : itemTypes) {
                itemTypeQuery.should(getItemTypeQuery(itemType));
            }

            BoolQuery.Builder wrappedQuery = new BoolQuery.Builder();
            wrappedQuery.filter(itemTypeQuery.build());
            wrappedQuery.must(originalQuery);
            if (tenantId != null) {
                wrappedQuery.must(q->q.term(t->t.field("tenantId").value(ConditionContextHelper.foldToASCII(tenantId))));
            }
            return Query.of(builder -> builder.bool(wrappedQuery.build()));
        }
        if (tenantId != null) {
            BoolQuery.Builder wrappedQuery = new BoolQuery.Builder();
            wrappedQuery.must(originalQuery);
            wrappedQuery.must(q->q.term(t->t.field("tenantId").value(ConditionContextHelper.foldToASCII(tenantId))));
            return Query.of(builder -> builder.bool(wrappedQuery.build()));
        }
        return originalQuery;
    }

    private Query getItemTypeQuery(String itemType) {
        return Query.of(q -> q.term(t -> t.field("itemType").value(ConditionContextHelper.foldToASCII(itemType))));
    }

    private boolean isItemTypeSharingIndex(String itemType) {
        return itemTypeIndexNameMap.containsKey(itemType);
    }

    private boolean isItemTypeRollingOver(String itemType) {
        return (rolloverIndices != null ? rolloverIndices.contains(itemType) : false);
    }

    private Refresh getRefreshPolicy(String itemType) {
        if (itemTypeToRefreshPolicy.containsKey(itemType)) {
            return itemTypeToRefreshPolicy.get(itemType);
        }

        return Refresh.False;
    }

    private void logMetadataItemOperation(String operation, Item item) {
        if (item instanceof MetadataItem) {
            LOGGER.info("Item of type {} with ID {} has been {}", item.getItemType(), item.getItemId(), operation);
        }
    }

    private void addTenantMetadata(Item item, String tenantId) {
        if (item != null && tenantId != null) {
            item.setTenantId(tenantId);
        }
    }

    private <T extends Item> T handleItemTransformation(T item) {
        if (item != null) {
            String tenantId = item.getTenantId();
            if (tenantId != null) {
                for (TenantTransformationListener listener : transformationListeners) {
                    if (listener.isTransformationEnabled()) {
                        try {
                            T transformedItem = (T) listener.transformItem(item, tenantId);
                            if (transformedItem != null) {
                                item = transformedItem;
                            }
                        } catch (Exception e) {
                            // Log error but continue with other listeners since transformation is optional
                            LOGGER.warn("Error during item transformation for tenant {} with listener {}: {}",
                                tenantId, listener.getTransformationType(), e.getMessage());
                        }
                    }
                }
            }
        }
        return item;
    }

    private <T extends Item> T handleItemReverseTransformation(T item) {
        if (item != null) {
            String tenantId = item.getTenantId();
            if (tenantId != null) {
                for (TenantTransformationListener listener : transformationListeners) {
                    if (listener.isTransformationEnabled()) {
                        try {
                            T transformedItem = (T) listener.reverseTransformItem(item, tenantId);
                            if (transformedItem != null) {
                                item = transformedItem;
                            }
                        } catch (Exception e) {
                            // Log error but continue with other listeners since transformation is optional
                            LOGGER.warn("Error during item reverse transformation for tenant {} with listener {}: {}",
                                tenantId, listener.getTransformationType(), e.getMessage());
                        }
                    }
                }
            }
        }
        return item;
    }

    @Override
    public long calculateStorageSize(String tenantId) {
        try {
            Query query = Query.of(q -> q.term(t -> t.field("tenantId").value(v -> v.stringValue(ConditionContextHelper.foldToASCII(tenantId)))));

            // Execute count query
            CountResponse response = esClient.count(c -> c
                    .index(getAllIndexForQuery())
                    .query(query));

            return response.count();

        } catch (IOException e) {
            LOGGER.error("Error calculating storage size for tenant " + tenantId, e);
            return -1;
        }
    }

    @Override
    public boolean migrateTenantData(String sourceTenantId, String targetTenantId, List<String> itemTypes) {
        try {
            Query query = Query.of(q -> q.term(t -> t.field("tenantId").value(v -> v.stringValue(ConditionContextHelper.foldToASCII(sourceTenantId)))));

            SearchResponse<Item> searchResponse = esClient.search(s -> s
                            .index(getAllIndexForQuery())
                            .query(query)
                            .size(1000)
                            .scroll(t -> t.time("1m")),
                    Item.class);

            String scrollId = searchResponse.scrollId();

            while (!searchResponse.hits().hits().isEmpty()) {
                List<BulkOperation> operations = new ArrayList<>();

                // Process each hit
                for (Hit<Item> hit : searchResponse.hits().hits()) {
                    Item source = hit.source();
                    if (source == null) {
                        LOGGER.warn("Source item is null for hit {}", hit.id());
                        continue;
                    }
                    source.setTenantId(targetTenantId);

                    // Create new document ID with target tenant prefix
                    String oldId = stripTenantFromDocumentId(hit.id());
                    String newDocumentId = getDocumentIDForItemType(oldId, source.getItemType());

                    // Add index operation for new document
                    operations.add(BulkOperation.of(b -> b.index(idx -> idx
                            .index(hit.index())
                            .id(newDocumentId)
                            .document(source))));

                    // Add delete operation for old document
                    operations.add(BulkOperation.of(b -> b.delete(del -> del
                            .index(hit.index())
                            .id(hit.id()))));
                }

                // Execute bulk update if there are operations
                if (!operations.isEmpty()) {
                    esClient.bulk(b -> b.operations(operations));
                }

                final String finalScrollId = scrollId;
                // Get next batch
                ScrollResponse scrollResponse = esClient.scroll(s -> s
                                .scrollId(finalScrollId)
                                .scroll(t -> t.time("1m")),
                        Item.class);

                scrollId = scrollResponse.scrollId();
            }
            // Clear scroll
            final String finalScrollId = scrollId;
            esClient.clearScroll(c -> c.scrollId(finalScrollId));

            return true;

        } catch (IOException e) {
            LOGGER.error("Error migrating tenant data from " + sourceTenantId + " to " + targetTenantId, e);
            return false;
        }
    }

    @Override
    public long getApiCallCount(String tenantId) {
        try {
            // Build query to count API calls for tenant
            Query query = Query.of(q -> q.bool(b -> b
                    .must(Query.of(q2 -> q2.term(t -> t.field("tenantId").value(v -> v.stringValue(ConditionContextHelper.foldToASCII(tenantId))))))
                    .must(Query.of(q2 -> q2.term(t -> t.field("itemType").value(v -> v.stringValue("apiCall")))))));

            // Execute count query
            CountResponse response = esClient.count(c -> c
                    .index(getAllIndexForQuery())
                    .query(query));

            return response.count();

        } catch (IOException e) {
            LOGGER.error("Error getting API call count for tenant " + tenantId, e);
            return -1;
        }
    }

    public void bindTransformationListener(ServiceReference<TenantTransformationListener> listenerReference) {
        TenantTransformationListener listener = bundleContext.getService(listenerReference);
        transformationListeners.add(listener);
        // Sort listeners by priority (highest first)
        transformationListeners.sort((l1, l2) -> Integer.compare(l2.getPriority(), l1.getPriority()));
    }

    public void unbindTransformationListener(ServiceReference<TenantTransformationListener> listenerReference) {
        if (listenerReference != null) {
            TenantTransformationListener listener = bundleContext.getService(listenerReference);
            transformationListeners.remove(listener);
        }
    }

}
