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
     * Default constructor.
     */
    public PersonaWithSessions() {
    }

    /**
     * Creates a persona bundled with its sessions.
     *
     * @param persona  the persona
     * @param sessions related persona sessions
     */
    public PersonaWithSessions(Persona persona, List<PersonaSession> sessions) {
        this.persona = persona;
        this.sessions = sessions;
    }

    /**
     * The persona being simulated or inspected.
     *
     * @return the persona
     */
    public Persona getPersona() {
        return persona;
    }

    /**
     * Sets the persona.
     *
     * @param persona the persona
     */
    public void setPersona(Persona persona) {
        this.persona = persona;
    }

    /**
     * Sessions linked to the persona.
     *
     * @return the persona sessions
     */
    public List<PersonaSession> getSessions() {
        return sessions;
    }

    /**
     * Sets the persona sessions.
     *
     * @param sessions the session list
     */
    public void setSessions(List<PersonaSession> sessions) {
        this.sessions = sessions;
    }

    /**
     * Most recent session, taken as the first element of {@link #getSessions()}.
     *
     * @return the latest session, or {@code null} if none exist
     */
    @XmlTransient
    public PersonaSession getLastSession() {
        return sessions.size()>0?sessions.get(0):null;
    }
}