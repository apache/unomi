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
     */
    @GET
    @Path("/count")
    public long getAllProfilesCount() {
        return profileService.getAllProfilesCount();
    }

    /**
     * Returns profiles matching the given query.
     *
     * @param query the search query
     * @return a paged list of matching profiles
     */
    @POST
    @Path("/search")
    public PartialList<Profile> getProfiles(Query query) {
        return profileService.search(query, Profile.class);
    }

    /**
     * Exports matching profiles as a downloadable CSV file.
     *
     * @param query JSON query string describing which profiles to export
     * @return a CSV download response
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
     *
     * @param update the batch update specification
     */
    @POST
    @Path("/batchProfilesUpdate")
    public void batchProfilesUpdate(BatchUpdate update) {
        profileService.batchProfilesUpdate(update);
    }

    /**
     * Returns the profile with the given ID.
     *
     * @param profileId the profile identifier
     * @return the profile, or {@code null} when it does not exist
     */
    @GET
    @Path("/{profileId}")
    public Profile load(@PathParam("profileId") String profileId) {
        return profileService.load(profileId);
    }

    /**
     * Saves the specified profile in the context server, sending a {@code profileUpdated} event.
     *
     * @param profile the profile to be saved
     * @return the newly saved profile
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
     */
    @POST
    @Path("/personas/search")
    public PartialList<Persona> getPersonas(Query query) {
        return profileService.search(query, Persona.class);
    }

    /**
     * Returns the persona with the given ID.
     *
     * @param personaId the persona identifier
     * @return the persona, or {@code null} when it does not exist
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
     *
     * @param sessionId the session identifier
     * @return the session
     * @throws ParseException if a stored date hint cannot be parsed
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
     * @return {@code true} if the property type was properly created, {@code false} otherwise (for example, if the property type already existed
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
     * @return {@code true} if the property type was properly created, {@code false} otherwise (for example, if the property type already existed
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
     */
    @POST
    @Path("/search/sessions")
    public PartialList<Session> searchSession(Query query) {
        return profileService.searchSessions(query);
    }

    /**
     * Adds an alias to a profile.
     *
     * @param profileId the profile identifier
     * @param aliasId the alias identifier
     * @param headerClientID optional client identifier from the request header
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
