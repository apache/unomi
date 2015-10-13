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

import java.util.*;

/**
 * A service to access and operate on {@link Profile}s, {@link Session}s and {@link Persona}s.
 */
public interface ProfileService {

    /**
     * Retrieves the number of unique profiles.
     *
     * @return the number of unique profiles.
     */
    long getAllProfilesCount();

    /**
     * Retrieves profiles or personas matching the specified query.
     *
     * @param <T>   the specific sub-type of {@link Profile} to retrieve
     * @param query a {@link Query} specifying which elements to retrieve
     * @param clazz the class of elements to retrieve
     * @return a {@link PartialList} of {@code T} instances matching the specified query
     */
    <T extends Profile> PartialList<T> search(Query query, Class<T> clazz);

    /**
     * Creates a String containing comma-separated values (CSV) formatted version of profiles matching the specified query.
     *
     * @param query the query specifying which profiles to export
     * @return a CSV-formatted String version of the profiles matching the specified query
     */
    String exportProfilesPropertiesToCsv(Query query);

    /**
     * Find profiles which have the specified property with the specified value, ordered according to the specified {@code sortBy} String and paged: only
     * {@code size} of them are retrieved, starting with the {@code offset}-th one.
     *
     * TODO: replace with version using a query instead of separate parameters
     * TODO: remove as it's unused?
     *
     * @param propertyName  the name of the property we're interested in
     * @param propertyValue the value of the property we want profiles to have
     * @param offset        zero or a positive integer specifying the position of the first profile in the total ordered collection of matching profiles
     * @param size          a positive integer specifying how many matching profiles should be retrieved or {@code -1} if all of them should be retrieved
     * @param sortBy        a String of comma ({@code ,}) separated property names on which ordering should be performed, ordering elements according to  the property order in
     *                      the String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally
     *                      followed by a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @return a {@link PartialList} of matching profiles
     */
    PartialList<Profile> findProfilesByPropertyValue(String propertyName, String propertyValue, int offset, int size, String sortBy);

    /**
     * Merges the specified profiles into the provided so-called master profile, merging properties according to the {@link PropertyMergeStrategyType} specified on their {@link
     * PropertyType}.
     *
     * @param masterProfile   the profile into which the specified profiles will be merged
     * @param profilesToMerge the list of profiles to merge into the specified master profile
     * @return the merged profile
     */
    Profile mergeProfiles(Profile masterProfile, List<Profile> profilesToMerge);

    /**
     * Retrieves the profile identified by the specified identifier.
     *
     * @param profileId the identifier of the profile to retrieve
     * @return the profile identified by the specified identifier or {@code null} if no such profile exists
     */
    Profile load(String profileId);

    /**
     * Saves the specified profile in the context server.
     *
     * @param profile the profile to be saved
     * @return the newly saved profile
     */
    Profile save(Profile profile);

    /**
     * Removes the profile (or persona if the {@code persona} parameter is set to {@code true}) identified by the specified identifier.
     *
     * @param profileId the identifier of the profile or persona to delete
     * @param persona   {@code true} if the specified identifier is supposed to refer to a persona, {@code false} if it is supposed to refer to a profile
     */
    void delete(String profileId, boolean persona);

    /**
     * Retrieves the sessions associated with the profile identified by the specified identifier that match the specified query (if specified), ordered according to the specified
     * {@code sortBy} String and and paged: only {@code size} of them are retrieved, starting with the {@code offset}-th one.
     *
     * TODO: use a Query object instead of distinct parameter
     *
     * @param profileId the identifier of the profile we want to retrieve sessions from
     * @param query     the query filtering which sessions we are interested in or {@code null} (or an empty String) if we want to retrieve all sessions
     * @param offset    zero or a positive integer specifying the position of the first session in the total ordered collection of matching sessions
     * @param size      a positive integer specifying how many matching sessions should be retrieved or {@code -1} if all of them should be retrieved
     * @param sortBy    a String of comma ({@code ,}) separated property names on which ordering should be performed, ordering elements according to the property order in the
     *                  String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally followed by
     *                  a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @return a {@link PartialList} of matching sessions
     */
    PartialList<Session> getProfileSessions(String profileId, String query, int offset, int size, String sortBy);

    /**
     * Retrieves the session identified by the specified identifier.
     *
     * @param sessionId the identifier of the session to be retrieved
     * @param dateHint  a Date helping in identifying where the item is located
     * @return the session identified by the specified identifier
     */
    Session loadSession(String sessionId, Date dateHint);

    /**
     * Saves the specified session.
     *
     * @param session the session to be saved
     * @return the newly saved session
     */
    Session saveSession(Session session);

    /**
     * Retrieves sessions associated with the profile identified by the specified identifier.
     *
     * @param profileId the profile id for which we want to retrieve the sessions
     * @return a {@link PartialList} of the profile's sessions
     */
    PartialList<Session> findProfileSessions(String profileId);

    /**
     * Checks whether the specified profile and/or session satisfy the specified condition.
     *
     * @param condition the condition we're testing against which might or might not have profile- or session-specific sub-conditions
     * @param profile   the profile we're testing
     * @param session   the session we're testing
     * @return {@code true} if the profile and/or sessions match the specified condition, {@code false} otherwise
     */
    boolean matchCondition(Condition condition, Profile profile, Session session);

    /**
     * Update all profiles in batch according to the specified {@link BatchUpdate}
     *
     * @param update the batch update specification
     */
    void batchProfilesUpdate(BatchUpdate update);

    /**
     * Retrieves the persona identified by the specified identifier.
     *
     * @param personaId the identifier of the persona to retrieve
     * @return the persona associated with the specified identifier or {@code null} if no such persona exists.
     */
    Persona loadPersona(String personaId);

    /**
     * Persists the specified {@link Persona} in the context server.
     *
     * @param persona the persona to persist
     * @return the newly persisted persona
     */
    Persona savePersona(Persona persona);

    /**
     * Retrieves the persona identified by the specified identifier and all its associated sessions
     *
     * @param personaId the identifier of the persona to retrieve
     * @return a {@link PersonaWithSessions} instance with the persona identified by the specified identifier
     */
    PersonaWithSessions loadPersonaWithSessions(String personaId);

    /**
     * Creates a persona with the specified identifier and automatically creates an associated session with it.
     *
     * @param personaId the identifier to use for the new persona
     * @return the newly created persona
     */
    Persona createPersona(String personaId);

    /**
     * Retrieves the sessions associated with the persona identified by the specified identifier, ordered according to the specified {@code sortBy} String and and paged: only
     * {@code size} of them are retrieved, starting with the {@code offset}-th one.
     *
     * @param personaId the persona id
     * @param offset    zero or a positive integer specifying the position of the first session in the total ordered collection of matching sessions
     * @param size      a positive integer specifying how many matching sessions should be retrieved or {@code -1} if all of them should be retrieved
     * @param sortBy    a String of comma ({@code ,}) separated property names on which ordering should be performed, ordering elements according to the property order in the
     *                  String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally followed by
     *                  a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @return a {@link PartialList} of sessions for the persona identified by the specified identifier
     */
    PartialList<Session> getPersonaSessions(String personaId, int offset, int size, String sortBy);

    /**
     * Retrieves all the property types associated with the specified target.
     *
     * TODO: move to a different class
     *
     * @param target the target for which we want to retrieve the associated property types
     * @return a collection of all the property types associated with the specified target
     */
    Collection<PropertyType> getAllPropertyTypes(String target);

    /**
     * Retrieves all known property types.
     *
     * TODO: move to a different class
     *
     * @return a Map associating targets as keys to related {@link PropertyType}s
     */
    HashMap<String, Collection<PropertyType>> getAllPropertyTypes();

    /**
     * Retrieves all property types with the specified tag also retrieving property types with sub-tags of the specified tag if so specified.
     *
     * TODO: move to a different class
     *
     * @param tag                the tag name marking property types we want to retrieve
     * @param includeFromSubtags {@code true} if sub-tags of the specified tag should also be considered, {@code false} otherwise
     * @return a Set of the property types with the specified tag
     */
    Set<PropertyType> getPropertyTypeByTag(String tag, boolean includeFromSubtags);

    /**
     * TODO
     */
    String getPropertyTypeMapping(String fromPropertyTypeId);

    /**
     * TODO
     */
    Collection<PropertyType> getPropertyTypeByMapping(String propertyName);

    /**
     * Retrieves the property type identified by the specified identifier.
     *
     * TODO: move to a different class
     *
     * @param id the identifier of the property type to retrieve
     * @return the property type identified by the specified identifier or {@code null} if no such property type exists
     */
    PropertyType getPropertyType(String id);

    /**
     * Persists the specified property type in the context server.
     *
     * TODO: move to a different class
     *
     * @param property the property type to persist
     * @return {@code true} if the property type was properly created, {@code false} otherwise (for example, if the property type already existed
     */
    boolean createPropertyType(PropertyType property);

    /**
     * Deletes the property type identified by the specified identifier.
     *
     * TODO: move to a different class
     *
     * @param propertyId the identifier of the property type to delete
     * @return {@code true} if the property type was properly deleted, {@code false} otherwise
     */
    boolean deletePropertyType(String propertyId);

    /**
     * Retrieves the existing property types for the specified type as defined by the Item subclass public field {@code ITEM_TYPE} and with the specified tag.
     *
     * TODO: move to a different class
     *
     * @param tagId    the tag we're interested in
     * @param itemType the String representation of the item type we want to retrieve the count of, as defined by its class' {@code ITEM_TYPE} field
     * @return all property types defined for the specified item type and with the specified tag
     */
    Set<PropertyType> getExistingProperties(String tagId, String itemType);


}
