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

package org.apache.unomi.web;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Persona;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.PrivacyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;

/**
 * @author dgaillard
 */
public class ServletCommon {
    private static final Logger logger = LoggerFactory.getLogger(ServletCommon.class.getName());

    public static String getProfileIdCookieValue(HttpServletRequest httpServletRequest, String profileIdCookieName) {
        String cookieProfileId = null;

        Cookie[] cookies = httpServletRequest.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (profileIdCookieName.equals(cookie.getName())) {
                    cookieProfileId = cookie.getValue();
                }
            }
        }

        return cookieProfileId;
    }

    public static Changes handleEvents(List<Event> events, Session session, Profile profile,
                                    ServletRequest request, ServletResponse response, Date timestamp,
                                    PrivacyService privacyService, EventService eventService) {
        List<String> filteredEventTypes = privacyService.getFilteredEventTypes(profile);

        String thirdPartyId = eventService.authenticateThirdPartyServer(((HttpServletRequest) request).getHeader("X-Unomi-Peer"),
                request.getRemoteAddr());

        int changes = EventService.NO_CHANGE;
        // execute provided events if any
        if (events != null && !(profile instanceof Persona)) {
            for (Event event : events) {
                if (event.getEventType() != null) {
                    Event eventToSend = new Event(event.getEventType(), session, profile, event.getScope(), event.getSource(),
                            event.getTarget(), event.getProperties(), timestamp, event.isPersistent());
                    if (!eventService.isEventAllowed(event, thirdPartyId)) {
                        logger.warn("Event is not allowed : {}", event.getEventType());
                        continue;
                    }
                    if (filteredEventTypes != null && filteredEventTypes.contains(event.getEventType())) {
                        logger.debug("Profile is filtering event type {}", event.getEventType());
                        continue;
                    }
                    if (profile.isAnonymousProfile()) {
                        // Do not keep track of profile in event
                        eventToSend.setProfileId(null);
                    }

                    eventToSend.getAttributes().put(Event.HTTP_REQUEST_ATTRIBUTE, request);
                    eventToSend.getAttributes().put(Event.HTTP_RESPONSE_ATTRIBUTE, response);
                    logger.debug("Received event " + event.getEventType() + " for profile=" + profile.getItemId() + " session="
                            + (session!= null?session.getItemId():null) + " target=" + event.getTarget() + " timestamp=" + timestamp);
                    changes = eventService.send(eventToSend);
                    // If the event execution changes the profile we need to update it so the next event use the right profile
                    if ((changes & EventService.PROFILE_UPDATED) == EventService.PROFILE_UPDATED) {
                        profile = eventToSend.getProfile();
                    }
                }
            }
        }

        return new Changes(changes, profile);
    }
}
