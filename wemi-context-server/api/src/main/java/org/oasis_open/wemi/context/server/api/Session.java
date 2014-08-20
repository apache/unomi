package org.oasis_open.wemi.context.server.api;

import java.util.Date;
import java.util.Properties;

public class Session extends Item {

    public static final String ITEM_TYPE = "session";

    private String userId;

    private Properties properties;

    private Date sessionCreationDate;

    private Date lastEventDate;

    private long duration = 0;

    public Session() {
    }

    public Session(String itemId, String userId) {
        super(itemId);
        this.userId = userId;
        properties = new Properties();
        this.sessionCreationDate = new Date();
    }

    public String getUserId() {
        return userId;
    }

    public void setLastEventDate(Date lastEventDate) {
        this.lastEventDate = lastEventDate;
        duration = lastEventDate.getTime() - sessionCreationDate.getTime();
    }

    public void setProperty(String name, String value) {
        properties.setProperty(name, value);
    }

    public String getProperty(String name) {
        return properties.getProperty(name);
    }

    public Properties getProperties() {
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
