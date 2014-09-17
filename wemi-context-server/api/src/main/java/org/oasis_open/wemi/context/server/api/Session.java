package org.oasis_open.wemi.context.server.api;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Session extends Item {

    public static final String ITEM_TYPE = "session";

    private String userId;

    private Map<String,Object> properties;

    private Date sessionCreationDate;

    private Date lastEventDate;

    private long duration = 0;

    public Session() {
    }

    public Session(String itemId, String userId, Date sessionCreationDate) {
        super(itemId);
        this.userId = userId;
        properties = new HashMap<String,Object>();
        this.sessionCreationDate = sessionCreationDate;
    }

    public String getUserId() {
        return userId;
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

    public Date getSessionCreationDate() {
        return sessionCreationDate;
    }

    public Date getLastEventDate() {
        return lastEventDate;
    }

    public void setLastEventDate(Date lastEventDate) {
        this.lastEventDate = lastEventDate;
        duration = lastEventDate.getTime() - sessionCreationDate.getTime();
    }

    public long getDuration() {
        return duration;
    }
}
