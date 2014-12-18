package org.oasis_open.contextserver.api;

import javax.xml.bind.annotation.XmlTransient;
import java.util.*;

/**
 * Created by loom on 24.04.14.
 */
public class Event extends Item implements TimestampedItem {

    public static final String ITEM_TYPE = "event";
    public static final String HTTP_REQUEST_ATTRIBUTE = "http_request";
    public static final String HTTP_RESPONSE_ATTRIBUTE = "http_response";
    //    public static final String PARENT_ITEM_TYPE = "session";
    private String eventType;
    private String sessionId = null;
    private String userId = null;
    private Date timeStamp;
    private Map<String, Object> properties;

    private transient User user;
    private transient Session session;

    private EventSource source;
    private EventTarget target;

    private transient boolean persistent = true;

    // attributes are not serializable, and can be used to pass additional contextual object such as HTTP request
    // response objects, etc...
    private transient Map<String, Object> attributes = new LinkedHashMap<String, Object>();

    public Event() {
    }

    public Event(String eventType, Session session, User user, EventSource source, EventTarget target, Date timestamp) {
        super(UUID.randomUUID().toString());
        this.eventType = eventType;
        this.user = user;
        this.session = session;
        this.userId = user.getItemId();
        this.source = source;
        this.target = target;

        if (session != null) {
            this.sessionId = session.getItemId();
        }
        this.timeStamp = timestamp;

        this.properties = new HashMap<String, Object>();
    }

    public Event(String eventType, Session session, User user, EventSource source, EventTarget target, Map<String, Object> properties, Date timestamp) {
        this(eventType, session, user, source, target, timestamp);
        if(properties != null) {
            this.properties = properties;
        }
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEventType() {
        return eventType;
    }

    public Date getTimeStamp() {
        return timeStamp;
    }

    @XmlTransient
    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
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

    public EventSource getSource() {
        return source;
    }

    public void setSource(EventSource source) {
        this.source = source;
    }

    public EventTarget getTarget() {
        return target;
    }

    public void setTarget(EventTarget target) {
        this.target = target;
    }
}
