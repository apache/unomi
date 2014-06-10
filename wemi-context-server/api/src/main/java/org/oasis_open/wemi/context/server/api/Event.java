package org.oasis_open.wemi.context.server.api;

import java.util.Properties;

/**
 * Created by loom on 24.04.14.
 */
public class Event extends Item {

    public static final String EVENT_ITEM_TYPE="event";

    public Event() {
        type=EVENT_ITEM_TYPE;
    }

    public Event(String itemId) {
        super(itemId);
        type=EVENT_ITEM_TYPE;
    }

    public Event(String itemId, String type, Properties properties) {
        super(itemId, type, properties);
        type=EVENT_ITEM_TYPE;
    }

}
