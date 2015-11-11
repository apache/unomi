package org.oasis_open.contextserver.api.conditions;

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

import org.oasis_open.contextserver.api.Parameter;
import org.oasis_open.contextserver.api.PluginType;
import org.oasis_open.contextserver.api.Tag;
import org.oasis_open.contextserver.api.rules.Rule;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.*;

/**
 * ConditionTypes define new conditions that can be applied to items (for example to decide whether a rule needs to be triggered or if a profile is considered as taking part in a
 * campaign) or to perform queries against the stored unomi data. They may be implemented in Java when attempting to define a particularly complex test or one that can better be
 * optimized by coding it. They may also be defined as combination of other conditions. A simple condition  could be: “User is male”, while a more generic condition with
 * parameters may test whether a given property has a specific value: “User property x has value y”.
 */
@XmlRootElement
public class ConditionType implements PluginType, Serializable {
    private static final long serialVersionUID = -6965481691241954968L;
    private String id;
    private String nameKey;
    private String descriptionKey;
    private long pluginId;
    private String conditionEvaluator;
    private String queryBuilder;
    private Condition parentCondition;
    private Set<Tag> tags = new TreeSet<Tag>();
    private Set<String> tagIDs = new LinkedHashSet<String>();
    private List<Parameter> parameters = new ArrayList<Parameter>();
    private Rule autoCreateRule;

    /**
     * Instantiates a new Condition type.
     */
    public ConditionType() {
    }

    /**
     * Instantiates a new Condition type with the specified identifier and .
     *
     * @param id      the id
     * @param nameKey the name key
     */
    public ConditionType(String id, String nameKey) {
        this.id = id;
        this.nameKey = nameKey;
    }

    /**
     * Retrieves the id.
     *
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * Retrieves the name key.
     *
     * @return the name key
     */
    public String getNameKey() {
        if (nameKey == null) {
            nameKey = "condition." + id + ".name";
        }
        return nameKey;
    }

    /**
     * Sets the name key.
     *
     * @param nameKey the name key
     */
    public void setNameKey(String nameKey) {
        this.nameKey = nameKey;
    }

    /**
     * Retrieves the description key.
     *
     * @return the description key
     */
    public String getDescriptionKey() {
        if (descriptionKey == null) {
            descriptionKey = "condition." + id + ".description";
        }
        return descriptionKey;
    }

    /**
     * Sets the description key.
     *
     * @param descriptionKey the description key
     */
    public void setDescriptionKey(String descriptionKey) {
        this.descriptionKey = descriptionKey;
    }

    @XmlTransient
    public long getPluginId() {
        return pluginId;
    }

    public void setPluginId(long pluginId) {
        this.pluginId = pluginId;
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
     * Retrieves the tags used by this PropertyType.
     *
     * @return the tags used by this PropertyType
     */
    @XmlTransient
    public Set<Tag> getTags() {
        return tags;
    }

    /**
     * Sets the tags used by this PropertyType.
     *
     * @param tags the tags used by this PropertyType
     */
    public void setTags(Set<Tag> tags) {
        this.tags = tags;
    }

    /**
     * Retrieves the identifiers of the tags used by this PropertyType.
     *
     * @return the identifiers of the tags used by this PropertyType
     */
    @XmlElement(name = "tags")
    public Set<String> getTagIDs() {
        return tagIDs;
    }

    /**
     * Sets the identifiers of the tags used by this PropertyType.
     *
     * @param tagIds the identifiers of the tags used by this PropertyType
     */
    public void setTagIDs(Set<String> tagIds) {
        this.tagIDs = tagIds;
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

    /**
     * Retrieves the auto create rule.
     *
     * @return the auto create rule
     */
    public Rule getAutoCreateRule() {
        return autoCreateRule;
    }

    /**
     * Sets the auto create rule.
     *
     * @param autoCreateRule the auto create rule
     */
    public void setAutoCreateRule(Rule autoCreateRule) {
        this.autoCreateRule = autoCreateRule;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConditionType that = (ConditionType) o;

        if (!id.equals(that.id)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
