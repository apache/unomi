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

import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.*;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.rest.service.impl.LocalizationHelper;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * A JAX-RS endpoint to manage {@link Profile}s and {@link Persona}s.
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
@Path("/profiles")
@Component(service=ProfileServiceEndPoint.class,property = "osgi.jaxrs.resource=true")
public class ProfileServiceEndPoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileServiceEndPoint.class.getName());

    @Reference
    private ProfileService profileService;

    @Reference
    private EventService eventService;

    @Reference
    private SegmentService segmentService;

    @Reference
    private LocalizationHelper localizationHelper;

    /**
     * Creates the profile service endpoint.
     */
    public ProfileServiceEndPoint() {
        LOGGER.info("Initializing profile service endpoint...");
    }

    /**
     * Sets the profile service.
     *
     * @param profileService the profile service
     */
    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * Sets the event service.
     *
     * @param eventService the event service
     */
    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * Sets the segment service.
     *
     * @param segmentService the segment service
     */
    public void setSegmentService(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

    /**
     * Sets the localization helper.
     *
     * @param localizationHelper the localization helper
     */
    public void setLocalizationHelper(LocalizationHelper localizationHelper) {
        this.localizationHelper = localizationHelper;
    }

    /**
     * Returns the total number of unique profiles.
     *
     * @return the profile count
     * @api.status 200 empty Total number of unique profiles.
     * @api.example 1200
     */
    @GET
    @Path("/count")
    public long getAllProfilesCount() {
        return profileService.getAllProfilesCount();
    }

    /**
     * Returns profiles matching the given query.
     * Prefer a {@link org.apache.unomi.api.conditions.Condition} in the body for precise filters; optional full-text {@code text} and paging fields are also supported.
     *
     * @param query the search query
     * @return a paged list of matching profiles
     * @api.status 200 org.apache.unomi.api.PartialList Profiles page (list items are Profile).
     * @api.status 400 empty Invalid or unreadable query body.
     * @api.example {"list":[{"itemId":"profile-1","itemType":"profile","properties":{"firstName":"Ada","email":"ada@example.com"},"segments":["vip"]}],"offset":0,"pageSize":1,"totalSize":1,"totalSizeRelation":"EQUAL"}
     */
    @POST
    @Path("/search")
    public PartialList<Profile> getProfiles(Query query) {
        return profileService.search(query, Profile.class);
    }

    /**
     * Exports matching profiles as a downloadable CSV file.
     * The {@code query} parameter must be a JSON-encoded {@link org.apache.unomi.api.query.Query}.
     *
     * @param query JSON query string describing which profiles to export
     * @return a CSV download response
     * @api.status 200 empty CSV attachment with matching profile properties.
     * @api.status 400 empty {@code query} JSON is missing or not a valid Query.
     * @api.status 500 empty Export failed while parsing the query or generating CSV.
     * @api.example itemId,properties.email,properties.firstName\nprofile-42,ada@example.com,Ada
     */
    @GET
    @Path("/export")
    @Produces("text/csv")
    public Response getExportProfiles(@QueryParam("query") String query) {
        try {
            return exportProfiles(CustomObjectMapper.getObjectMapper().readValue(query, Query.class));
        } catch (IOException e) {
            LOGGER.error("{}", e.getMessage(), e);
            return Response.serverError().build();
        }
    }

    /**
     * A version of {@link #getExportProfiles(String)} suitable to be called from an HTML form.
     *
     * @param query a form-encoded representation of the query the profiles to export should match
     * @return a Response object configured to allow caller to download the CSV export file
     * @api.status 200 empty CSV attachment with matching profile properties.
     * @api.status 400 empty Form {@code query} is missing or not a valid Query JSON.
     * @api.status 500 empty Export failed while parsing the query or generating CSV.
     */
    @GET
    @Path("/export")
    @Produces("text/csv")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response formExportProfiles(@FormParam("query") String query) {
        try {
            return exportProfiles(CustomObjectMapper.getObjectMapper().readValue(query, Query.class));
        } catch (IOException e) {
            LOGGER.error("{}", e.getMessage(), e);
            return Response.serverError().build();
        }
    }

    /**
     * Exports matching profiles as a downloadable CSV file.
     *
     * @param query the query describing which profiles to export
     * @return a CSV download response
     * @api.status 200 empty CSV attachment with matching profile properties.
     * @api.status 400 empty Invalid or unreadable query body.
     * @api.example itemId,properties.email,properties.firstName\nprofile-42,ada@example.com,Ada
     */
    @POST
    @Path("/export")
    @Produces("text/csv")
    public Response exportProfiles(Query query) {
        String toCsv = profileService.exportProfilesPropertiesToCsv(query);
        Response.ResponseBuilder response = Response.ok(toCsv);
        response.header("Content-Disposition",
                "attachment; filename=Profiles_export_" + new SimpleDateFormat("yyyy-MM-dd-HH-mm").format(new Date()) + ".csv");
        return response.build();
    }

    /**
     * Applies a batch update to all matching profiles.
     * Selects matching profiles with {@link org.apache.unomi.api.BatchUpdate#getCondition()} and sets {@code propertyName} to {@code propertyValue}.
     *
     * @param update the batch update specification
     * @api.status 204 empty Batch update accepted and processed.
     * @api.status 400 empty Invalid batch update body (missing propertyName/condition).
     * @api.example {"propertyName":"properties.email","propertyValue":"updated@example.com","condition":{"type":"profilePropertyCondition","parameterValues":{"propertyName":"properties.email","comparisonOperator":"exists","propertyValue":""}},"strategy":"defaultPropertyMergeStrategy","scrollTimeValidity":"10m","scrollBatchSize":1000}
     */
    @POST
    @Path("/batchProfilesUpdate")
    public void batchProfilesUpdate(BatchUpdate update) {
        profileService.batchProfilesUpdate(update);
    }

    /**
     * Returns the profile with the given ID.
     * When the profile does not exist the endpoint returns {@code null} (HTTP 200 with empty body), not 404.
     *
     * @param profileId the profile identifier
     * @return the profile, or {@code null} when it does not exist
     * @api.status 200 org.apache.unomi.api.Profile Profile found, or empty body when missing.
     * @api.example {"itemId":"profile-1","itemType":"profile","properties":{"firstName":"Ada","email":"ada@example.com"},"segments":["vip"],"scores":{"engagement":12},"consents":{}}
     */
    @GET
    @Path("/{profileId}")
    public Profile load(@PathParam("profileId") String profileId) {
        return profileService.load(profileId);
    }

    /**
     * Saves the specified profile in the context server, sending a {@code profileUpdated} event.
     * Creates a new profile or merges into an existing one when applicable, then fires a non-persistent {@code profileUpdated} event.
     *
     * @param profile the profile to be saved
     * @return the newly saved profile
     * @api.status 200 org.apache.unomi.api.Profile Profile saved (or merged).
     * @api.status 400 empty Invalid or unreadable profile body.
     * @api.example {"itemId":"profile-1","itemType":"profile","properties":{"firstName":"Ada","email":"ada@example.com"},"segments":["vip"]}
     */
    @POST
    @Path("/")
    public Profile save(Profile profile) {
        Profile savedProfile = profileService.saveOrMerge(profile);
        if (savedProfile != null) {
            Event profileUpdated = new Event("profileUpdated", null, savedProfile, null, null, savedProfile, new Date());
            profileUpdated.setPersistent(false);
            int changes = eventService.send(profileUpdated);
            if ((changes & EventService.PROFILE_UPDATED) == EventService.PROFILE_UPDATED) {
                profileService.save(savedProfile);
            }
        }
        return savedProfile;
    }

    /**
     * Removes the profile (or persona if the {@code persona} query parameter is set to {@code true}) identified by the specified identifier.
     *
     * @param profileId the identifier of the profile or persona to delete
     * @param persona   {@code true} if the specified identifier is supposed to refer to a persona, {@code false} if it is supposed to refer to a profile
     * @api.status 204 empty Profile or persona deleted.
     * @api.example {"itemId":"profile-42","itemType":"profile","properties":{"firstName":"Ada","email":"ada@example.com"}}
     */
    @DELETE
    @Path("/{profileId}")
    public void delete(@PathParam("profileId") String profileId, @QueryParam("persona") @DefaultValue("false") boolean persona) {
        profileService.delete(profileId, persona);
    }

    /**
     * Returns sessions for the profile with the given ID.
     * <p>
     * Results can be filtered with an optional full-text query and paged with offset, size, and sort parameters.
     *
     * @param profileId the profile identifier
     * @param query optional full-text filter, or {@code null} for all sessions
     * @param offset zero-based index of the first result
     * @param size maximum number of results to return, or {@code -1} for all matches
     * @param sortBy optional comma-separated sort fields with optional {@code :asc} or {@code :desc}
     * @return a paged list of matching sessions
     * @api.status 200 org.apache.unomi.api.PartialList Sessions page (list items are Session).
     * @api.example {"list":[{"itemId":"session-1","itemType":"session","profileId":"profile-1","scope":"mysite","size":4}],"offset":0,"pageSize":1,"totalSize":1,"totalSizeRelation":"EQUAL"}
     */
    @GET
    @Path("/{profileId}/sessions")
    public PartialList<Session> getProfileSessions(@PathParam("profileId") String profileId,
                                                   @QueryParam("q") String query,
                                                   @QueryParam("offset") @DefaultValue("0") int offset,
                                                   @QueryParam("size") @DefaultValue("50") int size,
                                                   @QueryParam("sort") String sortBy) {
        return profileService.getProfileSessions(profileId, query, offset, size, sortBy);
    }

    /**
     * Returns segment metadata for segments that contain the given profile.
     *
     * @param profileId the profile identifier
     * @return segment metadata for memberships of this profile
     * @api.status 200 array org.apache.unomi.api.Metadata Segment memberships for the profile (may be empty).
     * @api.example [{"id":"vip","name":"VIP customers","scope":"mysite","enabled":true}]
     */
    @GET
    @Path("/{profileId}/segments")
    public List<Metadata> getProfileSegments(@PathParam("profileId") String profileId) {
        Profile profile = profileService.load(profileId);
        return segmentService.getSegmentMetadatasForProfile(profile);
    }

    /**
     * Returns the property-type mapping for the given source property type ID.
     *
     * @param fromPropertyTypeId the source property type identifier
     * @return the mapped target property type identifier
     * @api.status 200 empty Mapped target property type id (may be empty/null when unmapped).
     * @api.example email
     */
    @GET
    @Path("/properties/mappings/{fromPropertyTypeId}")
    public String getPropertyTypeMapping(@PathParam("fromPropertyTypeId") String fromPropertyTypeId) {
        return profileService.getPropertyTypeMapping(fromPropertyTypeId);
    }

    /**
     * Returns personas matching the given query.
     *
     * @param query the search query
     * @return a paged list of matching personas
     * @api.status 200 org.apache.unomi.api.PartialList Personas page (list items are Persona).
     * @api.status 400 empty Invalid or unreadable query body.
     * @api.example {"list":[{"itemId":"persona-vip","itemType":"persona","properties":{"firstName":"VIP"}}],"offset":0,"pageSize":1,"totalSize":1,"totalSizeRelation":"EQUAL"}
     */
    @POST
    @Path("/personas/search")
    public PartialList<Persona> getPersonas(Query query) {
        return profileService.search(query, Persona.class);
    }

    /**
     * Returns the persona with the given ID.
     * When the persona does not exist the endpoint returns {@code null} (HTTP 200 with empty body).
     *
     * @param personaId the persona identifier
     * @return the persona, or {@code null} when it does not exist
     * @api.status 200 org.apache.unomi.api.Persona Persona found, or empty body when missing.
     * @api.example {"itemId":"persona-vip","itemType":"persona","properties":{"firstName":"VIP"},"segments":["vip"]}
     */
    @GET
    @Path("/personas/{personaId}")
    public Persona loadPersona(@PathParam("personaId") String personaId) {
        return profileService.loadPersona(personaId);
    }

    /**
     * Returns the persona with the given ID and all associated sessions.
     *
     * @param personaId the persona identifier
     * @return the persona and its sessions
     * @api.status 200 org.apache.unomi.api.PersonaWithSessions Persona bundled with its sessions.
     * @api.example {"persona":{"itemId":"persona-vip","itemType":"persona","properties":{"firstName":"VIP"}},"sessions":[{"itemId":"ps-1","profileId":"persona-vip","scope":"mysite"}]}
     */
    @GET
    @Path("/personasWithSessions/{personaId}")
    public PersonaWithSessions loadPersonaWithSessions(@PathParam("personaId") String personaId) {
        return profileService.loadPersonaWithSessions(personaId);
    }

    /**
     * Saves the posted persona together with its sessions.
     *
     * @param personaWithSessions the persona and sessions to persist
     * @return the saved persona and sessions
     * @api.status 200 org.apache.unomi.api.PersonaWithSessions Persona and sessions saved.
     * @api.status 400 empty Invalid or unreadable body.
     * @api.example {"persona":{"itemId":"persona-vip","itemType":"persona","properties":{"firstName":"VIP"}},"sessions":[{"itemId":"ps-1","profileId":"persona-vip","scope":"mysite"}]}
     */
    @POST
    @Path("/personasWithSessions")
    public PersonaWithSessions savePersonaWithSessions(PersonaWithSessions personaWithSessions) {
        return profileService.savePersonaWithSessions(personaWithSessions);
    }

    /**
     * Persists the specified {@link Persona} in the context server.
     *
     * @param persona the persona to persist
     * @return the newly persisted persona
     * @api.status 200 org.apache.unomi.api.Persona Persona saved.
     * @api.status 400 empty Invalid or unreadable persona body.
     * @api.example {"itemId":"persona-vip","itemType":"persona","properties":{"firstName":"VIP"},"segments":["vip"]}
     */
    @POST
    @Path("/personas")
    public Persona savePersona(Persona persona) {
        return profileService.savePersona(persona);
    }

    /**
     * Removes the persona identified by the specified identifier.
     *
     * @param personaId the identifier of the persona to delete
     * @param persona   {@code true} if the specified identifier is supposed to refer to a persona, {@code false} if it is supposed to refer to a profile
     * @api.status 204 empty Persona deleted.
     * @api.example {"itemId":"persona-vip","itemType":"persona","properties":{"firstName":"VIP"}}
     */
    @DELETE
    @Path("/personas/{personaId}")
    public void deletePersona(@PathParam("personaId") String personaId, @QueryParam("persona") @DefaultValue("true") boolean persona) {
        profileService.delete(personaId, persona);
    }

    /**
     * Creates a persona with the specified identifier and automatically creates an associated session with it.
     *
     * @param personaId the identifier to use for the new persona
     * @return the newly created persona
     * @api.status 200 org.apache.unomi.api.Persona Persona created with an initial session.
     * @api.example {"itemId":"persona-vip","itemType":"persona"}
     */
    @PUT
    @Path("/personas/{personaId}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Persona createPersona(@PathParam("personaId") String personaId) {
        return profileService.createPersona(personaId);
    }

    /**
     * Returns sessions for the persona with the given ID.
     *
     * @param personaId the persona identifier
     * @param offset zero-based index of the first result
     * @param size maximum number of results to return, or {@code -1} for all matches
     * @param sortBy optional comma-separated sort fields with optional {@code :asc} or {@code :desc}
     * @return a paged list of persona sessions
     * @api.status 200 org.apache.unomi.api.PartialList Persona sessions page (list items are PersonaSession).
     * @api.example {"list":[{"itemId":"ps-1","profileId":"persona-vip","scope":"mysite"}],"offset":0,"pageSize":1,"totalSize":1,"totalSizeRelation":"EQUAL"}
     */
    @GET
    @Path("/personas/{personaId}/sessions")
    public PartialList<PersonaSession> getPersonaSessions(@PathParam("personaId") String personaId,
                                                   @QueryParam("offset") @DefaultValue("0") int offset,
                                                   @QueryParam("size") @DefaultValue("50") int size,
                                                   @QueryParam("sort") String sortBy) {
        return profileService.getPersonaSessions(personaId, offset, size, sortBy);
    }

    /**
     * Returns the session with the given ID.
     * When the session does not exist the endpoint may return {@code null} (HTTP 200 with empty body).
     *
     * @param sessionId the session identifier
     * @return the session
     * @throws ParseException if a stored date hint cannot be parsed
     * @api.status 200 org.apache.unomi.api.Session Session found, or empty body when missing.
     * @api.example {"itemId":"session-1","itemType":"session","profileId":"profile-1","scope":"mysite","size":4,"duration":1800000}
     */
    @GET
    @Path("/sessions/{sessionId}")
    public Session loadSession(@PathParam("sessionId") String sessionId) throws ParseException {
        return profileService.loadSession(sessionId);
    }

    /**
     * Saves the specified session.
     *
     * @param session the session to be saved
     * @return the newly saved session
     * @api.status 200 org.apache.unomi.api.Session Session saved.
     * @api.status 400 empty Invalid or unreadable session body.
     * @api.example {"itemId":"session-1","itemType":"session","profileId":"profile-1","scope":"mysite"}
     */
    @POST
    @Path("/sessions/{sessionId}")
    public Session saveSession(Session session) {
        return profileService.saveSession(session);
    }

    /**
     * Deletes the session with the given ID.
     *
     * @param sessionId the session identifier
     * @api.status 204 empty Session deleted.
     * @api.example {"itemId":"session-1","itemType":"session","profileId":"profile-42","scope":"mysite"}
     */
    @DELETE
    @Path("/sessions/{sessionId}")
    public void deleteSession(@PathParam("sessionId") String sessionId) {
        profileService.deleteSession(sessionId);
    }

    /**
     * Returns events for the session with the given ID.
     * <p>
     * Results can be filtered by event type, optional full-text query, and paging parameters.
     *
     * @param sessionId the session identifier
     * @param eventTypes event types to include; an event must match at least one
     * @param query optional full-text filter
     * @param offset zero-based index of the first result
     * @param size maximum number of results to return, or {@code -1} for all matches
     * @param sortBy optional comma-separated sort fields with optional {@code :asc} or {@code :desc}
     * @return a paged list of matching events
     * @api.status 200 org.apache.unomi.api.PartialList Events page (list items are Event).
     * @api.example {"list":[{"itemId":"evt-1","itemType":"event","eventType":"view","sessionId":"session-1","profileId":"profile-1","scope":"mysite"}],"offset":0,"pageSize":1,"totalSize":1,"totalSizeRelation":"EQUAL"}
     */
    @GET
    @Path("/sessions/{sessionId}/events")
    public PartialList<Event> getSessionEvents(@PathParam("sessionId") String sessionId,
                                               @QueryParam("eventTypes") String[] eventTypes,
                                               @QueryParam("q") String query,
                                               @QueryParam("offset") @DefaultValue("0") int offset,
                                               @QueryParam("size") @DefaultValue("50") int size,
                                               @QueryParam("sort") String sortBy) {
        return eventService.searchEvents(sessionId, eventTypes, query, offset, size, sortBy);
    }

    /**
     * Finds sessions for the given profile.
     *
     * @param profileId the profile identifier
     * @return the matching sessions, or {@code null} when not implemented
     */
    public PartialList<Session> findProfileSessions(String profileId) {
        return null;
    }

    /**
     * Tests whether a condition matches the given profile and session.
     *
     * @param condition the condition to test
     * @param profile the profile
     * @param session the session
     * @return {@code true} when the condition matches
     */
    public boolean matchCondition(Condition condition, Profile profile, Session session) {
        return profileService.matchCondition(condition, profile, session);
    }

    /**
     * Returns property types already in use for the given item type and tag.
     *
     * Property-type lookup helpers that may move to a dedicated endpoint in a future release.
     *
     * @param tag the tag or system tag to match
     * @param isSystemTag whether {@code tag} is a system tag
     * @param itemType the item type name from the class {@code ITEM_TYPE} field
     * @param language the requested locale for property descriptions (currently unused)
     * @param response the HTTP response used to signal missing query parameters
     * @return matching property types, or {@code null} when required parameters are missing
     * @throws IOException if sending the error response fails
     * @api.status 200 array org.apache.unomi.api.PropertyType Property types already used for the item type/tag.
     * @api.status 400 empty Missing mandatory {@code tag} or {@code itemType} query parameter.
     * @api.example [{"itemId":"email","itemType":"propertyType","metadata":{"id":"email","name":"Email","scope":"systemscope"},"target":"profiles","valueTypeId":"email"}]
     */
    @GET
    @Path("/existingProperties")
    public Collection<PropertyType> getExistingProperties(@QueryParam("tag") String tag, @QueryParam("isSystemTag") boolean isSystemTag, @QueryParam("itemType") String itemType, @HeaderParam("Accept-Language") String language, @Context final HttpServletResponse response) throws IOException {
        if (StringUtils.isBlank(tag) || StringUtils.isBlank(itemType)) {
            response.sendError(Response.Status.BAD_REQUEST.getStatusCode(), "Missing mandatory query parameters when requesting /cxs/profiles/existingProperties, mandatory query parameters are tag and itemType");
            return null;
        }
        Set<PropertyType> properties;
        if (isSystemTag) {
            properties = profileService.getExistingProperties(tag, itemType, isSystemTag);
        } else {
            properties = profileService.getExistingProperties(tag, itemType);
        }
        return properties;
    }

    /**
     * Returns all known property types grouped by target.
     *
     * Property-type lookup helpers that may move to a dedicated endpoint in a future release.
     *
     * @param language the requested locale for property descriptions (currently unused)
     * @return target name to property type mappings
     * @api.status 200 empty Map of target name to property type collections.
     * @api.example {"profiles":[{"itemId":"email","itemType":"propertyType","metadata":{"id":"email","name":"Email","scope":"systemscope"},"target":"profiles","valueTypeId":"email"}]}
     */
    @GET
    @Path("/properties")
    public Map<String, Collection<PropertyType>> getPropertyTypes(@HeaderParam("Accept-Language") String language) {
        return profileService.getTargetPropertyTypes();
    }

    /**
     * Returns the property type for the given property ID.
     *
     * Property-type lookup helpers that may move to a dedicated endpoint in a future release.
     *
     * @param propertyId the property identifier
     * @param language the requested locale for property descriptions (currently unused)
     * @return the property type
     * @api.status 200 org.apache.unomi.api.PropertyType Property type found, or empty body when missing.
     * @api.example {"itemId":"email","itemType":"propertyType","metadata":{"id":"email","name":"Email","scope":"systemscope"},"target":"profiles","valueTypeId":"email"}
     */
    @GET
    @Path("/properties/{propertyId}")
    public PropertyType getPropertyType(@PathParam("propertyId") String propertyId, @HeaderParam("Accept-Language") String language) {
        return profileService.getPropertyType(propertyId);
    }

    /**
     * Returns property types for the given target.
     *
     * Property-type lookup helpers that may move to a dedicated endpoint in a future release.
     *
     * @param target the property target name
     * @param language the requested locale for property descriptions (currently unused)
     * @return property types for the target
     * @api.status 200 array org.apache.unomi.api.PropertyType Property types for the target.
     * @api.example [{"itemId":"email","itemType":"propertyType","metadata":{"id":"email","name":"Email","scope":"systemscope"},"target":"profiles","valueTypeId":"email"},{"itemId":"firstName","itemType":"propertyType","metadata":{"id":"firstName","name":"First Name","scope":"systemscope"},"target":"profiles","valueTypeId":"text"}]
     */
    @GET
    @Path("/properties/targets/{target}")
    public Collection<PropertyType> getPropertyTypesByTarget(@PathParam("target") String target, @HeaderParam("Accept-Language") String language) {
        return profileService.getTargetPropertyTypes(target);
    }

    /**
     * Returns property types that match any of the given tags.
     *
     * Property-type lookup helpers that may move to a dedicated endpoint in a future release.
     * Tags are passed as a comma-separated path segment for backward compatibility.
     *
     * @param tags comma-separated tag identifiers
     * @param language the requested locale for property descriptions (currently unused)
     * @return matching property types
     * @api.status 200 array org.apache.unomi.api.PropertyType Property types matching any of the tags.
     * @api.example [{"itemId":"email","itemType":"propertyType","metadata":{"id":"email","name":"Email","scope":"systemscope","tags":["contact"]},"target":"profiles","valueTypeId":"email"}]
     */
    @GET
    @Path("/properties/tags/{tags}")
    public Collection<PropertyType> getPropertyTypeByTag(@PathParam("tags") String tags, @HeaderParam("Accept-Language") String language) {
        String[] tagsArray = tags.split(",");
        Set<PropertyType> results = new LinkedHashSet<>();
        for (String tag : tagsArray) {
            results.addAll(profileService.getPropertyTypeByTag(tag));
        }
        return results;
    }

    /**
     * Returns property types that match any of the given system tags.
     *
     * Property-type lookup helpers that may move to a dedicated endpoint in a future release.
     * Tags are passed as a comma-separated path segment for backward compatibility.
     *
     * @param tags comma-separated system tag identifiers
     * @param language the requested locale for property descriptions (currently unused)
     * @return matching property types
     * @api.status 200 array org.apache.unomi.api.PropertyType Property types matching any of the system tags.
     * @api.example [{"itemId":"email","itemType":"propertyType","metadata":{"id":"email","name":"Email","scope":"systemscope","systemTags":["contactInfo"]},"target":"profiles","valueTypeId":"email"}]
     */
    @GET
    @Path("/properties/systemTags/{tags}")
    public Collection<PropertyType> getPropertyTypeBySystemTag(@PathParam("tags") String tags, @HeaderParam("Accept-Language") String language) {
        String[] tagsArray = tags.split(",");
        Set<PropertyType> results = new LinkedHashSet<>();
        for (String tag : tagsArray) {
            results.addAll(profileService.getPropertyTypeBySystemTag(tag));
        }
        return results;
    }

    /**
     * Persists the specified property type in the context server.
     *
     * Property-type lookup helpers that may move to a dedicated endpoint in a future release.
     *
     * @param property the property type to persist
     * @return {@code true} if the property type was properly created, {@code false} otherwise (for example if it already existed)
     * @api.status 200 empty {@code true} when created/updated, {@code false} when not persisted.
     * @api.status 400 empty Invalid or unreadable property type body.
     * @api.example true
     */
    @POST
    @Path("/properties")
    public boolean setPropertyType(PropertyType property) {
        return profileService.setPropertyType(property);
    }

    /**
     * Persists the specified properties type in the context server.
     *
     * Property-type lookup helpers that may move to a dedicated endpoint in a future release.
     *
     * @param properties the properties type to persist
     * @return {@code true} if at least one property type was persisted, {@code false} otherwise
     * @api.status 200 empty {@code true} when at least one property type was saved.
     * @api.status 400 empty Invalid or unreadable property type list body.
     * @api.example true
     */
    @POST
    @Path("/properties/bulk")
    public boolean setPropertyTypes(List<PropertyType> properties) {
        boolean saved = false;
        for (PropertyType property : properties) {
            saved |= profileService.setPropertyType(property);
        }
        return saved;
    }

    /**
     * Deletes the property type identified by the specified identifier.
     *
     * Property-type lookup helpers that may move to a dedicated endpoint in a future release.
     *
     * @param propertyId the identifier of the property type to delete
     * @return {@code true} if the property type was properly deleted, {@code false} otherwise
     * @api.status 200 empty {@code true} when deleted, {@code false} when not found / not deleted.
     * @api.example true
     */
    @DELETE
    @Path("/properties/{propertyId}")
    public boolean deleteProperty(@PathParam("propertyId") String propertyId) {
        return profileService.deletePropertyType(propertyId);
    }

    /**
     * Returns sessions matching the given query.
     *
     * @param query the search query
     * @return a paged list of matching sessions
     * @api.status 200 org.apache.unomi.api.PartialList Sessions page (list items are Session).
     * @api.status 400 empty Invalid or unreadable query body.
     * @api.example {"list":[{"itemId":"session-1","itemType":"session","profileId":"profile-1","scope":"mysite"}],"offset":0,"pageSize":1,"totalSize":1,"totalSizeRelation":"EQUAL"}
     */
    @POST
    @Path("/search/sessions")
    public PartialList<Session> searchSession(Query query) {
        return profileService.searchSessions(query);
    }

    /**
     * Adds an alias to a profile.
     * Links a client-specific alias id to the canonical {@code profileId}. When {@code X-Unomi-ClientId} is omitted, {@code defaultClientId} is used.
     *
     * @param profileId the profile identifier
     * @param aliasId the alias identifier
     * @param headerClientID optional client identifier from the request header
     * @api.status 204 empty Alias linked to the profile.
     * @api.example {"itemId":"alias-crm-9","itemType":"profileAlias","profileID":"profile-42","clientID":"web-tracker"}
     */
    @POST
    @Path("/{profileId}/aliases/{aliasId}")
    public void addAliasToProfile(final @PathParam("profileId") String profileId,
                                  final @PathParam("aliasId") String aliasId,
                                  final @HeaderParam("X-Unomi-ClientId") String headerClientID) {
        String clientId = headerClientID != null ? headerClientID : "defaultClientId";
        profileService.addAliasToProfile(profileId, aliasId, clientId);
    }

    /**
     * Removes an alias from a profile.
     *
     * @param profileId the profile identifier
     * @param aliasId the alias identifier
     * @param headerClientID optional client identifier from the request header
     * @api.status 204 empty Alias removed from the profile.
     * @api.example {"itemId":"alias-crm-9","itemType":"profileAlias","profileID":"profile-42","clientID":"web-tracker"}
     */
    @DELETE
    @Path("/{profileId}/aliases/{aliasId}")
    public void removeAliasFromProfile(final @PathParam("profileId") String profileId,
                                       final @PathParam("aliasId") String aliasId,
                                       final @HeaderParam("X-Unomi-ClientId") String headerClientID) {
        String clientId = headerClientID != null ? headerClientID : "defaultClientId";
        profileService.removeAliasFromProfile(profileId, aliasId, clientId);
    }

    /**
     * Lists aliases for the given profile.
     *
     * @param profileId the profile identifier
     * @param offset pagination offset
     * @param size page size
     * @param sortBy optional sort field
     * @return the matching profile aliases
     * @api.status 200 org.apache.unomi.api.PartialList Aliases page (list items are ProfileAlias).
     * @api.example {"list":[{"itemId":"alias-crm-9","itemType":"profileAlias","profileID":"profile-1","clientID":"web-tracker"}],"offset":0,"pageSize":1,"totalSize":1,"totalSizeRelation":"EQUAL"}
     */
    @GET
    @Path("/{profileId}/aliases")
    public PartialList<ProfileAlias> listAliasesByProfileId(final @PathParam("profileId") String profileId,
                                                            @QueryParam("offset") @DefaultValue("0") int offset,
                                                            @QueryParam("size") @DefaultValue("50") int size,
                                                            @QueryParam("sort") String sortBy) {
        return profileService.findProfileAliases(profileId, offset, size, sortBy);
    }
}
