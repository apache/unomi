package org.oasis_open.contextserver.rest;

import java.util.*;

public class RESTActionType {
    private String id;
    private String name;
    private String description;
    private Collection<String> tags;
    private List<RESTParameter> parameters;

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
