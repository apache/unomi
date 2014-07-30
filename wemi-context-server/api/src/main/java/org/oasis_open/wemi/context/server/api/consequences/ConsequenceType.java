package org.oasis_open.wemi.context.server.api.consequences;

import org.oasis_open.wemi.context.server.api.conditions.Parameter;
import org.oasis_open.wemi.context.server.api.conditions.Tag;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import java.util.*;

public class ConsequenceType {
    String id;
    String name;
    String description;
    Set<Tag> tags = new TreeSet<Tag>();
    Set<String> tagIds = new LinkedHashSet<String>();
    List<Parameter> parameters = new ArrayList<Parameter>();

    public ConsequenceType() {
    }

    public ConsequenceType(String id, String name) {
        this.id = id;
        this.name = name;
    }

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

    @XmlElement(name="tags")
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

}
