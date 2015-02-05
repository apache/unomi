package org.oasis_open.contextserver.persistence.spi;

import org.oasis_open.contextserver.api.Item;
import org.oasis_open.contextserver.api.PartialList;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.persistence.spi.aggregate.BaseAggregate;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by loom on 02.05.14.
 */
public interface PersistenceService {

    public <T extends Item> List<T> getAllItems(Class<T> clazz);

    public <T extends Item> PartialList<T> getAllItems(Class<T> clazz, int offset, int size, String sortBy);

    public long getAllItemsCount(String itemType);

    public boolean save(Item item);

    public boolean update(final String itemId, final Date dateHint, final Class clazz, final String propertyName, final Object propertyValue);

    public <T extends Item> T load(String itemId, Class<T> clazz);

    public <T extends Item> T load(String itemId, Date dateHint, Class<T> clazz);

    public <T extends Item> boolean remove(String itemId, Class<T> clazz);

    public boolean saveQuery(String queryName, Condition query);

    public boolean removeQuery(String queryName);

    public Map<String, Map<String, Object>> getMapping(String itemType);

    public List<String> getMatchingSavedQueries(Item item);

    public boolean testMatch(Condition query, Item item);

    public <T extends Item> List<T> query(String fieldName, String fieldValue, String sortBy, Class<T> clazz);

    public <T extends Item> List<T> query(String fieldName, String[] fieldValues, String sortBy, Class<T> clazz);

    public <T extends Item> PartialList<T> query(String fieldName, String fieldValue, String sortBy, Class<T> clazz, int offset, int size);

    public <T extends Item> List<T> query(Condition query, String sortBy, Class<T> clazz);

    public <T extends Item> PartialList<T> query(Condition query, String sortBy, Class<T> clazz, int offset, int size);

    public long queryCount(Condition query, String itemType);

    public Map<String, Long> aggregateQuery(Condition filter, BaseAggregate aggregate, String itemType);

    public void purge(Date date);
}
