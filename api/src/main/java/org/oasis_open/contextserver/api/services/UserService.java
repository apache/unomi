package org.oasis_open.contextserver.api.services;

import org.oasis_open.contextserver.api.*;
import org.oasis_open.contextserver.api.*;
import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.Date;
import java.util.Set;

/**
 * Created by loom on 24.04.14.
 */
public interface UserService {

    PartialList<User> getAllUsers();

    long getAllUsersCount();

    PartialList<User> getUsers(String query, int offset, int size, String sortBy);

    PartialList<User> findUsersByPropertyValue(String propertyName, String propertyValue);

    User mergeUsersOnProperty(User currentUser, Session currentSession, String propertyName, String propertyValue);

    User load(String userId);

    void save(User user);

    void delete(String userId, boolean persona);

    PartialList<Session> getUserSessions(String userId, int offset, int size, String sortBy);

    Set<PropertyType> getAllPropertyTypes();

    Set<PropertyType> getPropertyTypes(String tagId, boolean recursive);

    String getPropertyTypeMapping(String fromPropertyTypeId);

    Session loadSession(String sessionId, Date dateHint);

    boolean saveSession(Session session);

    PartialList<Session> findUserSessions(String userId);

    boolean matchCondition(Condition condition, User user, Session session);

    Persona loadPersona(String personaId);

    PersonaWithSessions loadPersonaWithSessions(String personaId);

    PartialList<Persona> getPersonas(int offset, int size, String sortBy);

    void createPersona(String personaId);

    PartialList<Session> getPersonaSessions(String personaId, int offset, int size, String sortBy);

}
