package org.oasis_open.wemi.context.server.api;

import javax.xml.bind.annotation.XmlTransient;
import java.util.*;

/**
 * Created by loom on 24.04.14.
 */
public class Event extends Item {

    public static final String ITEM_TYPE = "event";
//    public static final String PARENT_ITEM_TYPE = "session";
    private String eventType;
    private String sessionId = null;
    private String userId = null;
    private Date timeStamp;
    private Map<String,Object> properties;

    private transient User user;
    private transient Session session;

    // attributes are not serializable, and can be used to pass additional contextual object such as HTTP request
    // response objects, etc...
    private transient Map<String, Object> attributes = new LinkedHashMap<String, Object>();

    public Event() {
    }

    public Event(String eventType, Session session, User user, Date timestamp) {
        super(UUID.randomUUID().toString());
        this.eventType = eventType;
        this.user = user;
        this.session = session;
        this.userId = user.getItemId();
        if (session != null) {
            this.sessionId = session.getItemId();
        }
        this.timeStamp = timestamp;

        this.properties = new HashMap<String, Object>();
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
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }

    public Object getProperty(String name) {
        return properties.get(name);
    }

    public Map<String,Object> getProperties() {
        return properties;
    }

}
