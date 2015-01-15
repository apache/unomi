package org.oasis_open.contextserver.rest;

import org.oasis_open.contextserver.api.Parameter;
import org.oasis_open.contextserver.api.Tag;

import java.util.*;

/**
 * Created by toto on 14/01/15.
 */
public class RESTActionType {
    private String id;
    private String name;
    private String description;
    private Collection<String> tags;
    private String template;
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

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public List<RESTParameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<RESTParameter> parameters) {
        this.parameters = parameters;
    }
}
