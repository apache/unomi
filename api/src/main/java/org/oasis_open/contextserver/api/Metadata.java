package org.oasis_open.contextserver.api;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by loom on 24.04.14.
 */
public class Metadata implements Comparable<Metadata> {

    public static final String SYSTEM_SCOPE = "systemscope";
    private String id;
    private String name;
    private String description;
    private String scope;
    private Set<String> tags = new LinkedHashSet<>();
    private boolean enabled = true;
    private boolean missingPlugins = false;
    private boolean hidden = false;
    private boolean readOnly = false;

    public Metadata() {
    }

    public Metadata(String id) {
        this.id = id;
    }

    public Metadata(String scope, String id, String name, String description) {
        this.scope = scope;
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
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

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tagIDs) {
        this.tags = tagIDs;
    }

    @XmlTransient
    public String getIdWithScope() {
        return getIdWithScope(scope, id);
    }

    public static String getIdWithScope(String scope, String id) {
        return (scope == null ? SYSTEM_SCOPE : scope) + "_" + id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isMissingPlugins() {
        return missingPlugins;
    }

    public void setMissingPlugins(boolean missingPlugins) {
        this.missingPlugins = missingPlugins;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public int compareTo(Metadata o) {
        return getIdWithScope().compareTo(o.getIdWithScope());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Metadata metadata = (Metadata) o;

        if (!id.equals(metadata.id)) return false;
        if (scope != null ? !scope.equals(metadata.scope) : metadata.scope != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + (scope != null ? scope.hashCode() : 0);
        return result;
    }


}
