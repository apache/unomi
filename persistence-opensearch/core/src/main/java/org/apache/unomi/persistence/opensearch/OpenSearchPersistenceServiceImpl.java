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

package org.apache.unomi.persistence.opensearch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.stream.JsonParser;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.util.EntityUtils;
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
import org.apache.unomi.persistence.spi.aggregate.DateRangeAggregate;
import org.apache.unomi.persistence.spi.aggregate.IpRangeAggregate;
import org.apache.unomi.persistence.spi.aggregate.*;
import org.apache.unomi.persistence.spi.conditions.ConditionContextHelper;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluator;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluatorDispatcher;
import org.opensearch.client.*;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.*;
import org.opensearch.client.opensearch._types.aggregations.*;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.cluster.HealthRequest;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.opensearch.client.opensearch.core.*;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.core.search.TotalHits;
import org.opensearch.client.opensearch.core.search.TotalHitsRelation;
import org.opensearch.client.opensearch.indices.*;
import org.opensearch.client.opensearch.indices.get_alias.IndexAliases;
import org.opensearch.client.opensearch.tasks.GetTasksResponse;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.apache.unomi.api.tenants.TenantService.SYSTEM_TENANT;

@SuppressWarnings("rawtypes")
public class OpenSearchPersistenceServiceImpl implements PersistenceService, SynchronousBundleListener {

    public static final String SEQ_NO = "seq_no";
    public static final String PRIMARY_TERM = "primary_term";

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchPersistenceServiceImpl.class.getName());
    private static final String ROLLOVER_LIFECYCLE_NAME = "unomi-rollover-policy";

    private boolean throwExceptions = false;

    private OpenSearchClient client;
    private RestClient restClient;

    private final List<String> openSearchAddressList = new ArrayList<>();
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
    private final Map<String, String> mappings = new HashMap<>();
    private ConditionEvaluatorDispatcher conditionEvaluatorDispatcher;
    private ConditionOSQueryBuilderDispatcher conditionOSQueryBuilderDispatcher;
    private List<String> itemsMonthlyIndexed;
    private Map<String, String> routingByType;

    private Integer defaultQueryLimit = 10;
    private Integer removeByQueryTimeoutInMinutes = 10;
    private Integer taskWaitingTimeout = 3600000;
    private Integer taskWaitingPollingInterval = 1000;

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

    private String minimalOpenSearchVersion = "2.1.0";
    private String maximalOpenSearchVersion = "3.0.0";

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

    private final JsonpMapper jsonpMapper = new JacksonJsonpMapper();

    private String minimalClusterState = "GREEN"; // Add this as a class field
    private int clusterHealthTimeout = 30; // timeout in seconds
    private int clusterHealthRetries = 3;

    private volatile ExecutionContextManager contextManager = null;
    private List<TenantTransformationListener> transformationListeners = new CopyOnWriteArrayList<>();

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public void setOpenSearchAddresses(String openSearchAddresses) {
        String[] openSearchAddressesArray = openSearchAddresses.split(",");
        openSearchAddressList.clear();
        for (String openSearchAddress : openSearchAddressesArray) {
            openSearchAddressList.add(openSearchAddress.trim());
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
        this.fatalIllegalStateErrors = Arrays.stream(fatalIllegalStateErrors.split(","))
                .map(String::trim).filter(i -> !i.isEmpty()).toArray(String[]::new);
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

    public void setConditionOSQueryBuilderDispatcher(ConditionOSQueryBuilderDispatcher conditionOSQueryBuilderDispatcher) {
        this.conditionOSQueryBuilderDispatcher = conditionOSQueryBuilderDispatcher;
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

    public void setMinimalOpenSearchVersion(String minimalOpenSearchVersion) {
        this.minimalOpenSearchVersion = minimalOpenSearchVersion;
    }

    public void setMaximalOpenSearchVersion(String maximalOpenSearchVersion) {
        this.maximalOpenSearchVersion = maximalOpenSearchVersion;
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

    public void setMinimalClusterState(String minimalClusterState) {
        if ("GREEN".equalsIgnoreCase(minimalClusterState) || "YELLOW".equalsIgnoreCase(minimalClusterState)) {
            this.minimalClusterState = minimalClusterState.toUpperCase();
        } else {
            LOGGER.warn("Invalid minimal cluster state: {}. Using default: GREEN", minimalClusterState);
        }
    }

    public String getName() {
        return "opensearch";
    }

    public void start() throws Exception {

        // on startup
        new InClassLoaderExecute<>(null, null, this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            public Object execute(Object... args) throws Exception {

                buildClient();

                InfoResponse response = client.info();
                OpenSearchVersionInfo version = response.version();
                Version clusterVersion = Version.parseVersion(version.number());
                Version minimalVersion = Version.parseVersion(minimalOpenSearchVersion);
                Version maximalVersion = Version.parseVersion(maximalOpenSearchVersion);
                if (clusterVersion.compareTo(minimalVersion) < 0 ||
                        clusterVersion.equals(maximalVersion) ||
                        clusterVersion.compareTo(maximalVersion) > 0) {
                    throw new Exception("OpenSearch version is not within [" + minimalVersion + "," + maximalVersion + "), aborting startup !");
                }

                waitForClusterHealth();

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

                // Wait for minimal cluster state
                LOGGER.info("Waiting for {} cluster status...", minimalClusterState);
                client.cluster().health(new HealthRequest.Builder().waitForStatus(getHealthStatus(minimalClusterState)).build());
                LOGGER.info("Cluster status is {}", minimalClusterState);

                // We keep in memory the latest available session index to be able to load session using direct GET access on ES
                if (isItemTypeRollingOver(Session.ITEM_TYPE)) {
                    LOGGER.info("Sessions are using rollover indices, loading latest session index available ...");
                    GetAliasResponse sessionAliasResponse = client.indices().getAlias(new GetAliasRequest.Builder().index(getIndex(Session.ITEM_TYPE)).build());
                    Map<String, IndexAliases> aliases = sessionAliasResponse.result();
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

        LOGGER.info(this.getClass().getName() + " service started successfully.");
    }

    private void buildClient() throws NoSuchFieldException, IllegalAccessException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {

        List<Node> nodeList = new ArrayList<>();
        for (String openSearchAddress : openSearchAddressList) {
            String[] openSearchAddressParts = openSearchAddress.split(":");
            String openSearchHostName = openSearchAddressParts[0];
            int openSearchPort = Integer.parseInt(openSearchAddressParts[1]);

            // configure authentication
            nodeList.add(new Node(new HttpHost(openSearchHostName, openSearchPort, sslEnable ? "https" : "http")));
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
                    LOGGER.error("Error creating SSL Context for trust all certificates", e);
                }
            }

            if (StringUtils.isNotBlank(username)) {
                final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));

                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
            }

            return httpClientBuilder;
        });

        restClient = clientBuilder.build();
        OpenSearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper(OSCustomObjectMapper.getObjectMapper()));
        client = new OpenSearchClient(transport);

        LOGGER.info("Connecting to OpenSearch persistence backend using cluster name " + clusterName + " and index prefix " + indexPrefix + "...");
    }


    public void stop() {

        new InClassLoaderExecute<>(null, null, this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Object execute(Object... args) throws IOException {
                LOGGER.info("Closing OpenSearch persistence backend...");
                if (client != null) {
                    client.shutdown();
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

    public void bindConditionOSQueryBuilder(ServiceReference<ConditionOSQueryBuilder> conditionESQueryBuilderServiceReference) {
        ConditionOSQueryBuilder conditionOSQueryBuilder = bundleContext.getService(conditionESQueryBuilderServiceReference);
        conditionOSQueryBuilderDispatcher.addQueryBuilder(conditionESQueryBuilderServiceReference.getProperty("queryBuilderId").toString(), conditionOSQueryBuilder);
    }

    public void unbindConditionOSQueryBuilder(ServiceReference<ConditionOSQueryBuilder> conditionESQueryBuilderServiceReference) {
        if (conditionESQueryBuilderServiceReference == null) {
            return;
        }
        conditionOSQueryBuilderDispatcher.removeQueryBuilder(conditionESQueryBuilderServiceReference.getProperty("queryBuilderId").toString());
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
                // If context manager is not available, execute directly as operations won't be validated
                loadPredefinedMappings(event.getBundle().getBundleContext(), true);
                loadPainlessScripts(event.getBundle().getBundleContext());
            }
        }
    }

    private void loadPredefinedMappings(BundleContext bundleContext, boolean forceUpdateMapping) {
        Enumeration<URL> predefinedMappings = bundleContext.getBundle().findEntries("META-INF/cxs/mappings", "*.json", true);
        if (predefinedMappings == null) {
            return;
        }
        while (predefinedMappings.hasMoreElements()) {
            URL predefinedMappingURL = predefinedMappings.nextElement();
            LOGGER.info("Found mapping at " + predefinedMappingURL + ", loading... ");
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
                LOGGER.error("Error while loading mapping definition " + predefinedMappingURL, e);
            }
        }
    }

    private TypeMapping getTypeMapping(String mappingSource) {
        JsonpMapper mapper = client._transport().jsonpMapper();
        JsonParser parser = mapper
                .jsonProvider()
                .createParser(new StringReader(mappingSource));
        return  TypeMapping._DESERIALIZER.deserialize(parser, mapper);
    }

    private void loadPainlessScripts(BundleContext bundleContext) {
        Enumeration<URL> scriptsURL = bundleContext.getBundle().findEntries("META-INF/cxs/painless", "*.painless", true);
        if (scriptsURL == null) {
            return;
        }

        Map<String, String> scriptsById = new HashMap<>();
        while (scriptsURL.hasMoreElements()) {
            URL scriptURL = scriptsURL.nextElement();
            LOGGER.info("Found painless script at " + scriptURL + ", loading... ");
            try (InputStream in = scriptURL.openStream()) {
                String script = IOUtils.toString(in, StandardCharsets.UTF_8);
                String scriptId = FilenameUtils.getBaseName(scriptURL.getPath());
                scriptsById.put(scriptId, script);
            } catch (Exception e) {
                LOGGER.error("Error while loading painless script " + scriptURL, e);
            }

        }

        storeScripts(scriptsById);
    }

    private String  loadMappingFile(URL predefinedMappingURL) throws IOException {
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
        return queryCount(Query.of(q -> q.matchAll(t -> t)), itemType);
    }

    @Override
    public <T extends Item> PartialList<T> getAllItems(final Class<T> clazz, int offset, int size, String sortBy) {
        return getAllItems(clazz, offset, size, sortBy, null);
    }

    @Override
    public <T extends Item> PartialList<T> getAllItems(final Class<T> clazz, int offset, int size, String sortBy, String scrollTimeValidity) {
        long startTime = System.currentTimeMillis();
        try {
            return query(Query.of(q -> q.matchAll(t -> t)), sortBy, clazz, offset, size, null, scrollTimeValidity);
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
                                    PartialList<T> r = query(Query.of(q -> q.ids(i -> i.values(documentId))), null, clazz, 0, 1, null, null);
                                    if (r.size() > 0) {
                                        return r.get(0);
                                    }
                                } else {
                                    PartialList<CustomItem> r = query(Query.of(q -> q.ids(i -> i.values(documentId))), null, customItemType, 0, 1, null, null);
                                    if (r.size() > 0) {
                                        return (T) r.get(0);
                                    }
                                }
                                return null;
                            }
                        }.execute();
                    } else {
                        // Special handling for session we check the latest available index directly to speed up session loading
                        GetRequest.Builder getRequest = new GetRequest.Builder().index(sessionSpecialDirectAccess ? sessionLatestIndex : getIndex(itemType)).id(documentId);
                        GetResponse<T> response = client.get(getRequest.build(), clazz);
                        if (response.found()) {
                            T value = response.source();
                            setMetadata(value, response.id(), response.version(), response.seqNo(), response.primaryTerm(), response.index());
                            return value;
                        } else {
                            return null;
                        }
                    }
                } catch (OpenSearchException ose) {
                    if (ose.status() == 404) {
                        // this can happen if we are just testing the existence of the item, it is not always an error.
                        return null;
                    }
                    if ("IndexNotFound".equals(ose.error().type())) {
                        // this can happen if we are just testing the existence of the item, it is not always an error.
                        return null;
                    }
                    throw new Exception("Error loading itemType=" + clazz.getName() + " customItemType=" + customItemType + " itemId=" + itemId, ose);
                } catch (Exception ex) {
                    throw new Exception("Error loading itemType=" + clazz.getName() + " customItemType=" + customItemType + " itemId=" + itemId, ex);
                }
            }
        }.catchingExecuteInClassLoader(true);

    }

    private void setMetadata(Item item, String itemId, long version, long seqNo, long primaryTerm, String index) {
        if (item != null) {
            String strippedId = stripTenantFromDocumentId(itemId);
            if (!systemItems.contains(item.getItemType()) && item.getItemId() == null) {
                item.setItemId(strippedId);
            }
            item.setVersion(version);
            item.setSystemMetadata(SEQ_NO, seqNo);
            item.setSystemMetadata(PRIMARY_TERM, primaryTerm);
            item.setSystemMetadata("index", index);
        }
    }

    @Override
    public boolean isConsistent(Item item) {
        return getRefreshPolicy(item.getItemType()) != Refresh.False;
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

        validateTenantAndGetId(SecurityServiceConfiguration.PERMISSION_SAVE);

        handleItemTransformation(item);

        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".save", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws Exception {
                try {
                    String itemType = item.getItemType();
                    if (item instanceof CustomItem) {
                        itemType = ((CustomItem) item).getCustomItemType();
                    }
                    String documentId = getDocumentIDForItemType(item.getItemId(), itemType);
                    String index = item.getSystemMetadata("index") != null ? (String) item.getSystemMetadata("index") : getIndex(itemType);

                    IndexRequest.Builder<Item> indexRequest = new IndexRequest.Builder<Item>().index(index);
                    indexRequest.id(documentId);
                    indexRequest.document(item);

                    if (!alwaysOverwrite) {
                        Long seqNo = (Long) item.getSystemMetadata(SEQ_NO);
                        Long primaryTerm = (Long) item.getSystemMetadata(PRIMARY_TERM);

                        if (seqNo != null && primaryTerm != null) {
                            indexRequest.ifSeqNo(seqNo);
                            indexRequest.ifPrimaryTerm(primaryTerm);
                        } else {
                            indexRequest.opType(OpType.Create);
                        }
                    }

                    if (routingByType.containsKey(itemType)) {
                        indexRequest.routing(routingByType.get(itemType));
                    }

                    try {
                        indexRequest.refresh(getRefreshPolicy(itemType));
                        IndexResponse response = client.index(indexRequest.build());
                        String responseIndex = response.index();
                        String itemId = response.id();
                        setMetadata(item, itemId, response.version(), response.seqNo(), response.primaryTerm(), responseIndex);

                        // Special handling for session, in case of new session we check that a rollover happen or not to update the latest available index
                        if (Session.ITEM_TYPE.equals(itemType) &&
                                sessionLatestIndex != null &&
                                response.result().equals(Result.Created) &&
                                !responseIndex.equals(sessionLatestIndex)) {
                            sessionLatestIndex = responseIndex;
                        }
                        logMetadataItemOperation("saved", item);
                    } catch (OpenSearchException ose) {
                        LOGGER.error("Could not find index {}, could not register item type {} with id {} ", index, itemType, item.getItemId(), ose);
                        return false;
                    }
                    return true;
                } catch (IOException e) {
                    throw new Exception("Error saving item " + item, e);
                }
            }
        }.catchingExecuteInClassLoader(true);
        return Objects.requireNonNullElse(result, false);
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
        validateTenantAndGetId(SecurityServiceConfiguration.PERMISSION_UPDATE);

        // For property updates, we need to check if the field needs transformation
        handleItemTransformation(item);

        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".updateItem", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws Exception {
                try {
                    UpdateRequest updateRequest = createUpdateRequest(clazz, item, source, alwaysOverwrite);

                    UpdateResponse response = client.update(updateRequest, Item.class);
                    if (response.result().equals(Result.NoOp)) {
                        LOGGER.warn("Update of item {} with source {} returned NoOp", item.getItemId(), source);
                    }
                    setMetadata(item, response.id(), response.version(), response.seqNo(), response.primaryTerm(), response.index());
                    logMetadataItemOperation("updated", item);
                    return true;
                } catch (OpenSearchException ose) {
                    throw new Exception("No index found for itemType=" + clazz.getName() + "itemId=" + item.getItemId(), ose);
                }
            }
        }.catchingExecuteInClassLoader(true);
        return Objects.requireNonNullElse(result, false);
    }

    private UpdateRequest createUpdateRequest(Class clazz, Item item, Map source, boolean alwaysOverwrite) {
        String itemType = Item.getItemType(clazz);
        String documentId = getDocumentIDForItemType(item.getItemId(), itemType);
        String index =  item.getSystemMetadata("index") != null ? (String) item.getSystemMetadata("index") : getIndex(itemType);

        UpdateRequest.Builder updateRequest = new UpdateRequest.Builder<Item, Map>().index(index).id(documentId);
        updateRequest.doc(source);

        if (!alwaysOverwrite) {
            Long seqNo = (Long) item.getSystemMetadata(SEQ_NO);
            Long primaryTerm = (Long) item.getSystemMetadata(PRIMARY_TERM);

            if (seqNo != null && primaryTerm != null) {
                updateRequest.ifSeqNo(seqNo);
                updateRequest.ifPrimaryTerm(primaryTerm);
            }
        }
        return updateRequest.build();
    }

    private UpdateOperation createUpdateOperation(Class clazz, Item item, Map source, boolean alwaysOverwrite) {
        String itemType = Item.getItemType(clazz);
        String documentId = getDocumentIDForItemType(item.getItemId(), itemType);
        String index =  item.getSystemMetadata("index") != null ? (String) item.getSystemMetadata("index") : getIndex(itemType);

        UpdateOperation.Builder updateOperation = new UpdateOperation.Builder<Map>().index(index).id(documentId);
        updateOperation.document(source);

        if (!alwaysOverwrite) {
            Long seqNo = (Long) item.getSystemMetadata(SEQ_NO);
            Long primaryTerm = (Long) item.getSystemMetadata(PRIMARY_TERM);

            if (seqNo != null && primaryTerm != null) {
                updateOperation.ifSeqNo(seqNo);
                updateOperation.ifPrimaryTerm(primaryTerm);
            }
        }
        return updateOperation.build();
    }

    @Override
    public List<String> update(final Map<Item, Map> items, final Date dateHint, final Class clazz) {
        if (items.isEmpty())
            return new ArrayList<>();

        return new InClassLoaderExecute<List<String>>(metricsService, OpenSearchPersistenceServiceImpl.this.getClass().getName() + ".updateItems", OpenSearchPersistenceServiceImpl.this.bundleContext, OpenSearchPersistenceServiceImpl.this.fatalIllegalStateErrors, throwExceptions) {
            protected List<String> execute(Object... args) throws Exception {
                long batchRequestStartTime = System.currentTimeMillis();

                List<BulkOperation> operations = new ArrayList<>();
                items.forEach((item, source) -> {
                    UpdateOperation updateOperation = createUpdateOperation(clazz, item, source, alwaysOverwrite);
                    operations.add(BulkOperation.of(b -> b.update(updateOperation)));
                });

                BulkResponse bulkResponse = client.bulk(b -> b.operations(operations));
                LOGGER.debug("{} profiles updated with bulk segment in {}ms", operations.size(), System.currentTimeMillis() - batchRequestStartTime);

                List<String> failedItemsIds = new ArrayList<>();

                if (bulkResponse.errors()) {
                    bulkResponse.items().forEach(bulkItemResponse -> {
                        if (bulkItemResponse.error() != null) {
                            failedItemsIds.add(bulkItemResponse.id());
                        }
                    });
                }
                return failedItemsIds;
            }
        }.catchingExecuteInClassLoader(true);
    }

    @Override
    public boolean updateWithQueryAndScript(final Date dateHint, final Class<?> clazz, final String[] scripts, final Map<String, Object>[] scriptParams, final Condition[] conditions) {
        return updateWithQueryAndScript(clazz, scripts, scriptParams, conditions);
    }

    @Override
    public boolean updateWithQueryAndScript(final Class<?> clazz, final String[] scripts, final Map<String, Object>[] scriptParams, final Condition[] conditions) {
        Script[] builtScripts = new Script[scripts.length];
        for (int i = 0; i < scripts.length; i++) {
            final int finalI = i;
            builtScripts[i] = Script.of(script -> script.inline(inline->inline.lang("painless").source(scripts[finalI]).params(convertParams(scriptParams[finalI]))));
        }
        return updateWithQueryAndScript(new Class<?>[]{clazz}, builtScripts, conditions, true);
    }

    private Map<String, JsonData> convertParams(Map<String, Object> scriptParams) {
        Map<String,JsonData> jsonParams = new HashMap<>();
        for (Map.Entry<String,Object> paramEntry : scriptParams.entrySet()) {
            jsonParams.put(paramEntry.getKey(), JsonData.of(paramEntry.getValue(), jsonpMapper));
        }
        return jsonParams;
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
            final int finalI = i;
            builtScripts[i] = Script.of(s -> s.stored(stored -> stored.id(scripts[finalI]).params(convertParams(scriptParams[finalI]))));
        }
        return updateWithQueryAndScript(classes, builtScripts, conditions, waitForComplete);
    }

    private boolean updateWithQueryAndScript(final Class<?>[] classes, final Script[] scripts, final Condition[] conditions, boolean waitForComplete) {
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".updateWithQueryAndScript", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws Exception {
                String[] itemTypes = Arrays.stream(classes).map(Item::getItemType).toArray(String[]::new);
                List<String> indices = Arrays.stream(itemTypes).map(itemType -> getIndexNameForQuery(itemType)).collect(Collectors.toList());

                try {
                    for (int i = 0; i < scripts.length; i++) {
                        RefreshRequest refreshRequest = new RefreshRequest.Builder().index(indices).build();
                        client.indices().refresh(refreshRequest);

                        Query queryBuilder = conditionOSQueryBuilderDispatcher.buildFilter(conditions[i]);
                        UpdateByQueryRequest.Builder updateByQueryRequestBuilder = new UpdateByQueryRequest.Builder().index(indices);
                        updateByQueryRequestBuilder.conflicts(Conflicts.Proceed);
                        // TODO fix this updateByQueryRequest.setMaxRetries(1000);
                        updateByQueryRequestBuilder.slices(2L);
                        updateByQueryRequestBuilder.script(scripts[i]);
                        updateByQueryRequestBuilder.query(wrapWithTenantAndItemsTypeQuery(itemTypes, queryBuilder, getTenantId()));
                        updateByQueryRequestBuilder.waitForCompletion(false); // force the return of a task ID.

                        UpdateByQueryRequest updateByQueryRequest = updateByQueryRequestBuilder.build();
                        UpdateByQueryResponse updateByQueryResponse = client.updateByQuery(updateByQueryRequest);
                        if (updateByQueryResponse == null) {
                            LOGGER.error("update with query and script: no response returned for query: {}", queryBuilder);
                        } else if (waitForComplete) {
                            waitForTaskComplete(updateByQueryRequest.toString(), updateByQueryRequest.toString(), updateByQueryResponse.task());
                        } else {
                            LOGGER.debug("ES task started {}", updateByQueryResponse.task());
                        }
                    }
                    return true;
                } catch (OpenSearchException ose) {
                    throw new Exception("No index found for itemTypes=" + String.join(",", itemTypes), ose);
                    /* TODO Implement this
                } catch (ScriptException e) {
                    LOGGER.error("Error in the update script : {}\n{}\n{}", e.getScript(), e.getDetailedMessage(), e.getScriptStack());
                    throw new Exception("Error in the update script");
                     */
                }
            }
        }.catchingExecuteInClassLoader(true);
        return Objects.requireNonNullElse(result, false);
    }

    private void waitForTaskComplete(String request, String requestSource, String taskId) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Waiting task [{}]: [{}] using query: [{}], polling every {}ms with a timeout configured to {}ms",
                    taskId, request, requestSource, taskWaitingPollingInterval, taskWaitingTimeout);
        }
        if (taskId == null) {
            LOGGER.warn("No taskId provided, can't wait for task [{}]", request);
            return;
        }
        long start = System.currentTimeMillis();
        new InClassLoaderExecute<Void>(metricsService, this.getClass().getName() + ".waitForTask", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Void execute(Object... args) throws Exception {

                while (true){
                    GetTasksResponse getTasksResponse = client.tasks().get(t -> t.taskId(taskId));
                    if (getTasksResponse.completed()) {
                        if (LOGGER.isDebugEnabled()) {
                            long millis = getTasksResponse.task().runningTimeInNanos() / 1_000_000;
                            long seconds = millis / 1000;

                            LOGGER.debug("Waiting task [{}]: Finished in {} {}", taskId,
                                    seconds >= 1 ? seconds : millis,
                                    seconds >= 1 ? "seconds" : "milliseconds");
                        }
                        break;
                    } else {
                        if ((start + taskWaitingTimeout) < System.currentTimeMillis()) {
                            LOGGER.error("Waiting task [{}]: Exceeded configured timeout ({}ms), aborting wait process", taskId, taskWaitingTimeout);
                            break;
                        }

                        try {
                            Thread.sleep(taskWaitingPollingInterval);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Waiting task [{}]: interrupted");
                        }
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
                    PutScriptRequest.Builder putScriptRequestBuilder = new PutScriptRequest.Builder();
                    putScriptRequestBuilder.script(s -> s.lang("painless").source(script.getValue()));
                    putScriptRequestBuilder.id(script.getKey());
                    PutScriptResponse response = client.putScript(putScriptRequestBuilder.build());
                    executedSuccessfully &= response.acknowledged();
                    if (response.acknowledged()) {
                        LOGGER.info("Successfully stored painless script: {}", script.getKey());
                    } else {
                        LOGGER.error("Failed to store painless script: {}", script.getKey());
                    }
                }
                return executedSuccessfully;
            }
        }.catchingExecuteInClassLoader(true);
        return Objects.requireNonNullElse(result, false);
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

                    Script actualScript = Script.of(s -> s.inline(i -> i.lang("painless").source(script).params(convertParams(scriptParams))));

                    UpdateRequest.Builder<Item, Map> updateRequestBuilder = new UpdateRequest.Builder<Item, Map>().index(index).id(documentId);

                    Long seqNo = (Long) item.getSystemMetadata(SEQ_NO);
                    Long primaryTerm = (Long) item.getSystemMetadata(PRIMARY_TERM);

                    if (seqNo != null && primaryTerm != null) {
                        updateRequestBuilder.ifSeqNo(seqNo);
                        updateRequestBuilder.ifPrimaryTerm(primaryTerm);
                    }
                    updateRequestBuilder.script(actualScript);
                    UpdateResponse response = client.update(updateRequestBuilder.build(), Item.class);
                    setMetadata(item, response.id(), response.version(), response.seqNo(), response.primaryTerm(), response.index());

                    return true;
                } catch (OpenSearchException ose) {
                    throw new Exception("No index found for itemType=" + clazz.getName() + "itemId=" + item.getItemId(), ose);
                }
            }
        }.catchingExecuteInClassLoader(true);
        return Objects.requireNonNullElse(result, false);
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
                    String index = getIndex(itemType);

                    DeleteRequest deleteRequest = DeleteRequest.of(d->d.index(index).id(documentId));
                    client.delete(deleteRequest);
                    if (MetadataItem.class.isAssignableFrom(clazz)) {
                        LOGGER.info("Item of type {} with ID {} has been removed", customItemType != null ? customItemType : clazz.getSimpleName(), itemId);
                    }
                    return true;
                } catch (Exception e) {
                    throw new Exception("Cannot remove", e);
                }
            }
        }.catchingExecuteInClassLoader(true);
        return Objects.requireNonNullElse(result, false);
    }

    @Override
    public <T extends Item> boolean removeByQuery(final Condition query, final Class<T> clazz) {
        String finalTenantId = validateTenantAndGetId(SecurityServiceConfiguration.PERMISSION_REMOVE_BY_QUERY);

        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".removeByQuery", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws Exception {
                Query queryBuilder = conditionOSQueryBuilderDispatcher.getQueryBuilder(query);
                queryBuilder = wrapWithTenantAndItemTypeQuery(Item.getItemType(clazz), queryBuilder, finalTenantId);
                return removeByQuery(queryBuilder, clazz);
            }
        }.catchingExecuteInClassLoader(true);
        return Objects.requireNonNullElse(result, false);
    }

    public <T extends Item> boolean removeByQuery(Query queryBuilder, final Class<T> clazz) throws Exception {
        try {
            String itemType = Item.getItemType(clazz);
            LOGGER.debug("Remove item of type {} using a query", itemType);
            final DeleteByQueryRequest.Builder deleteByQueryRequestBuilder = new DeleteByQueryRequest.Builder().index(getIndexNameForQuery(itemType))
                    .query(wrapWithTenantAndItemTypeQuery(itemType, queryBuilder, getTenantId()))
                    // Setting slices to auto will let OpenSearch choose the number of slices to use.
                    // This setting will use one slice per shard, up to a certain limit.
                    // The delete request will be more efficient and faster than no slicing.
                    .slices(0L) // 0L means auto
                    // OpenSearch takes a snapshot of the index when you hit delete by query request and uses the _version of the documents to process the request.
                    // If a document gets updated in the meantime, it will result in a version conflict error and the delete operation will fail.
                    // So we explicitly set the conflict strategy to proceed in case of version conflict.
                    .conflicts(Conflicts.Proceed)
                    // We force waitForCompletion to the value false to make sure we get back a taskID that we can then poll for
                    // in our waitForTaskComplete method
                    .waitForCompletion(false)
                    // Remove by Query is mostly used for purge and cleaning up old data
                    // It's mostly used in jobs/timed tasks so we don't really care about long request
                    // So we increase default timeout of 1min to 10min
                    .timeout(t -> t.time(removeByQueryTimeoutInMinutes + "m"));

            DeleteByQueryRequest deleteByQueryRequest = deleteByQueryRequestBuilder.build();
            DeleteByQueryResponse deleteByQueryResponse = client.deleteByQuery(deleteByQueryRequest);

            if (deleteByQueryResponse == null) {
                LOGGER.error("Remove by query: no response returned for query: {}", queryBuilder);
                return false;
            }

            waitForTaskComplete(deleteByQueryRequest.toString(), deleteByQueryRequest.toString(), deleteByQueryResponse.task());

            return true;
        } catch (Exception e) {
            throw new Exception("Cannot remove by query", e);
        }
    }

    public boolean indexTemplateExists(final String templateName) {
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".indexTemplateExists", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws IOException {
                return client.indices().existsTemplate(e -> e.name(templateName)).value();
            }
        }.catchingExecuteInClassLoader(true);
        return Objects.requireNonNullElse(result, false);
    }

    public boolean removeIndexTemplate(final String templateName) {
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".removeIndexTemplate", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws IOException {
                DeleteTemplateResponse deleteTemplateResponse = client.indices().deleteTemplate(d -> d.name(templateName));
                return deleteTemplateResponse.acknowledged();
            }
        }.catchingExecuteInClassLoader(true);
        return Objects.requireNonNullElse(result, false);
    }

    public boolean registerRolloverLifecyclePolicy() {
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".createMonthlyIndexLifecyclePolicy", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws IOException {
                try {
                    String policyName = indexPrefix + "-rollover-lifecycle-policy";
                    String endpoint = "_plugins/_ism/policies/" + policyName;

                    RestClient restClient = ((RestClientTransport) client._transport()).restClient();

                    // Upon initial OpenSearch startup, the .opendistro-ism-config index may not exist yet, so we need to check if it exists first
                    // Check if the .opendistro-ism-config index exists
                    Request checkIndexRequest = new Request("HEAD", ".opendistro-ism-config");
                    Response checkIndexResponse = restClient.performRequest(checkIndexRequest);

                    if (checkIndexResponse.getStatusLine().getStatusCode() == 404) {
                        LOGGER.info(".opendistro-ism-config index does not exist. Initializing ISM configuration.");
                    } else {
                        Request getRequest = new Request("GET", endpoint);
                        Response response = restClient.performRequest(getRequest);
                        if (response.getStatusLine().getStatusCode() == 200) {
                            LOGGER.info("Found existing rollover lifecycle policy, deleting the existing one.");
                            Request deleteRequest = new Request("DELETE", endpoint);
                            restClient.performRequest(deleteRequest);
                        }
                    }

                    // Build the ILM policy JSON
                    Map<String, Object> rolloverAction = new HashMap<>();

                    if (rolloverMaxDocs != null && !rolloverMaxDocs.isEmpty()) {
                        rolloverAction.put("min_doc_count", Long.parseLong(rolloverMaxDocs));
                    }
                    if (rolloverMaxSize != null && !rolloverMaxSize.isEmpty()) {
                        rolloverAction.put("min_size", rolloverMaxSize);
                    }
                    if (rolloverMaxAge != null && !rolloverMaxAge.isEmpty()) {
                        rolloverAction.put("min_index_age", rolloverMaxAge);
                    }

                    List<Map<String,Object>> actions = new ArrayList<>();
                    actions.add(Map.of("rollover", rolloverAction));
                    Map<String, Object> state = new HashMap<>();
                    state.put("name", "ingest");
                    state.put("actions", actions);
                    state.put("transitions", new ArrayList<>());
                    List<Map<String,Object>> states = new ArrayList<>();
                    states.add(state);
                    Map<String, Object> policy = new HashMap<>();
                    policy.put("states", states);
                    policy.put("default_state", "ingest");
                    policy.put("description", "Rollover lifecycle policy");
                    if (rolloverIndices != null && !rolloverIndices.isEmpty()) {
                        Map<String,Object> ismTemplate = new HashMap<>();
                        List<String> indexPatterns = new ArrayList<>();
                        indexPatterns.addAll(rolloverIndices);
                        ismTemplate.put("index_patterns", indexPatterns);
                        ismTemplate.put("priority", 100);
                        policy.put("ism_template", ismTemplate);
                    }
                    Map<String,Object> policies = new HashMap<>();
                    policies.put("policy", policy);

                    // Convert the policy to JSON
                    ObjectMapper objectMapper = new ObjectMapper();
                    String policyJson = objectMapper.writeValueAsString(policies);

                    // Send the request

                    Request request = new Request("PUT", endpoint);
                    request.setJsonEntity(policyJson);
                    Response response = restClient.performRequest(request);

                    return response.getStatusLine().getStatusCode() == 200;
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }
        }.catchingExecuteInClassLoader(true);
        return Objects.requireNonNullElse(result, false);
    }

    public boolean createIndex(final String itemType) {
        LOGGER.debug("Create index {}", itemType);
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".createIndex", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws IOException {
                String index = getIndex(itemType);
                boolean indexExists = client.indices().exists(e -> e.index(index)).value();

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

        return Objects.requireNonNullElse(result, false);
    }

    public boolean removeIndex(final String itemType) {
        String index = getIndex(itemType);

        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".removeIndex", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws IOException {
                boolean indexExists = client.indices().exists(e -> e.index(index)).value();
                if (indexExists) {
                    client.indices().delete(d -> d.index(index));
                }
                return indexExists;
            }
        }.catchingExecuteInClassLoader(true);

        return Objects.requireNonNullElse(result, false);
    }

    private void internalCreateRolloverTemplate(String itemName) throws IOException {
        String rolloverAlias = indexPrefix + "-" + itemName;
        if (mappings.get(itemName) == null) {
            LOGGER.warn("Couldn't find mapping for item {}, won't create monthly index template", itemName);
            return;
        }
        String indexSource =
                "    {" +
                "        \"number_of_shards\" : " + StringUtils.defaultIfEmpty(rolloverIndexNumberOfShards, monthlyIndexNumberOfShards) + "," +
                "        \"number_of_replicas\" : " + StringUtils.defaultIfEmpty(rolloverIndexNumberOfReplicas, monthlyIndexNumberOfReplicas) + "," +
                "        \"mapping.total_fields.limit\" : " + StringUtils.defaultIfEmpty(rolloverIndexMappingTotalFieldsLimit, monthlyIndexMappingTotalFieldsLimit) + "," +
                "        \"max_docvalue_fields_search\" : " + StringUtils.defaultIfEmpty(rolloverIndexMaxDocValueFieldsSearch, monthlyIndexMaxDocValueFieldsSearch) + "," +
                "        \"plugins.index_state_management.rollover_alias\": \"" + rolloverAlias + "\"" +
                "    },";
        String analysisSource =
                "    {" +
                "      \"analyzer\": {" +
                "        \"folding\": {" +
                "          \"type\":\"custom\"," +
                "          \"tokenizer\": \"keyword\"," +
                "          \"filter\":  [ \"lowercase\", \"asciifolding\" ]" +
                "        }\n" +
                "      }\n" +
                "    }\n";
        Map<String, JsonData> settings = new HashMap<>();
        settings.put("index", getJsonData(indexSource));
        settings.put("analysis", getJsonData(analysisSource));
        client.indices().putTemplate(p->
                p.name(rolloverAlias + "-rollover-template")
                        .indexPatterns(Collections.singletonList(getRolloverIndexForQuery(itemName)))
                        .order(1)
                        .settings(settings)
                        .mappings(getTypeMapping(mappings.get(itemName)))
        );
    }

    private JsonData getJsonData(String input) {
        JsonpMapper mapper = client._transport().jsonpMapper();
        JsonParser parser = mapper
                .jsonProvider()
                .createParser(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
        return  JsonData._DESERIALIZER.deserialize(parser, mapper);
    }

    private void internalCreateRolloverIndex(String indexName) throws IOException {
        CreateIndexResponse createIndexResponse = client.indices().create(c->c
                .index(indexName + "-000001")
                .aliases(indexName, a->a.isWriteIndex(true)));
        LOGGER.info("Index created: [{}], acknowledge: [{}], shards acknowledge: [{}]", createIndexResponse.index(),
                createIndexResponse.acknowledged(), createIndexResponse.shardsAcknowledged());
    }

    private void internalCreateIndex(String indexName, String mappingSource) throws IOException {
        CreateIndexResponse createIndexResponse = client.indices().create(c -> c
                .index(indexName)
                .settings(s -> s
                        .index(i -> i
                                .numberOfShards(numberOfShards)
                                .numberOfReplicas(numberOfReplicas)
                                .mapping(m -> m
                                        .totalFields(t -> t
                                                .limit(Long.parseLong(indexMappingTotalFieldsLimit))
                                        )
                                )
                                .maxDocvalueFieldsSearch(Integer.parseInt(indexMaxDocValueFieldsSearch))
                        )
                        .analysis(a -> a
                                .analyzer("folding", an -> an
                                        .custom(cu -> cu
                                                .tokenizer("keyword")
                                                .filter("lowercase", "asciifolding")
                                        )
                                )
                        )
                )
                .mappings(getTypeMapping(mappingSource))
        );
        LOGGER.info("Index created: [{}], acknowledge: [{}], shards acknowledge: [{}]", createIndexResponse.index(),
                createIndexResponse.acknowledged(), createIndexResponse.shardsAcknowledged());
    }

    @Override
    public void createMapping(String type, String source) {
        try {
            putMapping(source, getIndex(type));
        } catch (IOException ioe) {
            LOGGER.error("Error while creating mapping for type " + type + " and source " + source, ioe);
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
                LOGGER.warn("Mapping already exists for type " + itemType + " and property " + property.getItemId());
                return;
            }

            Map<String, Object> propertyMapping = createPropertyMapping(property);
            if (propertyMapping.isEmpty()) {
                return;
            }

            mergePropertiesMapping(subSubMappings, propertyMapping);

            Map<String, Object> mappingsWrapper = new HashMap<>();
            mappingsWrapper.put("properties", mappings);
            final String mappingsSource = OSCustomObjectMapper.getObjectMapper().writeValueAsString(mappingsWrapper);

            putMapping(mappingsSource, getIndex(itemType));
        } catch (IOException ioe) {
            LOGGER.error("Error while creating mapping for type " + itemType + " and property " + property.getValueTypeId(), ioe);
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

    private boolean putMapping(final String source, final String indexName) throws IOException {
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".putMapping", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws Exception {
                try {
                    TypeMapping typeMapping = getTypeMapping(source);
                    PutMappingResponse response = client.indices().putMapping(p -> p
                            .index(indexName)
                            .properties(typeMapping.properties())
                            .dynamicTemplates(typeMapping.dynamicTemplates())
                            .dateDetection(typeMapping.dateDetection())
                            .dynamic(typeMapping.dynamic())
                            .dynamicDateFormats(typeMapping.dynamicDateFormats())
                            .fieldNames(typeMapping.fieldNames())
                            .meta(typeMapping.meta())
                            .numericDetection(typeMapping.numericDetection())
                            .routing(typeMapping.routing())
                            .source(typeMapping.source())
                    );
                    return response.acknowledged();
                } catch (Exception e) {
                    throw new Exception("Cannot create/update mapping", e);
                }
            }
        }.catchingExecuteInClassLoader(true);
        return Objects.requireNonNullElse(result, false);
    }

    @Override
    public Map<String, Map<String, Object>> getPropertiesMapping(final String itemType) {
        return new InClassLoaderExecute<Map<String, Map<String, Object>>>(metricsService, this.getClass().getName() + ".getPropertiesMapping", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            @SuppressWarnings("unchecked")
            protected Map<String, Map<String, Object>> execute(Object... args) throws Exception {

                Request request = new Request("GET", "/" + getIndexNameForQuery(itemType) + "/_mapping");

                // Send the request
                Response response = restClient.performRequest(request);
                String json = EntityUtils.toString(response.getEntity());
                Map<String,Map<String, Map<String, Object>>> indexMappings = (Map<String,Map<String,Map<String,Object>>>) new ObjectMapper().readValue(json, Map.class);

                Map<String,Map<String,Object>> mappings = (Map<String,Map<String,Object>>) indexMappings.get(indexMappings.keySet().iterator().next()); // remove index wrapping object

                // Get all mapping for current itemType

                // create a list of Keys to get the mappings in chronological order
                // in case there is monthly context then the mapping will be added from the oldest to the most recent one
                Set<String> orderedKeys = new TreeSet<>(mappings.keySet());
                Map<String, Map<String, Object>> result = new HashMap<>();
                try {
                    for (String key : orderedKeys) {
                        if (mappings.containsKey(key)) {
                            Map<String, Map<String, Object>> properties = (Map<String, Map<String, Object>>) mappings.get(key).get("properties");
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
                    LOGGER.info("Saving query : " + queryName);
                    String index = getIndex(".percolator");
                    client.index(i->i.id(queryName).index(index).refresh(Refresh.WaitFor).document(query));
                    return true;
                } catch (Exception e) {
                    throw new Exception("Cannot save query", e);
                }
            }
        }.catchingExecuteInClassLoader(true);
        return Objects.requireNonNullElse(result, false);
    }

    @Override
    public boolean saveQuery(String queryName, Condition query) {
        if (query == null) {
            return false;
        }
        saveQuery(queryName, conditionOSQueryBuilderDispatcher.getQuery(query));
        return true;
    }

    @Override
    public boolean removeQuery(final String queryName) {
        Boolean result = new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".removeQuery", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) throws Exception {
                //Index the query = register it in the percolator
                try {
                    String index = getIndex(".percolator");
                    client.delete(d->d.index(index).id(queryName).refresh(Refresh.WaitFor));
                    return true;
                } catch (Exception e) {
                    throw new Exception("Cannot delete query", e);
                }
            }
        }.catchingExecuteInClassLoader(true);
        return Objects.requireNonNullElse(result, false);
    }

    @Override
    public boolean isValidCondition(Condition condition, Item item) {
        try {
            conditionEvaluatorDispatcher.eval(condition, item);
            Query.of(q -> q
                    .bool(b -> b
                            .must(m -> m.ids(i -> i.values(item.getItemId())))
                            .must(conditionOSQueryBuilderDispatcher.buildFilter(condition))));
        } catch (Exception e) {
            LOGGER.error("Failed to validate condition. See debug log level for more information");
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Failed to validate condition, condition={}", condition, e);
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
            LOGGER.error("Eval not supported, continue with query", e);
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

            Query builder = Query.of(q -> q.bool(b -> b
                    .must(Query.of(q2->q2.ids(i->i.values(documentId))))
                    .must(conditionOSQueryBuilderDispatcher.buildFilter(query))));
            return queryCount(builder, itemType) > 0;
        } finally {
            if (metricsService != null && metricsService.isActivated()) {
                metricsService.updateTimer(this.getClass().getName() + ".testMatchInOpenSearch", startTime);
            }
        }
    }


    @Override
    public <T extends Item> List<T> query(final Condition query, String sortBy, final Class<T> clazz) {
        return query(query, sortBy, clazz, 0, -1).getList();
    }

    @Override
    public <T extends Item> PartialList<T> query(final Condition query, String sortBy, final Class<T> clazz, final int offset, final int size) {
        String finalTenantId = validateTenantAndGetId(SecurityServiceConfiguration.PERMISSION_QUERY);

        Query queryBuilder = conditionOSQueryBuilderDispatcher.buildFilter(query);
        return query(queryBuilder, sortBy, clazz, offset, size, null, null);
    }

    @Override
    public <T extends Item> PartialList<T> query(final Condition query, String sortBy, final Class<T> clazz, final int offset, final int size, final String scrollTimeValidity) {
        String finalTenantId = validateTenantAndGetId(SecurityServiceConfiguration.PERMISSION_QUERY);

        Query queryBuilder = conditionOSQueryBuilderDispatcher.buildFilter(query);
        return query(queryBuilder, sortBy, clazz, offset, size, null, scrollTimeValidity);
    }

    @Override
    public PartialList<CustomItem> queryCustomItem(final Condition query, String sortBy, final String customItemType, final int offset, final int size, final String scrollTimeValidity) {
        String finalTenantId = validateTenantAndGetId(SecurityServiceConfiguration.PERMISSION_QUERY);

        Query queryBuilder = conditionOSQueryBuilderDispatcher.getQueryBuilder(query);
        queryBuilder = wrapWithTenantAndItemTypeQuery(customItemType, queryBuilder, finalTenantId);
        return query(queryBuilder, sortBy, customItemType, offset, size, null, scrollTimeValidity);
    }

    @Override
    public <T extends Item> PartialList<T> queryFullText(final String fulltext, final Condition query, String sortBy, final Class<T> clazz, final int offset, final int size) {
        return query(Query.of(q ->
                q.bool(b ->
                        b.must(Query.of(q2 ->
                                        q2.queryString(qs -> qs.query(fulltext))))
                                .must(conditionOSQueryBuilderDispatcher.getQueryBuilder(query)))), sortBy, clazz, offset, size, null, null);
    }

    @Override
    public <T extends Item> List<T> query(final String fieldName, final String fieldValue, String sortBy, final Class<T> clazz) {
        return query(fieldName, fieldValue, sortBy, clazz, 0, -1).getList();
    }

    @Override
    public <T extends Item> List<T> query(final String fieldName, final String[] fieldValues, String sortBy, final Class<T> clazz) {
        List<FieldValue> foldedFieldValues = Arrays.stream(ConditionContextHelper.foldToASCII(fieldValues)).map(FieldValue::of).collect(Collectors.toList());
        return query(Query.of(q->q.terms(t->t.field(fieldName).terms(tv->tv.value(foldedFieldValues)))), sortBy, clazz, 0, -1, getRouting(fieldName, fieldValues, clazz), null).getList();
    }

    @Override
    public <T extends Item> PartialList<T> query(String fieldName, String fieldValue, String sortBy, Class<T> clazz, int offset, int size) {
        return query(Query.of(q->q.term(t->t.field(fieldName).value(v->v.stringValue(ConditionContextHelper.foldToASCII(fieldValue))))), sortBy, clazz, offset, size, getRouting(fieldName, new String[]{fieldValue}, clazz), null);
    }

    @Override
    public <T extends Item> PartialList<T> queryFullText(String fieldName, String fieldValue, String fulltext, String sortBy, Class<T> clazz, int offset, int size) {
        return query(Query.of(q -> q
                        .bool(b -> b
                                .must(Query.of(q2 -> q2
                                                .queryString(q3 -> q3
                                                        .query(fulltext)
                                                )
                                        )
                                )
                                .must(Query.of(q4 -> q4
                                                .term(t -> t
                                                        .field(fieldName)
                                                        .value(v -> v.stringValue(fieldValue)
                                                        )
                                                )
                                        )
                                )
                        )
                ),
                sortBy, clazz, offset, size, getRouting(fieldName, new String[]{fieldValue}, clazz), null);
    }

    @Override
    public <T extends Item> PartialList<T> queryFullText(String fulltext, String sortBy, Class<T> clazz, int offset, int size) {
        return query(Query.of(q->q.queryString(s->s.query(fulltext))), sortBy, clazz, offset, size, null, null);
    }

    @Override
    public <T extends Item> PartialList<T> rangeQuery(String fieldName, String from, String to, String sortBy, Class<T> clazz, int offset, int size) {
        return query(Query.of(q->q.range(r->r.field(fieldName).from(JsonData.of(from)).to(JsonData.of(to)))), sortBy, clazz, offset, size, null, null);
    }

    @Override
    public long queryCount(Condition query, String itemType) {
        try {
            return conditionOSQueryBuilderDispatcher.count(query);
        } catch (UnsupportedOperationException e) {
            try {
                Query filter = conditionOSQueryBuilderDispatcher.buildFilter(query);
                if (filter.isIds()) {
                    return filter.ids().values().size();
                }
                return queryCount(filter, itemType);
            } catch (UnsupportedOperationException e1) {
                return -1;
            }
        }
    }

    private long queryCount(final Query filter, final String itemType) {
        return new InClassLoaderExecute<Long>(metricsService, this.getClass().getName() + ".queryCount", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {

            @Override
            protected Long execute(Object... args) throws IOException {
                CountResponse response = client.count(count -> count.index(getIndexNameForQuery(itemType))
                        .query(wrapWithTenantAndItemTypeQuery(itemType, filter, getTenantId())));
                return response.count();
            }
        }.catchingExecuteInClassLoader(true);
    }

    private <T extends Item> PartialList<T> query(final Query query, final String sortBy, final Class<T> clazz, final int offset, final int size, final String[] routing, final String scrollTimeValidity) {
        return query(query, sortBy, clazz, null, offset, size, routing, scrollTimeValidity);
    }

    private PartialList<CustomItem> query(final Query query, final String sortBy, final String customItemType, final int offset, final int size, final String[] routing, final String scrollTimeValidity) {
        return query(query, sortBy, CustomItem.class, customItemType, offset, size, routing, scrollTimeValidity);
    }

    private <T extends Item> PartialList<T> query(final Query query, final String sortBy, final Class<T> clazz, final String customItemType, final int offset, final int size, final String[] routing, final String scrollTimeValidity) {
        String tenantId = getTenantId();
        validateTenantAndGetId(SecurityServiceConfiguration.PERMISSION_QUERY);
        return new InClassLoaderExecute<PartialList<T>>(metricsService, this.getClass().getName() + ".query", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {

            @Override
            protected PartialList<T> execute(Object... args) throws Exception {
                List<T> results = new ArrayList<>();
                String scrollIdentifier = null;
                long totalHits = 0;
                PartialList.Relation totalHitsRelation = PartialList.Relation.EQUAL;
                try {
                    String itemType = Item.getItemType(clazz);
                    if (customItemType != null) {
                        itemType = customItemType;
                    }
                    String keepAlive;
                    SearchRequest.Builder searchRequest = new SearchRequest.Builder().index(getIndexNameForQuery(itemType));
                    searchRequest.seqNoPrimaryTerm(true)
                            .query(wrapWithTenantAndItemTypeQuery(itemType, query, tenantId))
                            .size(size < 0 ? defaultQueryLimit : size)
                            .source(s->s.fetch(true))
                            .from(offset);
                    if (scrollTimeValidity != null) {
                        keepAlive = scrollTimeValidity;
                        searchRequest.scroll(s->s.time(keepAlive));
                    } else {
                        keepAlive = "1h";
                    }

                    if (size == Integer.MIN_VALUE) {
                        searchRequest.size(defaultQueryLimit);
                    } else if (size != -1) {
                        searchRequest.size(size);
                    } else {
                        // size == -1, use scroll query to retrieve all the results
                        searchRequest.scroll(s->s.time(keepAlive));
                    }
                    if (routing != null) {
                        searchRequest.routing(StringUtils.join(routing, ","));
                    }
                    if (sortBy != null) {
                        String[] sortByArray = sortBy.split(",");
                        for (String sortByElement : sortByArray) {
                            if (sortByElement.startsWith("geo:")) {
                                String[] elements = sortByElement.split(":");
                                GeoDistanceSort.Builder distanceSortBuilder = new GeoDistanceSort.Builder();
                                distanceSortBuilder.field(elements[1]);
                                distanceSortBuilder.location(l->l.latlon(latlon->latlon.lat(Double.parseDouble(elements[2])).lon(Double.parseDouble(elements[3]))));
                                distanceSortBuilder.unit(DistanceUnit.Kilometers);
                                if (elements.length > 4 && elements[4].equals("desc")) {
                                    distanceSortBuilder.order(SortOrder.Desc);
                                    searchRequest.sort(distanceSortBuilder.build()._toSortOptions());
                                } else {
                                    distanceSortBuilder.order(SortOrder.Asc);
                                    searchRequest.sort(distanceSortBuilder.build()._toSortOptions());
                                }
                            } else {
                                String name = getPropertyNameWithData(StringUtils.substringBeforeLast(sortByElement, ":"), itemType);
                                if (name != null) {
                                    if (sortByElement.endsWith(":desc")) {
                                        searchRequest.sort(s->s.field(f->f.field(name).order(SortOrder.Desc)));
                                    } else {
                                        searchRequest.sort(s->s.field(f->f.field(name).order(SortOrder.Asc)));
                                    }
                                } else {
                                    // in the case of no data existing for the property, we will not add the sorting to the request.
                                }

                            }
                        }
                    }
                    searchRequest.version(true);
                    SearchResponse response = client.search(searchRequest.build(), clazz);

                    if (size == -1) {
                        // Scroll until no more hits are returned
                        do {
                            HitsMetadata<T> hitsMetadata = (HitsMetadata<T>) response.hits();
                            List<Hit<T>> hits = hitsMetadata.hits();
                            for (Hit<T> searchHit : hits) {
                                // add hit to results
                                final T value = searchHit.source();
                                setMetadata(value, searchHit.id(), searchHit.version(), searchHit.seqNo(), searchHit.primaryTerm(), searchHit.index());
                                results.add(handleItemReverseTransformation(value));  // Replace decryption with reverse transformation
                            }

                            ScrollRequest searchScrollRequest = new ScrollRequest.Builder().scroll(s -> s.time(keepAlive)).scrollId(response.scrollId()).build();
                            response = client.scroll(searchScrollRequest, clazz);

                            // If we have no more hits, exit
                        } while (!response.hits().hits().isEmpty());
                        SearchResponse finalResponse = response;
                        client.clearScroll(c->c.scrollId(finalResponse.scrollId()));
                    } else {
                        HitsMetadata<T> searchHits = (HitsMetadata<T>) response.hits();
                        scrollIdentifier = response.scrollId();
                        totalHits = searchHits.total().value();
                        totalHitsRelation = getTotalHitsRelation(searchHits.total());
                        if (scrollIdentifier != null && totalHits == 0) {
                            // we have no results, we must clear the scroll request immediately.
                            String finalScrollIdentifier = scrollIdentifier;
                            client.clearScroll(c->c.scrollId(finalScrollIdentifier));
                        }
                        for (Hit<T> searchHit : searchHits.hits()) {
                            final T value = searchHit.source();
                            setMetadata(value, searchHit.id(), searchHit.version(), searchHit.seqNo(), searchHit.primaryTerm(), searchHit.index());
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
        return TotalHitsRelation.Gte.equals(totalHits.relation()) ? PartialList.Relation.GREATER_THAN_OR_EQUAL_TO : PartialList.Relation.EQUAL;
    }

    @Override
    public <T extends Item> PartialList<T> continueScrollQuery(final Class<T> clazz, final String scrollIdentifier, final String scrollTimeValidity) {
        String finalTenantId = validateTenantAndGetId(SecurityServiceConfiguration.PERMISSION_SCROLL_QUERY);

        return new InClassLoaderExecute<PartialList<T>>(metricsService, this.getClass().getName() + ".continueScrollQuery", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {

            @Override
            protected PartialList<T> execute(Object... args) throws Exception {
                List<T> results = new ArrayList<>();
                long totalHits = 0;
                try {
                    String keepAlive = scrollTimeValidity != null ? scrollTimeValidity : "10m";

                    SearchResponse response = client.scroll(s->s.scrollId(scrollIdentifier).scroll(t->t.time(keepAlive)), clazz);

                    if (response.hits().hits().isEmpty()) {
                        client.clearScroll(c->c.scrollId(response.scrollId()));
                    } else {
                        for (Hit<T> searchHit : (List<Hit<T>>) response.hits().hits()) {
                            String sourceTenantId = (String) searchHit.source().getTenantId();
                            if (finalTenantId.equals(sourceTenantId)) {
                                // add hit to results
                                final T value = searchHit.source();
                                setMetadata(value, searchHit.id(), searchHit.version(), searchHit.seqNo(), searchHit.primaryTerm(), searchHit.index());
                                results.add(value);
                            }
                        }
                    }
                    PartialList<T> result = new PartialList<T>(results, 0, response.hits().hits().size(), response.hits().total().value(), getTotalHitsRelation(response.hits().total()));
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
        String tenantId = getTenantId();
        validateTenantAndGetId(SecurityServiceConfiguration.PERMISSION_SCROLL_QUERY);

        return new InClassLoaderExecute<PartialList<CustomItem>>(metricsService, this.getClass().getName() + ".continueScrollQuery", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            @Override
            protected PartialList<CustomItem> execute(Object... args) throws Exception {
                List<CustomItem> results = new ArrayList<>();
                long totalHits = 0;
                try {
                    String keepAlive = scrollTimeValidity != null ? scrollTimeValidity : "10m";

                    SearchResponse<CustomItem> response = client.scroll(s->s.scrollId(scrollIdentifier).scroll(t->t.time(keepAlive)), CustomItem.class);

                    if (response.hits().hits().isEmpty()) {
                        client.clearScroll(c->c.scrollId(response.scrollId()));
                    } else {
                        // Validate tenants for each result
                        for (Hit<CustomItem> searchHit : (List<Hit<CustomItem>>) response.hits().hits()) {
                            String sourceTenantId = (String) searchHit.source().getTenantId();
                            if (tenantId.equals(sourceTenantId)) {
                                // add hit to results
                                final CustomItem value = searchHit.source();
                                setMetadata(value, searchHit.id(), searchHit.version(), searchHit.seqNo(), searchHit.primaryTerm(), searchHit.index());
                                results.add(value);
                            }
                        }
                    }
                    PartialList<CustomItem> result = new PartialList<CustomItem>(results, 0, response.hits().hits().size(), response.hits().total().value(), getTotalHitsRelation(response.hits().total()));
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
        String finalTenantId = validateTenantAndGetId(SecurityServiceConfiguration.PERMISSION_AGGREGATE);

        return new InClassLoaderExecute<Map<String, Long>>(metricsService, this.getClass().getName() + ".aggregateQuery", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {

            @Override
            protected Map<String, Long> execute(Object... args) throws IOException {
                Map<String, Long> results = new LinkedHashMap<>();

                SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder().index(getIndexNameForQuery(itemType));
                searchRequestBuilder.size(0);
                Query matchAll = Query.of(q->q.matchAll(m->m));
                boolean isItemTypeSharingIndex = isItemTypeSharingIndex(itemType);
                searchRequestBuilder.query(wrapWithTenantAndItemTypeQuery(itemType,matchAll, finalTenantId));
                Map<String, Aggregation.Builder.ContainerBuilder> lastAggregation = new LinkedHashMap<>();

                if (aggregate != null) {
                    Aggregation.Builder bucketsAggregationBuilder = new Aggregation.Builder();
                    Aggregation.Builder.ContainerBuilder bucketsContainerBuilder = null;
                    String fieldName = aggregate.getField();
                    if (aggregate instanceof DateAggregate) {
                        DateAggregate dateAggregate = (DateAggregate) aggregate;
                        DateHistogramAggregation.Builder dateHistogramBuilder = new DateHistogramAggregation.Builder()
                                .field(fieldName)
                                .calendarInterval(CalendarInterval.valueOf(dateAggregate.getInterval()));
                        if (dateAggregate.getFormat() != null) {
                            dateHistogramBuilder.format(dateAggregate.getFormat());
                        }
                        bucketsContainerBuilder = bucketsAggregationBuilder.dateHistogram(dateHistogramBuilder.build());
                    } else if (aggregate instanceof NumericRangeAggregate) {
                        RangeAggregation.Builder rangeBuilder = new RangeAggregation.Builder()
                                .field(fieldName);
                        for (NumericRange range : ((NumericRangeAggregate) aggregate).getRanges()) {
                            if (range != null) {
                                if (range.getFrom() != null && range.getTo() != null) {
                                    rangeBuilder.ranges(r -> r.key(range.getKey())
                                            .from(range.getFrom().toString())
                                            .to(range.getTo().toString()));
                                } else if (range.getFrom() != null) {
                                    rangeBuilder.ranges(r -> r.key(range.getKey())
                                            .from(range.getFrom().toString()));
                                } else if (range.getTo() != null) {
                                    rangeBuilder.ranges(r -> r.key(range.getKey())
                                            .to(range.getTo().toString()));
                                }
                            }
                        }
                        bucketsContainerBuilder = bucketsAggregationBuilder.range(rangeBuilder.build());
                    } else if (aggregate instanceof DateRangeAggregate) {
                        DateRangeAggregate dateRangeAggregate = (DateRangeAggregate) aggregate;
                        DateRangeAggregation.Builder rangeBuilder = new DateRangeAggregation.Builder()
                                .field(fieldName);
                        if (dateRangeAggregate.getFormat() != null) {
                            rangeBuilder.format(dateRangeAggregate.getFormat());
                        }
                        for (DateRange range : dateRangeAggregate.getDateRanges()) {
                            if (range != null) {
                                rangeBuilder.ranges(r -> r.key(range.getKey())
                                        .from(f -> f.expr(range.getFrom() != null ? range.getFrom().toString() : null))
                                        .to(t -> t.expr(range.getTo() != null ? range.getTo().toString() : null)));
                            }
                        }
                        bucketsContainerBuilder = bucketsAggregationBuilder.dateRange(rangeBuilder.build());
                    } else if (aggregate instanceof IpRangeAggregate) {
                        IpRangeAggregate ipRangeAggregate = (IpRangeAggregate) aggregate;
                        IpRangeAggregation.Builder rangeBuilder = new IpRangeAggregation.Builder()
                                .field(fieldName);
                        for (IpRange range : ipRangeAggregate.getRanges()) {
                            if (range != null) {
                                rangeBuilder.ranges(r -> r.mask(range.getKey()).from(range.getFrom()).to(range.getTo()));
                            }
                        }
                        bucketsContainerBuilder = bucketsAggregationBuilder.ipRange(rangeBuilder.build());
                    } else {
                        fieldName = getPropertyNameWithData(fieldName, itemType);
                        //default
                        if (fieldName != null) {
                            TermsAggregation.Builder termsBuilder = new TermsAggregation.Builder()
                                    .field(fieldName)
                                    .size(queryBucketSize);
                            if (aggregate instanceof TermsAggregate) {
                                TermsAggregate termsAggregate = (TermsAggregate) aggregate;
                                if (termsAggregate.getPartition() > -1 && termsAggregate.getNumPartitions() > -1) {
                                    termsBuilder.include(i->i.partition(p->p.partition(termsAggregate.getPartition()).numPartitions(termsAggregate.getNumPartitions())));
                                }
                            }
                            bucketsContainerBuilder = bucketsAggregationBuilder.terms(termsBuilder.build());
                        } else {
                            // field name could be null if no existing data exists
                        }
                    }
                    if (bucketsAggregationBuilder != null) {
                        Aggregation.Builder missingAggregationBuilder = new Aggregation.Builder();
                        MissingAggregation.Builder missingBuilder = new MissingAggregation.Builder().field(fieldName);
                        Aggregation.Builder.ContainerBuilder missingContainerBuilder = missingAggregationBuilder.missing(missingBuilder.build());
                        for (Map.Entry<String, Aggregation.Builder.ContainerBuilder> aggregationBuilder : lastAggregation.entrySet()) {
                            bucketsContainerBuilder.aggregations(aggregationBuilder.getKey(), aggregationBuilder.getValue().build());
                            missingContainerBuilder.aggregations(aggregationBuilder.getKey(), aggregationBuilder.getValue().build());
                        }
                        lastAggregation = Map.of("buckets", bucketsContainerBuilder, "missing", missingContainerBuilder);
                    }
                }

                // If the request is optimized then we don't need a global aggregation which is very slow and we can put the query with a
                // filter on range items in the query block so we don't retrieve all the document before filtering the whole
                if (optimizedQuery) {
                    for (Map.Entry<String, Aggregation.Builder.ContainerBuilder> aggregationBuilder : lastAggregation.entrySet()) {
                        searchRequestBuilder.aggregations(aggregationBuilder.getKey(), aggregationBuilder.getValue().build());
                    }

                    if (filter != null) {
                        searchRequestBuilder.query(wrapWithTenantAndItemTypeQuery(itemType, conditionOSQueryBuilderDispatcher.buildFilter(filter), finalTenantId));
                    }
                } else {
                    if (filter != null) {
                        Aggregation.Builder.ContainerBuilder filterAggregationContainerBuilder = new Aggregation.Builder().filter(wrapWithTenantAndItemTypeQuery(itemType, conditionOSQueryBuilderDispatcher.buildFilter(filter), finalTenantId));
                        for (Map.Entry<String,Aggregation.Builder.ContainerBuilder> aggregationBuilder : lastAggregation.entrySet()) {
                            filterAggregationContainerBuilder.aggregations(aggregationBuilder.getKey(), aggregationBuilder.getValue().build());
                        }
                        lastAggregation = Map.of("buckets", filterAggregationContainerBuilder);
                    }

                    Aggregation.Builder.ContainerBuilder globalAggregationContainerBuilder = new Aggregation.Builder().global(g -> g.name("global"));
                    for (Map.Entry<String, Aggregation.Builder.ContainerBuilder> aggregationBuilder : lastAggregation.entrySet()) {
                        globalAggregationContainerBuilder.aggregations(aggregationBuilder.getKey(), aggregationBuilder.getValue().build());
                    }

                    searchRequestBuilder.aggregations("buckets", globalAggregationContainerBuilder.build());
                }

                /*
                 @TODO can we implement this ?
                RequestOptions.Builder builder = RequestOptions.DEFAULT.toBuilder();

                if (aggQueryMaxResponseSizeHttp != null) {
                    builder.setHttpAsyncResponseConsumerFactory(
                            new HttpAsyncResponseConsumerFactory
                                    .HeapBufferedResponseConsumerFactory(aggQueryMaxResponseSizeHttp));
                }

                 */

                SearchResponse<Item> response = client.search(searchRequestBuilder.build(), Item.class);
                Map<String, Aggregate> aggregates = response.aggregations();


                if (aggregates != null) {
                    if (optimizedQuery) {
                        if (response.hits() != null) {
                            results.put("_filtered", response.hits().total().value());
                        }
                    } else {
                        GlobalAggregate globalAgg = aggregates.get("global").global();
                        results.put("_all", globalAgg.docCount());
                        aggregates = globalAgg.aggregations();

                        if (aggregates.get("filter") != null) {
                            FilterAggregate filterAgg = aggregates.get("filter").filter();
                            results.put("_filtered", filterAgg.docCount());
                            aggregates = filterAgg.aggregations();
                        }
                    }
                    if (aggregates.get("buckets") != null) {

                        if (aggQueryThrowOnMissingDocs) {
                            if (aggregates.get("buckets").isSterms()) {
                                StringTermsAggregate terms = aggregates.get("buckets").sterms();
                                if (terms.docCountErrorUpperBound() > 0 || terms.sumOtherDocCount() > 0) {
                                    throw new UnsupportedOperationException("Some docs are missing in aggregation query. docCountError is:" +
                                            terms.docCountErrorUpperBound() + " sumOfOtherDocCounts:" + terms.sumOtherDocCount());
                                }
                            }
                        }

                        long totalDocCount = 0;
                        Aggregate bucketsAggregate = aggregates.get("buckets");
                        if (bucketsAggregate.isSterms()) {
                            for (StringTermsBucket bucket : bucketsAggregate.sterms().buckets().array()) {
                                results.put(bucket.key(), bucket.docCount());
                                totalDocCount += bucket.docCount();
                            }
                        }
                        MissingAggregate missing = aggregates.get("missing").missing();
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

    @Override
    public void refresh() {
        new InClassLoaderExecute<Boolean>(metricsService, this.getClass().getName() + ".refresh", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            protected Boolean execute(Object... args) {
                try {
                    client.indices().refresh(r->r);
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
                    client.indices().refresh(r->r.index(index));
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
                    removeByQuery(Query.of(q->q.range(r->r.field("timeStamp").lte(JsonData.of("now-" + existsNumberOfDays + "d")))), clazz);

                    // get count per index for those time based data
                    TreeMap<String, Long> countsPerIndex = new TreeMap<>();
                    GetIndexResponse getIndexResponse = client.indices().get(g->g.index(getIndexNameForQuery(itemType)));
                    for (String index : getIndexResponse.result().keySet()) {
                        countsPerIndex.put(index, client.count(c->c.index(index)).count());
                    }

                    // Check for count=0 and remove them
                    if (!countsPerIndex.isEmpty()) {
                        // do not check the last index, because it's the one used to write documents
                        countsPerIndex.pollLastEntry();

                        for (Map.Entry<String, Long> indexCount : countsPerIndex.entrySet()) {
                            if (indexCount.getValue() == 0) {
                                client.indices().delete(d->d.index(indexCount.getKey()));
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
        String finalTenantId = validateTenantAndGetId(SecurityServiceConfiguration.PERMISSION_PURGE);

        LOGGER.debug("Purge scope {}", scope);
        new InClassLoaderExecute<Void>(metricsService, this.getClass().getName() + ".purgeWithScope", this.bundleContext, this.fatalIllegalStateErrors, throwExceptions) {
            @Override
            protected Void execute(Object... args) throws IOException {
                SearchResponse<Item> response = client.search(s -> s
                        .query(q -> q
                                .bool(b -> b
                                        .must(m -> m
                                                .term(t -> t
                                                        .field("scope")
                                                        .value(v -> v.stringValue(scope))
                                                )
                                        )
                                        .must(m -> m
                                                .term(t -> t
                                                        .field("tenantId")
                                                        .value(v -> v.stringValue(ConditionContextHelper.foldToASCII(finalTenantId)))
                                                )
                                        )
                                )
                        )
                        .size(100)
                        .scroll(scr -> scr.time("1h"))
                        .index(getAllIndexForQuery())
                        , Item.class);

                // Scroll until no more hits are returned
                List<BulkOperation> bulkOperations = new ArrayList<>();
                while (true) {
                    for (Hit<Item> hit : response.hits().hits()) {
                        // add hit to bulk delete
                        bulkOperations.add(BulkOperation.of(b -> b.delete(d->d.index(hit.index()).id(hit.id()))));
                    }

                    SearchResponse<Item> finalResponse = response;
                    response = client.scroll(scr -> scr.scrollId(finalResponse.scrollId()).scroll(t -> t.time("1h")), Item.class);

                    // If we have no more hits, exit
                    if (response.hits().hits().isEmpty()) {
                        SearchResponse<Item> clearfinalResponse = response;
                        client.clearScroll(c -> c.scrollId(clearfinalResponse.scrollId()));
                        break;
                    }
                }

                // we're done with the scrolling, delete now
                if (!bulkOperations.isEmpty()) {
                    final BulkResponse deleteResponse = client.bulk(b -> b.operations(bulkOperations));
                    if (deleteResponse.errors()) {
                        // do something
                        LOGGER.warn("Couldn't delete from scope " + scope + ":\n{}", deleteResponse);
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
                Map<String, Double> results = new LinkedHashMap<>();

                Aggregation.Builder.ContainerBuilder filterAggregationContainerBuilder = new Aggregation.Builder().filter(conditionOSQueryBuilderDispatcher.buildFilter(condition));

                if (metrics != null) {
                    for (String metric : metrics) {
                        switch (metric) {
                            case "sum":
                                filterAggregationContainerBuilder.aggregations("sum", Aggregation.of(a -> a.sum(s -> s.field(field))));
                                break;
                            case "avg":
                                filterAggregationContainerBuilder.aggregations("avg", Aggregation.of(a -> a.avg(s -> s.field(field))));
                                break;
                            case "min":
                                filterAggregationContainerBuilder.aggregations("min", Aggregation.of(a -> a.min(s -> s.field(field))));
                                break;
                            case "max":
                                filterAggregationContainerBuilder.aggregations("max", Aggregation.of(a -> a.max(s -> s.field(field))));
                                break;
                            case "card":
                                filterAggregationContainerBuilder.aggregations("card", Aggregation.of(a -> a.cardinality(s -> s.field(field))));
                                break;
                            case "count":
                                filterAggregationContainerBuilder.aggregations("count", Aggregation.of(a -> a.valueCount(s -> s.field(field))));
                                break;
                        }
                    }
                }
                SearchResponse<Item> response = client.search(s -> s
                        .index(getIndexNameForQuery(itemType))
                        .size(0)
                        .aggregations("metrics", filterAggregationContainerBuilder.build())
                        .query(isItemTypeSharingIndex(itemType) ? getItemTypeQueryBuilder(itemType) : Query.of(q -> q.matchAll(i -> i)))
                        , Item.class
                );

                Map<String, Aggregate> aggregations = response.aggregations();
                if (aggregations != null) {
                    FilterAggregate metricsResults = aggregations.get("metrics").filter();
                    if (!metricsResults.aggregations().isEmpty()) {
                        for (Map.Entry<String, Aggregate> aggregationEntry : metricsResults.aggregations().entrySet()) {
                            Aggregate aggregateValue = aggregationEntry.getValue();
                            if (aggregateValue.isSum()) {
                                results.put("_" + aggregationEntry.getKey(), aggregateValue.sum().value());
                            } else if (aggregateValue.isAvg()) {
                                results.put("_" + aggregationEntry.getKey(), aggregateValue.avg().value());
                            } else if (aggregateValue.isMin()) {
                                results.put("_" + aggregationEntry.getKey(), aggregateValue.min().value());
                            } else if (aggregateValue.isMax()) {
                                results.put("_" + aggregationEntry.getKey(), aggregateValue.max().value());
                            } else if (aggregateValue.isCardinality()) {
                                results.put("_" + aggregationEntry.getKey(), new Double(aggregateValue.cardinality().value()));
                            } else if (aggregateValue.isValueCount()) {
                                results.put("_" + aggregationEntry.getKey(), aggregateValue.valueCount().value());
                            }
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
        int firstUnderscore = documentId.indexOf('_');
        if (firstUnderscore < 0) {
            return documentId;
        }
        return documentId.substring(firstUnderscore + 1);
    }

    private Query getItemTypeQueryBuilder(String itemType) {
        return Query.of(q -> q
                .term(t -> t
                        .field("itemType")
                        .value(v -> v.stringValue(ConditionContextHelper.foldToASCII(itemType)))
                )
        );
    }

    private boolean isItemTypeSharingIndex(String itemType) {
        return itemTypeIndexNameMap.containsKey(itemType);
    }

    private boolean isItemTypeRollingOver(String itemType) {
        return (rolloverIndices != null ? rolloverIndices : itemsMonthlyIndexed).contains(itemType);
    }

    private Refresh getRefreshPolicy(String itemType) {
        if (itemTypeToRefreshPolicy.containsKey(itemType)) {
            return itemTypeToRefreshPolicy.get(itemType);
        }
        return Refresh.False;
    }

    private void logMetadataItemOperation (String operation, Item item) {
        if (item instanceof MetadataItem) {
            LOGGER.info("Item of type {} with ID {} has been {}", item.getItemType(), item.getItemId(), operation);
        }
    }

    private void waitForClusterHealth() throws Exception {
        LOGGER.info("Checking cluster health (minimum required state: {})...", minimalClusterState);
        HealthStatus requiredStatus = getHealthStatus(minimalClusterState);

        for (int attempt = 1; attempt <= clusterHealthRetries; attempt++) {
            try {
                HealthResponse health = client.cluster().health(new HealthRequest.Builder()
                    .waitForStatus(requiredStatus)
                    .timeout(t -> t.time(String.valueOf(clusterHealthTimeout) + "s"))
                    .build());

                if (health.status() == HealthStatus.Green) {
                    logClusterHealth(health, "Cluster status is GREEN - fully operational");
                    return;
                } else if (health.status() == HealthStatus.Yellow && "YELLOW".equals(minimalClusterState)) {
                    logClusterHealth(health, "Cluster status is YELLOW - operating with reduced redundancy");
                    return;
                }

                if (attempt == clusterHealthRetries && requiredStatus == HealthStatus.Green) {
                    LOGGER.warn("Unable to achieve GREEN status after {} attempts. Checking if YELLOW status is acceptable...", clusterHealthRetries);
                    if ("YELLOW".equals(minimalClusterState)) {
                        requiredStatus = HealthStatus.Yellow;
                        attempt = 0; // Reset attempts for yellow status check
                        continue;
                    }
                }

                logClusterHealth(health, "Cluster health check attempt " + attempt + " of " + clusterHealthRetries);

            } catch (OpenSearchException e) {
                if (e.getMessage().contains("408")) {
                    LOGGER.warn("Cluster health check timeout on attempt {} of {}", attempt, clusterHealthRetries);
                } else {
                    throw e;
                }
            }

            if (attempt < clusterHealthRetries) {
                Thread.sleep(1000); // Wait 1 second between attempts
            }
        }

        // Final check with detailed diagnostics if we couldn't achieve desired status
        try {
            HealthResponse finalHealth = client.cluster().health(new HealthRequest.Builder().build());
            String message = String.format("Could not achieve %s status after %d attempts. Current status: %s",
                minimalClusterState, clusterHealthRetries, finalHealth.status());
            logClusterHealth(finalHealth, message);

            if ("YELLOW".equals(minimalClusterState) && finalHealth.status() != HealthStatus.Red) {
                return; // Accept current state if yellow is minimum and we're not red
            }

            throw new Exception(message);
        } catch (OpenSearchException e) {
            throw new Exception("Failed to get final cluster health status", e);
        }
    }

    private void logClusterHealth(HealthResponse health, String message) {
        LOGGER.info("{}\nCluster Details:\n" +
            "- Status: {}\n" +
            "- Nodes: {} (data nodes: {})\n" +
            "- Shards: {} active ({} primary, {} relocating, {} initializing, {} unassigned)\n" +
            "- Active shards: {}%",
            message,
            health.status(),
            health.numberOfNodes(), health.numberOfDataNodes(),
            health.activeShards(), health.activePrimaryShards(),
            health.relocatingShards(), health.initializingShards(), health.unassignedShards(),
            health.activeShardsPercentAsNumber());
    }

    public static HealthStatus getHealthStatus(String value) {
        for (HealthStatus status : HealthStatus.values()) {
            if (status.jsonValue().equalsIgnoreCase(value)) {
                return status;
            }
            if (status.aliases() != null) {
                for (String alias : status.aliases()) {
                    if (alias.equalsIgnoreCase(value)) {
                        return status;
                    }
                }
            }
        }
        throw new IllegalArgumentException("Unknown HealthStatus: " + value);
    }

    private Query wrapWithTenantAndItemTypeQuery(String itemType, Query originalQuery, String tenantId) {
        return Query.of(q -> q
                .bool(b -> {
                    // Add tenants filter
                    if (tenantId != null) {
                        b.must(Query.of(q2 -> q2.term(t -> t.field("tenantId").value(v -> v.stringValue(ConditionContextHelper.foldToASCII(tenantId))))));
                    }

                    // Add item type filter if needed
                    if (isItemTypeSharingIndex(itemType)) {
                        b.must(getItemTypeQueryBuilder(itemType));
                    }

                    // Add original query
                    if (originalQuery != null) {
                        b.must(originalQuery);
                    }

                    return b;
                }));
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
            CountResponse response = client.count(c -> c
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

            SearchResponse<Item> searchResponse = client.search(s -> s
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
                    client.bulk(b -> b.operations(operations));
                }

                final String finalScrollId = scrollId;
                // Get next batch
                searchResponse = client.scroll(s -> s
                    .scrollId(finalScrollId)
                    .scroll(t -> t.time("1m")),
                    Item.class);

                scrollId = searchResponse.scrollId();
            }
            // Clear scroll
            final String finalScrollId = scrollId;
            client.clearScroll(c -> c.scrollId(finalScrollId));

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
            CountResponse response = client.count(c -> c
                .index(getAllIndexForQuery())
                .query(query));

            return response.count();

        } catch (IOException e) {
            LOGGER.error("Error getting API call count for tenant " + tenantId, e);
            return -1;
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

    private void bindTransformationListener(ServiceReference<TenantTransformationListener> listenerReference) {
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

    private Query wrapWithTenantAndItemsTypeQuery(String[] itemTypes, Query originalQuery, String tenantId) {
        if (itemTypes.length == 1) {
            return wrapWithTenantAndItemTypeQuery(itemTypes[0], originalQuery, tenantId);
        }

        if (Arrays.stream(itemTypes).anyMatch(this::isItemTypeSharingIndex)) {
            return Query.of(q -> q
                    .bool(b -> {
                        // Add tenant filter if provided
                        if (tenantId != null) {
                            b.must(Query.of(q2 -> q2.term(t -> t.field("tenantId").value(v -> v.stringValue(ConditionContextHelper.foldToASCII(tenantId))))));
                        }

                        // Add original query and item types filter
                        b.must(originalQuery)
                         .filter(f -> f
                                .bool(b2 -> b2
                                        .minimumShouldMatch("1")
                                        .should(Arrays
                                                .stream(itemTypes)
                                                .map(this::getItemTypeQueryBuilder)
                                                .collect(Collectors.toList())
                                        )
                                )
                        );
                        return b;
                    })
            );
        }
        return originalQuery;
    }

    public void bindContextManager(ExecutionContextManager contextManager   ) {
        this.contextManager = contextManager;
        LOGGER.info("ContextManager bound");
    }

    public void unbindContextManager(ExecutionContextManager contextManager) {
        if (this.contextManager == contextManager) {
            this.contextManager = null;
            LOGGER.info("ContextManager unbound");
        }
    }

}
