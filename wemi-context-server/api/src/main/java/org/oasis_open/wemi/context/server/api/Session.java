package org.oasis_open.wemi.context.server.api;

import java.util.Properties;

public class Session extends Item {

    public static final String ITEM_TYPE = "session";

    private String userId;

    private Properties properties;

    public Session() {
    }

    public Session(String itemId, String userId) {
        super(itemId);
        this.userId = userId;
        properties = new Properties();
    }

    public String getUserId() {
        return userId;
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

}
