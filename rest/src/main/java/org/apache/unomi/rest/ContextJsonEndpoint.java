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
import org.apache.unomi.api.*;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.PrivacyService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jws.WebService;
import javax.servlet.ServletContext;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
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
@Component(service=ContextJsonEndpoint.class,property = "osgi.jaxrs.resource=true")
public class ContextJsonEndpoint {
    private static final Logger logger = LoggerFactory.getLogger(ContextJsonEndpoint.class.getName());

    @Context
    ServletContext context;
    @Context
    HttpServletRequest request;
    @Context
    HttpServletResponse response;

    @Reference
    private ProfileService profileService;
    @Reference
    private PrivacyService privacyService;
    @Reference
    private EventService eventService;

    @POST
    @Path("/context.json")
    public ContextResponse getContextJSON(String contextRequestAsString, @CookieParam("context-profile-id") String cookieProfileId) {
        try {
            ObjectMapper mapper = CustomObjectMapper.getObjectMapper();
            JsonFactory factory = mapper.getFactory();
            ContextRequest contextRequest = null;
            try {
                contextRequest = mapper.readValue(factory.createParser(contextRequestAsString), ContextRequest.class);
            } catch (Exception e) {
                ((HttpServletResponse)response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Check logs for more details");
                logger.error("Cannot read contextRequest ", e);
                return null;
            }

            final Date timestamp = new Date();
            if (request.getParameter("timestamp") != null) {
                timestamp.setTime(Long.parseLong(request.getParameter("timestamp")));
            }

            // Handle persona
            Profile profile = null;
            Session session = null;
            String personaId = request.getParameter("personaId");
            if (personaId != null) {
                PersonaWithSessions personaWithSessions = profileService.loadPersonaWithSessions(personaId);
                if (personaWithSessions == null) {
                    logger.error("Couldn't find persona with id=" + personaId);
                    profile = null;
                } else {
                    profile = personaWithSessions.getPersona();
                    session = personaWithSessions.getLastSession();
                }
            }

            String scope = null;
            if (contextRequest.getSource() != null) {
                scope = contextRequest.getSource().getScope();
            }
            String sessionId = contextRequest.getSessionId();
            String profileId = contextRequest.getProfileId();

            if (sessionId == null) {
                sessionId = request.getParameter("sessionId");
            }

            if (profileId == null) {
                // Get profile id from the cookie
                profileId = cookieProfileId;
            }

            if (profileId == null && sessionId == null && personaId == null) {
                ((HttpServletResponse)response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Check logs for more details");
                return null;
            }

            int changes = EventService.NO_CHANGE;
            if (profile == null) {
                // Not a persona, resolve profile now
                boolean profileCreated = false;

                boolean invalidateProfile = request.getParameter("invalidateProfile") != null ?
                        new Boolean(request.getParameter("invalidateProfile")) : false;
                if (profileId == null || invalidateProfile) {
                    // no profileId cookie was found or the profile has to be invalidated, we generate a new one and create the profile in the profile service
                    profile = createNewProfile(null, response, timestamp);
                    profileCreated = true;
                } else {
                    profile = profileService.load(profileId);
                    if (profile == null) {
                        // this can happen if we have an old cookie but have reset the server,
                        // or if we merged the profiles and somehow this cookie didn't get updated.
                        profile = createNewProfile(profileId, response, timestamp);
                        profileCreated = true;
                    }
                }

                Profile sessionProfile;
                boolean invalidateSession = request.getParameter("invalidateSession") != null ?
                        new Boolean(request.getParameter("invalidateSession")) : false;
                if (StringUtils.isNotBlank(sessionId) && !invalidateSession) {
                    session = profileService.loadSession(sessionId, timestamp);
                    if (session != null) {
                        sessionProfile = session.getProfile();

                        boolean anonymousSessionProfile = sessionProfile.isAnonymousProfile();
                        if (!profile.isAnonymousProfile() && !anonymousSessionProfile && !profile.getItemId().equals(sessionProfile.getItemId())) {
                            // Session user has been switched, profile id in cookie is not up to date
                            // We must reload the profile with the session ID as some properties could be missing from the session profile
                            // #personalIdentifier
                            profile = profileService.load(sessionProfile.getItemId());

                        }

                        // Handle anonymous situation
                        Boolean requireAnonymousBrowsing = privacyService.isRequireAnonymousBrowsing(profile);
                        if (requireAnonymousBrowsing && anonymousSessionProfile) {
                            // User wants to browse anonymously, anonymous profile is already set.
                        } else if (requireAnonymousBrowsing && !anonymousSessionProfile) {
                            // User wants to browse anonymously, update the sessionProfile to anonymous profile
                            sessionProfile = privacyService.getAnonymousProfile(profile);
                            session.setProfile(sessionProfile);
                            changes |= EventService.SESSION_UPDATED;
                        } else if (!requireAnonymousBrowsing && anonymousSessionProfile) {
                            // User does not want to browse anonymously anymore, update the sessionProfile to real profile
                            sessionProfile = profile;
                            session.setProfile(sessionProfile);
                            changes |= EventService.SESSION_UPDATED;
                        } else if (!requireAnonymousBrowsing && !anonymousSessionProfile) {
                            // User does not want to browse anonymously, use the real profile. Check that session contains the current profile.
                            sessionProfile = profile;
                            if (!session.getProfileId().equals(sessionProfile.getItemId())) {
                                changes |= EventService.SESSION_UPDATED;
                            }
                            session.setProfile(sessionProfile);
                        }
                    }
                }

                if (session == null || invalidateSession) {
                    sessionProfile = privacyService.isRequireAnonymousBrowsing(profile) ? privacyService.getAnonymousProfile(profile) : profile;

                    if (StringUtils.isNotBlank(sessionId)) {
                        // Only save session and send event if a session id was provided, otherwise keep transient session
                        session = new Session(sessionId, sessionProfile, timestamp, scope);
                        changes |= EventService.SESSION_UPDATED;
                        Event event = new Event("sessionCreated", session, profile, scope, null, session, timestamp);
                        if (sessionProfile.isAnonymousProfile()) {
                            // Do not keep track of profile in event
                            event.setProfileId(null);
                        }
                        event.getAttributes().put(Event.HTTP_REQUEST_ATTRIBUTE, request);
                        event.getAttributes().put(Event.HTTP_RESPONSE_ATTRIBUTE, response);
                        if (logger.isDebugEnabled()) {
                            logger.debug("Received event {} for profile={} session={} target={} timestamp={}",
                                    event.getEventType(), profile.getItemId(), session.getItemId(), event.getTarget(), timestamp);
                        }
                        changes |= eventService.send(event);
                    }
                }

                if (profileCreated) {
                    changes |= EventService.PROFILE_UPDATED;

                    Event profileUpdated = new Event("profileUpdated", session, profile, scope, null, profile, timestamp);
                    profileUpdated.setPersistent(false);
                    profileUpdated.getAttributes().put(Event.HTTP_REQUEST_ATTRIBUTE, request);
                    profileUpdated.getAttributes().put(Event.HTTP_RESPONSE_ATTRIBUTE, response);

                    if (logger.isDebugEnabled()) {
                        logger.debug("Received event {} for profile={} {} target={} timestamp={}", profileUpdated.getEventType(), profile.getItemId(),
                                " session=" + (session != null ? session.getItemId() : null), profileUpdated.getTarget(), timestamp);
                    }
                    changes |= eventService.send(profileUpdated);
                }
            }

            ContextResponse contextResponse = new ContextResponse();
            contextResponse.setProfileId(profile.getItemId());
            if (session != null) {
                contextResponse.setSessionId(session.getItemId());
            } else if (sessionId != null) {
                contextResponse.setSessionId(sessionId);
            }

            if ((changes & EventService.PROFILE_UPDATED) == EventService.PROFILE_UPDATED) {
                profileService.save(profile);
                contextResponse.setProfileId(profile.getItemId());
            }
            if ((changes & EventService.SESSION_UPDATED) == EventService.SESSION_UPDATED && session != null) {
                profileService.saveSession(session);
                contextResponse.setSessionId(session.getItemId());
            }

            return contextResponse;
        } catch (Throwable t) { // Here in order to return generic message instead of the whole stack trace in case of not caught exception
            logger.error("ContextServlet failed to execute request", t);
            throw  new RuntimeException(t);
        }
    }

    private Profile createNewProfile(String existingProfileId, ServletResponse response, Date timestamp) {
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
