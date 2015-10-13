package org.oasis_open.contextserver.api;

/*
 * #%L
 * context-server-api
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

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A class providing information about context server entities.
 *
 * @see MetadataItem
 */
public class Metadata implements Comparable<Metadata> {

    /**
     * Default scope, gathers default entities and can also be used to share entities across scopes.
     */
    public static final String SYSTEM_SCOPE = "systemscope";
    private String id;
    private String name;
    private String description;
    private String scope;
    private Set<String> tags = new LinkedHashSet<>();
    private boolean enabled = true;
    private boolean missingPlugins = false;
    private boolean hidden = false;
    private boolean readOnly = false;

    public Metadata() {
    }

    public Metadata(String id) {
        this.id = id;
    }

    public Metadata(String scope, String id, String name, String description) {
        this.scope = scope;
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * Retrieves the identifier for the entity associated with this Metadata
     *
     * @return the identifier
     */
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Retrieves the scope for the entity associated with this Metadata
     * @return the scope for the entity associated with this Metadata
     * @see Item Item for a deeper discussion on scopes
     */
    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    /**
     * Retrieves a set of {@link Tag} names associated with this Metadata
     * @return a set of {@link Tag} names associated with this Metadata
     */
    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tagIDs) {
        this.tags = tagIDs;
    }

    /**
     * Whether the associated entity is considered active by the context server, in particular to check if rules need to be created / triggered
     * @return {@code true} if the associated entity is enabled, {@code false} otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Whether the associated entity is waiting on additional plugins to become available to be able to properly perform its function.
     *
     * @return {@code true} if plugins are missing, {@code false} otherwise
     */
    public boolean isMissingPlugins() {
        return missingPlugins;
    }

    public void setMissingPlugins(boolean missingPlugins) {
        this.missingPlugins = missingPlugins;
    }

    /**
     * Whether the associated entity is considered for internal purposes only and should therefore be hidden to accessing UIs.
     * @return {@code true} if the associated entity needs to be hidden, {@code false} otherwise
     */
    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    /**
     * Whether the associated entity can be accessed but not modified.
     *
     * @return {@code true} if the associated entity can be accessed but not modified, {@code false} otherwise
     */
    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public int compareTo(Metadata o) {
        return getId().compareTo(o.getId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Metadata metadata = (Metadata) o;

        if (!id.equals(metadata.id)) return false;
        if (scope != null ? !scope.equals(metadata.scope) : metadata.scope != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + (scope != null ? scope.hashCode() : 0);
        return result;
    }


}
