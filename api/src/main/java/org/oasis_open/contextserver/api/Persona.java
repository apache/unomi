package org.oasis_open.contextserver.api;

/**
 * A persona is a "virtual" profile used to represent categories of profiles, and may also be used to test
 * how a personalized experience would look like using this virtual profile.
 */
public class Persona extends Profile {

    public static final String ITEM_TYPE = "persona";

    public Persona() {
    }

    public Persona(String personaId) {
        super(personaId);
    }

}
