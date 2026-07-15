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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A time-bounded interaction between a user (via their associated {@link Profile}) and a unomi-enabled application. A session represents a sequence of operations the user
 * performed during its duration. In the context of web applications, sessions are usually linked to HTTP sessions.
 */
public class Session extends Item implements TimestampedItem, SystemPropertiesItem {

    /**
     * The Session ITEM_TYPE.
     *
     * @see Item for a discussion of ITEM_TYPE
     */
    public static final String ITEM_TYPE = "session";
    private static final long serialVersionUID = 4628640198281687336L;
    /**
     * Identifier of the profile linked to this session.
     * @api.example profile-1
     */
    private String profileId;

    /**
     * Profile associated with this session.
     */
    private Profile profile;

    /**
     * All user-visible session properties.
     * @api.example {"utm_source":"newsletter"}
     */
    private Map<String, Object> properties = new HashMap<>();

    /**
     * Internal properties used by implementations and not usually returned to clients.
     */
    private Map<String, Object> systemProperties = new HashMap<>();

    /**
     * When this session was created (ISO-8601 in JSON).
     * @api.example 2024-06-15T10:00:00.000Z
     */
    private Date timeStamp;

    /**
     * Scope this session belongs to.
     * @api.example mysite
     */
    private String scope;

    /**
     * Timestamp of the most recent event in this session (ISO-8601 in JSON).
     * @api.example 2024-06-15T10:30:00.000Z
     */
    private Date lastEventDate;

    /**
     * Number of events recorded in this session.
     * @api.example 4
     */
    private int size = 0;

    /**
     * Elapsed time between session start and last event, in milliseconds.
     * @api.example 1800000
     */
    private int duration = 0;

    /**
     * Event types that triggered session creation.
     * @api.example ["view"]
     */
    private List<String> originEventTypes = new ArrayList<>();
    /**
     * Event ids that triggered session creation.
     * @api.example ["evt-1"]
     */
    private List<String> originEventIds = new ArrayList<>();

    /**
     * Default constructor.
     */
    public Session() {
    }

    /**
     * Creates a session linked to a profile, timestamp, and scope.
     *
     * @param itemId    the session identifier
     * @param profile   the associated profile
     * @param timeStamp the session start time
     * @param scope     the session scope
     */
    public Session(String itemId, Profile profile, Date timeStamp, String scope) {
        super(itemId);
        this.profile = profile;
        this.profileId = profile.getItemId();
        this.timeStamp = timeStamp;
        this.scope = scope;
    }

    /**
     * Identifier of the profile linked to this session.
     *
     * @return the profile id
     */
    public String getProfileId() {
        return profileId;
    }

    /**
     * Profile associated with this session.
     *
     * @return the profile
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
     * Value of the named session property.
     *
     * @param name the property name
     * @return the property value, or {@code null} if unset
     */
    public Object getProperty(String name) {
        return properties.get(name);
    }

    /**
     * All user-visible session properties.
     *
     * @return the property map
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
     * Internal properties used by implementations and not shown in UIs.
     *
     * @return the system property map
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
     * When this session was created.
     *
     * @return the creation timestamp
     */
    public Date getTimeStamp() {
        return timeStamp;
    }

    /**
     * Timestamp of the most recent event in this session.
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
     * Elapsed time between session start and last event, in milliseconds.
     *
     * @return the session duration
     */
    public int getDuration() {
        return duration;
    }

    /**
     * Number of events recorded in this session.
     *
     * @return the event count
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

    /**
     * Scope this session belongs to.
     *
     * @return the scope id
     */
    public String getScope() {
        return scope;
    }

    /**
     * Sets the scope this session belongs to.
     *
     * @param scope the session scope identifier
     */
    public void setScope(String scope) {
        this.scope = scope;
    }

    /**
     * Event types that triggered session creation.
     *
     * @return the origin event types
     */
    public List<String> getOriginEventTypes() {
        return originEventTypes;
    }

    /**
     * Sets the event types that triggered session creation.
     *
     * @param originEventTypes the origin event types
     */
    public void setOriginEventTypes(List<String> originEventTypes) {
        this.originEventTypes = originEventTypes;
    }

    /**
     * Event ids that triggered session creation.
     *
     * @return the origin event ids
     */
    public List<String> getOriginEventIds() {
        return originEventIds;
    }

    /**
     * Sets the event ids that triggered session creation.
     *
     * @param originEventIds the origin event ids
     */
    public void setOriginEventIds(List<String> originEventIds) {
        this.originEventIds = originEventIds;
    }
}
