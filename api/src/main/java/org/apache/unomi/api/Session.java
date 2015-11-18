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
import java.util.HashMap;
import java.util.Map;

/**
 * A time-bounded interaction between a user (via their associated {@link Profile}) and a unomi-enabled application. A session represents a sequence of operations the user
 * performed during its duration. In the context of web applications, sessions are usually linked to HTTP sessions.
 */
public class Session extends Item implements TimestampedItem {

    /**
     * The Session ITEM_TYPE.
     *
     * @see Item for a discussion of ITEM_TYPE
     */
    public static final String ITEM_TYPE = "session";
    private static final long serialVersionUID = 4628640198281687336L;
    private String profileId;

    private Profile profile;

    private Map<String, Object> properties = new HashMap<>();

    private Map<String, Object> systemProperties = new HashMap<>();

    private Date timeStamp;

    private String scope;

    private Date lastEventDate;

    private int size = 0;

    private int duration = 0;

    /**
     * Instantiates a new Session.
     */
    public Session() {
    }

    /**
     * Instantiates a new Session.
     *
     * @param itemId    the identifier for this Session
     * @param profile   the associated {@link Profile}
     * @param timeStamp the time stamp
     * @param scope     the scope
     */
    public Session(String itemId, Profile profile, Date timeStamp, String scope) {
        super(itemId);
        this.profile = profile;
        this.profileId = profile.getItemId();
        this.timeStamp = timeStamp;
        this.scope = scope;
    }

    /**
     * Retrieves the identifier of the associated Profile.
     *
     * @return the identifier of the associated Profile
     */
    public String getProfileId() {
        return profileId;
    }

    /**
     * Retrieves the associated Profile.
     *
     * @return the associated profile
     */
    public Profile getProfile() {
        return profile;
    }

    /**
     * Sets the associated Profile.
     *
     * @param profile the associated Profile
     */
    public void setProfile(Profile profile) {
        this.profileId = profile.getItemId();
        this.profile = profile;
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
     * Retrieves a Map of all property name - value pairs.
     *
     * @return a Map of all property name - value pairs
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * Sets the property name - value pairs.
     *
     * @param properties a Map containing the property name - value pairs
     */
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    /**
     * Retrieves a Map of system property name - value pairs. System properties can be used by implementations to store non-user visible properties needed for
     * internal purposes.
     *
     * @return a Map of system property name - value pairs
     */
    public Map<String, Object> getSystemProperties() {
        return systemProperties;
    }

    /**
     * Specifies the system property name - value pairs.
     *
     * @param systemProperties a Map of system property name - value pairs
     */
    public void setSystemProperties(Map<String, Object> systemProperties) {
        this.systemProperties = systemProperties;
    }

    /**
     * Retrieves the session creation timestamp.
     *
     * @return the session creation timestamp
     */
    public Date getTimeStamp() {
        return timeStamp;
    }

    /**
     * Retrieves the last event date.
     *
     * @return the last event date
     */
    public Date getLastEventDate() {
        return lastEventDate;
    }

    /**
     * Sets the last event date.
     *
     * @param lastEventDate the last event date
     */
    public void setLastEventDate(Date lastEventDate) {
        this.lastEventDate = lastEventDate;
        if (lastEventDate != null) {
            duration = (int) (lastEventDate.getTime() - timeStamp.getTime());
        }
    }

    /**
     * Retrieves the duration.
     *
     * @return the duration
     */
    public int getDuration() {
        return duration;
    }

    /**
     * Retrieves the size.
     *
     * @return the size
     */
    public int getSize() {
        return size;
    }

    /**
     * Sets the size.
     *
     * @param size the size
     */
    public void setSize(int size) {
        this.size = size;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }
}
