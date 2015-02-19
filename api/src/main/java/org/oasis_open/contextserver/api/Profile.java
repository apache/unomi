package org.oasis_open.contextserver.api;

import javax.xml.bind.annotation.XmlTransient;
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

    private String mergedWith;

    public Profile() {
    }

    public Profile(String profileId) {
        super(profileId);
        properties = new HashMap<String, Object>();
        segments = new HashSet<String>();
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

    @XmlTransient
    public String getScope() {
        return "systemscope";
    }

    public Set<String> getSegments() {
        return segments;
    }

    public void setSegments(Set<String> segments) {
        this.segments = segments;
    }

    public String getMergedWith() {
        return mergedWith;
    }

    public void setMergedWith(String mergedWith) {
        this.mergedWith = mergedWith;
    }

    public Map<String, Integer> getScores() {
        return scores;
    }

    public void setScores(Map<String, Integer> scores) {
        this.scores = scores;
    }
    
    @Override
    public String toString() {
        return new StringBuilder(512).append("{id: \"").append(getItemId()).append("\", segments: ")
                .append(getSegments()).append(", scores: ").append(getScores()).append(", properties: ")
                .append(getProperties()).append("}").toString();
    }
}
