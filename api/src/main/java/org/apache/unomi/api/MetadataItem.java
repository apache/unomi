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
import org.apache.unomi.api.utils.YamlUtils.YamlMapBuilder;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.apache.unomi.api.utils.YamlUtils.toYamlValue;

/**
 * A superclass for all {@link Item}s that bear {@link Metadata}.
 */
public abstract class MetadataItem extends Item {
    private static final long serialVersionUID = -2459510107927663510L;
    protected Metadata metadata;

    public MetadataItem() {
    }

    public MetadataItem(Metadata metadata) {
        super(metadata != null ? metadata.getId() : null);
        this.metadata = metadata;
    }

    /**
     * Retrieves the associated Metadata.
     *
     * @return the associated Metadata
     */
    @XmlElement(name = "metadata")
    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        if (metadata != null) {
            this.itemId = metadata.getId();
        }
        this.metadata = metadata;
    }

    @XmlTransient
    public String getScope() {
        if (metadata != null) {
            return metadata.getScope();
        }
        return scope;
    }

    /**
     * Converts this metadata item to a Map structure for YAML output.
     * Merges fields from Item parent class and adds metadata field.
     * Subclasses should override this method, call super.toYaml(visited), and add their specific fields.
     *
     * @param visited set of already visited objects to prevent infinite recursion (may be null)
     * @return a Map representation of this metadata item
     */
    @Override
    public Map<String, Object> toYaml(Set<Object> visited, int maxDepth) {
        if (maxDepth <= 0) {
            return YamlMapBuilder.create()
                .put("metadata", "<max depth exceeded>")
                .build();
        }
        final Set<Object> visitedSet = visited != null ? visited : new HashSet<>();
        // Check if already visited - if so, we're being called from a child class via super.toYaml()
        // In that case, skip the circular reference check and just proceed
        boolean alreadyVisited = visitedSet.contains(this);
        if (!alreadyVisited) {
            // Only check for circular references if this is the first time we're seeing this object
            visitedSet.add(this);
        }
        try {
            return YamlMapBuilder.create()
                .mergeObject(super.toYaml(visitedSet, maxDepth))
                .putIfNotNull("metadata", metadata != null ? toYamlValue(metadata, visitedSet, maxDepth - 1) : null)
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
