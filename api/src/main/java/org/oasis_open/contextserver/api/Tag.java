package org.oasis_open.contextserver.api;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import java.util.Set;
import java.util.TreeSet;

/**
 * Represents a tag on a condition
 */
public class Tag implements PluginType, Comparable<Tag> {

    Set<Tag> subTags = new TreeSet<Tag>();
    private String id;
    private String nameKey;
    private String descriptionKey;
    private String parentId;
    private double rank = 0.0;
    private long pluginId;
    private String resourceBundle;

    public Tag() {
    }

    public Tag(String id, String nameKey, String descriptionKey, String parentId) {
        this.id = id;
        this.nameKey = nameKey;
        this.descriptionKey = descriptionKey;
        this.parentId = parentId;
    }

    public String getId() {
        return id;
    }

    public String getNameKey() {
        if (nameKey == null) {
            nameKey = id.toUpperCase().replaceAll("\\.", "_") + "_TAG_NAME_LABEL";
        }
        return nameKey;
    }

    public String getDescriptionKey() {
        if (descriptionKey == null) {
            descriptionKey = id.toUpperCase().replaceAll("\\.", "_") + "_TAG_DESCRIPTION_LABEL";
        }
        return descriptionKey;
    }

    @XmlElement(name = "parent")
    public String getParentId() {
        return parentId;
    }

    public Set<Tag> getSubTags() {
        return subTags;
    }

    public void setSubTags(Set<Tag> subTags) {
        this.subTags = subTags;
    }

    public double getRank() {
        return rank;
    }

    public void setRank(double rank) {
        this.rank = rank;
    }

    @XmlTransient
    public long getPluginId() {
        return pluginId;
    }

    public void setPluginId(long pluginId) {
        this.pluginId = pluginId;
    }

    public String getResourceBundle() {
        return resourceBundle;
    }

    public void setResourceBundle(String resourceBundle) {
        this.resourceBundle = resourceBundle;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Tag that = (Tag) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        return result;
    }

    public int compareTo(Tag otherRank) {
        int rankCompare = Double.compare(rank, otherRank.rank);
        if (rankCompare != 0) {
            return rankCompare;
        }
        return id.compareTo(otherRank.id);
    }
}
