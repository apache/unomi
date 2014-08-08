package org.oasis_open.wemi.context.server.api;

/**
 * Created by loom on 24.04.14.
 */
public class Metadata implements Comparable<Metadata> {

    private String id;
    private String name;
    private String description;

    public Metadata() {
    }

    public Metadata(String id) {
        this.id = id;
    }

    public Metadata(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
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

    public int compareTo(Metadata o) {
        if (id != null) {
            return id.compareTo(o.id);
        } else {
            return -1;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Metadata metadata = (Metadata) o;

        if (id != null ? !id.equals(metadata.id) : metadata.id != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
