package org.oasis_open.wemi.context.server.api.services;

import org.oasis_open.wemi.context.server.api.*;

import java.util.Set;

/**
 * Created by loom on 24.04.14.
 */
public interface UserService {

    PartialList<User> getAllUsers();

    public long getAllUsersCount();

    PartialList<User> getUsers(String query, int offset, int size, String sortBy);

    PartialList<User> findUsersByPropertyValue(String propertyName, String propertyValue);

    User load(String userId);

    void save(User user);

    void delete(User user);

    public Set<PropertyTypeGroup> getPropertyTypeGroups();

    public Set<PropertyType> getAllPropertyTypes();

    public Set<PropertyType> getPropertyTypes(String propertyGroupId);

    public String getPropertyTypeMapping(String fromPropertyTypeId);

    Session loadSession(String eventId);

    boolean saveSession(Session event);

    boolean matchCondition(String condition, User user, Session session);

    public Persona loadPersona(String personaId);

    PartialList<Persona> getPersonas(int offset, int size, String sortBy);

    public void createPersona(String personaId);

}
