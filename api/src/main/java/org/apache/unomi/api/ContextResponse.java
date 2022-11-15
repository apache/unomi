/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.unomi.api;

import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.RulesService;

import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A context server response resulting from the evaluation of a client's context request. Note that all returned values result of the evaluation of the data provided in the
 * associated ContextRequest and might therefore reflect results due to user impersonation via properties / segment overrides.
 *
 * @see ContextRequest
 */
public class ContextResponse implements Serializable {

    private static final long serialVersionUID = -5638595408986826332L;

    private String profileId;

    private String sessionId;

    private Map<String, Object> profileProperties;

    private Map<String, Object> sessionProperties;

    private Set<String> profileSegments;

    private Map<String,Integer> profileScores;

    private Map<String, Boolean> filteringResults;

    private int processedEvents;

    private Map<String, List<String>> personalizations;

    private Map<String, PersonalizationResult> personalizationResults;

    private Set<Condition> trackedConditions;

    private boolean anonymousBrowsing;

    private Map<String, Consent> consents = new LinkedHashMap<>();

    /**
     * Retrieves the profile identifier associated with the profile of the user on behalf of which the client performed the context request.
     *
     * @return the profile identifier associated with the profile of the active user
     */
    public String getProfileId() {
        return profileId;
    }

    /**
     * Sets the profile id.
     *
     * @param profileId the profile id
     */
    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    /**
     * Retrieves the session identifier associated with the processed request.
     *
     * @return the session identifier associated with the processed request
     * @see Session
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Sets the session id.
     *
     * @param sessionId the session id
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Retrieves the profile properties that were requested by the client.
     *
     * @return the profile properties that were requested by the client
     * @see ContextRequest#getRequiredProfileProperties()
     */
    public Map<String, Object> getProfileProperties() {
        return profileProperties;
    }

    /**
     * Sets the profile properties.
     *
     * @param profileProperties the profile properties
     */
    public void setProfileProperties(Map<String, Object> profileProperties) {
        this.profileProperties = profileProperties;
    }

    /**
     * Retrieves the session properties that were requested by the client.
     *
     * @return the session properties that were requested by the client
     * @see ContextRequest#getRequiredSessionProperties()
     */
    public Map<String, Object> getSessionProperties() {
        return sessionProperties;
    }

    /**
     * Sets the session properties.
     *
     * @param sessionProperties the session properties
     */
    public void setSessionProperties(Map<String, Object> sessionProperties) {
        this.sessionProperties = sessionProperties;
    }

    /**
     * Retrieves the identifiers of the profile segments associated with the user if they were requested by the client. Note that these segments are evaluated taking potential
     * overrides as requested by the client or as a result of evaluating overridden properties.
     *
     * @return the profile segments associated with the user accounting for potential overrides
     */
    public Set<String> getProfileSegments() {
        return profileSegments;
    }

    /**
     * Sets the profile segments.
     *
     * @param profileSegments the profile segments
     */
    public void setProfileSegments(Set<String> profileSegments) {
        this.profileSegments = profileSegments;
    }

    /**
     * Retrieve the current scores for the profile if they were requested in the request using the requireScores boolean.
     * @return a map that contains the score identifier as the key and the score value as the value
     */
    public Map<String, Integer> getProfileScores() {
        return profileScores;
    }

    /**
     * Stores the scores for the current profile if requested using the requireScores boolean in the request.
     * @param profileScores a map that contains the score identifier as the key and the score value as the value
     */
    public void setProfileScores(Map<String, Integer> profileScores) {
        this.profileScores = profileScores;
    }

    /**
     * Retrieves the results of the evaluation content filtering definitions and whether individual definitions match with the associated profile (potentially modified by
     * overridden values).
     *
     * @return a Map associating the filter identifier as key to its evaluation result by the context server
     */
    public Map<String, Boolean> getFilteringResults() {
        return filteringResults;
    }

    /**
     * Sets the filtering results.
     *
     * @param filteringResults the filtering results
     */
    public void setFilteringResults(Map<String, Boolean> filteringResults) {
        this.filteringResults = filteringResults;
    }


    public int getProcessedEvents() {
        return processedEvents;
    }

    public void setProcessedEvents(int processedEvents) {
        this.processedEvents = processedEvents;
    }

    /**
     * @deprecated personalizations results are more complex since 2.1.0 and they are now available under: getPersonalizationResults()
     */
    @Deprecated
    @XmlTransient
    public Map<String, List<String>> getPersonalizations() {
        return personalizations;
    }

    /**
     * @deprecated personalizations results are more complex since 2.1.0 and they are now available under: setPersonalizationResults()
     */
    @Deprecated
    public void setPersonalizations(Map<String, List<String>> personalizations) {
        this.personalizations = personalizations;
    }

    /**
     * Get the result of the personalization resolutions done during the context request.
     * @return a Map key/value pair (key:personalization id, value:the result that contains the matching content ids and additional information)
     */
    public Map<String, PersonalizationResult> getPersonalizationResults() {
        return personalizationResults;
    }

    public void setPersonalizationResults(Map<String, PersonalizationResult> personalizationResults) {
        this.personalizationResults = personalizationResults;
    }

    /**
     * Retrieves the tracked conditions, if any, associated with the source of the context request that resulted in this ContextResponse. Upon evaluating the incoming request,
     * the context server will determine if there are any rules marked with the "trackedCondition" tag and which source condition matches the source of the incoming request and
     * return these tracked conditions to the client that can use them to know that the context server can react to events matching the tracked condition and coming from that
     * source. This is, in particular, used to implement form mapping (a solution that allows clients to update user profiles based on values provided when a form is submitted).
     *
     * TODO: trackedCondition should be a constant, possibly on the Tag class?
     *
     * @return the tracked conditions
     * @see ContextRequest#getSource()
     * @see RulesService#getTrackedConditions(Item)
     */
    public Set<Condition> getTrackedConditions() {
        return trackedConditions;
    }

    /**
     * Sets the tracked conditions.
     *
     * @param trackedConditions the tracked conditions
     */
    public void setTrackedConditions(Set<Condition> trackedConditions) {
        this.trackedConditions = trackedConditions;
    }

    /**
     * Retrieves the current status of anonymous browsing, as set by the privacy service
     * @return anonymous browsing status
     */
    public boolean isAnonymousBrowsing() {
        return anonymousBrowsing;
    }

    /**
     * Set the user anonymous browsing status
     * @param anonymousBrowsing new value for anonymousBrowsing
     */
    public void setAnonymousBrowsing(boolean anonymousBrowsing) {
        this.anonymousBrowsing = anonymousBrowsing;
    }

    /**
     * Retrieves the map of consents for the current profile.
     * @return a Map where the key is the name of the consent identifier, and the value is a consent object that
     * contains all the consent data such as whether the consent was granted or deny, the date of granting/denying
     * the date at which the consent will be revoked automatically.
     */
    public Map<String, Consent> getConsents() {
        return consents;
    }

    /**
     * Sets the map of consents for the current profile.
     * @param consents a Map where the key is the name of the consent identifier, and the value is a consent object that
     * contains all the consent data such as whether the consent was granted or deny, the date of granting/denying
     * the date at which the consent will be revoked automatically.
     */
    public void setConsents(Map<String, Consent> consents) {
        this.consents = consents;
    }
}
