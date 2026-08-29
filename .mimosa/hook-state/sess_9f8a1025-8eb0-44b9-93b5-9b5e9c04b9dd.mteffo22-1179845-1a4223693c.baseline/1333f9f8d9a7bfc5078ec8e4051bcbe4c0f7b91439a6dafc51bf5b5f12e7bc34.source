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
import org.apache.unomi.tracing.api.TraceNode;

import java.io.Serializable;
import java.util.*;

/**
 * A context server response resulting from the evaluation of a client's context request. Note that all returned values result of the evaluation of the data provided in the
 * associated ContextRequest and might therefore reflect results due to user impersonation via properties / segment overrides.
 *
 * @see ContextRequest
 */
public class ContextResponse implements Serializable {

    private static final long serialVersionUID = -5638595408986826332L;

    /**
     * Resolved profile id for this evaluation.
     * @api.example profile-1
     */
    private String profileId;

    /**
     * Resolved session id for this evaluation (may be omitted when no session exists).
     * @api.example session-1
     */
    private String sessionId;

    /**
     * Profile properties subset requested via {@link ContextRequest#getRequiredProfileProperties()}.
     * @api.example {"firstName":"Ada"}
     */
    private Map<String, Object> profileProperties;

    /**
     * Session properties subset requested via {@link ContextRequest#getRequiredSessionProperties()}.
     * @api.example {"utm_source":"newsletter"}
     */
    private Map<String, Object> sessionProperties;

    /**
     * Segment ids for the profile when {@link ContextRequest#isRequireSegments()} was true.
     * @api.example ["vip","returning"]
     */
    private Set<String> profileSegments;

    /**
     * Scoring plan id → score when {@link ContextRequest#isRequireScores()} was true.
     * @api.example {"engagement":12}
     */
    private Map<String,Integer> profileScores;

    /**
     * Filter id → whether the profile matched that content filter.
     * @api.example {"hero-banner":true}
     */
    private Map<String, Boolean> filteringResults;

    /**
     * Number of events from the request that were processed.
     * @api.example 1
     */
    private int processedEvents;

    /**
     * Legacy personalization id → selected content ids.
     * Prefer {@link #personalizationResults} since 2.1.0.
     * @api.example {"hero":["variant-a"]}
     */
    private Map<String, List<String>> personalizations;

    /**
     * Personalization id → resolution result (content ids, scores, filters).
     * Prefer this over the legacy {@link #personalizations} map.
     */
    private Map<String, PersonalizationResult> personalizationResults;

    /**
     * Tracked conditions clients should watch for (for example form field mapping rules).
     */
    private Set<Condition> trackedConditions;

    /**
     * {@code true} when privacy requires anonymous browsing for this profile.
     * @api.example false
     */
    private boolean anonymousBrowsing;

    /**
     * Consent map for the profile, keyed by consent identifier.
     * @api.example {"newsletter":{"scope":"mysite","typeIdentifier":"newsletter","status":"GRANTED"}}
     */
    private Map<String, Consent> consents = new LinkedHashMap<>();

    /**
     * Present only when {@code explain=true} and the caller is an administrator / tenant administrator.
     */
    private TraceNode requestTracing;

    /**
     * Profile identifier for the user on whose behalf the context request was made.
     *
     * @return the profile identifier
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
     * Session identifier for the processed request.
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
     * Profile properties requested by the client.
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
     * Session properties requested by the client.
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
     * Profile segment identifiers for the user if they were requested by the client. Note that these segments are evaluated taking potential
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
     * Profile scores when requested via {@link ContextRequest#isRequireScores()}.
     *
     * @return map of scoring identifier to score value
     */
    public Map<String, Integer> getProfileScores() {
        return profileScores;
    }

    /**
     * Sets profile scores for the response.
     *
     * @param profileScores map of scoring identifier to score value
     */
    public void setProfileScores(Map<String, Integer> profileScores) {
        this.profileScores = profileScores;
    }

    /**
     * Content filtering evaluation results and whether individual definitions match with the associated profile (potentially modified by
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


    /**
     * Number of events processed in this request.
     *
     * @return the processed event count
     */
    public int getProcessedEvents() {
        return processedEvents;
    }

    /**
     * Sets the number of processed events.
     *
     * @param processedEvents the count
     */
    public void setProcessedEvents(int processedEvents) {
        this.processedEvents = processedEvents;
    }

    /**
     * @deprecated Personalization results are more complex since 2.1.0; use {@link #getPersonalizationResults()} instead.
     *
     * @return the legacy personalization results map
     */
    @Deprecated
    public Map<String, List<String>> getPersonalizations() {
        return personalizations;
    }

    /**
     * @deprecated Personalization results are more complex since 2.1.0; use {@link #setPersonalizationResults(Map)} instead.
     *
     * @param personalizations the legacy personalization results
     */
    @Deprecated
    public void setPersonalizations(Map<String, List<String>> personalizations) {
        this.personalizations = personalizations;
    }

    /**
     * Personalization resolution results from the context request.
     *
     * @return map of personalization id to resolution result
     */
    public Map<String, PersonalizationResult> getPersonalizationResults() {
        return personalizationResults;
    }

    /**
     * Sets the personalization results.
     *
     * @param personalizationResults the results map
     */
    public void setPersonalizationResults(Map<String, PersonalizationResult> personalizationResults) {
        this.personalizationResults = personalizationResults;
    }

    /**
     * Tracked conditions associated with the request source.
     * <p>
     * Rules tagged with {@code trackedCondition} whose source condition matches the incoming
     * request source are returned so clients can emit matching events (for example form mapping).
     *
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
     * Whether anonymous browsing is enabled, as set by the privacy service.
     *
     * @return {@code true} if anonymous browsing is active
     */
    public boolean isAnonymousBrowsing() {
        return anonymousBrowsing;
    }

    /**
     * Sets the anonymous browsing status.
     *
     * @param anonymousBrowsing {@code true} to enable anonymous browsing
     */
    public void setAnonymousBrowsing(boolean anonymousBrowsing) {
        this.anonymousBrowsing = anonymousBrowsing;
    }

    /**
     * Consent map for the current profile, keyed by consent identifier.
     *
     * @return map of consent identifier to consent details
     */
    public Map<String, Consent> getConsents() {
        return consents;
    }

    /**
     * Sets the consent map for the current profile.
     *
     * @param consents map of consent identifier to consent details
     */
    public void setConsents(Map<String, Consent> consents) {
        this.consents = consents;
    }

    /**
     * Request tracing tree for this context evaluation.
     *
     * @return the request tracing data
     */
    public TraceNode getRequestTracing() {
        return requestTracing;
    }

    /**
     * Sets the request tracing data.
     *
     * @param requestTracing the tracing node
     */
    public void setRequestTracing(TraceNode requestTracing) {
        this.requestTracing = requestTracing;
    }
}
