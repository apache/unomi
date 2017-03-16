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
import org.apache.commons.io.IOUtils;
import org.apache.unomi.api.*;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.PrivacyService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.services.RulesService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.*;

/**
 * A servlet filter to serve a context-specific Javascript containing the current request context object.
 */
public class ContextServlet extends HttpServlet {
    public static final String BASE_SCRIPT_LOCATION = "/javascript/base.js";
    public static final String IMPERSONATE_BASE_SCRIPT_LOCATION = "/javascript/impersonateBase.js";
    public static final String PROFILE_OVERRIDE_MARKER = "---IGNORE---";
    private static final Logger logger = LoggerFactory.getLogger(ContextServlet.class.getName());
    private static final long serialVersionUID = 2928875830103325238L;
    private ProfileService profileService;
    private EventService eventService;
    private RulesService rulesService;
    private PrivacyService privacyService;

    private String profileIdCookieName = "context-profile-id";
    private String profileIdCookieDomain;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        logger.info("ContextServlet initialized.");
    }

    @Override
    public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
        final Date timestamp = new Date();
        if (request.getParameter("timestamp") != null) {
            timestamp.setTime(Long.parseLong(request.getParameter("timestamp")));
        }

        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        String httpMethod = httpServletRequest.getMethod();

        // set up CORS headers as soon as possible so that errors are not misconstrued on the client for CORS errors
        HttpUtils.setupCORSHeaders(httpServletRequest, response);

        if ("options".equals(httpMethod.toLowerCase())) {
            response.flushBuffer();
            logger.debug("OPTIONS request received. No context will be returned.");
            return;
        }

        Profile profile = null;
        Profile sessionProfile = null;

        String cookieProfileId = null;
        Cookie[] cookies = httpServletRequest.getCookies();
        for (Cookie cookie : cookies) {
            if (profileIdCookieName.equals(cookie.getName())) {
                cookieProfileId = cookie.getValue();
            }
        }

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

        String sessionId = request.getParameter("sessionId");

        if (cookieProfileId == null && sessionId == null && personaId == null) {
            ((HttpServletResponse)response).sendError(HttpServletResponse.SC_BAD_REQUEST);
            logger.error("Couldn't find cookieProfileId, sessionId or personaId in incoming request! Stopped processing request. See debug level for more information");
            if (logger.isDebugEnabled()) {
                logger.debug("Request dump:" + HttpUtils.dumpRequestInfo(httpServletRequest));
            }
            return;
        }

        ContextRequest contextRequest = null;
        String scope = null;
        String stringPayload = HttpUtils.getPayload(httpServletRequest);
        if (stringPayload != null) {
            ObjectMapper mapper = CustomObjectMapper.getObjectMapper();
            JsonFactory factory = mapper.getFactory();
            try {
                contextRequest = mapper.readValue(factory.createParser(stringPayload), ContextRequest.class);
            } catch (Exception e) {
                logger.error("Cannot read payload " + stringPayload, e);
                return;
            }
            scope = contextRequest.getSource().getScope();
        }

        int changes = EventService.NO_CHANGE;

        if (profile == null) {
            boolean profileCreated = false;

            // Not a persona, resolve profile now
            if (cookieProfileId == null) {
                // no profileId cookie was found, we generate a new one and create the profile in the profile service
                profile = createNewProfile(null, response, timestamp);
                profileCreated = true;
            } else {
                profile = profileService.load(cookieProfileId);
                if (profile == null) {
                    // this can happen if we have an old cookie but have reset the server,
                    // or if we merged the profiles and somehow this cookie didn't get updated.
                    profile = createNewProfile(null, response, timestamp);
                    profileCreated = true;
                } else {
                    profile = checkMergedProfile(response, profile, session);
                }
            }

            if (sessionId != null) {
                session = profileService.loadSession(sessionId, timestamp);
                if (session != null) {
                    sessionProfile = session.getProfile();
                    boolean anonymousProfile = sessionProfile.isAnonymousProfile();

                    if (!profile.isAnonymousProfile() && !anonymousProfile && !profile.getItemId().equals(sessionProfile.getItemId())) {
                        // Session user has been switched, profile id in cookie is not uptodate
                        profile = sessionProfile;
                        HttpUtils.sendProfileCookie(profile, response, profileIdCookieName, profileIdCookieDomain);
                    }

                    Boolean requireAnonymousBrowsing = privacyService.isRequireAnonymousBrowsing(profile.getItemId());

                    if (requireAnonymousBrowsing && anonymousProfile) {
                        // User wants to browse anonymously, anonymous profile is already set.
                    } else if (requireAnonymousBrowsing && !anonymousProfile) {
                        // User wants to browse anonymously, update the sessionProfile to anonymous profile
                        sessionProfile = privacyService.getAnonymousProfile(profile);
                        session.setProfile(sessionProfile);
                        changes = EventService.SESSION_UPDATED;
                    } else if (!requireAnonymousBrowsing && anonymousProfile) {
                        // User does not want to browse anonymously anymore, update the sessionProfile to real profile
                        sessionProfile = profile;
                        session.setProfile(sessionProfile);
                        changes = EventService.SESSION_UPDATED;
                    } else if (!requireAnonymousBrowsing && !anonymousProfile) {
                        // User does not want to browse anonymously, use the real profile. Check that session contains the current profile.
                        sessionProfile = profile;
                        if (!session.getProfileId().equals(sessionProfile.getItemId())) {
                            changes = EventService.SESSION_UPDATED;
                        }
                        session.setProfile(sessionProfile);
                    }
                }
            }

            if (session == null) {
                sessionProfile = privacyService.isRequireAnonymousBrowsing(profile.getItemId()) ? privacyService.getAnonymousProfile(profile) : profile;
                session = new Session(sessionId, sessionProfile, timestamp, scope);

                if (sessionId != null) {
                    // Only save session and send event if a session id was provided, otherise keep transient session
                    changes |= EventService.SESSION_UPDATED;
                    Event event = new Event("sessionCreated", session, profile, scope, null, session, timestamp);
                    if (sessionProfile.isAnonymousProfile()) {
                        // Do not keep track of profile in event
                        event.setProfileId(null);
                    }
                    event.getAttributes().put(Event.HTTP_REQUEST_ATTRIBUTE, request);
                    event.getAttributes().put(Event.HTTP_RESPONSE_ATTRIBUTE, response);
                    logger.debug("Received event " + event.getEventType() + " for profile=" + profile.getItemId() + " session=" + session.getItemId() + " target=" + event.getTarget() + " timestamp=" + timestamp);
                    changes |= eventService.send(event);
                }
            }

            if (profileCreated) {
                changes |= EventService.PROFILE_UPDATED;

                Event profileUpdated = new Event("profileUpdated", session, profile, scope, null, profile, timestamp);
                profileUpdated.setPersistent(false);
                profileUpdated.getAttributes().put(Event.HTTP_REQUEST_ATTRIBUTE, request);
                profileUpdated.getAttributes().put(Event.HTTP_RESPONSE_ATTRIBUTE, response);

                logger.debug("Received event {} for profile={} {} target={} timestamp={}", profileUpdated.getEventType(), profile.getItemId(),
                        session != null ? " session=" + session.getItemId() : "", profileUpdated.getTarget(), timestamp);
                changes |= eventService.send(profileUpdated);
            }
        }

        ContextResponse data = new ContextResponse();
        data.setProfileId(profile.getItemId());

        if (contextRequest != null){
            changes |= handleRequest(contextRequest, profile, session, data, request, response, timestamp);
        }

        if ((changes & EventService.PROFILE_UPDATED) == EventService.PROFILE_UPDATED) {
            profileService.save(profile);
        }
        if ((changes & EventService.SESSION_UPDATED) == EventService.SESSION_UPDATED && session != null) {
            profileService.saveSession(session);
        }


        String extension = httpServletRequest.getRequestURI().substring(httpServletRequest.getRequestURI().lastIndexOf(".") + 1);
        boolean noScript = "json".equals(extension);
        String contextAsJSONString = CustomObjectMapper.getObjectMapper().writeValueAsString(data);
        Writer responseWriter;
        if(noScript){
            response.setCharacterEncoding("UTF-8");
            responseWriter = response.getWriter();
            response.setContentType("application/json");
            IOUtils.write(contextAsJSONString, responseWriter);
        }else {
            responseWriter = response.getWriter();
            responseWriter.append("window.digitalData = window.digitalData || {};\n")
                    .append("var cxs = ")
                    .append(contextAsJSONString)
                    .append(";\n");

            // now we copy the base script source code
            InputStream baseScriptStream = getServletContext().getResourceAsStream(profile instanceof Persona ? IMPERSONATE_BASE_SCRIPT_LOCATION : BASE_SCRIPT_LOCATION);
            IOUtils.copy(baseScriptStream, responseWriter);
        }

        responseWriter.flush();
    }

    private Profile checkMergedProfile(ServletResponse response, Profile profile, Session session) {
        if (profile.getMergedWith() != null && !privacyService.isRequireAnonymousBrowsing(profile.getItemId()) && !profile.isAnonymousProfile()) {
            String profileId = profile.getMergedWith();
            Profile profileToDelete = profile;
            profile = profileService.load(profileId);
            if (profile != null) {
                logger.debug("Session profile was merged with profile " + profileId + ", replacing profile in session");
                if (session != null) {
                    session.setProfile(profile);
                    profileService.saveSession(session);
                }
                HttpUtils.sendProfileCookie(profile, response, profileIdCookieName, profileIdCookieDomain);
            } else {
                logger.warn("Couldn't find merged profile" + profileId + ", falling back to profile " + profileToDelete.getItemId());
                profile = profileToDelete;
                profile.setMergedWith(null);
                profileService.save(profile);
            }
        }

        return profile;
    }

    private int handleRequest(ContextRequest contextRequest, Profile profile, Session session, ContextResponse data, ServletRequest request, ServletResponse response, Date timestamp)
            throws IOException {
        List<String> filteredEventTypes = privacyService.getFilteredEventTypes(profile.getItemId());

        String thirdPartyId = eventService.authenticateThirdPartyServer(((HttpServletRequest)request).getHeader("X-Unomi-Peer"), request.getRemoteAddr());

        int changes = EventService.NO_CHANGE;
        // execute provided events if any
        if(contextRequest.getEvents() != null && !(profile instanceof Persona)) {
            for (Event event : contextRequest.getEvents()){
                if(event.getEventType() != null) {
                    Profile sessionProfile = session.getProfile();
                    Event eventToSend = new Event(event.getEventType(), session, sessionProfile, contextRequest.getSource().getScope(), event.getSource(), event.getTarget(), event.getProperties(), timestamp);
                    if (!eventService.isEventAllowed(event, thirdPartyId)) {
                        logger.debug("Event is not allowed : {}", event.getEventType());
                        continue;
                    }
                    if (filteredEventTypes != null && filteredEventTypes.contains(event.getEventType())) {
                        logger.debug("Profile is filtering event type {}", event.getEventType());
                        continue;
                    }
                    if (sessionProfile.isAnonymousProfile()) {
                        // Do not keep track of profile in event
                        event.setProfileId(null);
                    }

                    event.getAttributes().put(Event.HTTP_REQUEST_ATTRIBUTE, request);
                    event.getAttributes().put(Event.HTTP_RESPONSE_ATTRIBUTE, response);
                    logger.debug("Received event " + event.getEventType() + " for profile=" + session.getProfileId() + " session=" + session.getItemId() + " target=" + event.getTarget() + " timestamp=" + timestamp);
                    changes |= eventService.send(eventToSend);
                }
            }
        }

        if (contextRequest.isRequireSegments()) {
            data.setProfileSegments(profile.getSegments());
        }

        if (contextRequest.getRequiredProfileProperties() != null) {
            Map<String, Object> profileProperties = new HashMap<String, Object>(profile.getProperties());
            if (!contextRequest.getRequiredProfileProperties().contains("*")) {
                profileProperties.keySet().retainAll(contextRequest.getRequiredProfileProperties());
            }
            data.setProfileProperties(profileProperties);
        }
        if (session != null) {
            data.setSessionId(session.getItemId());
            if (contextRequest.getRequiredSessionProperties() != null) {
                Map<String, Object> sessionProperties = new HashMap<String, Object>(session.getProperties());
                if (!contextRequest.getRequiredSessionProperties().contains("*")) {
                    sessionProperties.keySet().retainAll(contextRequest.getRequiredSessionProperties());
                }
                data.setSessionProperties(sessionProperties);
            }
        }

        processOverrides(contextRequest, profile, session);

        List<ContextRequest.FilteredContent> filterNodes = contextRequest.getFilters();
        if (filterNodes != null) {
            data.setFilteringResults(new HashMap<String, Boolean>());
            for (ContextRequest.FilteredContent filteredContent : filterNodes) {
                boolean result = true;
                for (ContextRequest.Filter filter : filteredContent.getFilters()) {
                    Condition condition = filter.getCondition();
                    result &= profileService.matchCondition(condition, profile, session);
                }
                data.getFilteringResults().put(filteredContent.getFilterid(), result);
            }
        }

        if(!(profile instanceof Persona)) {
            data.setTrackedConditions(rulesService.getTrackedConditions(contextRequest.getSource()));
        } else {
            data.setTrackedConditions(Collections.<Condition>emptySet());
        }

        data.setAnonymousBrowsing(privacyService.isRequireAnonymousBrowsing(profile.getItemId()));

        return changes;
    }

    /**
     * This function will update the profile if it is from Persona instance.
     * The profile will be updated using the overrides attributes :
     * - profileOverrides for profile properties, segments and scores
     * - sessionPropertiesOverrides for session properties
     * @param contextRequest
     * @param profile
     * @param session
     */
    private void processOverrides(ContextRequest contextRequest, Profile profile, Session session) {
        if (profile instanceof Persona) {
            if (contextRequest.getProfileOverrides() != null) {
                if(contextRequest.getProfileOverrides().getScores()!=null){
                    profile.setScores(contextRequest.getProfileOverrides().getScores());
                }
                if(contextRequest.getProfileOverrides().getSegments()!=null){
                    profile.setSegments(contextRequest.getProfileOverrides().getSegments());
                }
                if(contextRequest.getProfileOverrides().getProperties()!=null){
                    profile.setProperties(contextRequest.getProfileOverrides().getProperties());
                }
                if(contextRequest.getSessionPropertiesOverrides()!=null){
                    session.setProperties(contextRequest.getSessionPropertiesOverrides());
                }
            }
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
        HttpUtils.sendProfileCookie(profile, response, profileIdCookieName, profileIdCookieDomain);
        return profile;
    }


    public void destroy() {
        logger.info("Context servlet shutdown.");
    }

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    public void setRulesService(RulesService rulesService) {
        this.rulesService = rulesService;
    }

    public void setProfileIdCookieDomain(String profileIdCookieDomain) {
        this.profileIdCookieDomain = profileIdCookieDomain;
    }

    public void setPrivacyService(PrivacyService privacyService) {
        this.privacyService = privacyService;
    }
}
