package org.oasis_open.wemi.context.server.api;

import java.util.Properties;

/**
 * Created by loom on 24.04.14.
 */
public class User extends Item {

    private Properties properties;

    public static final String ITEM_TYPE ="user";

    public User() {
    }

    public User(String itemId) {
        super(itemId);
        properties = new Properties();
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
