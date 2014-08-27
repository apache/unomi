package org.oasis_open.wemi.context.server.api;

import java.util.Properties;
import java.util.Set;

/**
 * Created by loom on 24.04.14.
 */
public class User extends Item {

    public static final String ITEM_TYPE = "user";
    private Metadata metadata;
    private Properties properties;
    private Set<String> segments;

    public User() {
    }

    public User(Metadata metadata) {
        super(metadata.getId());
        properties = new Properties();
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.itemId = metadata.getId();
        this.metadata = metadata;
    }

    public void setProperty(String name, String value) {
        properties.setProperty(name, value);
    }

    public String getProperty(String name) {
        return properties.getProperty(name);
    }

    public Properties getProperties() {
        return properties;
    }

    public Set<String> getSegments() {
        return segments;
    }

    public void setSegments(Set<String> segments) {
        this.segments = segments;
    }
}
