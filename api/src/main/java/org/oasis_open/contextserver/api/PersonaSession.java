package org.oasis_open.contextserver.api;

import java.util.Date;

public class PersonaSession extends Session {
    public static final String ITEM_TYPE = "personaSession";

    public PersonaSession() {
    }

    public PersonaSession(String itemId, Profile profile, Date timeStamp) {
        super(itemId, profile, timeStamp);
    }
}
