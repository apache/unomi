package org.oasis_open.wemi.context.server.persistence.elasticsearch;

import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.percolate.PercolateResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.Node;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHitField;
import org.elasticsearch.search.SearchHits;
import org.oasis_open.wemi.context.server.api.Item;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.elasticsearch.node.NodeBuilder.*;
import static org.elasticsearch.common.xcontent.XContentFactory.*;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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
                    IndexResponse response = client.prepareIndex("wemi", item.getType(), item.getItemId())
                            .setSource(jsonObject)
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

    public boolean saveQuery(final String queryName, final String query) {
        return new InClassLoaderExecute<Boolean>() {
            protected Boolean execute(Object... args) {
            //Index the query = register it in the percolator
            client.prepareIndex("wemi", ".percolator", queryName)
                .setSource(query)
                .setRefresh(true) // Needed when the query shall be available immediately
                .execute().actionGet();
                return true;
            }
        }.executeInClassLoader(queryName, query);
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
