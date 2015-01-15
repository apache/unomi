package org.oasis_open.contextserver.rest;

import java.util.Collection;
import java.util.Set;

/**
 * Created by toto on 14/01/15.
 */
public class RESTValueType {

    private String id;
    private String name;
    private String description;
    private Collection<RESTTag> tags;

    public RESTValueType() {
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

    public Collection<RESTTag> getTags() {
        return tags;
    }

    public void setTags(Collection<RESTTag> tags) {
        this.tags = tags;
    }
}
