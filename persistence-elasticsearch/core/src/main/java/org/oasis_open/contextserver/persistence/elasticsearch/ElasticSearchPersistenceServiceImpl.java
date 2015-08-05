package org.oasis_open.contextserver.persistence.elasticsearch;

/*
 * #%L
 * context-server-persistence-elasticsearch-core
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilterFactory;
import org.apache.lucene.util.ArrayUtil;
import org.elasticsearch.action.admin.cluster.node.info.NodeInfo;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.action.admin.cluster.node.stats.NodeStats;
import org.elasticsearch.action.admin.cluster.node.stats.NodesStatsResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.action.admin.indices.stats.IndicesStatsResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.percolate.PercolateResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.action.support.nodes.NodesOperationRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Requests;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.collect.UnmodifiableIterator;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.*;
import org.elasticsearch.indices.IndexMissingException;
import org.elasticsearch.node.Node;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.*;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.global.Global;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogram;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramBuilder;
import org.elasticsearch.search.aggregations.bucket.missing.MissingBuilder;
import org.elasticsearch.search.aggregations.bucket.range.RangeBuilder;
import org.elasticsearch.search.aggregations.bucket.range.date.DateRangeBuilder;
import org.elasticsearch.search.aggregations.bucket.range.ipv4.IPv4RangeBuilder;
import org.elasticsearch.search.aggregations.metrics.InternalNumericMetricsAggregation;
import org.elasticsearch.search.sort.GeoDistanceSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.oasis_open.contextserver.api.*;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.query.DateRange;
import org.oasis_open.contextserver.api.query.IpRange;
import org.oasis_open.contextserver.api.query.NumericRange;
import org.oasis_open.contextserver.api.services.ClusterService;
import org.oasis_open.contextserver.persistence.elasticsearch.conditions.*;
import org.oasis_open.contextserver.persistence.spi.CustomObjectMapper;
import org.oasis_open.contextserver.persistence.spi.PersistenceService;
import org.oasis_open.contextserver.persistence.spi.aggregate.*;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.SynchronousBundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

@SuppressWarnings("rawtypes")
public class ElasticSearchPersistenceServiceImpl implements PersistenceService, ClusterService, SynchronousBundleListener {

    public static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;
    private static final Logger logger = LoggerFactory.getLogger(ElasticSearchPersistenceServiceImpl.class.getName());
    private Node node;
    private Client client;
    private String clusterName;
    private String indexName;
    private String monthlyIndexNumberOfShards;
    private String monthlyIndexNumberOfReplicas;
    private String numberOfShards;
    private String numberOfReplicas;
    private Boolean nodeData;
    private Boolean discoveryEnabled;
    private String elasticSearchConfig = null;
    private BundleContext bundleContext;
    private Map<String, String> mappings = new HashMap<String, String>();
    private ConditionEvaluatorDispatcher conditionEvaluatorDispatcher;
    private ConditionESQueryBuilderDispatcher conditionESQueryBuilderDispatcher;

    private Map<String,String> indexNames;
    private List<String> itemsMonthlyIndexed;
    private Map<String, String> routingByType;

    private String address;
    private String port;
    private String secureAddress;
    private String securePort;

    private Timer timer;

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

    public void setDiscoveryEnabled(Boolean discoveryEnabled) {
        this.discoveryEnabled = discoveryEnabled;
    }

    public void setNumberOfShards(String numberOfShards) {
        this.numberOfShards = numberOfShards;
    }

    public void setNumberOfReplicas(String numberOfReplicas) {
        this.numberOfReplicas = numberOfReplicas;
    }

    public void setNodeData(Boolean nodeData) {
        this.nodeData = nodeData;
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

    public void start() {

        loadPredefinedMappings(bundleContext, false);

        // on startup
        new InClassLoaderExecute<Object>() {
            public Object execute(Object... args) {
                logger.info("Starting ElasticSearch persistence backend using cluster name " + clusterName + " and index name " + indexName + "...");
                Map<String, String> settings = null;
                if (elasticSearchConfig != null && elasticSearchConfig.length() > 0) {
                    try {
                        URL elasticSearchConfigURL = new URL(elasticSearchConfig);
                        Settings.Builder settingsBuilder = ImmutableSettings.builder().loadFromUrl(elasticSearchConfigURL);
                        settings = settingsBuilder.build().getAsMap();
                        logger.info("Successfully loaded ElasticSearch configuration from " + elasticSearchConfigURL);
                    } catch (MalformedURLException e) {
                        logger.error("Error in ElasticSearch configuration URL ", e);
                    } catch (SettingsException se) {
                        logger.info("Error trying to load settings from " + elasticSearchConfig + ": " + se.getMessage() + " (activate debug mode for exception details)");
                        if (logger.isDebugEnabled()) {
                            logger.debug("Exception details", se);
                        }
                    }
                }

                address = System.getProperty("contextserver.address", address);
                port = System.getProperty("contextserver.port", port);
                secureAddress = System.getProperty("contextserver.secureAddress", secureAddress);
                securePort = System.getProperty("contextserver.securePort", securePort);

                ImmutableSettings.Builder settingsBuilder = ImmutableSettings.builder();
                if (settings != null) {
                    settingsBuilder.put(settings);
                }

                settingsBuilder.put("cluster.name", clusterName)
                        .put("node.data", nodeData)
                        .put("discovery.zen.ping.multicast.enabled", discoveryEnabled)
                        .put("index.number_of_replicas", numberOfReplicas)
                        .put("index.number_of_shards", numberOfShards)
                        .put("node.contextserver.address", address)
                        .put("node.contextserver.port", port)
                        .put("node.contextserver.secureAddress", secureAddress)
                        .put("node.contextserver.securePort", securePort);

                node = nodeBuilder().settings(settingsBuilder).node();
                client = node.client();
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
                    internalCreateIndex(indexName);
                }

                for (Map.Entry<String, String> entry : mappings.entrySet()) {
                    if (!itemsMonthlyIndexed.contains(entry.getKey()) && !indexNames.containsKey(entry.getKey())) {
                        createMapping(entry.getKey(), entry.getValue(), indexName);
                    }
                }

                client.admin().indices().preparePutTemplate(indexName + "_monthlyindex")
                        .setTemplate(indexName + "-*")
                        .setOrder(1)
                        .setSettings(ImmutableSettings.settingsBuilder()
                                .put("number_of_shards", Integer.parseInt(monthlyIndexNumberOfShards))
                                .put("number_of_replicas", Integer.parseInt(monthlyIndexNumberOfReplicas))
                                .build()).execute().actionGet();

                getMonthlyIndex(new Date(), true);

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
                    getMonthlyIndex(gc.getTime(), true);
                }
            }
        }, 10000L, 24L * 60L * 60L * 1000L);
    }

    public void stop() {

        new InClassLoaderExecute<Object>() {
            protected Object execute(Object... args) {
                logger.info("Closing ElasticSearch persistence backend...");
                node.close();
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
                logger.info("{} index doesn't exist yet, creating it...", indexName);
                internalCreateIndex(monthlyIndexName);
                for (Map.Entry<String, String> entry : mappings.entrySet()) {
                    if (itemsMonthlyIndexed.contains(entry.getKey())) {
                        createMapping(entry.getKey(), entry.getValue(), monthlyIndexName);
                    }
                }
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
                mappings.put(name, content.toString());
                if (createMapping) {
                    if (itemsMonthlyIndexed.contains(name)) {
                        createMapping(name, content.toString(), indexName + "-*");
                    } else if (indexNames.containsKey(name)) {
                        createMapping(name, content.toString(), indexNames.get(name));
                    } else {
                        createMapping(name, content.toString(), indexName);
                    }
                }
            } catch (Exception e) {
                logger.error("Error while loading segment definition " + predefinedMappingURL, e);
            }
        }
    }


    @Override
    public <T extends Item> List<T> getAllItems(final Class<T> clazz) {
        return getAllItems(clazz, 0, -1, null).getList();
    }

    @Override
    public long getAllItemsCount(String itemType) {
        return queryCount(FilterBuilders.matchAllFilter(), itemType);
    }

    @Override
    public <T extends Item> PartialList<T> getAllItems(final Class<T> clazz, int offset, int size, String sortBy) {
        return query(QueryBuilders.matchAllQuery(), sortBy, clazz, offset, size, null);
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
                        PartialList<T> r = query(QueryBuilders.idsQuery(itemType).ids(itemId), null, clazz, 0, 1, null);
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
                } catch (IndexMissingException e) {
                    logger.debug("No index found for itemType=" + clazz.getName() + "itemId=" + itemId, e);
                } catch (IllegalAccessException e) {
                    logger.error("Error loading itemType=" + clazz.getName() + "itemId=" + itemId, e);
                } catch (Exception t) {
                    logger.error("Error loading itemType=" + clazz.getName() + "itemId=" + itemId, t);
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
                    String itemType = (String) item.getItemType();
                    String index = indexNames.containsKey(itemType) ? indexNames.get(itemType) :
                            (itemsMonthlyIndexed.contains(itemType) ? getMonthlyIndex(((TimestampedItem) item).getTimeStamp()) : indexName);
                    IndexRequestBuilder indexBuilder = client.prepareIndex(index, itemType, item.getItemId())
                            .setSource(source);
                    if (routingByType.containsKey(itemType)) {
                        indexBuilder = indexBuilder.setRouting(routingByType.get(itemType));
                    }
                    try {
                        indexBuilder.execute().actionGet();
                    } catch (IndexMissingException e) {
                        if (itemsMonthlyIndexed.contains(itemType)) {
                            getMonthlyIndex(((TimestampedItem) item).getTimeStamp(), true);
                            indexBuilder.execute().actionGet();
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

                    client.prepareUpdate(index, itemType, itemId).setDoc(source)
                            .execute()
                            .actionGet();
                    return true;
                } catch (IndexMissingException e) {
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

                    client.prepareDeleteByQuery(getIndexNameForQuery(itemType))
                            .setQuery(conditionESQueryBuilderDispatcher.getQueryBuilder(query))
                            .execute().actionGet();
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
                    internalCreateIndex(indexName);

                    for (Map.Entry<String, String> entry : mappings.entrySet()) {
                        if (indexNames.containsKey(entry.getKey()) && indexNames.get(entry.getKey()).equals(indexName)) {
                            createMapping(entry.getKey(), entry.getValue(), indexName);
                        }
                    }

                }
                return !indexExists;
            }
        }.executeInClassLoader();
    }


    private void internalCreateIndex(String indexName) {
        client.admin().indices().prepareCreate(indexName)
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
                        "}\n")
                .execute().actionGet();
    }


    private boolean createMapping(final String type, final String source, final String indexName) {
        client.admin().indices()
                .preparePutMapping(indexName)
                .setType(type)
                .setSource(source)
                .execute().actionGet();
        return true;
    }

    @Override
    public Map<String, Map<String, Object>> getMapping(final String itemType) {
        return new InClassLoaderExecute<Map<String, Map<String, Object>>>() {
            @SuppressWarnings("unchecked")
            protected Map<String, Map<String, Object>> execute(Object... args) {
                GetMappingsResponse getMappingsResponse = client.admin().indices().prepareGetMappings().setTypes(itemType).execute().actionGet();
                ImmutableOpenMap<String, ImmutableOpenMap<String, MappingMetaData>> mappings = getMappingsResponse.getMappings();
                Map<String, Map<String, Object>> propertyMap = new HashMap<>();
                try {
                    UnmodifiableIterator<ImmutableOpenMap<String, MappingMetaData>> it = mappings.valuesIt();
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
                            .setRefresh(true) // Needed when the query shall be available immediately
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
                            .setRefresh(true) // Needed when the query shall be available immediately
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
    public List<String> getMatchingSavedQueries(final Item item) {
        return new InClassLoaderExecute<List<String>>() {
            protected List<String> execute(Object... args) {
                List<String> matchingQueries = new ArrayList<String>();
                try {
                    String source = CustomObjectMapper.getObjectMapper().writeValueAsString(item);

                    String itemType = (String) item.getItemType();

                    //Percolate
                    PercolateResponse response = client.preparePercolate()
                            .setIndices(indexName)
                            .setDocumentType(itemType)
                            .setSource("{doc:" + source + "}").execute().actionGet();
                    //Iterate over the results
                    for (PercolateResponse.Match match : response) {
                        //Handle the result which is the name of
                        //the query in the percolator
                        matchingQueries.add(match.getId().string());
                    }
                } catch (IOException e) {
                    logger.error("Error getting matching saved queries for item=" + item, e);
                }
                return matchingQueries;
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

            FilterBuilder builder = FilterBuilders.andFilter(
                    FilterBuilders.idsFilter(itemType).ids(item.getItemId()),
                    conditionESQueryBuilderDispatcher.buildFilter(query));
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
        return query(conditionESQueryBuilderDispatcher.getQueryBuilder(query), sortBy, clazz, 0, -1, null).getList();
    }

    @Override
    public <T extends Item> PartialList<T> query(final Condition query, String sortBy, final Class<T> clazz, final int offset, final int size) {
        return query(conditionESQueryBuilderDispatcher.getQueryBuilder(query), sortBy, clazz, offset, size, null);
    }

    @Override
    public <T extends Item> PartialList<T> queryFullText(final String fulltext, final Condition query, String sortBy, final Class<T> clazz, final int offset, final int size) {
        return query(QueryBuilders.boolQuery().must(QueryBuilders.queryStringQuery(fulltext).defaultField("_all")).must(conditionESQueryBuilderDispatcher.getQueryBuilder(query)), sortBy, clazz, offset, size, null);
    }

    @Override
    public <T extends Item> List<T> query(final String fieldName, final String fieldValue, String sortBy, final Class<T> clazz) {
        return query(fieldName, fieldValue, sortBy, clazz, 0, -1).getList();
    }

    @Override
    public <T extends Item> List<T> query(final String fieldName, final String[] fieldValues, String sortBy, final Class<T> clazz) {
        return query(QueryBuilders.termsQuery(fieldName, ConditionContextHelper.foldToASCII(fieldValues)), sortBy, clazz, 0, -1, getRouting(fieldName, fieldValues, clazz)).getList();
    }

    @Override
    public <T extends Item> PartialList<T> query(String fieldName, String fieldValue, String sortBy, Class<T> clazz, int offset, int size) {
        return query(QueryBuilders.termQuery(fieldName, ConditionContextHelper.foldToASCII(fieldValue)), sortBy, clazz, offset, size, getRouting(fieldName, new String[]{fieldValue}, clazz));
    }

    @Override
    public <T extends Item> PartialList<T> queryFullText(String fieldName, String fieldValue, String fulltext, String sortBy, Class<T> clazz, int offset, int size) {
        return query(QueryBuilders.boolQuery().must(QueryBuilders.queryStringQuery(fulltext).defaultField("_all")).must(QueryBuilders.termQuery(fieldName, fieldValue)), sortBy, clazz, offset, size, getRouting(fieldName, new String[]{fieldValue}, clazz));
    }

    @Override
    public <T extends Item> PartialList<T> queryFullText(String fulltext, String sortBy, Class<T> clazz, int offset, int size) {
        return query(QueryBuilders.queryStringQuery(fulltext).defaultField("_all"), sortBy, clazz, offset, size, getRouting("_all", new String[]{fulltext}, clazz));
    }

    @Override
    public <T extends Item> PartialList<T> rangeQuery(String fieldName, String from, String to, String sortBy, Class<T> clazz, int offset, int size) {
        RangeQueryBuilder builder = QueryBuilders.rangeQuery(fieldName);
        builder.from(from);
        builder.to(to);
        return query(builder, sortBy, clazz, offset, size, null);
    }

    @Override
    public long queryCount(Condition query, String itemType) {
        return queryCount(conditionESQueryBuilderDispatcher.buildFilter(query), itemType);
    }

    private long queryCount(final FilterBuilder filter, final String itemType) {
        return new InClassLoaderExecute<Long>() {

            @Override
            protected Long execute(Object... args) {
                SearchResponse response = client.prepareSearch(getIndexNameForQuery(itemType))
                        .setTypes(itemType)
                        .setSearchType(SearchType.COUNT)
                        .setQuery(QueryBuilders.matchAllQuery())
                        .addAggregation(AggregationBuilders.filter("filter").filter(filter))
                        .execute()
                        .actionGet();
                Aggregations searchHits = response.getAggregations();
                Filter filter = searchHits.get("filter");
                return filter.getDocCount();
            }
        }.executeInClassLoader();
    }

    private <T extends Item> PartialList<T> query(final QueryBuilder query, final String sortBy, final Class<T> clazz, final int offset, final int size, final String[] routing) {
        return new InClassLoaderExecute<PartialList<T>>() {

            @Override
            protected PartialList<T> execute(Object... args) {
                List<T> results = new ArrayList<T>();
                long totalHits = 0;
                try {
                    String itemType = getItemType(clazz);

                    SearchRequestBuilder requestBuilder = client.prepareSearch(getIndexNameForQuery(itemType))
                            .setTypes(itemType)
                            .setFetchSource(true)
                            .setQuery(query)
                            .setFrom(offset);
                    if (size != -1) {
                        requestBuilder.setSize(size);
                    } else {
                        requestBuilder.setSize(Integer.MAX_VALUE);
                    }
                    if (routing != null) {
                        requestBuilder.setRouting(routing);
                    }
                    if (sortBy != null) {
                        String[] sortByArray = sortBy.split(",");
                        for (String sortByElement : sortByArray) {
                            if (sortByElement.startsWith("geo:")) {
                                String[] elements = sortByElement.split(":");
                                GeoDistanceSortBuilder distanceSortBuilder = SortBuilders.geoDistanceSort(elements[1]).point(Double.parseDouble(elements[2]), Double.parseDouble(elements[3])).unit(DistanceUnit.KILOMETERS);
                                if (elements.length > 4 && elements[4].equals("desc")) {
                                    requestBuilder = requestBuilder.addSort(distanceSortBuilder.order(SortOrder.DESC));
                                } else {
                                    requestBuilder = requestBuilder.addSort(distanceSortBuilder.order(SortOrder.ASC));
                                }
                            } else {
                                if (sortByElement.endsWith(":desc")) {
                                    requestBuilder = requestBuilder.addSort(sortByElement.substring(0, sortByElement.length() - ":desc".length()), SortOrder.DESC);
                                } else if (sortByElement.endsWith(":asc")) {
                                    requestBuilder = requestBuilder.addSort(sortByElement.substring(0, sortByElement.length() - ":asc".length()), SortOrder.ASC);
                                } else {
                                    requestBuilder = requestBuilder.addSort(sortByElement, SortOrder.ASC);
                                }
                            }
                        }
                    }
                    SearchResponse response = requestBuilder
                            .execute()
                            .actionGet();
                    SearchHits searchHits = response.getHits();
                    totalHits = searchHits.getTotalHits();
                    for (SearchHit searchHit : searchHits) {
                        String sourceAsString = searchHit.getSourceAsString();
                        final T value = CustomObjectMapper.getObjectMapper().readValue(sourceAsString, clazz);
                        value.setItemId(searchHit.getId());
                        results.add(value);
                    }
                } catch (Exception t) {
                    logger.error("Error loading itemType=" + clazz.getName() + "query=" + query, t);
                }

                return new PartialList<T>(results, offset, size, totalHits);
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
                        .setSearchType(SearchType.COUNT)
                        .setQuery(QueryBuilders.matchAllQuery());

                List<AggregationBuilder> lastAggregation = new ArrayList<AggregationBuilder>();

                if (aggregate != null) {
                    AggregationBuilder bucketsAggregation = null;
                    if (aggregate instanceof DateAggregate) {
                        DateAggregate dateAggregate = (DateAggregate) aggregate;
                        DateHistogramBuilder dateHistogramBuilder = AggregationBuilders.dateHistogram("buckets").field(aggregate.getField()).interval(new DateHistogram.Interval((dateAggregate.getInterval())));
                        if (dateAggregate.getFormat() != null) {
                            dateHistogramBuilder.format(dateAggregate.getFormat());
                        }
                        bucketsAggregation = dateHistogramBuilder;
                    } else if (aggregate instanceof NumericRangeAggregate) {
                        RangeBuilder rangebuilder = AggregationBuilders.range("buckets").field(aggregate.getField());
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
                        DateRangeBuilder rangebuilder = AggregationBuilders.dateRange("buckets").field(aggregate.getField());
                        if (dateRangeAggregate.getFormat() != null) {
                            rangebuilder.format(dateRangeAggregate.getFormat());
                        }
                        for (DateRange range : dateRangeAggregate.getDateRanges()) {
                            if (range != null) {
                                rangebuilder.addRange(range.getKey(), range.getFrom(), range.getTo());
                            }
                        }
                        bucketsAggregation = rangebuilder;
                    } else if (aggregate instanceof IpRangeAggregate) {
                        IpRangeAggregate ipRangeAggregate = (IpRangeAggregate) aggregate;
                        IPv4RangeBuilder rangebuilder = AggregationBuilders.ipRange("buckets").field(aggregate.getField());
                        for (IpRange range : ipRangeAggregate.getRanges()) {
                            if (range != null) {
                                rangebuilder.addRange(range.getKey(), range.getFrom(), range.getTo());
                            }
                        }
                        bucketsAggregation = rangebuilder;
                    } else {
                        //default
                        bucketsAggregation = AggregationBuilders.terms("buckets").field(aggregate.getField()).size(Integer.MAX_VALUE);
                    }
                    if (bucketsAggregation != null) {
                        final MissingBuilder missingBucketsAggregation = AggregationBuilders.missing("missing").field(aggregate.getField());
                        for (AggregationBuilder aggregationBuilder : lastAggregation) {
                            bucketsAggregation.subAggregation(aggregationBuilder);
                            missingBucketsAggregation.subAggregation(aggregationBuilder);
                        }
                        lastAggregation = Arrays.asList(bucketsAggregation, missingBucketsAggregation);
                    }
                }

                if (filter != null) {
                    AggregationBuilder filterAggregation = AggregationBuilders.filter("filter").filter(conditionESQueryBuilderDispatcher.buildFilter(filter));
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
                            results.put(bucket.getKey(), bucket.getDocCount());
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

                NodesInfoResponse nodesInfoResponse = client.admin().cluster().prepareNodesInfo(NodesOperationRequest.ALL_NODES)
                        .setSettings(true)
                        .execute()
                        .actionGet();
                NodeInfo[] nodesInfoArray = nodesInfoResponse.getNodes();
                for (NodeInfo nodeInfo : nodesInfoArray) {
                    if (nodeInfo.getSettings().get("node.contextserver.address") != null) {
                        ClusterNode clusterNode = new ClusterNode();
                        clusterNode.setHostName(nodeInfo.getHostname());
                        clusterNode.setHostAddress(nodeInfo.getSettings().get("node.contextserver.address"));
                        clusterNode.setPublicPort(Integer.parseInt(nodeInfo.getSettings().get("node.contextserver.port")));
                        clusterNode.setSecureHostAddress(nodeInfo.getSettings().get("node.contextserver.secureAddress"));
                        clusterNode.setSecurePort(Integer.parseInt(nodeInfo.getSettings().get("node.contextserver.securePort")));
                        clusterNode.setMaster(nodeInfo.getNode().isMasterNode());
                        clusterNode.setData(nodeInfo.getNode().isDataNode());
                        clusterNodes.put(nodeInfo.getNode().getId(), clusterNode);
                    }
                }

                NodesStatsResponse nodesStatsResponse = client.admin().cluster().prepareNodesStats(NodesOperationRequest.ALL_NODES)
                        .setOs(true)
                        .setProcess(true)
                        .execute()
                        .actionGet();
                NodeStats[] nodeStatsArray = nodesStatsResponse.getNodes();
                for (NodeStats nodeStats : nodeStatsArray) {
                    ClusterNode clusterNode = clusterNodes.get(nodeStats.getNode().getId());
                    if (clusterNode != null) {
                        // the following may be null in the case where Sigar didn't initialize properly, for example
                        // because the native libraries were not installed or if we redeployed the OSGi bundle in which
                        // case Sigar cannot initialize properly since it tries to reload the native libraries, generates
                        // an error and doesn't initialize properly.
                        if (nodeStats.getProcess() != null && nodeStats.getProcess().getCpu() != null) {
                            clusterNode.setCpuLoad(nodeStats.getProcess().getCpu().getPercent());
                        }
                        if (nodeStats.getOs() != null) {
                            clusterNode.setLoadAverage(nodeStats.getOs().getLoadAverage());
                            clusterNode.setUptime(nodeStats.getOs().getUptime().getMillis());
                        }
                    }
                }

                return new ArrayList<ClusterNode>(clusterNodes.values());
            }
        }.executeInClassLoader();
    }

    @Override
    public void refresh() {
        new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) {
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
                        .setSuggest(false)
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
                QueryBuilder query = QueryBuilders.termQuery("scope", ConditionContextHelper.foldToASCII(scope));

                BulkRequestBuilder deleteByScope = client.prepareBulk();

                final TimeValue keepAlive = TimeValue.timeValueHours(1);
                SearchResponse response = client.prepareSearch(indexName + "*")
                        .setSearchType(SearchType.SCAN)
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
                        .setSearchType(SearchType.COUNT)
                        .setQuery(QueryBuilders.matchAllQuery());
                AggregationBuilder filterAggregation = AggregationBuilders.filter("metrics").filter(conditionESQueryBuilderDispatcher.buildFilter(condition));

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

    public abstract class InClassLoaderExecute<T> {

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


}
