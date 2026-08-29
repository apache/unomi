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
 * Entry point for consent, anonymization, and privacy-related operations.
 * Wraps profile updates required for GDPR-style requests and consent tracking.
 */
public interface PrivacyService {

    /**
     * Returns base server information (name, version, build time, event types, capabilities).
     * For extension details, use {@link #getServerInfos()}.
     *
     * @return default server information
     */
    ServerInfo getServerInfo();

    /**
     * Returns server information for the core instance and all extensions.
     *
     * @return server information entries, including extensions
     */
    List<ServerInfo> getServerInfos();

    /**
     * Replaces a profile with a new empty profile that keeps the same id.
     * Sessions and events are not removed.
     *
     * @param profileId profile identifier
     * @return {@code true} if deletion succeeded
     */
    Boolean deleteProfile(String profileId);

    /**
     * Removes denied (personally identifying) properties from a profile.
     *
     * @param profileId profile identifier
     * @param scope scope used when emitting anonymize and profile-updated events
     * @return {@code true} if any properties were removed
     */
    Boolean anonymizeProfile(String profileId, String scope);

    /**
     * Moves a profile's sessions and events to a new anonymous profile.
     * Does not anonymize session or event properties that may contain PII.
     *
     * @param profileId profile identifier
     * @return {@code true} if the operation succeeded
     */
    Boolean anonymizeBrowsingData(String profileId);

    /**
     * Anonymizes browsing data, then optionally deletes the original profile.
     *
     * @param profileId profile identifier
     * @param purgeData when {@code true}, deletes the profile after anonymization
     * @return {@code true} if the operation succeeded
     */
    Boolean deleteProfileData(String profileId,boolean purgeData);

    /**
     * Enables or disables anonymous browsing for a profile.
     * Resets goals and past-events system properties when the flag changes.
     *
     * @param profileId profile identifier
     * @param anonymous anonymous-browsing flag value
     * @param scope scope used when emitting a profile-updated event
     * @return {@code true} if the update succeeded
     */
    Boolean setRequireAnonymousBrowsing(String profileId, boolean anonymous, String scope);

    /**
     * Checks whether anonymous browsing is required for the given profile id.
     *
     * @param profileId profile identifier
     * @return {@code true} if anonymous browsing is required
     */
    Boolean isRequireAnonymousBrowsing(String profileId);

    /**
     * Checks whether anonymous browsing is required for the given profile.
     *
     * @param profile profile to inspect
     * @return {@code true} if anonymous browsing is required
     */
    Boolean isRequireAnonymousBrowsing(Profile profile);

    /**
     * Builds an anonymous profile copy without persisting it.
     * Copies non-denied properties from the source profile.
     *
     * @param profile source profile
     * @return new anonymous profile (not persisted)
     */
    Profile getAnonymousProfile(Profile profile);

    /**
     * Returns event types the visitor has opted out of collecting.
     *
     * @param profileId profile identifier
     * @return blocked event type names
     */
    List<String> getFilteredEventTypes(String profileId);

    /**
     * Returns event types the visitor has opted out of collecting.
     *
     * @param profile profile to inspect
     * @return blocked event type names
     */
    List<String> getFilteredEventTypes(Profile profile);

    /**
     * Sets event types the visitor has opted out of collecting.
     *
     * @param profileId profile identifier
     * @param eventTypes blocked event type names
     * @return {@code true} if the update succeeded
     */
    Boolean setFilteredEventTypes(String profileId, List<String> eventTypes);

    /**
     * Returns property names tagged as personally identifying for the profile.
     *
     * @param profileId profile identifier
     * @return denied property identifiers
     */
    List<String> getDeniedProperties(String profileId);

    /**
     * Sets the list of denied properties.
     *
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
     *
     * @param profileId the identifier of the profile
     * @return do not use
     * @deprecated As of version 1.3.0-incubating, do not use this method
     */
    @Deprecated
    List<String> getDeniedPropertyDistribution(String profileId);

    /**
     * This method doesn't do anything anymore please don't use it
     *
     * @param profileId the identifier of the profile
     * @param propertyNames do not use
     * @return do not use
     * @deprecated As of version 1.3.0-incubating, do not use this method
     */
    @Deprecated
    Boolean setDeniedPropertyDistribution(String profileId, List<String> propertyNames);

    /**
     * Removes a property from a profile and persists the change.
     *
     * @param profileId profile identifier
     * @param propertyName property to remove
     * @return {@code true} if the removal succeeded
     */
    Boolean removeProperty(String profileId, String propertyName);

}
