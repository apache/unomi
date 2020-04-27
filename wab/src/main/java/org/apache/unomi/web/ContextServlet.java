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
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.*;
import org.apache.unomi.api.services.*;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
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
    private static final long serialVersionUID = 2928875830103325238L;
    private static final Logger logger = LoggerFactory.getLogger(ContextServlet.class.getName());

    private static final int MAX_COOKIE_AGE_IN_SECONDS = 60 * 60 * 24 * 365; // 1 year

    private String profileIdCookieName = "context-profile-id";
    private String profileIdCookieDomain;
    private int profileIdCookieMaxAgeInSeconds = MAX_COOKIE_AGE_IN_SECONDS;

    private ProfileService profileService;
    private EventService eventService;
    private RulesService rulesService;
    private PrivacyService privacyService;
    private PersonalizationService personalizationService;
    private ConfigSharingService configSharingService;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        configSharingService.setProperty("profileIdCookieName", profileIdCookieName);
        configSharingService.setProperty("profileIdCookieDomain", profileIdCookieDomain);
        configSharingService.setProperty("profileIdCookieMaxAgeInSeconds", (Integer) profileIdCookieMaxAgeInSeconds);
        logger.info("ContextServlet initialized.");
    }

    @Override
    public void service(ServletRequest request, ServletResponse response) throws IOException {
        final Date timestamp = new Date();
        if (request.getParameter("timestamp") != null) {
            timestamp.setTime(Long.parseLong(request.getParameter("timestamp")));
        }

        HttpServletRequest httpServletRequest = (HttpServletRequest) request;

        // set up CORS headers as soon as possible so that errors are not misconstrued on the client for CORS errors
        HttpUtils.setupCORSHeaders(httpServletRequest, response);

        // Handle OPTIONS request
        String httpMethod = httpServletRequest.getMethod();
        if ("options".equals(httpMethod.toLowerCase())) {
            response.flushBuffer();
            if (logger.isDebugEnabled()) {
                logger.debug("OPTIONS request received. No context will be returned.");
            }
            return;
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

        // Extract payload
        ContextRequest contextRequest = null;
        String scope = null;
        String sessionId = null;
        String stringPayload = HttpUtils.getPayload(httpServletRequest);
        if (stringPayload != null) {
            ObjectMapper mapper = CustomObjectMapper.getObjectMapper();
            JsonFactory factory = mapper.getFactory();
            try {
                contextRequest = mapper.readValue(factory.createParser(stringPayload), ContextRequest.class);
            } catch (Exception e) {
                ((HttpServletResponse)response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Check logs for more details");
                logger.error("Cannot read payload " + stringPayload, e);
                return;
            }
            if (contextRequest.getSource() != null) {
                scope = contextRequest.getSource().getScope();
            }
            sessionId = contextRequest.getSessionId();
        }

        if (sessionId == null) {
            sessionId = request.getParameter("sessionId");
        }

        // Get profile id from the cookie
        String cookieProfileId = ServletCommon.getProfileIdCookieValue(httpServletRequest, profileIdCookieName);

        if (cookieProfileId == null && sessionId == null && personaId == null) {
            ((HttpServletResponse)response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Check logs for more details");
            logger.error("Couldn't find cookieProfileId, sessionId or personaId in incoming request! Stopped processing request. See debug level for more information");
            if (logger.isDebugEnabled()) {
                logger.debug("Request dump: {}", HttpUtils.dumpRequestInfo(httpServletRequest));
            }
            return;
        }

        int changes = EventService.NO_CHANGE;
        if (profile == null) {
            // Not a persona, resolve profile now
            boolean profileCreated = false;

            boolean invalidateProfile = request.getParameter("invalidateProfile") != null ?
                    new Boolean(request.getParameter("invalidateProfile")) : false;
            if (cookieProfileId == null || invalidateProfile) {
                // no profileId cookie was found or the profile has to be invalidated, we generate a new one and create the profile in the profile service
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
                    Changes changesObject = checkMergedProfile(response, profile, session);
                    changes |= changesObject.getChangeType();
                    profile = changesObject.getProfile();
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
                        HttpUtils.sendProfileCookie(profile, response, profileIdCookieName, profileIdCookieDomain, profileIdCookieMaxAgeInSeconds);
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

        if (contextRequest != null) {
            Changes changesObject = handleRequest(contextRequest, session, profile, contextResponse, request, response, timestamp);
            changes |= changesObject.getChangeType();
            profile = changesObject.getProfile();
        }

        if ((changes & EventService.PROFILE_UPDATED) == EventService.PROFILE_UPDATED) {
            profileService.save(profile);
            contextResponse.setProfileId(profile.getItemId());
        }
        if ((changes & EventService.SESSION_UPDATED) == EventService.SESSION_UPDATED && session != null) {
            profileService.saveSession(session);
            contextResponse.setSessionId(session.getItemId());
        }

        String extension = httpServletRequest.getRequestURI().substring(httpServletRequest.getRequestURI().lastIndexOf(".") + 1);
        boolean noScript = "json".equals(extension);
        String contextAsJSONString = CustomObjectMapper.getObjectMapper().writeValueAsString(contextResponse);
        Writer responseWriter;
        response.setCharacterEncoding("UTF-8");
        if (noScript) {
            responseWriter = response.getWriter();
            response.setContentType("application/json");
            IOUtils.write(contextAsJSONString, responseWriter);
        } else {
            responseWriter = response.getWriter();
            responseWriter.append("window.digitalData = window.digitalData || {};\n")
                    .append("var cxs = ")
                    .append(contextAsJSONString)
                    .append(";\n");
        }

        responseWriter.flush();
    }

    private Changes checkMergedProfile(ServletResponse response, Profile profile, Session session) {
        int changes = EventService.NO_CHANGE;
        if (profile.getMergedWith() != null && !privacyService.isRequireAnonymousBrowsing(profile) && !profile.isAnonymousProfile()) {
            Profile currentProfile = profile;
            String masterProfileId = profile.getMergedWith();
            Profile masterProfile = profileService.load(masterProfileId);
            if (masterProfile != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Current profile was merged with profile {}, replacing profile in session", masterProfileId);
                }
                profile = masterProfile;
                if (session != null) {
                    session.setProfile(profile);
                    changes = EventService.SESSION_UPDATED;
                }
                HttpUtils.sendProfileCookie(profile, response, profileIdCookieName, profileIdCookieDomain, profileIdCookieMaxAgeInSeconds);
            } else {
                logger.warn("Couldn't find merged profile {}, falling back to profile {}", masterProfileId, currentProfile.getItemId());
                profile = currentProfile;
                profile.setMergedWith(null);
                changes = EventService.PROFILE_UPDATED;
            }
        }

        return new Changes(changes, profile);
    }

    private Changes handleRequest(ContextRequest contextRequest, Session session, Profile profile, ContextResponse data,
                                ServletRequest request, ServletResponse response, Date timestamp) {
        Changes changes = ServletCommon.handleEvents(contextRequest.getEvents(), session, profile, request, response, timestamp,
                privacyService, eventService);

        profile = changes.getProfile();

        if (contextRequest.isRequireSegments()) {
            data.setProfileSegments(profile.getSegments());
        }

        if (contextRequest.getRequiredProfileProperties() != null) {
            Map<String, Object> profileProperties = new HashMap<>(profile.getProperties());
            if (!contextRequest.getRequiredProfileProperties().contains("*")) {
                profileProperties.keySet().retainAll(contextRequest.getRequiredProfileProperties());
            }
            data.setProfileProperties(profileProperties);
        }

        if (session != null) {
            data.setSessionId(session.getItemId());
            if (contextRequest.getRequiredSessionProperties() != null) {
                Map<String, Object> sessionProperties = new HashMap<>(session.getProperties());
                if (!contextRequest.getRequiredSessionProperties().contains("*")) {
                    sessionProperties.keySet().retainAll(contextRequest.getRequiredSessionProperties());
                }
                data.setSessionProperties(sessionProperties);
            }
        }

        processOverrides(contextRequest, profile, session);

        List<PersonalizationService.PersonalizedContent> filterNodes = contextRequest.getFilters();
        if (filterNodes != null) {
            data.setFilteringResults(new HashMap<>());
            for (PersonalizationService.PersonalizedContent personalizedContent : filterNodes) {
                data.getFilteringResults().put(personalizedContent.getId(), personalizationService.filter(profile,
                        session, personalizedContent));
            }
        }

        List<PersonalizationService.PersonalizationRequest> personalizations = contextRequest.getPersonalizations();
        if (personalizations != null) {
            data.setPersonalizations(new HashMap<>());
            for (PersonalizationService.PersonalizationRequest personalization : personalizations) {
                data.getPersonalizations().put(personalization.getId(), personalizationService.personalizeList(profile,
                        session, personalization));
            }
        }

        if (!(profile instanceof Persona)) {
            data.setTrackedConditions(rulesService.getTrackedConditions(contextRequest.getSource()));
        } else {
            data.setTrackedConditions(Collections.emptySet());
        }

        data.setAnonymousBrowsing(privacyService.isRequireAnonymousBrowsing(profile));
        data.setConsents(profile.getConsents());

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
                if (contextRequest.getProfileOverrides().getScores()!=null) {
                    profile.setScores(contextRequest.getProfileOverrides().getScores());
                }
                if (contextRequest.getProfileOverrides().getSegments()!=null) {
                    profile.setSegments(contextRequest.getProfileOverrides().getSegments());
                }
                if (contextRequest.getProfileOverrides().getProperties()!=null) {
                    profile.setProperties(contextRequest.getProfileOverrides().getProperties());
                }
                if (contextRequest.getSessionPropertiesOverrides()!=null && session != null) {
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
        HttpUtils.sendProfileCookie(profile, response, profileIdCookieName, profileIdCookieDomain, profileIdCookieMaxAgeInSeconds);
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

    public void setProfileIdCookieName(String profileIdCookieName) {
        this.profileIdCookieName = profileIdCookieName;
    }

    public void setProfileIdCookieMaxAgeInSeconds(int profileIdCookieMaxAgeInSeconds) {
        this.profileIdCookieMaxAgeInSeconds = profileIdCookieMaxAgeInSeconds;
    }

    public void setPrivacyService(PrivacyService privacyService) {
        this.privacyService = privacyService;
    }

    public void setPersonalizationService(PersonalizationService personalizationService) {
        this.personalizationService = personalizationService;
    }

    public void setConfigSharingService(ConfigSharingService configSharingService) {
        this.configSharingService = configSharingService;
    }
}
