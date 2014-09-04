package org.oasis_open.wemi.context.server.api;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class Session extends Item {

    public static final String ITEM_TYPE = "session";

    private String userId;

    private User user;

    private Map<String,Object> properties;

    private Date sessionCreationDate;

    private Date lastEventDate;

    private long duration = 0;

    public Session() {
    }

    public Session(String itemId, User user, Date sessionCreationDate) {
        super(itemId);
        this.userId = user.getItemId();
        this.user = user;
        properties = new HashMap<String,Object>();
        this.sessionCreationDate = sessionCreationDate;
    }

    public String getUserId() {
        return userId;
    }

    public User getUser() {
        return user;
    }



    public void setLastEventDate(Date lastEventDate) {
        this.lastEventDate = lastEventDate;
        duration = lastEventDate.getTime() - sessionCreationDate.getTime();
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

    public Date getSessionCreationDate() {
        return sessionCreationDate;
    }

    public Date getLastEventDate() {
        return lastEventDate;
    }

    public long getDuration() {
        return duration;
    }
}
