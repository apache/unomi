package org.oasis_open.wemi.context.server.api;

import javax.xml.bind.annotation.XmlElement;
import java.util.Set;
import java.util.TreeSet;

/**
 * Represents a tag on a condition
 */
public class Tag implements Comparable<Tag> {

    String id;
    String nameKey;
    String descriptionKey;
    String parentId;

    Set<Tag> subTags = new TreeSet<Tag>();

    public Tag() {
    }

    public Tag(String id, String nameKey, String descriptionKey, String parentId) {
        this.id = id;
        this.nameKey = nameKey;
        this.descriptionKey = descriptionKey;
        this.parentId = parentId;
    }

    public Tag(String id) {
        this.id = id;
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

    public int compareTo(Tag o) {
        return id.compareTo(o.id);
    }
}
