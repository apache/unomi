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

import org.apache.unomi.api.Profile;
import org.apache.unomi.api.ServerInfo;

import java.util.List;

/**
 * This service regroups all privacy-related operations
 */
public interface PrivacyService {

    /**
     * Retrieves the default base Apache Unomi server information, including the name and version of the server, build
     * time information and the event types
     * if recognizes as well as the capabilities supported by the system. For more detailed information about the system
     * and extensions use the getServerInfos method.
     * @return a ServerInfo object with all the server information
     */
    ServerInfo getServerInfo();

    /**
     * Retrieves the list of the server information objects, that include extensions. Each object includes the
     * name and version of the server, build time information and the event types
     * if recognizes as well as the capabilities supported by the system.
     * @return a list of ServerInfo objects with all the server information
     */
    List<ServerInfo> getServerInfos();

    /**
     * Deletes the current profile (but has no effect on sessions and events). This will delete the
     * persisted profile and replace it with a new empty one with the same profileId.
     * @param profileId the identifier of the profile to delete and replace
     * @return true if the deletion was successful
     */
    Boolean deleteProfile(String profileId);

    /**
     * This method will "anonymize" a profile by removing from the associated profile all the properties
     * that have been defined as "denied properties".
     * @param profileId the identifier of the profile that needs to be anonymized.
     * @param scope The scope will be used to send events, once for the anonymizeProfile event, the other for the profileUpdated event
     * @return true if the profile had some properties purged, false otherwise
     */
    Boolean anonymizeProfile(String profileId, String scope);

    /**
     * This method will anonymize browsing data by creating an anonymous profile for the current profile,
     * and then re-associating all the profile's sessions and events with the new anonymous profile
     * todo this method does not anonymize any session or event properties that may contain profile
     * data (such as the login event)
     * @param profileId the identifier of the profile on which to perform the anonymizations of the browsing
     *                  data
     * @return true if the operation was successful, false otherwise
     */
    Boolean anonymizeBrowsingData(String profileId);

    /**
     * This method will perform two operations, first it will call the anonymizeBrowsingData method on the
     * specified profile, and then it will delete the profile from the persistence service.
     * @param profileId the identifier of the profile
     * @param purgeData flag that indicates whether to purge the profile's data
     * @return true if the operation was successful, false otherwise
     */
    Boolean deleteProfileData(String profileId,boolean purgeData);

    /**
     * Controls the activation/deactivation of anonymous browsing. This method will simply set a system
     * property called requireAnonymousProfile that will be then use to know if we should associate
     * browsing data with the main profile or the associated anonymous profile.
     * Note that changing this setting will also reset the goals and pastEvents system properties for the
     * profile.
     * @param profileId the identifier of the profile on which to set the anonymous browsing property flag
     * @param anonymous the value of the anonymous browsing flag.
     * @param scope a scope used to send a profileUpdated event internally
     * @return true if successful, false otherwise
     */
    Boolean setRequireAnonymousBrowsing(String profileId, boolean anonymous, String scope);

    /**
     * Tests if the anonymous browsing flag is set of the specified profile.
     * @param profileId the identifier of the profile on which we want to retrieve the anonymous browsing flag
     * @return true if successful, false otherwise
     */
    Boolean isRequireAnonymousBrowsing(String profileId);

    /**
     * Tests if the anonymous browsing flag is set of the specified profile.
     * @param profile the profile on which we want to retrieve the anonymous browsing flag
     * @return true if successful, false otherwise
     */
    Boolean isRequireAnonymousBrowsing(Profile profile);

    /**
     * Build a new anonymous profile (but doesn't persist it in the persistence service). This will also
     * copy the profile properties from the passed profile that are not listed as denied properties.
     * @param profile the profile for which to create the anonymous profile
     * @return a newly created (but not persisted) profile for the passed profile.
     */
    Profile getAnonymousProfile(Profile profile);

    /**
     * Retrieve the list of events that the profile has deactivated. For each profile a visitor may indicate
     * that he doesn't want some events to be collected. This method retrieves this list from the specified
     * profile
     * @param profileId the identifier for the profile for which we want to retrieve the list of forbidden
     *                  event types
     * @return a list of event types
     */
    List<String> getFilteredEventTypes(String profileId);

    /**
     * Retrieve the list of events that the profile has deactivated. For each profile a visitor may indicate
     * that he doesn't want some events to be collected. This method retrieves this list from the specified
     * profile
     * @param profile the profile for which we want to retrieve the list of forbidden
     *                  event types
     * @return a list of event types
     */
    List<String> getFilteredEventTypes(Profile profile);

    /**
     * Set the list of filtered event types for a profile. This is the list of event types that the visitor
     * has specified he does not want the server to collect.
     * @param profileId the identifier of the profile on which to filter the events
     * @param eventTypes a list of event types that will be filter for the profile
     * @return true if successfull, false otherwise.
     */
    Boolean setFilteredEventTypes(String profileId, List<String> eventTypes);

    /**
     * Gets the list of denied properties. These are properties marked with a personal identifier tag.
     * @param profileId the identified of the profile
     * @return a list of profile properties identifiers that are marked as personally identifying
     */
    List<String> getDeniedProperties(String profileId);

    /**
     * Sets the list of denied properties.
     * @param profileId the profile for which to see the denied properties
     * @param propertyNames the property names to be denied
     * @return null all the time, this method is not used and is marked as deprecated
     * @deprecated As of version 1.3.0-incubating, do not use this method, instead mark properties with the personal identifier tag which
     * will mark them as denied by the getDeniedProperties method
     */
    @Deprecated
    Boolean setDeniedProperties(String profileId, List<String> propertyNames);

    /**
     * This method doesn't do anything anymore please don't use it
     * @param profileId the identifier of the profile
     * @return do not use
     * @deprecated As of version 1.3.0-incubating, do not use this method
     */
    @Deprecated
    List<String> getDeniedPropertyDistribution(String profileId);

    /**
     * This method doesn't do anything anymore please don't use it
     * @param profileId the identifier of the profile
     * @param propertyNames do not use
     * @return do not use
     * @deprecated As of version 1.3.0-incubating, do not use this method
     */
    @Deprecated
    Boolean setDeniedPropertyDistribution(String profileId, List<String> propertyNames);

    /**
     * Removes a property from the specified profile. This change is persisted.
     * @param profileId the identifier of the profile
     * @param propertyName the name of the property to remove
     * @return true if sucessfull, false otherwise
     */
    Boolean removeProperty(String profileId, String propertyName);

}
