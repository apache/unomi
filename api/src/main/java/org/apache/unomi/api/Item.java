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

    /**
     * Java serialization version; Unomi does not rely on Java serialization of this type as a cross-version persistence contract.
     */
    private static final long serialVersionUID = 1217180125083162915L;

    private static final Map<Class,String> itemTypeCache = new ConcurrentHashMap<>();

    /**
     * Resolves the item type string from a class's {@code ITEM_TYPE} constant.
     * Results are cached per class.
     *
     * @param clazz item class
     * @return item type, or {@code null} if {@code ITEM_TYPE} is missing or inaccessible
     */
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

    /**
     * Unique id used when this item is persisted or referenced.
     * Must be unique among items of the same {@link #itemType}.
     */
    protected String itemId;
    /**
     * Persistence type string for this item.
     * Subclasses must define a public {@code ITEM_TYPE} constant with this value.
     */
    protected String itemType;
    /**
     * Scope that groups related items (often one analyzed site).
     * Used by clients to filter data returned from the context server.
     */
    protected String scope;
    /**
     * Optimistic-locking version, incremented when the item is updated.
     */
    protected Long version;
    /**
     * Server-managed metadata keyed by string.
     * Stores values that are not part of the item's core properties.
     */
    protected Map<String, Object> systemMetadata = new HashMap<>();
    private String tenantId;

    // Audit metadata fields
    private String createdBy;
    private String lastModifiedBy;
    private Date creationDate;
    private Date lastModificationDate;
    private String sourceInstanceId;
    private Date lastSyncDate;

    /**
     * Initializes {@link #itemType} from the subclass {@code ITEM_TYPE} constant
     * and sets default audit metadata ({@link #creationDate}, {@link #version}).
     * Logs an error when {@code ITEM_TYPE} is missing on the concrete class.
     */
    public Item() {
        this.itemType = getItemType(this.getClass());
        if (itemType == null) {
            LOGGER.error("Item implementations must provide a public String constant named ITEM_TYPE to uniquely identify this Item for the persistence service.");
        }
        initializeAuditMetadata();
    }

    /**
     * Creates an item with the given id.
     *
     * @param itemId item id
     */
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
     * Unique id among items of the same type.
     * No particular format is required as long as the id is unique for this item type.
     *
     * @return item id
     */
    public String getItemId() {
        return itemId;
    }

    /**
     * Sets the item id.
     *
     * @param itemId item id
     */
    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    /**
     * Item type used for persistence and metadata.
     * Must match the implementing class {@code ITEM_TYPE} constant.
     *
     * @return item type
     */
    public String getItemType() {
        return itemType;
    }

    /**
     * Sets the item type.
     *
     * @param itemType item type
     */
    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    /**
     * Scope that groups related items.
     *
     * @return scope name
     */
    public String getScope() {
        return scope;
    }

    /**
     * Sets the scope.
     *
     * @param scope scope name
     */
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

    /**
     * Optimistic-locking version.
     *
     * @return item version
     */
    public Long getVersion() {
        return version;
    }

    /**
     * Sets the item version.
     *
     * @param version item version
     */
    public void setVersion(Long version) {
        this.version = version;
    }

    /**
     * Returns system metadata for the given key.
     *
     * @param key metadata key
     * @return metadata value
     */
    public Object getSystemMetadata(String key) {
        return systemMetadata.get(key);
    }

    /**
     * Sets system metadata for the given key.
     *
     * @param key metadata key
     * @param value metadata value
     */
    public void setSystemMetadata(String key, Object value) {
        systemMetadata.put(key, value);
    }

    /**
     * Tenant that owns this item.
     *
     * @return tenant id
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Sets the tenant id.
     *
     * @param tenantId tenant id
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * User or system that created this item.
     *
     * @return creator id
     */
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     * Sets the creator id.
     *
     * @param createdBy creator id
     */
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * User or system that last modified this item.
     *
     * @return last modifier id
     */
    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    /**
     * Sets the last modifier id.
     *
     * @param lastModifiedBy last modifier id
     */
    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    /**
     * When this item was created.
     *
     * @return creation date
     */
    public Date getCreationDate() {
        return creationDate;
    }

    /**
     * Sets the creation date.
     *
     * @param creationDate creation date
     */
    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    /**
     * When this item was last modified.
     *
     * @return last modification date
     */
    public Date getLastModificationDate() {
        return lastModificationDate;
    }

    /**
     * Sets the last modification date.
     *
     * @param lastModificationDate last modification date
     */
    public void setLastModificationDate(Date lastModificationDate) {
        this.lastModificationDate = lastModificationDate;
    }

    /**
     * Cluster node that originated this item.
     *
     * @return source instance id
     */
    public String getSourceInstanceId() {
        return sourceInstanceId;
    }

    /**
     * Sets the source instance id.
     *
     * @param sourceInstanceId source instance id
     */
    public void setSourceInstanceId(String sourceInstanceId) {
        this.sourceInstanceId = sourceInstanceId;
    }

    /**
     * When this item was last synchronized from another node.
     *
     * @return last sync date
     */
    public Date getLastSyncDate() {
        return lastSyncDate;
    }

    /**
     * Sets the last sync date.
     *
     * @param lastSyncDate last sync date
     */
    public void setLastSyncDate(Date lastSyncDate) {
        this.lastSyncDate = lastSyncDate;
    }

    /**
     * Converts this item to a map for YAML output.
     *
     * @param visited objects already visited while converting (may be {@code null})
     * @param maxDepth remaining recursion depth
     * @return map representation of this item
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
        final Set<Object> visitedSet = visited != null ? visited : YamlUtils.newIdentityVisitedSet();
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
