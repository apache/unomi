package org.oasis_open.wemi.context.server.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.oasis_open.wemi.context.server.api.*;
import org.oasis_open.wemi.context.server.api.services.UserService;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Set;

/**
 * Created by loom on 27.08.14.
 */
@WebService
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
public class UserServiceEndPoint implements UserService {

    public UserService userService;

    public UserServiceEndPoint() {
        System.out.println("Initializing user service endpoint...");
    }

    @WebMethod(exclude = true)
    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    @GET
    @Path("/")
    public PartialList<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @GET
    @Path("/count")
    public long getAllUsersCount() {
        return userService.getAllUsersCount();
    }

    @GET
    @Path("/search")
    public PartialList<User> getUsers(@QueryParam("q") String query,
                                      @QueryParam("offset") @DefaultValue("0") int offset,
                                      @QueryParam("size") @DefaultValue("50") int size,
                                      @QueryParam("sort") String sortBy) {
        return userService.getUsers(query, offset, size, sortBy);
    }

    @WebMethod(exclude = true)
    public PartialList<User> findUsersByPropertyValue(String propertyName, String propertyValue) {
        return userService.findUsersByPropertyValue(propertyName, propertyValue);
    }

    @GET
    @Path("/{userId}")
    public User load(@PathParam("userId") String userId) {
        return userService.load(userId);
    }

    @POST
    @Path("/{userId}")
    public void save(User user) {
        userService.save(user);
    }

    @DELETE
    @Path("/{userId}")
    public void delete(User user) {
        userService.delete(user);
    }

    @GET
    @Path("/{userId}/sessions")
    public PartialList<Session> getUserSessions(@PathParam("userId") String userId,
                                                @QueryParam("offset") @DefaultValue("0") int offset,
                                                @QueryParam("size") @DefaultValue("50") int size,
                                                @QueryParam("sort") String sortBy) {
        return userService.getUserSessions(userId, offset, size, sortBy);
    }

    @GET
    @Path("/properties")
    public Set<PropertyType> getAllPropertyTypes() {
        return userService.getAllPropertyTypes();
    }

    @GET
    @Path("/properties/tags/{tagId}")
    public Set<PropertyType> getPropertyTypes(@PathParam("tagId") String tagId, @QueryParam("recursive") @DefaultValue("false") boolean recursive) {
        return userService.getPropertyTypes(tagId, recursive);
    }

    @GET
    @Path("/properties/mappings/{fromPropertyTypeId}")
    public String getPropertyTypeMapping(@PathParam("fromPropertyTypeId") String fromPropertyTypeId) {
        return userService.getPropertyTypeMapping(fromPropertyTypeId);
    }

    @GET
    @Path("/personas")
    public PartialList<Persona> getPersonas(@QueryParam("offset") @DefaultValue("0") int offset,
                                            @QueryParam("size") @DefaultValue("50") int size,
                                            @QueryParam("sort") String sortBy) {
        return userService.getPersonas(offset, size, sortBy);
    }

    @GET
    @Path("/personas/{personaId}")
    public Persona loadPersona(@PathParam("personaId") String personaId) {
        return userService.loadPersona(personaId);
    }

    @POST
    @Path("/personas/{personaId}")
    public void savePersona(Persona persona) {
        userService.save(persona);
    }

    @DELETE
    @Path("/personas/{personaId}")
    public void deletePersona(Persona persona) {
        userService.delete(persona);
    }

    @PUT
    @Path("/personas/{personaId}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public void createPersona(@PathParam("personaId") String personaId) {
        userService.createPersona(personaId);
    }

    @GET
    @Path("/personas/{personaId}/sessions")
    public PartialList<Session> getPersonaSessions(@PathParam("personaId") String personaId,
                                                   @QueryParam("offset") @DefaultValue("0") int offset,
                                                   @QueryParam("size") @DefaultValue("50") int size,
                                                   @QueryParam("sort") String sortBy) {
        return userService.getPersonaSessions(personaId, offset, size, sortBy);
    }

    @GET
    @Path("/sessions/{sessionId}")
    public Session loadSession(@PathParam("sessionId") String sessionId) {
        return userService.loadSession(sessionId);
    }

    @POST
    @Path("/sessions/{sessionId}")
    public boolean saveSession(Session session) {
        return userService.saveSession(session);
    }

    @WebMethod(exclude = true)
    public PartialList<Session> findUserSessions(String userId) {
        return null;
    }

    @WebMethod(exclude = true)
    public boolean matchCondition(String condition, User user, Session session) {
        return userService.matchCondition(condition, user, session);
    }

}
