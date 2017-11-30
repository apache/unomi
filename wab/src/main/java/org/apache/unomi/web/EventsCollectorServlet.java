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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.*;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.PrivacyService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class EventsCollectorServlet extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(EventsCollectorServlet.class.getName());

    private static final long serialVersionUID = 2008054804885122957L;

    private EventService eventService;
    private ProfileService profileService;
    private PrivacyService privacyService;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        logger.info("Event collector servlet initialized.");
    }

    @Override
    public void destroy() {
        super.destroy();
        logger.info("Event collector servlet shutdown.");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        doEvent(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        doEvent(req, resp);
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//        logger.debug(HttpUtils.dumpRequestInfo(request));
        HttpUtils.setupCORSHeaders(request, response);
        response.flushBuffer();
    }

    private void doEvent(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Date timestamp = new Date();
        if (request.getParameter("timestamp") != null) {
            timestamp.setTime(Long.parseLong(request.getParameter("timestamp")));
        }

        HttpUtils.setupCORSHeaders(request, response);

        String payload = HttpUtils.getPayload(request);
        if (payload == null){
            logger.error("No event payload found for request, aborting !");
            return;
        }

        ObjectMapper mapper = CustomObjectMapper.getObjectMapper();
        JsonFactory factory = mapper.getFactory();
        EventsCollectorRequest events = null;
        try {
            events = mapper.readValue(factory.createParser(payload), EventsCollectorRequest.class);
        } catch (Exception e) {
            logger.error("Cannot read payload " + payload,e);
            return;
        }
        if (events == null || events.getEvents() == null) {
            logger.error("No events found in payload");
            return;
        }

        String sessionId = request.getParameter("sessionId");
        if (sessionId == null) {
            logger.error("No sessionId found in incoming request, aborting processing. See debug level for more information");
            if (logger.isDebugEnabled()) {
                logger.debug("Request dump:" + HttpUtils.dumpRequestInfo(request));
            }
            return;
        }

        Session session = profileService.loadSession(sessionId, timestamp);
        Profile sessionProfile;
        Profile profile = null;
        if (session == null) {
            String scope = "systemscope";
            // Get the first available scope that is not equal to systemscope to create the session otherwise systemscope will be used
            for (Event event : events.getEvents()) {
                if (StringUtils.isNotBlank(event.getEventType())) {
                    if (StringUtils.isNotBlank(event.getScope()) && !event.getScope().equals("systemscope")) {
                        scope = event.getScope();
                        break;
                    } else if (event.getSource() != null && StringUtils.isNotBlank(event.getSource().getScope()) && !event.getSource().getScope().equals("systemscope")) {
                        scope = event.getSource().getScope();
                        break;
                    }
                }
            }
            // Create non persisted profile to create the session
            profile = new Profile("temp_" + UUID.randomUUID().toString());
            profile.setProperty("firstVisit", timestamp);
            // Create anonymous profile so we don't keep track of the temp profile anywhere
            Profile anonymousProfile = privacyService.getAnonymousProfile(profile);
            // Create new session which should not be persisted as well as the temp profile
            session = new Session(sessionId, anonymousProfile, timestamp, scope);
            if (logger.isDebugEnabled()) {
                logger.debug("No session found for sessionId={}, creating new session!", sessionId);
            }
            sessionProfile = session.getProfile();
        } else {
            String profileIdCookieName = "context-profile-id";

            sessionProfile = session.getProfile();
            if (sessionProfile.getItemId() != null) {
                // Reload up-to-date profile
                profile = profileService.load(sessionProfile.getItemId());
                if (profile == null || profile instanceof Persona) {
                    logger.error("No valid profile found or persona found for profileId={}, aborting request !", session.getProfileId());
                    return;
                }
            } else {
                // Session uses anonymous profile, try to find profile from cookie
                Cookie[] cookies = request.getCookies();
                for (Cookie cookie : cookies) {
                    if (profileIdCookieName.equals(cookie.getName())) {
                        profile = profileService.load(cookie.getValue());
                    }
                }
                if (profile == null) {
                    logger.error("No valid profile found or persona found for profileId={}, aborting request !", session.getProfileId());
                    return;
                }
            }
        }

        String thirdPartyId = eventService.authenticateThirdPartyServer(((HttpServletRequest)request).getHeader("X-Unomi-Peer"), request.getRemoteAddr());

        int changes = 0;

        List<String> filteredEventTypes = privacyService.getFilteredEventTypes(profile.getItemId());

        for (Event event : events.getEvents()){
            if(event.getEventType() != null){
                Event eventToSend = new Event(event.getEventType(), session, profile, event.getScope(), event.getSource(), event.getTarget(), event.getProperties(), timestamp);
                if (sessionProfile.isAnonymousProfile()) {
                    // Do not keep track of profile in event
                    eventToSend.setProfileId(null);
                }

                if (!eventService.isEventAllowed(event, thirdPartyId)) {
                    logger.warn("Event is not allowed : {}", event.getEventType());
                    continue;
                }
                if (filteredEventTypes != null && filteredEventTypes.contains(event.getEventType())) {
                    logger.debug("Profile is filtering event type {}", event.getEventType());
                    continue;
                }

                eventToSend.getAttributes().put(Event.HTTP_REQUEST_ATTRIBUTE, request);
                eventToSend.getAttributes().put(Event.HTTP_RESPONSE_ATTRIBUTE, response);
                logger.debug("Received event " + event.getEventType() + " for profile=" + sessionProfile.getItemId() + " session=" + session.getItemId() + " target=" + event.getTarget() + " timestamp=" + timestamp);
                int eventChanged = eventService.send(eventToSend);
                //if the event execution changes the profile
                if ((eventChanged & EventService.PROFILE_UPDATED) == EventService.PROFILE_UPDATED) {
                    profile = eventToSend.getProfile();
                }
                changes |= eventChanged;
            }
        }

        if ((changes & EventService.PROFILE_UPDATED) == EventService.PROFILE_UPDATED) {
            profileService.save(profile);
        }
        if ((changes & EventService.SESSION_UPDATED) == EventService.SESSION_UPDATED) {
            profileService.saveSession(session);
        }

        response.setContentType("application/json");
        PrintWriter responseWriter = response.getWriter();
        responseWriter.append("{\"updated\":" + changes + "}");
        responseWriter.flush();
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public void setPrivacyService(PrivacyService privacyService) {
        this.privacyService = privacyService;
    }
}
