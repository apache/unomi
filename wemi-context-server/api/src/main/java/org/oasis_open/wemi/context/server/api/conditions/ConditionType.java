package org.oasis_open.wemi.context.server.api.conditions;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Represents a node in the segment definition expression tree
 */
@XmlRootElement
public class ConditionType {
    String id;
    String name;
    String description;
    Set<Tag> tags = new TreeSet<Tag>();
    List<Parameter> conditionParameters = new ArrayList<Parameter>();

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

    public Set<Tag> getTags() {
        return tags;
    }

    public void setTags(Set<Tag> tags) {
        this.tags = tags;
    }

    @XmlElement(name="parameters")
    public List<Parameter> getConditionParameters() {
        return conditionParameters;
    }

    public void setConditionParameters(List<Parameter> conditionParameters) {
        this.conditionParameters = conditionParameters;
    }

}
