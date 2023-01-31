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

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.*;
import org.apache.unomi.api.services.ConfigSharingService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.PrivacyService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.rest.exception.InvalidRequestException;
import org.apache.unomi.rest.service.RestServiceUtils;
import org.apache.unomi.schema.api.SchemaService;
import org.apache.unomi.utils.HttpUtils;
import org.apache.unomi.utils.EventsRequestContext;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.BadRequestException;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component(service = RestServiceUtils.class)
public class RestServiceUtilsImpl implements RestServiceUtils {

    private static final String DEFAULT_CLIENT_ID = "defaultClientId";

    private static final Logger logger = LoggerFactory.getLogger(RestServiceUtilsImpl.class.getName());

    @Reference
    private ConfigSharingService configSharingService;

    @Reference
    private PrivacyService privacyService;

    @Reference
    private EventService eventService;

    @Reference
    private ProfileService profileService;

    @Reference
    SchemaService schemaService;

    @Override
    public String getProfileIdCookieValue(HttpServletRequest httpServletRequest) {
        String cookieProfileId = null;

        Cookie[] cookies = httpServletRequest.getCookies();

        if (cookies != null) {
            final Object profileIdCookieName = configSharingService.getProperty("profileIdCookieName");
            for (Cookie cookie : cookies) {
                if (profileIdCookieName.equals(cookie.getName())) {
                    String profileIdJSON = JsonNodeFactory.instance.objectNode().put("profileId", cookie.getValue()).toString();
                    if (!schemaService.isValid(profileIdJSON, "https://unomi.apache.org/schemas/json/rest/requestIds/1-0-0")) {
                        throw new InvalidRequestException("Invalid profile ID format in cookie", "Invalid received data");
                    }
                    cookieProfileId = cookie.getValue();
                }
            }
        }
        return cookieProfileId;
    }

    @Override
    public EventsRequestContext initEventsRequest(String scope, String sessionId, String profileId, String personaId,
                                                  boolean invalidateProfile, boolean invalidateSession,
                                                  HttpServletRequest request, HttpServletResponse response, Date timestamp) {

        // Build context
        EventsRequestContext eventsRequestContext = new EventsRequestContext(timestamp, null, null, request, response);

        // Handle persona
        if (personaId != null) {
            PersonaWithSessions personaWithSessions = profileService.loadPersonaWithSessions(personaId);
            if (personaWithSessions == null) {
                logger.error("Couldn't find persona, please check your personaId parameter");
            } else {
                eventsRequestContext.setProfile(personaWithSessions.getPersona());
                eventsRequestContext.setSession(personaWithSessions.getLastSession());
            }
        }

        if (profileId == null) {
            // Get profile id from the cookie
            profileId = getProfileIdCookieValue(request);
        }

        if (profileId == null && sessionId == null && personaId == null) {
            logger.error("Couldn't find profileId, sessionId or personaId in incoming request! Stopped processing request. See debug level for more information");
            if (logger.isDebugEnabled()) {
                logger.debug("Request dump: {}", HttpUtils.dumpRequestInfo(request));
            }
            throw new BadRequestException("Couldn't find profileId, sessionId or personaId in incoming request!");
        }

        boolean profileCreated = false;
        if (eventsRequestContext.getProfile() == null) {
            if (profileId == null || invalidateProfile) {
                // no profileId cookie was found or the profile has to be invalidated, we generate a new one and create the profile in the profile service
                eventsRequestContext.setProfile(createNewProfile(null, timestamp));
                profileCreated = true;
            } else {
                eventsRequestContext.setProfile(profileService.load(profileId));
                if (eventsRequestContext.getProfile() == null) {
                    // this can happen if we have an old cookie but have reset the server,
                    // or if we merged the profiles and somehow this cookie didn't get updated.
                    eventsRequestContext.setProfile(createNewProfile(profileId, timestamp));
                    profileCreated = true;
                }
            }

            // Try to recover existing session
            Profile sessionProfile;
            if (StringUtils.isNotBlank(sessionId) && !invalidateSession) {

                eventsRequestContext.setSession(profileService.loadSession(sessionId));
                if (eventsRequestContext.getSession() != null) {

                    sessionProfile = eventsRequestContext.getSession().getProfile();
                    boolean anonymousSessionProfile = sessionProfile.isAnonymousProfile();
                    if (!eventsRequestContext.getProfile().isAnonymousProfile() &&
                            !anonymousSessionProfile &&
                            !eventsRequestContext.getProfile().getItemId().equals(sessionProfile.getItemId())) {
                        // Session user has been switched, profile id in cookie is not up to date
                        // We must reload the profile with the session ID as some properties could be missing from the session profile
                        // #personalIdentifier
                        eventsRequestContext.setProfile(profileService.load(sessionProfile.getItemId()));
                    }

                    // Handle anonymous situation
                    Boolean requireAnonymousBrowsing = privacyService.isRequireAnonymousBrowsing(eventsRequestContext.getProfile());
                    if (requireAnonymousBrowsing && anonymousSessionProfile) {
                        // User wants to browse anonymously, anonymous profile is already set.
                    } else if (requireAnonymousBrowsing && !anonymousSessionProfile) {
                        // User wants to browse anonymously, update the sessionProfile to anonymous profile
                        sessionProfile = privacyService.getAnonymousProfile(eventsRequestContext.getProfile());
                        eventsRequestContext.getSession().setProfile(sessionProfile);
                        eventsRequestContext.addChanges(EventService.SESSION_UPDATED);
                    } else if (!requireAnonymousBrowsing && anonymousSessionProfile) {
                        // User does not want to browse anonymously anymore, update the sessionProfile to real profile
                        sessionProfile = eventsRequestContext.getProfile();
                        eventsRequestContext.getSession().setProfile(sessionProfile);
                        eventsRequestContext.addChanges(EventService.SESSION_UPDATED);
                    } else if (!requireAnonymousBrowsing && !anonymousSessionProfile) {
                        // User does not want to browse anonymously, use the real profile. Check that session contains the current profile.
                        sessionProfile = eventsRequestContext.getProfile();
                        if (!eventsRequestContext.getSession().getProfileId().equals(sessionProfile.getItemId())) {
                            eventsRequestContext.addChanges(EventService.SESSION_UPDATED);
                        }
                        eventsRequestContext.getSession().setProfile(sessionProfile);
                    }
                }
            }

            // Try to create new session
            if (eventsRequestContext.getSession() == null || invalidateSession) {
                sessionProfile = privacyService.isRequireAnonymousBrowsing(eventsRequestContext.getProfile()) ?
                        privacyService.getAnonymousProfile(eventsRequestContext.getProfile()) : eventsRequestContext.getProfile();

                if (StringUtils.isNotBlank(sessionId)) {
                    // Only save session and send event if a session id was provided, otherwise keep transient session

                    Session session = new Session(sessionId, sessionProfile, timestamp, scope);
                    eventsRequestContext.setSession(session);
                    eventsRequestContext.setNewSession(true);
                    eventsRequestContext.addChanges(EventService.SESSION_UPDATED);
                    Event event = new Event("sessionCreated", eventsRequestContext.getSession(), eventsRequestContext.getProfile(),
                            scope, null, eventsRequestContext.getSession(), null, timestamp, false);
                    if (sessionProfile.isAnonymousProfile()) {
                        // Do not keep track of profile in event
                        event.setProfileId(null);
                    }
                    event.getAttributes().put(Event.HTTP_REQUEST_ATTRIBUTE, request);
                    event.getAttributes().put(Event.HTTP_RESPONSE_ATTRIBUTE, response);
                    if (logger.isDebugEnabled()) {
                        logger.debug("Received event {} for profile={} session={} target={} timestamp={}", event.getEventType(),
                                eventsRequestContext.getProfile().getItemId(), eventsRequestContext.getSession().getItemId(), event.getTarget(), timestamp);
                    }
                    eventsRequestContext.addChanges(eventService.send(event));
                }
            }

            // Handle new profile creation
            if (profileCreated) {
                eventsRequestContext.addChanges(EventService.PROFILE_UPDATED);

                Event profileUpdated = new Event("profileUpdated", eventsRequestContext.getSession(), eventsRequestContext.getProfile(),
                        scope, null, eventsRequestContext.getProfile(), timestamp);
                profileUpdated.setPersistent(false);
                profileUpdated.getAttributes().put(Event.HTTP_REQUEST_ATTRIBUTE, request);
                profileUpdated.getAttributes().put(Event.HTTP_RESPONSE_ATTRIBUTE, response);
                profileUpdated.getAttributes().put(Event.CLIENT_ID_ATTRIBUTE, DEFAULT_CLIENT_ID);

                if (logger.isDebugEnabled()) {
                    logger.debug("Received event {} for profile={} {} target={} timestamp={}", profileUpdated.getEventType(),
                            eventsRequestContext.getProfile().getItemId(),
                            " session=" + (eventsRequestContext.getSession() != null ? eventsRequestContext.getSession().getItemId() : null),
                            profileUpdated.getTarget(), timestamp);
                }
                eventsRequestContext.addChanges(eventService.send(profileUpdated));
            }
        }

        return eventsRequestContext;
    }

    @Override
    public EventsRequestContext performEventsRequest(List<Event> events, EventsRequestContext eventsRequestContext) {
        List<String> filteredEventTypes = privacyService.getFilteredEventTypes(eventsRequestContext.getProfile());
        String thirdPartyId = eventService.authenticateThirdPartyServer(eventsRequestContext.getRequest().getHeader("X-Unomi-Peer"),
                eventsRequestContext.getRequest().getRemoteAddr());

        // execute provided events if any
        if (events != null && !(eventsRequestContext.getProfile() instanceof Persona)) {
            // set Total items on context
            eventsRequestContext.setTotalItems(events.size());

            for (Event event : events) {
                eventsRequestContext.setProcessedItems(eventsRequestContext.getProcessedItems() + 1);

                if (event.getEventType() != null) {
                    Event eventToSend = new Event(event.getEventType(), eventsRequestContext.getSession(), eventsRequestContext.getProfile(), event.getScope(), event.getSource(),
                            event.getTarget(), event.getProperties(), eventsRequestContext.getTimestamp(), event.isPersistent());
                    eventToSend.setFlattenedProperties(event.getFlattenedProperties());
                    if (!eventService.isEventAllowed(event, thirdPartyId)) {
                        logger.warn("Event is not allowed : {}", event.getEventType());
                        continue;
                    }
                    if (thirdPartyId != null && event.getItemId() != null) {
                        eventToSend = new Event(event.getItemId(), event.getEventType(), eventsRequestContext.getSession(), eventsRequestContext.getProfile(), event.getScope(),
                                event.getSource(), event.getTarget(), event.getProperties(), eventsRequestContext.getTimestamp(), event.isPersistent());
                        eventToSend.setFlattenedProperties(event.getFlattenedProperties());
                    }
                    if (filteredEventTypes != null && filteredEventTypes.contains(event.getEventType())) {
                        logger.debug("Profile is filtering event type {}", event.getEventType());
                        continue;
                    }
                    if (eventsRequestContext.getProfile().isAnonymousProfile()) {
                        // Do not keep track of profile in event
                        eventToSend.setProfileId(null);
                    }

                    eventToSend.getAttributes().put(Event.HTTP_REQUEST_ATTRIBUTE, eventsRequestContext.getRequest());
                    eventToSend.getAttributes().put(Event.HTTP_RESPONSE_ATTRIBUTE, eventsRequestContext.getResponse());
                    logger.debug("Received event " + event.getEventType() + " for profile=" + eventsRequestContext.getProfile().getItemId() + " session=" + (
                            eventsRequestContext.getSession() != null ? eventsRequestContext.getSession().getItemId() : null) +
                            " target=" + event.getTarget() + " timestamp=" + eventsRequestContext.getTimestamp());
                    eventsRequestContext.addChanges(eventService.send(eventToSend));
                    // If the event execution changes the profile we need to update it so the next event use the right profile
                    if ((eventsRequestContext.getChanges() & EventService.PROFILE_UPDATED) == EventService.PROFILE_UPDATED) {
                        eventsRequestContext.setProfile(eventToSend.getProfile());
                    }
                    if (eventsRequestContext.isNewSession()) {
                        eventsRequestContext.getSession().getOriginEventIds().add(eventToSend.getItemId());
                        eventsRequestContext.getSession().getOriginEventTypes().add(eventToSend.getEventType());
                    }
                    if ((eventsRequestContext.getChanges() & EventService.ERROR) == EventService.ERROR) {
                        //Don't count the event that failed
                        eventsRequestContext.setProcessedItems(eventsRequestContext.getProcessedItems() - 1);
                        logger.error("Error processing events. Total number of processed events: {}/{}", eventsRequestContext.getProcessedItems(), eventsRequestContext.getTotalItems());
                        break;
                    }
                }
            }

        }

        return eventsRequestContext;
    }

    @Override
    public void finalizeEventsRequest(EventsRequestContext eventsRequestContext, boolean crashOnError) {
        // in case of changes on profile, persist the profile
        if ((eventsRequestContext.getChanges() & EventService.PROFILE_UPDATED) == EventService.PROFILE_UPDATED) {
            profileService.save(eventsRequestContext.getProfile());
        }

        // in case of changes on session, persist the session
        if ((eventsRequestContext.getChanges() & EventService.SESSION_UPDATED) == EventService.SESSION_UPDATED && eventsRequestContext.getSession() != null) {
            profileService.saveSession(eventsRequestContext.getSession());
        }

        // In case of error, return an error message
        if ((eventsRequestContext.getChanges() & EventService.ERROR) == EventService.ERROR) {
            if (crashOnError) {
                String errorMessage = "Error processing events. Total number of processed events: " + eventsRequestContext.getProcessedItems() + "/"
                        + eventsRequestContext.getTotalItems();
                throw new BadRequestException(errorMessage);
            } else {
                eventsRequestContext.getResponse().setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }

        // Set profile cookie
        if (!(eventsRequestContext.getProfile() instanceof Persona)) {
            eventsRequestContext.getResponse().setHeader("Set-Cookie",
                    HttpUtils.getProfileCookieString(eventsRequestContext.getProfile(), configSharingService, eventsRequestContext.getRequest().isSecure()));
        }
    }

    private Profile createNewProfile(String existingProfileId, Date timestamp) {
        Profile profile;
        String profileId = existingProfileId;
        if (profileId == null) {
            profileId = UUID.randomUUID().toString();
        }
        profile = new Profile(profileId);
        profile.setProperty("firstVisit", timestamp);
        return profile;
    }
}
