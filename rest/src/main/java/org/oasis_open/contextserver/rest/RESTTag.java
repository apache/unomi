package org.oasis_open.contextserver.rest;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

public class RESTTag {
    private String id;
    private String name;
    private String description;
    private String parentId;
    private double rank = 0.0;
    private Collection<RESTTag> subTags = new TreeSet<RESTTag>();

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

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public double getRank() {
        return rank;
    }

    public void setRank(double rank) {
        this.rank = rank;
    }

    public Collection<RESTTag> getSubTags() {
        return subTags;
    }

    public void setSubTags(Collection<RESTTag> subTags) {
        this.subTags = subTags;
    }

}
