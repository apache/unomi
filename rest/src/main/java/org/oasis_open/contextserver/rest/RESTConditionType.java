package org.oasis_open.contextserver.rest;

import org.oasis_open.contextserver.api.conditions.ConditionType;

import java.util.*;

public class RESTConditionType {
    private String id;
    private String name;
    private String description;
    private String template;
    private Collection<String> tags = new TreeSet<String>();
    private List<RESTParameter> parameters = new ArrayList<RESTParameter>();

    public RESTConditionType() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public Collection<String> getTags() {
        return tags;
    }

    public void setTags(Collection<String> tags) {
        this.tags = tags;
    }

    public List<RESTParameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<RESTParameter> parameters) {
        this.parameters = parameters;
    }
}
