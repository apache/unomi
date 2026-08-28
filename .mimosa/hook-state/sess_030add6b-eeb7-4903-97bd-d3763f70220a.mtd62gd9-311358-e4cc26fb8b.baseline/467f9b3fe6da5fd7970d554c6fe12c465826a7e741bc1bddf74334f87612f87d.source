/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.unomi.api.services;

import org.apache.unomi.api.*;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;

import java.net.URL;
import java.util.*;

/**
 * Primary API for {@link Profile}s, {@link Session}s, and {@link Persona}s.
 * Loads and saves visitor data, merges profiles, manages sessions, and
 * supports persona-based testing workflows.
 */
public interface ProfileService {

    /**
     * The system tag name used to identify property types that store
     * personal identifiers.
     */
    String PERSONAL_IDENTIFIER_TAG_NAME = "personalIdentifierProperties";

    /**
     * Returns the total number of profiles.
     *
     * @return profile count
     */
    long getAllProfilesCount();

    /**
     * Searches profiles or personas using a structured query.
     *
     * @param <T> profile subtype to return
     * @param query search query
     * @param clazz profile class to load
     * @return matching profiles or personas
     */
    <T extends Profile> PartialList<T> search(Query query, Class<T> clazz);

    /**
     * Searches sessions using a structured query.
     *
     * @param query search query
     * @return matching sessions
     */
    PartialList<Session> searchSessions(Query query);

    /**
     * Exports matching profile properties as a CSV string.
     *
     * @param query search query selecting profiles to export
     * @return CSV representation of matching profile properties
     */
    String exportProfilesPropertiesToCsv(Query query);

    /**
     * Finds profiles with a given property value, ordered and paged.
     *
     * Prefer {@link #search(Query, Class)} for new code; this overload remains for backward compatibility.
     *
     * @param propertyName property name to match
     * @param propertyValue required property value
     * @param offset zero-based index of the first result
     * @param size maximum results to return, or {@code -1} for all
     * @param sortBy optional comma-separated property list with optional {@code :asc}/{@code :desc} suffixes
     * @return matching profiles
     */
    PartialList<Profile> findProfilesByPropertyValue(String propertyName, String propertyValue, int offset, int size, String sortBy);

    /**
     * Merges profiles into a master profile using each property's merge strategy.
     *
     * @param masterProfile profile that receives merged data
     * @param profilesToMerge profiles to merge into the master
     * @return merged master profile
     */
    Profile mergeProfiles(Profile masterProfile, List<Profile> profilesToMerge);

    /**
     * Loads a profile by id.
     *
     * @param profileId profile identifier
     * @return matching profile, or {@code null} if none exists
     */
    Profile load(String profileId);

    /**
     * Persists a profile.
     *
     * @param profile profile to save
     * @return saved profile
     */
    Profile save(Profile profile);

    /**
     * Links an alias to a profile for the given client.
     *
     * @param profileID profile identifier
     * @param alias alias to link
     * @param clientID client identifier
     */
    void addAliasToProfile(String profileID, String alias, String clientID);

    /**
     * Unlinks an alias from a profile for the given client.
     *
     * @param profileID profile identifier
     * @param alias alias to unlink
     * @param clientID client identifier
     * @return removed alias, or {@code null} if not found
     */
    ProfileAlias removeAliasFromProfile(String profileID, String alias, String clientID);

    /**
     * Lists aliases linked to a profile, ordered and paged.
     *
     * @param profileId profile identifier
     * @param offset zero-based index of the first result
     * @param size maximum results to return, or {@code -1} for all
     * @param sortBy optional comma-separated property list with optional {@code :asc}/{@code :desc} suffixes
     * @return matching profile aliases
     */
    PartialList<ProfileAlias> findProfileAliases(String profileId, int offset, int size, String sortBy);

    /**
     * Saves a new profile or merges properties into an existing one.
     *
     * @param profile profile to save or merge
     * @return saved or merged profile, or {@code null} on failure
     */
    Profile saveOrMerge(Profile profile);

    /**
     * Deletes a profile or persona by id.
     *
     * @param profileId profile or persona identifier
     * @param persona {@code true} when deleting a persona, {@code false} for a profile
     */
    void delete(String profileId, boolean persona);

    /**
     * Lists a profile's sessions with optional full-text filtering, ordered and paged.
     *
     * @param profileId profile identifier
     * @param query optional full-text filter, or {@code null} for all sessions
     * @param offset zero-based index of the first result
     * @param size maximum results to return, or {@code -1} for all
     * @param sortBy optional comma-separated property list with optional {@code :asc}/{@code :desc} suffixes
     * @return matching sessions
     */
    PartialList<Session> getProfileSessions(String profileId, String query, int offset, int size, String sortBy);

    /**
     * Loads a session by id.
     *
     * @deprecated {@code dateHint} is not supported anymore; use {@link #loadSession(String)}
     * @param sessionId session identifier
     * @param dateHint unused date hint
     * @return matching session
     */
    @Deprecated
    Session loadSession(String sessionId, Date dateHint);

    /**
     * Loads a session by id.
     *
     * @param sessionId session identifier
     * @return matching session
     */
    default Session loadSession(String sessionId) {
        return loadSession(sessionId, null);
    };

    /**
     * Persists a session.
     *
     * @param session session to save
     * @return saved session
     */
    Session saveSession(Session session);

    /**
     * Returns all sessions linked to a profile.
     *
     * @param profileId profile identifier
     * @return profile sessions
     */
    PartialList<Session> findProfileSessions(String profileId);

    /**
     * Deletes all sessions belonging to a profile.
     *
     * @param profileId profile identifier
     */
    void removeProfileSessions(String profileId);

    /**
     * Deletes a session by id.
     * Events for the session remain in persistence with a dangling session id.
     *
     * @param sessionIdentifier session identifier
     */
    void deleteSession(String sessionIdentifier);

    /**
     * Evaluates whether a profile and/or session satisfy a condition.
     *
     * @param condition condition to test (may include profile- or session-specific parts)
     * @param profile profile to evaluate
     * @param session session to evaluate
     * @return {@code true} when the condition matches
     */
    boolean matchCondition(Condition condition, Profile profile, Session session);

    /**
     * Applies a batch update to matching profiles.
     *
     * @param update batch update specification
     */
    void batchProfilesUpdate(BatchUpdate update);

    /**
     * Loads a persona by id.
     *
     * @param personaId persona identifier
     * @return matching persona, or {@code null} if none exists
     */
    Persona loadPersona(String personaId);

    /**
     * Persists a persona.
     *
     * @param persona persona to save
     * @return saved persona
     */
    Persona savePersona(Persona persona);

    /**
     * Loads a persona and all of its sessions.
     *
     * @param personaId persona identifier
     * @return persona with associated sessions
     */
    PersonaWithSessions loadPersonaWithSessions(String personaId);

    /**
     * Creates a persona and an initial session for it.
     *
     * @param personaId identifier for the new persona
     * @return newly created persona
     */
    Persona createPersona(String personaId);

    /**
     * Lists persona sessions, ordered and paged.
     *
     * @param personaId persona identifier
     * @param offset zero-based index of the first result
     * @param size maximum results to return, or {@code -1} for all
     * @param sortBy optional comma-separated property list with optional {@code :asc}/{@code :desc} suffixes
     * @return matching persona sessions
     */
    PartialList<PersonaSession> getPersonaSessions(String personaId, int offset, int size, String sortBy);

    /**
     * Persists a persona together with its sessions.
     *
     * @param personaToSave persona and session data to save
     * @return saved persona with sessions
     */
    PersonaWithSessions savePersonaWithSessions(PersonaWithSessions personaToSave);

    /**
     * Returns property types registered for the given target.
     *
     * @param target target name (for example profiles or events)
     * @return property types for the target
     */
    Collection<PropertyType> getTargetPropertyTypes(String target);

    /**
     * Returns all property types grouped by target.
     *
     * @return map of target name to property types
     */
    Map<String, Collection<PropertyType>> getTargetPropertyTypes();

    /**
     * Returns property types tagged with the given tag.
     *
     * @param tag tag name
     * @return matching property types
     */
    Set<PropertyType> getPropertyTypeByTag(String tag);

    /**
     * Returns property types with the given system tag.
     *
     * @param tag system tag name
     * @return matching property types
     */
    Set<PropertyType> getPropertyTypeBySystemTag(String tag);

    /**
     * Returns the persistence mapping for a property type.
     *
     * @param fromPropertyTypeId source property type id
     * @return mapped property type id
     */
    String getPropertyTypeMapping(String fromPropertyTypeId);

    /**
     * Returns property types mapped to the given property name.
     *
     * @param propertyName property name to look up
     * @return matching property types
     */
    Collection<PropertyType> getPropertyTypeByMapping(String propertyName);

    /**
     * Looks up a property type by id.
     *
     * @param id property type identifier
     * @return matching property type, or {@code null} if none exists
     */
    PropertyType getPropertyType(String id);

    /**
     * Registers or updates a property type definition.
     *
     * @param property property type to persist
     * @return {@code true} when created, {@code false} when it already existed
     */
    boolean setPropertyType(PropertyType property);

    /**
     * Infers and sets the property type target from a definition URL when missing.
     * By default uses the fifth path segment after {@code /}.
     *
     * @param predefinedPropertyTypeURL URL of the property type definition
     * @param propertyType property type to update
     */
    void setPropertyTypeTarget(URL predefinedPropertyTypeURL, PropertyType propertyType);

    /**
     * Deletes a property type by id.
     *
     * @param propertyId property type identifier
     * @return {@code true} when deleted successfully
     */
    boolean deletePropertyType(String propertyId);

    /**
     * Returns property types defined for an item type that carry the given tag.
     *
     * @param tag tag to match
     * @param itemType item type name from the item class {@code ITEM_TYPE} field
     * @return matching property types
     */
    Set<PropertyType> getExistingProperties(String tag, String itemType);

    /**
     * Returns property types defined for an item type that carry the given tag or system tag.
     *
     * @param tag tag to match
     * @param itemType item type name from the item class {@code ITEM_TYPE} field
     * @param systemTag {@code true} when {@code tag} is a system tag
     * @return matching property types
     */
    Set<PropertyType> getExistingProperties(String tag, String itemType, boolean systemTag);

    /**
     * Reloads profile service state from persistence immediately.
     * Expensive; intended for integration tests and similar scenarios.
     */
    void refresh();

    /**
     * Deletes profiles by inactivity and/or age criteria.
     * Example: purge profiles inactive for 10 days only: {@code purgeProfiles(10, 0)}.
     * Example: purge profiles created within the last 30 days: {@code purgeProfiles(0, 30)}.
     *
     * @param inactiveNumberOfDays purge profiles with no visits since this many days (ignored when {@code <= 0})
     * @param existsNumberOfDays purge profiles created within this many days (ignored when {@code <= 0})
     */
    void purgeProfiles(int inactiveNumberOfDays, int existsNumberOfDays);

    /**
     * Deletes session items older than the given age threshold.
     *
     * @param existsNumberOfDays purge sessions created within this many days (ignored when {@code <= 0})
     */
    void purgeSessionItems(int existsNumberOfDays);

    /**
     * Deletes event items older than the given age threshold.
     *
     * @param existsNumberOfDays purge events created within this many days (ignored when {@code <= 0})
     */
    void purgeEventItems(int existsNumberOfDays);

    /**
     * @deprecated Use {@link #purgeSessionItems(int)} and {@link #purgeEventItems(int)} for rollover cleanup.
     *
     * @param existsNumberOfMonths remove monthly indices older than this many months
     */
    @Deprecated
    void purgeMonthlyItems(int existsNumberOfMonths);
}
