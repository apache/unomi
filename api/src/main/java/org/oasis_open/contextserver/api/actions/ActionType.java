package org.oasis_open.contextserver.api.actions;

import org.oasis_open.contextserver.api.Parameter;
import org.oasis_open.contextserver.api.Tag;
import org.oasis_open.contextserver.api.TemplateablePluginType;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.*;

public class ActionType implements TemplateablePluginType, Serializable {

    String id;
    String nameKey;
    String descriptionKey;
    String serviceFilter;
    Set<Tag> tags = new TreeSet<Tag>();
    Set<String> tagIds = new LinkedHashSet<String>();
    String template;
    long pluginId;
    List<Parameter> parameters = new ArrayList<Parameter>();

    public ActionType() {
    }

    public ActionType(String id, String nameKey) {
        this.id = id;
        this.nameKey = nameKey;
    }

    public String getId() {
        return id;
    }

    public String getNameKey() {
        if (nameKey == null) {
            nameKey = id.toUpperCase().replaceAll("\\.", "_") + "_NAME_LABEL";
        }
        return nameKey;
    }

    public void setNameKey(String nameKey) {
        this.nameKey = nameKey;
    }

    public String getDescriptionKey() {
        if (descriptionKey == null) {
            descriptionKey = id.toUpperCase().replaceAll("\\.", "_") + "_DESCRIPTION_LABEL";
        }
        return descriptionKey;
    }

    public void setDescriptionKey(String descriptionKey) {
        this.descriptionKey = descriptionKey;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    @XmlTransient
    public long getPluginId() {
        return pluginId;
    }

    public void setPluginId(long pluginId) {
        this.pluginId = pluginId;
    }

    public String getServiceFilter() {
        return serviceFilter;
    }

    public void setServiceFilter(String serviceFilter) {
        this.serviceFilter = serviceFilter;
    }

    @XmlElement(name = "tags")
    public Set<String> getTagIds() {
        return tagIds;
    }

    public void setTagIds(Set<String> tagIds) {
        this.tagIds = tagIds;
    }

    @XmlTransient
    public Set<Tag> getTags() {
        return tags;
    }

    public void setTags(Set<Tag> tags) {
        this.tags = tags;
    }

    public List<Parameter> getParameters() {
        return parameters;
    }

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
