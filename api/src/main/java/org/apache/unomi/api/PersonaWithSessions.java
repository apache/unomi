/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.unomi.api;

import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.List;

/**
 * Bundles a {@link Persona} with its related {@link PersonaSession} list.
 * REST and tooling use this wrapper when returning a persona together with
 * the sessions needed to simulate or inspect that persona.
 */
public class PersonaWithSessions implements Serializable {
    private Persona persona;

    private List<PersonaSession> sessions;

    /**
     * Constructs a new, empty {@link PersonaWithSessions} instance.
     */
    public PersonaWithSessions() {
    }

    /**
     * Constructs a {@link PersonaWithSessions} object with the specified
     * persona and list of sessions.
     * @param persona the associated {@link Persona}
     * @param sessions the list of associated {@link PersonaSession}s
     */
    public PersonaWithSessions(Persona persona, List<PersonaSession> sessions) {
        this.persona = persona;
        this.sessions = sessions;
    }

    /**
     * Returns the {@link Persona} associated with this object.
     * @return the contained {@link Persona}
     */
    public Persona getPersona() {
        return persona;
    }

    /**
     * Sets the {@link Persona} associated with this object.
     * @param persona the new {@link Persona}
     */
    public void setPersona(Persona persona) {
        this.persona = persona;
    }

    /**
     * Returns all {@link PersonaSession}s associated with this object.
     * @return a list of {@link PersonaSession}s
     */
    public List<PersonaSession> getSessions() {
        return sessions;
    }

    /**
     * Sets the list of {@link PersonaSession}s associated with this object.
     * @param sessions the new list of {@link PersonaSession}s
     */
    public void setSessions(List<PersonaSession> sessions) {
        this.sessions = sessions;
    }

    /**
     * Retrieves the last session from the stored list. This is assumed to be
     * the first element (index 0).
     * If no sessions are present, returns null.
     * @return the most recent {@link PersonaSession}, or null if none exist
     */
    @XmlTransient
    public PersonaSession getLastSession() {
        return sessions.size()>0?sessions.get(0):null;
    }
}