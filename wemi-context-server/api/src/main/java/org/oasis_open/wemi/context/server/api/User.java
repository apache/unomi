package org.oasis_open.wemi.context.server.api;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by loom on 24.04.14.
 */
public class User extends Item {

    public static final String ITEM_TYPE = "user";
    private Map<String,Object> properties;
    private Set<String> segments;

    public User() {
    }

    public User(String userId) {
        super(userId);
        properties = new HashMap<String, Object>();
        segments = new HashSet<String>();
    }

    public String getId() {
        return itemId;
    }

    public void setId(String id) {
        this.itemId = id;
    }

    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }

    public Object getProperty(String name) {
        return properties.get(name);
    }

    public Map<String,Object> getProperties() {
        return properties;
    }

    public Set<String> getSegments() {
        return segments;
    }

    public void setSegments(Set<String> segments) {
        this.segments = segments;
    }


}
