package org.oasis_open.wemi.context.server.api;

import java.util.Set;
import java.util.TreeSet;

/**
 * Represents a tag on a condition
 */
public class ConditionTag implements Comparable<ConditionTag> {

    String tagKey;

    Set<ConditionTag> subTags = new TreeSet<ConditionTag>();

    public ConditionTag(String tagKey, Set<ConditionTag> subTags) {
        this.tagKey = tagKey;
        this.subTags = subTags;
    }

    public String getTagKey() {
        return tagKey;
    }

    public Set<ConditionTag> getSubTags() {
        return subTags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConditionTag)) return false;

        ConditionTag that = (ConditionTag) o;

        if (subTags != null ? !subTags.equals(that.subTags) : that.subTags != null) return false;
        if (tagKey != null ? !tagKey.equals(that.tagKey) : that.tagKey != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = tagKey != null ? tagKey.hashCode() : 0;
        result = 31 * result + (subTags != null ? subTags.hashCode() : 0);
        return result;
    }

    public int compareTo(ConditionTag o) {
        return 0;
    }
}
