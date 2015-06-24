package org.oasis_open.contextserver.api.services;

/*
 * #%L
 * context-server-api
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

import org.oasis_open.contextserver.api.*;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.query.Query;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Set;

public interface ProfileService {

    long getAllProfilesCount();

    <T extends Profile>  PartialList<T> search(Query query, Class<T> clazz);

    String exportProfilesPropertiesToCsv(Query query);

    PartialList<Profile> findProfilesByPropertyValue(String propertyName, String propertyValue, int offset, int size, String sortBy);

    boolean mergeProfilesOnProperty(Profile currentProfile, Session currentSession, String propertyName, String propertyValue);

    Profile load(String profileId);

    void save(Profile profile);

    void delete(String profileId, boolean persona);

    PartialList<Session> getProfileSessions(String profileId, String query, int offset, int size, String sortBy);

    String getPropertyTypeMapping(String fromPropertyTypeId);

    Session loadSession(String sessionId, Date dateHint);

    Session saveSession(Session session);

    PartialList<Session> findProfileSessions(String profileId);

    boolean matchCondition(Condition condition, Profile profile, Session session);

    void batchProfilesUpdate(BatchUpdate update);

    Persona loadPersona(String personaId);

    PersonaWithSessions loadPersonaWithSessions(String personaId);

    Persona createPersona(String personaId);

    PartialList<Session> getPersonaSessions(String personaId, int offset, int size, String sortBy);

    Collection<PropertyType> getAllPropertyTypes(String target);

    HashMap<String, Collection<PropertyType>> getAllPropertyTypes();

    Set<PropertyType> getPropertyTypeByTag(String tag, boolean recursive);

    Collection<PropertyType> getPropertyTypeByMapping(String propertyName);

    PropertyType getPropertyType(String target, String id);

    boolean createPropertyType(PropertyType property);

    boolean deletePropertyType(String propertyId);

    Set<PropertyType> getExistingProperties(String tagId, String itemType);


}
