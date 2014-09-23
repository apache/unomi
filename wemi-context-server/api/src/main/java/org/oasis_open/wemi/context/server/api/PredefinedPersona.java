package org.oasis_open.wemi.context.server.api;

import java.util.List;

/**
 * Created by toto on 23/09/14.
 */
public class PredefinedPersona {
    private Persona persona;

    private List<Session> sessions;

    public Persona getPersona() {
        return persona;
    }

    public void setPersona(Persona persona) {
        this.persona = persona;
    }

    public List<Session> getSessions() {
        return sessions;
    }

    public void setSessions(List<Session> sessions) {
        this.sessions = sessions;
    }
}
