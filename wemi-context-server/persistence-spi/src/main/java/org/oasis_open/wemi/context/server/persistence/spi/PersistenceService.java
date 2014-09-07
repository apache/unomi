package org.oasis_open.wemi.context.server.persistence.spi;

import org.oasis_open.wemi.context.server.api.Item;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by loom on 02.05.14.
 */
public interface PersistenceService {

    public <T extends Item> Collection<T> getAllItems(Class<T> clazz);

    public <T extends Item> long getAllItemsCount(Class<T> clazz);

    public <T extends Item> Collection<T> getAllItems(Class<T> clazz, int offset, int size);

    public boolean save(Item item);

    public <T extends Item> T load(String itemId, Class<T> clazz);

    public <T extends Item> boolean remove(String itemId, Class<T> clazz);

    public boolean saveQuery(String queryName, Condition query);

    public boolean removeQuery(String queryName);

    public boolean createMapping(String queryName, String query);

    public Map<String, Map<String,String>> getMapping(String itemType);

    public List<String> getMatchingSavedQueries(Item item);

    public boolean testMatch(Condition query, Item item);

    public <T extends Item> List<T> query(Condition query, String sortBy, Class<T> clazz);

    public <T extends Item> long queryCount(Condition query, Class<T> clazz);

    public <T extends Item> List<T> query(String fieldName, String fieldValue, String sortBy, Class<T> clazz);

    public <T extends Item> Map<String, Long> aggregateQuery(Condition filter, Aggregate aggregate, Class<T> clazz);

}
