package org.oasis_open.wemi.context.server.api;

import java.util.Properties;

/**
 * Created by loom on 24.04.14.
 */
public class User extends Item {

    public static final String USER_ITEM_TYPE="user";

    public User() {
        type=USER_ITEM_TYPE;
    }

    public User(String itemId) {
        super(itemId, USER_ITEM_TYPE, new Properties());
    }

    public User(String itemId, String type, Properties properties) {
        super(itemId, type, properties);
    }
}
