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

package org.apache.unomi.rest.service;

import org.apache.unomi.api.Event;
import org.apache.unomi.utils.EventsRequestContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.List;

/**
 * Utility service for Public REST endpoints
 */
public interface RestServiceUtils {
    /**
     * This method is used to initialize the context for a request that would require executing events.
     *
     * It will load existing profile/session for current user or build new ones if necessary
     * IT will also handle anonymous profile/session preferences in case there is specific ones.
     * It will also handle persona in case it is provided
     * And finally it will provide a contextual bean named: EventsRequestContext,
     * that will contain all the required information for the next steps of the request, like: processing the events
     *
     * @param scope the current scope (mandatory, in case session need to be created)
     * @param sessionId the current sessionId (mandatory)
     * @param profileId the current profileId (optional in case profile doesn't exists yet for incoming visitor)
     * @param personaId the current personaId (optional in case we don't want to apply persona on current request)
     * @param invalidateProfile true in case we want to invalidate the current visitor profile, false otherwise
     * @param invalidateSession true in case we want to invalidate the current visitor session, false otherwise
     * @param request the current request
     * @param response the current request response
     * @param timestamp the current date, for timestamp the current visitor data
     *
     * @return the built EventsRequestContext
     */
    EventsRequestContext initEventsRequest(String scope, String sessionId, String profileId, String personaId,
                                           boolean invalidateProfile, boolean invalidateSession,
                                           HttpServletRequest request, HttpServletResponse response,
                                           Date timestamp);

    /**
     * Execute the list of events using the dedicated eventsRequestContext
     * @param events the list of events to he executed
     * @param eventsRequestContext the current EventsRequestContext
     * @return an updated version of the current eventsRequestContext
     */
    EventsRequestContext performEventsRequest(List<Event> events, EventsRequestContext eventsRequestContext);

    /**
     * At the end of an events requests we want to save/update the profile and/or the session depending on the changes
     * Also we want to return a cookie about current visitor profile ID
     *
     * @param eventsRequestContext the current EventsRequestContext
     * @param crashOnError true if we want to throw an Exception in case of errors during events execution,
     *                     false otherwise (otherwise, no exception, but just an error code directly returned to the HTTP response)
     */
    void finalizeEventsRequest(EventsRequestContext eventsRequestContext, boolean crashOnError);

    /**
     * Try to extract the current visitor profileId from the current request cookies.
     * @param httpServletRequest the current HTTP request
     * @return the profileId if found in the cookies, null otherwise
     */
    String getProfileIdCookieValue(HttpServletRequest httpServletRequest);
}
