package org.oasis_open.wemi.context.server.api;

import javax.xml.bind.annotation.XmlTransient;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Created by loom on 28.08.14.
 */
public class UserPropertyGroup extends Item implements Comparable<UserPropertyGroup> {

    private String id;
    private double rank;
    private String resourceBundle;
    private SortedSet<UserProperty> userProperties = new TreeSet<UserProperty>();

    public UserPropertyGroup() {
    }

    public UserPropertyGroup(String itemId) {
        super(itemId);
        this.id = itemId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public double getRank() {
        return rank;
    }

    public void setRank(double rank) {
        this.rank = rank;
    }

    @XmlTransient
    public SortedSet<UserProperty> getUserProperties() {
        return userProperties;
    }

    public void setUserProperties(SortedSet<UserProperty> userProperties) {
        this.userProperties = userProperties;
    }

    public String getResourceBundle() {
        return resourceBundle;
    }

    public void setResourceBundle(String resourceBundle) {
        this.resourceBundle = resourceBundle;
    }

    public int compareTo(UserPropertyGroup o) {
        return Double.compare(rank, o.rank);
    }
}
