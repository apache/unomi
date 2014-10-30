package org.oasis_open.wemi.context.server.api;

import java.io.Serializable;
import java.util.Map;

/**
 * Created by toto on 29/10/14.
 */
public class EventTarget implements Serializable {
    private static EventTarget ourInstance = new EventTarget();
    private String id;
    private String type;
    private Map<String, Object> properties;

    public EventTarget() {
    }

    public EventTarget(String id, String type) {
        this.id = id;
        this.type = type;
    }

    public static EventTarget getInstance() {
        return ourInstance;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("EventTarget{");
        sb.append("id='").append(id).append('\'');
        sb.append(", type='").append(type).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
