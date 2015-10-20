package org.oasis_open.contextserver.persistence.spi;

/*
 * #%L
 * context-server-persistence-spi
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.oasis_open.contextserver.api.Item;
import org.oasis_open.contextserver.api.PartialList;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.persistence.spi.aggregate.BaseAggregate;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * A service to provide persistence and retrieval of context server entities.
 */
public interface PersistenceService {

    /**
     * Retrieves all known items of the specified class.
     * <em>WARNING</em>: this method can be quite computationally intensive and calling the paged version {@link #getAllItems(Class, int, int, String)} is preferred.
     *
     * @param <T>   the type of the {@link Item}s we want to retrieve
     * @param clazz the {@link Item} subclass of entities we want to retrieve
     * @return a list of all known items with the given type
     */
    <T extends Item> List<T> getAllItems(Class<T> clazz);

    /**
     * Retrieves all known items of the specified class, ordered according to the specified {@code sortBy} String and and paged: only {@code size} of them are retrieved,
     * starting with the {@code offset}-th one.
     *
     * TODO: use a Query object instead of distinct parameters?
     *
     * @param <T>    the type of the {@link Item}s we want to retrieve
     * @param clazz  the {@link Item} subclass of entities we want to retrieve
     * @param offset zero or a positive integer specifying the position of the first item in the total ordered collection of matching items
     * @param size   a positive integer specifying how many matching items should be retrieved or {@code -1} if all of them should be retrieved
     * @param sortBy an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering
     *               elements according to the property order in the
     *               String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally followed by
     *               a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @return a {@link PartialList} of pages items with the given type
     */
    <T extends Item> PartialList<T> getAllItems(Class<T> clazz, int offset, int size, String sortBy);

    /**
     * Persists the specified Item in the context server.
     *
     * @param item the item to persist
     * @return {@code true} if the item was properly persisted, {@code false} otherwise
     */
    boolean save(Item item);

    /**
     * Updates the item of the specified class and identified by the specified identifier with new property values provided as name - value pairs in the specified Map.
     *
     * @param itemId   the identifier of the item we want to update
     * @param dateHint a Date helping in identifying where the item is located
     * @param clazz    the Item subclass of the item to update
     * @param source   a Map with entries specifying as key the property name to update and as value its new value
     * @return {@code true} if the update was successful, {@code false} otherwise
     */
    boolean update(String itemId, Date dateHint, Class<?> clazz, Map<?, ?> source);

    /**
     * Updates the item of the specified class and identified by the specified identifier with a new property value for the specified property name. Same as
     * {@code update(itemId, dateHint, clazz, Collections.singletonMap(propertyName, propertyValue))}
     *
     * @param itemId        the identifier of the item we want to update
     * @param dateHint      a Date helping in identifying where the item is located
     * @param clazz         the Item subclass of the item to update
     * @param propertyName  the name of the property to update
     * @param propertyValue the new value of the property
     * @return {@code true} if the update was successful, {@code false} otherwise
     */
    boolean update(String itemId, Date dateHint, Class<?> clazz, String propertyName, Object propertyValue);

    /**
     * Retrieves the item identified with the specified identifier and with the specified Item subclass if it exists.
     *
     * @param <T>    the type of the Item subclass we want to retrieve
     * @param itemId the identifier of the item we want to retrieve
     * @param clazz  the {@link Item} subclass of the item we want to retrieve
     * @return the item identified with the specified identifier and with the specified Item subclass if it exists, {@code null} otherwise
     */
    <T extends Item> T load(String itemId, Class<T> clazz);

    /**
     * Retrieves the item identified with the specified identifier and with the specified Item subclass if it exists.
     *
     * @param <T>      the type of the Item subclass we want to retrieve
     * @param itemId   the identifier of the item we want to retrieve
     * @param dateHint a Date helping in identifying where the item is located
     * @param clazz    the {@link Item} subclass of the item we want to retrieve
     * @return the item identified with the specified identifier and with the specified Item subclass if it exists, {@code null} otherwise
     */
    <T extends Item> T load(String itemId, Date dateHint, Class<T> clazz);

    /**
     * Deletes the item identified with the specified identifier and with the specified Item subclass if it exists.
     *
     * @param <T>    the type of the Item subclass we want to delete
     * @param itemId the identifier of the item we want to delete
     * @param clazz  the {@link Item} subclass of the item we want to delete
     * @return {@code true} if the deletion was successful, {@code false} otherwise
     */
    <T extends Item> boolean remove(String itemId, Class<T> clazz);

    /**
     * Deletes items with the specified Item subclass matching the specified {@link Condition}.
     *
     * @param <T>   the type of the Item subclass we want to delete
     * @param query a {@link Condition} identifying which elements we want to delete
     * @param clazz the {@link Item} subclass of the items we want to delete
     * @return {@code true} if the deletion was successful, {@code false} otherwise
     */
    <T extends Item> boolean removeByQuery(Condition query, Class<T> clazz);

    /**
     * Persists the specified query under the specified name.
     *
     * @param queryName the name under which the specified query should be recorded
     * @param query     the query to be recorded
     * @return {@code true} if the query was properly saved, {@code false} otherwise
     */
    boolean saveQuery(String queryName, Condition query);

    /**
     * Deletes the query identified by the specified name.
     *
     * @param queryName the name under which the specified query was recorded
     * @return {@code true} if the deletion was successful, {@code false} otherwise
     */
    boolean removeQuery(String queryName);

    /**
     * TODO
     *
     * @param itemType
     * @return
     */
    Map<String, Map<String, Object>> getMapping(String itemType);

    /**
     * TODO
     *
     * @param item
     * @return
     */
    List<String> getMatchingSavedQueries(Item item);

    /**
     * Checks whether the specified item satisfies the provided condition.
     *
     * TODO: rename to isMatching?
     *
     * @param query the condition we're testing the specified item against
     * @param item  the item we're checking against the specified condition
     * @return {@code true} if the item satisfies the condition, {@code false} otherwise
     */
    boolean testMatch(Condition query, Item item);

    /**
     * Same as {@code query(fieldName, fieldValue, sortBy, clazz, 0, -1).getList()}
     *
     * @see #query(Condition, String, Class, int, int)
     */
    <T extends Item> List<T> query(String fieldName, String fieldValue, String sortBy, Class<T> clazz);

    /**
     * Retrieves a list of items with the specified field having the specified values.
     *
     * @param <T>         the type of the Item subclass we want to retrieve
     * @param fieldName   the name of the field which we want items to have the specified values
     * @param fieldValues the values the items to retrieve should have for the specified field
     * @param sortBy      an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering
     *                    elements according to the property order in the
     *                    String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally followed by
     *                    a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @param clazz       the {@link Item} subclass of the items we want to retrieve
     * @return a list of items matching the specified criteria
     */
    <T extends Item> List<T> query(String fieldName, String[] fieldValues, String sortBy, Class<T> clazz);

    /**
     * Retrieves a list of items with the specified field having the specified value.
     *
     * @param <T>        the type of the Item subclass we want to retrieve
     * @param fieldName  the name of the field which we want items to have the specified value
     * @param fieldValue the value the items to retrieve should have for the specified field
     * @param sortBy     an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering
     *                   elements according to the property order in the
     *                   String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally followed by
     *                   a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @param clazz      the {@link Item} subclass of the items we want to retrieve
     * @return a {@link PartialList} of items matching the specified criteria
     */
    <T extends Item> PartialList<T> query(String fieldName, String fieldValue, String sortBy, Class<T> clazz, int offset, int size);

    /**
     * Retrieves a list of items with the specified field having the specified value and having at least a field with the specified full text value in it, ordered according to the
     * specified {@code sortBy} String and and paged: only {@code size} of them are retrieved, starting with the {@code offset}-th one.
     *
     * @param <T>        the type of the Item subclass we want to retrieve
     * @param fieldName  the name of the field which we want items to have the specified value
     * @param fieldValue the value the items to retrieve should have for the specified field
     * @param fulltext   the text that the item must have in one of its fields to be considered a match
     * @param sortBy     an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering
     *                   elements according to the property order in the
     *                   String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally followed by
     *                   a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @param clazz      the {@link Item} subclass of the items we want to retrieve
     * @param offset     zero or a positive integer specifying the position of the first item in the total ordered collection of matching items
     * @param size       a positive integer specifying how many matching items should be retrieved or {@code -1} if all of them should be retrieved
     * @return a {@link PartialList} of items matching the specified criteria
     */
    <T extends Item> PartialList<T> queryFullText(String fieldName, String fieldValue, String fulltext, String sortBy, Class<T> clazz, int offset, int size);

    /**
     * Retrieves a list of items having at least a field with the specified full text value in it, ordered according to the specified {@code sortBy} String and and paged: only
     * {@code size} of them are retrieved, starting with the {@code offset}-th one.
     *
     * @param <T>      the type of the Item subclass we want to retrieve
     * @param fulltext the text that the item must have in one of its fields to be considered a match
     * @param sortBy   an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering
     *                 elements according to the property order in the
     *                 String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally followed by
     *                 a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @param clazz    the {@link Item} subclass of the items we want to retrieve
     * @param offset   zero or a positive integer specifying the position of the first item in the total ordered collection of matching items
     * @param size     a positive integer specifying how many matching items should be retrieved or {@code -1} if all of them should be retrieved
     * @return a {@link PartialList} of items matching the specified criteria
     */
    <T extends Item> PartialList<T> queryFullText(String fulltext, String sortBy, Class<T> clazz, int offset, int size);

    /**
     * Same as {@code query(query, sortBy, clazz, 0, -1).getList()}
     *
     * @see #query(Condition, String, Class, int, int)
     */
    <T extends Item> List<T> query(Condition query, String sortBy, Class<T> clazz);

    /**
     * Retrieves a list of items satisfying the specified {@link Condition}, ordered according to the specified {@code sortBy} String and and paged: only {@code size} of them
     * are retrieved, starting with the {@code offset}-th one.
     *
     * @param <T>    the type of the Item subclass we want to retrieve
     * @param query  the {@link Condition} the items must satisfy to be retrieved
     * @param sortBy an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering
     *               elements according to the property order in the
     *               String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally followed by
     *               a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @param clazz  the {@link Item} subclass of the items we want to retrieve
     * @param offset zero or a positive integer specifying the position of the first item in the total ordered collection of matching items
     * @param size   a positive integer specifying how many matching items should be retrieved or {@code -1} if all of them should be retrieved
     * @return a {@link PartialList} of items matching the specified criteria
     */
    <T extends Item> PartialList<T> query(Condition query, String sortBy, Class<T> clazz, int offset, int size);

    /**
     * Retrieves the same items as {@code query(query, sortBy, clazz, 0, -1)} with the added constraints that the matching elements must also have at least a field matching the
     * specified full text query.
     *
     * @param <T>      the type of the Item subclass we want to retrieve
     * @param fulltext the text that the item must have in one of its fields to be considered a match
     * @param query    the {@link Condition} the items must satisfy to be retrieved
     * @param sortBy   an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering
     *                 elements according to the property order in the
     *                 String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally followed by
     *                 a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @param clazz    the {@link Item} subclass of the items we want to retrieve
     * @param offset   zero or a positive integer specifying the position of the first item in the total ordered collection of matching items
     * @param size     a positive integer specifying how many matching items should be retrieved or {@code -1} if all of them should be retrieved
     * @return a {@link PartialList} of items matching the specified criteria
     */
    <T extends Item> PartialList<T> queryFullText(String fulltext, Condition query, String sortBy, Class<T> clazz, int offset, int size);

    /**
     * Retrieves the number of items of the specified type as defined by the Item subclass public field {@code ITEM_TYPE} and matching the specified {@link Condition}.
     *
     * @param query    the condition the items must satisfy
     * @param itemType the String representation of the item type we want to retrieve the count of, as defined by its class' {@code ITEM_TYPE} field
     * @return the number of items of the specified type
     * @see Item Item for a discussion of {@code ITEM_TYPE}
     */
    long queryCount(Condition query, String itemType);

    /**
     * Retrieves the number of items with the specified type as defined by the Item subclass public field {@code ITEM_TYPE}.
     *
     * @param itemType the String representation of the item type we want to retrieve the count of, as defined by its class' {@code ITEM_TYPE} field
     * @return the number of items of the specified type
     * @see Item Item for a discussion of {@code ITEM_TYPE}
     */
    long getAllItemsCount(String itemType);

    /**
     * Retrieves the number of items with the specified type as defined by the Item subclass public field {@code ITEM_TYPE} matching the optional specified condition and
     * aggregated according to the specified {@link BaseAggregate}.
     *
     * @param filter    the condition the items must match or {@code null} if no filtering is needed
     * @param aggregate an aggregate specifying how matching items must be bundled
     * @param itemType  the String representation of the item type we want to retrieve the count of, as defined by its class' {@code ITEM_TYPE} field
     * @return a Map associating aggregation dimension name as key and cardinality for that dimension as value
     */
    Map<String, Long> aggregateQuery(Condition filter, BaseAggregate aggregate, String itemType);

    /**
     * Updates the persistence's engine indices if needed.
     */
    void refresh();

    /**
     * Purges all data in the context server up to the specified date, not included.
     *
     * @param date the date (not included) before which we want to erase all data
     */
    void purge(Date date);

    /**
     * Retrieves all items of the specified Item subclass which specified ranged property is within the specified bounds, ordered according to the specified {@code sortBy} String
     * and and paged: only {@code size} of them are retrieved, starting with the {@code offset}-th one.
     *
     * @param <T>    the type of the Item subclass we want to retrieve
     * @param s      the name of the range property we want items to retrieve to be included between the specified start and end points
     * @param from   the beginning of the range we want to consider
     * @param to     the end of the range we want to consider
     * @param sortBy an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering
     *               elements according to the property order in the String, considering each in turn and moving on to the next one in case of equality of all preceding ones.
     *               Each property name is optionally followed by a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @param clazz  the {@link Item} subclass of the items we want to retrieve
     * @param offset zero or a positive integer specifying the position of the first item in the total ordered collection of matching items
     * @param size   a positive integer specifying how many matching items should be retrieved or {@code -1} if all of them should be retrieved
     * @return a {@link PartialList} of items matching the specified criteria
     */
    <T extends Item> PartialList<T> rangeQuery(String s, String from, String to, String sortBy, Class<T> clazz, int offset, int size);

    /**
     * Retrieves the specified metrics for the specified field of items of the specified type as defined by the Item subclass public field {@code ITEM_TYPE} and matching the
     * specified {@link Condition}.
     *
     * @param condition the condition the items must satisfy
     * @param metrics   a String array which metrics should be computed (possible values: {@code sum} for the sum of the values,  {@code avg} for the average of the values, {@code
     *                  min} for the minimum value and {@code max} for the maximum value)
     * @param field     the name of the field for which the metrics should be computed
     * @param type      the String representation of the item type we want to retrieve the count of, as defined by its class' {@code ITEM_TYPE} field
     * @return a Map associating computed metric name as key to its associated value
     */
    Map<String, Double> getSingleValuesMetrics(Condition condition, String[] metrics, String field, String type);

    /**
     * Creates an index with the specified name in the persistence engine.
     *
     * TODO: remove from API?
     *
     * @param indexName the index name
     * @return {@code true} if the operation was successful, {@code false} otherwise
     */
    boolean createIndex(final String indexName);

    /**
     * Removes the index with the specified name.
     *
     * TODO: remove from API?
     *
     * @param indexName the index name
     * @return {@code true} if the operation was successful, {@code false} otherwise
     */
    boolean removeIndex(final String indexName);
}