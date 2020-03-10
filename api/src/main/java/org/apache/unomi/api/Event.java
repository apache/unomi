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

import org.apache.unomi.api.actions.ActionPostExecutor;

import javax.xml.bind.annotation.XmlTransient;
import java.util.*;

/**
 * An event that can be processed and evaluated by the context server. Events can be triggered by clients following user actions or can also be issued internally in the context
 * server in response to another event. Conceptually, an event can be seen as a sentence, the event's type being the verb, the source the subject and the target the object.
 * <p>
 * Source and target can be any unomi item but are not limited to them. In particular, as long as they can be described using properties and unomi’s type mechanism and can be
 * processed either natively or via extension plugins, source and target can represent just about anything.
 */
public class Event extends Item implements TimestampedItem {

    /**
     * The Event ITEM_TYPE.
     *
     * @see Item for a discussion of ITEM_TYPE
     */
    public static final String ITEM_TYPE = "event";
    /**
     * A constant for the name of the attribute that can be used to retrieve the current HTTP request.
     */
    public static final String HTTP_REQUEST_ATTRIBUTE = "http_request";
    /**
     * A constant for the name of the attribute that can be used to retrieve the current HTTP response.
     */
    public static final String HTTP_RESPONSE_ATTRIBUTE = "http_response";
    private static final long serialVersionUID = -1096874942838593575L;
    private String eventType;
    private String sessionId = null;
    private String profileId = null;
    private Date timeStamp;
    private Map<String, Object> properties;

    private transient Profile profile;
    private transient Session session;
    private transient List<ActionPostExecutor> actionPostExecutors;

    private String scope;

    private Item source;
    private Item target;

    private boolean persistent = true;

    private transient Map<String, Object> attributes = new LinkedHashMap<>();

    /**
     * Instantiates a new Event.
     */
    public Event() {
    }

    /**
     * Instantiates a new Event.
     *
     * @param eventType the event type identifier
     * @param session   the session associated with the event
     * @param profile   the profile associated with the event
     * @param scope     the scope from which the event is issued
     * @param source    the source of the event
     * @param target    the target of the event if any
     * @param timestamp the timestamp associated with the event if provided
     */
    public Event(String eventType, Session session, Profile profile, String scope, Item source, Item target, Date timestamp) {
        super(UUID.randomUUID().toString());
        this.eventType = eventType;
        this.profile = profile;
        this.session = session;
        this.profileId = profile.getItemId();
        this.scope = scope;
        this.source = source;
        this.target = target;

        if (session != null) {
            this.sessionId = session.getItemId();
        }
        this.timeStamp = timestamp;

        this.properties = new HashMap<String, Object>();

        actionPostExecutors = new ArrayList<>();
    }

    /**
     * Instantiates a new Event.
     *
     * @param eventType  the event type identifier
     * @param session    the session associated with the event
     * @param profile    the profile associated with the event
     * @param scope      the scope from which the event is issued
     * @param source     the source of the event
     * @param target     the target of the event if any
     * @param timestamp  the timestamp associated with the event if provided
     * @param properties the properties for this event if any
     * @param persistent specifies if the event needs to be persisted
     */
    public Event(String eventType, Session session, Profile profile, String scope, Item source, Item target, Map<String, Object> properties, Date timestamp, boolean persistent) {
        this(eventType, session, profile, scope, source, target, timestamp);
        this.persistent = persistent;
        if (properties != null) {
            this.properties = properties;
        }
    }

    /**
     * Retrieves the session identifier if available.
     *
     * @return the session identifier or {@code null} if unavailable
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Set the session id
     * @param sessionId the session id
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Retrieves the profile identifier of the Profile associated with this event
     *
     * @return the profile id
     */
    public String getProfileId() {
        return profileId;
    }

    /**
     * Sets the profile id.
     *
     * @param profileId the profile id
     */
    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    /**
     * Retrieves the event type.
     *
     * @return the event type
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * Sets the event type
     * @param eventType the event type
     */
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    /**
     * Retrieves the event time stamp
     *
     * @return the event time stamp
     */
    public Date getTimeStamp() {
        return timeStamp;
    }

    /**
     * @param timeStamp set the time stamp
     */
    public void setTimeStamp(Date timeStamp) {
        this.timeStamp = timeStamp;
    }

    /**
     * Retrieves the profile.
     *
     * @return the profile
     */
    @XmlTransient
    public Profile getProfile() {
        return profile;
    }

    /**
     * Sets the profile.
     *
     * @param profile the profile
     */
    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    /**
     * Retrieves the session.
     *
     * @return the session
     */
    @XmlTransient
    public Session getSession() {
        return session;
    }

    /**
     * Sets the session.
     *
     * @param session the session
     */
    public void setSession(Session session) {
        this.session = session;
    }

    /**
     * Determines whether this Event needs to be persisted to the context server or not. Events that don't participate in building the user profile don't usually need to be
     * persisted.
     *
     * @return {@code true} if this Event needs to be persisted, {@code false} otherwise
     */
    public boolean isPersistent() {
        return persistent;
    }

    /**
     * Specifies if this Event needs to be persisted.
     *
     * @param persistent {@code true} if this Event needs to be persisted, {@code false} otherwise
     */
    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    /**
     * Retrieves the attributes. Attributes are not serializable, and can be used to provide additional contextual objects such as HTTP request or response objects, etc...
     *
     * @return the attributes
     */
    @XmlTransient
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * Sets the map of attribues
     * @param attributes the attributes map
     */
    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    /**
     * Sets the property identified by the provided name to the specified value.
     *
     * @param name  the name of the property to be set
     * @param value the value of the property
     */
    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }

    /**
     * Retrieves the value of the property identified by the specified name.
     *
     * @param name the name of the property to be retrieved
     * @return the value of the property identified by the specified name
     */
    public Object getProperty(String name) {
        return properties.get(name);
    }

    /**
     * Retrieves the properties.
     *
     * @return the properties
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * Sets map of properties that will override existing field if it exists
     *
     * @param properties Map of new Properties
     */
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    /**
     * @return the scope
     */
    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    /**
     * Retrieves the source.
     *
     * @return the source
     */
    public Item getSource() {
        return source;
    }

    /**
     * Sets the source.
     *
     * @param source the source
     */
    public void setSource(Item source) {
        this.source = source;
    }

    /**
     * Retrieves the target.
     *
     * @return the target
     */
    public Item getTarget() {
        return target;
    }

    /**
     * Sets the target.
     *
     * @param target the target
     */
    public void setTarget(Item target) {
        this.target = target;
    }

    /**
     * Retrieves the action post executors for this event, if extra actions need to be executed after all Rule-triggered actions have been processed
     *
     * @return the action post executors
     */
    @XmlTransient
    public List<ActionPostExecutor> getActionPostExecutors() {
        return actionPostExecutors;
    }

    /**
     * Sets the action post executors.
     *
     * @param actionPostExecutors the action post executors
     */
    public void setActionPostExecutors(List<ActionPostExecutor> actionPostExecutors) {
        this.actionPostExecutors = actionPostExecutors;
    }
}
