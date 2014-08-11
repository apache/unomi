package org.oasis_open.wemi.context.server.api;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by loom on 24.04.14.
 */
public class Event extends Item {

    public static final String EVENT_ITEM_TYPE = "event";
//    public static final String PARENT_ITEM_TYPE = "session";
    private String eventType;
    private String sessionId = null;
    private String visitorId = null;
    private Date timeStamp;

    private transient User user;
    private transient Session session;

    // attributes are not serializable, and can be used to pass additional contextual object such as HTTP request
    // response objects, etc...
    private transient Map<String, Object> attributes = new LinkedHashMap<String, Object>();

    public Event() {
        this.type = EVENT_ITEM_TYPE;
        this.timeStamp = new Date();
    }

    public Event(String itemId, String eventType, String sessionId, String visitorId, Date timeStamp) {
        super(itemId, EVENT_ITEM_TYPE, null, new Properties());
//        super(itemId, EVENT_ITEM_TYPE, sessionId, new Properties());
        this.eventType = eventType;
        setProperty("eventType", eventType);
        this.sessionId = sessionId;
        setProperty("sessionId", sessionId);
        this.visitorId = visitorId;
        if (visitorId != null) {
            setProperty("visitorId", visitorId);
        }
        if (timeStamp != null) {
            this.timeStamp = timeStamp;
        } else {
            this.timeStamp = new Date();
        }

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        setProperty("eventTimeStamp", format.format(this.timeStamp));
    }

    public void setVisitorId(String visitorId) {
        this.visitorId = visitorId;
        if (visitorId != null) {
            setProperty("visitorId", visitorId);
        }
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getVisitorId() {
        return visitorId;
    }

    public String getEventType() {
        return eventType;
    }

    public Date getTimeStamp() {
        return timeStamp;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }
}
