package org.oasis_open.wemi.context.server.persistence.elasticsearch;

import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.percolate.PercolateResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.get.GetField;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.node.Node;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHitField;
import org.elasticsearch.search.SearchHits;
import org.oasis_open.wemi.context.server.api.Item;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;

import static org.elasticsearch.node.NodeBuilder.*;
import static org.elasticsearch.common.xcontent.XContentFactory.*;

import java.beans.Beans;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Created by loom on 02.05.14.
 */
public class ElasticSearchPersistenceServiceImpl implements PersistenceService {

    private static final Logger logger = Logger.getLogger(ElasticSearchPersistenceServiceImpl.class.getName());

    Node node;
    Client client;

    public void start() {
        // on startup

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            logger.info("Starting ElasticSearch persistence backend...");
            node = nodeBuilder().clusterName("wemiElasticSearch").node();
            client = node.client();
        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }

    }

    public void stop() {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            logger.info("Closing ElasticSearch persistence backend...");
            node.close();
        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }
    }

    public boolean save(Item item) {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

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
            e.printStackTrace();
        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }

        return false;
    }

    public Item load(String itemId, String itemType, Class clazz) {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
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
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }

        return null;
    }

    public boolean saveQuery(String queryName, String query) {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        boolean successfull = false;
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            //Index the query = register it in the percolator
            client.prepareIndex("wemi", ".percolator", queryName)
                .setSource(query)
                .setRefresh(true) // Needed when the query shall be available immediately
                .execute().actionGet();
            successfull = true;
        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }
        return successfull;
    }

    public List<String> getMatchingSavedQueries(String document, String documentType) {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        List<String> matchingQueries = new ArrayList<String>();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            //Percolate
            PercolateResponse response = client.preparePercolate()
                                    .setIndices("wemi")
                                    .setDocumentType(documentType)
                                    .setSource(document).execute().actionGet();
            //Iterate over the results
            for(PercolateResponse.Match match : response) {
                //Handle the result which is the name of
                //the query in the percolator
                matchingQueries.add(match.getId().string());
            }
        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }
        return matchingQueries;

    }


    public List<Item> query(String itemType, String fieldName, String fieldValue, Class clazz) {
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
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
