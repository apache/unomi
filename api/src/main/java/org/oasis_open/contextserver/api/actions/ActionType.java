package org.oasis_open.contextserver.api.actions;

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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.*;

/**
 * ActionTypes define new actions that can be used as consequences of Rules being triggered. When a rule triggers, it creates new actions based on the event data and the rule
 * internal processes, providing values for parameters defined in the associated ActionType. Example actions include: “Set user property x to value y” or “Send a message to service
 * x”.
 */
public class ActionType implements PluginType, Serializable {

    private static final long serialVersionUID = -3522958600710010934L;
    private String id;
    private String nameKey;
    private String descriptionKey;
    private String actionExecutor;
    private Set<Tag> tags = new TreeSet<Tag>();
    private Set<String> tagIds = new LinkedHashSet<String>();
    private long pluginId;
    private List<Parameter> parameters = new ArrayList<Parameter>();

    /**
     * Instantiates a new Action type.
     */
    public ActionType() {
    }

    /**
     * Instantiates a new Action type.
     *
     * @param id      the id
     * @param nameKey the name key
     */
    public ActionType(String id, String nameKey) {
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
     * Retrieves the {@link java.util.ResourceBundle} key used to localize this ActionType's name.
     *
     * @return the {@link java.util.ResourceBundle} key used to localize this ActionType's name
     */
    public String getNameKey() {
        if (nameKey == null) {
            nameKey = "action." + id + ".name";
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
     * Retrieves the {@link java.util.ResourceBundle} key used to localize this ActionType's description.
     *
     * @return the {@link java.util.ResourceBundle} key used to localize this ActionType's name
     */
    public String getDescriptionKey() {
        if (descriptionKey == null) {
            descriptionKey = "action." + id + ".description";
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
     * Retrieves the tags used by this ActionType.
     *
     * @return the tags used by this ActionType
     */
    @XmlTransient
    public Set<Tag> getTags() {
        return tags;
    }

    /**
     * Sets the tags used by this ActionType.
     *
     * @param tags the tags used by this ActionType
     */
    public void setTags(Set<Tag> tags) {
        this.tags = tags;
    }

    /**
     * Retrieves the identifiers of the tags used by this ActionType.
     *
     * @return the identifiers of the tags used by this ActionType
     */
    @XmlElement(name = "tags")
    public Set<String> getTagIds() {
        return tagIds;
    }

    /**
     * Sets the identifiers of the tags used by this ActionType.
     *
     * @param tagIds the identifiers of the tags used by this ActionType
     */
    public void setTagIds(Set<String> tagIds) {
        this.tagIds = tagIds;
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

        if (!id.equals(that.id)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
