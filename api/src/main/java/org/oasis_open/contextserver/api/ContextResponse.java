package org.oasis_open.contextserver.api;

/*
 * #%L
 * context-server-api
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

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
