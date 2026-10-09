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
package org.apache.unomi.api.security;

import java.security.Principal;
import java.util.Objects;
import java.util.Set;

/**
 * Marks a request subject as a validated third-party peer in single-tenant compatibility mode.
 * <p>
 * This is not an administrator role: it only carries the abilities that Unomi 3.0 gave a server
 * that passed the {@code X-Unomi-Peer} check (choose a profile id, set an event id, merge on login,
 * update another profile). Private / admin endpoints ignore it.
 */
public final class CompatPeerPrincipal implements Principal {

    /** Ability to keep a client-supplied event item id. */
    public static final String ABILITY_SET_EVENT_ID = "setEventId";
    /** Ability to bind the request to a body-supplied profile id. */
    public static final String ABILITY_CHOOSE_PROFILE_ID = "chooseProfileId";
    /** Ability to claim a merge identifier and merge profiles on login. */
    public static final String ABILITY_MERGE_ON_LOGIN = "mergeOnLogin";
    /** Ability to update another profile and write segments, scores, consents and systemProperties. */
    public static final String ABILITY_UPDATE_OTHER_PROFILES = "updateOtherProfiles";

    /** The 3.0 peer set: every ability above. Used when a provider does not configure abilities at all. */
    public static final Set<String> DEFAULT_ABILITIES = Set.of(
            ABILITY_SET_EVENT_ID,
            ABILITY_CHOOSE_PROFILE_ID,
            ABILITY_MERGE_ON_LOGIN,
            ABILITY_UPDATE_OTHER_PROFILES
    );

    private final String providerId;
    private final Set<String> abilities;

    /**
     * @param providerId the configured provider name
     * @param abilities  abilities granted to this peer, each one of the {@code ABILITY_*} constants.
     *                   An empty set grants nothing; pass {@link #DEFAULT_ABILITIES} for the 3.0 set.
     * @throws IllegalArgumentException when the provider id is blank, or the abilities are
     *                                  {@code null} or contain a name that is not a known ability
     */
    public CompatPeerPrincipal(String providerId, Set<String> abilities) {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId cannot be blank");
        }
        if (abilities == null) {
            throw new IllegalArgumentException("abilities cannot be null");
        }
        if (!DEFAULT_ABILITIES.containsAll(abilities)) {
            throw new IllegalArgumentException("Unknown compat peer ability in " + abilities);
        }
        this.providerId = providerId;
        this.abilities = Set.copyOf(abilities);
    }

    public String getProviderId() {
        return providerId;
    }

    public Set<String> getAbilities() {
        return abilities;
    }

    public boolean hasAbility(String ability) {
        return ability != null && abilities.contains(ability);
    }

    @Override
    public String getName() {
        return providerId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CompatPeerPrincipal that = (CompatPeerPrincipal) o;
        return Objects.equals(providerId, that.providerId) && Objects.equals(abilities, that.abilities);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerId, abilities);
    }

    @Override
    public String toString() {
        return "CompatPeerPrincipal[" + providerId + ", abilities=" + abilities + "]";
    }
}
