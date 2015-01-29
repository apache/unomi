package org.oasis_open.contextserver.api;

import org.oasis_open.contextserver.api.conditions.Condition;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

public class ContextResponse implements Serializable {

    private String profileId;

    private String sessionId;

    private Map<String, Object> profileProperties;

    private Map<String, Object> sessionProperties;

    private Set<String> profileSegments;

    private Map<String, Boolean> filteringResults;

    private Set<Condition> trackedConditions;

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Map<String, Object> getProfileProperties() {
        return profileProperties;
    }

    public void setProfileProperties(Map<String, Object> profileProperties) {
        this.profileProperties = profileProperties;
    }

    public Map<String, Object> getSessionProperties() {
        return sessionProperties;
    }

    public void setSessionProperties(Map<String, Object> sessionProperties) {
        this.sessionProperties = sessionProperties;
    }

    public Set<String> getProfileSegments() {
        return profileSegments;
    }

    public void setProfileSegments(Set<String> profileSegments) {
        this.profileSegments = profileSegments;
    }

    public Map<String, Boolean> getFilteringResults() {
        return filteringResults;
    }

    public void setFilteringResults(Map<String, Boolean> filteringResults) {
        this.filteringResults = filteringResults;
    }

    public Set<Condition> getTrackedConditions() {
        return trackedConditions;
    }

    public void setTrackedConditions(Set<Condition> trackedConditions) {
        this.trackedConditions = trackedConditions;
    }
}
