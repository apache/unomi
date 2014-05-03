package org.oasis_open.wemi.context.server.api;

import java.util.Properties;

/**
 * Created by loom on 24.04.14.
 */
public class Item {

    String itemId;
    Properties properties = new Properties();

    public Item() {
    }

    public Item(String itemId) {
        this.itemId = itemId;
    }

    public String getItemId() {
        return itemId;
    }

    public void setProperty(String name, String value) {
        properties.setProperty(name, value);
    }

    public String getProperty(String name) {
        return properties.getProperty(name);
    }

    public boolean hasProperty(String name) {
        return properties.containsKey(name);
    }

    public String removeProperty(String name) {
        return (String) properties.remove(name);
    }

    public Properties getProperties() {
        return properties;
    }
}
