package org.oasis_open.wemi.context.server.api;

import javax.xml.bind.annotation.XmlTransient;
import java.util.*;

/**
 * A persona is a "virtual" user used to represent categories of users, and may also be used to test
 * how a personalized experience would look like using this virtual user.
 */
public class Persona extends User {

    public static final String ITEM_TYPE = "persona";

    private List<Session> sessions;

    public Persona() {
    }

    public Persona(String personaId) {
        super(personaId);
        this.sessions = new ArrayList<Session>();
    }

    public List<Session> getSessions() {
        return sessions;
    }

    public void setSessions(List<Session> sessions) {
        this.sessions = sessions;
    }

    @XmlTransient
    public Session getSession() {
        return sessions.get(sessions.size() - 1);
    }


}
