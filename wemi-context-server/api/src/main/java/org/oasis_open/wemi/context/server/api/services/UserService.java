package org.oasis_open.wemi.context.server.api.services;

import org.oasis_open.wemi.context.server.api.PropertyType;
import org.oasis_open.wemi.context.server.api.PropertyTypeGroup;
import org.oasis_open.wemi.context.server.api.Session;
import org.oasis_open.wemi.context.server.api.User;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Created by loom on 24.04.14.
 */
public interface UserService {

    Collection<User> getAllUsers();

    Collection<User> getUsers(String query, int offset, int size);

    List<User> findUsersByPropertyValue(String propertyName, String propertyValue);

    User load(String userId);

    void save(User user);

    public Set<PropertyTypeGroup> getPropertyTypeGroups();

    public Set<PropertyType> getAllPropertyTypes();

    public Set<PropertyType> getPropertyTypes(String propertyGroupId);

    public String getPropertyTypeMapping(String fromPropertyTypeId);

    Session loadSession(String eventId);

    boolean saveSession(Session event);

    boolean matchCondition(String condition, User user, Session session);
}
