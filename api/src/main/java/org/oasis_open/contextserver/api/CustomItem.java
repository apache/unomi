package org.oasis_open.contextserver.api;

import java.util.HashMap;
import java.util.Map;

public class CustomItem extends Item {
    public static final String ITEM_TYPE = "custom";

    private Map<String,Object> properties = new HashMap<String,Object>();

    public CustomItem() {
    }

    public CustomItem(String itemId, String itemType) {
        super(itemId);
        this.itemType = itemType;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }
}
