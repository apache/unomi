package org.oasis_open.contextserver.persistence.elasticsearch;

import org.elasticsearch.action.admin.cluster.node.info.NodeInfo;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.action.admin.cluster.node.stats.NodeStats;
import org.elasticsearch.action.admin.cluster.node.stats.NodesStatsResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.action.admin.indices.stats.IndicesStatsResponse;
import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.percolate.PercolateResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.action.support.nodes.NodesOperationRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.Node;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.global.Global;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogram;
import org.elasticsearch.search.aggregations.bucket.missing.MissingBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.oasis_open.contextserver.api.*;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.services.ClusterService;
import org.oasis_open.contextserver.persistence.elasticsearch.conditions.ConditionESQueryBuilderDispatcher;
import org.oasis_open.contextserver.persistence.elasticsearch.conditions.ConditionEvaluatorDispatcher;
import org.oasis_open.contextserver.persistence.spi.Aggregate;
import org.oasis_open.contextserver.persistence.spi.CustomObjectMapper;
import org.oasis_open.contextserver.persistence.spi.PersistenceService;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

/**
 * Created by loom on 02.05.14.
 */
public class ElasticSearchPersistenceServiceImpl implements PersistenceService, ClusterService {

    public static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;
    private static final Logger logger = LoggerFactory.getLogger(ElasticSearchPersistenceServiceImpl.class.getName());
    private Node node;
    private Client client;
    private String clusterName;
    private String indexName;
    private String elasticSearchConfig = null;
    private BundleContext bundleContext;
    private Map<String, String> mappings = new HashMap<String, String>();
    private ConditionEvaluatorDispatcher conditionEvaluatorDispatcher;
    private ConditionESQueryBuilderDispatcher conditionESQueryBuilderDispatcher;

    private List<String> itemsDailyIndexed;
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

    public void setItemsDailyIndexed(List<String> itemsDailyIndexed) {
        this.itemsDailyIndexed = itemsDailyIndexed;
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

        // on startup
        new InClassLoaderExecute<Object>() {
            public Object execute(Object... args) {
                logger.info("Starting ElasticSearch persistence backend using cluster name " + clusterName + " and index name " + indexName + "...");
                Map<String,String> settings = null;
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
                        e.printStackTrace();
                    }
                }
                if (!indexExists) {
                    logger.info(indexName + " index doesn't exist yet, creating it...");
                    CreateIndexResponse createIndexResponse = client.admin().indices().prepareCreate(indexName).execute().actionGet();
                }

                PutIndexTemplateResponse response = client.admin().indices().preparePutTemplate(indexName + "_dailyindex")
                        .setTemplate(indexName + "-*")
                        .setOrder(1)
                        .setSettings(ImmutableSettings.settingsBuilder().put("number_of_shards", 1).build())
                        .execute().actionGet();
                return null;
            }
        }.executeInClassLoader();

        loadPredefinedMappings(bundleContext);

        initializeTimer();

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

    }

    private String getDailyIndex(Date date) {
        String d = new SimpleDateFormat("-YYYY-MM-dd").format(date);
        String dailyIndexName = indexName + d;

        IndicesExistsResponse indicesExistsResponse = client.admin().indices().prepareExists(dailyIndexName).execute().actionGet();
        boolean indexExists = indicesExistsResponse.isExists();

        if (!indexExists) {
            logger.info(indexName + " index doesn't exist yet, creating it...");
            CreateIndexResponse createIndexResponse = client.admin().indices().prepareCreate(dailyIndexName).execute().actionGet();

            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                createMapping(entry.getKey(), entry.getValue(), dailyIndexName);
            }
        }

        return dailyIndexName;
    }

    private void loadPredefinedMappings(BundleContext bundleContext) {
        Enumeration<URL> predefinedMappings = bundleContext.getBundle().findEntries("META-INF/wemi/mappings", "*.json", true);
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

                createMapping(name, content.toString(), indexName);
            } catch (Exception e) {
                logger.error("Error while loading segment definition " + predefinedMappingURL, e);
            }
        }
    }


    @Override
    public <T extends Item> List<T> getAllItems(final Class<T> clazz) {
        return getAllItems(clazz, 0, -1, null).getList();
    }

    public long getAllItemsCount(String itemType) {
        return queryCount(FilterBuilders.matchAllFilter(), itemType);
    }

    @Override
    public <T extends Item> PartialList<T> getAllItems(final Class<T> clazz, int offset, int size, String sortBy) {
        return query(QueryBuilders.matchAllQuery(), sortBy, clazz, offset, size, null);
    }

    public <T extends Item> T load(final String itemId, final Class<T> clazz) {
        return load(itemId, null, clazz);
    }

    public <T extends Item> T load(final String itemId, final Date dateHint, final Class<T> clazz) {
        return new InClassLoaderExecute<T>() {
            protected T execute(Object... args) {
                try {
                    String itemType = (String) clazz.getField("ITEM_TYPE").get(null);
                    String dailyIndexName = indexName;
                    if (itemsDailyIndexed.contains(itemType) && dateHint != null) {
                        dailyIndexName = getDailyIndex(dateHint);
                    }
                    if (itemsDailyIndexed.contains(itemType) && dateHint == null) {
                        PartialList<T> r = query(QueryBuilders.idsQuery(itemType).ids(itemId), null, clazz, 0, 1, null);
                        if (r.size() > 0) {
                            return r.get(0);
                        }
                        return null;
                    } else {
                        GetResponse response = client.prepareGet(dailyIndexName, itemType, itemId)
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
                } catch (IllegalAccessException e) {
                    logger.error("Error loading itemType=" + clazz.getName() + "itemId=" + itemId, e);
                } catch (Throwable t) {
                    logger.error("Error loading itemType=" + clazz.getName() + "itemId=" + itemId, t);
                }
                return null;
            }
        }.executeInClassLoader();

    }

    public boolean save(final Item item) {

        return new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) {
                try {
                    String source = CustomObjectMapper.getObjectMapper().writeValueAsString(item);
                    String itemType = (String) item.getClass().getField("ITEM_TYPE").get(null);
                    IndexRequestBuilder indexBuilder = client.prepareIndex(itemsDailyIndexed.contains(itemType) ? getDailyIndex(((TimestampedItem) item).getTimeStamp()) : indexName, itemType, item.getItemId())
                            .setSource(source);
                    if (routingByType.containsKey(itemType)) {
                        indexBuilder = indexBuilder.setRouting(routingByType.get(itemType));
                    }
                    IndexResponse response = indexBuilder
                            .execute()
                            .actionGet();
                    return true;
                } catch (NoSuchFieldException e) {
                    logger.error("Error saving item " + item, e);
                } catch (IllegalAccessException e) {
                    logger.error("Error saving item " + item, e);
                } catch (IOException e) {
                    logger.error("Error saving item " + item, e);
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

                    client.prepareDelete(itemsDailyIndexed.contains(itemType) ? indexName + "-*" : indexName, itemType, itemId)
                            .execute().actionGet();
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return false;
            }
        }.executeInClassLoader();
    }

    private boolean createMapping(final String type, final String source, final String indexName) {
        return new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) {
                client.admin().indices()
                        .preparePutMapping(indexName)
                        .setType(type)
                        .setSource(source)
                        .execute().actionGet();
                return true;
            }
        }.executeInClassLoader();
    }

    public Map<String, Map<String, String>> getMapping(final String itemType) {
        return new InClassLoaderExecute<Map<String, Map<String, String>>>() {
            protected Map<String, Map<String, String>> execute(Object... args) {
                GetMappingsResponse getMappingsResponse = client.admin().indices().prepareGetMappings(indexName).setTypes(itemType).execute().actionGet();
                ImmutableOpenMap<String, ImmutableOpenMap<String, MappingMetaData>> mappings = getMappingsResponse.getMappings();
                Map<String, Map<String, String>> propertyMap = null;
                try {
                    propertyMap = (Map<String, Map<String, String>>) mappings.get(indexName).get(itemType).getSourceAsMap().get("properties");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return new HashMap<String, Map<String, String>>(propertyMap);
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
                    e.printStackTrace();
                }
                return false;
            }
        }.executeInClassLoader();
    }

    public boolean saveQuery(String queryName, Condition query) {
        if (query == null) {
            return false;
        }
        saveQuery(queryName, conditionESQueryBuilderDispatcher.getQuery(query));
        return true;
    }

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
                    e.printStackTrace();
                }
                return false;
            }
        }.executeInClassLoader();
    }

    public List<String> getMatchingSavedQueries(final Item item) {
        return new InClassLoaderExecute<List<String>>() {
            protected List<String> execute(Object... args) {
                List<String> matchingQueries = new ArrayList<String>();
                try {
                    String source = CustomObjectMapper.getObjectMapper().writeValueAsString(item);

                    String itemType = (String) item.getClass().getField("ITEM_TYPE").get(null);

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
                } catch (NoSuchFieldException e) {
                    logger.error("Error getting matching saved queries for item=" + item, e);
                } catch (IllegalAccessException e) {
                    logger.error("Error getting matching saved queries for item=" + item, e);
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

    public <T extends Item> List<T> query(final Condition query, String sortBy, final Class<T> clazz) {
        return query(conditionESQueryBuilderDispatcher.getQueryBuilder(query), sortBy, clazz, 0, -1, null).getList();
    }

    public <T extends Item> PartialList<T> query(final Condition query, String sortBy, final Class<T> clazz, final int offset, final int size) {
        return query(conditionESQueryBuilderDispatcher.getQueryBuilder(query), sortBy, clazz, offset, size, null);
    }

    public <T extends Item> List<T> query(final String fieldName, final String fieldValue, String sortBy, final Class<T> clazz) {
        return query(fieldName, fieldValue, sortBy, clazz, 0, -1).getList();
    }

    public <T extends Item> List<T> query(final String fieldName, final String[] fieldValues, String sortBy, final Class<T> clazz) {
        return query(QueryBuilders.termsQuery(fieldName, fieldValues), sortBy, clazz, 0, -1, getRouting(fieldName, fieldValues, clazz)).getList();
    }

    @Override
    public <T extends Item> PartialList<T> query(String fieldName, String fieldValue, String sortBy, Class<T> clazz, int offset, int size) {
        return query(QueryBuilders.termQuery(fieldName, fieldValue), sortBy, clazz, offset, size, getRouting(fieldName, new String[]{fieldValue}, clazz));
    }

    @Override
    public long queryCount(Condition query, String itemType) {
        return queryCount(conditionESQueryBuilderDispatcher.buildFilter(query), itemType);
    }

    private long queryCount(final FilterBuilder filter, final String itemType) {
        return new InClassLoaderExecute<Long>() {

            @Override
            protected Long execute(Object... args) {
                SearchResponse response = client.prepareSearch(itemsDailyIndexed.contains(itemType) ? indexName + "-*" : indexName)
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
                    SearchRequestBuilder requestBuilder = client.prepareSearch(itemsDailyIndexed.contains(itemType) ? indexName + "-*" : indexName)
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
                            if (sortByElement.endsWith(":desc")) {
                                requestBuilder = requestBuilder.addSort(sortByElement.substring(0, sortByElement.length() - ":desc".length()), SortOrder.DESC);
                            } else if (sortByElement.endsWith(":asc")) {
                                requestBuilder = requestBuilder.addSort(sortByElement.substring(0, sortByElement.length() - ":asc".length()), SortOrder.ASC);
                            } else {
                                requestBuilder = requestBuilder.addSort(sortByElement, SortOrder.ASC);
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

    public Map<String, Long> aggregateQuery(final Condition filter, final Aggregate aggregate, final String itemType) {
        return new InClassLoaderExecute<Map<String, Long>>() {

            @Override
            protected Map<String, Long> execute(Object... args) {
                Map<String, Long> results = new LinkedHashMap<String, Long>();

                SearchRequestBuilder builder = client.prepareSearch(itemsDailyIndexed.contains(itemType) ? indexName + "-*" : indexName)
                        .setTypes(itemType)
                        .setSearchType(SearchType.COUNT)
                        .setQuery(QueryBuilders.matchAllQuery());

                List<AggregationBuilder> lastAggregation = new ArrayList<AggregationBuilder>();

                if (aggregate != null) {
                    AggregationBuilder bucketsAggregation = null;
                    switch (aggregate.getType()) {
                        case TERMS:
                            bucketsAggregation = AggregationBuilders.terms("buckets").field(aggregate.getField());
                            break;
                        case DATE:
                            bucketsAggregation = AggregationBuilders.dateHistogram("buckets").field(aggregate.getField()).interval(new DateHistogram.Interval("1M"));
                            break;
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
                    lastAggregation = Arrays.asList(filterAggregation);
                }


                AggregationBuilder globalAggregation = AggregationBuilders.global("global");
                for (AggregationBuilder aggregationBuilder : lastAggregation) {
                    globalAggregation.subAggregation(aggregationBuilder);
                }

                builder.addAggregation(globalAggregation);

                SearchResponse response = builder.execute().actionGet();

                Aggregations aggregations = response.getAggregations();

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

                return results;
            }
        }.executeInClassLoader();
    }

    private <T extends Item> String getItemType(Class<T> clazz) {
        try {
            return (String) clazz.getField("ITEM_TYPE").get(null);
        } catch (NoSuchFieldException e) {
            logger.error("Error loading itemType=" + clazz.getName(), e);
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

    private void initializeTimer() {
        final int autoPurge = node.settings().getAsInt("node.contextserver.autoPurge", -1);
        if (autoPurge > 0) {
            timer = new Timer();
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    purge(getDay(-autoPurge).getTime());
                }
            };
            timer.scheduleAtFixedRate(task, getDay(1).getTime(), MILLIS_PER_DAY);
        }
    }

    private GregorianCalendar getDay(int offset) {
        GregorianCalendar gc = new GregorianCalendar();
        gc = new GregorianCalendar(gc.get(Calendar.YEAR), gc.get(Calendar.MONTH), gc.get(Calendar.DAY_OF_MONTH));
        gc.add(Calendar.DAY_OF_MONTH, offset);
        return gc;
    }


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

                SimpleDateFormat d = new SimpleDateFormat("yyyy-MM-dd");

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
                    DeleteIndexResponse response = client.admin().indices().prepareDelete(toDelete.toArray(new String[toDelete.size()])).execute().actionGet();
                }
                return null;
            }
        }.executeInClassLoader();
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
