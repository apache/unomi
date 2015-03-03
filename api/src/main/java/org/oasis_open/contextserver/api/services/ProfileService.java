package org.oasis_open.contextserver.api.services;

import org.oasis_open.contextserver.api.*;
import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.Date;
import java.util.Set;

public interface ProfileService {

    PartialList<Profile> getAllProfiles();

    long getAllProfilesCount();

    PartialList<Profile> getProfiles(String query, int offset, int size, String sortBy);

    PartialList<Profile> findProfilesByPropertyValue(String propertyName, String propertyValue, int offset, int size, String sortBy);

    boolean mergeProfilesOnProperty(Profile currentProfile, Session currentSession, String propertyName, String propertyValue);

    Profile load(String profileId);

    void save(Profile profile);

    void delete(String profileId, boolean persona);

    PartialList<Session> getProfileSessions(String profileId, int offset, int size, String sortBy);

    String getPropertyTypeMapping(String fromPropertyTypeId);

    Session loadSession(String sessionId, Date dateHint);

    boolean saveSession(Session session);

    PartialList<Session> findProfileSessions(String profileId);

    boolean matchCondition(Condition condition, Profile profile, Session session);

    Persona loadPersona(String personaId);

    PersonaWithSessions loadPersonaWithSessions(String personaId);

    PartialList<Persona> getPersonas(int offset, int size, String sortBy);

    void createPersona(String personaId);

    PartialList<Session> getPersonaSessions(String personaId, int offset, int size, String sortBy);

}
