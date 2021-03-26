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

package org.apache.unomi.rest;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.EventsCollectorRequest;
import org.apache.unomi.api.Persona;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.PrivacyService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.utils.Changes;
import org.apache.unomi.utils.ServletCommon;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jws.WebService;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Date;
import java.util.UUID;

@WebService
@Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
@Consumes(MediaType.TEXT_PLAIN)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
@Path("/")
@Component(service = EventsCollectorEndpoint.class, property = "osgi.jaxrs.resource=true")
public class EventsCollectorEndpoint {
    private static final Logger logger = LoggerFactory.getLogger(EventsCollectorEndpoint.class.getName());

    private String profileIdCookieName;

    @Reference
    private EventService eventService;
    @Reference
    private ProfileService profileService;
    @Reference
    private PrivacyService privacyService;

    @Context
    HttpServletRequest request;
    @Context
    HttpServletResponse response;

    @GET
    @Path("/eventcollector")
    public EventCollectorResponse get(String eventsCollectorRequestAsString, @QueryParam("timestamp") Long timestampAsString) throws IOException {
        return doEvent(eventsCollectorRequestAsString, timestampAsString);
    }

    @POST
    @Path("/eventcollector")
    public EventCollectorResponse post(String eventsCollectorRequestAsString, @QueryParam("timestamp") Long timestampAsLong) throws IOException {
        return doEvent(eventsCollectorRequestAsString, timestampAsLong);
    }

    private EventCollectorResponse doEvent(String eventsCollectorRequestAsString, Long timestampAsLong) throws IOException {
        Date timestamp = new Date();
        if (timestampAsLong != null) {
            timestamp = new Date(timestampAsLong);
        }


        ObjectMapper mapper = CustomObjectMapper.getObjectMapper();
        JsonFactory factory = mapper.getFactory();
        EventsCollectorRequest eventsCollectorRequest;
        try {
            eventsCollectorRequest = mapper.readValue(factory.createParser(eventsCollectorRequestAsString), EventsCollectorRequest.class);
        } catch (Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Cannot parse eventsCollectorRequest: {}", eventsCollectorRequestAsString, e);
            }
            throw new RemoteException("Cannot read payload. See debug level for more information");
        }
        if (eventsCollectorRequest == null || eventsCollectorRequest.getEvents() == null) {
            throw new InternalServerErrorException("No events found");
        }

        String sessionId = eventsCollectorRequest.getSessionId();
        if (sessionId == null) {
            sessionId = request.getParameter("sessionId");
        }
        Session session = null;
        if (sessionId != null) {
            session = profileService.loadSession(sessionId, timestamp);
        }
        Profile profile = null;
        if (session == null) {
            String scope = "systemscope";
            // Get the first available scope that is not equal to systemscope to create the session otherwise systemscope will be used
            for (Event event : eventsCollectorRequest.getEvents()) {
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
            String cookieProfileId = ServletCommon.getProfileIdCookieValue(request, profileIdCookieName);
            if (StringUtils.isNotBlank(cookieProfileId)) {
                profile = profileService.load(cookieProfileId);
            }
            if (profile == null) {
                // Create non persisted profile to create the session
                profile = new Profile("temp_" + UUID.randomUUID().toString());
                profile.setProperty("firstVisit", timestamp);
            }
            /*
            // Create anonymous profile so we don't keep track of the temp profile anywhere
            Profile anonymousProfile = privacyService.getAnonymousProfile(profile);
            // Create new session which should not be persisted as well as the temp profile
            session = new Session(sessionId, anonymousProfile, timestamp, scope);
            if (logger.isDebugEnabled()) {
                logger.debug("No session found for sessionId={}, creating new session!", sessionId);
            }
            */
        } else {
            Profile sessionProfile = session.getProfile();
            if (sessionProfile.getItemId() != null) {
                // Reload up-to-date profile
                profile = profileService.load(sessionProfile.getItemId());
                if (profile == null || profile instanceof Persona) {
                    throw new InternalServerErrorException(String.format("No valid profile found or persona found for profileId=%s, aborting request !", session.getProfileId()));
                }
            } else {
                // Session uses anonymous profile, try to find profile from cookie
                String cookieProfileId = ServletCommon.getProfileIdCookieValue(request, profileIdCookieName);
                if (StringUtils.isNotBlank(cookieProfileId)) {
                    profile = profileService.load(cookieProfileId);
                }

                if (profile == null) {
                    throw new InternalServerErrorException(String.format("No valid profile found or persona found for profileId=%s, aborting request !", session.getProfileId()));
                }
            }
        }

        Changes changesObject = ServletCommon.handleEvents(eventsCollectorRequest.getEvents(), session, profile, request, response,
                timestamp, privacyService, eventService);
        int changes = changesObject.getChangeType();
        profile = changesObject.getProfile();

        if ((changes & EventService.PROFILE_UPDATED) == EventService.PROFILE_UPDATED) {
            profileService.save(profile);
        }
        if ((changes & EventService.SESSION_UPDATED) == EventService.SESSION_UPDATED && session != null) {
                profileService.saveSession(session);
        }
        if ((changes & EventService.ERROR) == EventService.ERROR) {
            String errorMessage = "Error processing events. Total number of processed events: " + changesObject.getProcessedItems() + "/" + eventsCollectorRequest.getEvents().size();
            throw new InternalServerErrorException(errorMessage);
        }

        return new EventCollectorResponse(changes);
    }

    @Activate
    public void init() {
        // TODO DMF-4436 read values from the configuration file: org.apache.unomi.web.cfg
        profileIdCookieName = "context-profile-id";
    }
}
