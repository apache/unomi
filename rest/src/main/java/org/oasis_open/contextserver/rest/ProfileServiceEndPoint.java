package org.oasis_open.contextserver.rest;

/*
 * #%L
 * context-server-rest
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.oasis_open.contextserver.api.*;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.query.Query;
import org.oasis_open.contextserver.api.services.EventService;
import org.oasis_open.contextserver.api.services.ProfileService;
import org.oasis_open.contextserver.api.services.SegmentService;
import org.oasis_open.contextserver.persistence.spi.CustomObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

@WebService
@Produces(MediaType.APPLICATION_JSON)
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

    @GET
    @Path("/count")
    public long getAllProfilesCount() {
        return profileService.getAllProfilesCount();
    }

    @POST
    @Path("/search")
    public PartialList<Profile> getProfiles(Query query) {
        return profileService.search(query, Profile.class);
    }

    @GET
    @Path("/export")
    @Produces("text/csv")
    public Response getExportProfiles(@QueryParam("query") String query) {
        try {
            Query queryObject = CustomObjectMapper.getObjectMapper().readValue(query, Query.class);
            Response.ResponseBuilder response = Response.ok(profileService.exportProfilesPropertiesToCsv(queryObject));
            response.header("Content-Disposition",
                    "attachment; filename=Profiles_export_" + new SimpleDateFormat("yyyy-MM-dd-HH-mm").format(new Date()) + ".csv");
            return response.build();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return Response.serverError().build();
        }
    }

    @GET
    @Path("/export")
    @Produces("text/csv")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response formExportProfiles(@FormParam("query") String query) {
        try {
            Query queryObject = CustomObjectMapper.getObjectMapper().readValue(query, Query.class);
            Response.ResponseBuilder response = Response.ok(profileService.exportProfilesPropertiesToCsv(queryObject));
            response.header("Content-Disposition",
                    "attachment; filename=Profiles_export_" + new SimpleDateFormat("yyyy-MM-dd-HH-mm").format(new Date()) + ".csv");
            return response.build();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return Response.serverError().build();
        }
    }

    @POST
    @Path("/batchProfilesUpdate")
    public void batchProfilesUpdate(BatchUpdate update) {
        profileService.batchProfilesUpdate(update);
    }

    @GET
    @Path("/{profileId}")
    public Profile load(@PathParam("profileId") String profileId) {
        return profileService.load(profileId);
    }

    @POST
    @Path("/")
    public Profile save(Profile profile) {
        Event profileUpdated = new Event("profileUpdated", null, profile, null, null, profile, new Date());
        profileUpdated.setPersistent(false);
        eventService.send(profileUpdated);
        return profileService.save(profile);
    }

    @DELETE
    @Path("/{profileId}")
    public void delete(@PathParam("profileId") String profileId, @QueryParam("persona") @DefaultValue("false") boolean persona) {
        profileService.delete(profileId, false);
    }

    @GET
    @Path("/{profileId}/sessions")
    public PartialList<Session> getProfileSessions(@PathParam("profileId") String profileId,
                                                   @QueryParam("q") String query,
                                                   @QueryParam("offset") @DefaultValue("0") int offset,
                                                   @QueryParam("size") @DefaultValue("50") int size,
                                                   @QueryParam("sort") String sortBy) {
        return profileService.getProfileSessions(profileId, query, offset, size, sortBy);
    }

    @GET
    @Path("/{profileId}/segments")
    public List<Metadata> getProfileSegments(@PathParam("profileId") String profileId) {
        Profile profile = profileService.load(profileId);
        return segmentService.getSegmentMetadatasForProfile(profile);
    }

    @GET
    @Path("/properties/mappings/{fromPropertyTypeId}")
    public String getPropertyTypeMapping(@PathParam("fromPropertyTypeId") String fromPropertyTypeId) {
        return profileService.getPropertyTypeMapping(fromPropertyTypeId);
    }

    @POST
    @Path("/personas/search")
    public PartialList<Persona> getPersonas(Query query) {
        return profileService.search(query, Persona.class);
    }

    @GET
    @Path("/personas/{personaId}")
    public Persona loadPersona(@PathParam("personaId") String personaId) {
        return profileService.loadPersona(personaId);
    }

    @GET
    @Path("/personasWithSessions/{personaId}")
    public PersonaWithSessions loadPersonaWithSessions(@PathParam("personaId") String personaId) {
        return profileService.loadPersonaWithSessions(personaId);
    }

    @POST
    @Path("/personas")
    public void savePersona(Persona persona) {
        profileService.save(persona);
    }

    @DELETE
    @Path("/personas/{personaId}")
    public void deletePersona(@PathParam("personaId") String personaId) {
        profileService.delete(personaId, true);
    }

    @PUT
    @Path("/personas/{personaId}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Persona createPersona(@PathParam("personaId") String personaId) {
        return profileService.createPersona(personaId);
    }

    @GET
    @Path("/personas/{personaId}/sessions")
    public PartialList<Session> getPersonaSessions(@PathParam("personaId") String personaId,
                                                   @QueryParam("offset") @DefaultValue("0") int offset,
                                                   @QueryParam("size") @DefaultValue("50") int size,
                                                   @QueryParam("sort") String sortBy) {
        return profileService.getPersonaSessions(personaId, offset, size, sortBy);
    }

    @GET
    @Path("/sessions/{sessionId}")
    public Session loadSession(@PathParam("sessionId") String sessionId, @QueryParam("dateHint") Date dateHint) {
        return profileService.loadSession(sessionId, dateHint);
    }

    @POST
    @Path("/sessions/{sessionId}")
    public Session saveSession(Session session) {
        return profileService.saveSession(session);
    }

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

    @GET
    @Path("/existingProperties")
    public Collection<PropertyType> getExistingProperties(@QueryParam("tagId") String tagId, @QueryParam("itemType") String itemType, @HeaderParam("Accept-Language") String language) {
        Set<PropertyType> properties = profileService.getExistingProperties(tagId, itemType);
        return properties;
    }

    @GET
    @Path("/properties")
    public Map<String, Collection<PropertyType>> getPropertyTypes(@HeaderParam("Accept-Language") String language) {
        return profileService.getAllPropertyTypes();
    }

    @GET
    @Path("/properties/{target}")
    public Collection<PropertyType> getPropertyTypesByTarget(@PathParam("target") String target, @HeaderParam("Accept-Language") String language) {
        return profileService.getAllPropertyTypes(target);
    }

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

    @POST
    @Path("/properties")
    public boolean createPropertyType(PropertyType property) {
        return profileService.createPropertyType(property);
    }

    @DELETE
    @Path("/properties/{propertyId}")
    public boolean deleteProperty(@PathParam("propertyId") String propertyId) {
        return profileService.deletePropertyType(propertyId);
    }

}
