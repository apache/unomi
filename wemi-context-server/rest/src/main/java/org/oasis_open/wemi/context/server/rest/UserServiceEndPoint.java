package org.oasis_open.wemi.context.server.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.oasis_open.wemi.context.server.api.PropertyType;
import org.oasis_open.wemi.context.server.api.PropertyTypeGroup;
import org.oasis_open.wemi.context.server.api.Session;
import org.oasis_open.wemi.context.server.api.User;
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
