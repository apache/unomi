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
import org.oasis_open.contextserver.api.Profile;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.persistence.spi.aggregate.BaseAggregate;

import java.util.Date;
import java.util.List;
import java.util.Map;

public interface PersistenceService {

    public <T extends Item> List<T> getAllItems(Class<T> clazz);

    public <T extends Item> PartialList<T> getAllItems(Class<T> clazz, int offset, int size, String sortBy);

    public long getAllItemsCount(String itemType);

    public boolean save(Item item);

    public boolean update(String itemId, Date dateHint, Class clazz, Map source);

    public boolean update(String itemId, Date dateHint, Class clazz, String propertyName, Object propertyValue);

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

    public void refresh();

    public void purge(Date date);

    public <T extends Item> PartialList<T>  rangeQuery(String s, String from, String to, String sortBy, Class<T> clazz, int offset, int size);
}
