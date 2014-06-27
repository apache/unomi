package org.oasis_open.wemi.context.server.api.consequences;

import org.oasis_open.wemi.context.server.api.conditions.Parameter;
import org.oasis_open.wemi.context.server.api.conditions.ConditionTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class ConsequenceType {
    String id;
    String name;
    String description;
    Set<ConditionTag> conditionTags = new TreeSet<ConditionTag>();
    List<Parameter> consequenceParameters = new ArrayList<Parameter>();

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

    public Set<ConditionTag> getConditionTags() {
        return conditionTags;
    }

    public void setConditionTags(Set<ConditionTag> conditionTags) {
        this.conditionTags = conditionTags;
    }

    public List<Parameter> getConsequenceParameters() {
        return consequenceParameters;
    }

    public void setConsequenceParameters(List<Parameter> consequenceParameters) {
        this.consequenceParameters = consequenceParameters;
    }

}
