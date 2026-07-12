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
 * JSON body accepted by the events collector REST endpoint.
 * Bundles one or more {@link Event} instances plus optional {@code sessionId}
 * and {@code profileId} hints so Unomi can attach incoming events to the
 * correct session and profile before rules run and persistence writes occur.
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
     * Events submitted for collection and rule evaluation.
     *
     * @return the event list
     */
    public List<Event> getEvents() {
        return events;
    }

    /**
     * Sets the events to collect and process.
     *
     * @param events the events to submit
     */
    public void setEvents(List<Event> events) {
        this.events = events;
    }

    /**
     * Default session id applied to all events in this request when an event does not specify its own.
     *
     * @return the session id, or {@code null} if none was provided
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Sets the default session id for events in this request.
     * Prefer this over passing the session id in the URL to avoid leaking identifiers in logs or referrers.
     *
     * @param sessionId the session id
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Default profile id applied to all events in this request when an event does not specify its own.
     *
     * @return the profile id, or {@code null} if none was provided
     */
    public String getProfileId() {
        return profileId;
    }

    /**
     * Sets the default profile id for events in this request.
     *
     * @param profileId the profile id
     */
    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    /**
     * Public API key used for tenant authentication.
     *
     * @return the public API key, or {@code null} if none was provided
     */
    public String getPublicApiKey() {
        return publicApiKey;
    }

    /**
     * Sets the public API key used for tenant authentication.
     *
     * @param publicApiKey the public API key
     */
    public void setPublicApiKey(String publicApiKey) {
        this.publicApiKey = publicApiKey;
    }
}
