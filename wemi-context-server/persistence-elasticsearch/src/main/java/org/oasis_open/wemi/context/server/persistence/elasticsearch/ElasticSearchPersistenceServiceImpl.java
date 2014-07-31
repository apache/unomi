package org.oasis_open.wemi.context.server.persistence.elasticsearch;

import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.percolate.PercolateResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.Node;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHitField;
import org.elasticsearch.search.SearchHits;
import org.oasis_open.wemi.context.server.api.Item;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions.ConditionESQueryBuilderDispatcher;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.elasticsearch.node.NodeBuilder.*;
import static org.elasticsearch.common.xcontent.XContentFactory.*;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * Created by loom on 02.05.14.
 */
public class ElasticSearchPersistenceServiceImpl implements PersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(ElasticSearchPersistenceServiceImpl.class.getName());

    Node node;
    Client client;

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

    public void start() {
        // on startup
        new InClassLoaderExecute<Object>() {
            public Object execute(Object... args) {
                logger.info("Starting ElasticSearch persistence backend...");
                node = nodeBuilder().clusterName("wemiElasticSearch").node();
                client = node.client();
                IndicesExistsResponse indicesExistsResponse = client.admin().indices().prepareExists("wemi").execute().actionGet();
                if (!indicesExistsResponse.isExists()) {
                    logger.info("WEMI index doesn't exist yet, creating it...");
                    CreateIndexResponse createIndexResponse = client.admin().indices().prepareCreate("wemi").execute().actionGet();
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
                    IndexRequestBuilder indexBuilder = client.prepareIndex("wemi", item.getType(), item.getItemId())
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

    public Item load(final String itemId, final String itemType, final Class clazz) {

        return new InClassLoaderExecute<Item>() {
            protected Item execute(Object... args) {
                try {
                    GetResponse response = client.prepareGet("wemi", itemType, itemId)
                            .execute()
                            .actionGet();
                    Constructor constructor = clazz.getConstructor(String.class, String.class, Properties.class);
                    Map<String,Object> sourceMap = response.getSource();
                    if (sourceMap == null) {
                        return null;
                    }
                    Properties properties = new Properties();
                    for (Map.Entry<String,Object> sourceEntry : sourceMap.entrySet()) {
                        properties.setProperty(sourceEntry.getKey(), sourceEntry.getValue().toString());
                    }
                    Item itemInstance = (Item) constructor.newInstance(response.getId(), response.getType(), properties);
                    return itemInstance;
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
        }.executeInClassLoader(itemId, itemType, clazz);

    }

    public boolean createMapping(final String type, final String source) {
        return new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) {
                client.admin().indices()
                        .preparePutMapping("wemi")
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
                GetMappingsResponse getMappingsResponse = client.admin().indices().prepareGetMappings("wemi").setTypes(itemType).execute().actionGet();
                ImmutableOpenMap<String, ImmutableOpenMap<String,MappingMetaData>> mappings = getMappingsResponse.getMappings();
                Map<String,Map<String,String>> propertyMap = null;
                try {
                    propertyMap = (Map<String,Map<String,String>>) mappings.get("wemi").get("user").getSourceAsMap().get("properties");
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
                    client.prepareIndex("wemi", ".percolator", queryName)
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
        final ConditionESQueryBuilderDispatcher builder = new ConditionESQueryBuilderDispatcher();
        saveQuery(queryName, builder.getQuery(query));
        return true;
    }

    public boolean removeQuery(final String queryName) {
        return new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) {
            //Index the query = register it in the percolator
                try {
                    client.prepareDelete("wemi", ".percolator", queryName)
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
                                            .setIndices("wemi")
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

    public List<Item> query(final String itemType, final String fieldName, final String fieldValue, final Class clazz) {

        return new InClassLoaderExecute<List<Item>>() {

            @Override
            protected List<Item> execute(Object... args) {
                List<Item> results = new ArrayList<Item>();
                SearchResponse response = client.prepareSearch("wemi")
                        .setTypes(itemType)
                        .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                        .setQuery(QueryBuilders.termQuery(fieldName, fieldValue))             // Query
                        // .setPostFilter(FilterBuilders.rangeFilter("age").from(12).to(18))   // Filter
                        .setFrom(0).setSize(60).setExplain(true)
                        .execute()
                        .actionGet();
                SearchHits searchHits = response.getHits();
                for (SearchHit searchHit : searchHits) {
                    Map<String, SearchHitField> fields = searchHit.getFields();
                    try {
                        Item itemInstance = null;
                        itemInstance = (Item) clazz.newInstance();
                        for (Map.Entry<String,SearchHitField> searchHitFieldEntry : fields.entrySet()) {
                            itemInstance.setProperty(searchHitFieldEntry.getKey(), searchHitFieldEntry.getValue().getValue().toString());
                        }
                        results.add(itemInstance);
                    } catch (InstantiationException e) {
                        logger.error("Error while executing query on itemType=" + itemType + " fieldName=" + fieldName + " fieldValue=" + fieldValue + " clazz=" + clazz, e);
                    } catch (IllegalAccessException e) {
                        logger.error("Error while executing query on itemType=" + itemType + " fieldName=" + fieldName + " fieldValue=" + fieldValue + " clazz=" + clazz, e);
                    }
                }
                return results;
            }
        }.executeInClassLoader(itemType, fieldName, fieldValue, clazz);
    }
}
