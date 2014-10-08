package org.oasis_open.wemi.context.server.api;

import java.util.Date;

public class PersonaSession extends Session {
    public static final String ITEM_TYPE = "personaSession";

    public PersonaSession() {
    }

    public PersonaSession(String itemId, User user, Date timeStamp) {
        super(itemId, user, timeStamp);
    }
}
