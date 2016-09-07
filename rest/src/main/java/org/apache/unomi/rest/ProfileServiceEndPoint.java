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

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.*;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * A JAX-RS endpoint to manage {@link Profile}s and {@link Persona}s.
 */
@WebService
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
public class ProfileServiceEndPoint {

    private static final Logger logger = LoggerFactory.getLogger(ProfileServiceEndPoint.class.getName());

    private ProfileService profileService;

    private EventService eventService;

    private SegmentService segmentService;

    private LocalizationHelper localizationHelper;

    public ProfileServiceEndPoint() {
        System.out.println("Initializing profile service endpoint...");
    }

    @WebMethod(exclude = true)
    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    @WebMethod(exclude = true)
    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    @WebMethod(exclude = true)
    public void setSegmentService(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

    @WebMethod(exclude = true)
    public void setLocalizationHelper(LocalizationHelper localizationHelper) {
        this.localizationHelper = localizationHelper;
    }

    /**
     * Retrieves the number of unique profiles.
     *
     * @return the number of unique profiles.
     */
    @GET
    @Path("/count")
    public long getAllProfilesCount() {
        return profileService.getAllProfilesCount();
    }

    /**
     * Retrieves profiles matching the specified query.
     *
     * @param query a {@link Query} specifying which elements to retrieve
     * @return a {@link PartialList} of profiles instances matching the specified query
     */
    @POST
    @Path("/search")
    public PartialList<Profile> getProfiles(Query query) {
        return profileService.search(query, Profile.class);
    }

    /**
     * Retrieves an export of profiles matching the specified query as a downloadable file using the comma-separated values (CSV) format.
     *
     * @param query a String JSON representation of the query the profiles to export should match
     * @return a Response object configured to allow caller to download the CSV export file
     */
    @GET
    @Path("/export")
    @Produces("text/csv")
    public Response getExportProfiles(@QueryParam("query") String query) {
        try {
            return exportProfiles(CustomObjectMapper.getObjectMapper().readValue(query, Query.class));
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
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
            logger.error(e.getMessage(), e);
            return Response.serverError().build();
        }
    }

    /**
     * Retrieves an export of profiles matching the specified query as a downloadable file using the comma-separated values (CSV) format.
     *
     * @param query a String JSON representation of the query the profiles to export should match
     * @return a Response object configured to allow caller to download the CSV export file
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
     * Update all profiles in batch according to the specified {@link BatchUpdate}
     *
     * @param update the batch update specification
     */
    @POST
    @Path("/batchProfilesUpdate")
    public void batchProfilesUpdate(BatchUpdate update) {
        profileService.batchProfilesUpdate(update);
    }

    /**
     * Retrieves the profile identified by the specified identifier.
     *
     * @param profileId the identifier of the profile to retrieve
     * @return the profile identified by the specified identifier or {@code null} if no such profile exists
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
        if (profileService.saveOrMerge(profile)) {
            profile = profileService.load(profile.getItemId());
            Event profileUpdated = new Event("profileUpdated", null, profile, null, null, profile, new Date());
            profileUpdated.setPersistent(false);
            int changes = eventService.send(profileUpdated);
            if ((changes & EventService.PROFILE_UPDATED) == EventService.PROFILE_UPDATED) {
                profileService.save(profile);
            }
        }

        return profile;
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
        profileService.delete(profileId, false);
    }

    /**
     * Retrieves the sessions associated with the profile identified by the specified identifier that match the specified query (if specified), ordered according to the specified
     * {@code sortBy} String and and paged: only {@code size} of them are retrieved, starting with the {@code offset}-th one.
     *
     * TODO: use a Query object instead of distinct parameter?
     *
     * @param profileId the identifier of the profile we want to retrieve sessions from
     * @param query     a String of text used for fulltext filtering which sessions we are interested in or {@code null} (or an empty String) if we want to retrieve all sessions
     * @param offset    zero or a positive integer specifying the position of the first session in the total ordered collection of matching sessions
     * @param size      a positive integer specifying how many matching sessions should be retrieved or {@code -1} if all of them should be retrieved
     * @param sortBy    an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering
     *                  elements according to the property order in the
     *                  String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally followed by
     *                  a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @return a {@link PartialList} of matching sessions
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
     * Retrieves the list of segment metadata for the segments the specified profile is a member of.
     *
     * @param profileId the identifier of the profile for which we want to retrieve the segment metadata
     * @return the (possibly empty) list of segment metadata for the segments the specified profile is a member of
     */
    @GET
    @Path("/{profileId}/segments")
    public List<Metadata> getProfileSegments(@PathParam("profileId") String profileId) {
        Profile profile = profileService.load(profileId);
        return segmentService.getSegmentMetadatasForProfile(profile);
    }

    /**
     * TODO
     *
     * @param fromPropertyTypeId
     * @return
     */
    @GET
    @Path("/properties/mappings/{fromPropertyTypeId}")
    public String getPropertyTypeMapping(@PathParam("fromPropertyTypeId") String fromPropertyTypeId) {
        return profileService.getPropertyTypeMapping(fromPropertyTypeId);
    }

    /**
     * Retrieves {@link Persona} matching the specified query.
     *
     * @param query a {@link Query} specifying which elements to retrieve
     * @return a {@link PartialList} of Persona instances matching the specified query
     */
    @POST
    @Path("/personas/search")
    public PartialList<Persona> getPersonas(Query query) {
        return profileService.search(query, Persona.class);
    }

    /**
     * Retrieves the {@link Persona} identified by the specified identifier.
     *
     * @param personaId the identifier of the persona to retrieve
     * @return the persona identified by the specified identifier or {@code null} if no such persona exists
     */
    @GET
    @Path("/personas/{personaId}")
    public Persona loadPersona(@PathParam("personaId") String personaId) {
        return profileService.loadPersona(personaId);
    }

    /**
     * Retrieves the persona identified by the specified identifier and all its associated sessions
     *
     * @param personaId the identifier of the persona to retrieve
     * @return a {@link PersonaWithSessions} instance with the persona identified by the specified identifier and all its associated sessions
     */
    @GET
    @Path("/personasWithSessions/{personaId}")
    public PersonaWithSessions loadPersonaWithSessions(@PathParam("personaId") String personaId) {
        return profileService.loadPersonaWithSessions(personaId);
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
     */
    @DELETE
    @Path("/personas/{personaId}")
    public void deletePersona(@PathParam("personaId") String personaId) {
        profileService.delete(personaId, true);
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
     * Retrieves the sessions associated with the persona identified by the specified identifier, ordered according to the specified {@code sortBy} String and and paged: only
     * {@code size} of them are retrieved, starting with the {@code offset}-th one.
     *
     * @param personaId the persona id
     * @param offset    zero or a positive integer specifying the position of the first session in the total ordered collection of matching sessions
     * @param size      a positive integer specifying how many matching sessions should be retrieved or {@code -1} if all of them should be retrieved
     * @param sortBy    an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering
     *                  elements according to the property order in the
     *                  String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally followed by
     *                  a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @return a {@link PartialList} of sessions for the persona identified by the specified identifier
     */
    @GET
    @Path("/personas/{personaId}/sessions")
    public PartialList<Session> getPersonaSessions(@PathParam("personaId") String personaId,
                                                   @QueryParam("offset") @DefaultValue("0") int offset,
                                                   @QueryParam("size") @DefaultValue("50") int size,
                                                   @QueryParam("sort") String sortBy) {
        return profileService.getPersonaSessions(personaId, offset, size, sortBy);
    }

    /**
     * Retrieves the session identified by the specified identifier.
     *
     * @param sessionId the identifier of the session to be retrieved
     * @param dateHint  a Date helping in identifying where the item is located
     * @return the session identified by the specified identifier
     * @throws ParseException if the date hint cannot be parsed as a proper {@link Date} object
     */
    @GET
    @Path("/sessions/{sessionId}")
    public Session loadSession(@PathParam("sessionId") String sessionId, @QueryParam("dateHint") String dateHint) throws ParseException {
        return profileService.loadSession(sessionId, dateHint != null ? new SimpleDateFormat("yyyy-MM").parse(dateHint) : null);
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
     * Retrieves {@link Event}s for the {@link Session} identified by the provided session identifier, matching any of the provided event types,
     * ordered according to the specified {@code sortBy} String and paged: only {@code size} of them are retrieved, starting with the {@code offset}-th one.
     * If a {@code query} is provided, a full text search is performed on the matching events to further filter them.
     *
     * @param sessionId  the identifier of the user session we're considering
     * @param eventTypes an array of event type names; the events to retrieve should at least match one of these
     * @param query      a String to perform full text filtering on events matching the other conditions
     * @param offset     zero or a positive integer specifying the position of the first event in the total ordered collection of matching events
     * @param size       a positive integer specifying how many matching events should be retrieved or {@code -1} if all of them should be retrieved
     * @param sortBy     an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering
     *                   elements according to the property order in
     *                   the String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally followed by
     *                   a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @return a {@link PartialList} of matching events
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

    @WebMethod(exclude = true)
    public PartialList<Session> findProfileSessions(String profileId) {
        return null;
    }

    @WebMethod(exclude = true)
    public boolean matchCondition(Condition condition, Profile profile, Session session) {
        return profileService.matchCondition(condition, profile, session);
    }

    /**
     * Retrieves the existing property types for the specified type as defined by the Item subclass public field {@code ITEM_TYPE} and with the specified tag.
     *
     * TODO: move to a different class
     *
     * @param tagId    the tag we're interested in
     * @param itemType the String representation of the item type we want to retrieve the count of, as defined by its class' {@code ITEM_TYPE} field
     * @param language the value of the {@code Accept-Language} header to specify in which locale the properties description should be returned TODO unused
     * @return all property types defined for the specified item type and with the specified tag
     */
    @GET
    @Path("/existingProperties")
    public Collection<PropertyType> getExistingProperties(@QueryParam("tagId") String tagId, @QueryParam("itemType") String itemType, @HeaderParam("Accept-Language") String language) {
        Set<PropertyType> properties = profileService.getExistingProperties(tagId, itemType);
        return properties;
    }

    /**
     * Retrieves all known property types.
     *
     * TODO: move to a different class
     *
     * @param language the value of the {@code Accept-Language} header to specify in which locale the properties description should be returned TODO unused
     * @return a Map associating targets as keys to related {@link PropertyType}s
     */
    @GET
    @Path("/properties")
    public Map<String, Collection<PropertyType>> getPropertyTypes(@HeaderParam("Accept-Language") String language) {
        return profileService.getAllPropertyTypes();
    }

    /**
     * Retrieves all the property types associated with the specified target.
     *
     * TODO: move to a different class
     *
     * @param target   the target for which we want to retrieve the associated property types
     * @param language the value of the {@code Accept-Language} header to specify in which locale the properties description should be returned TODO unused
     * @return a collection of all the property types associated with the specified target
     */
    @GET
    @Path("/properties/{target}")
    public Collection<PropertyType> getPropertyTypesByTarget(@PathParam("target") String target, @HeaderParam("Accept-Language") String language) {
        return profileService.getAllPropertyTypes(target);
    }

    /**
     * Retrieves all property types with the specified tags also retrieving property types with sub-tags of the specified tags if so specified.
     *
     * TODO: move to a different class
     * TODO: passing a list of tags via a comma-separated list is not very RESTful
     *
     * @param tags      a comma-separated list of tag identifiers
     * @param recursive {@code true} if sub-tags of the specified tag should also be considered, {@code false} otherwise
     * @param language  the value of the {@code Accept-Language} header to specify in which locale the properties description should be returned TODO unused
     * @return a Set of the property types with the specified tag
     */
    @GET
    @Path("/properties/tags/{tagId}")
    public Collection<PropertyType> getPropertyTypeByTag(@PathParam("tagId") String tags, @QueryParam("recursive") @DefaultValue("false") boolean recursive, @HeaderParam("Accept-Language") String language) {
        String[] tagsArray = tags.split(",");
        Set<PropertyType> results = new LinkedHashSet<>();
        for (String s : tagsArray) {
            results.addAll(profileService.getPropertyTypeByTag(s, recursive));
        }
        return results;
    }

    /**
     * Persists the specified property type in the context server.
     *
     * TODO: move to a different class
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
     * Deletes the property type identified by the specified identifier.
     *
     * TODO: move to a different class
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
     * Retrieves sessions matching the specified query.
     *
     * @param query a {@link Query} specifying which elements to retrieve
     * @return a {@link PartialList} of sessions matching the specified query
     */
    @POST
    @Path("/search/sessions")
    public PartialList<Session> searchSession(Query query) {
        return profileService.searchSessions(query);
    }
}
