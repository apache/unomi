package org.oasis_open.contextserver.api;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by loom on 24.04.14.
 */
public class Profile extends Item {

    private static final long serialVersionUID = -7409439322939712238L;
    
    public static final String ITEM_TYPE = "profile";
    
    private Map<String,Object> properties;
    
    private Set<String> segments;
    
    private Map<String,Integer> scores;

    public Profile() {
    }

    public Profile(String profileId) {
        super(profileId);
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

    public Map<String, Integer> getScores() {
        return scores;
    }

    public void setScores(Map<String, Integer> scores) {
        this.scores = scores;
    }
    
    @Override
    public String toString() {
        return new StringBuilder(512).append("{id: \"").append(getId()).append("\", segments: ")
                .append(getSegments()).append(", scores: ").append(getScores()).append(", properties: ")
                .append(getProperties()).append("}").toString();
    }
}
