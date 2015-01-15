package org.oasis_open.contextserver.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.oasis_open.contextserver.api.*;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListInitializer;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListValue;
import org.oasis_open.contextserver.api.services.ProfileService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.*;

@WebService
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
public class ProfileServiceEndPoint {

    private ProfileService profileService;
    private BundleContext bundleContext;
    private ResourceBundleHelper resourceBundleHelper;

    public ProfileServiceEndPoint() {
        System.out.println("Initializing profile service endpoint...");
    }

    @WebMethod(exclude = true)
    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    @WebMethod(exclude = true)
    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    @WebMethod(exclude = true)
    public void setResourceBundleHelper(ResourceBundleHelper resourceBundleHelper) {
        this.resourceBundleHelper = resourceBundleHelper;
    }

    @GET
    @Path("/")
    public PartialList<Profile> getAllProfiles() {
        return profileService.getAllProfiles();
    }

    @GET
    @Path("/count")
    public long getAllProfilesCount() {
        return profileService.getAllProfilesCount();
    }

    @GET
    @Path("/search")
    public PartialList<Profile> getProfiles(@QueryParam("q") String query,
                                            @QueryParam("offset") @DefaultValue("0") int offset,
                                            @QueryParam("size") @DefaultValue("50") int size,
                                            @QueryParam("sort") String sortBy) {
        return profileService.getProfiles(query, offset, size, sortBy);
    }

    @WebMethod(exclude = true)
    public PartialList<Profile> findProfilesByPropertyValue(String propertyName, String propertyValue) {
        return profileService.findProfilesByPropertyValue(propertyName, propertyValue);
    }

    @WebMethod(exclude = true)
    public Profile mergeProfilesOnProperty(Profile currentProfile, Session currentSession, String propertyName, String propertyValue) {
        return profileService.mergeProfilesOnProperty(currentProfile, currentSession, propertyName, propertyValue);
    }

    @GET
    @Path("/{profileId}")
    public Profile load(@PathParam("profileId") String profileId) {
        return profileService.load(profileId);
    }

    @POST
    @Path("/{profileId}")
    public void save(Profile profile) {
        profileService.save(profile);
    }

    @DELETE
    @Path("/{profileId}")
    public void delete(@PathParam("profileId") String profileId, @QueryParam("persona") @DefaultValue("false") boolean persona) {
        profileService.delete(profileId, false);
    }

    @GET
    @Path("/{profileId}/sessions")
    public PartialList<Session> getProfileSessions(@PathParam("profileId") String profileId,
                                                   @QueryParam("offset") @DefaultValue("0") int offset,
                                                   @QueryParam("size") @DefaultValue("50") int size,
                                                   @QueryParam("sort") String sortBy) {
        return profileService.getProfileSessions(profileId, offset, size, sortBy);
    }

    @GET
    @Path("/properties")
    public Collection<RESTPropertyType> getAllPropertyTypes(@HeaderParam("Accept-Language") String language) {
        return generatePropertyTypes(profileService.getAllPropertyTypes(), language);
    }

    @GET
    @Path("/properties/tags/{tagId}")
    public Collection<RESTPropertyType> getPropertyTypes(@PathParam("tagId") String tagId, @QueryParam("recursive") @DefaultValue("false") boolean recursive, @HeaderParam("Accept-Language") String language) {
        return generatePropertyTypes(profileService.getPropertyTypes(tagId, recursive), language);
    }

    @GET
    @Path("/properties/mappings/{fromPropertyTypeId}")
    public String getPropertyTypeMapping(@PathParam("fromPropertyTypeId") String fromPropertyTypeId) {
        return profileService.getPropertyTypeMapping(fromPropertyTypeId);
    }

    @GET
    @Path("/personas")
    public PartialList<Persona> getPersonas(@QueryParam("offset") @DefaultValue("0") int offset,
                                            @QueryParam("size") @DefaultValue("50") int size,
                                            @QueryParam("sort") String sortBy) {
        return profileService.getPersonas(offset, size, sortBy);
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
    @Path("/personas/{personaId}")
    public void savePersona(Persona persona) {
        profileService.save(persona);
    }

    @DELETE
    @Path("/personas/{personaId}")
    public void deletePersona(Persona persona) {
        profileService.delete(persona.getId(), true);
    }

    @PUT
    @Path("/personas/{personaId}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public void createPersona(@PathParam("personaId") String personaId) {
        profileService.createPersona(personaId);
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
    public boolean saveSession(Session session) {
        return profileService.saveSession(session);
    }

    @WebMethod(exclude = true)
    public PartialList<Session> findProfileSessions(String profileId) {
        return null;
    }

    @WebMethod(exclude = true)
    public boolean matchCondition(Condition condition, Profile profile, Session session) {
        return profileService.matchCondition(condition, profile, session);
    }

    private Collection<RESTPropertyType> generatePropertyTypes(Collection<PropertyType> type, String language) {
        Set<RESTPropertyType> result = new LinkedHashSet<>();
        for (PropertyType propertyType : type) {
            result.add(generatePropertyType(propertyType, resourceBundleHelper.getResourceBundle(propertyType, language)));
        }
        return result;
    }

    private RESTPropertyType generatePropertyType(PropertyType type, ResourceBundle bundle) {
        RESTPropertyType result = new RESTPropertyType();
        result.setId(type.getId());
        result.setName(resourceBundleHelper.getResourceBundleValue(bundle, type.getId()));
        result.setValueTypeId(type.getValueTypeId());
        result.setDefaultValue(type.getDefaultValue());
        result.setRank(type.getRank());
        result.setTags(type.getTagIds());
        result.setAutomaticMappingsFrom(type.getAutomaticMappingsFrom());
        result.setMergeStrategy(type.getMergeStrategy());
        result.setSelectorId(type.getSelectorId());

        ArrayList<ChoiceListValue> choiceListValues = new ArrayList<ChoiceListValue>();
        result.setChoiceListValues(choiceListValues);
        if (type.getChoiceListInitializerFilter() != null && type.getChoiceListInitializerFilter().length() > 0) {
            try {
                Collection<ServiceReference<ChoiceListInitializer>> matchingChoiceListInitializerReferences = bundleContext.getServiceReferences(ChoiceListInitializer.class, type.getChoiceListInitializerFilter());
                for (ServiceReference<ChoiceListInitializer> choiceListInitializerReference : matchingChoiceListInitializerReferences) {
                    ChoiceListInitializer choiceListInitializer = bundleContext.getService(choiceListInitializerReference);
                    for (ChoiceListValue value : choiceListInitializer.getValues(null)) {
                        choiceListValues.add(value.localizedCopy(resourceBundleHelper.getResourceBundleValue(bundle, value.getName())));
                    }
                }
            } catch (InvalidSyntaxException e) {
                e.printStackTrace();
            }
        }

        return result;
    }

}
