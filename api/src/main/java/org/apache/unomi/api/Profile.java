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

package org.apache.unomi.api;

import org.apache.unomi.api.segments.Scoring;
import org.apache.unomi.api.segments.Segment;

import javax.xml.bind.annotation.XmlTransient;
import java.util.*;

/**
 * A user profile gathering all known information about a given user as well as segments it is part of and scores.
 * <p>
 * Contrary to other unomi {@link Item}s, profiles are not part of a scope since we want to be able to track the associated user across applications. For this reason, data
 * collected for a given profile in a specific scope is still available to any scoped item that accesses the profile information.
 * <p>
 * It is interesting to note that there is not necessarily a one to one mapping between users and profiles as users can be captured across applications and different observation
 * contexts. As identifying information might not be available in all contexts in which data is collected, resolving profiles to a single physical user can become complex because
 * physical users are not observed directly. Rather, their portrait is progressively patched together and made clearer as unomi captures more and more traces of their actions.
 * Unomi will merge related profiles as soon as collected data permits positive association between distinct profiles, usually as a result of the user performing some identifying
 * action in a context where the user hadn’t already been positively identified.
 *
 * @see Segment
 */
public class Profile extends Item {

    /**
     * The Profile ITEM_TYPE
     *
     * @see Item for a discussion of ITEM_TYPE
     */
    public static final String ITEM_TYPE = "profile";
    private static final long serialVersionUID = -7409439322939712238L;
    private Map<String, Object> properties = new HashMap<>();

    private Map<String, Object> systemProperties = new HashMap<>();

    private Set<String> segments = new HashSet<>();

    private Map<String, Integer> scores;

    private String mergedWith;

    private Map<String, Consent> consents = new LinkedHashMap<>();

    /**
     * Instantiates a new Profile.
     */
    public Profile() {
    }

    /**
     * Instantiates a new Profile with the specified identifier.
     *
     * @param profileId the profile identifier
     */
    public Profile(String profileId) {
        super(profileId);
    }

    /**
     * Sets the property identified by the specified name to the specified value. If a property with that name already exists, replaces its value, otherwise adds the new
     * property with the specified name and value.
     *
     * @param name  the name of the property to set
     * @param value the value of the property
     */
    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }

    /**
     * Retrieves the property identified by the specified name.
     *
     * @param name the name of the property to retrieve
     * @return the value of the specified property or {@code null} if no such property exists
     */
    public Object getProperty(String name) {
        return properties.get(name);
    }

    /**
     * Retrieves a Map of all property name - value pairs for this profile.
     *
     * @return a Map of all property name - value pairs for this profile
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * Sets the property name - value pairs for this profile.
     *
     * @param properties a Map containing the property name - value pairs for this profile
     */
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    /**
     * Retrieves a Map of system property name - value pairs for this profile. System properties can be used by implementations to store non-user visible properties needed for
     * internal purposes.
     *
     * @return a Map of system property name - value pairs for this profile
     */
    public Map<String, Object> getSystemProperties() {
        return systemProperties;
    }

    /**
     * Specifies the system property name - value pairs for this profile.
     *
     * @param systemProperties a Map of system property name - value pairs for this profile
     */
    public void setSystemProperties(Map<String, Object> systemProperties) {
        this.systemProperties = systemProperties;
    }

    /**
     * Sets a system property, overwriting an existing one if it existed. This call will also created the system
     * properties hash map if it didn't exist.
     * @param key the key for the system property hash map
     * @param value the value for the system property hash map
     * @return the previous value object if it existing.
     */
    public Object setSystemProperty(String key, Object value) {
        if (this.systemProperties == null) {
            this.systemProperties = new LinkedHashMap<>();
        }
        return this.systemProperties.put(key, value);
    }

    /**
     * {@inheritDoc}
     *
     * Note that Profiles are always in the shared system scope ({@link Metadata#SYSTEM_SCOPE}).
     */
    @XmlTransient
    public String getScope() {
        return Metadata.SYSTEM_SCOPE;
    }

    /**
     * Retrieves the identifiers of the segments this profile is a member of.
     *
     * @return the identifiers of the segments this profile is a member of
     */
    public Set<String> getSegments() {
        return segments;
    }

    /**
     * Sets the identifiers of the segments this profile is a member of.
     *
     * TODO: should be removed from the API
     *
     * @param segments the segments
     */
    public void setSegments(Set<String> segments) {
        this.segments = segments;
    }

    /**
     * Retrieves the identifier of the profile this profile is merged with if any.
     *
     * @return the identifier of the profile this profile is merged with if any, {@code null} otherwise
     */
    public String getMergedWith() {
        return mergedWith;
    }

    /**
     * TODO: should be removed from the API
     * @param mergedWith new value for mergedWith
     */
    public void setMergedWith(String mergedWith) {
        this.mergedWith = mergedWith;
    }

    /**
     * Retrieves the scores associated to this profile.
     *
     * @return the scores associated to this profile as a Map of {@link Scoring} identifier - score pairs
     */
    public Map<String, Integer> getScores() {
        return scores;
    }

    /**
     * TODO: should be removed from the API
     * @param scores new value for scores
     */
    public void setScores(Map<String, Integer> scores) {
        this.scores = scores;
    }

    /**
     * Returns all the consents, including the revokes ones.
     * @return a map that contains as a key the scope + "/" + consent type ID (or just the consent type ID if no scope was set on the consent), and the consent itself as a value
     */
    public Map<String, Consent> getConsents() {
        return consents;
    }

    /**
     * Returns true if this profile is an anonymous profile.
     * @return true of the profile has been marked as an anonymous profile, false otherwise.
     */
    @XmlTransient
    public boolean isAnonymousProfile() {
        Boolean anonymous = (Boolean) getSystemProperties().get("isAnonymousProfile");
        return anonymous != null && anonymous;
    }

    /**
     * Set a consent into the profile.
     * @param consent if the consent is REVOKED, it will try to remove a consent with the same type id if it
     *                exists for the profile.
     * @return true if the operation was successful (inserted exception in the case of a revoked consent, in which case
     * it is successful if there was a consent to revoke).
     */
    @XmlTransient
    public boolean setConsent(Consent consent) {
        if (ConsentStatus.REVOKED.equals(consent.getStatus())) {
            if (consents.containsKey(consent.getTypeIdentifier())) {
                consents.remove(consent.getTypeIdentifier());
                return true;
            }
            return false;
        }
        if (consent.getScope() != null) {
            consents.put(consent.getScope() + "/" + consent.getTypeIdentifier(), consent);
        } else {
            consents.put(consent.getTypeIdentifier(), consent);
        }
        return true;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Profile{");
        sb.append("properties=").append(properties);
        sb.append(", systemProperties=").append(systemProperties);
        sb.append(", segments=").append(segments);
        sb.append(", scores=").append(scores);
        sb.append(", mergedWith='").append(mergedWith).append('\'');
        sb.append(", consents=").append(consents);
        sb.append(", itemId='").append(itemId).append('\'');
        sb.append(", itemType='").append(itemType).append('\'');
        sb.append(", scope='").append(scope).append('\'');
        sb.append(", version=").append(version);
        sb.append('}');
        return sb.toString();
    }
}
