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

package org.apache.unomi.api.actions;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.MetadataItem;
import org.apache.unomi.api.Parameter;
import org.apache.unomi.api.PluginType;
import org.apache.unomi.api.utils.YamlUtils.YamlConvertible;
import org.apache.unomi.api.utils.YamlUtils.YamlMapBuilder;

import java.util.*;

import static org.apache.unomi.api.utils.YamlUtils.circularRef;
import static org.apache.unomi.api.utils.YamlUtils.toYamlValue;

/**
 * A type definition for {@link Action}s.
 */
public class ActionType extends MetadataItem implements PluginType, YamlConvertible {
    public static final String ITEM_TYPE = "actionType";

    private static final long serialVersionUID = -3522958600710010935L;
    private String actionExecutor;
    private List<Parameter> parameters = new ArrayList<Parameter>();
    private long pluginId;

    /**
     * Instantiates a new Action type.
     */
    public ActionType() {
    }

    /**
     * Instantiates a new Action type.
     * @param metadata the metadata
     */
    public ActionType(Metadata metadata) {
        super(metadata);
    }

    /**
     * Retrieves the action executor.
     *
     * @return the action executor
     */
    public String getActionExecutor() {
        return actionExecutor;
    }

    /**
     * Sets the action executor.
     *
     * @param actionExecutor the action executor
     */
    public void setActionExecutor(String actionExecutor) {
        this.actionExecutor = actionExecutor;
    }

    /**
     * Retrieves the parameters.
     *
     * @return the parameters
     */
    public List<Parameter> getParameters() {
        return parameters;
    }

    /**
     * Sets the parameters.
     *
     * @param parameters the parameters
     */
    public void setParameters(List<Parameter> parameters) {
        this.parameters = parameters;
    }



    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ActionType that = (ActionType) o;

        return itemId.equals(that.itemId);

    }

    @Override
    public int hashCode() {
        return itemId.hashCode();
    }

    @Override
    public long getPluginId() {
        return pluginId;
    }

    @Override
    public void setPluginId(long pluginId) {
        this.pluginId = pluginId;
    }

    /**
     * Converts this action type to a Map structure for YAML output.
     * Implements YamlConvertible interface with circular reference detection.
     *
     * @param visited set of already visited objects to prevent infinite recursion (may be null)
     * @return a Map representation of this action type
     */
    @Override
    public Map<String, Object> toYaml(Set<Object> visited, int maxDepth) {
        if (maxDepth <= 0) {
            return YamlMapBuilder.create()
                .put("parameters", "<max depth exceeded>")
                .put("pluginId", pluginId)
                .build();
        }
        if (visited != null && visited.contains(this)) {
            return circularRef();
        }
        final Set<Object> visitedSet = visited != null ? visited : new HashSet<>();
        visitedSet.add(this);
        try {
            return YamlMapBuilder.create()
                .mergeObject(super.toYaml(visitedSet, maxDepth))
                .putIfNotNull("actionExecutor", actionExecutor)
                .putIfNotEmpty("parameters", parameters != null ? (Collection<?>) toYamlValue(parameters, visitedSet, maxDepth - 1) : null)
                .put("pluginId", pluginId)
                .build();
        } finally {
            visitedSet.remove(this);
        }
    }
}
