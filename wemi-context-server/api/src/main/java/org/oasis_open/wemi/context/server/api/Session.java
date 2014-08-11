package org.oasis_open.wemi.context.server.api;

import java.util.Properties;

public class Session extends Item {

    public static final String SESSION_ITEM_TYPE = "session";
//    public static final String PARENT_ITEM_TYPE = "user";

    public Session() {
        type= SESSION_ITEM_TYPE;
    }

    public Session(String itemId, String userId) {
        super(itemId, SESSION_ITEM_TYPE, null, new Properties());
//        super(itemId, SESSION_ITEM_TYPE, userId, new Properties());
        getProperties().setProperty("userId",userId);
    }

    public Session(String itemId, String type, Properties properties) {
        super(itemId, type, properties);
    }

    public String getUserId() {
        return getProperty("userId");
    }

}
