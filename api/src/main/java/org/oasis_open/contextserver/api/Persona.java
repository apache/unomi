package org.oasis_open.contextserver.api;

/**
 * A persona is a "virtual" user used to represent categories of users, and may also be used to test
 * how a personalized experience would look like using this virtual user.
 */
public class Persona extends User {

    public static final String ITEM_TYPE = "persona";

    public Persona() {
    }

    public Persona(String personaId) {
        super(personaId);
    }

}
