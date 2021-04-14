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

import org.apache.unomi.api.utils.ValidationPattern;

import javax.validation.constraints.Pattern;
import java.util.List;

/**
 * A request for events to be processed.
 */
public class EventsCollectorRequest {
    private List<Event> events;


    @Pattern(regexp = ValidationPattern.PATTERN)
    private String sessionId;

    /**
     * Retrieves the events to be processed.
     *
     * @return the events to be processed
     */
    public List<Event> getEvents() {
        return events;
    }

    public void setEvents(List<Event> events) {
        this.events = events;
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

}
