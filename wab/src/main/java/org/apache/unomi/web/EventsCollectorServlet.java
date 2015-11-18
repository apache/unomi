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
import org.apache.unomi.api.*;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.PrivacyService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;

public class EventsCollectorServlet extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(EventsCollectorServlet.class.getName());

    private static final long serialVersionUID = 2008054804885122957L;

    private EventService eventService;
    private ProfileService profileService;
    private PrivacyService privacyService;

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

//        logger.debug(HttpUtils.dumpRequestInfo(request));

        HttpUtils.setupCORSHeaders(request, response);

        Profile profile = null;

        String sessionId = request.getParameter("sessionId");
        if (sessionId == null) {
            return;
        }

        Session session = profileService.loadSession(sessionId, timestamp);
        if (session == null) {
            return;
        }

        String profileId = session.getProfileId();
        if (profileId == null) {
            return;
        }

        profile = profileService.load(profileId);
        if (profile == null || profile instanceof Persona) {
            return;
        }

        String payload = HttpUtils.getPayload(request);
        if(payload == null){
            return;
        }

        Profile realProfile = profile;
        Boolean profileIsAnonymous = privacyService.isAnonymous(profile.getItemId());
        if (profileIsAnonymous != null && profileIsAnonymous.booleanValue()) {
            // we are surfing anonymously, we must use the global anonymous profile if it exists, or create it if
            // it doesn't.
            Profile anonymousProfile = profileService.load(PrivacyService.GLOBAL_ANONYMOUS_PROFILE_ID);
            if (anonymousProfile == null) {
                anonymousProfile = new Profile(PrivacyService.GLOBAL_ANONYMOUS_PROFILE_ID);
                profileService.save(profile);
            }
            realProfile = profile;
            profile = anonymousProfile;
        }

        List<String> filteredEventTypes = privacyService.getFilteredEventTypes(profile.getItemId());

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
            return;
        }

        int changes = 0;
        for (Event event : events.getEvents()){
            if(event.getEventType() != null){
                Event eventToSend;
                if(event.getProperties() != null){
                    eventToSend = new Event(event.getEventType(), session, profile, event.getScope(), event.getSource(), event.getTarget(), event.getProperties(), timestamp);
                } else {
                    eventToSend = new Event(event.getEventType(), session, profile, event.getScope(), event.getSource(), event.getTarget(), timestamp);
                }

                if (filteredEventTypes != null && filteredEventTypes.contains(event.getEventType())) {
                    logger.debug("Profile is filtering event type {}", event.getEventType());
                    continue;
                }

                eventToSend.getAttributes().put(Event.HTTP_REQUEST_ATTRIBUTE, request);
                eventToSend.getAttributes().put(Event.HTTP_RESPONSE_ATTRIBUTE, response);
                logger.debug("Received event " + event.getEventType() + " for profile=" + profile.getItemId() + " session=" + session.getItemId() + " target=" + event.getTarget() + " timestamp=" + timestamp);
                int eventChanged = eventService.send(eventToSend);
                //if the event execution changes the profile
                if ((eventChanged & EventService.PROFILE_UPDATED) == EventService.PROFILE_UPDATED) {
                    profile = eventToSend.getProfile();
                    session.setProfile(profile);
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
