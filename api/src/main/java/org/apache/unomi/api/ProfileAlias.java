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

import java.util.Date;

/**
 * Cross-application link between a client profile id and a canonical {@link Profile}.
 * When the same visitor is known under different ids in separate scopes or apps,
 * aliases let {@link org.apache.unomi.api.services.ProfileService} merge activity
 * onto one persisted profile record.
 */
public class ProfileAlias extends Item {

    /**
     * Item type identifier for profile aliases.
     */
    public static final String ITEM_TYPE = "profileAlias";

    /**
     * Canonical profile id linked by this alias.
     * @api.example profile-1
     */
    private String profileID;

    /**
     * Client that owns / created this alias link.
     * @api.example web-tracker
     */
    private String clientID;

    /**
     * When this alias was created (ISO-8601 in JSON).
     * @api.example 2024-06-15T10:00:00.000Z
     */
    private Date creationTime;

    /**
     * When this alias was last modified (ISO-8601 in JSON).
     * @api.example 2024-06-15T11:00:00.000Z
     */
    private Date modifiedTime;

    /**
     * Creates an empty profile alias.
     */
    public ProfileAlias() {
    }

    /**
     * Canonical profile id linked by this alias.
     *
     * @return profile id
     */
    public String getProfileID() {
        return profileID;
    }

    /**
     * Sets the canonical profile id.
     *
     * @param profileID profile id
     */
    public void setProfileID(String profileID) {
        this.profileID = profileID;
    }

    /**
     * Client-specific profile identifier.
     *
     * @return client id, or {@code null} if unset
     */
    public String getClientID() {
        return clientID;
    }

    /**
     * Sets the client-specific profile identifier.
     *
     * @param clientID client id
     */
    public void setClientID(String clientID) {
        this.clientID = clientID;
    }

    /**
     * When this alias was created.
     *
     * @return creation time
     */
    public Date getCreationTime() {
        return creationTime;
    }

    /**
     * Sets the creation time.
     *
     * @param creationTime creation time
     */
    public void setCreationTime(Date creationTime) {
        this.creationTime = creationTime;
    }

    /**
     * When this alias was last modified.
     *
     * @return last modification time, or {@code null} if unset
     */
    public Date getModifiedTime() {
        return modifiedTime;
    }

    /**
     * Sets the last modification time.
     *
     * @param modifiedTime last modification time
     */
    public void setModifiedTime(Date modifiedTime) {
        this.modifiedTime = modifiedTime;
    }
}
