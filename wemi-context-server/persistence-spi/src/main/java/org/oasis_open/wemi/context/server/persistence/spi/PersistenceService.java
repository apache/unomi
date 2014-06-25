package org.oasis_open.wemi.context.server.persistence.spi;

import org.oasis_open.wemi.context.server.api.Item;

import java.util.List;
import java.util.Properties;

/**
 * Created by loom on 02.05.14.
 */
public interface PersistenceService {

    public boolean save(Item item);

    public Item load(String itemId, String itemType, Class clazz);

    public boolean saveQuery(String queryName, String query);

    public boolean createMapping(String queryName, String query);

    public List<String> getMatchingSavedQueries(Item item);

    public List<Item> query(String itemType, String fieldName, String fieldValue, Class clazz);

}
