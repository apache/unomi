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

package org.apache.unomi.rest.endpoints;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.*;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.*;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.rest.exception.InvalidRequestException;
import org.apache.unomi.rest.service.RestServiceUtils;
import org.apache.unomi.schema.api.SchemaService;
import org.apache.unomi.utils.EventsRequestContext;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jws.WebService;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
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

@WebService
@Consumes(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(allowAllOrigins = true, allowCredentials = true)
@Path("/")
@Component(service = ContextJsonEndpoint.class, property = "osgi.jaxrs.resource=true")
public class ContextJsonEndpoint {
    private static final Logger logger = LoggerFactory.getLogger(ContextJsonEndpoint.class.getName());

    private final boolean sanitizeConditions = Boolean
            .parseBoolean(System.getProperty("org.apache.unomi.security.personalization.sanitizeConditions", "true"));

    @Context
    ServletContext context;
    @Context
    HttpServletRequest request;
    @Context
    HttpServletResponse response;

    @Reference
    private PrivacyService privacyService;
    @Reference
    private RulesService rulesService;
    @Reference
    private PersonalizationService personalizationService;
    @Reference
    private RestServiceUtils restServiceUtils;
    @Reference
    private SchemaService schemaService;

    @OPTIONS
    @Path("/context.js")
    public Response contextJSAsOptions() {
        return Response.status(Response.Status.NO_CONTENT).header("Access-Control-Allow-Origin", "*").build();
    }

    @OPTIONS
    @Path("/context.json")
    public Response contextJSONAsOptions() {
        return contextJSAsOptions();
    }

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/context.js")
    public Response contextJSAsPost(ContextRequest contextRequest,
            @QueryParam("personaId") String personaId,
            @QueryParam("sessionId") String sessionId,
            @QueryParam("timestamp") Long timestampAsLong, @QueryParam("invalidateProfile") boolean invalidateProfile,
            @QueryParam("invalidateSession") boolean invalidateSession) throws JsonProcessingException {
        return contextJSAsGet(contextRequest, personaId, sessionId, timestampAsLong, invalidateProfile, invalidateSession);
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/context.js")
    public Response contextJSAsGet(@QueryParam("payload") ContextRequest contextRequest,
            @QueryParam("personaId") String personaId,
            @QueryParam("sessionId") String sessionId,
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
    public ContextResponse contextJSONAsGet(@QueryParam("payload") ContextRequest contextRequest,
            @QueryParam("personaId") String personaId,
            @QueryParam("sessionId") String sessionId,
            @QueryParam("timestamp") Long timestampAsLong, @QueryParam("invalidateProfile") boolean invalidateProfile,
            @QueryParam("invalidateSession") boolean invalidateSession) {
        return contextJSONAsPost(contextRequest, personaId, sessionId, timestampAsLong, invalidateProfile, invalidateSession);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @Path("/context.json")
    public ContextResponse contextJSONAsPost(ContextRequest contextRequest,
                                             @QueryParam("personaId") String personaId,
                                             @QueryParam("sessionId") String sessionId,
                                             @QueryParam("timestamp") Long timestampAsLong,
                                             @QueryParam("invalidateProfile") boolean invalidateProfile,
                                             @QueryParam("invalidateSession") boolean invalidateSession) {

        // Schema validation
        ObjectNode paramsAsJson = JsonNodeFactory.instance.objectNode();
        paramsAsJson.put("personaId", personaId);
        paramsAsJson.put("sessionId", sessionId);
        if (!schemaService.isValid(paramsAsJson.toString(), "https://unomi.apache.org/schemas/json/rest/requestIds/1-0-0")) {
            throw new InvalidRequestException("Invalid parameter", "Invalid received data");
        }

        // Generate timestamp
        Date timestamp = new Date();
        if (timestampAsLong != null) {
            timestamp = new Date(timestampAsLong);
        }

        // init ids
        String profileId = null;
        String scope = null;
        if (contextRequest != null) {
            scope = contextRequest.getSource() != null ? contextRequest.getSource().getScope() : scope;
            sessionId = contextRequest.getSessionId() != null ? contextRequest.getSessionId() : sessionId;
            profileId = contextRequest.getProfileId();
        }

        // build public context, profile + session creation/anonymous etc ...
        EventsRequestContext eventsRequestContext = restServiceUtils.initEventsRequest(scope, sessionId, profileId,
                personaId, invalidateProfile, invalidateSession, request, response, timestamp);

        // Build response
        ContextResponse contextResponse = new ContextResponse();
        if (contextRequest != null) {
            eventsRequestContext = processContextRequest(contextRequest, contextResponse, eventsRequestContext);
        }

        // finalize request, save profile and session if necessary and return profileId cookie in response
        restServiceUtils.finalizeEventsRequest(eventsRequestContext, false);

        contextResponse.setProfileId(eventsRequestContext.getProfile().getItemId());
        if (eventsRequestContext.getSession() != null) {
            contextResponse.setSessionId(eventsRequestContext.getSession().getItemId());
        } else if (sessionId != null) {
            contextResponse.setSessionId(sessionId);
        }
        return contextResponse;
    }

    private EventsRequestContext processContextRequest(ContextRequest contextRequest, ContextResponse data, EventsRequestContext eventsRequestContext) {

        processOverrides(contextRequest, eventsRequestContext.getProfile(), eventsRequestContext.getSession());

        eventsRequestContext = restServiceUtils.performEventsRequest(contextRequest.getEvents(), eventsRequestContext);
        data.setProcessedEvents(eventsRequestContext.getProcessedItems());

        List<PersonalizationService.PersonalizedContent> filterNodes = contextRequest.getFilters();
        if (filterNodes != null) {
            data.setFilteringResults(new HashMap<>());
            for (PersonalizationService.PersonalizedContent personalizedContent : sanitizePersonalizedContentObjects(filterNodes)) {
                data.getFilteringResults()
                        .put(personalizedContent.getId(), personalizationService.filter(eventsRequestContext.getProfile(), eventsRequestContext.getSession(), personalizedContent));
            }
        }

        List<PersonalizationService.PersonalizationRequest> personalizations = contextRequest.getPersonalizations();
        if (personalizations != null) {
            data.setPersonalizations(new HashMap<>());
            for (PersonalizationService.PersonalizationRequest personalization : sanitizePersonalizations(personalizations)) {
                PersonalizationResult personalizationResult = personalizationService.personalizeList(eventsRequestContext.getProfile(), eventsRequestContext.getSession(), personalization);
                eventsRequestContext.addChanges(personalizationResult.getChangeType());
                data.getPersonalizations().put(personalization.getId(), personalizationResult.getContentIds());
            }
        }

        if (contextRequest.isRequireSegments()) {
            data.setProfileSegments(eventsRequestContext.getProfile().getSegments());
        }
        if (contextRequest.isRequireScores()) {
            data.setProfileScores(eventsRequestContext.getProfile().getScores());
        }

        if (contextRequest.getRequiredProfileProperties() != null) {
            Map<String, Object> profileProperties = new HashMap<>(eventsRequestContext.getProfile().getProperties());
            if (!contextRequest.getRequiredProfileProperties().contains("*")) {
                profileProperties.keySet().retainAll(contextRequest.getRequiredProfileProperties());
            }
            data.setProfileProperties(profileProperties);
        }

        if (eventsRequestContext.getSession() != null) {
            data.setSessionId(eventsRequestContext.getSession().getItemId());
            if (contextRequest.getRequiredSessionProperties() != null) {
                Map<String, Object> sessionProperties = new HashMap<>(eventsRequestContext.getSession().getProperties());
                if (!contextRequest.getRequiredSessionProperties().contains("*")) {
                    sessionProperties.keySet().retainAll(contextRequest.getRequiredSessionProperties());
                }
                data.setSessionProperties(sessionProperties);
            }
        }

        if (!(eventsRequestContext.getProfile() instanceof Persona)) {
            data.setTrackedConditions(rulesService.getTrackedConditions(contextRequest.getSource()));
        } else {
            data.setTrackedConditions(Collections.emptySet());
        }

        data.setAnonymousBrowsing(privacyService.isRequireAnonymousBrowsing(eventsRequestContext.getProfile()));
        data.setConsents(eventsRequestContext.getProfile().getConsents());

        return eventsRequestContext;
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
