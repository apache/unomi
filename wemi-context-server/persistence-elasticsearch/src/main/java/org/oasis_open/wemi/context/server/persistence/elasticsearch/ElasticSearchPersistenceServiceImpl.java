package org.oasis_open.wemi.context.server.persistence.elasticsearch;

import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.percolate.PercolateResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.Node;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.oasis_open.wemi.context.server.api.Item;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions.ConditionESQueryBuilderDispatcher;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

/**
 * Created by loom on 02.05.14.
 */
public class ElasticSearchPersistenceServiceImpl implements PersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(ElasticSearchPersistenceServiceImpl.class.getName());

    private Node node;
    private Client client;
    private String clusterName = "wemiElasticSearch";
    private String indexName = "wemi";
    private String elasticSearchConfig = null;

    ConditionESQueryBuilderDispatcher conditionESQueryBuilderDispatcher;

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
                logger.info("Starting ElasticSearch persistence backend using cluster name "+clusterName+" and index name "+indexName+"...");
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
                IndicesExistsResponse indicesExistsResponse = client.admin().indices().prepareExists(indexName).execute().actionGet();
                if (!indicesExistsResponse.isExists()) {
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

    public boolean save(final Item item) {

        return new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) {
                try {
                    XContentBuilder jsonObject = jsonBuilder().startObject();
                    for (String propertyName : item.getProperties().stringPropertyNames()) {
                        String propertyValue = item.getProperty(propertyName);
                        jsonObject.field(propertyName, propertyValue);
                    }
                    jsonObject.field("itemClass", item.getClass().getName());
                    jsonObject.endObject();
                    IndexRequestBuilder indexBuilder = client.prepareIndex(indexName, item.getType(), item.getItemId())
                            .setSource(jsonObject);
                    if (item.getParentId() != null) {
                        indexBuilder = indexBuilder.setParent(item.getParentId());
                    }
                    IndexResponse response = indexBuilder
                            .execute()
                            .actionGet();
                    return true;
                } catch (IOException e) {
                    logger.error("Error saving item " + item, e);
                }
                return false;
            }
        }.executeInClassLoader(item);

    }

    public <T extends Item> T load(final String itemId, final String itemType, final Class<T> clazz) {

        return new InClassLoaderExecute<T>() {
            protected T execute(Object... args) {
                try {
                    try {
                        // Check if class has a parent defined - use a query instead, as we can't do a get on items
                        // with parents
                        clazz.getField("PARENT_ITEM_TYPE");

                        List<T> r = query(itemType, QueryBuilders.idsQuery(itemType).ids(itemId),clazz);
                        if (r.size() > 0) {
                            return r.get(0);
                        }
                        return null;
                    } catch (NoSuchFieldException e) {
                        GetResponse response = client.prepareGet(indexName, itemType, itemId)
                                .execute()
                                .actionGet();
                        Constructor<T> constructor = clazz.getConstructor(String.class, String.class, Properties.class);
                        Map<String, Object> sourceMap = response.getSource();
                        if (sourceMap == null) {
                            return null;
                        }
                        Properties properties = new Properties();
                        for (Map.Entry<String, Object> sourceEntry : sourceMap.entrySet()) {
                            properties.setProperty(sourceEntry.getKey(), sourceEntry.getValue().toString());
                        }
                        return constructor.newInstance(response.getId(), response.getType(), properties);
                    }
                } catch (InstantiationException e) {
                    logger.error("Error loading itemType=" + itemType + "itemId=" + itemId, e);
                } catch (IllegalAccessException e) {
                    logger.error("Error loading itemType=" + itemType + "itemId=" + itemId, e);
                } catch (NoSuchMethodException e) {
                    logger.error("Error loading itemType=" + itemType + "itemId=" + itemId, e);
                } catch (InvocationTargetException e) {
                    logger.error("Error loading itemType=" + itemType + "itemId=" + itemId, e);
                } catch (Throwable t) {
                    logger.error("Error loading itemType=" + itemType + "itemId=" + itemId, t);
                }
                return null;
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

    public Map<String, Map<String,String>> getMapping(final String itemType) {
        return new InClassLoaderExecute<Map<String, Map<String,String>>>() {
            protected Map<String, Map<String,String>> execute(Object... args) {
                GetMappingsResponse getMappingsResponse = client.admin().indices().prepareGetMappings(indexName).setTypes(itemType).execute().actionGet();
                ImmutableOpenMap<String, ImmutableOpenMap<String,MappingMetaData>> mappings = getMappingsResponse.getMappings();
                Map<String,Map<String,String>> propertyMap = null;
                try {
                    propertyMap = (Map<String,Map<String,String>>) mappings.get(indexName).get(itemType).getSourceAsMap().get("properties");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return new HashMap<String,Map<String,String>>(propertyMap);
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
        }.executeInClassLoader(queryName, query);
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
        }.executeInClassLoader(queryName);
    }

    public List<String> getMatchingSavedQueries(final Item item) {
        return new InClassLoaderExecute<List<String>>() {
            protected List<String> execute(Object... args) {
                List<String> matchingQueries = new ArrayList<String>();
                try {
                    XContentBuilder documentJsonObject = jsonBuilder().startObject();
                    documentJsonObject.startObject("doc");
                    for (String propertyName : item.getProperties().stringPropertyNames()) {
                        String propertyValue = item.getProperty(propertyName);
                        documentJsonObject.field(propertyName, propertyValue);
                    }
                    documentJsonObject.field("itemClass", item.getClass().getName());
                    documentJsonObject.endObject();
                    documentJsonObject.endObject();

                    //Percolate
                    PercolateResponse response = client.preparePercolate()
                                            .setIndices(indexName)
                                            .setDocumentType(item.getType())
                                            .setSource(documentJsonObject).execute().actionGet();
                    //Iterate over the results
                    for(PercolateResponse.Match match : response) {
                        //Handle the result which is the name of
                        //the query in the percolator
                        matchingQueries.add(match.getId().string());
                    }
                } catch (IOException e) {
                    logger.error("Error getting matching saved queries for item=" + item, e);
                }
                return matchingQueries;
            }
        }.executeInClassLoader(item);

    }

    public <T extends Item> List<T> query(final String itemType, final Condition query, final Class<T> clazz) {
        return query(itemType, conditionESQueryBuilderDispatcher.getQueryBuilder(query), clazz);
    }

    public <T extends Item> List<T> query(final String itemType, final String fieldName, final String fieldValue, final Class<T> clazz) {
        return query(itemType, QueryBuilders.termQuery(fieldName, fieldValue), clazz);
    }

    public <T extends Item> List<T> query(final String itemType, final QueryBuilder query, final Class<T> clazz) {
        return new InClassLoaderExecute<List<T>>() {

            @Override
            protected List<T> execute(Object... args) {
                List<T> results = new ArrayList<T>();
                SearchResponse response = client.prepareSearch(indexName)
                        .setTypes(itemType)
                        .setFetchSource(true)
                        .setSearchType(SearchType.QUERY_AND_FETCH)
                        .setQuery(query)
                        .setFrom(0).setSize(60)
                        .execute()
                        .actionGet();
                SearchHits searchHits = response.getHits();
                for (SearchHit searchHit : searchHits) {
                    try {
                        Constructor<T> constructor = clazz.getConstructor(String.class, String.class, Properties.class);
                        Map<String, Object> sourceMap = searchHit.getSource();
                        Properties properties = new Properties();
                        for (Map.Entry<String, Object> sourceEntry : sourceMap.entrySet()) {
                            properties.setProperty(sourceEntry.getKey(), sourceEntry.getValue().toString());
                        }
                        results.add(constructor.newInstance(searchHit.getId(), searchHit.getType(), properties));
                    } catch (InstantiationException e) {
                        logger.error("Error loading itemType=" + itemType + "query=" + query, e);
                    } catch (IllegalAccessException e) {
                        logger.error("Error loading itemType=" + itemType + "query=" + query, e);
                    } catch (NoSuchMethodException e) {
                        logger.error("Error loading itemType=" + itemType + "query=" + query, e);
                    } catch (InvocationTargetException e) {
                        logger.error("Error loading itemType=" + itemType + "query=" + query, e);
                    } catch (Throwable t) {
                        logger.error("Error loading itemType=" + itemType + "query=" + query, t);
                    }
                }
                return results;
            }
        }.executeInClassLoader();
    }

    public List<String> aggregateQuery(final String itemType, final Condition filter, final String aggregateOnField) {
        return new InClassLoaderExecute<List<String>>() {

            @Override
            protected List<String> execute(Object... args) {
                List<String> results = new ArrayList<String>();
                SearchResponse response = client.prepareSearch(indexName)
                        .setTypes(itemType)
                        .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                        .setQuery(QueryBuilders.matchAllQuery())
                        .addAggregation(AggregationBuilders.filter("filter").filter(conditionESQueryBuilderDispatcher.buildFilter(filter)).subAggregation(AggregationBuilders.terms("terms").field(aggregateOnField)))
                        .setFrom(0).setSize(0).setExplain(true)
                        .execute()
                        .actionGet();
                Aggregations searchHits = response.getAggregations();
                Filter filter = searchHits.get("filter");
                Terms terms = filter.getAggregations().get("terms");
                for (Terms.Bucket bucket : terms.getBuckets()) {
                    results.add(bucket.getKey());
                }

                return results;
            }
        }.executeInClassLoader();
    }


}
