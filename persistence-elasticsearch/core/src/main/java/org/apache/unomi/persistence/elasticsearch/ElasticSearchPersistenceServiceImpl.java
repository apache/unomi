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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.log4j.Level;
import org.apache.lucene.search.TotalHits;
import org.apache.unomi.api.*;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.DateRange;
import org.apache.unomi.api.query.IpRange;
import org.apache.unomi.api.query.NumericRange;
import org.apache.unomi.metrics.MetricAdapter;
import org.apache.unomi.metrics.MetricsService;
import org.apache.unomi.persistence.elasticsearch.conditions.*;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.aggregate.*;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.Version;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.storedscripts.PutStoredScriptRequest;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.template.delete.DeleteIndexTemplateRequest;
import org.elasticsearch.action.bulk.*;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.*;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.client.core.MainResponse;
import org.elasticsearch.client.indexlifecycle.*;
import org.elasticsearch.client.indices.*;
import org.elasticsearch.client.tasks.GetTaskRequest;
import org.elasticsearch.client.tasks.GetTaskResponse;
import org.elasticsearch.client.tasks.TaskSubmissionResponse;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.*;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.query.*;
import org.elasticsearch.index.reindex.*;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptException;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.*;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.global.Global;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.missing.MissingAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.range.DateRangeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.range.IpRangeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.IncludeExclude;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.NumericMetricsAggregation;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.GeoDistanceSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.tasks.TaskId;
import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@SuppressWarnings("rawtypes")
public class ElasticSearchPersistenceServiceImpl implements PersistenceService, SynchronousBundleListener {

    public static final String BULK_PROCESSOR_BULK_SIZE = "bulkProcessor.bulkSize";
    public static final String BULK_PROCESSOR_FLUSH_INTERVAL = "bulkProcessor.flushInterval";
    public static final String BULK_PROCESSOR_BACKOFF_POLICY = "bulkProcessor.backoffPolicy";
    public static final String SEQ_NO = "seq_no";
    public static final String PRIMARY_TERM = "primary_term";

    private static final Logger logger = LoggerFactory.getLogger(ElasticSearchPersistenceServiceImpl.class.getName());
    private static final String ROLLOVER_LIFECYCLE_NAME = "unomi-rollover-policy";

    private boolean throwExceptions = false;
    private CustomRestHighLevelClient client;
    private BulkProcessor bulkProcessor;
    private String elasticSearchAddresses;
    private List<String> elasticSearchAddressList = new ArrayList<>();
    private String clusterName;
    private String indexPrefix;
    private String monthlyIndexNumberOfShards;
    private String monthlyIndexNumberOfReplicas;
    private String monthlyIndexMappingTotalFieldsLimit;
    private String monthlyIndexMaxDocValueFieldsSearch;
    private String numberOfShards;
    private String numberOfReplicas;
    private String indexMappingTotalFieldsLimit;
    private String indexMaxDocValueFieldsSearch;
    private String[] fatalIllegalStateErrors;
    private BundleContext bundleContext;
    private Map<String, String> mappings = new HashMap<String, String>();
    private ConditionEvaluatorDispatcher conditionEvaluatorDispatcher;
    private ConditionESQueryBuilderDispatcher conditionESQueryBuilderDispatcher;
    private List<String> itemsMonthlyIndexed;
    private Map<String, String> routingByType;

    private Integer defaultQueryLimit = 10;
    private Integer removeByQueryTimeoutInMinutes = 10;
    private Integer taskWaitingTimeout = 3600000;
    private Integer taskWaitingPollingInterval = 1000;

    private String bulkProcessorConcurrentRequests = "1";
    private String bulkProcessorBulkActions = "1000";
    private String bulkProcessorBulkSize = "5MB";
    private String bulkProcessorFlushInterval = "5s";
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

    private String minimalElasticSearchVersion = "7.0.0";
    private String maximalElasticSearchVersion = "8.0.0";

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
    private Map<String, WriteRequest.RefreshPolicy> itemTypeToRefreshPolicy = new HashMap<>();

    private Map<String, Map<String, Map<String, Object>>> knownMappings = new HashMap<>();

    private static final Map<String, String> itemTypeIndexNameMap = new HashMap<>();
    private static final Collection<String> systemItems = Arrays.asList("actionType", "campaign", "campaignevent", "goal",
            "userList", "propertyType", "scope", "conditionType", "rule", "scoring", "segment", "groovyAction", "topic",
            "patch", "jsonSchema", "importConfig", "exportConfig", "rulestats");
    static {
        for (String systemItem : systemItems) {
            itemTypeIndexNameMap.put(systemItem, "systemItems");
        }

        itemTypeIndexNameMap.put("profile", "profile");
        itemTypeIndexNameMap.put("persona", "profile");
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
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
                    new TypeReference<HashMap<String, WriteRequest.RefreshPolicy>>() {
                    });
        }
    }

    public void setFatalIllegalStateErrors(String fatalIllegalStateErrors) {
        this.fatalIllegalStateErrors = Arrays.stream(fatalIllegalStateErrors.split(","))
                .map(i -> i.trim()).filter(i -> !i.isEmpty()).toArray(String[]::new);
    }

    public void setAggQueryMaxResponseSizeHttp(String aggQueryMaxResponseSizeHttp) {
        if (StringUtils.isNumeric(aggQueryMaxResponseSizeHttp)) {
            this.aggQueryMaxResponseSizeHttp = Integer.parseInt(aggQueryMaxResponseSizeHttp);
        }
    }

    public void setIndexPrefix(String indexPrefix) {
        this.indexPrefix = indexPrefix;
    }

    @Deprecated
    public void setMonthlyIndexNumberOfShards(String monthlyIndexNumberOfShards) {
        this.monthlyIndexNumberOfShards = monthlyIndexNumberOfShards;
    }

    @Deprecated
    public void setMonthlyIndexNumberOfReplicas(String monthlyIndexNumberOfReplicas) {
        this.monthlyIndexNumberOfReplicas = monthlyIndexNumberOfReplicas;
    }

    @Deprecated
    public void setMonthlyIndexMappingTotalFieldsLimit(String monthlyIndexMappingTotalFieldsLimit) {
        this.monthlyIndexMappingTotalFieldsLimit = monthlyIndexMappingTotalFieldsLimit;
    }

    @Deprecated
    public void setMonthlyIndexMaxDocValueFieldsSearch(String monthlyIndexMaxDocValueFieldsSearch) {
        this.monthlyIndexMaxDocValueFieldsSearch = monthlyIndexMaxDocValueFieldsSearch;
    }

    @Deprecated
    public void setItemsMonthlyIndexedOverride(String itemsMonthlyIndexedOverride) {
        this.itemsMonthlyIndexed = StringUtils.isNotEmpty(itemsMonthlyIndexedOverride) ? Arrays.asList(itemsMonthlyIndexedOverride.split(",").clone()) : Collections.emptyList();
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

    public void setBulkProcessorBulkSize(String bulkProcessorBulkSize) {
        this.bulkProcessorBulkSize = bulkProcessorBulkSize;
    }

    public void setBulkProcessorFlushInterval(String bulkProcessorFlushInterval) {
        this.bulkProcessorFlushInterval = bulkProcessorFlushInterval;
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

    public void start() throws Exception {

        // Work around to avoid ES Logs regarding the deprecated [ignore_throttled] parameter
        try {
            Level lvl = Level.toLevel(logLevelRestClient, Level.ERROR);
            org.apache.log4j.Logger.getLogger("org.elasticsearch.client.RestClient").setLevel(lvl);
        } catch (Exception e) {
            // Never fail because of the set of the logger
        }

        // on startup
        new InClassLoaderExecute<Object>(null, null, this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            public Object execute(Object... args) throws Exception {

                buildClient();

                MainResponse response = client.info(RequestOptions.DEFAULT);
                MainResponse.Version version = response.getVersion();
                Version clusterVersion = Version.fromString(version.getNumber());
                Version minimalVersion = Version.fromString(minimalElasticSearchVersion);
                Version maximalVersion = Version.fromString(maximalElasticSearchVersion);
                if (clusterVersion.before(minimalVersion) ||
                        clusterVersion.equals(maximalVersion) ||
                        clusterVersion.after(maximalVersion)) {
                    throw new Exception("ElasticSearch version is not within [" + minimalVersion + "," + maximalVersion + "), aborting startup !");
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

                if (client != null && bulkProcessor == null) {
                    bulkProcessor = getBulkProcessor();
                }

                // Wait for green
                logger.info("Waiting for GREEN cluster status...");
                client.cluster().health(new ClusterHealthRequest().waitForGreenStatus(), RequestOptions.DEFAULT);
                logger.info("Cluster status is GREEN");

                // We keep in memory the latest available session index to be able to load session using direct GET access on ES
                if (isItemTypeRollingOver(Session.ITEM_TYPE)) {
                    logger.info("Sessions are using rollover indices, loading latest session index available ...");
                    GetAliasesResponse sessionAliasResponse = client.indices().getAlias(new GetAliasesRequest(getIndex(Session.ITEM_TYPE)), RequestOptions.DEFAULT);
                    Map<String, Set<AliasMetaData>> aliases = sessionAliasResponse.getAliases();
                    if (!aliases.isEmpty()) {
                        sessionLatestIndex = new TreeSet<>(aliases.keySet()).last();
                        logger.info("Latest available session index found is: {}", sessionLatestIndex);
                    } else {
                        throw new IllegalStateException("No index found for sessions");
                    }
                }

                return true;
            }
        }.executeInClassLoader();

        bundleContext.addBundleListener(this);

        logger.info(this.getClass().getName() + " service started successfully.");
    }

    private void buildClient() throws NoSuchFieldException, IllegalAccessException {
        List<Node> nodeList = new ArrayList<>();
        for (String elasticSearchAddress : elasticSearchAddressList) {
            String[] elasticSearchAddressParts = elasticSearchAddress.split(":");
            String elasticSearchHostName = elasticSearchAddressParts[0];
            int elasticSearchPort = Integer.parseInt(elasticSearchAddressParts[1]);

            // configure authentication
            nodeList.add(new Node(new HttpHost(elasticSearchHostName, elasticSearchPort, sslEnable ? "https" : "http")));
        }

        RestClientBuilder clientBuilder = RestClient.builder(nodeList.toArray(new Node[nodeList.size()]));

        if (clientSocketTimeout != null) {
            clientBuilder.setRequestConfigCallback(requestConfigBuilder -> {
                requestConfigBuilder.setSocketTimeout(clientSocketTimeout);
                return requestConfigBuilder;
            });
        }

        clientBuilder.setHttpClientConfigCallback(httpClientBuilder -> {
            if (sslTrustAllCertificates) {
                try {
                    final SSLContext sslContext = SSLContext.getInstance("SSL");
                    sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(X509Certificate[] certs,
                                                       String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs,
                                                       String authType) {
                        }
                    }}, new SecureRandom());

                    httpClientBuilder.setSSLContext(sslContext).setSSLHostnameVerifier(new NoopHostnameVerifier());
                } catch (NoSuchAlgorithmException | KeyManagementException e) {
                    logger.error("Error creating SSL Context for trust all certificates", e);
                }
            }

            if (StringUtils.isNotBlank(username)) {
                final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));

                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
            }

            return httpClientBuilder;
        });

        logger.info("Connecting to ElasticSearch persistence backend using cluster name " + clusterName + " and index prefix " + indexPrefix + "...");
        client = new CustomRestHighLevelClient(clientBuilder);
    }

    public BulkProcessor getBulkProcessor() {
        if (bulkProcessor != null) {
            return bulkProcessor;
        }
        BulkProcessor.Listener bulkProcessorListener = new BulkProcessor.Listener() {
            @Override
            public void beforeBulk(long executionId,
                                   BulkRequest request) {
                logger.debug("Before Bulk");
            }

            @Override
            public void afterBulk(long executionId,
                                  BulkRequest request,
                                  BulkResponse response) {
                logger.debug("After Bulk");
            }

            @Override
            public void afterBulk(long executionId,
                                  BulkRequest request,
                                  Throwable failure) {
                logger.error("After Bulk (failure)", failure);
            }
        };
        BulkProcessor.Builder bulkProcessorBuilder = BulkProcessor.builder(
                (request, bulkListener) ->
                        client.bulkAsync(request, RequestOptions.DEFAULT, bulkListener),
                bulkProcessorListener);

        if (bulkProcessorConcurrentRequests != null) {
            int concurrentRequests = Integer.parseInt(bulkProcessorConcurrentRequests);
            if (concurrentRequests > 1) {
                bulkProcessorBuilder.setConcurrentRequests(concurrentRequests);
            }
        }
        if (bulkProcessorBulkActions != null) {
            int bulkActions = Integer.parseInt(bulkProcessorBulkActions);
            bulkProcessorBuilder.setBulkActions(bulkActions);
        }
        if (bulkProcessorBulkSize != null) {
            bulkProcessorBuilder.setBulkSize(ByteSizeValue.parseBytesSizeValue(bulkProcessorBulkSize, new ByteSizeValue(5, ByteSizeUnit.MB), BULK_PROCESSOR_BULK_SIZE));
        }
        if (bulkProcessorFlushInterval != null) {
            bulkProcessorBuilder.setFlushInterval(TimeValue.parseTimeValue(bulkProcessorFlushInterval, null, BULK_PROCESSOR_FLUSH_INTERVAL));
        } else {
            // in ElasticSearch this defaults to null, but we would like to set a value to 5 seconds by default
            bulkProcessorBuilder.setFlushInterval(new TimeValue(5, TimeUnit.SECONDS));
        }
        if (bulkProcessorBackoffPolicy != null) {
            String backoffPolicyStr = bulkProcessorBackoffPolicy;
            if (backoffPolicyStr != null && backoffPolicyStr.length() > 0) {
                backoffPolicyStr = backoffPolicyStr.toLowerCase();
                if ("nobackoff".equals(backoffPolicyStr)) {
                    bulkProcessorBuilder.setBackoffPolicy(BackoffPolicy.noBackoff());
                } else if (backoffPolicyStr.startsWith("constant(")) {
                    int paramStartPos = backoffPolicyStr.indexOf("constant(" + "constant(".length());
                    int paramEndPos = backoffPolicyStr.indexOf(")", paramStartPos);
                    int paramSeparatorPos = backoffPolicyStr.indexOf(",", paramStartPos);
                    TimeValue delay = TimeValue.parseTimeValue(backoffPolicyStr.substring(paramStartPos, paramSeparatorPos), new TimeValue(5, TimeUnit.SECONDS), BULK_PROCESSOR_BACKOFF_POLICY);
                    int maxNumberOfRetries = Integer.parseInt(backoffPolicyStr.substring(paramSeparatorPos + 1, paramEndPos));
                    bulkProcessorBuilder.setBackoffPolicy(BackoffPolicy.constantBackoff(delay, maxNumberOfRetries));
                } else if (backoffPolicyStr.startsWith("exponential")) {
                    if (!backoffPolicyStr.contains("(")) {
                        bulkProcessorBuilder.setBackoffPolicy(BackoffPolicy.exponentialBackoff());
                    } else {
                        // we detected parameters, must process them.
                        int paramStartPos = backoffPolicyStr.indexOf("exponential(" + "exponential(".length());
                        int paramEndPos = backoffPolicyStr.indexOf(")", paramStartPos);
                        int paramSeparatorPos = backoffPolicyStr.indexOf(",", paramStartPos);
                        TimeValue delay = TimeValue.parseTimeValue(backoffPolicyStr.substring(paramStartPos, paramSeparatorPos), new TimeValue(5, TimeUnit.SECONDS), BULK_PROCESSOR_BACKOFF_POLICY);
                        int maxNumberOfRetries = Integer.parseInt(backoffPolicyStr.substring(paramSeparatorPos + 1, paramEndPos));
                        bulkProcessorBuilder.setBackoffPolicy(BackoffPolicy.exponentialBackoff(delay, maxNumberOfRetries));
                    }
                }
            }
        }

        bulkProcessor = bulkProcessorBuilder.build();
        return bulkProcessor;
    }

    public void stop() {

        new InClassLoaderExecute<Object>(null, null, this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Object execute(Object... args) throws IOException {
                logger.info("Closing ElasticSearch persistence backend...");
                if (bulkProcessor != null) {
                    try {
                        bulkProcessor.awaitClose(2, TimeUnit.MINUTES);
                    } catch (InterruptedException e) {
                        logger.error("Error waiting for bulk operations to flush !", e);
                    }
                }
                if (client != null) {
                    client.close();
                }
                return null;
            }
        }.catchingExecuteInClassLoader(true);

        bundleContext.removeBundleListener(this);
    }

    public void bindConditionEvaluator(ServiceReference<ConditionEvaluator> conditionEvaluatorServiceReference) {
        ConditionEvaluator conditionEvaluator = bundleContext.getService(conditionEvaluatorServiceReference);
        conditionEvaluatorDispatcher.addEvaluator(conditionEvaluatorServiceReference.getProperty("conditionEvaluatorId").toString(), conditionEvaluator);
    }

    public void unbindConditionEvaluator(ServiceReference<ConditionEvaluator> conditionEvaluatorServiceReference) {
        if (conditionEvaluatorServiceReference == null) {
            return;
        }
        conditionEvaluatorDispatcher.removeEvaluator(conditionEvaluatorServiceReference.getProperty("conditionEvaluatorId").toString());
    }

    public void bindConditionESQueryBuilder(ServiceReference<ConditionESQueryBuilder> conditionESQueryBuilderServiceReference) {
        ConditionESQueryBuilder conditionESQueryBuilder = bundleContext.getService(conditionESQueryBuilderServiceReference);
        conditionESQueryBuilderDispatcher.addQueryBuilder(conditionESQueryBuilderServiceReference.getProperty("queryBuilderId").toString(), conditionESQueryBuilder);
    }

    public void unbindConditionESQueryBuilder(ServiceReference<ConditionESQueryBuilder> conditionESQueryBuilderServiceReference) {
        if (conditionESQueryBuilderServiceReference == null) {
            return;
        }
        conditionESQueryBuilderDispatcher.removeQueryBuilder(conditionESQueryBuilderServiceReference.getProperty("queryBuilderId").toString());
    }

    @Override
    public void bundleChanged(BundleEvent event) {
        switch (event.getType()) {
            case BundleEvent.STARTING:
                loadPredefinedMappings(event.getBundle().getBundleContext(), true);
                loadPainlessScripts(event.getBundle().getBundleContext());
                break;
        }
    }

    private void loadPredefinedMappings(BundleContext bundleContext, boolean forceUpdateMapping) {
        Enumeration<URL> predefinedMappings = bundleContext.getBundle().findEntries("META-INF/cxs/mappings", "*.json", true);
        if (predefinedMappings == null) {
            return;
        }
        while (predefinedMappings.hasMoreElements()) {
            URL predefinedMappingURL = predefinedMappings.nextElement();
            logger.info("Found mapping at " + predefinedMappingURL + ", loading... ");
            try {
                final String path = predefinedMappingURL.getPath();
                String name = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
                String mappingSource = loadMappingFile(predefinedMappingURL);

                mappings.put(name, mappingSource);

                if (!createIndex(name)) {
                    logger.info("Found index for type {}", name);
                    if (forceUpdateMapping) {
                        logger.info("Updating mapping for {}", name);
                        createMapping(name, mappingSource);
                    }
                }
            } catch (Exception e) {
                logger.error("Error while loading mapping definition " + predefinedMappingURL, e);
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
            logger.info("Found painless script at " + scriptURL + ", loading... ");
            try (InputStream in = scriptURL.openStream()) {
                String script = IOUtils.toString(in, StandardCharsets.UTF_8);
                String scriptId = FilenameUtils.getBaseName(scriptURL.getPath());
                scriptsById.put(scriptId, script);
            } catch (Exception e) {
                logger.error("Error while loading painless script " + scriptURL, e);
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

    @Override
    public <T extends Item> List<T> getAllItems(final Class<T> clazz) {
        return getAllItems(clazz, 0, -1, null).getList();
    }

    @Override
    public long getAllItemsCount(String itemType) {
        return queryCount(QueryBuilders.matchAllQuery(), itemType);
    }

    @Override
    public <T extends Item> PartialList<T> getAllItems(final Class<T> clazz, int offset, int size, String sortBy) {
        return getAllItems(clazz, offset, size, sortBy, null);
    }

    @Override
    public <T extends Item> PartialList<T> getAllItems(final Class<T> clazz, int offset, int size, String sortBy, String scrollTimeValidity) {
        long startTime = System.currentTimeMillis();
        try {
            return query(QueryBuilders.matchAllQuery(), sortBy, clazz, offset, size, null, scrollTimeValidity);
        } finally {
            if (metricsService != null && metricsService.isActivated()) {
                metricsService.updateTimer(this.getClass().getName() + ".getAllItems", startTime);
            }
        }
    }

    @Override
    public <T extends Item> T load(final String itemId, final Class<T> clazz) {
        return load(itemId, clazz, null);
    }

    @Override
    @Deprecated
    public <T extends Item> T load(final String itemId, final Date dateHint, final Class<T> clazz) {
        return load(itemId, clazz, null);
    }

    @Override
    @Deprecated
    public CustomItem loadCustomItem(final String itemId, final Date dateHint, String customItemType) {
        return load(itemId, CustomItem.class, customItemType);
    }

    @Override
    public CustomItem loadCustomItem(final String itemId, String customItemType) {
        return load(itemId, CustomItem.class, customItemType);
    }

    private <T extends Item> T load(final String itemId, final Class<T> clazz, final String customItemType) {
        if (StringUtils.isEmpty(itemId)) {
            return null;
        }

        return new InClassLoaderExecute<T>(metricsService, this.getClass().getName() + ".loadItem", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected T execute(Object... args) throws Exception {
                try {
                    String itemType = Item.getItemType(clazz);
                    if (customItemType != null) {
                        itemType = customItemType;
                    }
                    String documentId = getDocumentIDForItemType(itemId, itemType);

                    boolean sessionSpecialDirectAccess = sessionLatestIndex != null && Session.ITEM_TYPE.equals(itemType) ;
                    if (!sessionSpecialDirectAccess && isItemTypeRollingOver(itemType)) {
                        return new MetricAdapter<T>(metricsService, ".loadItemWithQuery") {
                            @Override
                            public T execute(Object... args) throws Exception {
                                if (customItemType == null) {
                                    PartialList<T> r = query(QueryBuilders.idsQuery().addIds(documentId), null, clazz, 0, 1, null, null);
                                    if (r.size() > 0) {
                                        return r.get(0);
                                    }
                                } else {
                                    PartialList<CustomItem> r = query(QueryBuilders.idsQuery().addIds(documentId), null, customItemType, 0, 1, null, null);
                                    if (r.size() > 0) {
                                        return (T) r.get(0);
                                    }
                                }
                                return null;
                            }
                        }.execute();
                    } else {
                        // Special handling for session we check the latest available index directly to speed up session loading
                        GetRequest getRequest = new GetRequest(sessionSpecialDirectAccess ? sessionLatestIndex : getIndex(itemType), documentId);
                        GetResponse response = client.get(getRequest, RequestOptions.DEFAULT);
                        if (response.isExists()) {
                            String sourceAsString = response.getSourceAsString();
                            final T value = ESCustomObjectMapper.getObjectMapper().readValue(sourceAsString, clazz);
                            setMetadata(value, response.getId(), response.getVersion(), response.getSeqNo(), response.getPrimaryTerm(), response.getIndex());
                            return value;
                        } else {
                            return null;
                        }
                    }
                } catch (ElasticsearchStatusException ese) {
                    if (ese.status().equals(RestStatus.NOT_FOUND)) {
                        // this can happen if we are just testing the existence of the item, it is not always an error.
                        return null;
                    }
                    throw new Exception("Error loading itemType=" + clazz.getName() + " customItemType=" + customItemType + " itemId=" + itemId, ese);
                } catch (IndexNotFoundException e) {
                    // this can happen if we are just testing the existence of the item, it is not always an error.
                    return null;
                } catch (Exception ex) {
                    throw new Exception("Error loading itemType=" + clazz.getName() + " customItemType=" + customItemType + " itemId=" + itemId, ex);
                }
            }
        }.catchingExecuteInClassLoader(true);

    }

    private void setMetadata(Item item, String itemId, long version, long seqNo, long primaryTerm, String index) {
        if (!systemItems.contains(item.getItemType()) && item.getItemId() == null) {
            item.setItemId(itemId);
        }
        item.setVersion(version);
        item.setSystemMetadata(SEQ_NO, seqNo);
        item.setSystemMetadata(PRIMARY_TERM, primaryTerm);
        item.setSystemMetadata("index", index);
    }

    @Override
    public boolean isConsistent(Item item) {
        return getRefreshPolicy(item.getItemType()) != WriteRequest.RefreshPolicy.NONE;
    }

    @Override
    public boolean save(final Item item) {
        return save(item, useBatchingForSave, alwaysOverwrite);
    }

    @Override
    public boolean save(final Item item, final boolean useBatching) {
        return save(item, useBatching, alwaysOverwrite);
    }

    @Override
    public boolean save(final Item item, final Boolean useBatchingOption, final Boolean alwaysOverwriteOption) {
        final boolean useBatching = useBatchingOption == null ? this.useBatchingForSave : useBatchingOption;
        final boolean alwaysOverwrite = alwaysOverwriteOption == null ? this.alwaysOverwrite : alwaysOverwriteOption;

        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".saveItem", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws Exception {
                try {
                    String source = ESCustomObjectMapper.getObjectMapper().writeValueAsString(item);
                    String itemType = item.getItemType();
                    if (item instanceof CustomItem) {
                        itemType = ((CustomItem) item).getCustomItemType();
                    }
                    String documentId = getDocumentIDForItemType(item.getItemId(), itemType);
                    String index = item.getSystemMetadata("index") != null ? (String) item.getSystemMetadata("index") : getIndex(itemType);

                    IndexRequest indexRequest = new IndexRequest(index);
                    indexRequest.id(documentId);
                    indexRequest.source(source, XContentType.JSON);

                    if (!alwaysOverwrite) {
                        Long seqNo = (Long) item.getSystemMetadata(SEQ_NO);
                        Long primaryTerm = (Long) item.getSystemMetadata(PRIMARY_TERM);

                        if (seqNo != null && primaryTerm != null) {
                            indexRequest.setIfSeqNo(seqNo);
                            indexRequest.setIfPrimaryTerm(primaryTerm);
                        } else {
                            indexRequest.opType(DocWriteRequest.OpType.CREATE);
                        }
                    }

                    if (routingByType.containsKey(itemType)) {
                        indexRequest.routing(routingByType.get(itemType));
                    }

                    try {
                        if (bulkProcessor == null || !useBatching) {
                            indexRequest.setRefreshPolicy(getRefreshPolicy(itemType));
                            IndexResponse response = client.index(indexRequest, RequestOptions.DEFAULT);
                            String responseIndex = response.getIndex();
                            String itemId = response.getId();
                            setMetadata(item, itemId, response.getVersion(), response.getSeqNo(), response.getPrimaryTerm(), responseIndex);

                            // Special handling for session, in case of new session we check that a rollover happen or not to update the latest available index
                            if (Session.ITEM_TYPE.equals(itemType) &&
                                    sessionLatestIndex != null &&
                                    response.getResult().equals(DocWriteResponse.Result.CREATED) &&
                                    !responseIndex.equals(sessionLatestIndex)) {
                                sessionLatestIndex = responseIndex;
                            }
                        } else {
                            bulkProcessor.add(indexRequest);
                        }
                        logMetadataItemOperation("saved", item);
                    } catch (IndexNotFoundException e) {
                        logger.error("Could not find index {}, could not register item type {} with id {} ", index, itemType, item.getItemId(), e);
                        return false;
                    }
                    return true;
                } catch (IOException e) {
                    throw new Exception("Error saving item " + item, e);
                }
            }
        }.catchingExecuteInClassLoader(true);
        if (result == null) {
            return false;
        } else {
            return result;
        }
    }

    @Override
    public boolean update(final Item item, final Date dateHint, final Class clazz, final String propertyName, final Object propertyValue) {
        return update(item, clazz, propertyName, propertyValue);
    }

    @Override
    public boolean update(final Item item, final Date dateHint, final Class clazz, final Map source) {
        return update(item, clazz, source);
    }

    @Override
    public boolean update(final Item item, final Date dateHint, final Class clazz, final Map source, final boolean alwaysOverwrite) {
        return update(item, clazz, source, alwaysOverwrite);
    }

    @Override
    public boolean update(final Item item, final Class clazz, final String propertyName, final Object propertyValue) {
        return update(item, clazz, Collections.singletonMap(propertyName, propertyValue), alwaysOverwrite);
    }


    @Override
    public boolean update(final Item item, final Class clazz, final Map source) {
        return update(item, clazz, source, alwaysOverwrite);
    }

    @Override
    public boolean update(final Item item, final Class clazz, final Map source, final boolean alwaysOverwrite) {
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".updateItem", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws Exception {
                try {
                    UpdateRequest updateRequest = createUpdateRequest(clazz, item, source, alwaysOverwrite);

                    if (bulkProcessor == null || !useBatchingForUpdate) {
                        UpdateResponse response = client.update(updateRequest, RequestOptions.DEFAULT);
                        setMetadata(item, response.getId(), response.getVersion(), response.getSeqNo(), response.getPrimaryTerm(), response.getIndex());
                    } else {
                        bulkProcessor.add(updateRequest);
                    }
                    logMetadataItemOperation("updated", item);
                    return true;
                } catch (IndexNotFoundException e) {
                    throw new Exception("No index found for itemType=" + clazz.getName() + "itemId=" + item.getItemId(), e);
                }
            }
        }.catchingExecuteInClassLoader(true);
        if (result == null) {
            return false;
        } else {
            return result;
        }
    }

    private UpdateRequest createUpdateRequest(Class clazz, Item item, Map source, boolean alwaysOverwrite) {
        String itemType = Item.getItemType(clazz);
        String documentId = getDocumentIDForItemType(item.getItemId(), itemType);
        String index =  item.getSystemMetadata("index") != null ? (String) item.getSystemMetadata("index") : getIndex(itemType);

        UpdateRequest updateRequest = new UpdateRequest(index, documentId);
        updateRequest.doc(source);

        if (!alwaysOverwrite) {
            Long seqNo = (Long) item.getSystemMetadata(SEQ_NO);
            Long primaryTerm = (Long) item.getSystemMetadata(PRIMARY_TERM);

            if (seqNo != null && primaryTerm != null) {
                updateRequest.setIfSeqNo(seqNo);
                updateRequest.setIfPrimaryTerm(primaryTerm);
            }
        }
        return updateRequest;
    }

    @Override
    public List<String> update(final Map<Item, Map> items, final Date dateHint, final Class clazz) {
        if (items.size() == 0)
            return new ArrayList<>();

        List<String> result = new InClassLoaderExecute<List<String>>(metricsService, this.getClass().getName() + ".updateItems", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected List<String> execute(Object... args) throws Exception {
                long batchRequestStartTime = System.currentTimeMillis();

                BulkRequest bulkRequest = new BulkRequest();
                items.forEach((item, source) -> {
                    UpdateRequest updateRequest = createUpdateRequest(clazz, item, source, alwaysOverwrite);
                    bulkRequest.add(updateRequest);
                });

                BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
                logger.debug("{} profiles updated with bulk segment in {}ms", bulkRequest.numberOfActions(), System.currentTimeMillis() - batchRequestStartTime);

                List<String> failedItemsIds = new ArrayList<>();

                if (bulkResponse.hasFailures()) {
                    Iterator<BulkItemResponse> iterator = bulkResponse.iterator();
                    iterator.forEachRemaining(bulkItemResponse -> {
                        failedItemsIds.add(bulkItemResponse.getId());
                    });
                }
                return failedItemsIds;
            }
        }.catchingExecuteInClassLoader(true);

        return result;
    }

    @Override
    public boolean updateWithQueryAndScript(final Date dateHint, final Class<?> clazz, final String[] scripts, final Map<String, Object>[] scriptParams, final Condition[] conditions) {
        return updateWithQueryAndScript(clazz, scripts, scriptParams, conditions);
    }

    @Override
    public boolean updateWithQueryAndScript(final Class<?> clazz, final String[] scripts, final Map<String, Object>[] scriptParams, final Condition[] conditions) {
        Script[] builtScripts = new Script[scripts.length];
        for (int i = 0; i < scripts.length; i++) {
            builtScripts[i] = new Script(ScriptType.INLINE, "painless", scripts[i], scriptParams[i]);
        }
        return updateWithQueryAndScript(new Class<?>[]{clazz}, builtScripts, conditions, true);
    }

    @Override
    public boolean updateWithQueryAndStoredScript(Date dateHint, Class<?> clazz, String[] scripts, Map<String, Object>[] scriptParams, Condition[] conditions) {
        return updateWithQueryAndStoredScript(new Class<?>[]{clazz}, scripts, scriptParams, conditions, true);
    }

    @Override
    public boolean updateWithQueryAndStoredScript(Class<?> clazz, String[] scripts, Map<String, Object>[] scriptParams, Condition[] conditions) {
        return updateWithQueryAndStoredScript(new Class<?>[]{clazz}, scripts, scriptParams, conditions, true);
    }

    @Override
    public boolean updateWithQueryAndStoredScript(Class<?>[] classes, String[] scripts, Map<String, Object>[] scriptParams, Condition[] conditions, boolean waitForComplete) {
        Script[] builtScripts = new Script[scripts.length];
        for (int i = 0; i < scripts.length; i++) {
            builtScripts[i] = new Script(ScriptType.STORED, null, scripts[i], scriptParams[i]);
        }
        return updateWithQueryAndScript(classes, builtScripts, conditions, waitForComplete);
    }

    private boolean updateWithQueryAndScript(final Class<?>[] classes, final Script[] scripts, final Condition[] conditions, boolean waitForComplete) {
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".updateWithQueryAndScript", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws Exception {
                String[] itemTypes = Arrays.stream(classes).map(Item::getItemType).toArray(String[]::new);
                String[] indices = Arrays.stream(itemTypes).map(itemType -> getIndexNameForQuery(itemType)).toArray(String[]::new);

                try {
                    for (int i = 0; i < scripts.length; i++) {
                        RefreshRequest refreshRequest = new RefreshRequest(indices);
                        client.indices().refresh(refreshRequest, RequestOptions.DEFAULT);

                        QueryBuilder queryBuilder = conditionESQueryBuilderDispatcher.buildFilter(conditions[i]);
                        UpdateByQueryRequest updateByQueryRequest = new UpdateByQueryRequest(indices);
                        updateByQueryRequest.setConflicts("proceed");
                        updateByQueryRequest.setMaxRetries(1000);
                        updateByQueryRequest.setSlices(2);
                        updateByQueryRequest.setScript(scripts[i]);
                        updateByQueryRequest.setQuery(wrapWithItemsTypeQuery(itemTypes, queryBuilder));

                        TaskSubmissionResponse taskResponse = client.submitUpdateByQuery(updateByQueryRequest, RequestOptions.DEFAULT);
                        if (taskResponse == null) {
                            logger.error("update with query and script: no response returned for query: {}", queryBuilder);
                        } else if (waitForComplete) {
                            waitForTaskComplete(updateByQueryRequest, taskResponse);
                        } else {
                            logger.debug("ES task started {}", taskResponse.getTask());
                        }
                    }
                    return true;
                } catch (IndexNotFoundException e) {
                    throw new Exception("No index found for itemTypes=" + String.join(",", itemTypes), e);
                } catch (ScriptException e) {
                    logger.error("Error in the update script : {}\n{}\n{}", e.getScript(), e.getDetailedMessage(), e.getScriptStack());
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

    private void waitForTaskComplete(AbstractBulkByScrollRequest request, TaskSubmissionResponse response) {
        if (logger.isDebugEnabled()) {
            logger.debug("Waiting task [{}]: [{}] using query: [{}], polling every {}ms with a timeout configured to {}ms",
                    response.getTask(), request.toString(), request.getSearchRequest().source().query(), taskWaitingPollingInterval, taskWaitingTimeout);
        }
        long start = System.currentTimeMillis();
        new InClassLoaderExecute<Void>(metricsService, this.getClass().getName() + ".waitForTask", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Void execute(Object... args) throws Exception {

                TaskId taskId = new TaskId(response.getTask());
                while (true){
                    Optional<GetTaskResponse> getTaskResponseOptional = client.tasks().get(new GetTaskRequest(taskId.getNodeId(), taskId.getId()), RequestOptions.DEFAULT);
                    if (getTaskResponseOptional.isPresent()) {
                        GetTaskResponse getTaskResponse = getTaskResponseOptional.get();
                        if (getTaskResponse.isCompleted()) {
                            if (logger.isDebugEnabled()) {
                                long millis = getTaskResponse.getTaskInfo().getRunningTimeNanos() / 1_000_000;
                                long seconds = millis / 1000;

                                logger.debug("Waiting task [{}]: Finished in {} {}", taskId,
                                        seconds >= 1 ? seconds : millis,
                                        seconds >= 1 ? "seconds" : "milliseconds");
                            }
                            break;
                        } else {
                            if ((start + taskWaitingTimeout) < System.currentTimeMillis()) {
                                logger.error("Waiting task [{}]: Exceeded configured timeout ({}ms), aborting wait process", taskId, taskWaitingTimeout);
                                break;
                            }

                            try {
                                Thread.sleep(taskWaitingPollingInterval);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException("Waiting task [{}]: interrupted");
                            }
                        }
                    } else {
                        logger.error("Waiting task [{}]: No task found", taskId);
                        break;
                    }
                }
                return null;
            }
        }.catchingExecuteInClassLoader(true);
    }

    @Override
    public boolean storeScripts(Map<String, String> scripts) {
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".storeScripts", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws Exception {
                boolean executedSuccessfully = true;
                for (Map.Entry<String, String> script : scripts.entrySet()) {
                    PutStoredScriptRequest putStoredScriptRequest = new PutStoredScriptRequest();
                    XContentBuilder builder = XContentFactory.jsonBuilder();
                    builder.startObject();
                    {
                        builder.startObject("script");
                        {
                            builder.field("lang", "painless");
                            builder.field("source", script.getValue());
                        }
                        builder.endObject();
                    }
                    builder.endObject();
                    putStoredScriptRequest.content(BytesReference.bytes(builder), XContentType.JSON);
                    putStoredScriptRequest.id(script.getKey());
                    AcknowledgedResponse response = client.putScript(putStoredScriptRequest, RequestOptions.DEFAULT);
                    executedSuccessfully &= response.isAcknowledged();
                    if (response.isAcknowledged()) {
                        logger.info("Successfully stored painless script: {}", script.getKey());
                    } else {
                        logger.error("Failed to store painless script: {}", script.getKey());
                    }
                }
                return executedSuccessfully;
            }
        }.catchingExecuteInClassLoader(true);
        if (result == null) {
            return false;
        } else {
            return result;
        }
    }

    public boolean updateWithScript(final Item item, final Date dateHint, final Class<?> clazz, final String script, final Map<String, Object> scriptParams) {
        return updateWithScript(item, clazz, script, scriptParams);
    }

    @Override
    public boolean updateWithScript(final Item item, final Class<?> clazz, final String script, final Map<String, Object> scriptParams) {
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".updateWithScript", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws Exception {
                try {
                    String itemType = Item.getItemType(clazz);
                    String index = getIndex(itemType);
                    String documentId = getDocumentIDForItemType(item.getItemId(), itemType);

                    Script actualScript = new Script(ScriptType.INLINE, "painless", script, scriptParams);

                    UpdateRequest updateRequest = new UpdateRequest(index, documentId);

                    Long seqNo = (Long) item.getSystemMetadata(SEQ_NO);
                    Long primaryTerm = (Long) item.getSystemMetadata(PRIMARY_TERM);

                    if (seqNo != null && primaryTerm != null) {
                        updateRequest.setIfSeqNo(seqNo);
                        updateRequest.setIfPrimaryTerm(primaryTerm);
                    }
                    updateRequest.script(actualScript);
                    if (bulkProcessor == null) {
                        UpdateResponse response = client.update(updateRequest, RequestOptions.DEFAULT);
                        setMetadata(item, response.getId(), response.getVersion(), response.getSeqNo(), response.getPrimaryTerm(), response.getIndex());
                    } else {
                        bulkProcessor.add(updateRequest);
                    }

                    return true;
                } catch (IndexNotFoundException e) {
                    throw new Exception("No index found for itemType=" + clazz.getName() + "itemId=" + item.getItemId(), e);
                }
            }
        }.catchingExecuteInClassLoader(true);
        if (result == null) {
            return false;
        } else {
            return result;
        }
    }

    @Override
    public <T extends Item> boolean remove(final String itemId, final Class<T> clazz) {
        return remove(itemId, clazz, null);
    }

    @Override
    public boolean removeCustomItem(final String itemId, final String customItemType) {
        return remove(itemId, CustomItem.class, customItemType);
    }

    private <T extends Item> boolean remove(final String itemId, final Class<T> clazz, String customItemType) {
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".removeItem", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws Exception {
                try {
                    String itemType = Item.getItemType(clazz);
                    if (customItemType != null) {
                        itemType = customItemType;
                    }
                    String documentId = getDocumentIDForItemType(itemId, itemType);
                    String index = getIndexNameForQuery(itemType);

                    DeleteRequest deleteRequest = new DeleteRequest(index, documentId);
                    client.delete(deleteRequest, RequestOptions.DEFAULT);
                    if (MetadataItem.class.isAssignableFrom(clazz)) {
                        logger.info("Item of type {} with ID {} has been removed", customItemType != null ? customItemType : clazz.getSimpleName(), itemId);
                    }
                    return true;
                } catch (Exception e) {
                    throw new Exception("Cannot remove", e);
                }
            }
        }.catchingExecuteInClassLoader(true);
        if (result == null) {
            return false;
        } else {
            return result;
        }
    }

    public <T extends Item> boolean removeByQuery(final Condition query, final Class<T> clazz) {
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".removeByQuery", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws Exception {
                QueryBuilder queryBuilder = conditionESQueryBuilderDispatcher.getQueryBuilder(query);
                return removeByQuery(queryBuilder, clazz);
            }
        }.catchingExecuteInClassLoader(true);
        if (result == null) {
            return false;
        } else {
            return result;
        }
    }

    public <T extends Item> boolean removeByQuery(QueryBuilder queryBuilder, final Class<T> clazz) throws Exception {
        try {
            String itemType = Item.getItemType(clazz);
            logger.debug("Remove item of type {} using a query", itemType);
            final DeleteByQueryRequest deleteByQueryRequest = new DeleteByQueryRequest(getIndexNameForQuery(itemType))
                    .setQuery(wrapWithItemTypeQuery(itemType, queryBuilder))
                    // Setting slices to auto will let Elasticsearch choose the number of slices to use.
                    // This setting will use one slice per shard, up to a certain limit.
                    // The delete request will be more efficient and faster than no slicing.
                    .setSlices(AbstractBulkByScrollRequest.AUTO_SLICES)
                    // Elasticsearch takes a snapshot of the index when you hit delete by query request and uses the _version of the documents to process the request.
                    // If a document gets updated in the meantime, it will result in a version conflict error and the delete operation will fail.
                    // So we explicitly set the conflict strategy to proceed in case of version conflict.
                    .setAbortOnVersionConflict(false)
                    // Remove by Query is mostly used for purge and cleaning up old data
                    // It's mostly used in jobs/timed tasks so we don't really care about long request
                    // So we increase default timeout of 1min to 10min
                    .setTimeout(TimeValue.timeValueMinutes(removeByQueryTimeoutInMinutes));

            TaskSubmissionResponse taskResponse = client.submitDeleteByQuery(deleteByQueryRequest, RequestOptions.DEFAULT);

            if (taskResponse == null) {
                logger.error("Remove by query: no response returned for query: {}", queryBuilder);
                return false;
            }

            waitForTaskComplete(deleteByQueryRequest, taskResponse);

            return true;
        } catch (Exception e) {
            throw new Exception("Cannot remove by query", e);
        }
    }

    public boolean indexTemplateExists(final String templateName) {
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".indexTemplateExists", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws IOException {
                IndexTemplatesExistRequest indexTemplatesExistRequest = new IndexTemplatesExistRequest(templateName);
                return client.indices().existsTemplate(indexTemplatesExistRequest, RequestOptions.DEFAULT);
            }
        }.catchingExecuteInClassLoader(true);
        if (result == null) {
            return false;
        } else {
            return result;
        }
    }

    public boolean removeIndexTemplate(final String templateName) {
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".removeIndexTemplate", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws IOException {
                DeleteIndexTemplateRequest deleteIndexTemplateRequest = new DeleteIndexTemplateRequest(templateName);
                AcknowledgedResponse deleteIndexTemplateResponse = client.indices().deleteTemplate(deleteIndexTemplateRequest, RequestOptions.DEFAULT);
                return deleteIndexTemplateResponse.isAcknowledged();
            }
        }.catchingExecuteInClassLoader(true);
        if (result == null) {
            return false;
        } else {
            return result;
        }
    }

    public boolean registerRolloverLifecyclePolicy() {
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".createMonthlyIndexLifecyclePolicy", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws IOException {
                // Create the lifecycle policy for monthly indices
                Map<String, Phase> phases = new HashMap<>();
                Map<String, LifecycleAction> hotActions = new HashMap<>();
                final Long maxDocs = StringUtils.isEmpty(rolloverMaxDocs) ? null : Long.parseLong(rolloverMaxDocs);
                hotActions.put(
                        RolloverAction.NAME,
                        new RolloverAction(
                                StringUtils.isEmpty(rolloverMaxSize) ? null : ByteSizeValue.parseBytesSizeValue(rolloverMaxSize, "rollover.maxSize"),
                                StringUtils.isEmpty(rolloverMaxAge) ? null : TimeValue.parseTimeValue(rolloverMaxAge, null, "rollover.maxAge"),
                                maxDocs
                        )
                );
                phases.put("hot", new Phase("hot", TimeValue.ZERO, hotActions));

                LifecyclePolicy policy = new LifecyclePolicy(indexPrefix + "-" + ROLLOVER_LIFECYCLE_NAME, phases);
                PutLifecyclePolicyRequest request = new PutLifecyclePolicyRequest(policy);
                org.elasticsearch.client.core.AcknowledgedResponse putLifecyclePolicy = client.indexLifecycle().putLifecyclePolicy(request, RequestOptions.DEFAULT);
                return putLifecyclePolicy.isAcknowledged();
            }
        }.catchingExecuteInClassLoader(true);
        if (result == null) {
            return false;
        } else {
            return result;
        }
    }

    public boolean createIndex(final String itemType) {
        logger.debug("Create index {}", itemType);
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".createIndex", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws IOException {
                String index = getIndex(itemType);
                GetIndexRequest getIndexRequest = new GetIndexRequest(index);
                boolean indexExists = client.indices().exists(getIndexRequest, RequestOptions.DEFAULT);

                if (!indexExists) {
                    if (isItemTypeRollingOver(itemType)) {
                        internalCreateRolloverTemplate(itemType);
                        internalCreateRolloverIndex(index);
                    } else {
                        internalCreateIndex(index, mappings.get(itemType));
                    }
                }

                return !indexExists;
            }
        }.catchingExecuteInClassLoader(true);

        if (result == null) {
            return false;
        } else {
            return result;
        }
    }

    public boolean removeIndex(final String itemType) {
        String index = getIndex(itemType);

        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".removeIndex", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws IOException {
                GetIndexRequest getIndexRequest = new GetIndexRequest(index);
                boolean indexExists = client.indices().exists(getIndexRequest, RequestOptions.DEFAULT);
                if (indexExists) {
                    DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(index);
                    client.indices().delete(deleteIndexRequest, RequestOptions.DEFAULT);
                }
                return indexExists;
            }
        }.catchingExecuteInClassLoader(true);

        if (result == null) {
            return false;
        } else {
            return result;
        }
    }

    private void internalCreateRolloverTemplate(String itemName) throws IOException {
        String rolloverAlias = indexPrefix + "-" + itemName;
        PutIndexTemplateRequest putIndexTemplateRequest = new PutIndexTemplateRequest(rolloverAlias + "-rollover-template")
                .patterns(Collections.singletonList(getRolloverIndexForQuery(itemName)))
                .order(1)
                .settings("{\n" +
                        "    \"index\" : {\n" +
                        "        \"number_of_shards\" : " + StringUtils.defaultIfEmpty(rolloverIndexNumberOfShards, monthlyIndexNumberOfShards) + ",\n" +
                        "        \"number_of_replicas\" : " + StringUtils.defaultIfEmpty(rolloverIndexNumberOfReplicas, monthlyIndexNumberOfReplicas) + ",\n" +
                        "        \"mapping.total_fields.limit\" : " + StringUtils.defaultIfEmpty(rolloverIndexMappingTotalFieldsLimit, monthlyIndexMappingTotalFieldsLimit) + ",\n" +
                        "        \"max_docvalue_fields_search\" : " + StringUtils.defaultIfEmpty(rolloverIndexMaxDocValueFieldsSearch, monthlyIndexMaxDocValueFieldsSearch) + ",\n" +
                        "        \"lifecycle.name\": \"" + (indexPrefix + "-" + ROLLOVER_LIFECYCLE_NAME) + "\",\n" +
                        "        \"lifecycle.rollover_alias\": \"" + rolloverAlias + "\"" +
                        "" +
                        "    },\n" +
                        "    \"analysis\": {\n" +
                        "      \"analyzer\": {\n" +
                        "        \"folding\": {\n" +
                        "          \"type\":\"custom\",\n" +
                        "          \"tokenizer\": \"keyword\",\n" +
                        "          \"filter\":  [ \"lowercase\", \"asciifolding\" ]\n" +
                        "        }\n" +
                        "      }\n" +
                        "    }\n" +
                        "}\n", XContentType.JSON);
        if (mappings.get(itemName) == null) {
            logger.warn("Couldn't find mapping for item {}, won't create monthly index template", itemName);
            return;
        }
        putIndexTemplateRequest.mapping(mappings.get(itemName), XContentType.JSON);
        client.indices().putTemplate(putIndexTemplateRequest, RequestOptions.DEFAULT);
    }

    private void internalCreateRolloverIndex(String indexName) throws IOException {
        CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexName + "-000001")
                .alias(new Alias(indexName).writeIndex(true));
        CreateIndexResponse createIndexResponse = client.indices().create(createIndexRequest, RequestOptions.DEFAULT);
        logger.info("Index created: [{}], acknowledge: [{}], shards acknowledge: [{}]", createIndexResponse.index(),
                createIndexResponse.isAcknowledged(), createIndexResponse.isShardsAcknowledged());
    }

    private void internalCreateIndex(String indexName, String mappingSource) throws IOException {
        CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexName);
        createIndexRequest.settings("{\n" +
                "    \"index\" : {\n" +
                "        \"number_of_shards\" : " + numberOfShards + ",\n" +
                "        \"number_of_replicas\" : " + numberOfReplicas + ",\n" +
                "        \"mapping.total_fields.limit\" : " + indexMappingTotalFieldsLimit + ",\n" +
                "        \"max_docvalue_fields_search\" : " + indexMaxDocValueFieldsSearch + "\n" +
                "    },\n" +
                "    \"analysis\": {\n" +
                "      \"analyzer\": {\n" +
                "        \"folding\": {\n" +
                "          \"type\":\"custom\",\n" +
                "          \"tokenizer\": \"keyword\",\n" +
                "          \"filter\":  [ \"lowercase\", \"asciifolding\" ]\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "}\n", XContentType.JSON);

        if (mappingSource != null) {
            createIndexRequest.mapping(mappingSource, XContentType.JSON);
        }
        CreateIndexResponse createIndexResponse = client.indices().create(createIndexRequest, RequestOptions.DEFAULT);
        logger.info("Index created: [{}], acknowledge: [{}], shards acknowledge: [{}]", createIndexResponse.index(),
                createIndexResponse.isAcknowledged(), createIndexResponse.isShardsAcknowledged());
    }

    @Override
    public void createMapping(String type, String source) {
        try {
            putMapping(source, getIndex(type));
        } catch (IOException ioe) {
            logger.error("Error while creating mapping for type " + type + " and source " + source, ioe);
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
                logger.warn("Mapping already exists for type " + itemType + " and property " + property.getItemId());
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
            logger.error("Error while creating mapping for type " + itemType + " and property " + property.getValueTypeId(), ioe);
        }
    }

    private Map<String, Object> createPropertyMapping(final PropertyType property) {
        final String esType = convertValueTypeToESType(property.getValueTypeId());
        final HashMap<String, Object> definition = new HashMap<>();

        if (esType == null) {
            logger.warn("No predefined type found for property[{}], no mapping will be created", property.getValueTypeId());
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

    private boolean putMapping(final String source, final String indexName) throws IOException {
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".putMapping", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws Exception {
                try {
                    PutMappingRequest putMappingRequest = new PutMappingRequest(indexName);
                    putMappingRequest.source(source, XContentType.JSON);
                    AcknowledgedResponse response = client.indices().putMapping(putMappingRequest, RequestOptions.DEFAULT);
                    return response.isAcknowledged();
                } catch (Exception e) {
                    throw new Exception("Cannot create/update mapping", e);
                }
            }
        }.catchingExecuteInClassLoader(true);
        if (result == null) {
            return false;
        } else {
            return result;
        }
    }

    @Override
    public Map<String, Map<String, Object>> getPropertiesMapping(final String itemType) {
        return new InClassLoaderExecute<Map<String, Map<String, Object>>>(metricsService, this.getClass().getName() + ".getPropertiesMapping", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            @SuppressWarnings("unchecked")
            protected Map<String, Map<String, Object>> execute(Object... args) throws Exception {
                // Get all mapping for current itemType
                GetMappingsRequest getMappingsRequest = new GetMappingsRequest();
                getMappingsRequest.indices(getIndexNameForQuery(itemType));
                GetMappingsResponse getMappingsResponse = client.indices().getMapping(getMappingsRequest, RequestOptions.DEFAULT);
                Map<String, MappingMetaData> mappings = getMappingsResponse.mappings();

                // create a list of Keys to get the mappings in chronological order
                // in case there is monthly context then the mapping will be added from the oldest to the most recent one
                Set<String> orderedKeys = new TreeSet<>(mappings.keySet());
                Map<String, Map<String, Object>> result = new HashMap<>();
                try {
                    for (String key : orderedKeys) {
                        if (mappings.containsKey(key)) {
                            Map<String, Map<String, Object>> properties = (Map<String, Map<String, Object>>) mappings.get(key).getSourceAsMap().get("properties");
                            for (Map.Entry<String, Map<String, Object>> entry : properties.entrySet()) {
                                if (result.containsKey(entry.getKey())) {
                                    Map<String, Object> subResult = result.get(entry.getKey());

                                    for (Map.Entry<String, Object> subentry : entry.getValue().entrySet()) {
                                        if (subResult.containsKey(subentry.getKey())
                                                && subResult.get(subentry.getKey()) instanceof Map
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

    private void mergePropertiesMapping(Map<String, Object> result, Map<String, Object> entry) {
        if (entry == null || entry.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> subentry : entry.entrySet()) {
            if (result.containsKey(subentry.getKey())
                    && result.get(subentry.getKey()) instanceof Map
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
        if (propertyMapping != null
                && "text".equals(propertyMapping.get("type"))
                && propertyMapping.containsKey("fields")
                && ((Map) propertyMapping.get("fields")).containsKey("keyword")) {
            name += ".keyword";
        }
        return name;
    }

    public boolean saveQuery(final String queryName, final String query) {
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".saveQuery", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws Exception {
                //Index the query = register it in the percolator
                try {
                    logger.info("Saving query : " + queryName);
                    String index = getIndex(".percolator");
                    IndexRequest indexRequest = new IndexRequest(index);
                    indexRequest.id(queryName);
                    indexRequest.source(query, XContentType.JSON);
                    indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                    client.index(indexRequest, RequestOptions.DEFAULT);
                    return true;
                } catch (Exception e) {
                    throw new Exception("Cannot save query", e);
                }
            }
        }.catchingExecuteInClassLoader(true);
        if (result == null) {
            return false;
        } else {
            return result;
        }
    }

    @Override
    public boolean saveQuery(String queryName, Condition query) {
        if (query == null) {
            return false;
        }
        saveQuery(queryName, conditionESQueryBuilderDispatcher.getQuery(query));
        return true;
    }

    @Override
    public boolean removeQuery(final String queryName) {
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".removeQuery", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws Exception {
                //Index the query = register it in the percolator
                try {
                    String index = getIndex(".percolator");
                    DeleteRequest deleteRequest = new DeleteRequest(index);
                    deleteRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                    client.delete(deleteRequest, RequestOptions.DEFAULT);
                    return true;
                } catch (Exception e) {
                    throw new Exception("Cannot delete query", e);
                }
            }
        }.catchingExecuteInClassLoader(true);
        if (result == null) {
            return false;
        } else {
            return result;
        }
    }

    @Override
    public boolean isValidCondition(Condition condition, Item item) {
        try {
            conditionEvaluatorDispatcher.eval(condition, item);
            QueryBuilders.boolQuery()
                    .must(QueryBuilders.idsQuery().addIds(item.getItemId()))
                    .must(conditionESQueryBuilderDispatcher.buildFilter(condition));
        } catch (Exception e) {
            logger.error("Failed to validate condition. See debug log level for more information");
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to validate condition, condition={}", condition, e);
            }

            return false;
        }
        return true;
    }

    @Override
    public boolean testMatch(Condition query, Item item) {
        long startTime = System.currentTimeMillis();
        try {
            return conditionEvaluatorDispatcher.eval(query, item);
        } catch (UnsupportedOperationException e) {
            logger.error("Eval not supported, continue with query", e);
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

            QueryBuilder builder = QueryBuilders.boolQuery()
                    .must(QueryBuilders.idsQuery().addIds(documentId))
                    .must(conditionESQueryBuilderDispatcher.buildFilter(query));
            return queryCount(builder, itemType) > 0;
        } finally {
            if (metricsService != null && metricsService.isActivated()) {
                metricsService.updateTimer(this.getClass().getName() + ".testMatchInElasticSearch", startTime);
            }
        }
    }


    @Override
    public <T extends Item> List<T> query(final Condition query, String sortBy, final Class<T> clazz) {
        return query(query, sortBy, clazz, 0, -1).getList();
    }

    @Override
    public <T extends Item> PartialList<T> query(final Condition query, String sortBy, final Class<T> clazz, final int offset, final int size) {
        return query(conditionESQueryBuilderDispatcher.getQueryBuilder(query), sortBy, clazz, offset, size, null, null);
    }

    @Override
    public <T extends Item> PartialList<T> query(final Condition query, String sortBy, final Class<T> clazz, final int offset, final int size, final String scrollTimeValidity) {
        return query(conditionESQueryBuilderDispatcher.getQueryBuilder(query), sortBy, clazz, offset, size, null, scrollTimeValidity);
    }

    @Override
    public PartialList<CustomItem> queryCustomItem(final Condition query, String sortBy, final String customItemType, final int offset, final int size, final String scrollTimeValidity) {
        return query(conditionESQueryBuilderDispatcher.getQueryBuilder(query), sortBy, customItemType, offset, size, null, scrollTimeValidity);
    }

    @Override
    public <T extends Item> PartialList<T> queryFullText(final String fulltext, final Condition query, String sortBy, final Class<T> clazz, final int offset, final int size) {
        return query(QueryBuilders.boolQuery().must(QueryBuilders.queryStringQuery(fulltext)).must(conditionESQueryBuilderDispatcher.getQueryBuilder(query)), sortBy, clazz, offset, size, null, null);
    }

    @Override
    public <T extends Item> List<T> query(final String fieldName, final String fieldValue, String sortBy, final Class<T> clazz) {
        return query(fieldName, fieldValue, sortBy, clazz, 0, -1).getList();
    }

    @Override
    public <T extends Item> List<T> query(final String fieldName, final String[] fieldValues, String sortBy, final Class<T> clazz) {
        return query(QueryBuilders.termsQuery(fieldName, ConditionContextHelper.foldToASCII(fieldValues)), sortBy, clazz, 0, -1, getRouting(fieldName, fieldValues, clazz), null).getList();
    }

    @Override
    public <T extends Item> PartialList<T> query(String fieldName, String fieldValue, String sortBy, Class<T> clazz, int offset, int size) {
        return query(termQuery(fieldName, ConditionContextHelper.foldToASCII(fieldValue)), sortBy, clazz, offset, size, getRouting(fieldName, new String[]{fieldValue}, clazz), null);
    }

    @Override
    public <T extends Item> PartialList<T> queryFullText(String fieldName, String fieldValue, String fulltext, String sortBy, Class<T> clazz, int offset, int size) {
        return query(QueryBuilders.boolQuery().must(QueryBuilders.queryStringQuery(fulltext)).must(termQuery(fieldName, fieldValue)), sortBy, clazz, offset, size, getRouting(fieldName, new String[]{fieldValue}, clazz), null);
    }

    @Override
    public <T extends Item> PartialList<T> queryFullText(String fulltext, String sortBy, Class<T> clazz, int offset, int size) {
        return query(QueryBuilders.queryStringQuery(fulltext), sortBy, clazz, offset, size, null, null);
    }

    @Override
    public <T extends Item> PartialList<T> rangeQuery(String fieldName, String from, String to, String sortBy, Class<T> clazz, int offset, int size) {
        RangeQueryBuilder builder = QueryBuilders.rangeQuery(fieldName);
        builder.from(from);
        builder.to(to);
        return query(builder, sortBy, clazz, offset, size, null, null);
    }

    @Override
    public long queryCount(Condition query, String itemType) {
        try {
            return conditionESQueryBuilderDispatcher.count(query);
        } catch (UnsupportedOperationException e) {
            try {
                QueryBuilder filter = conditionESQueryBuilderDispatcher.buildFilter(query);
                if (filter instanceof IdsQueryBuilder) {
                    return ((IdsQueryBuilder) filter).ids().size();
                }
                return queryCount(filter, itemType);
            } catch (UnsupportedOperationException e1) {
                return -1;
            }
        }
    }

    private long queryCount(final QueryBuilder filter, final String itemType) {
        return new InClassLoaderExecute<Long>(metricsService, this.getClass().getName() + ".queryCount", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {

            @Override
            protected Long execute(Object... args) throws IOException {

                CountRequest countRequest = new CountRequest(getIndexNameForQuery(itemType));
                SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
                searchSourceBuilder.query(wrapWithItemTypeQuery(itemType, filter));
                countRequest.source(searchSourceBuilder);
                CountResponse response = client.count(countRequest, RequestOptions.DEFAULT);
                return response.getCount();
            }
        }.catchingExecuteInClassLoader(true);
    }

    private <T extends Item> PartialList<T> query(final QueryBuilder query, final String sortBy, final Class<T> clazz, final int offset, final int size, final String[] routing, final String scrollTimeValidity) {
        return query(query, sortBy, clazz, null, offset, size, routing, scrollTimeValidity);
    }

    private PartialList<CustomItem> query(final QueryBuilder query, final String sortBy, final String customItemType, final int offset, final int size, final String[] routing, final String scrollTimeValidity) {
        return query(query, sortBy, CustomItem.class, customItemType, offset, size, routing, scrollTimeValidity);
    }

    private <T extends Item> PartialList<T> query(final QueryBuilder query, final String sortBy, final Class<T> clazz, final String customItemType, final int offset, final int size, final String[] routing, final String scrollTimeValidity) {
        return new InClassLoaderExecute<PartialList<T>>(metricsService, this.getClass().getName() + ".query", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {

            @Override
            protected PartialList<T> execute(Object... args) throws Exception {
                List<T> results = new ArrayList<T>();
                String scrollIdentifier = null;
                long totalHits = 0;
                PartialList.Relation totalHitsRelation = PartialList.Relation.EQUAL;
                try {
                    String itemType = Item.getItemType(clazz);
                    if (customItemType != null) {
                        itemType = customItemType;
                    }
                    TimeValue keepAlive = TimeValue.timeValueHours(1);
                    SearchRequest searchRequest = new SearchRequest(getIndexNameForQuery(itemType));
                    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                            .fetchSource(true)
                            .seqNoAndPrimaryTerm(true)
                            .query(wrapWithItemTypeQuery(itemType, query))
                            .size(size < 0 ? defaultQueryLimit : size)
                            .from(offset);
                    if (scrollTimeValidity != null) {
                        keepAlive = TimeValue.parseTimeValue(scrollTimeValidity, TimeValue.timeValueHours(1), "scrollTimeValidity");
                        searchRequest.scroll(keepAlive);
                    }

                    if (size == Integer.MIN_VALUE) {
                        searchSourceBuilder.size(defaultQueryLimit);
                    } else if (size != -1) {
                        searchSourceBuilder.size(size);
                    } else {
                        // size == -1, use scroll query to retrieve all the results
                        searchRequest.scroll(keepAlive);
                    }
                    if (routing != null) {
                        searchRequest.routing(routing);
                    }
                    if (sortBy != null) {
                        String[] sortByArray = sortBy.split(",");
                        for (String sortByElement : sortByArray) {
                            if (sortByElement.startsWith("geo:")) {
                                String[] elements = sortByElement.split(":");
                                GeoDistanceSortBuilder distanceSortBuilder = SortBuilders.geoDistanceSort(elements[1], Double.parseDouble(elements[2]), Double.parseDouble(elements[3])).unit(DistanceUnit.KILOMETERS);
                                if (elements.length > 4 && elements[4].equals("desc")) {
                                    searchSourceBuilder.sort(distanceSortBuilder.order(SortOrder.DESC));
                                } else {
                                    searchSourceBuilder.sort(distanceSortBuilder.order(SortOrder.ASC));
                                }
                            } else {
                                String name = getPropertyNameWithData(StringUtils.substringBeforeLast(sortByElement, ":"), itemType);
                                if (name != null) {
                                    if (sortByElement.endsWith(":desc")) {
                                        searchSourceBuilder.sort(name, SortOrder.DESC);
                                    } else {
                                        searchSourceBuilder.sort(name, SortOrder.ASC);
                                    }
                                } else {
                                    // in the case of no data existing for the property, we will not add the sorting to the request.
                                }

                            }
                        }
                    }
                    searchSourceBuilder.version(true);
                    searchRequest.source(searchSourceBuilder);
                    SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);

                    if (size == -1) {
                        // Scroll until no more hits are returned
                        while (true) {

                            for (SearchHit searchHit : response.getHits().getHits()) {
                                // add hit to results
                                String sourceAsString = searchHit.getSourceAsString();
                                final T value = ESCustomObjectMapper.getObjectMapper().readValue(sourceAsString, clazz);
                                setMetadata(value, searchHit.getId(), searchHit.getVersion(), searchHit.getSeqNo(), searchHit.getPrimaryTerm(), searchHit.getIndex());
                                results.add(value);
                            }

                            SearchScrollRequest searchScrollRequest = new SearchScrollRequest(response.getScrollId());
                            searchScrollRequest.scroll(keepAlive);
                            response = client.scroll(searchScrollRequest, RequestOptions.DEFAULT);

                            // If we have no more hits, exit
                            if (response.getHits().getHits().length == 0) {
                                break;
                            }
                        }
                        ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
                        clearScrollRequest.addScrollId(response.getScrollId());
                        client.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
                    } else {
                        SearchHits searchHits = response.getHits();
                        scrollIdentifier = response.getScrollId();
                        totalHits = searchHits.getTotalHits().value;
                        totalHitsRelation = getTotalHitsRelation(searchHits.getTotalHits());
                        if (scrollIdentifier != null && totalHits == 0) {
                            // we have no results, we must clear the scroll request immediately.
                            ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
                            clearScrollRequest.addScrollId(response.getScrollId());
                            client.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
                        }
                        for (SearchHit searchHit : searchHits) {
                            String sourceAsString = searchHit.getSourceAsString();
                            final T value = ESCustomObjectMapper.getObjectMapper().readValue(sourceAsString, clazz);
                            setMetadata(value, searchHit.getId(), searchHit.getVersion(), searchHit.getSeqNo(), searchHit.getPrimaryTerm(), searchHit.getIndex());
                            results.add(value);
                        }
                    }
                } catch (Exception t) {
                    throw new Exception("Error loading itemType=" + clazz.getName() + " query=" + query + " sortBy=" + sortBy, t);
                }

                PartialList<T> result = new PartialList<T>(results, offset, size, totalHits, totalHitsRelation);
                if (scrollIdentifier != null && totalHits != 0) {
                    result.setScrollIdentifier(scrollIdentifier);
                    result.setScrollTimeValidity(scrollTimeValidity);
                }
                return result;
            }
        }.catchingExecuteInClassLoader(true);
    }

    private PartialList.Relation getTotalHitsRelation(TotalHits totalHits) {
        return TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO.equals(totalHits.relation) ? PartialList.Relation.GREATER_THAN_OR_EQUAL_TO : PartialList.Relation.EQUAL;
    }

    @Override
    public <T extends Item> PartialList<T> continueScrollQuery(final Class<T> clazz, final String scrollIdentifier, final String scrollTimeValidity) {
        return new InClassLoaderExecute<PartialList<T>>(metricsService, this.getClass().getName() + ".continueScrollQuery", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {

            @Override
            protected PartialList<T> execute(Object... args) throws Exception {
                List<T> results = new ArrayList<T>();
                long totalHits = 0;
                try {
                    TimeValue keepAlive = TimeValue.parseTimeValue(scrollTimeValidity, TimeValue.timeValueMinutes(10), "scrollTimeValidity");

                    SearchScrollRequest searchScrollRequest = new SearchScrollRequest(scrollIdentifier);
                    searchScrollRequest.scroll(keepAlive);
                    SearchResponse response = client.scroll(searchScrollRequest, RequestOptions.DEFAULT);

                    if (response.getHits().getHits().length == 0) {
                        ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
                        clearScrollRequest.addScrollId(response.getScrollId());
                        client.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
                    } else {
                        for (SearchHit searchHit : response.getHits().getHits()) {
                            // add hit to results
                            String sourceAsString = searchHit.getSourceAsString();
                            final T value = ESCustomObjectMapper.getObjectMapper().readValue(sourceAsString, clazz);
                            setMetadata(value, searchHit.getId(), searchHit.getVersion(), searchHit.getSeqNo(), searchHit.getPrimaryTerm(), searchHit.getIndex());
                            results.add(value);
                        }
                    }
                    PartialList<T> result = new PartialList<T>(results, 0, response.getHits().getHits().length, response.getHits().getTotalHits().value, getTotalHitsRelation(response.getHits().getTotalHits()));
                    if (scrollIdentifier != null) {
                        result.setScrollIdentifier(scrollIdentifier);
                        result.setScrollTimeValidity(scrollTimeValidity);
                    }
                    return result;
                } catch (Exception t) {
                    throw new Exception("Error continuing scrolling query for itemType=" + clazz.getName() + " scrollIdentifier=" + scrollIdentifier + " scrollTimeValidity=" + scrollTimeValidity, t);
                }
            }
        }.catchingExecuteInClassLoader(true);
    }

    @Override
    public PartialList<CustomItem> continueCustomItemScrollQuery(final String customItemType, final String scrollIdentifier, final String scrollTimeValidity) {
        return new InClassLoaderExecute<PartialList<CustomItem>>(metricsService, this.getClass().getName() + ".continueScrollQuery", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {

            @Override
            protected PartialList<CustomItem> execute(Object... args) throws Exception {
                List<CustomItem> results = new ArrayList<CustomItem>();
                long totalHits = 0;
                try {
                    TimeValue keepAlive = TimeValue.parseTimeValue(scrollTimeValidity, TimeValue.timeValueMinutes(10), "scrollTimeValidity");

                    SearchScrollRequest searchScrollRequest = new SearchScrollRequest(scrollIdentifier);
                    searchScrollRequest.scroll(keepAlive);
                    SearchResponse response = client.scroll(searchScrollRequest, RequestOptions.DEFAULT);

                    if (response.getHits().getHits().length == 0) {
                        ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
                        clearScrollRequest.addScrollId(response.getScrollId());
                        client.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
                    } else {
                        for (SearchHit searchHit : response.getHits().getHits()) {
                            // add hit to results
                            String sourceAsString = searchHit.getSourceAsString();
                            final CustomItem value = ESCustomObjectMapper.getObjectMapper().readValue(sourceAsString, CustomItem.class);
                            setMetadata(value, searchHit.getId(), searchHit.getVersion(), searchHit.getSeqNo(), searchHit.getPrimaryTerm(), searchHit.getIndex());
                            results.add(value);
                        }
                    }
                    PartialList<CustomItem> result = new PartialList<CustomItem>(results, 0, response.getHits().getHits().length, response.getHits().getTotalHits().value, getTotalHitsRelation(response.getHits().getTotalHits()));
                    if (scrollIdentifier != null) {
                        result.setScrollIdentifier(scrollIdentifier);
                        result.setScrollTimeValidity(scrollTimeValidity);
                    }
                    return result;
                } catch (Exception t) {
                    throw new Exception("Error continuing scrolling query for itemType=" + customItemType + " scrollIdentifier=" + scrollIdentifier + " scrollTimeValidity=" + scrollTimeValidity, t);
                }
            }
        }.catchingExecuteInClassLoader(true);
    }

    /**
     * @deprecated As of version 1.3.0-incubating, use {@link #aggregateWithOptimizedQuery(Condition, BaseAggregate, String)} instead
     */
    @Deprecated
    @Override
    public Map<String, Long> aggregateQuery(Condition filter, BaseAggregate aggregate, String itemType) {
        return aggregateQuery(filter, aggregate, itemType, false, aggregateQueryBucketSize);
    }

    @Override
    public Map<String, Long> aggregateWithOptimizedQuery(Condition filter, BaseAggregate aggregate, String itemType) {
        return aggregateQuery(filter, aggregate, itemType, true, aggregateQueryBucketSize);
    }

    @Override
    public Map<String, Long> aggregateWithOptimizedQuery(Condition filter, BaseAggregate aggregate, String itemType, int size) {
        return aggregateQuery(filter, aggregate, itemType, true, size);
    }

    private Map<String, Long> aggregateQuery(final Condition filter, final BaseAggregate aggregate, final String itemType,
                                             final boolean optimizedQuery, int queryBucketSize) {
        return new InClassLoaderExecute<Map<String, Long>>(metricsService, this.getClass().getName() + ".aggregateQuery", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {

            @Override
            protected Map<String, Long> execute(Object... args) throws IOException {
                Map<String, Long> results = new LinkedHashMap<String, Long>();

                SearchRequest searchRequest = new SearchRequest(getIndexNameForQuery(itemType));
                SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
                searchSourceBuilder.size(0);
                MatchAllQueryBuilder matchAll = QueryBuilders.matchAllQuery();
                boolean isItemTypeSharingIndex = isItemTypeSharingIndex(itemType);
                searchSourceBuilder.query(isItemTypeSharingIndex ? getItemTypeQueryBuilder(itemType) : matchAll);
                List<AggregationBuilder> lastAggregation = new ArrayList<AggregationBuilder>();

                if (aggregate != null) {
                    AggregationBuilder bucketsAggregation = null;
                    String fieldName = aggregate.getField();
                    if (aggregate instanceof DateAggregate) {
                        DateAggregate dateAggregate = (DateAggregate) aggregate;
                        DateHistogramAggregationBuilder dateHistogramBuilder = AggregationBuilders.dateHistogram("buckets").field(fieldName).calendarInterval(new DateHistogramInterval((dateAggregate.getInterval())));
                        if (dateAggregate.getFormat() != null) {
                            dateHistogramBuilder.format(dateAggregate.getFormat());
                        }
                        bucketsAggregation = dateHistogramBuilder;
                    } else if (aggregate instanceof NumericRangeAggregate) {
                        RangeAggregationBuilder rangebuilder = AggregationBuilders.range("buckets").field(fieldName);
                        for (NumericRange range : ((NumericRangeAggregate) aggregate).getRanges()) {
                            if (range != null) {
                                if (range.getFrom() != null && range.getTo() != null) {
                                    rangebuilder.addRange(range.getKey(), range.getFrom(), range.getTo());
                                } else if (range.getFrom() != null) {
                                    rangebuilder.addUnboundedFrom(range.getKey(), range.getFrom());
                                } else if (range.getTo() != null) {
                                    rangebuilder.addUnboundedTo(range.getKey(), range.getTo());
                                }
                            }
                        }
                        bucketsAggregation = rangebuilder;
                    } else if (aggregate instanceof DateRangeAggregate) {
                        DateRangeAggregate dateRangeAggregate = (DateRangeAggregate) aggregate;
                        DateRangeAggregationBuilder rangebuilder = AggregationBuilders.dateRange("buckets").field(fieldName);
                        if (dateRangeAggregate.getFormat() != null) {
                            rangebuilder.format(dateRangeAggregate.getFormat());
                        }
                        for (DateRange range : dateRangeAggregate.getDateRanges()) {
                            if (range != null) {
                                rangebuilder.addRange(range.getKey(), range.getFrom() != null ? range.getFrom().toString() : null, range.getTo() != null ? range.getTo().toString() : null);
                            }
                        }
                        bucketsAggregation = rangebuilder;
                    } else if (aggregate instanceof IpRangeAggregate) {
                        IpRangeAggregate ipRangeAggregate = (IpRangeAggregate) aggregate;
                        IpRangeAggregationBuilder rangebuilder = AggregationBuilders.ipRange("buckets").field(fieldName);
                        for (IpRange range : ipRangeAggregate.getRanges()) {
                            if (range != null) {
                                rangebuilder.addRange(range.getKey(), range.getFrom(), range.getTo());
                            }
                        }
                        bucketsAggregation = rangebuilder;
                    } else {
                        fieldName = getPropertyNameWithData(fieldName, itemType);
                        //default
                        if (fieldName != null) {
                            bucketsAggregation = AggregationBuilders.terms("buckets").field(fieldName).size(queryBucketSize);
                            if (aggregate instanceof TermsAggregate) {
                                TermsAggregate termsAggregate = (TermsAggregate) aggregate;
                                if (termsAggregate.getPartition() > -1 && termsAggregate.getNumPartitions() > -1) {
                                    ((TermsAggregationBuilder) bucketsAggregation).includeExclude(new IncludeExclude(termsAggregate.getPartition(), termsAggregate.getNumPartitions()));
                                }
                            }
                        } else {
                            // field name could be null if no existing data exists
                        }
                    }
                    if (bucketsAggregation != null) {
                        final MissingAggregationBuilder missingBucketsAggregation = AggregationBuilders.missing("missing").field(fieldName);
                        for (AggregationBuilder aggregationBuilder : lastAggregation) {
                            bucketsAggregation.subAggregation(aggregationBuilder);
                            missingBucketsAggregation.subAggregation(aggregationBuilder);
                        }
                        lastAggregation = Arrays.asList(bucketsAggregation, missingBucketsAggregation);
                    }
                }

                // If the request is optimized then we don't need a global aggregation which is very slow and we can put the query with a
                // filter on range items in the query block so we don't retrieve all the document before filtering the whole
                if (optimizedQuery) {
                    for (AggregationBuilder aggregationBuilder : lastAggregation) {
                        searchSourceBuilder.aggregation(aggregationBuilder);
                    }

                    if (filter != null) {
                        searchSourceBuilder.query(wrapWithItemTypeQuery(itemType, conditionESQueryBuilderDispatcher.buildFilter(filter)));
                    }
                } else {
                    if (filter != null) {
                        AggregationBuilder filterAggregation = AggregationBuilders.filter("filter",
                                wrapWithItemTypeQuery(itemType, conditionESQueryBuilderDispatcher.buildFilter(filter)));
                        for (AggregationBuilder aggregationBuilder : lastAggregation) {
                            filterAggregation.subAggregation(aggregationBuilder);
                        }
                        lastAggregation = Collections.singletonList(filterAggregation);
                    }

                    AggregationBuilder globalAggregation = AggregationBuilders.global("global");
                    for (AggregationBuilder aggregationBuilder : lastAggregation) {
                        globalAggregation.subAggregation(aggregationBuilder);
                    }

                    searchSourceBuilder.aggregation(globalAggregation);
                }

                searchRequest.source(searchSourceBuilder);

                RequestOptions.Builder builder = RequestOptions.DEFAULT.toBuilder();

                if (aggQueryMaxResponseSizeHttp != null) {
                    builder.setHttpAsyncResponseConsumerFactory(
                            new HttpAsyncResponseConsumerFactory
                                    .HeapBufferedResponseConsumerFactory(aggQueryMaxResponseSizeHttp));
                }

                SearchResponse response = client.search(searchRequest, builder.build());
                Aggregations aggregations = response.getAggregations();


                if (aggregations != null) {

                    if (optimizedQuery) {
                        if (response.getHits() != null) {
                            results.put("_filtered", response.getHits().getTotalHits().value);
                        }
                    } else {
                        Global globalAgg = aggregations.get("global");
                        results.put("_all", globalAgg.getDocCount());
                        aggregations = globalAgg.getAggregations();

                        if (aggregations.get("filter") != null) {
                            Filter filterAgg = aggregations.get("filter");
                            results.put("_filtered", filterAgg.getDocCount());
                            aggregations = filterAgg.getAggregations();
                        }
                    }
                    if (aggregations.get("buckets") != null) {

                        if (aggQueryThrowOnMissingDocs) {
                            if (aggregations.get("buckets") instanceof Terms) {
                                Terms terms = aggregations.get("buckets");
                                if (terms.getDocCountError() > 0 || terms.getSumOfOtherDocCounts() > 0) {
                                    throw new UnsupportedOperationException("Some docs are missing in aggregation query. docCountError is:" +
                                            terms.getDocCountError() + " sumOfOtherDocCounts:" + terms.getSumOfOtherDocCounts());
                                }
                            }
                        }

                        long totalDocCount = 0;
                        MultiBucketsAggregation terms = aggregations.get("buckets");
                        for (MultiBucketsAggregation.Bucket bucket : terms.getBuckets()) {
                            results.put(bucket.getKeyAsString(), bucket.getDocCount());
                            totalDocCount += bucket.getDocCount();
                        }
                        SingleBucketAggregation missing = aggregations.get("missing");
                        if (missing.getDocCount() > 0) {
                            results.put("_missing", missing.getDocCount());
                            totalDocCount += missing.getDocCount();
                        }
                        if (response.getHits() != null && TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO.equals(response.getHits().getTotalHits().relation)) {
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

    @Override
    public void refresh() {
        new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".refresh", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) {
                if (bulkProcessor != null) {
                    bulkProcessor.flush();
                }
                try {
                    client.indices().refresh(Requests.refreshRequest(), RequestOptions.DEFAULT);
                } catch (IOException e) {
                    e.printStackTrace();//TODO manage ES7
                }
                return true;
            }
        }.catchingExecuteInClassLoader(true);
    }

    @Override
    public <T extends Item> void refreshIndex(Class<T> clazz, Date dateHint) {
        new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".refreshIndex", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) {
                try {
                    String itemType = Item.getItemType(clazz);
                    String index = getIndex(itemType);
                    client.indices().refresh(Requests.refreshRequest(index), RequestOptions.DEFAULT);
                } catch (IOException e) {
                    e.printStackTrace();//TODO manage ES7
                }
                return true;
            }
        }.catchingExecuteInClassLoader(true);
    }


    @Override
    public void purge(final Date date) {
        // nothing, this method is deprecated since 2.2.0
    }

    @Override
    public <T extends Item> void purgeTimeBasedItems(int existsNumberOfDays, Class<T> clazz) {
        new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".purgeTimeBasedItems", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws Exception {
                String itemType = Item.getItemType(clazz);

                if (existsNumberOfDays > 0 && isItemTypeRollingOver(itemType)) {
                    // First we purge the documents
                    removeByQuery(QueryBuilders.rangeQuery("timeStamp").lte("now-" + existsNumberOfDays + "d"), clazz);

                    // get count per index for those time based data
                    TreeMap<String, Long> countsPerIndex = new TreeMap<>();
                    GetIndexResponse getIndexResponse = client.indices().get(new GetIndexRequest(getIndexNameForQuery(itemType)), RequestOptions.DEFAULT);
                    for (String index : getIndexResponse.getIndices()) {
                        countsPerIndex.put(index, client.count(new CountRequest(index), RequestOptions.DEFAULT).getCount());
                    }

                    // Check for count=0 and remove them
                    if (countsPerIndex.size() >= 1) {
                        // do not check the last index, because it's the one used to write documents
                        countsPerIndex.pollLastEntry();

                        for (Map.Entry<String, Long> indexCount : countsPerIndex.entrySet()) {
                            if (indexCount.getValue() == 0) {
                                client.indices().delete(new DeleteIndexRequest(indexCount.getKey()), RequestOptions.DEFAULT);
                            }
                        }
                    }
                }

                return true;
            }
        }.catchingExecuteInClassLoader(true);
    }

    @Override
    public void purge(final String scope) {
        logger.debug("Purge scope {}", scope);
        new InClassLoaderExecute<Void>(metricsService, this.getClass().getName() + ".purgeWithScope", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            @Override
            protected Void execute(Object... args) throws IOException {
                QueryBuilder query = termQuery("scope", scope);

                BulkRequest deleteByScopeBulkRequest = new BulkRequest();

                final TimeValue keepAlive = TimeValue.timeValueHours(1);
                SearchRequest searchRequest = new SearchRequest(getAllIndexForQuery()).scroll(keepAlive);
                SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                        .query(query)
                        .size(100);
                searchRequest.source(searchSourceBuilder);
                SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);

                // Scroll until no more hits are returned
                while (true) {

                    for (SearchHit hit : response.getHits().getHits()) {
                        // add hit to bulk delete
                        DeleteRequest deleteRequest = new DeleteRequest(hit.getIndex(), hit.getId());
                        deleteByScopeBulkRequest.add(deleteRequest);
                    }

                    SearchScrollRequest searchScrollRequest = new SearchScrollRequest(response.getScrollId());
                    searchScrollRequest.scroll(keepAlive);
                    response = client.scroll(searchScrollRequest, RequestOptions.DEFAULT);

                    // If we have no more hits, exit
                    if (response.getHits().getHits().length == 0) {
                        ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
                        clearScrollRequest.addScrollId(response.getScrollId());
                        client.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
                        break;
                    }
                }

                // we're done with the scrolling, delete now
                if (deleteByScopeBulkRequest.numberOfActions() > 0) {
                    final BulkResponse deleteResponse = client.bulk(deleteByScopeBulkRequest, RequestOptions.DEFAULT);
                    if (deleteResponse.hasFailures()) {
                        // do something
                        logger.warn("Couldn't delete from scope " + scope + ":\n{}", deleteResponse.buildFailureMessage());
                    }
                }
                return null;
            }
        }.catchingExecuteInClassLoader(true);
    }

    @Override
    public Map<String, Double> getSingleValuesMetrics(final Condition condition, final String[] metrics, final String field, final String itemType) {
        return new InClassLoaderExecute<Map<String, Double>>(metricsService, this.getClass().getName() + ".getSingleValuesMetrics", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {

            @Override
            protected Map<String, Double> execute(Object... args) throws IOException {
                Map<String, Double> results = new LinkedHashMap<String, Double>();

                SearchRequest searchRequest = new SearchRequest(getIndexNameForQuery(itemType));
                SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                        .size(0)
                        .query(isItemTypeSharingIndex(itemType) ? getItemTypeQueryBuilder(itemType) : QueryBuilders.matchAllQuery());

                AggregationBuilder filterAggregation = AggregationBuilders.filter("metrics", conditionESQueryBuilderDispatcher.buildFilter(condition));

                if (metrics != null) {
                    for (String metric : metrics) {
                        switch (metric) {
                            case "sum":
                                filterAggregation.subAggregation(AggregationBuilders.sum("sum").field(field));
                                break;
                            case "avg":
                                filterAggregation.subAggregation(AggregationBuilders.avg("avg").field(field));
                                break;
                            case "min":
                                filterAggregation.subAggregation(AggregationBuilders.min("min").field(field));
                                break;
                            case "max":
                                filterAggregation.subAggregation(AggregationBuilders.max("max").field(field));
                                break;
                            case "card":
                                filterAggregation.subAggregation(AggregationBuilders.cardinality("card").field(field));
                                break;
                            case "count":
                                filterAggregation.subAggregation(AggregationBuilders.count("count").field(field));
                                break;
                        }
                    }
                }
                searchSourceBuilder.aggregation(filterAggregation);
                searchRequest.source(searchSourceBuilder);
                SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);

                Aggregations aggregations = response.getAggregations();
                if (aggregations != null) {
                    Aggregation metricsResults = aggregations.get("metrics");
                    if (metricsResults instanceof HasAggregations) {
                        aggregations = ((HasAggregations) metricsResults).getAggregations();
                        for (Aggregation aggregation : aggregations) {
                            NumericMetricsAggregation.SingleValue singleValue = (NumericMetricsAggregation.SingleValue) aggregation;
                            results.put("_" + singleValue.getName(), singleValue.value());
                        }
                    }
                }
                return results;
            }
        }.catchingExecuteInClassLoader(true);
    }


    private String getConfig(Map<String, String> settings, String key,
                             String defaultValue) {
        if (settings != null && settings.get(key) != null) {
            return settings.get(key);
        }
        return defaultValue;
    }

    public abstract static class InClassLoaderExecute<T> {

        private String timerName;
        private MetricsService metricsService;
        private BundleContext bundleContext;
        private String[] fatalIllegalStateErrors; // Errors that if occur - stop the application
        private boolean throwExceptions;

        public InClassLoaderExecute(MetricsService metricsService, String timerName, BundleContext bundleContext, String[] fatalIllegalStateErrors, boolean throwExceptions) {
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
                    if (tTemp instanceof IllegalStateException && Arrays.stream(this.fatalIllegalStateErrors).anyMatch(tTemp.getMessage()::contains)) {
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
                logger.error("Error while executing in class loader", t);
            }
            if (throwExceptions) {
                throw new RuntimeException(t);
            }
        }

        private void handleFatalStateError() {
            logger.error("Fatal state error occurred - stopping application");
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
        return systemItems.contains(itemType) ? (itemId + "_" + itemType.toLowerCase()) : itemId;
    }

    private QueryBuilder wrapWithItemTypeQuery(String itemType, QueryBuilder originalQuery) {
        if (isItemTypeSharingIndex(itemType)) {
            BoolQueryBuilder wrappedQuery = QueryBuilders.boolQuery();
            wrappedQuery.must(getItemTypeQueryBuilder(itemType));
            wrappedQuery.must(originalQuery);
            return wrappedQuery;
        }
        return originalQuery;
    }

    private QueryBuilder wrapWithItemsTypeQuery(String[] itemTypes, QueryBuilder originalQuery) {
        if (itemTypes.length == 1) {
            return wrapWithItemTypeQuery(itemTypes[0], originalQuery);
        }

        if (Arrays.stream(itemTypes).anyMatch(this::isItemTypeSharingIndex)) {
            BoolQueryBuilder itemTypeQuery = QueryBuilders.boolQuery();
            itemTypeQuery.minimumShouldMatch(1);
            for (String itemType : itemTypes) {
                itemTypeQuery.should(getItemTypeQueryBuilder(itemType));
            }

            BoolQueryBuilder wrappedQuery = QueryBuilders.boolQuery();
            wrappedQuery.filter(itemTypeQuery);
            wrappedQuery.must(originalQuery);
            return wrappedQuery;
        }
        return originalQuery;
    }

    private QueryBuilder getItemTypeQueryBuilder(String itemType) {
        return QueryBuilders.termQuery("itemType", ConditionContextHelper.foldToASCII(itemType));
    }

    private boolean isItemTypeSharingIndex(String itemType) {
        return itemTypeIndexNameMap.containsKey(itemType);
    }

    private boolean isItemTypeRollingOver(String itemType) {
        return (rolloverIndices != null ? rolloverIndices : itemsMonthlyIndexed).contains(itemType);
    }

    private WriteRequest.RefreshPolicy getRefreshPolicy(String itemType) {
        if (itemTypeToRefreshPolicy.containsKey(itemType)) {
            return itemTypeToRefreshPolicy.get(itemType);
        }
        return WriteRequest.RefreshPolicy.NONE;
    }

    private void logMetadataItemOperation (String operation, Item item) {
        if (item instanceof MetadataItem) {
            logger.info("Item of type {} with ID {} has been {}", item.getItemType(), item.getItemId(), operation);
        }
    }
}
