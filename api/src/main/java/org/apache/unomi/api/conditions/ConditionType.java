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

package org.apache.unomi.api.conditions;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.MetadataItem;
import org.apache.unomi.api.Parameter;
import org.apache.unomi.api.PluginType;
import org.apache.unomi.api.utils.YamlUtils.*;

import javax.xml.bind.annotation.XmlElement;
import java.util.*;

/**
 * ConditionTypes define new conditions that can be applied to items (for example to decide whether a rule needs to be triggered or if a profile is considered as taking part in a
 * campaign) or to perform queries against the stored unomi data. They may be implemented in Java when attempting to define a particularly complex test or one that can better be
 * optimized by coding it. They may also be defined as combination of other conditions. A simple condition  could be: “User is male”, while a more generic condition with
 * parameters may test whether a given property has a specific value: “User property x has value y”.
 */
public class ConditionType extends MetadataItem implements PluginType, YamlConvertible {
    public static final String ITEM_TYPE = "conditionType";

    private static final long serialVersionUID = -6965481691241954969L;
    private String conditionEvaluator;
    private String queryBuilder;
    private Condition parentCondition;
    private List<Parameter> parameters = new ArrayList<Parameter>();
    private long pluginId;

    /**
     * Instantiates a new Condition type.
     */
    public ConditionType() {
    }

    /**
     * Instantiates a new Condition type with the specified metadata
     * @param metadata the metadata
     */
    public ConditionType(Metadata metadata) {
        super(metadata);
    }

    /**
     * Retrieves the condition evaluator.
     *
     * @return the condition evaluator
     */
    public String getConditionEvaluator() {
        return conditionEvaluator;
    }

    /**
     * Sets the condition evaluator.
     *
     * @param conditionEvaluator the condition evaluator
     */
    public void setConditionEvaluator(String conditionEvaluator) {
        this.conditionEvaluator = conditionEvaluator;
    }

    /**
     * Retrieves the query builder.
     *
     * @return the query builder
     */
    public String getQueryBuilder() {
        return queryBuilder;
    }

    /**
     * Sets the query builder.
     *
     * @param queryBuilder the query builder
     */
    public void setQueryBuilder(String queryBuilder) {
        this.queryBuilder = queryBuilder;
    }

    /**
     * Retrieves the parent condition.
     *
     * @return the parent condition
     */
    public Condition getParentCondition() {
        return parentCondition;
    }

    /**
     * Sets the parent condition.
     *
     * @param parentCondition the parent condition
     */
    public void setParentCondition(Condition parentCondition) {
        this.parentCondition = parentCondition;
    }

    /**
     * Retrieves the defined {@link Parameter}s for this ConditionType.
     *
     * @return a List of the defined {@link Parameter}s for this ConditionType
     */
    @XmlElement(name = "parameters")
    public List<Parameter> getParameters() {
        return parameters;
    }

    /**
     * Sets the List of the defined {@link Parameter}s for this ConditionType.
     *
     * @param parameters a List of the defined {@link Parameter}s for this ConditionType
     */
    public void setParameters(List<Parameter> parameters) {
        this.parameters = parameters;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConditionType that = (ConditionType) o;

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
     * Converts this condition type to a Map structure for YAML output.
     * Implements YamlConvertible interface with circular reference detection.
     *
     * @param visited set of already visited objects to prevent infinite recursion (may be null)
     * @return a Map representation of this condition type
     */
    @Override
    public Map<String, Object> toYaml(Set<Object> visited, int maxDepth) {
        if (maxDepth <= 0) {
            return YamlMapBuilder.create()
                .put("parentCondition", "<max depth exceeded>")
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
                .putIfNotNull("conditionEvaluator", conditionEvaluator)
                .putIfNotNull("queryBuilder", queryBuilder)
                .putIfNotNull("parentCondition", parentCondition != null ? toYamlValue(parentCondition, visitedSet, maxDepth - 1) : null)
                .putIfNotEmpty("parameters", parameters != null ? (Collection<?>) toYamlValue(parameters, visitedSet, maxDepth - 1) : null)
                .put("pluginId", pluginId)
                .build();
        } finally {
            visitedSet.remove(this);
        }
    }
}
