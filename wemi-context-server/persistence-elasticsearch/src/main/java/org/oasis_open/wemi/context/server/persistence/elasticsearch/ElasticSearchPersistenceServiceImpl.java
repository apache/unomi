package org.oasis_open.wemi.context.server.persistence.elasticsearch;

import org.elasticsearch.client.Client;
import org.elasticsearch.node.Node;
import org.oasis_open.wemi.context.server.api.Item;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;
import static org.elasticsearch.node.NodeBuilder.*;

import java.util.List;

/**
 * Created by loom on 02.05.14.
 */
public class ElasticSearchPersistenceServiceImpl implements PersistenceService {

    Node node;
    Client client;

    public void start() {
        // on startup

        node = nodeBuilder().clusterName("clusterName").node();
        client = node.client();
    }

    public void stop() {
        node.close();
    }

    public boolean save(Item item) {
        return false;
    }

    public Item load(String itemId) {
        return null;
    }

    public List<Item> query(String query) {
        return null;
    }
}
