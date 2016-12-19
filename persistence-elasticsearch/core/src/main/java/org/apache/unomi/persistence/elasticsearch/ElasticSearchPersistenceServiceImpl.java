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
import org.apache.karaf.cellar.config.ClusterConfigurationEvent;
import org.apache.karaf.cellar.config.Constants;
import org.apache.karaf.cellar.core.*;
import org.apache.karaf.cellar.core.control.SwitchStatus;
import org.apache.karaf.cellar.core.event.EventProducer;
import org.apache.karaf.cellar.core.event.EventType;
import org.apache.unomi.api.ClusterNode;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.TimestampedItem;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.DateRange;
import org.apache.unomi.api.query.IpRange;
import org.apache.unomi.api.query.NumericRange;
import org.apache.unomi.api.services.ClusterService;
import org.apache.unomi.persistence.elasticsearch.conditions.*;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.aggregate.*;
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
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Requests;
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
import org.elasticsearch.script.Script;
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
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("rawtypes")
public class ElasticSearchPersistenceServiceImpl implements PersistenceService, ClusterService, SynchronousBundleListener {

    private static final Logger logger = LoggerFactory.getLogger(ElasticSearchPersistenceServiceImpl.class.getName());

    public static final String CONTEXTSERVER_ADDRESS = "contextserver.address";
    public static final String CONTEXTSERVER_PORT = "contextserver.port";
    public static final String CONTEXTSERVER_SECURE_ADDRESS = "contextserver.secureAddress";
    public static final String CONTEXTSERVER_SECURE_PORT = "contextserver.securePort";
    public static final String NUMBER_OF_SHARDS = "number_of_shards";
    public static final String NUMBER_OF_REPLICAS = "number_of_replicas";
    public static final String CLUSTER_NAME = "cluster.name";
    public static final String BULK_PROCESSOR_NAME = "bulkProcessor.name";
    public static final String BULK_PROCESSOR_CONCURRENT_REQUESTS = "bulkProcessor.concurrentRequests";
    public static final String BULK_PROCESSOR_BULK_ACTIONS = "bulkProcessor.bulkActions";
    public static final String BULK_PROCESSOR_BULK_SIZE = "bulkProcessor.bulkSize";
    public static final String BULK_PROCESSOR_FLUSH_INTERVAL = "bulkProcessor.flushInterval";
    public static final String BULK_PROCESSOR_BACKOFF_POLICY = "bulkProcessor.backoffPolicy";
    public static final String KARAF_CELLAR_CLUSTER_NODE_CONFIGURATION = "org.apache.unoni.nodes";
    public static final String KARAF_CLUSTER_CONFIGURATION_PUBLIC_ENDPOINTS = "publicEndpoints";
    public static final String KARAF_CLUSTER_CONFIGURATION_SECURE_ENDPOINTS = "secureEndpoints";

    private Client client;
    private BulkProcessor bulkProcessor;
    private String clusterName;
    private String indexName;
    private String monthlyIndexNumberOfShards;
    private String monthlyIndexNumberOfReplicas;
    private String elasticSearchConfig = null;
    private BundleContext bundleContext;
    private Map<String, String> mappings = new HashMap<String, String>();
    private ConditionEvaluatorDispatcher conditionEvaluatorDispatcher;
    private ConditionESQueryBuilderDispatcher conditionESQueryBuilderDispatcher;
    private ClusterManager karafCellarClusterManager;
    private EventProducer karafCellarEventProducer;
    private GroupManager karafCellarGroupManager;
    private String karafCellarGroupName = Configurations.DEFAULT_GROUP_NAME;
    private ConfigurationAdmin osgiConfigurationAdmin;
    private String karafJMXUsername = "karaf";
    private String karafJMXPassword = "karaf";
    private int karafJMXPort = 1099;

    private Map<String,String> indexNames;
    private List<String> itemsMonthlyIndexed;
    private Map<String, String> routingByType;
    private Set<String> existingIndexNames = new TreeSet<String>();

    private String address;
    private String port;
    private String secureAddress;
    private String securePort;
    private Integer defaultQueryLimit = 10;

    private Timer timer;

    private String bulkProcessorName = "unomi-bulk";
    private String bulkProcessorConcurrentRequests = "1";
    private String bulkProcessorBulkActions = "1000";
    private String bulkProcessorBulkSize= "5MB";
    private String bulkProcessorFlushInterval = "5s";
    private String bulkProcessorBackoffPolicy = "exponential";

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
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

    public void setAddress(String address) {
        this.address = address;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public void setSecureAddress(String secureAddress) {
        this.secureAddress = secureAddress;
    }

    public void setSecurePort(String securePort) {
        this.securePort = securePort;
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

    public void setElasticSearchConfig(String elasticSearchConfig) {
        this.elasticSearchConfig = elasticSearchConfig;
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

    public void setKarafCellarClusterManager(ClusterManager karafCellarClusterManager) {
        this.karafCellarClusterManager = karafCellarClusterManager;
    }

    public void setKarafCellarEventProducer(EventProducer karafCellarEventProducer) {
        this.karafCellarEventProducer = karafCellarEventProducer;
    }

    public void setKarafCellarGroupManager(GroupManager karafCellarGroupManager) {
        this.karafCellarGroupManager = karafCellarGroupManager;
    }

    public void setKarafCellarGroupName(String karafCellarGroupName) {
        this.karafCellarGroupName = karafCellarGroupName;
    }

    public void setOsgiConfigurationAdmin(ConfigurationAdmin osgiConfigurationAdmin) {
        this.osgiConfigurationAdmin = osgiConfigurationAdmin;
    }

    public void setKarafJMXUsername(String karafJMXUsername) {
        this.karafJMXUsername = karafJMXUsername;
    }

    public void setKarafJMXPassword(String karafJMXPassword) {
        this.karafJMXPassword = karafJMXPassword;
    }

    public void setKarafJMXPort(int karafJMXPort) {
        this.karafJMXPort = karafJMXPort;
    }

    public void start() {

        loadPredefinedMappings(bundleContext, false);

        // on startup
        new InClassLoaderExecute<Object>() {
            public Object execute(Object... args) {
                logger.info("Connecting to ElasticSearch persistence backend using cluster name " + clusterName + " and index name " + indexName + "...");

                address = System.getProperty(CONTEXTSERVER_ADDRESS, address);
                port = System.getProperty(CONTEXTSERVER_PORT, port);
                secureAddress = System.getProperty(CONTEXTSERVER_SECURE_ADDRESS, secureAddress);
                securePort = System.getProperty(CONTEXTSERVER_SECURE_PORT, securePort);

                if (karafCellarEventProducer != null && karafCellarClusterManager != null) {

                    boolean setupConfigOk = true;
                    Group group = karafCellarGroupManager.findGroupByName(karafCellarGroupName);
                    if (setupConfigOk && group == null) {
                        logger.error("Cluster group " + karafCellarGroupName + " doesn't exist");
                        setupConfigOk = false;
                    }

                    // check if the producer is ON
                    if (setupConfigOk && karafCellarEventProducer.getSwitch().getStatus().equals(SwitchStatus.OFF)) {
                        logger.error("Cluster event producer is OFF");
                        setupConfigOk = false;
                    }

                    // check if the config pid is allowed
                    if (setupConfigOk && !isClusterConfigPIDAllowed(group, Constants.CATEGORY, KARAF_CELLAR_CLUSTER_NODE_CONFIGURATION, EventType.OUTBOUND)) {
                        logger.error("Configuration PID " + KARAF_CELLAR_CLUSTER_NODE_CONFIGURATION + " is blocked outbound for cluster group " + karafCellarGroupName);
                        setupConfigOk = false;
                    }

                    if (setupConfigOk) {
                        Map<String, Properties> configurations = karafCellarClusterManager.getMap(Constants.CONFIGURATION_MAP + Configurations.SEPARATOR + karafCellarGroupName);
                        org.apache.karaf.cellar.core.Node thisKarafNode = karafCellarClusterManager.getNode();
                        Properties karafCellarClusterNodeConfiguration = configurations.get(KARAF_CELLAR_CLUSTER_NODE_CONFIGURATION);
                        if (karafCellarClusterNodeConfiguration == null) {
                            karafCellarClusterNodeConfiguration = new Properties();
                        }
                        String publicEndpointsPropValue = karafCellarClusterNodeConfiguration.getProperty(KARAF_CLUSTER_CONFIGURATION_PUBLIC_ENDPOINTS, thisKarafNode.getId() + "=" + address + ":" + port);
                        String secureEndpointsPropValue = karafCellarClusterNodeConfiguration.getProperty(KARAF_CLUSTER_CONFIGURATION_SECURE_ENDPOINTS, thisKarafNode.getId() + "=" + secureAddress + ":" + securePort);
                        String[] publicEndpointsArray = publicEndpointsPropValue.split(",");
                        Set<String> publicEndpoints = new TreeSet<String>(Arrays.asList(publicEndpointsArray));
                        String[] secureEndpointsArray = secureEndpointsPropValue.split(",");
                        Set<String> secureEndpoints = new TreeSet<String>(Arrays.asList(secureEndpointsArray));
                        publicEndpoints.add(thisKarafNode.getId() + "=" + address + ":" + port);
                        secureEndpoints.add(thisKarafNode.getId() + "=" + secureAddress + ":" + port);
                        karafCellarClusterNodeConfiguration.setProperty(KARAF_CLUSTER_CONFIGURATION_PUBLIC_ENDPOINTS, StringUtils.join(publicEndpoints, ","));
                        karafCellarClusterNodeConfiguration.setProperty(KARAF_CLUSTER_CONFIGURATION_SECURE_ENDPOINTS, StringUtils.join(secureEndpoints, ","));
                        configurations.put(KARAF_CELLAR_CLUSTER_NODE_CONFIGURATION, karafCellarClusterNodeConfiguration);
                        ClusterConfigurationEvent clusterConfigurationEvent = new ClusterConfigurationEvent(KARAF_CELLAR_CLUSTER_NODE_CONFIGURATION);
                        clusterConfigurationEvent.setSourceGroup(group);
                        karafCellarEventProducer.produce(clusterConfigurationEvent);
                    }
                }

                bulkProcessorName = System.getProperty(BULK_PROCESSOR_NAME, bulkProcessorName);
                bulkProcessorConcurrentRequests = System.getProperty(BULK_PROCESSOR_CONCURRENT_REQUESTS, bulkProcessorConcurrentRequests);
                bulkProcessorBulkActions = System.getProperty(BULK_PROCESSOR_BULK_ACTIONS, bulkProcessorBulkActions);
                bulkProcessorBulkSize = System.getProperty(BULK_PROCESSOR_BULK_SIZE, bulkProcessorBulkSize);
                bulkProcessorFlushInterval = System.getProperty(BULK_PROCESSOR_FLUSH_INTERVAL, bulkProcessorFlushInterval);
                bulkProcessorBackoffPolicy = System.getProperty(BULK_PROCESSOR_BACKOFF_POLICY, bulkProcessorBackoffPolicy);

                try {
                    Settings transportSettings = Settings.builder()
                            .put(CLUSTER_NAME, clusterName).build();
                    client = new PreBuiltTransportClient(transportSettings)
                            .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(address), 9300));
                } catch (UnknownHostException e) {
                    logger.error("Error resolving address " + address + " ElasticSearch transport client not connected, using internal client instead", e);
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
                    Map<String,String> indexMappings = new HashMap<String,String>();
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

                logger.info("Waiting for index creation to complete...");

                client.admin().cluster().prepareHealth()
                        .setWaitForGreenStatus()
                        .get();

                logger.info("Cluster status is GREEN");

                return null;
            }
        }.executeInClassLoader();


        bundleContext.addBundleListener(this);

        try {
            for (ServiceReference<ConditionEvaluator> reference : bundleContext.getServiceReferences(ConditionEvaluator.class, null)) {
                ConditionEvaluator service = bundleContext.getService(reference);
                conditionEvaluatorDispatcher.addEvaluator(reference.getProperty("conditionEvaluatorId").toString(), reference.getBundle().getBundleId(), service);
            }
            for (ServiceReference<ConditionESQueryBuilder> reference : bundleContext.getServiceReferences(ConditionESQueryBuilder.class, null)) {
                ConditionESQueryBuilder service = bundleContext.getService(reference);
                conditionESQueryBuilderDispatcher.addQueryBuilder(reference.getProperty("queryBuilderId").toString(), reference.getBundle().getBundleId(), service);
            }
        } catch (Exception e) {
            logger.error("Cannot get services", e);
        }

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

        logger.info(this.getClass().getName() + " service started successfully.");
    }

    private void refreshExistingIndexNames() {
        new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) {
                try {
                    logger.info("Refreshing existing indices list...");
                    IndicesStatsResponse indicesStatsResponse = client.admin().indices().prepareStats().all().execute().get();
                    existingIndexNames = new TreeSet<>(indicesStatsResponse.getIndices().keySet());
                } catch (InterruptedException e) {
                    logger.error("Error retrieving indices stats", e);
                } catch (ExecutionException e) {
                    logger.error("Error retrieving indices stats", e);
                }
                return true;
            }
        }.executeInClassLoader();
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
                    int maxNumberOfRetries = Integer.parseInt(backoffPolicyStr.substring(paramSeparatorPos+1, paramEndPos));
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
                        int maxNumberOfRetries = Integer.parseInt(backoffPolicyStr.substring(paramSeparatorPos+1, paramEndPos));
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
        }.executeInClassLoader();

        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        bundleContext.removeBundleListener(this);
    }

    @Override
    public void bundleChanged(BundleEvent event) {
        switch (event.getType()) {
            case BundleEvent.STARTED:
                if (event.getBundle() != null && event.getBundle().getRegisteredServices() != null) {
                    for (ServiceReference<?> reference : event.getBundle().getRegisteredServices()) {
                        Object service = bundleContext.getService(reference);
                        if (service instanceof ConditionEvaluator) {
                            conditionEvaluatorDispatcher.addEvaluator(reference.getProperty("conditionEvaluatorId").toString(), event.getBundle().getBundleId(), (ConditionEvaluator) service);
                        }
                        if (service instanceof ConditionESQueryBuilder) {
                            conditionESQueryBuilderDispatcher.addQueryBuilder(reference.getProperty("queryBuilderId").toString(), event.getBundle().getBundleId(), (ConditionESQueryBuilder) service);
                        }
                    }
                }
                break;
            case BundleEvent.STARTING:
                loadPredefinedMappings(event.getBundle().getBundleContext(), true);
                break;
            case BundleEvent.STOPPING:
                conditionEvaluatorDispatcher.removeEvaluators(event.getBundle().getBundleId());
                conditionESQueryBuilderDispatcher.removeQueryBuilders(event.getBundle().getBundleId());
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

                Map<String,String> indexMappings = new HashMap<String,String>();
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
            logger.debug("Found mapping at " + predefinedMappingURL + ", loading... ");
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
            protected T execute(Object... args) {
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
                    logger.debug("No index found for itemType=" + clazz.getName() + " itemId=" + itemId, e);
                } catch (IllegalAccessException e) {
                    logger.error("Error loading itemType=" + clazz.getName() + " itemId=" + itemId, e);
                } catch (Exception t) {
                    logger.error("Error loading itemType=" + clazz.getName() + " itemId=" + itemId, t);
                }
                return null;
            }
        }.executeInClassLoader();

    }

    @Override
    public boolean save(final Item item) {

        return new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) {
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
                        indexBuilder.execute().actionGet();
                    } catch (IndexNotFoundException e) {
                        if (existingIndexNames.contains(index)) {
                            existingIndexNames.remove(index);
                        }
                    }
                    return true;
                } catch (IOException e) {
                    logger.error("Error saving item " + item, e);
                }
                return false;
            }
        }.executeInClassLoader();

    }

    @Override
    public boolean update(final String itemId, final Date dateHint, final Class clazz, final String propertyName, final Object propertyValue) {
        return update(itemId, dateHint, clazz, Collections.singletonMap(propertyName, propertyValue));
    }

    @Override
    public boolean update(final String itemId, final Date dateHint, final Class clazz, final Map source) {
        return new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) {
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
                    logger.debug("No index found for itemType=" + clazz.getName() + "itemId=" + itemId, e);
                } catch (NoSuchFieldException e) {
                    logger.error("Error updating item " + itemId, e);
                } catch (IllegalAccessException e) {
                    logger.error("Error updating item " + itemId, e);
                }
                return false;
            }
        }.executeInClassLoader();
    }

    @Override
    public boolean updateWithScript(final String itemId, final Date dateHint, final Class<?> clazz, final String script, final Map<String, Object> scriptParams) {
        return new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) {
                try {
                    String itemType = (String) clazz.getField("ITEM_TYPE").get(null);

                    String index = indexNames.containsKey(itemType) ? indexNames.get(itemType) :
                            (itemsMonthlyIndexed.contains(itemType) && dateHint != null ? getMonthlyIndex(dateHint) : indexName);

                    Script actualScript = new Script(ScriptType.INLINE, "groovy", script, scriptParams);
                    if (bulkProcessor == null) {
                        client.prepareUpdate(index, itemType, itemId).setScript(actualScript)
                                .execute()
                                .actionGet();
                    } else {
                        UpdateRequest updateRequest = client.prepareUpdate(index, itemType, itemId).setScript(actualScript).request();
                        bulkProcessor.add(updateRequest);
                    }
                    return true;
                } catch (IndexNotFoundException e) {
                    logger.debug("No index found for itemType=" + clazz.getName() + "itemId=" + itemId, e);
                } catch (NoSuchFieldException e) {
                    logger.error("Error updating item " + itemId, e);
                } catch (IllegalAccessException e) {
                    logger.error("Error updating item " + itemId, e);
                }
                return false;
            }
        }.executeInClassLoader();
    }

    @Override
    public <T extends Item> boolean remove(final String itemId, final Class<T> clazz) {
        return new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) {
                //Index the query = register it in the percolator
                try {
                    String itemType = (String) clazz.getField("ITEM_TYPE").get(null);

                    client.prepareDelete(getIndexNameForQuery(itemType), itemType, itemId)
                            .execute().actionGet();
                    return true;
                } catch (Exception e) {
                    logger.error("Cannot remove", e);
                }
                return false;
            }
        }.executeInClassLoader();
    }

    public <T extends Item> boolean removeByQuery(final Condition query, final Class<T> clazz) {
        return new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) {
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
                    logger.error("Cannot remove by query", e);
                }
                return false;
            }
        }.executeInClassLoader();
    }

    public boolean createIndex(final String indexName) {
        return new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) {
                IndicesExistsResponse indicesExistsResponse = client.admin().indices().prepareExists(indexName).execute().actionGet();
                boolean indexExists = indicesExistsResponse.isExists();
                if (!indexExists) {
                    Map<String,String> indexMappings = new HashMap<String,String>();
                    for (Map.Entry<String, String> entry : mappings.entrySet()) {
                        if (indexNames.containsKey(entry.getKey()) && indexNames.get(entry.getKey()).equals(indexName)) {
                            indexMappings.put(entry.getKey(), entry.getValue());
                        }
                    }
                    internalCreateIndex(indexName, indexMappings);
                }
                return !indexExists;
            }
        }.executeInClassLoader();
    }

    public boolean removeIndex(final String indexName) {
        return new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) {
                IndicesExistsResponse indicesExistsResponse = client.admin().indices().prepareExists(indexName).execute().actionGet();
                boolean indexExists = indicesExistsResponse.isExists();
                if (indexExists) {
                    client.admin().indices().prepareDelete(indexName).execute().actionGet();
                    existingIndexNames.remove(indexName);
                }
                return indexExists;
            }
        }.executeInClassLoader();
    }

    private void internalCreateIndex(String indexName, Map<String,String> mappings) {
        CreateIndexRequestBuilder builder = client.admin().indices().prepareCreate(indexName)
                .setSettings("{\n" +
                        "    \"analysis\": {\n" +
                        "      \"tokenizer\": {\n" +
                        "        \"myTokenizer\": {\n" +
                        "          \"type\":\"pattern\",\n" +
                        "          \"pattern\":\".*\",\n" +
                        "          \"group\":0\n" +
                        "        }\n" +
                        "      },\n" +
                        "      \"analyzer\": {\n" +
                        "        \"folding\": {\n" +
                        "          \"type\":\"custom\",\n" +
                        "          \"tokenizer\": \"myTokenizer\",\n" +
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
            protected Map<String, Map<String, Object>> execute(Object... args) {
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
                    logger.error("Cannot get mapping", e);
                }
                return propertyMap;
            }
        }.executeInClassLoader();
    }

    public boolean saveQuery(final String queryName, final String query) {
        return new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) {
                //Index the query = register it in the percolator
                try {
                    logger.info("Saving query : " + queryName);
                    client.prepareIndex(indexName, ".percolator", queryName)
                            .setSource(query)
                            .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
                            .execute().actionGet();
                    return true;
                } catch (Exception e) {
                    logger.error("Cannot save query", e);
                }
                return false;
            }
        }.executeInClassLoader();
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
        return new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) {
                //Index the query = register it in the percolator
                try {
                    client.prepareDelete(indexName, ".percolator", queryName)
                            .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
                            .execute().actionGet();
                    return true;
                } catch (Exception e) {
                    logger.error("Cannot delete query", e);
                }
                return false;
            }
        }.executeInClassLoader();
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
        return query(QueryBuilders.termsQuery(fieldName, fieldValues), sortBy, clazz, 0, -1, getRouting(fieldName, fieldValues, clazz), null).getList();
    }

    @Override
    public <T extends Item> PartialList<T> query(String fieldName, String fieldValue, String sortBy, Class<T> clazz, int offset, int size) {
        return query(QueryBuilders.termQuery(fieldName, fieldValue), sortBy, clazz, offset, size, getRouting(fieldName, new String[]{fieldValue}, clazz), null);
    }

    @Override
    public <T extends Item> PartialList<T> queryFullText(String fieldName, String fieldValue, String fulltext, String sortBy, Class<T> clazz, int offset, int size) {
        return query(QueryBuilders.boolQuery().must(QueryBuilders.queryStringQuery(fulltext).defaultField("_all")).must(QueryBuilders.termQuery(fieldName, fieldValue)), sortBy, clazz, offset, size, getRouting(fieldName, new String[]{fieldValue}, clazz), null);
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
                        .setQuery(QueryBuilders.matchAllQuery())
                        .addAggregation(AggregationBuilders.filter("filter", filter))
                        .execute()
                        .actionGet();
                Aggregations searchHits = response.getAggregations();
                Filter filter = searchHits.get("filter");
                return filter.getDocCount();
            }
        }.executeInClassLoader();
    }

    private <T extends Item> PartialList<T> query(final QueryBuilder query, final String sortBy, final Class<T> clazz, final int offset, final int size, final String[] routing, final String scrollTimeValidity) {
        return new InClassLoaderExecute<PartialList<T>>() {

            @Override
            protected PartialList<T> execute(Object... args) {
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
                                String name = StringUtils.substringBeforeLast(sortByElement,":");
                                if (name.equals("metadata.name") || name.equals("metadata.description")
                                        || name.equals("properties.firstName") || name.equals("properties.lastName")
                                        ) {
                                    name += ".keyword";
                                }
                                if (sortByElement.endsWith(":desc")) {
                                    requestBuilder = requestBuilder.addSort(name, SortOrder.DESC);
                                } else {
                                    requestBuilder = requestBuilder.addSort(name, SortOrder.ASC);
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
                    logger.error("Error loading itemType=" + clazz.getName() + " query=" + query + " sortBy=" + sortBy, t);
                }

                PartialList<T> result = new PartialList<T>(results, offset, size, totalHits);
                if (scrollIdentifier != null && totalHits != 0) {
                    result.setScrollIdentifier(scrollIdentifier);
                    result.setScrollTimeValidity(scrollTimeValidity);
                }
                return result;
            }
        }.executeInClassLoader();
    }

    @Override
    public <T extends Item> PartialList<T> continueScrollQuery(final Class<T> clazz, final String scrollIdentifier, final String scrollTimeValidity) {
        return new InClassLoaderExecute<PartialList<T>>() {

            @Override
            protected PartialList<T> execute(Object... args) {
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
                    logger.error("Error continuing scrolling query for itemType=" + clazz.getName() + " scrollIdentifier=" + scrollIdentifier + " scrollTimeValidity=" + scrollTimeValidity, t);
                }
                return null;
            }
        }.executeInClassLoader();
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
                    if (aggregate instanceof DateAggregate) {
                        DateAggregate dateAggregate = (DateAggregate) aggregate;
                        DateHistogramAggregationBuilder dateHistogramBuilder = AggregationBuilders.dateHistogram("buckets").field(aggregate.getField()).dateHistogramInterval(new DateHistogramInterval((dateAggregate.getInterval())));
                        if (dateAggregate.getFormat() != null) {
                            dateHistogramBuilder.format(dateAggregate.getFormat());
                        }
                        bucketsAggregation = dateHistogramBuilder;
                    } else if (aggregate instanceof NumericRangeAggregate) {
                        RangeAggregationBuilder rangebuilder = AggregationBuilders.range("buckets").field(aggregate.getField());
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
                        DateRangeAggregationBuilder rangebuilder = AggregationBuilders.dateRange("buckets").field(aggregate.getField());
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
                        IpRangeAggregationBuilder rangebuilder = AggregationBuilders.ipRange("buckets").field(aggregate.getField());
                        for (IpRange range : ipRangeAggregate.getRanges()) {
                            if (range != null) {
                                rangebuilder.addRange(range.getKey(), range.getFrom(), range.getTo());
                            }
                        }
                        bucketsAggregation = rangebuilder;
                    } else {
                        //default
                        bucketsAggregation = AggregationBuilders.terms("buckets").field(aggregate.getField()).size(5000);
                    }
                    if (bucketsAggregation != null) {
                        final MissingAggregationBuilder missingBucketsAggregation = AggregationBuilders.missing("missing").field(aggregate.getField());
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
        }.executeInClassLoader();
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
    public List<ClusterNode> getClusterNodes() {
        return new InClassLoaderExecute<List<ClusterNode>>() {

            @Override
            protected List<ClusterNode> execute(Object... args) {
                Map<String, ClusterNode> clusterNodes = new LinkedHashMap<String, ClusterNode>();

                Set<org.apache.karaf.cellar.core.Node> karafCellarNodes = karafCellarClusterManager.listNodes();
                org.apache.karaf.cellar.core.Node thisKarafNode = karafCellarClusterManager.getNode();
                Map<String, Properties> clusterConfigurations = karafCellarClusterManager.getMap(Constants.CONFIGURATION_MAP + Configurations.SEPARATOR + karafCellarGroupName);
                Properties karafCellarClusterNodeConfiguration = clusterConfigurations.get(KARAF_CELLAR_CLUSTER_NODE_CONFIGURATION);
                Map<String, String> publicNodeEndpoints = new TreeMap<>();
                Map<String, String> secureNodeEndpoints = new TreeMap<>();
                if (karafCellarClusterNodeConfiguration != null) {
                    String publicEndpointsPropValue = karafCellarClusterNodeConfiguration.getProperty(KARAF_CLUSTER_CONFIGURATION_PUBLIC_ENDPOINTS, thisKarafNode.getId() + "=" + address + ":" + port);
                    String secureEndpointsPropValue = karafCellarClusterNodeConfiguration.getProperty(KARAF_CLUSTER_CONFIGURATION_SECURE_ENDPOINTS, thisKarafNode.getId() + "=" + secureAddress + ":" + securePort);
                    String[] publicEndpointsArray = publicEndpointsPropValue.split(",");
                    Set<String> publicEndpoints = new TreeSet<String>(Arrays.asList(publicEndpointsArray));
                    for (String endpoint : publicEndpoints) {
                        String[] endpointParts = endpoint.split("=");
                        publicNodeEndpoints.put(endpointParts[0], endpointParts[1]);
                    }
                    String[] secureEndpointsArray = secureEndpointsPropValue.split(",");
                    Set<String> secureEndpoints = new TreeSet<String>(Arrays.asList(secureEndpointsArray));
                    for (String endpoint : secureEndpoints) {
                        String[] endpointParts = endpoint.split("=");
                        secureNodeEndpoints.put(endpointParts[0], endpointParts[1]);
                    }
                }
                for (org.apache.karaf.cellar.core.Node karafCellarNode : karafCellarNodes) {
                    ClusterNode clusterNode = new ClusterNode();
                    clusterNode.setHostName(karafCellarNode.getHost());
                    String publicEndpoint = publicNodeEndpoints.get(karafCellarNode.getId());
                    String[] publicEndpointParts = publicEndpoint.split(":");
                    clusterNode.setHostAddress(publicEndpointParts[0]);
                    clusterNode.setPublicPort(Integer.parseInt(publicEndpointParts[1]));
                    String secureEndpoint = secureNodeEndpoints.get(karafCellarNode.getId());
                    String[] secureEndpointParts = secureEndpoint.split(":");
                    clusterNode.setSecureHostAddress(secureEndpointParts[0]);
                    clusterNode.setSecurePort(Integer.parseInt(secureEndpointParts[1]));
                    clusterNode.setMaster(false);
                    clusterNode.setData(false);
                    try {
                        // now let's connect to remote JMX service to retrieve information from the runtime and operating system MX beans
                        JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://"+karafCellarNode.getHost() + ":"+karafJMXPort+"/karaf-root");
                        Map<String,Object> environment=new HashMap<String,Object>();
                        if (karafJMXUsername != null && karafJMXPassword != null) {
                            environment.put(JMXConnector.CREDENTIALS,new String[]{karafJMXUsername,karafJMXPassword});
                        }
                        JMXConnector jmxc = JMXConnectorFactory.connect(url, environment);
                        MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();
                        final RuntimeMXBean remoteRuntime = ManagementFactory.newPlatformMXBeanProxy(mbsc, ManagementFactory.RUNTIME_MXBEAN_NAME, RuntimeMXBean.class);
                        clusterNode.setUptime(remoteRuntime.getUptime());
                        ObjectName operatingSystemMXBeanName = new ObjectName(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME);
                        Double processCpuLoad = null;
                        Double systemCpuLoad = null;
                        try {
                            processCpuLoad = (Double) mbsc.getAttribute(operatingSystemMXBeanName, "ProcessCpuLoad");
                        } catch (MBeanException e) {
                            e.printStackTrace();
                        } catch (AttributeNotFoundException e) {
                            e.printStackTrace();
                        }
                        try {
                            systemCpuLoad = (Double) mbsc.getAttribute(operatingSystemMXBeanName, "SystemCpuLoad");
                        } catch (MBeanException e) {
                            e.printStackTrace();
                        } catch (AttributeNotFoundException e) {
                            e.printStackTrace();
                        }
                        final OperatingSystemMXBean remoteOperatingSystemMXBean = ManagementFactory.newPlatformMXBeanProxy(mbsc, ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME, OperatingSystemMXBean.class);
                        clusterNode.setLoadAverage(new double[] { remoteOperatingSystemMXBean.getSystemLoadAverage()});
                        if (systemCpuLoad != null) {
                            clusterNode.setCpuLoad(systemCpuLoad);
                        }

                    } catch (MalformedURLException e) {
                        logger.error("Error connecting to remote JMX server", e);
                    } catch (IOException e) {
                        logger.error("Error retrieving remote JMX data", e);
                    } catch (MalformedObjectNameException e) {
                        logger.error("Error retrieving remote JMX data", e);
                    } catch (InstanceNotFoundException e) {
                        logger.error("Error retrieving remote JMX data", e);
                    } catch (ReflectionException e) {
                        logger.error("Error retrieving remote JMX data", e);
                    }
                    clusterNodes.put(karafCellarNode.getId(), clusterNode);
                }

                return new ArrayList<ClusterNode>(clusterNodes.values());
            }
        }.executeInClassLoader();
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
        }.executeInClassLoader();

    }


    @Override
    public void purge(final Date date) {
        new InClassLoaderExecute<Object>() {
            @Override
            protected Object execute(Object... args) {
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
                            logger.error("Cannot parse index name " + currentIndexName, e);
                        }
                    }
                }
                if (!toDelete.isEmpty()) {
                    client.admin().indices().prepareDelete(toDelete.toArray(new String[toDelete.size()])).execute().actionGet();
                }
                return null;
            }
        }.executeInClassLoader();
    }

    @Override
    public void purge(final String scope) {
        new InClassLoaderExecute<Void>() {
            @Override
            protected Void execute(Object... args) {
                QueryBuilder query = QueryBuilders.termQuery("scope", scope);

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
        }.executeInClassLoader();
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
        }.executeInClassLoader();
    }

    private String getIndexNameForQuery(String itemType) {
        return indexNames.containsKey(itemType) ? indexNames.get(itemType) :
                (itemsMonthlyIndexed.contains(itemType) ? indexName + "-*" : indexName);
    }

    public abstract static class InClassLoaderExecute<T> {

        protected abstract T execute(Object... args);

        public T executeInClassLoader(Object... args) {
            ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
                return execute(args);
            } finally {
                Thread.currentThread().setContextClassLoader(tccl);
            }
        }
    }

    private String getConfig(Map<String,String> settings, String key,
                             String defaultValue) {
        if (settings != null && settings.get(key) != null) {
            return settings.get(key);
        }
        return defaultValue;
    }

    /**
     * Check if a configuration is allowed.
     *
     * @param group the cluster group.
     * @param category the configuration category constant.
     * @param pid the configuration PID.
     * @param type the cluster event type.
     * @return true if the cluster event type is allowed, false else.
     */
    public boolean isClusterConfigPIDAllowed(Group group, String category, String pid, EventType type) {
        CellarSupport support = new CellarSupport();
        support.setClusterManager(this.karafCellarClusterManager);
        support.setGroupManager(this.karafCellarGroupManager);
        support.setConfigurationAdmin(this.osgiConfigurationAdmin);
        return support.isAllowed(group, category, pid, type);
    }

}
