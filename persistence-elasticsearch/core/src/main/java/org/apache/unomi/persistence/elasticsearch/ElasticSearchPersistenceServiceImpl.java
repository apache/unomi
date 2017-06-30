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

import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.TimestampedItem;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.DateRange;
import org.apache.unomi.api.query.IpRange;
import org.apache.unomi.api.query.NumericRange;
import org.apache.unomi.persistence.elasticsearch.conditions.*;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.aggregate.*;
import org.elasticsearch.action.admin.cluster.node.info.NodeInfo;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.action.admin.indices.stats.IndicesStatsResponse;
import org.elasticsearch.action.bulk.*;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.Requests;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.reindex.BulkIndexByScrollResponse;
import org.elasticsearch.index.reindex.UpdateByQueryAction;
import org.elasticsearch.index.reindex.UpdateByQueryRequestBuilder;
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
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.range.date.DateRangeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.range.ip.IpRangeAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.InternalNumericMetricsAggregation;
import org.elasticsearch.search.sort.GeoDistanceSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@SuppressWarnings("rawtypes")
public class ElasticSearchPersistenceServiceImpl implements PersistenceService, SynchronousBundleListener {

    public static final String NUMBER_OF_SHARDS = "number_of_shards";
    public static final String NUMBER_OF_REPLICAS = "number_of_replicas";
    public static final String CLUSTER_NAME = "cluster.name";
    public static final String BULK_PROCESSOR_NAME = "bulkProcessor.name";
    public static final String BULK_PROCESSOR_CONCURRENT_REQUESTS = "bulkProcessor.concurrentRequests";
    public static final String BULK_PROCESSOR_BULK_ACTIONS = "bulkProcessor.bulkActions";
    public static final String BULK_PROCESSOR_BULK_SIZE = "bulkProcessor.bulkSize";
    public static final String BULK_PROCESSOR_FLUSH_INTERVAL = "bulkProcessor.flushInterval";
    public static final String BULK_PROCESSOR_BACKOFF_POLICY = "bulkProcessor.backoffPolicy";
    private static final Logger logger = LoggerFactory.getLogger(ElasticSearchPersistenceServiceImpl.class.getName());
    private TransportClient client;
    private BulkProcessor bulkProcessor;
    private String elasticSearchAddresses;
    private List<String> elasticSearchAddressList = new ArrayList<>();
    private String clusterName;
    private String indexName;
    private String monthlyIndexNumberOfShards;
    private String monthlyIndexNumberOfReplicas;
    private String numberOfShards;
    private String numberOfReplicas;
    private BundleContext bundleContext;
    private Map<String, String> mappings = new HashMap<String, String>();
    private ConditionEvaluatorDispatcher conditionEvaluatorDispatcher;
    private ConditionESQueryBuilderDispatcher conditionESQueryBuilderDispatcher;

    private Map<String, String> indexNames;
    private List<String> itemsMonthlyIndexed;
    private Map<String, String> routingByType;
    private Set<String> existingIndexNames = new TreeSet<String>();

    private Integer defaultQueryLimit = 10;

    private Timer timer;

    private String bulkProcessorName = "unomi-bulk";
    private String bulkProcessorConcurrentRequests = "1";
    private String bulkProcessorBulkActions = "1000";
    private String bulkProcessorBulkSize = "5MB";
    private String bulkProcessorFlushInterval = "5s";
    private String bulkProcessorBackoffPolicy = "exponential";

    private String minimalElasticSearchVersion = "5.0.0";
    private String maximalElasticSearchVersion = "5.3.0";

    private Map<String, Map<String, Map<String, Object>>> knownMappings = new HashMap<>();

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

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public void setMonthlyIndexNumberOfShards(String monthlyIndexNumberOfShards) {
        this.monthlyIndexNumberOfShards = monthlyIndexNumberOfShards;
    }

    public void setMonthlyIndexNumberOfReplicas(String monthlyIndexNumberOfReplicas) {
        this.monthlyIndexNumberOfReplicas = monthlyIndexNumberOfReplicas;
    }

    public void setNumberOfShards(String numberOfShards) {
        this.numberOfShards = numberOfShards;
    }

    public void setNumberOfReplicas(String numberOfReplicas) {
        this.numberOfReplicas = numberOfReplicas;
    }

    public void setDefaultQueryLimit(Integer defaultQueryLimit) {
        this.defaultQueryLimit = defaultQueryLimit;
    }

    public void setItemsMonthlyIndexed(List<String> itemsMonthlyIndexed) {
        this.itemsMonthlyIndexed = itemsMonthlyIndexed;
    }

    public void setIndexNames(Map<String, String> indexNames) {
        this.indexNames = indexNames;
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

    public void setBulkProcessorName(String bulkProcessorName) {
        this.bulkProcessorName = bulkProcessorName;
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

    public void setMinimalElasticSearchVersion(String minimalElasticSearchVersion) {
        this.minimalElasticSearchVersion = minimalElasticSearchVersion;
    }

    public void setMaximalElasticSearchVersion(String maximalElasticSearchVersion) {
        this.maximalElasticSearchVersion = maximalElasticSearchVersion;
    }

    public void start() throws Exception {

        loadPredefinedMappings(bundleContext, false);

        // on startup
        new InClassLoaderExecute<Object>() {
            public Object execute(Object... args) throws Exception {
                logger.info("Connecting to ElasticSearch persistence backend using cluster name " + clusterName + " and index name " + indexName + "...");

                bulkProcessorName = System.getProperty(BULK_PROCESSOR_NAME, bulkProcessorName);
                bulkProcessorConcurrentRequests = System.getProperty(BULK_PROCESSOR_CONCURRENT_REQUESTS, bulkProcessorConcurrentRequests);
                bulkProcessorBulkActions = System.getProperty(BULK_PROCESSOR_BULK_ACTIONS, bulkProcessorBulkActions);
                bulkProcessorBulkSize = System.getProperty(BULK_PROCESSOR_BULK_SIZE, bulkProcessorBulkSize);
                bulkProcessorFlushInterval = System.getProperty(BULK_PROCESSOR_FLUSH_INTERVAL, bulkProcessorFlushInterval);
                bulkProcessorBackoffPolicy = System.getProperty(BULK_PROCESSOR_BACKOFF_POLICY, bulkProcessorBackoffPolicy);

                Settings transportSettings = Settings.builder()
                        .put(CLUSTER_NAME, clusterName).build();

                // this property is used for integration tests, to make sure we don't conflict with an already running ElasticSearch instance.
                if (System.getProperty("org.apache.unomi.itests.elasticsearch.transport.port") != null) {
                    elasticSearchAddressList.clear();
                    elasticSearchAddressList.add("localhost:" + System.getProperty("org.apache.unomi.itests.elasticsearch.transport.port"));
                }

                client = new PreBuiltTransportClient(transportSettings);
                for (String elasticSearchAddress : elasticSearchAddressList) {
                    String[] elasticSearchAddressParts = elasticSearchAddress.split(":");
                    String elasticSearchHostName = elasticSearchAddressParts[0];
                    int elasticSearchPort = Integer.parseInt(elasticSearchAddressParts[1]);
                    try {
                        client.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(elasticSearchHostName), elasticSearchPort));
                    } catch (UnknownHostException e) {
                        String message = "Error resolving address " + elasticSearchAddress + " ElasticSearch transport client not connected";
                        throw new Exception(message, e);
                    }
                }

                // let's now check the versions of all the nodes in the cluster, to make sure they are as expected.
                try {
                    NodesInfoResponse nodesInfoResponse = client.admin().cluster().prepareNodesInfo()
                            .all().execute().get();

                    org.elasticsearch.Version minimalVersion = org.elasticsearch.Version.fromString(minimalElasticSearchVersion);
                    org.elasticsearch.Version maximalVersion = org.elasticsearch.Version.fromString(maximalElasticSearchVersion);
                    for (NodeInfo nodeInfo : nodesInfoResponse.getNodes()) {
                        org.elasticsearch.Version version = nodeInfo.getVersion();
                        if (version.before(minimalVersion) ||
                                version.equals(maximalVersion) ||
                                version.after(maximalVersion)) {
                            throw new Exception("ElasticSearch version on node " + nodeInfo.getHostname() + " is not within [" + minimalVersion + "," + maximalVersion + "), aborting startup !");
                        }
                    }
                } catch (InterruptedException e) {
                    throw new Exception("Error checking ElasticSearch versions", e);
                } catch (ExecutionException e) {
                    throw new Exception("Error checking ElasticSearch versions", e);
                }

                // @todo is there a better way to detect index existence than to wait for it to startup ?
                boolean indexExists = false;
                int tries = 0;

                while (!indexExists && tries < 20) {

                    IndicesExistsResponse indicesExistsResponse = client.admin().indices().prepareExists(indexName).execute().actionGet();
                    indexExists = indicesExistsResponse.isExists();
                    tries++;
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        logger.error("Interrupted", e);
                    }
                }
                if (!indexExists) {
                    logger.info("{} index doesn't exist yet, creating it...", indexName);
                    Map<String, String> indexMappings = new HashMap<String, String>();
                    indexMappings.put("_default_", mappings.get("_default_"));
                    for (Map.Entry<String, String> entry : mappings.entrySet()) {
                        if (!itemsMonthlyIndexed.contains(entry.getKey()) && !indexNames.containsKey(entry.getKey())) {
                            indexMappings.put(entry.getKey(), entry.getValue());
                        }
                    }

                    internalCreateIndex(indexName, indexMappings);
                } else {
                    logger.info("Found index {}, ElasticSearch started successfully.", indexName);
                    for (Map.Entry<String, String> entry : mappings.entrySet()) {
                        createMapping(entry.getKey(), entry.getValue());
                    }
                }

                client.admin().indices().preparePutTemplate(indexName + "_monthlyindex")
                        .setTemplate(indexName + "-*")
                        .setOrder(1)
                        .setSettings(Settings.builder()
                                .put(NUMBER_OF_SHARDS, Integer.parseInt(monthlyIndexNumberOfShards))
                                .put(NUMBER_OF_REPLICAS, Integer.parseInt(monthlyIndexNumberOfReplicas))
                                .build()).execute().actionGet();

                getMonthlyIndex(new Date(), true);

                if (client != null && bulkProcessor == null) {
                    bulkProcessor = getBulkProcessor();
                }

                refreshExistingIndexNames();

                logger.info("Waiting for GREEN cluster status...");

                client.admin().cluster().prepareHealth()
                        .setWaitForGreenStatus()
                        .get();

                logger.info("Cluster status is GREEN");

                return true;
            }
        }.executeInClassLoader();


        bundleContext.addBundleListener(this);

        timer = new Timer();

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                GregorianCalendar gc = new GregorianCalendar();
                int thisMonth = gc.get(Calendar.MONTH);
                gc.add(Calendar.DAY_OF_MONTH, 1);
                if (gc.get(Calendar.MONTH) != thisMonth) {
                    String monthlyIndex = getMonthlyIndex(gc.getTime(), true);
                    existingIndexNames.add(monthlyIndex);
                }
            }
        }, 10000L, 24L * 60L * 60L * 1000L);

        // load predefined mappings and condition dispatchers of any bundles that were started before this one.
        for (Bundle existingBundle : bundleContext.getBundles()) {
            if (existingBundle.getBundleContext() != null) {
                loadPredefinedMappings(existingBundle.getBundleContext(), true);
            }
        }

        logger.info(this.getClass().getName() + " service started successfully.");
    }

    private void refreshExistingIndexNames() {
        new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) throws Exception {
                try {
                    logger.info("Refreshing existing indices list...");
                    IndicesStatsResponse indicesStatsResponse = client.admin().indices().prepareStats().all().execute().get();
                    existingIndexNames = new TreeSet<>(indicesStatsResponse.getIndices().keySet());
                } catch (InterruptedException e) {
                    throw new Exception("Error retrieving indices stats", e);
                } catch (ExecutionException e) {
                    throw new Exception("Error retrieving indices stats", e);
                }
                return true;
            }
        }.catchingExecuteInClassLoader(true);
    }

    public BulkProcessor getBulkProcessor() {
        if (bulkProcessor != null) {
            return bulkProcessor;
        }
        BulkProcessor.Builder bulkProcessorBuilder = BulkProcessor.builder(
                client,
                new BulkProcessor.Listener() {
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
                });
        if (bulkProcessorName != null && bulkProcessorName.length() > 0) {
            bulkProcessorBuilder.setName(bulkProcessorName);
        }
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

        new InClassLoaderExecute<Object>() {
            protected Object execute(Object... args) {
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

        if (timer != null) {
            timer.cancel();
            timer = null;
        }

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
                break;
        }
    }

    private String getMonthlyIndex(Date date) {
        return getMonthlyIndex(date, false);
    }

    private String getMonthlyIndex(Date date, boolean checkAndCreate) {
        String d = new SimpleDateFormat("-YYYY-MM").format(date);
        String monthlyIndexName = indexName + d;

        if (checkAndCreate) {
            IndicesExistsResponse indicesExistsResponse = client.admin().indices().prepareExists(monthlyIndexName).execute().actionGet();
            boolean indexExists = indicesExistsResponse.isExists();
            if (!indexExists) {
                logger.info("{} index doesn't exist yet, creating it...", monthlyIndexName);

                Map<String, String> indexMappings = new HashMap<String, String>();
                indexMappings.put("_default_", mappings.get("_default_"));
                for (Map.Entry<String, String> entry : mappings.entrySet()) {
                    if (itemsMonthlyIndexed.contains(entry.getKey())) {
                        indexMappings.put(entry.getKey(), entry.getValue());
                    }
                }

                internalCreateIndex(monthlyIndexName, indexMappings);
                logger.info("{} index created.", monthlyIndexName);
            }
        }
        return monthlyIndexName;
    }

    private void loadPredefinedMappings(BundleContext bundleContext, boolean createMapping) {
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
                BufferedReader reader = new BufferedReader(new InputStreamReader(predefinedMappingURL.openStream()));

                StringBuilder content = new StringBuilder();
                String l;
                while ((l = reader.readLine()) != null) {
                    content.append(l);
                }
                String mappingSource = content.toString();
                mappings.put(name, mappingSource);
                if (createMapping) {
                    createMapping(name, mappingSource);
                }
            } catch (Exception e) {
                logger.error("Error while loading mapping definition " + predefinedMappingURL, e);
            }
        }
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
        return query(QueryBuilders.matchAllQuery(), sortBy, clazz, offset, size, null, null);
    }

    @Override
    public <T extends Item> T load(final String itemId, final Class<T> clazz) {
        return load(itemId, null, clazz);
    }

    @Override
    public <T extends Item> T load(final String itemId, final Date dateHint, final Class<T> clazz) {
        return new InClassLoaderExecute<T>() {
            protected T execute(Object... args) throws Exception {
                try {
                    String itemType = (String) clazz.getField("ITEM_TYPE").get(null);

                    if (itemsMonthlyIndexed.contains(itemType) && dateHint == null) {
                        PartialList<T> r = query(QueryBuilders.idsQuery(itemType).addIds(itemId), null, clazz, 0, 1, null, null);
                        if (r.size() > 0) {
                            return r.get(0);
                        }
                        return null;
                    } else {
                        String index = indexNames.containsKey(itemType) ? indexNames.get(itemType) :
                                (itemsMonthlyIndexed.contains(itemType) ? getMonthlyIndex(dateHint) : indexName);

                        GetResponse response = client.prepareGet(index, itemType, itemId)
                                .execute()
                                .actionGet();
                        if (response.isExists()) {
                            String sourceAsString = response.getSourceAsString();
                            final T value = CustomObjectMapper.getObjectMapper().readValue(sourceAsString, clazz);
                            value.setItemId(response.getId());
                            return value;
                        } else {
                            return null;
                        }
                    }
                } catch (IndexNotFoundException e) {
                    throw new Exception("No index found for itemType=" + clazz.getName() + " itemId=" + itemId, e);
                } catch (IllegalAccessException e) {
                    throw new Exception("Error loading itemType=" + clazz.getName() + " itemId=" + itemId, e);
                } catch (Exception t) {
                    throw new Exception("Error loading itemType=" + clazz.getName() + " itemId=" + itemId, t);
                }
            }
        }.catchingExecuteInClassLoader(true);

    }

    @Override
    public boolean save(final Item item) {
        return save(item, false);
    }

    @Override
    public boolean save(final Item item, final boolean useBatching) {
        Boolean result =  new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) throws Exception {
                try {
                    String source = CustomObjectMapper.getObjectMapper().writeValueAsString(item);
                    String itemType = item.getItemType();
                    String index = indexNames.containsKey(itemType) ? indexNames.get(itemType) :
                            (itemsMonthlyIndexed.contains(itemType) ? getMonthlyIndex(((TimestampedItem) item).getTimeStamp()) : indexName);
                    IndexRequestBuilder indexBuilder = client.prepareIndex(index, itemType, item.getItemId())
                            .setSource(source);
                    if (routingByType.containsKey(itemType)) {
                        indexBuilder = indexBuilder.setRouting(routingByType.get(itemType));
                    }

                    if (!existingIndexNames.contains(index)) {
                        // index probably doesn't exist, unless something else has already created it.
                        if (itemsMonthlyIndexed.contains(itemType)) {
                            Date timeStamp = ((TimestampedItem) item).getTimeStamp();
                            if (timeStamp != null) {
                                getMonthlyIndex(timeStamp, true);
                            } else {
                                logger.warn("Missing time stamp on item " + item + " id=" + item.getItemId() + " can't create related monthly index !");
                            }
                        } else {
                            // this is not a timestamped index, should we create it anyway ?
                            createIndex(index);
                        }
                    }

                    try {
                        if (bulkProcessor == null || !useBatching) {
                            indexBuilder.execute().actionGet();
                        } else {
                            bulkProcessor.add(indexBuilder.request());
                        }
                    } catch (IndexNotFoundException e) {
                        if (existingIndexNames.contains(index)) {
                            existingIndexNames.remove(index);
                        }
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
    public boolean update(final String itemId, final Date dateHint, final Class clazz, final String propertyName, final Object propertyValue) {
        return update(itemId, dateHint, clazz, Collections.singletonMap(propertyName, propertyValue));
    }

    @Override
    public boolean update(final String itemId, final Date dateHint, final Class clazz, final Map source) {
        Boolean result = new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) throws Exception {
                try {
                    String itemType = (String) clazz.getField("ITEM_TYPE").get(null);

                    String index = indexNames.containsKey(itemType) ? indexNames.get(itemType) :
                            (itemsMonthlyIndexed.contains(itemType) && dateHint != null ? getMonthlyIndex(dateHint) : indexName);

                    if (bulkProcessor == null) {
                        client.prepareUpdate(index, itemType, itemId).setDoc(source)
                                .execute()
                                .actionGet();
                    } else {
                        UpdateRequest updateRequest = client.prepareUpdate(index, itemType, itemId).setDoc(source).request();
                        bulkProcessor.add(updateRequest);
                    }
                    return true;
                } catch (IndexNotFoundException e) {
                    throw new Exception("No index found for itemType=" + clazz.getName() + "itemId=" + itemId, e);
                } catch (NoSuchFieldException e) {
                    throw new Exception("Error updating item " + itemId, e);
                } catch (IllegalAccessException e) {
                    throw new Exception("Error updating item " + itemId, e);
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
    public boolean updateWithQueryAndScript(final Date dateHint, final Class<?> clazz, final String[] scripts, final Map<String, Object>[] scriptParams, final Condition[] conditions) {
        Boolean result = new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) throws Exception {
                try {
                    String itemType = (String) clazz.getField("ITEM_TYPE").get(null);

                    String index = indexNames.containsKey(itemType) ? indexNames.get(itemType) :
                            (itemsMonthlyIndexed.contains(itemType) && dateHint != null ? getMonthlyIndex(dateHint) : indexName);

                    for (int i = 0; i < scripts.length; i++) {
                        Script actualScript = new Script(ScriptType.INLINE, "painless", scripts[i], scriptParams[i]);

                        client.admin().indices().prepareRefresh(index).get();

                        UpdateByQueryRequestBuilder ubqrb = UpdateByQueryAction.INSTANCE.newRequestBuilder(client);
                        ubqrb.source(index).source().setTypes(itemType);
                        BulkIndexByScrollResponse response = ubqrb.setSlices(2)
                                .setMaxRetries(1000).abortOnVersionConflict(false).script(actualScript)
                                .filter(conditionESQueryBuilderDispatcher.buildFilter(conditions[i])).get();
                        if (response.getBulkFailures().size() > 0) {
                            for (BulkItemResponse.Failure failure : response.getBulkFailures()) {
                                logger.error("Failure : cause={} , message={}", failure.getCause(), failure.getMessage());
                            }
                        } else {
                            logger.info("Update By Query has processed {} in {}.", response.getUpdated(), response.getTook().toString());
                        }
                        if (response.isTimedOut()) {
                            logger.error("Update By Query ended with timeout!");
                        }
                        if (response.getVersionConflicts() > 0) {
                            logger.warn("Update By Query ended with {} Version Conflicts!", response.getVersionConflicts());
                        }
                        if (response.getNoops() > 0) {
                            logger.warn("Update By Query ended with {} noops!", response.getNoops());
                        }
                    }
                    return true;
                } catch (IndexNotFoundException e) {
                    throw new Exception("No index found for itemType=" + clazz.getName(), e);
                } catch (NoSuchFieldException e) {
                    throw new Exception("Error updating item ", e);
                } catch (IllegalAccessException e) {
                    throw new Exception("Error updating item ", e);
                } catch (ScriptException e) {
                    logger.error("Error in the update script : {}\n{}\n{}", e.getScript(), e.getDetailedMessage(), e.getScriptStack());
                    throw new Exception("Error in the update script");
                } finally {
                    return false;
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
    public boolean updateWithScript(final String itemId, final Date dateHint, final Class<?> clazz, final String script, final Map<String, Object> scriptParams) {
        Boolean result = new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) throws Exception {
                try {
                    String itemType = (String) clazz.getField("ITEM_TYPE").get(null);

                    String index = indexNames.containsKey(itemType) ? indexNames.get(itemType) :
                            (itemsMonthlyIndexed.contains(itemType) && dateHint != null ? getMonthlyIndex(dateHint) : indexName);

                    Script actualScript = new Script(ScriptType.INLINE, "painless", script, scriptParams);

                    if (bulkProcessor == null) {
                        client.prepareUpdate(index, itemType, itemId).setScript(actualScript)
                                .execute()
                                .actionGet();
                    } else {
                        UpdateRequest updateRequest = client.prepareUpdate(index, itemType, itemId).setScript(actualScript).
                                setRefreshPolicy(WriteRequest.RefreshPolicy.WAIT_UNTIL).request();
                        bulkProcessor.add(updateRequest);
                    }
                    return true;
                } catch (IndexNotFoundException e) {
                    throw new Exception("No index found for itemType=" + clazz.getName() + "itemId=" + itemId, e);
                } catch (NoSuchFieldException e) {
                    throw new Exception("Error updating item " + itemId, e);
                } catch (IllegalAccessException e) {
                    throw new Exception("Error updating item " + itemId, e);
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
        Boolean result = new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) throws Exception {
                //Index the query = register it in the percolator
                try {
                    String itemType = (String) clazz.getField("ITEM_TYPE").get(null);

                    client.prepareDelete(getIndexNameForQuery(itemType), itemType, itemId)
                            .execute().actionGet();
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
        Boolean result = new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) throws Exception {
                try {
                    String itemType = (String) clazz.getField("ITEM_TYPE").get(null);

                    BulkRequestBuilder deleteByScope = client.prepareBulk();

                    final TimeValue keepAlive = TimeValue.timeValueHours(1);
                    SearchResponse response = client.prepareSearch(indexName + "*")
                            .setIndices(getIndexNameForQuery(itemType))
                            .setScroll(keepAlive)
                            .setQuery(conditionESQueryBuilderDispatcher.getQueryBuilder(query))
                            .setSize(100).execute().actionGet();

                    // Scroll until no more hits are returned
                    while (true) {

                        for (SearchHit hit : response.getHits().getHits()) {
                            // add hit to bulk delete
                            deleteByScope.add(Requests.deleteRequest(hit.index()).type(hit.type()).id(hit.id()));
                        }

                        response = client.prepareSearchScroll(response.getScrollId()).setScroll(keepAlive).execute().actionGet();

                        // If we have no more hits, exit
                        if (response.getHits().getHits().length == 0) {
                            break;
                        }
                    }
                    client.prepareClearScroll().addScrollId(response.getScrollId()).execute().actionGet();

                    // we're done with the scrolling, delete now
                    if (deleteByScope.numberOfActions() > 0) {
                        final BulkResponse deleteResponse = deleteByScope.get();
                        if (deleteResponse.hasFailures()) {
                            // do something
                            logger.debug("Couldn't remove by query " + query + ":\n{}", deleteResponse.buildFailureMessage());
                        }
                    }

                    return true;
                } catch (Exception e) {
                    throw new Exception("Cannot remove by query", e);
                }
            }
        }.catchingExecuteInClassLoader(true);
        if (result == null) {
            return false;
        } else {
            return result;
        }
    }

    public boolean createIndex(final String indexName) {
        Boolean result = new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) {
                IndicesExistsResponse indicesExistsResponse = client.admin().indices().prepareExists(indexName).execute().actionGet();
                boolean indexExists = indicesExistsResponse.isExists();
                if (!indexExists) {
                    Map<String, String> indexMappings = new HashMap<String, String>();
                    indexMappings.put("_default_", mappings.get("_default_"));
                    for (Map.Entry<String, String> entry : mappings.entrySet()) {
                        if (indexNames.containsKey(entry.getKey()) && indexNames.get(entry.getKey()).equals(indexName)) {
                            indexMappings.put(entry.getKey(), entry.getValue());
                        }
                    }
                    internalCreateIndex(indexName, indexMappings);
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

    public boolean removeIndex(final String indexName) {
        Boolean result = new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) {
                IndicesExistsResponse indicesExistsResponse = client.admin().indices().prepareExists(indexName).execute().actionGet();
                boolean indexExists = indicesExistsResponse.isExists();
                if (indexExists) {
                    client.admin().indices().prepareDelete(indexName).execute().actionGet();
                    existingIndexNames.remove(indexName);
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

    private void internalCreateIndex(String indexName, Map<String, String> mappings) {
        CreateIndexRequestBuilder builder = client.admin().indices().prepareCreate(indexName)
                .setSettings("{\n" +
                        "    \"index\" : {\n" +
                        "        \"number_of_shards\" : " + numberOfShards + ",\n" +
                        "        \"number_of_replicas\" : " + numberOfReplicas + "\n" +
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
                        "}\n");

        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            builder.addMapping(entry.getKey(), entry.getValue());
        }

        builder.execute().actionGet();
        existingIndexNames.add(indexName);

    }


    private void createMapping(final String type, final String source, final String indexName) {
        client.admin().indices()
                .preparePutMapping(indexName)
                .setType(type)
                .setSource(source)
                .execute().actionGet();
    }

    @Override
    public void createMapping(String type, String source) {
        if (type.equals("_default_")) {
            return;
        }
        if (itemsMonthlyIndexed.contains(type)) {
            createMapping(type, source, indexName + "-*");
        } else if (indexNames.containsKey(type)) {
            if (client.admin().indices().prepareExists(indexNames.get(type)).execute().actionGet().isExists()) {
                createMapping(type, source, indexNames.get(type));
            }
        } else {
            createMapping(type, source, indexName);
        }
    }

    @Override
    public Map<String, Map<String, Object>> getPropertiesMapping(final String itemType) {
        return new InClassLoaderExecute<Map<String, Map<String, Object>>>() {
            @SuppressWarnings("unchecked")
            protected Map<String, Map<String, Object>> execute(Object... args) throws Exception {
                GetMappingsResponse getMappingsResponse = client.admin().indices().prepareGetMappings().setTypes(itemType).execute().actionGet();
                ImmutableOpenMap<String, ImmutableOpenMap<String, MappingMetaData>> mappings = getMappingsResponse.getMappings();
                Map<String, Map<String, Object>> propertyMap = new HashMap<>();
                try {
                    Iterator<ImmutableOpenMap<String, MappingMetaData>> it = mappings.valuesIt();
                    while (it.hasNext()) {
                        ImmutableOpenMap<String, MappingMetaData> next = it.next();
                        Map<String, Map<String, Object>> properties = (Map<String, Map<String, Object>>) next.get(itemType).getSourceAsMap().get("properties");
                        for (Map.Entry<String, Map<String, Object>> entry : properties.entrySet()) {
                            if (propertyMap.containsKey(entry.getKey())) {
                                Map<String, Object> subPropMap = propertyMap.get(entry.getKey());
                                for (Map.Entry<String, Object> subentry : entry.getValue().entrySet()) {
                                    if (subPropMap.containsKey(subentry.getKey()) && subPropMap.get(subentry.getKey()) instanceof Map && subentry.getValue() instanceof Map) {
                                        ((Map) subPropMap.get(subentry.getKey())).putAll((Map) subentry.getValue());
                                    } else {
                                        subPropMap.put(subentry.getKey(), subentry.getValue());
                                    }
                                }
                            } else {
                                propertyMap.put(entry.getKey(), entry.getValue());
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new Exception("Cannot get mapping", e);
                }
                return propertyMap;
            }
        }.catchingExecuteInClassLoader(true);
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
        Boolean result = new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) throws Exception {
                //Index the query = register it in the percolator
                try {
                    logger.info("Saving query : " + queryName);
                    client.prepareIndex(indexName, ".percolator", queryName)
                            .setSource(query)
                            .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
                            .execute().actionGet();
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
        Boolean result = new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) throws Exception {
                //Index the query = register it in the percolator
                try {
                    client.prepareDelete(indexName, ".percolator", queryName)
                            .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
                            .execute().actionGet();
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
    public boolean testMatch(Condition query, Item item) {
        try {
            return conditionEvaluatorDispatcher.eval(query, item);
        } catch (UnsupportedOperationException e) {
            logger.error("Eval not supported, continue with query", e);
        }
        try {
            final Class<? extends Item> clazz = item.getClass();
            String itemType = (String) clazz.getField("ITEM_TYPE").get(null);

            QueryBuilder builder = QueryBuilders.boolQuery()
                    .must(QueryBuilders.idsQuery(itemType).addIds(item.getItemId()))
                    .must(conditionESQueryBuilderDispatcher.buildFilter(query));
            return queryCount(builder, itemType) > 0;
        } catch (IllegalAccessException e) {
            logger.error("Error getting query for item=" + item, e);
        } catch (NoSuchFieldException e) {
            logger.error("Error getting query for item=" + item, e);
        }
        return false;
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
    public <T extends Item> PartialList<T> queryFullText(final String fulltext, final Condition query, String sortBy, final Class<T> clazz, final int offset, final int size) {
        return query(QueryBuilders.boolQuery().must(QueryBuilders.queryStringQuery(fulltext).defaultField("_all")).must(conditionESQueryBuilderDispatcher.getQueryBuilder(query)), sortBy, clazz, offset, size, null, null);
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
        return query(QueryBuilders.boolQuery().must(QueryBuilders.queryStringQuery(fulltext).defaultField("_all")).must(termQuery(fieldName, fieldValue)), sortBy, clazz, offset, size, getRouting(fieldName, new String[]{fieldValue}, clazz), null);
    }

    @Override
    public <T extends Item> PartialList<T> queryFullText(String fulltext, String sortBy, Class<T> clazz, int offset, int size) {
        return query(QueryBuilders.queryStringQuery(fulltext).defaultField("_all"), sortBy, clazz, offset, size, getRouting("_all", new String[]{fulltext}, clazz), null);
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
        return queryCount(conditionESQueryBuilderDispatcher.buildFilter(query), itemType);
    }

    private long queryCount(final QueryBuilder filter, final String itemType) {
        return new InClassLoaderExecute<Long>() {

            @Override
            protected Long execute(Object... args) {
                SearchResponse response = client.prepareSearch(getIndexNameForQuery(itemType))
                        .setTypes(itemType)
                        .setSize(0)
                        .setQuery(filter)
                        .execute()
                        .actionGet();
                return response.getHits().getTotalHits();
            }
        }.catchingExecuteInClassLoader(true);
    }

    private <T extends Item> PartialList<T> query(final QueryBuilder query, final String sortBy, final Class<T> clazz, final int offset, final int size, final String[] routing, final String scrollTimeValidity) {
        return new InClassLoaderExecute<PartialList<T>>() {

            @Override
            protected PartialList<T> execute(Object... args) throws Exception {
                List<T> results = new ArrayList<T>();
                String scrollIdentifier = null;
                long totalHits = 0;
                try {
                    String itemType = getItemType(clazz);
                    TimeValue keepAlive = TimeValue.timeValueHours(1);
                    SearchRequestBuilder requestBuilder = null;
                    if (scrollTimeValidity != null) {
                        keepAlive = TimeValue.parseTimeValue(scrollTimeValidity, TimeValue.timeValueHours(1), "scrollTimeValidity");
                        requestBuilder = client.prepareSearch(getIndexNameForQuery(itemType))
                                .setTypes(itemType)
                                .setFetchSource(true)
                                .setScroll(keepAlive)
                                .setFrom(offset)
                                .setQuery(query)
                                .setSize(size);
                    } else {
                        requestBuilder = client.prepareSearch(getIndexNameForQuery(itemType))
                                .setTypes(itemType)
                                .setFetchSource(true)
                                .setQuery(query)
                                .setFrom(offset);
                    }

                    if (size == Integer.MIN_VALUE) {
                        requestBuilder.setSize(defaultQueryLimit);
                    } else if (size != -1) {
                        requestBuilder.setSize(size);
                    } else {
                        // size == -1, use scroll query to retrieve all the results
                        requestBuilder = client.prepareSearch(getIndexNameForQuery(itemType))
                                .setTypes(itemType)
                                .setFetchSource(true)
                                .setScroll(keepAlive)
                                .setFrom(offset)
                                .setQuery(query)
                                .setSize(100);
                    }
                    if (routing != null) {
                        requestBuilder.setRouting(routing);
                    }
                    if (sortBy != null) {
                        String[] sortByArray = sortBy.split(",");
                        for (String sortByElement : sortByArray) {
                            if (sortByElement.startsWith("geo:")) {
                                String[] elements = sortByElement.split(":");
                                GeoDistanceSortBuilder distanceSortBuilder = SortBuilders.geoDistanceSort(elements[1], Double.parseDouble(elements[2]), Double.parseDouble(elements[3])).unit(DistanceUnit.KILOMETERS);
                                if (elements.length > 4 && elements[4].equals("desc")) {
                                    requestBuilder = requestBuilder.addSort(distanceSortBuilder.order(SortOrder.DESC));
                                } else {
                                    requestBuilder = requestBuilder.addSort(distanceSortBuilder.order(SortOrder.ASC));
                                }
                            } else {
                                String name = getPropertyNameWithData(StringUtils.substringBeforeLast(sortByElement, ":"), itemType);
                                if (name != null) {
                                    if (sortByElement.endsWith(":desc")) {
                                        requestBuilder = requestBuilder.addSort(name, SortOrder.DESC);
                                    } else {
                                        requestBuilder = requestBuilder.addSort(name, SortOrder.ASC);
                                    }
                                } else {
                                    // in the case of no data existing for the property, we will not add the sorting to the request.
                                }

                            }
                        }
                    }
                    SearchResponse response = requestBuilder
                            .execute()
                            .actionGet();
                    if (size == -1) {
                        // Scroll until no more hits are returned
                        while (true) {

                            for (SearchHit searchHit : response.getHits().getHits()) {
                                // add hit to results
                                String sourceAsString = searchHit.getSourceAsString();
                                final T value = CustomObjectMapper.getObjectMapper().readValue(sourceAsString, clazz);
                                value.setItemId(searchHit.getId());
                                results.add(value);
                            }

                            response = client.prepareSearchScroll(response.getScrollId()).setScroll(keepAlive).execute().actionGet();

                            // If we have no more hits, exit
                            if (response.getHits().getHits().length == 0) {
                                break;
                            }
                        }
                        client.prepareClearScroll().addScrollId(response.getScrollId()).execute().actionGet();
                    } else {
                        SearchHits searchHits = response.getHits();
                        scrollIdentifier = response.getScrollId();
                        totalHits = searchHits.getTotalHits();
                        for (SearchHit searchHit : searchHits) {
                            String sourceAsString = searchHit.getSourceAsString();
                            final T value = CustomObjectMapper.getObjectMapper().readValue(sourceAsString, clazz);
                            value.setItemId(searchHit.getId());
                            results.add(value);
                        }
                    }
                } catch (Exception t) {
                    throw new Exception("Error loading itemType=" + clazz.getName() + " query=" + query + " sortBy=" + sortBy, t);
                }

                PartialList<T> result = new PartialList<T>(results, offset, size, totalHits);
                if (scrollIdentifier != null && totalHits != 0) {
                    result.setScrollIdentifier(scrollIdentifier);
                    result.setScrollTimeValidity(scrollTimeValidity);
                }
                return result;
            }
        }.catchingExecuteInClassLoader(true);
    }

    @Override
    public <T extends Item> PartialList<T> continueScrollQuery(final Class<T> clazz, final String scrollIdentifier, final String scrollTimeValidity) {
        return new InClassLoaderExecute<PartialList<T>>() {

            @Override
            protected PartialList<T> execute(Object... args) throws Exception {
                List<T> results = new ArrayList<T>();
                long totalHits = 0;
                try {
                    TimeValue keepAlive = TimeValue.parseTimeValue(scrollTimeValidity, TimeValue.timeValueMinutes(10), "scrollTimeValidity");
                    SearchResponse response = client.prepareSearchScroll(scrollIdentifier).setScroll(keepAlive).execute().actionGet();

                    if (response.getHits().getHits().length == 0) {
                        client.prepareClearScroll().addScrollId(response.getScrollId()).execute().actionGet();
                    } else {
                        for (SearchHit searchHit : response.getHits().getHits()) {
                            // add hit to results
                            String sourceAsString = searchHit.getSourceAsString();
                            final T value = CustomObjectMapper.getObjectMapper().readValue(sourceAsString, clazz);
                            value.setItemId(searchHit.getId());
                            results.add(value);
                        }
                    }
                    PartialList<T> result = new PartialList<T>(results, 0, response.getHits().getHits().length, response.getHits().getTotalHits());
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
    public Map<String, Long> aggregateQuery(final Condition filter, final BaseAggregate aggregate, final String itemType) {
        return new InClassLoaderExecute<Map<String, Long>>() {

            @Override
            protected Map<String, Long> execute(Object... args) {
                Map<String, Long> results = new LinkedHashMap<String, Long>();

                SearchRequestBuilder builder = client.prepareSearch(getIndexNameForQuery(itemType))
                        .setTypes(itemType)
                        .setSize(0)
                        .setQuery(QueryBuilders.matchAllQuery());

                List<AggregationBuilder> lastAggregation = new ArrayList<AggregationBuilder>();

                if (aggregate != null) {
                    AggregationBuilder bucketsAggregation = null;
                    String fieldName = aggregate.getField();
                    if (aggregate instanceof DateAggregate) {
                        DateAggregate dateAggregate = (DateAggregate) aggregate;
                        DateHistogramAggregationBuilder dateHistogramBuilder = AggregationBuilders.dateHistogram("buckets").field(fieldName).dateHistogramInterval(new DateHistogramInterval((dateAggregate.getInterval())));
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
                            bucketsAggregation = AggregationBuilders.terms("buckets").field(fieldName).size(5000);
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

                if (filter != null) {
                    AggregationBuilder filterAggregation = AggregationBuilders.filter("filter", conditionESQueryBuilderDispatcher.buildFilter(filter));
                    for (AggregationBuilder aggregationBuilder : lastAggregation) {
                        filterAggregation.subAggregation(aggregationBuilder);
                    }
                    lastAggregation = Collections.singletonList(filterAggregation);
                }


                AggregationBuilder globalAggregation = AggregationBuilders.global("global");
                for (AggregationBuilder aggregationBuilder : lastAggregation) {
                    globalAggregation.subAggregation(aggregationBuilder);
                }

                builder.addAggregation(globalAggregation);

                SearchResponse response = builder.execute().actionGet();

                Aggregations aggregations = response.getAggregations();
                if (aggregations != null) {
                    Global globalAgg = aggregations.get("global");
                    results.put("_all", globalAgg.getDocCount());
                    aggregations = globalAgg.getAggregations();

                    if (aggregations.get("filter") != null) {
                        Filter filterAgg = aggregations.get("filter");
                        results.put("_filtered", filterAgg.getDocCount());
                        aggregations = filterAgg.getAggregations();
                    }
                    if (aggregations.get("buckets") != null) {
                        MultiBucketsAggregation terms = aggregations.get("buckets");
                        for (MultiBucketsAggregation.Bucket bucket : terms.getBuckets()) {
                            results.put(bucket.getKeyAsString(), bucket.getDocCount());
                        }
                        SingleBucketAggregation missing = aggregations.get("missing");
                        if (missing.getDocCount() > 0) {
                            results.put("_missing", missing.getDocCount());
                        }
                    }
                }

                return results;
            }
        }.catchingExecuteInClassLoader(true);
    }

    private <T extends Item> String getItemType(Class<T> clazz) {
        try {
            return (String) clazz.getField("ITEM_TYPE").get(null);
        } catch (NoSuchFieldException e) {
            logger.error("Class " + clazz.getName() + " doesn't define a publicly accessible ITEM_TYPE field", e);
        } catch (IllegalAccessException e) {
            logger.error("Error loading itemType=" + clazz.getName(), e);
        }
        return null;
    }

    private <T extends Item> String[] getRouting(String fieldName, String[] fieldValues, Class<T> clazz) {
        String itemType = getItemType(clazz);
        String[] routing = null;
        if (routingByType.containsKey(itemType) && routingByType.get(itemType).equals(fieldName)) {
            routing = fieldValues;
        }
        return routing;
    }


    @Override
    public void refresh() {
        new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) {
                if (bulkProcessor != null) {
                    bulkProcessor.flush();
                }
                client.admin().indices().refresh(Requests.refreshRequest()).actionGet();
                return true;
            }
        }.catchingExecuteInClassLoader(true);

    }


    @Override
    public void purge(final Date date) {
        new InClassLoaderExecute<Object>() {
            @Override
            protected Object execute(Object... args) throws Exception {
                IndicesStatsResponse statsResponse = client.admin().indices().prepareStats(indexName + "-*")
                        .setIndexing(false)
                        .setGet(false)
                        .setSearch(false)
                        .setWarmer(false)
                        .setMerge(false)
                        .setFieldData(false)
                        .setFlush(false)
                        .setCompletion(false)
                        .setRefresh(false)
                        .execute()
                        .actionGet();

                SimpleDateFormat d = new SimpleDateFormat("yyyy-MM");

                List<String> toDelete = new ArrayList<String>();
                for (String currentIndexName : statsResponse.getIndices().keySet()) {
                    if (currentIndexName.startsWith(indexName + "-")) {
                        try {
                            Date indexDate = d.parse(currentIndexName.substring(indexName.length() + 1));

                            if (indexDate.before(date)) {
                                toDelete.add(currentIndexName);
                            }
                        } catch (ParseException e) {
                            throw new Exception("Cannot parse index name " + currentIndexName, e);
                        }
                    }
                }
                if (!toDelete.isEmpty()) {
                    client.admin().indices().prepareDelete(toDelete.toArray(new String[toDelete.size()])).execute().actionGet();
                }
                return null;
            }
        }.catchingExecuteInClassLoader(true);
    }

    @Override
    public void purge(final String scope) {
        new InClassLoaderExecute<Void>() {
            @Override
            protected Void execute(Object... args) {
                QueryBuilder query = termQuery("scope", scope);

                BulkRequestBuilder deleteByScope = client.prepareBulk();

                final TimeValue keepAlive = TimeValue.timeValueHours(1);
                SearchResponse response = client.prepareSearch(indexName + "*")
                        .setScroll(keepAlive)
                        .setQuery(query)
                        .setSize(100).execute().actionGet();

                // Scroll until no more hits are returned
                while (true) {

                    for (SearchHit hit : response.getHits().getHits()) {
                        // add hit to bulk delete
                        deleteByScope.add(Requests.deleteRequest(hit.index()).type(hit.type()).id(hit.id()));
                    }

                    response = client.prepareSearchScroll(response.getScrollId()).setScroll(keepAlive).execute().actionGet();

                    // If we have no more hits, exit
                    if (response.getHits().getHits().length == 0) {
                        break;
                    }
                }

                // we're done with the scrolling, delete now
                if (deleteByScope.numberOfActions() > 0) {
                    final BulkResponse deleteResponse = deleteByScope.get();
                    if (deleteResponse.hasFailures()) {
                        // do something
                        logger.debug("Couldn't delete from scope " + scope + ":\n{}", deleteResponse.buildFailureMessage());
                    }
                }

                return null;
            }
        }.catchingExecuteInClassLoader(true);
    }

    @Override
    public Map<String, Double> getSingleValuesMetrics(final Condition condition, final String[] metrics, final String field, final String itemType) {
        return new InClassLoaderExecute<Map<String, Double>>() {

            @Override
            protected Map<String, Double> execute(Object... args) {
                Map<String, Double> results = new LinkedHashMap<String, Double>();

                SearchRequestBuilder builder = client.prepareSearch(getIndexNameForQuery(itemType))
                        .setTypes(itemType)
                        .setSize(0)
                        .setQuery(QueryBuilders.matchAllQuery());
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
                        }
                    }
                }
                builder.addAggregation(filterAggregation);
                SearchResponse response = builder.execute().actionGet();

                Aggregations aggregations = response.getAggregations();
                if (aggregations != null) {
                    Aggregation metricsResults = aggregations.get("metrics");
                    if (metricsResults instanceof HasAggregations) {
                        aggregations = ((HasAggregations) metricsResults).getAggregations();
                        for (Aggregation aggregation : aggregations) {
                            InternalNumericMetricsAggregation.SingleValue singleValue = (InternalNumericMetricsAggregation.SingleValue) aggregation;
                            results.put("_" + singleValue.getName(), singleValue.value());
                        }
                    }
                }
                return results;
            }
        }.catchingExecuteInClassLoader(true);
    }

    private String getIndexNameForQuery(String itemType) {
        return indexNames.containsKey(itemType) ? indexNames.get(itemType) :
                (itemsMonthlyIndexed.contains(itemType) ? indexName + "-*" : indexName);
    }

    private String getConfig(Map<String, String> settings, String key,
                             String defaultValue) {
        if (settings != null && settings.get(key) != null) {
            return settings.get(key);
        }
        return defaultValue;
    }

    public abstract static class InClassLoaderExecute<T> {

        protected abstract T execute(Object... args) throws Exception;

        public T executeInClassLoader(Object... args) throws Exception {
            ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
                return execute(args);
            } finally {
                Thread.currentThread().setContextClassLoader(tccl);
            }
        }

        public T catchingExecuteInClassLoader(boolean logError, Object... args) {
            try {
                return executeInClassLoader(args);
            } catch (Exception e) {
                logger.error("Error while executing in class loader", e);
            }
            return null;
        }
    }


}
