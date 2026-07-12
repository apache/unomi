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

import java.util.List;

/**
 * Payload sent to the events collector endpoint.
 * Wraps one or more {@link Event} instances that a client wants Unomi to
 * process, evaluate against rules, and persist.
 */
public class EventsCollectorRequest {

    private List<Event> events;

    private String sessionId;

    private String profileId;

    /**
     * The public API key for tenant authentication.
     */
    private String publicApiKey;

    /**
     * Retrieves the events to be processed.
     * @return the events to be processed
     */
    public List<Event> getEvents() {
        return events;
    }

    /**
     * Sets the list of events to be processed by this request.
     * @param events a list containing all events that should be
     * collected and processed.
     */
    public void setEvents(List<Event> events) {
        this.events = events;
    }

    /**
     * Retrieve the sessionId passed along with the request. All events will be processed with this sessionId as a
     * default
     * @return the identifier for the session
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Sets the sessionId in the request. This is the preferred method of passing along a session identifier with the
     * request, as passing it along in the URL can lead to potential security vulnerabilities.
     * @param sessionId an unique identifier for the session
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Retrieve the profileId passed along with the request. All events will be processed with this profileId as a
     * default
     * @return the identifier for the profile
     */
    public String getProfileId() {
        return profileId;
    }

    /**
     * Sets the profileId in the request.
     * @param profileId an unique identifier for the profile
     */
    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    /**
     * Gets the public API key used for tenant authentication.
     * @return the public API key
     */
    public String getPublicApiKey() {
        return publicApiKey;
    }

    /**
     * Sets the public API key used for tenant authentication.
     * @param publicApiKey the public API key to set
     */
    public void setPublicApiKey(String publicApiKey) {
        this.publicApiKey = publicApiKey;
    }
}
