package org.oasis_open.wemi.context.server.persistence.elasticsearch;

import org.elasticsearch.action.admin.cluster.node.stats.NodeStats;
import org.elasticsearch.action.admin.cluster.node.stats.NodesStatsResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.percolate.PercolateResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
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
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogram;
import org.elasticsearch.search.aggregations.bucket.missing.MissingBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.oasis_open.wemi.context.server.api.ClusterNode;
import org.oasis_open.wemi.context.server.api.Item;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.services.ClusterService;
import org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions.ConditionESQueryBuilderDispatcher;
import org.oasis_open.wemi.context.server.persistence.spi.Aggregate;
import org.oasis_open.wemi.context.server.persistence.spi.CustomObjectMapper;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

/**
 * Created by loom on 02.05.14.
 */
public class ElasticSearchPersistenceServiceImpl implements PersistenceService, ClusterService {

    private static final Logger logger = LoggerFactory.getLogger(ElasticSearchPersistenceServiceImpl.class.getName());
    ConditionESQueryBuilderDispatcher conditionESQueryBuilderDispatcher;
    private Node node;
    private Client client;
    private String clusterName = "wemiElasticSearch";
    private String indexName = "wemi";
    private String elasticSearchConfig = null;

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public void setElasticSearchConfig(String elasticSearchConfig) {
        this.elasticSearchConfig = elasticSearchConfig;
    }

    public void setConditionESQueryBuilderDispatcher(ConditionESQueryBuilderDispatcher conditionESQueryBuilderDispatcher) {
        this.conditionESQueryBuilderDispatcher = conditionESQueryBuilderDispatcher;
    }

    public void start() {
        // on startup
        new InClassLoaderExecute<Object>() {
            public Object execute(Object... args) {
                logger.info("Starting ElasticSearch persistence backend using cluster name " + clusterName + " and index name " + indexName + "...");
                Settings.Builder settingsBuilder = null;
                if (elasticSearchConfig != null && elasticSearchConfig.length() > 0) {
                    try {
                        URL elasticSearchConfigURL = new URL(elasticSearchConfig);
                        settingsBuilder = ImmutableSettings.builder().loadFromUrl(elasticSearchConfigURL);
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
                if (settingsBuilder != null) {
                    node = nodeBuilder().settings(settingsBuilder).node();
                } else {
                    node = nodeBuilder().clusterName(clusterName).node();
                }
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
                return null;
            }
        }.executeInClassLoader();

    }

    public void stop() {

        new InClassLoaderExecute<Object>() {
            protected Object execute(Object... args) {
                logger.info("Closing ElasticSearch persistence backend...");
                node.close();
                return null;
            }
        }.executeInClassLoader();

    }

    @Override
    public <T extends Item> Collection<T> getAllItems(final Class<T> clazz) {
        return query(QueryBuilders.matchAllQuery(), null, clazz);
    }

    public <T extends Item> long getAllItemsCount(Class<T> clazz) {
        return queryCount(FilterBuilders.matchAllFilter(), clazz);
    }

    @Override
    public <T extends Item> Collection<T> getAllItems(final Class<T> clazz, int offset, int size) {
        return query(QueryBuilders.matchAllQuery(), null, clazz, offset, size);
    }

    public <T extends Item> T load(final String itemId, final Class<T> clazz) {
        return new InClassLoaderExecute<T>() {
            protected T execute(Object... args) {
                try {
                    String itemType = (String) clazz.getField("ITEM_TYPE").get(null);
                    try {
                        // Check if class has a parent defined - use a query instead, as we can't do a get on items
                        // with parents
                        clazz.getField("PARENT_ITEM_TYPE");

                        List<T> r = query(QueryBuilders.idsQuery(itemType).ids(itemId), null, clazz);
                        if (r.size() > 0) {
                            return r.get(0);
                        }
                        return null;
                    } catch (NoSuchFieldException e) {
                        GetResponse response = client.prepareGet(indexName, itemType, itemId)
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
                    IndexRequestBuilder indexBuilder = client.prepareIndex(indexName, itemType, item.getItemId())
                            .setSource(source);
                    if (item.getParentId() != null) {
                        indexBuilder = indexBuilder.setParent(item.getParentId());
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

                    client.prepareDelete(indexName, itemType, itemId)
                            .execute().actionGet();
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return false;
            }
        }.executeInClassLoader();
    }

    public boolean createMapping(final String type, final String source) {
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
            final Class<? extends Item> clazz = item.getClass();
            String itemType = (String) clazz.getField("ITEM_TYPE").get(null);

            FilterBuilder builder = FilterBuilders.andFilter(
                    FilterBuilders.idsFilter(itemType).ids(item.getItemId()),
                    conditionESQueryBuilderDispatcher.buildFilter(query));
            return query(QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), builder), null, clazz).size() > 0;
        } catch (IllegalAccessException e) {
            logger.error("Error getting query for item=" + item, e);
        } catch (NoSuchFieldException e) {
            logger.error("Error getting query for item=" + item, e);
        }
        return false;
    }

    public <T extends Item> List<T> query(final Condition query, String sortBy, final Class<T> clazz) {
        return query(conditionESQueryBuilderDispatcher.getQueryBuilder(query), sortBy, clazz);
    }

    public <T extends Item> List<T> query(final String fieldName, final String fieldValue, String sortBy, final Class<T> clazz) {
        return query(QueryBuilders.termQuery(fieldName, fieldValue), sortBy, clazz);
    }

    @Override
    public <T extends Item> long queryCount(final Condition query, final Class<T> clazz) {
        return queryCount(conditionESQueryBuilderDispatcher.buildFilter(query), clazz);
    }

    public <T extends Item> long queryCount(final FilterBuilder filter, final Class<T> clazz) {
        return new InClassLoaderExecute<Long>() {

            @Override
            protected Long execute(Object... args) {
                try {
                    String itemType = (String) clazz.getField("ITEM_TYPE").get(null);

                    SearchResponse response = client.prepareSearch(indexName)
                            .setTypes(itemType)
                            .setSearchType(SearchType.COUNT)
                            .setQuery(QueryBuilders.matchAllQuery())
                            .addAggregation(AggregationBuilders.filter("filter").filter(filter))
                            .execute()
                            .actionGet();
                    Aggregations searchHits = response.getAggregations();
                    Filter filter = searchHits.get("filter");
                    return filter.getDocCount();
                } catch (IllegalAccessException e) {
                    logger.error("Error loading itemType=" + clazz.getName() + "query=" + filter, e);
                } catch (NoSuchFieldException e) {
                    logger.error("Error loading itemType=" + clazz.getName() + "query=" + filter, e);
                }
                return -1L;
            }
        }.executeInClassLoader();
    }

    public <T extends Item> List<T> query(final QueryBuilder query, final String sortBy, final Class<T> clazz) {
        return query(query, sortBy, clazz, 0, 60);
    }

    public <T extends Item> List<T> query(final QueryBuilder query, final String sortBy, final Class<T> clazz, final int offset, final int size) {
        return new InClassLoaderExecute<List<T>>() {

            @Override
            protected List<T> execute(Object... args) {
                List<T> results = new ArrayList<T>();
                try {
                    String itemType = (String) clazz.getField("ITEM_TYPE").get(null);
                    SearchRequestBuilder requestBuilder = client.prepareSearch(indexName)
                            .setTypes(itemType)
                            .setFetchSource(true)
                            .setSearchType(SearchType.QUERY_AND_FETCH)
                            .setQuery(query)
                            .setFrom(offset).setSize(size);
                    if (sortBy != null) {
                        requestBuilder = requestBuilder.addSort(sortBy, SortOrder.ASC);
                    }
                    SearchResponse response = requestBuilder
                            .execute()
                            .actionGet();
                    SearchHits searchHits = response.getHits();
                    for (SearchHit searchHit : searchHits) {
                        String sourceAsString = searchHit.getSourceAsString();
                        final T value = CustomObjectMapper.getObjectMapper().readValue(sourceAsString, clazz);
                        value.setItemId(searchHit.getId());
                        results.add(value);
                    }
                } catch (NoSuchFieldException e) {
                    logger.error("Error loading itemType=" + clazz.getName() + "query=" + query, e);
                } catch (IllegalAccessException e) {
                    logger.error("Error loading itemType=" + clazz.getName() + "query=" + query, e);
                } catch (Throwable t) {
                    logger.error("Error loading itemType=" + clazz.getName() + "query=" + query, t);
                }

                return results;
            }
        }.executeInClassLoader();
    }

    public <T extends Item> Map<String, Long> aggregateQuery(final Condition filter, final Aggregate aggregate, final Class<T> clazz) {
        return new InClassLoaderExecute<Map<String, Long>>() {

            @Override
            protected Map<String, Long> execute(Object... args) {
                Map<String, Long> results = new LinkedHashMap<String, Long>();
                try {
                    String itemType = (String) clazz.getField("ITEM_TYPE").get(null);

                    SearchRequestBuilder builder = client.prepareSearch(indexName)
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

                    for (AggregationBuilder aggregationBuilder : lastAggregation) {
                        builder.addAggregation(aggregationBuilder);
                    }

                    SearchResponse response = builder.execute().actionGet();

                    Aggregations aggregations = response.getAggregations();
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
                } catch (IllegalAccessException e) {
                    logger.error("Error loading itemType=" + clazz.getName(), e);
                } catch (NoSuchFieldException e) {
                    logger.error("Error loading itemType=" + clazz.getName(), e);
                }
                return results;
            }
        }.executeInClassLoader();
    }

    public List<ClusterNode> getClusterNodes() {
        return new InClassLoaderExecute<List<ClusterNode>>() {

            @Override
            protected List<ClusterNode> execute(Object... args) {
                List<ClusterNode> clusterNodes = new ArrayList<ClusterNode>();

                NodesStatsResponse nodesStatsResponse = client.admin().cluster().prepareNodesStats(null)
                        .execute()
                        .actionGet();
                NodeStats[] nodeStatsArray = nodesStatsResponse.getNodes();
                for (NodeStats nodeStats : nodeStatsArray) {
                    ClusterNode clusterNode = new ClusterNode();
                    clusterNode.setHostName(nodeStats.getHostname());
                    clusterNode.setPublicPort(8181);
                    if (nodeStats.getProcess() != null) {
                        clusterNode.setCpuLoad(nodeStats.getProcess().getCpu().getPercent());
                    }
                    clusterNodes.add(clusterNode);
                }

                return clusterNodes;
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
