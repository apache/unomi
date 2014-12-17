package org.oasis_open.wemi.context.server.api;

import org.oasis_open.wemi.context.server.api.Session;
import org.oasis_open.wemi.context.server.api.User;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ContextResponse implements Serializable {

    private String userId;

    private String sessionId;

    private Map<String, Object> userProperties;

    private Map<String, Object> sessionProperties;

    private Set<String> userSegments;

    private Map<String, Boolean> filteringResults;

    private List<String> formNames;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Map<String, Object> getUserProperties() {
        return userProperties;
    }

    public void setUserProperties(Map<String, Object> userProperties) {
        this.userProperties = userProperties;
    }

    public Map<String, Object> getSessionProperties() {
        return sessionProperties;
    }

    public void setSessionProperties(Map<String, Object> sessionProperties) {
        this.sessionProperties = sessionProperties;
    }

    public Set<String> getUserSegments() {
        return userSegments;
    }

    public void setUserSegments(Set<String> userSegments) {
        this.userSegments = userSegments;
    }

    public Map<String, Boolean> getFilteringResults() {
        return filteringResults;
    }

    public void setFilteringResults(Map<String, Boolean> filteringResults) {
        this.filteringResults = filteringResults;
    }

    public List<String> getFormNames() {
        return formNames;
    }

    public void setFormNames(List<String> formNames) {
        this.formNames = formNames;
    }
}
