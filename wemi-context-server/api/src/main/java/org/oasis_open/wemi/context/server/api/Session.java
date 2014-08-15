package org.oasis_open.wemi.context.server.api;

public class Session extends Item {

    public static final String ITEM_TYPE = "session";

    private String userId;

    public Session() {
    }

    public Session(String itemId, String userId) {
        super(itemId);
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }


}
