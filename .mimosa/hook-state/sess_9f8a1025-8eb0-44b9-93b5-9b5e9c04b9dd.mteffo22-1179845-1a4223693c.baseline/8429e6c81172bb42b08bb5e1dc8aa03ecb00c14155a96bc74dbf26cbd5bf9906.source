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

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.apache.unomi.api.utils.YamlUtils.circularRef;

/**
 * Common descriptive fields shared by Unomi configuration items.
 * Includes id, name, description, scope, tags, and related attributes that
 * appear on segments, rules, property types, and other managed entities.
 *
 * @see MetadataItem
 */
public class Metadata implements Comparable<Metadata>, Serializable, YamlConvertible {

    private static final long serialVersionUID = 7446061538573517071L;

    /**
     * Default scope, gathers default entities and can also be used to share entities across scopes.
     */
    public static final String SYSTEM_SCOPE = "systemscope";
    /**
     * Stable identifier of this metadata item (often equals itemId of the owning entity).
     * @api.example vip
     */
    private String id;
    /**
     * Display name.
     * @api.example VIP customers
     */
    private String name;
    /**
     * Human-readable description.
     * @api.example Customers with high engagement score
     */
    private String description;
    /**
     * Scope that owns this metadata.
     * @api.example mysite
     */
    private String scope;
    /**
     * User-visible tags.
     * @api.example ["marketing"]
     */
    private Set<String> tags = new LinkedHashSet<>();
    /**
     * System tags used by Unomi internals and plugins.
     * @api.example ["profileProperties"]
     */
    private Set<String> systemTags = new LinkedHashSet<>();
    /**
     * Whether this item is enabled.
     * @api.example true
     */
    private boolean enabled = true;
    /**
     * {@code true} when required plugins are not installed.
     * @api.example false
     */
    private boolean missingPlugins = false;
    /**
     * Whether this item is hidden from default UIs.
     * @api.example false
     */
    private boolean hidden = false;
    /**
     * Whether this item is read-only.
     * @api.example false
     */
    private boolean readOnly = false;

    /**
     * Default constructor.
     */
    public Metadata() {
    }

    /**
     * Creates metadata with the given identifier.
     *
     * @param id the metadata identifier
     */
    public Metadata(String id) {
        this.id = id;
    }

    /**
     * Creates metadata with scope, identifier, name, and description.
     *
     * @param scope       the item scope
     * @param id          the item identifier
     * @param name        the display name
     * @param description the human-readable description
     */
    public Metadata(String scope, String id, String name, String description) {
        this.scope = scope;
        this.id = id;
        this.name = name;
        this.description = description;
    }

    /**
     * Identifier of the item described by this metadata.
     *
     * @return the item id
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the item identifier.
     *
     * @param id the item id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Display name shown in administrative UIs.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the display name.
     *
     * @param name the name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Human-readable description of the item.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description.
     *
     * @param description the description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Scope that owns this item.
     *
     * @return the scope id
     * @see Item Item for a deeper discussion of scopes
     */
    public String getScope() {
        return scope;
    }

    /**
     * Sets the item scope.
     *
     * @param scope the scope id
     */
    public void setScope(String scope) {
        this.scope = scope;
    }

    /**
     * User-defined tags for organizing and filtering items.
     *
     * @return the tag names
     */
    public Set<String> getTags() {
        return tags;
    }

    /**
     * Sets the user-defined tags.
     *
     * @param tags the tag names
     */
    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    /**
     * System tags applied by the platform (not editable in UIs).
     *
     * @return the system tag names
     */
    public Set<String> getSystemTags() {
        return systemTags;
    }

    /**
     * Sets the system tags.
     *
     * @param systemTags the system tag names
     */
    public void setSystemTags(Set<String> systemTags) {
        this.systemTags = systemTags;
    }

    /**
     * Whether the associated item is active and eligible for rule evaluation.
     *
     * @return {@code true} if enabled, {@code false} otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether the associated item is active.
     *
     * @param enabled {@code true} to enable the item, {@code false} to disable it
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Whether required OSGi plugins are missing and the item cannot run yet.
     *
     * @return {@code true} if plugins are missing, {@code false} otherwise
     */
    public boolean isMissingPlugins() {
        return missingPlugins;
    }

    /**
     * Sets whether required plugins are missing.
     *
     * @param missingPlugins {@code true} if plugins are missing, {@code false} otherwise
     */
    public void setMissingPlugins(boolean missingPlugins) {
        this.missingPlugins = missingPlugins;
    }

    /**
     * Whether the item should be hidden from administrative UIs.
     *
     * @return {@code true} if hidden, {@code false} otherwise
     */
    public boolean isHidden() {
        return hidden;
    }

    /**
     * Sets whether the item is hidden from UIs.
     *
     * @param hidden {@code true} to hide the item, {@code false} to show it
     */
    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    /**
     * Whether the item can be read but not modified.
     *
     * @return {@code true} if read-only, {@code false} otherwise
     */
    public boolean isReadOnly() {
        return readOnly;
    }

    /**
     * Sets whether the item is read-only.
     *
     * @param readOnly {@code true} for read-only access, {@code false} to allow updates
     */
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    /**
     * Compares metadata by item identifier.
     *
     * @param o the other metadata
     * @return a negative, zero, or positive value depending on id ordering
     */
    public int compareTo(Metadata o) {
        return getId().compareTo(o.getId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Metadata metadata = (Metadata) o;

        if (!id.equals(metadata.id)) return false;
        return !(scope != null ? !scope.equals(metadata.scope) : metadata.scope != null);

    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + (scope != null ? scope.hashCode() : 0);
        return result;
    }

    /**
     * Converts this metadata to a Map structure for YAML output.
     * Implements YamlConvertible interface with circular reference detection.
     *
     * @param visited set of already visited objects to prevent infinite recursion (may be null)
     * @return a Map representation of this metadata
     */
    @Override
    public Map<String, Object> toYaml(Set<Object> visited, int maxDepth) {
        if (visited != null && visited.contains(this)) {
            return circularRef();
        }
        final Set<Object> visitedSet = visited != null ? visited : YamlUtils.newIdentityVisitedSet();
        visitedSet.add(this);
        try {
            return YamlMapBuilder.create()
                .putIfNotNull("id", id)
                .putIfNotNull("name", name)
                .putIfNotNull("description", description)
                .putIfNotNull("scope", scope)
                .putIfNotEmpty("tags", tags)
                .putIfNotEmpty("systemTags", systemTags)
                .putIf("enabled", true, enabled)
                .putIf("missingPlugins", true, missingPlugins)
                .putIf("hidden", true, hidden)
                .putIf("readOnly", true, readOnly)
                .build();
        } finally {
            visitedSet.remove(this);
        }
    }

    @Override
    public String toString() {
        Map<String, Object> map = toYaml();
        return YamlUtils.format(map);
    }
}
