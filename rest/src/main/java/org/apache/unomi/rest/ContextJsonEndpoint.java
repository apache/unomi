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

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.ContextRequest;
import org.apache.unomi.api.ContextResponse;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Persona;
import org.apache.unomi.api.PersonaWithSessions;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.ConfigSharingService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.PersonalizationService;
import org.apache.unomi.api.services.PrivacyService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.services.RulesService;
import org.apache.unomi.api.utils.ValidationPattern;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.rest.validation.cookies.CookieWrapper;
import org.apache.unomi.utils.Changes;
import org.apache.unomi.utils.HttpUtils;
import org.apache.unomi.utils.ServletCommon;
import org.hibernate.validator.HibernateValidatorFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jws.WebService;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolation;
import javax.validation.Valid;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.constraints.Pattern;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@WebService
@Consumes(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(allowAllOrigins = true, allowCredentials = true)
@Path("/")
@Component(service = ContextJsonEndpoint.class, property = "osgi.jaxrs.resource=true")
public class ContextJsonEndpoint {
    private static final Logger logger = LoggerFactory.getLogger(ContextJsonEndpoint.class.getName());

    private boolean sanitizeConditions = Boolean
            .parseBoolean(System.getProperty("org.apache.unomi.security.personalization.sanitizeConditions", "true"));

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
    @Reference
    private RulesService rulesService;
    @Reference
    private PersonalizationService personalizationService;
    @Reference
    private ConfigSharingService configSharingService;

    @OPTIONS
    @Path("/context.json")
    public Response options() {
        return Response.status(Response.Status.NO_CONTENT).header("Access-Control-Allow-Origin", "*").build();
    }

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/context.js")
    public Response contextJSAsPost(@Valid ContextRequest contextRequest,
            @QueryParam("personaId") @Pattern(regexp = ValidationPattern.TEXT_VALID_CHARACTERS_PATTERN) String personaId,
            @QueryParam("sessionId") @Pattern(regexp = ValidationPattern.TEXT_VALID_CHARACTERS_PATTERN) String sessionId,
            @QueryParam("timestamp") Long timestampAsLong,
            @QueryParam("invalidateProfile") boolean invalidateProfile, @QueryParam("invalidateSession") boolean invalidateSession)
            throws JsonProcessingException {
        return contextJSAsGet(contextRequest, personaId, sessionId, timestampAsLong, invalidateProfile, invalidateSession);
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/context.js")
    public Response contextJSAsGet(@Valid ContextRequest contextRequest,
            @QueryParam("personaId") @Pattern(regexp = ValidationPattern.TEXT_VALID_CHARACTERS_PATTERN) String personaId,
            @QueryParam("sessionId") @Pattern(regexp = ValidationPattern.TEXT_VALID_CHARACTERS_PATTERN) String sessionId,
            @QueryParam("timestamp") Long timestampAsLong, @QueryParam("invalidateProfile") boolean invalidateProfile,
            @QueryParam("invalidateSession") boolean invalidateSession) throws JsonProcessingException {
        ContextResponse contextResponse = contextJSONAsPost(contextRequest, personaId, sessionId, timestampAsLong, invalidateProfile,
                invalidateSession);
        String contextAsJSONString = CustomObjectMapper.getObjectMapper().writeValueAsString(contextResponse);
        StringBuilder responseAsString = new StringBuilder();
        responseAsString.append("window.digitalData = window.digitalData || {};\n").append("var cxs = ").append(contextAsJSONString)
                .append(";\n");
        return Response.ok(responseAsString.toString()).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @Path("/context.json")
    public ContextResponse contextJSONAsGet(@Valid ContextRequest contextRequest,
            @QueryParam("personaId") @Pattern(regexp = ValidationPattern.TEXT_VALID_CHARACTERS_PATTERN) String personaId,
            @QueryParam("sessionId") @Pattern(regexp = ValidationPattern.TEXT_VALID_CHARACTERS_PATTERN) String sessionId,
            @QueryParam("timestamp") Long timestampAsLong,
            @QueryParam("invalidateProfile") boolean invalidateProfile, @QueryParam("invalidateSession") boolean invalidateSession) {
        return contextJSONAsPost(contextRequest, personaId, sessionId, timestampAsLong, invalidateProfile, invalidateSession);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @Path("/context.json")
    public ContextResponse contextJSONAsPost(@Valid ContextRequest contextRequest,
            @QueryParam("personaId") @Pattern(regexp = ValidationPattern.TEXT_VALID_CHARACTERS_PATTERN) String personaId,
            @QueryParam("sessionId") @Pattern(regexp = ValidationPattern.TEXT_VALID_CHARACTERS_PATTERN) String sessionId,
            @QueryParam("timestamp") Long timestampAsLong,
            @QueryParam("invalidateProfile") boolean invalidateProfile, @QueryParam("invalidateSession") boolean invalidateSession) {
        Date timestamp = new Date();
        if (timestampAsLong != null) {
            timestamp = new Date(timestampAsLong);
        }

        // Handle persona
        Profile profile = null;
        Session session = null;
        String profileId = null;
        if (personaId != null) {
            PersonaWithSessions personaWithSessions = profileService.loadPersonaWithSessions(personaId);
            if (personaWithSessions == null) {
                logger.error("Couldn't find persona, please check your personaId parameter");
                profile = null;
            } else {
                profile = personaWithSessions.getPersona();
                session = personaWithSessions.getLastSession();
            }
        }

        String scope = null;
        if (contextRequest != null) {
            if (contextRequest.getSource() != null) {
                scope = contextRequest.getSource().getScope();
            }

            if (contextRequest.getSessionId() != null) {
                sessionId = contextRequest.getSessionId();
            }

            profileId = contextRequest.getProfileId();
        }
        if (profileId == null) {
            // Get profile id from the cookie
            profileId = ServletCommon.getProfileIdCookieValue(request, (String) configSharingService.getProperty("profileIdCookieName"));
        }

        if (profileId == null && sessionId == null && personaId == null) {
            logger.error(
                    "Couldn't find profileId, sessionId or personaId in incoming request! Stopped processing request. See debug level for more information");
            if (logger.isDebugEnabled()) {
                logger.debug("Request dump: {}", HttpUtils.dumpRequestInfo(request));
            }
            throw new BadRequestException("Couldn't find profileId, sessionId or personaId in incoming request!");
        }

        int changes = EventService.NO_CHANGE;
        if (profile == null) {
            // Not a persona, resolve profile now
            boolean profileCreated = false;

            if (profileId == null || invalidateProfile) {
                // no profileId cookie was found or the profile has to be invalidated, we generate a new one and create the profile in the profile service
                profile = createNewProfile(null, timestamp);
                profileCreated = true;
            } else {
                profile = profileService.load(profileId);
                if (profile == null) {
                    // this can happen if we have an old cookie but have reset the server,
                    // or if we merged the profiles and somehow this cookie didn't get updated.
                    profile = createNewProfile(profileId, timestamp);
                    profileCreated = true;
                } else {
                    Changes changesObject = checkMergedProfile(profile, session);
                    changes |= changesObject.getChangeType();
                    profile = changesObject.getProfile();
                }
            }

            Profile sessionProfile;
            if (StringUtils.isNotBlank(sessionId) && !invalidateSession) {
                session = profileService.loadSession(sessionId, timestamp);
                if (session != null) {
                    sessionProfile = session.getProfile();

                    boolean anonymousSessionProfile = sessionProfile.isAnonymousProfile();
                    if (!profile.isAnonymousProfile() && !anonymousSessionProfile && !profile.getItemId()
                            .equals(sessionProfile.getItemId())) {
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
                        logger.debug("Received event {} for profile={} session={} target={} timestamp={}", event.getEventType(),
                                profile.getItemId(), session.getItemId(), event.getTarget(), timestamp);
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
                    logger.debug("Received event {} for profile={} {} target={} timestamp={}", profileUpdated.getEventType(),
                            profile.getItemId(), " session=" + (session != null ? session.getItemId() : null), profileUpdated.getTarget(),
                            timestamp);
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

        if ((changes & EventService.ERROR) == EventService.ERROR) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        // Set profile cookie
        if (!(profile instanceof Persona)) {
            response.setHeader("Set-Cookie", HttpUtils.getProfileCookieString(profile, configSharingService));
        }
        return contextResponse;
    }

    private Changes checkMergedProfile(Profile profile, Session session) {
        int changes = EventService.NO_CHANGE;
        if (profile.getMergedWith() != null && !privacyService.isRequireAnonymousBrowsing(profile) && !profile.isAnonymousProfile()) {
            Profile currentProfile = profile;
            String masterProfileId = profile.getMergedWith();
            Profile masterProfile = profileService.load(masterProfileId);
            if (masterProfile != null) {
                logger.info("Current profile {} was merged with profile {}, replacing profile in session", currentProfile.getItemId(),
                        masterProfileId);
                profile = masterProfile;
                if (session != null) {
                    session.setProfile(profile);
                    changes = EventService.SESSION_UPDATED;
                }
            } else {
                logger.warn("Couldn't find merged profile {}, falling back to profile {}", masterProfileId, currentProfile.getItemId());
                profile.setMergedWith(null);
                changes = EventService.PROFILE_UPDATED;
            }
        }

        return new Changes(changes, profile);
    }

    private Changes handleRequest(ContextRequest contextRequest, Session session, Profile profile, ContextResponse data,
            ServletRequest request, ServletResponse response, Date timestamp) {
        Changes changes = ServletCommon
                .handleEvents(contextRequest.getEvents(), session, profile, request, response, timestamp, privacyService, eventService);
        data.setProcessedEvents(changes.getProcessedItems());

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
            for (PersonalizationService.PersonalizedContent personalizedContent : sanitizePersonalizedContentObjects(filterNodes)) {
                data.getFilteringResults()
                        .put(personalizedContent.getId(), personalizationService.filter(profile, session, personalizedContent));
            }
        }

        List<PersonalizationService.PersonalizationRequest> personalizations = contextRequest.getPersonalizations();
        if (personalizations != null) {
            data.setPersonalizations(new HashMap<>());
            for (PersonalizationService.PersonalizationRequest personalization : sanitizePersonalizations(personalizations)) {
                data.getPersonalizations()
                        .put(personalization.getId(), personalizationService.personalizeList(profile, session, personalization));
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
     *
     * @param contextRequest
     * @param profile
     * @param session
     */
    private void processOverrides(ContextRequest contextRequest, Profile profile, Session session) {
        if (profile instanceof Persona && contextRequest.getProfileOverrides() != null) {
            if (contextRequest.getProfileOverrides().getScores() != null) {
                profile.setScores(contextRequest.getProfileOverrides().getScores());
            }
            if (contextRequest.getProfileOverrides().getSegments() != null) {
                profile.setSegments(contextRequest.getProfileOverrides().getSegments());
            }
            if (contextRequest.getProfileOverrides().getProperties() != null) {
                profile.setProperties(contextRequest.getProfileOverrides().getProperties());
            }
            if (contextRequest.getSessionPropertiesOverrides() != null && session != null) {
                session.setProperties(contextRequest.getSessionPropertiesOverrides());
            }
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

    public void destroy() {
        logger.info("Context servlet shutdown.");
    }

    private List<PersonalizationService.PersonalizedContent> sanitizePersonalizedContentObjects(
            List<PersonalizationService.PersonalizedContent> personalizedContentObjects) {
        if (!sanitizeConditions) {
            return personalizedContentObjects;
        }
        List<PersonalizationService.PersonalizedContent> result = new ArrayList<>();
        for (PersonalizationService.PersonalizedContent personalizedContentObject : personalizedContentObjects) {
            boolean foundInvalidCondition = false;
            if (personalizedContentObject.getFilters() != null) {
                for (PersonalizationService.Filter filter : personalizedContentObject.getFilters()) {
                    if (sanitizeCondition(filter.getCondition()) == null) {
                        foundInvalidCondition = true;
                        break;
                    }
                }
            }
            if (!foundInvalidCondition) {
                result.add(personalizedContentObject);
            }
        }

        return result;
    }

    private List<PersonalizationService.PersonalizationRequest> sanitizePersonalizations(
            List<PersonalizationService.PersonalizationRequest> personalizations) {
        if (!sanitizeConditions) {
            return personalizations;
        }
        List<PersonalizationService.PersonalizationRequest> result = new ArrayList<>();
        for (PersonalizationService.PersonalizationRequest personalizationRequest : personalizations) {
            List<PersonalizationService.PersonalizedContent> personalizedContents = sanitizePersonalizedContentObjects(
                    personalizationRequest.getContents());
            if (personalizedContents != null && !personalizedContents.isEmpty()) {
                result.add(personalizationRequest);
            }
        }
        return result;
    }

    private Condition sanitizeCondition(Condition condition) {
        Map<String, Object> newParameterValues = new LinkedHashMap<>();
        for (Map.Entry<String, Object> parameterEntry : condition.getParameterValues().entrySet()) {
            Object sanitizedValue = sanitizeValue(parameterEntry.getValue());
            if (sanitizedValue != null) {
                newParameterValues.put(parameterEntry.getKey(), parameterEntry.getValue());
            } else {
                return null;
            }
        }
        return condition;
    }

    private Object sanitizeValue(Object value) {
        if (value instanceof String) {
            String stringValue = (String) value;
            if (stringValue.startsWith("script::") || stringValue.startsWith("parameter::")) {
                logger.warn("Scripting detected in context request, filtering out. See debug level for more information");
                if (logger.isDebugEnabled()) {
                    logger.debug("Scripting detected in context request with value {}, filtering out...", value);
                }
                return null;
            } else {
                return stringValue;
            }
        } else if (value instanceof List) {
            List values = (List) value;
            List newValues = new ArrayList();
            for (Object listObject : values) {
                Object newObject = sanitizeValue(listObject);
                if (newObject != null) {
                    newValues.add(newObject);
                }
            }
            return values;
        } else if (value instanceof Map) {
            Map<Object, Object> newMap = new LinkedHashMap<>();
            ((Map<?, ?>) value).forEach((key, value1) -> {
                Object newObject = sanitizeValue(value1);
                if (newObject != null) {
                    newMap.put(key, newObject);
                }
            });
            return newMap;
        } else if (value instanceof Condition) {
            return sanitizeCondition((Condition) value);
        } else {
            return value;
        }
    }

}
