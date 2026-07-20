/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.unomi.persistence.spi;

import org.apache.unomi.api.CustomItem;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.persistence.spi.aggregate.BaseAggregate;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Persistence SPI for storing and querying Unomi context-server entities.
 */
public interface PersistenceService {

    /**
     * {@link Item#getSystemMetadata(String)} key holding this backend's sequence number for an
     * item, populated by every {@code PersistenceService} implementation after a successful
     * {@code load()}/{@code save()}. Paired with {@link #SYSTEM_METADATA_PRIMARY_TERM}, this
     * value changes on every successful write and can be treated as an opaque, monotonically
     * -changing fencing token — no application-level version counter is needed on top of it for
     * compare-and-set or distributed-locking use cases (see {@link #save(Item, Boolean, Boolean)}).
     * This is a persistence-implementation detail, not a generic {@link Item} concept, which is
     * why it lives here rather than on {@link Item} itself. Every current and planned
     * {@code PersistenceService} implementation (Elasticsearch, OpenSearch) uses this exact key
     * name, by convention, so it is declared once here rather than duplicated per implementation.
     */
    String SYSTEM_METADATA_SEQ_NO = "seq_no";

    /**
     * {@link Item#getSystemMetadata(String)} key holding this backend's primary term for an item.
     * Must always be supplied together with {@link #SYSTEM_METADATA_SEQ_NO} as the
     * compare-and-set precondition; the pair identifies a specific document generation.
     */
    String SYSTEM_METADATA_PRIMARY_TERM = "primary_term";

    /**
     * Returns the unique name of this persistence backend implementation.
     *
     * @return the persistence service name
     */
    String getName();

    /**
     * Loads all items of the given type.
     * <em>WARNING</em>: this method can be expensive; prefer the paged overload {@link #getAllItems(Class, int, int, String)}.
     *
     * @param <T> the item type
     * @param clazz the {@link Item} subclass to load
     * @return all known items of that type
     */
    <T extends Item> List<T> getAllItems(Class<T> clazz);

    /**
     * Loads a paged slice of all items of the given type.
     * <p>
     * Future API versions may replace these parameters with a {@link org.apache.unomi.api.query.Query} object.
     *
     * @param <T> the item type
     * @param clazz the {@link Item} subclass to load
     * @param offset zero-based index of the first result
     * @param size maximum number of results to return, or {@code -1} for all matches
     * @param sortBy optional comma-separated sort fields with optional {@code :asc} or {@code :desc}
     * @return a paged list of items
     */
    <T extends Item> PartialList<T> getAllItems(Class<T> clazz, int offset, int size, String sortBy);

    /**
     * Loads a paged slice of all items of the given type, optionally using a scroll query.
     * <p>
     * Future API versions may replace these parameters with a {@link org.apache.unomi.api.query.Query} object.
     *
     * @param <T> the item type
     * @param clazz the {@link Item} subclass to load
     * @param offset zero-based index of the first result
     * @param size maximum number of results to return, or {@code -1} for all matches
     * @param sortBy optional comma-separated sort fields with optional {@code :asc} or {@code :desc}
     * @param scrollTimeValidity scroll context lifetime, using Elasticsearch time-unit syntax
     * @return a paged list of items, with scroll metadata when scrolling is enabled
     */
    <T extends Item> PartialList<T> getAllItems(final Class<T> clazz, int offset, int size, String sortBy, String scrollTimeValidity);

    /**
     * Returns whether the item is consistent with the persistence backend schema.
     *
     * @param item the item to validate
     * @return {@code true} when the item is consistent
     */
    boolean isConsistent(Item item);

    /**
     * Persists the specified Item in the context server.
     *
     * @param item the item to persist
     * @return {@code true} if the item was properly persisted, {@code false} otherwise
     */
    boolean save(Item item);

    /**
     * Persists the specified Item in the context server.
     *
     * @param item        the item to persist
     * @param useBatching whether to use batching or not for saving the item. If activating there may be a delay between
     *                    the call to this method and the actual saving in the persistence backend.
     *
     * @return {@code true} if the item was properly persisted, {@code false} otherwise
     */
    boolean save(Item item, boolean useBatching);

    /**
     * Persists the specified Item in the context server.
     * <p>
     * When {@code alwaysOverwrite} is {@code false}, this becomes a compare-and-set (CAS) write:
     * the backend applies it only if the item's current {@link Item#SYSTEM_METADATA_SEQ_NO}/
     * {@link Item#SYSTEM_METADATA_PRIMARY_TERM} system metadata (typically populated by a prior
     * {@code load()} or {@code save()} on the same item) still match the backend's current state
     * for that document. A {@code true} return is itself authoritative proof the write applied —
     * callers do not need a follow-up read to double-check, since every implementation applies
     * this precondition atomically. On success, the item's system metadata is updated in place
     * with the new post-write seq_no/primary_term, which callers may treat as an opaque,
     * monotonically-changing fencing token (e.g. for distributed-locking use cases — see
     * {@code TaskLockManager#acquireLock}). A {@code false} return means the precondition did not
     * match (the caller's view was stale / lost a race); nothing was written, and the caller
     * should reload and retry rather than treat it as an application error.
     * <p>
     * When {@code alwaysOverwrite} is {@code true} (or {@code null}, which falls back to the
     * implementation's configured default), the write is unconditional and always succeeds
     * (barring backend errors), regardless of the item's system metadata.
     *
     * @param item            the item to persist
     * @param useBatching     whether to use batching or not for saving the item. If activating there may be a delay between
     *                        the call to this method and the actual saving in the persistence backend
     * @param alwaysOverwrite whether to overwrite a document even if we are holding an old item when saving
     * @return {@code true} if the item was properly persisted, {@code false} otherwise
     */
    boolean save(Item item, Boolean useBatching, Boolean alwaysOverwrite);

    /**
     * Updates the item of the specified class and identified by the specified identifier with new property values provided as name - value pairs in the specified Map.
     *
     * @param item   the item we want to update
     * @param clazz  the Item subclass of the item to update
     * @param source a Map with entries specifying as key the property name to update and as value its new value
     * @return {@code true} if the update was successful, {@code false} otherwise
     */
    default boolean update(Item item, Class<?> clazz, Map<?, ?> source) {
        return update(item, null, clazz, source);
    }

    /**
     * @deprecated use {@link #update(Item, Class, Map)}
     */
    @Deprecated
    boolean update(Item item, Date dateHint, Class<?> clazz, Map<?, ?> source);

    /**
     * Updates the item of the specified class and identified by the specified identifier with a new property value for the specified property name. Same as
     * {@code update(itemId, clazz, Collections.singletonMap(propertyName, propertyValue))}
     *
     * @param item          the item we want to update
     * @param clazz         the Item subclass of the item to update
     * @param propertyName  the name of the property to update
     * @param propertyValue the new value of the property
     * @return {@code true} if the update was successful, {@code false} otherwise
     */
    default boolean update(Item item, Class<?> clazz, String propertyName, Object propertyValue) {
        return update(item, null, clazz, propertyName, propertyValue);
    }

    /**
     * @deprecated use {@link #update(Item, Class, String, Object)}
     */
    @Deprecated
    boolean update(Item item, Date dateHint, Class<?> clazz, String propertyName, Object propertyValue);

    /**
     * Updates the item of the specified class and identified by the specified identifier with new property values provided as name - value pairs in the specified Map.
     *
     * @param item            the item we want to update
     * @param clazz           the Item subclass of the item to update
     * @param source          a Map with entries specifying as key the property name to update and as value its new value
     * @param alwaysOverwrite whether to overwrite a document even if we are holding an old item when saving
     * @return {@code true} if the update was successful, {@code false} otherwise
     */
    default boolean update(Item item, Class<?> clazz, Map<?, ?> source, final boolean alwaysOverwrite) {
        return update(item, null, clazz, source, alwaysOverwrite);
    }

    /**
     * @deprecated use {@link #update(Item, Class, Map, boolean)}
     */
    @Deprecated
    boolean update(Item item, Date dateHint, Class<?> clazz, Map<?, ?> source, final boolean alwaysOverwrite);

    /**
     * Updates Map of items of the specified class and identified by the specified identifier with a new property value for the specified property name. Same as
     * {@code update(itemId, clazz, Collections.singletonMap(propertyName, propertyValue))}
     *
     * @param items A map the consist of item (key) and properties to update (value)
     * @param clazz the Item subclass of the item to update
     * @return List of failed Items Ids, if all succesful then returns an empty list. if the whole operation failed then will return null
     */
    default List<String> update(Map<Item, Map> items, Class clazz) {
        return update(items, null, clazz);
    }

    /**
     * @deprecated use {@link #update(Map, Class)}
     */
    @Deprecated
    List<String> update(Map<Item, Map> items, Date dateHint, Class clazz);

    /**
     * Updates the item of the specified class and identified by the specified identifier with a new property value for the specified property name. Same as
     * {@code update(itemId, clazz, Collections.singletonMap(propertyName, propertyValue))}
     *
     * @param item         the item we want to update
     * @param clazz        the Item subclass of the item to update
     * @param script       inline script
     * @param scriptParams script params
     * @return {@code true} if the update was successful, {@code false} otherwise
     */
    default boolean updateWithScript(Item item, Class<?> clazz, String script, Map<String, Object> scriptParams) {
        return updateWithScript(item, null, clazz, script, scriptParams);
    }

    /**
     * @deprecated use {@link #updateWithScript(Item, Class, String, Map)}
     */
    @Deprecated
    boolean updateWithScript(Item item, Date dateHint, Class<?> clazz, String script, Map<String, Object> scriptParams);

    /**
     * Updates the items of the specified class by a query with a new property value for the specified property name
     * based on provided scripts and script parameters
     *
     * @param clazz        the Item subclass of the item to update
     * @param scripts      inline scripts array
     * @param scriptParams script params array
     * @param conditions   conditions array
     * @return {@code true} if the update was successful, {@code false} otherwise
     */
    default boolean updateWithQueryAndScript(Class<?> clazz, String[] scripts, Map<String, Object>[] scriptParams, Condition[] conditions) {
        return updateWithQueryAndScript(null, clazz, scripts, scriptParams, conditions);
    }

    /**
     * @deprecated use {@link #updateWithQueryAndScript(Class, String[], Map[], Condition[])}
     */
    @Deprecated
    boolean updateWithQueryAndScript(Date dateHint, Class<?> clazz, String[] scripts, Map<String, Object>[] scriptParams, Condition[] conditions);

    /**
     * Updates the items of the specified class by a query with a new property value for the specified property name
     * based on provided stored scripts and script parameters
     *
     * @param clazz        the Item subclass of the item to update
     * @param scripts      Stored scripts name
     * @param scriptParams script params array
     * @param conditions   conditions array
     * @return {@code true} if the update was successful, {@code false} otherwise
     */
    default boolean updateWithQueryAndStoredScript(Class<?> clazz, String[] scripts, Map<String, Object>[] scriptParams, Condition[] conditions) {
        return updateWithQueryAndStoredScript(null, clazz, scripts, scriptParams, conditions);
    }

    /**
     * Updates the items of the specified class by a query with a new property value for the specified property name
     * based on provided stored scripts and script parameters,
     * This one is able to perform an update on multiple types in a single run, be careful with your query as it will be performed on all of them.
     *
     * @param classes      classes of items to update, be careful all of them will be submitted to update for all scripts/conditions
     * @param scripts      Stored scripts name
     * @param scriptParams script params array
     * @param conditions   conditions array
     * @param waitForComplete if true, wait for the ES execution to be complete
     * @return {@code true} if the update was successful, {@code false} otherwise
     */
    boolean updateWithQueryAndStoredScript(Class<?>[] classes, String[] scripts, Map<String, Object>[] scriptParams, Condition[] conditions, boolean waitForComplete);

    /**
     * @deprecated use {@link #updateWithQueryAndStoredScript(Class, String[], Map[], Condition[])}
     */
    @Deprecated
    boolean updateWithQueryAndStoredScript(Date dateHint, Class<?> clazz, String[] scripts, Map<String, Object>[] scriptParams, Condition[] conditions);

    /**
     * Stores inline scripts in the persistence backend for later scripted updates.
     *
     * @param scripts inline scripts keyed by script ID
     * @return {@code true} when all scripts are stored successfully
     */
    boolean storeScripts(Map<String, String> scripts);

    /**
     * Loads an item by ID and type.
     *
     * @param <T> the item type
     * @param itemId the item identifier
     * @param clazz the {@link Item} subclass to load
     * @return the item, or {@code null} when it does not exist
     */
    <T extends Item> T load(String itemId, Class<T> clazz);

    /**
     * @deprecated use {@link #load(String, Class)}
     */
    @Deprecated
    <T extends Item> T load(String itemId, Date dateHint, Class<T> clazz);

    /**
     * Loads a custom item by ID and custom item type.
     *
     * @param itemId the custom item identifier
     * @param customItemType the custom item type identifier
     * @return the custom item, or {@code null} when it does not exist
     */
    default CustomItem loadCustomItem(String itemId, String customItemType) {
        return loadCustomItem(itemId, null, customItemType);
    }

    /**
     * @deprecated use {@link #loadCustomItem(String, String)}
     */
    @Deprecated
    CustomItem loadCustomItem(String itemId, Date dateHint, String customItemType);

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
     * Deletes a custom item by ID and custom item type.
     *
     * @param itemId the custom item identifier
     * @param customItemType the custom item type identifier
     * @return {@code true} when deletion succeeds
     */
    boolean removeCustomItem(String itemId, String customItemType);

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
     * Returns property mappings for the given item type, when supported by the backend.
     *
     * @param itemType the item type name
     * @return property name to mapping metadata
     */
    Map<String, Map<String, Object>> getPropertiesMapping(String itemType);

    /**
     * Returns the mapping metadata for one property on the given item type.
     *
     * @param property the property name, including nested dot notation
     * @param itemType the item type name
     * @return property mapping metadata
     */
    Map<String, Object> getPropertyMapping(String property, String itemType);

    /**
     * Creates or updates the persistence mapping for a property on the given item type.
     *
     * @param property the property type definition
     * @param itemType the item type name
     */
    void setPropertyMapping(PropertyType property, String itemType);

    /**
     * Creates an index mapping from the given source definition.
     *
     * @param type the item or index type
     * @param source the mapping source definition
     */
    void createMapping(String type, String source);

    /**
     * Checks whether the specified item satisfies the provided condition.
     * <p>
     * The method name may change in a future release.
     *
     * @param query the condition we're testing the specified item against
     * @param item  the item we're checking against the specified condition
     * @return {@code true} if the item satisfies the condition, {@code false} otherwise
     */
    boolean testMatch(Condition query, Item item);

    /**
     * Returns whether the condition can be compiled for the given item without error.
     *
     * @param condition the condition to validate
     * @param item the sample item used during validation
     * @return {@code true} when the condition is valid
     */
    boolean isValidCondition(Condition condition, Item item);

    /**
     * Same as {@code query(fieldName, fieldValue, sortBy, clazz, 0, -1).getList()}
     *
     * @param <T>        the type of the Item subclass we want to retrieve
     * @param fieldName  the name of the field which we want items to have the specified values
     * @param fieldValue the value the items to retrieve should have for the specified field
     * @param sortBy     an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering
     *                   elements according to the property order in the
     *                   String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally followed by
     *                   a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @param clazz      the {@link Item} subclass of the items we want to retrieve
     * @return a list of items matching the specified criteria
     * @see #query(Condition, String, Class, int, int)
     */
    <T extends Item> List<T> query(String fieldName, String fieldValue, String sortBy, Class<T> clazz);

    /**
     * Queries items whose field matches any of the given values.
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
     * Queries items whose field matches the given value.
     *
     * @param <T>        the type of the Item subclass we want to retrieve
     * @param fieldName  the name of the field which we want items to have the specified value
     * @param fieldValue the value the items to retrieve should have for the specified field
     * @param sortBy     an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering
     *                   elements according to the property order in the
     *                   String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally followed by
     *                   a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @param clazz      the {@link Item} subclass of the items we want to retrieve
     * @param offset     zero or a positive integer specifying the position of the first item in the total ordered collection of matching items
     * @param size       a positive integer specifying how many matching items should be retrieved or {@code -1} if all of them should be retrieved
     * @return a {@link PartialList} of items matching the specified criteria
     */
    <T extends Item> PartialList<T> query(String fieldName, String fieldValue, String sortBy, Class<T> clazz, int offset, int size);

    /**
     * Queries items by field value and full-text match, with paging and optional sorting.
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
     * Queries items that match a full-text search, with paging and optional sorting.
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
     * @param <T>    the type of the Item subclass we want to retrieve
     * @param query  the {@link Condition} the items must satisfy to be retrieved
     * @param sortBy an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering
     *               elements according to the property order in the
     *               String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally followed by
     *               a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @param clazz  the {@link Item} subclass of the items we want to retrieve
     * @return a {@link PartialList} of items matching the specified criteria
     * @see #query(Condition, String, Class, int, int)
     */
    <T extends Item> List<T> query(Condition query, String sortBy, Class<T> clazz);

    /**
     * Queries items that satisfy the condition, with paging and optional sorting.
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
     * Queries items that satisfy the condition, with paging and optional sorting.
     * are retrieved, starting with the {@code offset}-th one. If a scroll identifier and time validity are specified, they will be used to perform a scrolling query, meaning
     * that only partial results will be returned, but the scrolling can be continued.
     *
     * @param <T>                the type of the Item subclass we want to retrieve
     * @param query              the {@link Condition} the items must satisfy to be retrieved
     * @param sortBy             an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering
     *                           elements according to the property order in the
     *                           String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally followed by
     *                           a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @param clazz              the {@link Item} subclass of the items we want to retrieve
     * @param offset             zero or a positive integer specifying the position of the first item in the total ordered collection of matching items
     * @param size               a positive integer specifying how many matching items should be retrieved or {@code -1} if all of them should be retrieved. In the case of a scroll query
     *                           this will be used as the scrolling window size.
     * @param scrollTimeValidity the time the scrolling query should stay valid. This must contain a time unit value such as the ones supported by ElasticSearch, such as
     *                           the ones declared here : https://www.elastic.co/guide/en/elasticsearch/reference/current/common-options.html#time-units
     *
     * @return a {@link PartialList} of items matching the specified criteria, with an scroll identifier and the scroll validity used if a scroll query was requested.
     */
    <T extends Item> PartialList<T> query(Condition query, String sortBy, Class<T> clazz, int offset, int size, String scrollTimeValidity);

    /**
     * Continues the execution of a scroll query, to retrieve the next results. If there are no more results the scroll query is also cleared.
     *
     * @param clazz              the {@link Item} subclass of the items we want to retrieve
     * @param scrollIdentifier   a scroll identifier obtained by the execution of a first query and returned in the {@link PartialList} object
     * @param scrollTimeValidity a scroll time validity value for the scroll query to stay valid. This must contain a time unit value such as the ones supported by ElasticSearch, such as
     *                           the ones declared here : https://www.elastic.co/guide/en/elasticsearch/reference/current/common-options.html#time-units
     * @param <T>                the type of the Item subclass we want to retrieve
     * @return a {@link PartialList} of items matching the specified criteria, with an scroll identifier and the scroll validity used if a scroll query was requested. Note that if
     * there are no more results the list will be empty but not null.
     */
    <T extends Item> PartialList<T> continueScrollQuery(Class<T> clazz, String scrollIdentifier, String scrollTimeValidity);

    /**
     * Queries items that satisfy the condition, with paging and optional
     * {@code sortBy} String and paged: only {@code size} of them are retrieved, starting with the
     * {@code offset}-th one. If a scroll identifier and time validity are specified, they will be used to perform a
     * scrolling query, meaning that only partial results will be returned, but the scrolling can be continued.
     *
     * @param query              the {@link Condition} the items must satisfy to be retrieved
     * @param sortBy             an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering
     *                           elements according to the property order in the
     *                           String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally followed by
     *                           a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @param customItemType     the identifier of the custom item type we want to query
     * @param offset             zero or a positive integer specifying the position of the first item in the total ordered collection of matching items
     * @param size               a positive integer specifying how many matching items should be retrieved or {@code -1} if all of them should be retrieved. In the case of a scroll query
     *                           this will be used as the scrolling window size.
     * @param scrollTimeValidity the time the scrolling query should stay valid. This must contain a time unit value such as the ones supported by ElasticSearch, such as
     *                           the ones declared here : https://www.elastic.co/guide/en/elasticsearch/reference/current/common-options.html#time-units
     *
     * @return a {@link PartialList} of items matching the specified criteria, with an scroll identifier and the scroll validity used if a scroll query was requested.
     */
    PartialList<CustomItem> queryCustomItem(Condition query, String sortBy, String customItemType, int offset, int size, String scrollTimeValidity);

    /**
     * Continues the execution of a scroll query, to retrieve the next results. If there are no more results the scroll query is also cleared.
     *
     * @param customItemType     the identifier of the custom item type we want to continue querying
     * @param scrollIdentifier   a scroll identifier obtained by the execution of a first query and returned in the {@link PartialList} object
     * @param scrollTimeValidity a scroll time validity value for the scroll query to stay valid. This must contain a time unit value such as the ones supported by ElasticSearch, such as
     *                           the ones declared here : https://www.elastic.co/guide/en/elasticsearch/reference/current/common-options.html#time-units
     *
     * @return a {@link PartialList} of items matching the specified criteria, with an scroll identifier and the scroll validity used if a scroll query was requested. Note that if
     * there are no more results the list will be empty but not null.
     */
    PartialList<CustomItem> continueCustomItemScrollQuery(String customItemType, String scrollIdentifier, String scrollTimeValidity);

    /**
     * Queries items that satisfy the condition and a full-text filter.
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
     * Counts items of the given type that match the condition.
     *
     * @param query    the condition the items must satisfy
     * @param itemType the String representation of the item type we want to retrieve the count of, as defined by its class' {@code ITEM_TYPE} field
     * @return the number of items of the specified type
     * @see Item Item for a discussion of {@code ITEM_TYPE}
     */
    long queryCount(Condition query, String itemType);

    /**
     * Counts all items of the given type.
     *
     * @param itemType the String representation of the item type we want to retrieve the count of, as defined by its class' {@code ITEM_TYPE} field
     * @return the number of items of the specified type
     * @see Item Item for a discussion of {@code ITEM_TYPE}
     */
    long getAllItemsCount(String itemType);

    /**
     * Counts all items of the given type for the tenant.
     *
     * @param itemType the String representation of the item type we want to retrieve the count of, as defined by its class' {@code ITEM_TYPE} field
     * @param tenantId the ID of the tenant whose items should be counted
     * @return the number of items of the specified type for the given tenant
     * @see Item Item for a discussion of {@code ITEM_TYPE}
     */
    long getAllItemsCount(String itemType, String tenantId);

    /**
     * Aggregates item counts for the given type, optionally filtered by condition and
     * aggregated according to the specified {@link BaseAggregate}.
     * Also return the global count of document matching the {@code ITEM_TYPE}
     *
     * @param filter    the condition the items must match or {@code null} if no filtering is needed
     * @param aggregate an aggregate specifying how matching items must be bundled
     * @param itemType  the String representation of the item type we want to retrieve the count of, as defined by its class' {@code ITEM_TYPE} field
     * @return a Map associating aggregation dimension name as key and cardinality for that dimension as value
     * @deprecated As of 1.3.0-incubating, please use {@link #aggregateWithOptimizedQuery(Condition, BaseAggregate, String)} instead
     */
    @Deprecated
    Map<String, Long> aggregateQuery(Condition filter, BaseAggregate aggregate, String itemType);

    /**
     * Aggregates item counts for the given type, optionally filtered by condition and
     * aggregated according to the specified {@link BaseAggregate}.
     * This aggregate won't return the global count and should therefore be much faster than {@link #aggregateQuery(Condition, BaseAggregate, String)}
     *
     * @param filter    the condition the items must match or {@code null} if no filtering is needed
     * @param aggregate an aggregate specifying how matching items must be bundled
     * @param itemType  the String representation of the item type we want to retrieve the count of, as defined by its class' {@code ITEM_TYPE} field
     * @return a Map associating aggregation dimension name as key and cardinality for that dimension as value
     */
    Map<String, Long> aggregateWithOptimizedQuery(Condition filter, BaseAggregate aggregate, String itemType);

    /**
     * Aggregates item counts for the given type, optionally filtered by condition and
     * aggregated according to the specified {@link BaseAggregate}.
     *
     * @param filter    the condition the items must match or {@code null} if no filtering is needed
     * @param aggregate an aggregate specifying how matching items must be bundled
     * @param itemType  the String representation of the item type we want to retrieve the count of, as defined by its class' {@code ITEM_TYPE} field
     * @param size      size of returned buckets in the response
     * @return a Map associating aggregation dimension name as key and cardinality for that dimension as value
     */
    Map<String, Long> aggregateWithOptimizedQuery(Condition filter, BaseAggregate aggregate, String itemType, int size);

    /**
     * Refreshes persistence engine indices when required by the backend.
     */
    void refresh();

    /**
     * Refreshes the index for the item type represented by the given class.
     *
     * @param <T> an {@link Item} subclass
     * @param clazz the item class whose index should be refreshed
     */
    default <T extends Item> void refreshIndex(Class<T> clazz) {
        refreshIndex(clazz, null);
    }

    /**
     * @deprecated use {@link #refreshIndex(Class)}
     */
    @Deprecated
    <T extends Item> void refreshIndex(Class<T> clazz, Date dateHint);

    /**
     * @deprecated use {@link #purgeTimeBasedItems(int, Class)} instead
     */
    @Deprecated
    void purge(Date date);

    /**
     * Purges time based data in the context server up to the specified days number of existence.
     * (This only works for time based data stored in rolling over indices, it have no effect on other types)
     *
     * @param <T> the item type
     * @param existsNumberOfDays the number of days
     * @param clazz the item type to be purged
     */
    <T extends Item> void purgeTimeBasedItems(int existsNumberOfDays, Class<T> clazz);

    /**
     * Queries items whose ranged property falls within the given bounds, with paging and optional sorting.
     * and paged: only {@code size} of them are retrieved, starting with the {@code offset}-th one.
     * <p>
     * Both bounds are inclusive: items whose property value equals {@code from} or {@code to} are included in the results. Either bound may be {@code null} to leave that side
     * of the range unbounded.
     *
     * @param <T>       the type of the Item subclass we want to retrieve
     * @param fieldName the name of the range property we want items to retrieve to be included between the specified start and end points
     * @param from      the beginning (inclusive) of the range we want to consider, or {@code null} for no lower bound
     * @param to        the end (inclusive) of the range we want to consider, or {@code null} for no upper bound
     * @param sortBy    an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering
     *                  elements according to the property order in the String, considering each in turn and moving on to the next one in case of equality of all preceding ones.
     *                  Each property name is optionally followed by a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @param clazz     the {@link Item} subclass of the items we want to retrieve
     * @param offset    zero or a positive integer specifying the position of the first item in the total ordered collection of matching items
     * @param size      a positive integer specifying how many matching items should be retrieved or {@code -1} if all of them should be retrieved
     * @return a {@link PartialList} of items matching the specified criteria
     */
    <T extends Item> PartialList<T> rangeQuery(String fieldName, String from, String to, String sortBy, Class<T> clazz, int offset, int size);

    /**
     * Computes numeric metrics for a field on items that match the condition and type.
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
     * Creates an index with for the specified item type in the persistence engine.
     * <p>
     * These low-level index operations may be removed from the public API in a future release.
     *
     * @param itemType the item type
     * @return {@code true} if the operation was successful, {@code false} otherwise
     */
    boolean createIndex(final String itemType);

    /**
     * Removes the index for the specified item type.
     * <p>
     * These low-level index operations may be removed from the public API in a future release.
     *
     * @param itemType the item type
     * @return {@code true} if the operation was successful, {@code false} otherwise
     */
    boolean removeIndex(final String itemType);

    /**
     * Removes all data associated with the provided scope.
     *
     * @param scope the scope for which we want to remove data
     */
    void purge(final String scope);

    /**
     * Calculates the total storage size for a specific tenant.
     *
     * @param tenantId the ID of the tenant
     * @return the total storage size in bytes
     */
    long calculateStorageSize(String tenantId);

    /**
     * Returns the number of API calls recorded for the tenant.
     *
     * @param tenantId the ID of the tenant
     * @return the number of API calls
     */
    long getApiCallCount(String tenantId);

    /**
     * Migrates data from one tenant to another.
     *
     * @param sourceTenantId the source tenant ID
     * @param targetTenantId the target tenant ID
     * @param itemTypes the types of items to migrate
     * @return true if migration was successful, false otherwise
     */
    boolean migrateTenantData(String sourceTenantId, String targetTenantId, List<String> itemTypes);

}
