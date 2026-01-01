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

import org.apache.unomi.api.utils.YamlUtils;
import org.apache.unomi.api.utils.YamlUtils.YamlConvertible;
import org.apache.unomi.api.utils.YamlUtils.YamlMapBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.unomi.api.utils.YamlUtils.toYamlValue;

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
public abstract class Item implements Serializable, YamlConvertible {
    private static final Logger LOGGER = LoggerFactory.getLogger(Item.class.getName());

    private static final long serialVersionUID = 1217180125083162915L;

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
            LOGGER.error("Class {} doesn't define a publicly accessible ITEM_TYPE field", clazz.getName(), e);
        } catch (IllegalAccessException e) {
            LOGGER.error("Error resolving itemType for class {}", clazz.getName(), e);
        }
        return null;
    }

    protected String itemId;
    protected String itemType;
    protected String scope;
    protected Long version;
    protected Map<String, Object> systemMetadata = new HashMap<>();
    private String tenantId;

    // Audit metadata fields
    private String createdBy;
    private String lastModifiedBy;
    private Date creationDate;
    private Date lastModificationDate;
    private String sourceInstanceId;
    private Date lastSyncDate;

    public Item() {
        this.itemType = getItemType(this.getClass());
        if (itemType == null) {
            LOGGER.error("Item implementations must provide a public String constant named ITEM_TYPE to uniquely identify this Item for the persistence service.");
        }
        initializeAuditMetadata();
    }

    public Item(String itemId) {
        this();
        this.itemId = itemId;
    }

    private void initializeAuditMetadata() {
        this.creationDate = new Date();
        this.lastModificationDate = this.creationDate;
        this.version = 0L;
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

    public Object getSystemMetadata(String key) {
        return systemMetadata.get(key);
    }

    public void setSystemMetadata(String key, Object value) {
        systemMetadata.put(key, value);
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    // Audit metadata getters and setters
    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    public Date getLastModificationDate() {
        return lastModificationDate;
    }

    public void setLastModificationDate(Date lastModificationDate) {
        this.lastModificationDate = lastModificationDate;
    }

    public String getSourceInstanceId() {
        return sourceInstanceId;
    }

    public void setSourceInstanceId(String sourceInstanceId) {
        this.sourceInstanceId = sourceInstanceId;
    }

    public Date getLastSyncDate() {
        return lastSyncDate;
    }

    public void setLastSyncDate(Date lastSyncDate) {
        this.lastSyncDate = lastSyncDate;
    }

    /**
     * Converts this item to a Map structure for YAML output.
     * Implements YamlConvertible interface with circular reference detection.
     *
     * @param visited set of already visited objects to prevent infinite recursion (may be null)
     * @return a Map representation of this item
     */
    @Override
    public Map<String, Object> toYaml(Set<Object> visited, int maxDepth) {
        if (maxDepth <= 0) {
            return YamlMapBuilder.create()
                .put("itemId", itemId)
                .put("itemType", itemType)
                .put("systemMetadata", "<max depth exceeded>")
                .build();
        }
        final Set<Object> visitedSet = visited != null ? visited : new HashSet<>();
        // Check if already visited - if so, we're being called from a child class via super.toYaml()
        // OR it's a real circular reference. We can't distinguish, but since child classes
        // (like Rule, ConditionType, etc.) all check for circular refs before calling super,
        // if we're already visited here, it's safe to assume it's a super call, not a circular ref.
        // If Item is directly serialized and encounters itself, the check would happen at the
        // top level before nested processing, so this should be safe.
        boolean alreadyVisited = visitedSet.contains(this);
        if (!alreadyVisited) {
            // First time seeing this object - add it to track for circular references
            visitedSet.add(this);
        }
        try {
            return YamlMapBuilder.create()
                .put("itemId", itemId)  // Always include, even if null, to reflect actual state
                .put("itemType", itemType)  // Always include, even if null, to reflect actual state
                .putIfNotNull("scope", scope)
                .putIfNotNull("version", version)
                .putIfNotNull("systemMetadata", systemMetadata != null && !systemMetadata.isEmpty() ? toYamlValue(systemMetadata, visitedSet, maxDepth - 1) : null)
                .putIfNotNull("tenantId", tenantId)
                .putIfNotNull("createdBy", createdBy)
                .putIfNotNull("lastModifiedBy", lastModifiedBy)
                .putIfNotNull("creationDate", creationDate)
                .putIfNotNull("lastModificationDate", lastModificationDate)
                .putIfNotNull("sourceInstanceId", sourceInstanceId)
                .putIfNotNull("lastSyncDate", lastSyncDate)
                .build();
        } finally {
            // Only remove if we added it (i.e., if it wasn't already visited)
            if (!alreadyVisited) {
                visitedSet.remove(this);
            }
        }
    }

    @Override
    public String toString() {
        Map<String, Object> map = toYaml();
        return YamlUtils.format(map);
    }
}
