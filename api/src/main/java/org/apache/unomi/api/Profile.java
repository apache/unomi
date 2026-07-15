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

import org.apache.commons.lang3.StringUtils;
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
public class Profile extends Item implements SystemPropertiesItem {

    /**
     * The Profile ITEM_TYPE
     *
     * @see Item for a discussion of ITEM_TYPE
     */
    public static final String ITEM_TYPE = "profile";
    private static final long serialVersionUID = -7409439322939712238L;
    /**
     * All user-visible profile properties.
     * @api.example {"firstName":"Ada","email":"ada@example.com"}
     */
    private Map<String, Object> properties = new HashMap<>();

    /**
     * Internal properties used by implementations and not usually returned to clients.
     */
    private Map<String, Object> systemProperties = new HashMap<>();

    /**
     * Segment identifiers matched by this profile.
     * @api.example ["vip","returning"]
     */
    private Set<String> segments = new HashSet<>();

    /**
     * Scoring plan id → numeric score for this profile.
     * @api.example {"engagement":12}
     */
    private Map<String, Integer> scores;

    /**
     * Legacy merge target profile id; unused since 2.0.0 (profile aliases replaced merges).
     *
     * @deprecated since 2.0.0 merge mechanism is now based on profile aliases, and this property is not used anymore
     * @api.example profile-merged-target
     */
    @Deprecated
    private String mergedWith;

    /**
     * Consent map for this profile, keyed by consent identifier.
     * @api.example {"newsletter":{"scope":"mysite","typeIdentifier":"newsletter","status":"GRANTED"}}
     */
    private Map<String, Consent> consents = new LinkedHashMap<>();

    /**
     * Default constructor.
     */
    public Profile() {
    }

    /**
     * Creates a profile with the given identifier.
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
     * Value of the named profile property.
     *
     * @param name the property name
     * @return the property value, or {@code null} if unset
     */
    public Object getProperty(String name) {
        return properties.get(name);
    }

    /**
     * Value of a nested property, using dot-separated path segments.
     *
     * @param name the property path (for example {@code address.city})
     * @return the nested value, or {@code null} if any segment is missing
     */
    public Object getNestedProperty(String name) {
        if (!name.contains(".")) {
            return getProperty(name);
        }

        Map properties = this.properties;
        String[] propertyPath = StringUtils.substringBeforeLast(name, ".").split("\\.");
        String propertyName = StringUtils.substringAfterLast(name, ".");

        for (String property: propertyPath) {
            properties = (Map) properties.get(property);
            if (properties == null) {
                return null;
            }
        }
        return properties.get(propertyName);
    }

    /**
     * All user-visible profile properties.
     *
     * @return the property map
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
     * Internal properties used by implementations and not shown in UIs.
     *
     * @return the system property map
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
     * Sets or replaces a system property, creating the map if needed.
     *
     * @param key   the system property name
     * @param value the system property value
     * @return the previous value, or {@code null} if the key was not set
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
     * Segment ids this profile currently belongs to.
     *
     * @return the segment ids
     */
    public Set<String> getSegments() {
        return segments;
    }

    /**
     * Sets the identifiers of the segments this profile is a member of.
     *
     * @param segments the segments
     */
    public void setSegments(Set<String> segments) {
        this.segments = segments;
    }

    /**
     * @deprecated since 2.0.0 merge mechanism is now based on profile aliases, and this property is not used anymore
     * @return the profile id this profile was merged with, or {@code null}
     */
    @Deprecated
    public String getMergedWith() {
        return mergedWith;
    }

    /**
     * @deprecated since 2.0.0 merge mechanism is now based on profile aliases, and this property is not used anymore
     * @param mergedWith the profile id this profile was merged with
     */
    @Deprecated
    public void setMergedWith(String mergedWith) {
        this.mergedWith = mergedWith;
    }

    /**
     * Scoring model values keyed by scoring id.
     *
     * @return the score map
     */
    public Map<String, Integer> getScores() {
        return scores;
    }

    /**
     * Sets scoring model values keyed by scoring id.
     *
     * @param scores new value for scores
     */
    public void setScores(Map<String, Integer> scores) {
        this.scores = scores;
    }

    /**
     * All consents on this profile, including revoked entries.
     *
     * @return consent map keyed by scope/type path
     */
    public Map<String, Consent> getConsents() {
        return consents;
    }

    /**
     * Whether this profile is marked anonymous via the system property {@code isAnonymousProfile}.
     *
     * @return {@code true} if anonymous, {@code false} otherwise
     */
    @XmlTransient
    public boolean isAnonymousProfile() {
        Boolean anonymous = (Boolean) getSystemProperties().get("isAnonymousProfile");
        return anonymous != null && anonymous;
    }

    /**
     * Adds or removes a consent on this profile.
     * Revoked consents remove the matching entry when present.
     *
     * @param consent the consent to store or revoke
     * @return {@code true} if the operation changed stored consents
     */
    @XmlTransient
    public boolean setConsent(Consent consent) {
        if (ConsentStatus.REVOKED.equals(consent.getStatus())) {
            if (consent.getScope() != null) {
                if (consents.containsKey(consent.getScope() + "/" + consent.getTypeIdentifier())) {
                    consents.remove(consent.getScope() + "/" + consent.getTypeIdentifier());
                    return true;
                }
            } else {
                if (consents.containsKey(consent.getTypeIdentifier())) {
                    consents.remove(consent.getTypeIdentifier());
                    return true;
                }
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
        sb.append(", consents=").append(consents);
        sb.append(", itemId='").append(itemId).append('\'');
        sb.append(", itemType='").append(itemType).append('\'');
        sb.append(", scope='").append(scope).append('\'');
        sb.append(", tenantId'").append(getTenantId()).append('\'');
        sb.append(", version=").append(version);
        sb.append('}');
        return sb.toString();
    }
}
