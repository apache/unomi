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
package org.apache.unomi.rest.service.impl;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Persona;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.services.ConfigSharingService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.PrivacyService;
import org.apache.unomi.rest.service.RestServiceUtils;
import org.apache.unomi.rest.validation.BeanValidationService;
import org.apache.unomi.rest.validation.wrapper.CookieWrapper;
import org.apache.unomi.utils.Changes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;

@Component(service = RestServiceUtils.class)
public class RestServiceUtilsImpl implements RestServiceUtils {

    private static final Logger logger = LoggerFactory.getLogger(RestServiceUtilsImpl.class.getName());

    @Reference
    private ConfigSharingService configSharingService;

    @Reference
    private BeanValidationService localBeanValidationProvider;

    @Reference
    private PrivacyService privacyService;

    @Reference
    private EventService eventService;

    public String getProfileIdCookieValue(HttpServletRequest httpServletRequest) {
        String cookieProfileId = null;

        Cookie[] cookies = httpServletRequest.getCookies();

        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (configSharingService.getProperty("profileIdCookieName").equals(cookie.getName())) {
                    localBeanValidationProvider.getBeanValidationProvider().validateBean(new CookieWrapper(cookie.getValue()));
                    cookieProfileId = cookie.getValue();
                }
            }
        }
        return cookieProfileId;
    }

    @Override
    public Changes handleEvents(List<Event> events, Session session, Profile profile, ServletRequest request, ServletResponse response,
            Date timestamp) {
        List<String> filteredEventTypes = privacyService.getFilteredEventTypes(profile);

        String thirdPartyId = eventService
                .authenticateThirdPartyServer(((HttpServletRequest) request).getHeader("X-Unomi-Peer"), request.getRemoteAddr());

        int changes = EventService.NO_CHANGE;
        // execute provided events if any
        int processedEventsCnt = 0;
        if (events != null && !(profile instanceof Persona)) {
            for (Event event : events) {
                processedEventsCnt++;
                if (event.getEventType() != null) {
                    if (!eventService.isEventValid(event)) {
                        logger.warn("Event is not valid : {}", event.getEventType());
                        continue;
                    }

                    Event eventToSend = new Event(event.getEventType(), session, profile, event.getSourceId(), event.getSource(),
                            event.getTarget(), event.getProperties(), timestamp, event.isPersistent());
                    if (!eventService.isEventAllowed(event, thirdPartyId)) {
                        logger.warn("Event is not allowed : {}", event.getEventType());
                        continue;
                    }
                    if (thirdPartyId != null && event.getItemId() != null) {
                        eventToSend = new Event(event.getItemId(), event.getEventType(), session, profile, event.getSourceId(),
                                event.getSource(), event.getTarget(), event.getProperties(), timestamp, event.isPersistent());
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
                    logger.debug("Received event " + event.getEventType() + " for profile=" + profile.getItemId() + " session=" + (
                            session != null ? session.getItemId() : null) + " target=" + event.getTarget() + " timestamp=" + timestamp);
                    changes |= eventService.send(eventToSend);
                    // If the event execution changes the profile we need to update it so the next event use the right profile
                    if ((changes & EventService.PROFILE_UPDATED) == EventService.PROFILE_UPDATED) {
                        profile = eventToSend.getProfile();
                    }
                    if ((changes & EventService.ERROR) == EventService.ERROR) {
                        //Don't count the event that failed
                        processedEventsCnt--;
                        logger.error("Error processing events. Total number of processed events: {}/{}", processedEventsCnt, events.size());
                        break;
                    }
                }
            }
        }
        return new Changes(changes, processedEventsCnt, profile);
    }
}
