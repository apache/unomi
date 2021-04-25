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

package org.apache.unomi.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A context server tracked entity. All tracked entities need to extend this class so as to provide the minimal information the context server needs to be able to track such
 * entities and operate on them. Items are persisted according to their type (structure) and identifier (identity). Of note, all Item subclasses <strong>must</strong> define a
 * public String constant named {@code ITEM_TYPE} that is used to identify the type of a specific Item via {@link #getItemType}. It is therefore important that
 * {@code ITEM_TYPE} be unique across all persisted type of Items. Similarly, since Items are persisted according to their type, an Item's identifier must be unique among
 * Items of the same type.
 * <p>
 * Additionally, Items are also gathered by scope allowing the context server to group together related Items (usually pertaining to a given site being analyzed,
 * though scopes could span across sites depending on the desired analysis granularity). Scopes allow clients accessing the context server to filter data. The context server
 * defines a built-in scope ({@link Metadata#SYSTEM_SCOPE}) that clients can use to share data across scopes.
 */
public abstract class Item implements Serializable {
    private static final Logger logger = LoggerFactory.getLogger(Item.class.getName());

    private static final long serialVersionUID = 7446061538573517071L;

    private static final Map<Class,String> itemTypeCache = new ConcurrentHashMap<>();

    public static String getItemType(Class clazz) {
        String itemType = itemTypeCache.get(clazz);
        if (itemType != null) {
            return itemType;
        }
        try {
            itemType = (String) clazz.getField("ITEM_TYPE").get(null);
            itemTypeCache.put(clazz, itemType);
            return itemType;
        } catch (NoSuchFieldException e) {
            logger.error("Class " + clazz.getName() + " doesn't define a publicly accessible ITEM_TYPE field", e);
        } catch (IllegalAccessException e) {
            logger.error("Error resolving itemType for class " + clazz.getName(), e);
        }
        return null;
    }

    protected String itemId;
    protected String itemType;
    protected String scope;
    protected Long version;

    protected Map<String, Object> systemMetadata = new HashMap<>();

    public Item() {
        this.itemType = getItemType(this.getClass());
        if (itemType == null) {
            logger.error("Item implementations must provide a public String constant named ITEM_TYPE to uniquely identify this Item for the persistence service.");
        }
    }

    public Item(String itemId) {
        this();
        this.itemId = itemId;
    }


    /**
     * Retrieves the Item's identifier used to uniquely identify this Item when persisted or when referred to. An Item's identifier must be unique among Items with the same type.
     *
     * @return a String representation of the identifier, no particular format is prescribed as long as it is guaranteed unique for this particular Item.
     */
    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    /**
     * Retrieves the Item's type used to assert metadata and structure common to Items of this type, notably for persistence purposes. The Item's type <strong>must</strong>
     * match the value defined by the implementation's {@code ITEM_TYPE} public constant.
     *
     * @return a String representation of this Item's type, must equal the {@code ITEM_TYPE} value
     */
    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    /**
     * Retrieves the Item's scope.
     *
     * @return the Item's scope name
     */
    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Item item = (Item) o;

        return !(itemId != null ? !itemId.equals(item.itemId) : item.itemId != null);

    }

    @Override
    public int hashCode() {
        return itemId != null ? itemId.hashCode() : 0;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Object getSystemMetadataMetadata(String key) {
        return systemMetadata.get(key);
    }

    public void setSystemMetadata(String key, Object value) {
        systemMetadata.put(key, value);
    }

    public Map<String, Object> getSystemMetadata() {
        return systemMetadata;
    }
}
