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

import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.services.EventListenerService;
import org.apache.unomi.api.services.PersonalizationService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.services.RulesService;
import org.apache.unomi.api.utils.ValidationPattern;

import javax.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;

/**
 * An incoming request for context information from clients of the context server. This allows clients to specify which type of information they are interested in getting from
 * the context server as well as specify incoming events or content filtering or property/segment overrides for personalization or impersonation. This conditions what the
 * context server will return with its response.
 * <p>
 * Events that are generated on the client as part of its functioning can be specified in the client as part of its request for contextual data. The context server will deliver
 * these events to {@link EventListenerService}s to be processed. In particular, the {@link
 * RulesService} will trigger any applicable {@link Rule} which in turn might trigger {@link
 * Action}s. An appropriate Event is also triggered when a Rule matches so that other rules can react to it. Finally, an event will also
 * be emitted if the user {@link Profile} has been updated as part of the event processing.
 * <p>
 * A client wishing to perform content personalization might also specify filtering condition to be evaluated by the context server so that it can tell the client
 * whether the content associated with the filter should be activated for this profile/session.
 * <p>
 * It is also possible to clients wishing to perform user impersonation to specify properties or segment to override the proper ones so as to emulate a specific profile, in
 * which case the overridden value will temporarily replace the proper values so that all rules will be evaluated with these values instead of the proper ones.
 *
 * @see ContextResponse
 * @see Event
 */

public class ContextRequest {

    private Item source;
    private boolean requireSegments;
    private List<String> requiredProfileProperties;
    private List<String> requiredSessionProperties;
    private boolean requireScores;
    private List<Event> events;
    private List<PersonalizationService.PersonalizedContent> filters;
    private List<PersonalizationService.PersonalizationRequest> personalizations;

    // the following overrides make it possible to override temporarily the current profile segments, properties or
    // even session properties. This is useful for building UIs to temporarily override one of these parameters to
    // test different filter results.
    private Profile profileOverrides;
    private Map<String, Object> sessionPropertiesOverrides;

    @Pattern(regexp = ValidationPattern.TEXT_VALID_CHARACTERS_PATTERN)
    private String sessionId;

    @Pattern(regexp = ValidationPattern.TEXT_VALID_CHARACTERS_PATTERN)
    private String profileId;

    private String clientId;

    /**
     * Retrieves the source of the context request.
     *
     * @return the source
     */
    public Item getSource() {
        return source;
    }

    /**
     * Sets the source.
     *
     * @param source the source
     */
    public void setSource(Item source) {
        this.source = source;
    }

    /**
     * Determines whether or not the context server should return the segments associated with the profile from which the request was issued.
     *
     * @return {@code true} if the context server should return the profile segments, {@code false otherwise}
     * @see ContextResponse#getProfileSegments()
     */
    public boolean isRequireSegments() {
        return requireSegments;
    }

    /**
     * Specifies whether to return the profile segments with the response.
     *
     * @param requireSegments {@code true} if the context server should return the profile segments, {@code false otherwise}
     */
    public void setRequireSegments(boolean requireSegments) {
        this.requireSegments = requireSegments;
    }

    /**
     * Retrieves the list of profile properties the context server should return with its context response.
     *
     * @return the required profile properties the client requested to be returned with the response
     * @see ContextResponse#getProfileProperties()
     */
    public List<String> getRequiredProfileProperties() {
        return requiredProfileProperties;
    }

    /**
     * Specifies which profile properties should be returned with the response.
     *
     * @param requiredProfileProperties the profile properties that should be returned with the response
     */
    public void setRequiredProfileProperties(List<String> requiredProfileProperties) {
        this.requiredProfileProperties = requiredProfileProperties;
    }

    /**
     * Retrieves the list of session properties the context server should return with its context response.
     *
     * @return the required session properties the client requested to be returned with the response
     * @see ContextResponse#getSessionProperties()
     */
    public List<String> getRequiredSessionProperties() {
        return requiredSessionProperties;
    }

    /**
     * Specifies which session properties should be returned with the response.
     *
     * @param requiredSessionProperties the session properties that should be returned with the response
     */
    public void setRequiredSessionProperties(List<String> requiredSessionProperties) {
        this.requiredSessionProperties = requiredSessionProperties;
    }

    /**
     * Specifies whether the profiles scores should be part of the ContextResponse.
     * @return a boolean indicating if the scores should be part of the response.
     */
    public boolean isRequireScores() {
        return requireScores;
    }

    /**
     * Setting this value to true indicates that the profile scores should be included in the response. By default this
     * value is false.
     * @param requireScores set to true if you want the scores to be part of the context response
     */
    public void setRequireScores(boolean requireScores) {
        this.requireScores = requireScores;
    }

    /**
     * Retrieves the filters aimed at content personalization that should be evaluated for the given session and/or profile so that the context server can tell the client
     * whether the content associated with the filter should be activated for this profile/session. The filter identifier is used in the {@link ContextResponse} with the
     * associated evaluation result.
     *
     * @return the filters aimed at content personalization that should be evaluated for the given session and/or profile
     * @see ProfileService#matchCondition(Condition, Profile, Session) Details on how the filter conditions are evaluated
     * @see ContextResponse#getFilteringResults() Details on how the evaluation results are returned to the client
     */
    public List<PersonalizationService.PersonalizedContent> getFilters() {
        return filters;
    }

    /**
     * Specifies the content filters to be evaluated.
     *
     * @param filters the content filters to be evaluated
     */
    public void setFilters(List<PersonalizationService.PersonalizedContent> filters) {
        this.filters = filters;
    }

    public List<PersonalizationService.PersonalizationRequest> getPersonalizations() {
        return personalizations;
    }

    public void setPersonalizations(List<PersonalizationService.PersonalizationRequest> personalizations) {
        this.personalizations = personalizations;
    }

    /**
     * Retrieves the events that the client has generated as part of its processes and wishes the context server to process.
     *
     * @return the client events to be processed by the context server
     * @see Event
     */
    public List<Event> getEvents() {
        return events;
    }

    /**
     * Specifies the events to be processed by the context server.
     *
     * @param events the events to be processed by the context server
     */
    public void setEvents(List<Event> events) {
        this.events = events;
    }

    /**
     * Retrieves the profile overrides.
     *
     * @return the profile overrides
     */
    public Profile getProfileOverrides() {
        return profileOverrides;
    }

    /**
     * Sets the profile overrides.
     *
     * @param overrides the profile overrides
     */
    public void setProfileOverrides(Profile overrides) {
        this.profileOverrides = overrides;
    }

    /**
     * Retrieves the session properties overrides.
     *
     * @return the session properties overrides
     */
    public Map<String, Object> getSessionPropertiesOverrides() {
        return sessionPropertiesOverrides;
    }

    /**
     * Sets the session properties overrides.
     *
     * @param sessionPropertiesOverrides the session properties overrides
     */
    public void setSessionPropertiesOverrides(Map<String, Object> sessionPropertiesOverrides) {
        this.sessionPropertiesOverrides = sessionPropertiesOverrides;
    }

    /**
     * Retrieve the sessionId passed along with the request. All events will be processed with this sessionId as a
     * default
     *
     * @return the identifier for the session
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Sets the sessionId in the request. This is the preferred method of passing along a session identifier with the
     * request, as passing it along in the URL can lead to potential security vulnerabilities.
     *
     * @param sessionId an unique identifier for the session
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Retrieve the profileId passed along with the request. All events will be processed with this profileId as a
     * default
     *
     * @return the identifier for the profile
     */
    public String getProfileId() {
        return profileId;
    }

    /**
     * Sets the profileId in the request.
     *
     * @param profileId an unique identifier for the profile
     */
    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
}
