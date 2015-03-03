package org.oasis_open.contextserver.api;

import javax.xml.bind.annotation.XmlTransient;
import java.util.*;

public class Event extends Item implements TimestampedItem {

    public static final String ITEM_TYPE = "event";
    public static final String HTTP_REQUEST_ATTRIBUTE = "http_request";
    public static final String HTTP_RESPONSE_ATTRIBUTE = "http_response";

    private String eventType;
    private String sessionId = null;
    private String profileId = null;
    private Date timeStamp;
    private Map<String, Object> properties;

    private transient Profile profile;
    private transient Session session;

    private String scope;

    private Item source;
    private Item target;

    private transient boolean persistent = true;

    // attributes are not serializable, and can be used to pass additional contextual object such as HTTP request
    // response objects, etc...
    private transient Map<String, Object> attributes = new LinkedHashMap<String, Object>();

    public Event() {
    }

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
    }

    public Event(String eventType, Session session, Profile profile, String scope, Item source, Item target, Map<String, Object> properties, Date timestamp) {
        this(eventType, session, profile, scope, source, target, timestamp);
        if(properties != null) {
            this.properties = properties;
        }
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getEventType() {
        return eventType;
    }

    public Date getTimeStamp() {
        return timeStamp;
    }

    @XmlTransient
    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    @XmlTransient
    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    @XmlTransient
    public boolean isPersistent() {
        return persistent;
    }

    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    @XmlTransient
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }

    public Object getProperty(String name) {
        return properties.get(name);
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Item getSource() {
        return source;
    }

    public void setSource(Item source) {
        this.source = source;
    }

    public Item getTarget() {
        return target;
    }

    public void setTarget(Item target) {
        this.target = target;
    }
}
