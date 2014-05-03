package org.oasis_open.wemi.context.server.persistence.elasticsearch;

import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.node.Node;
import org.oasis_open.wemi.context.server.api.Item;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;

import static org.elasticsearch.node.NodeBuilder.*;
import static org.elasticsearch.common.xcontent.XContentFactory.*;

import java.io.IOException;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

/**
 * Created by loom on 02.05.14.
 */
public class ElasticSearchPersistenceServiceImpl implements PersistenceService {

    Node node;
    Client client;

    public void start() {
        // on startup

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
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
            IndexResponse response = client.prepareIndex("wemi", "item", item.getItemId())
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

    public Item load(String itemId) {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            GetResponse response = client.prepareGet("wemi", "item", itemId)
                    .execute()
                    .actionGet();
            String itemClass = (String) response.getField("itemClass").getValue();
        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }

        return null;
    }

    public List<Item> query(String query) {
        return null;
    }
}
