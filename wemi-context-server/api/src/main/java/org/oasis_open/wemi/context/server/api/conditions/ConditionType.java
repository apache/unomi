package org.oasis_open.wemi.context.server.api.conditions;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.util.*;

/**
 * Represents a node in the segment definition expression tree
 */
@XmlRootElement
public class ConditionType {
    String id;
    String name;
    String description;
    Set<Tag> tags = new TreeSet<Tag>();
    Set<String> tagIDs = new LinkedHashSet<String>();
    List<Parameter> parameters = new ArrayList<Parameter>();

    public ConditionType() {
    }

    public ConditionType(String id, String name) {
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
    public Set<String> getTagIDs() {
        return tagIDs;
    }

    public void setTagIDs(Set<String> tagIDs) {
        this.tagIDs = tagIDs;
    }

    @XmlTransient
    public Set<Tag> getTags() {
        return tags;
    }

    public void setTags(Set<Tag> tags) {
        this.tags = tags;
    }

    @XmlElement(name="parameters")
    public List<Parameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<Parameter> parameters) {
        this.parameters = parameters;
    }

}
