package org.oasis_open.wemi.context.server.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.oasis_open.wemi.context.server.api.*;
import org.oasis_open.wemi.context.server.api.services.UserService;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.List;
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
    public Collection<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @GET
    @Path("/count")
    public long getAllUsersCount() {
        return userService.getAllUsersCount();
    }

    @GET
    @Path("/search")
    public Collection<User> getUsers(@QueryParam("q") String query, int offset, int size) {
        return userService.getUsers(query, offset, size);
    }

    @WebMethod(exclude = true)
    public List<User> findUsersByPropertyValue(String propertyName, String propertyValue) {
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
    @Path("/properties/groups")
    public Set<PropertyTypeGroup> getPropertyTypeGroups() {
        return userService.getPropertyTypeGroups();
    }

    @GET
    @Path("/properties")
    public Set<PropertyType> getAllPropertyTypes() {
        return userService.getAllPropertyTypes();
    }

    @GET
    @Path("/properties/groups/{groupId}")
    public Set<PropertyType> getPropertyTypes(@PathParam("groupId") String propertyGroupId) {
        return userService.getPropertyTypes(propertyGroupId);
    }

    @GET
    @Path("/properties/mappings/{fromPropertyTypeId}")
    public String getPropertyTypeMapping(@PathParam("fromPropertyTypeId") String fromPropertyTypeId) {
        return userService.getPropertyTypeMapping(fromPropertyTypeId);
    }

    @GET
    @Path("/personas")
    public Collection<Persona> getPersonas() {
        return userService.getPersonas();
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


    @WebMethod(exclude = true)
    public Session loadSession(String eventId) {
        return userService.loadSession(eventId);
    }

    @WebMethod(exclude = true)
    public boolean saveSession(Session event) {
        return userService.saveSession(event);
    }

    @WebMethod(exclude = true)
    public boolean matchCondition(String condition, User user, Session session) {
        return userService.matchCondition(condition, user, session);
    }

}
