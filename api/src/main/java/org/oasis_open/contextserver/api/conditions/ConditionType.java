package org.oasis_open.contextserver.api.conditions;

import org.oasis_open.contextserver.api.Tag;
import org.oasis_open.contextserver.api.rules.Rule;
import org.oasis_open.contextserver.api.Parameter;
import org.oasis_open.contextserver.api.TemplateablePluginType;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.*;

/**
 * Represents a node in the segment definition expression tree
 */
@XmlRootElement
public class ConditionType implements TemplateablePluginType, Serializable {
    String id;
    String nameKey;
    String descriptionKey;
    String template;
    String resourceBundle;
    long pluginId;
    String conditionEvaluator;
    String queryBuilderFilter;
    Condition parentCondition;
    Set<Tag> tags = new TreeSet<Tag>();
    Set<String> tagIDs = new LinkedHashSet<String>();
    List<Parameter> parameters = new ArrayList<Parameter>();
    Rule autoCreateRule;

    public ConditionType() {
    }

    public ConditionType(String id, String nameKey) {
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

    public String getConditionEvaluator() {
        return conditionEvaluator;
    }

    public void setConditionEvaluator(String conditionEvaluator) {
        this.conditionEvaluator = conditionEvaluator;
    }

    public String getQueryBuilderFilter() {
        return queryBuilderFilter;
    }

    public void setQueryBuilderFilter(String queryBuilderFilter) {
        this.queryBuilderFilter = queryBuilderFilter;
    }

    public Condition getParentCondition() {
        return parentCondition;
    }

    public void setParentCondition(Condition parentCondition) {
        this.parentCondition = parentCondition;
    }

    @XmlElement(name = "tags")
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

    @XmlElement(name = "parameters")
    public List<Parameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<Parameter> parameters) {
        this.parameters = parameters;
    }

    public Rule getAutoCreateRule() {
        return autoCreateRule;
    }

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
