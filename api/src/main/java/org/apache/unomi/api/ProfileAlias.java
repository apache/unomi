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
 * Maps a client-specific profile identifier to a canonical Unomi profile.
 * Lets multiple applications reference the same visitor under different ids
 * while sharing one merged profile record.
 */
public class ProfileAlias extends Item {

    /**
     * The constant string used to identify this item type as a profile alias.
     */
    public static final String ITEM_TYPE = "profileAlias";

    private String profileID;

    private String clientID;

    private Date creationTime;

    private Date modifiedTime;

    /**
     * Constructs an empty ProfileAlias object.
     */
    public ProfileAlias() {
    }

    /**
     * Retrieves the unique identifier of the associated profile.
     * @return The profile ID as a {@link String}.
     */
    public String getProfileID() {
        return profileID;
    }

    /**
     * Sets the unique identifier of the associated profile.
     * @param profileID The profile ID to set.
     */
    public void setProfileID(String profileID) {
        this.profileID = profileID;
    }

    /**
     * Retrieves the client identifier associated with this alias.
     * @return The client ID as a {@link String}, or null if not set.
     */
    public String getClientID() {
        return clientID;
    }

    /**
     * Sets the client identifier associated with this alias.
     * @param clientID The client ID to set.
     */
    public void setClientID(String clientID) {
        this.clientID = clientID;
    }

    /**
     * Retrieves the date and time when this profile alias was created.
     * @return The creation timestamp as a {@link java.util.Date}.
     */
    public Date getCreationTime() {
        return creationTime;
    }

    /**
     * Sets the date and time of creation for this profile alias.
     * @param creationTime The creation timestamp to set.
     */
    public void setCreationTime(Date creationTime) {
        this.creationTime = creationTime;
    }

    /**
     * Retrieves the date and time when this profile alias was last modified.
     * or null if no modification time has been recorded.
     * @return The {@link java.util.Date} representing the modification time,
     */
    public Date getModifiedTime() {
        return modifiedTime;
    }

    /**
     * Sets the date and time when this profile alias was last modified.
     * @param modifiedTime The new timestamp indicating the modification time.
     */
    public void setModifiedTime(Date modifiedTime) {
        this.modifiedTime = modifiedTime;
    }
}
