package org.oasis_open.wemi.context.server.api;

import javax.xml.bind.annotation.XmlTransient;
import java.util.List;

public class PersonaWithSessions {
    private Persona persona;

    private List<PersonaSession> sessions;

    public PersonaWithSessions() {
    }

    public PersonaWithSessions(Persona persona, List<PersonaSession> sessions) {
        this.persona = persona;
        this.sessions = sessions;
    }

    public Persona getPersona() {
        return persona;
    }

    public void setPersona(Persona persona) {
        this.persona = persona;
    }

    public List<PersonaSession> getSessions() {
        return sessions;
    }

    public void setSessions(List<PersonaSession> sessions) {
        this.sessions = sessions;
    }

    @XmlTransient
    public PersonaSession getLastSession() {
        return sessions.get(0);
    }
}
