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

import java.util.Date;

/**
 * A browsing session attached to a {@link Persona}.
 * Personas are test or synthetic profiles; their sessions let marketers
 * preview personalized content as if they were a visitor in that category.
 */
public class PersonaSession extends Session {
    /**
     * The constant string identifier used as the item type for
     * {@link PersonaSession} instances.
     */
    public static final String ITEM_TYPE = "personaSession";
    private static final long serialVersionUID = -1499107289607498852L;

    /**
     * Constructs an empty {@link PersonaSession} instance.
     * This session must be fully configured and saved using persistence
     * services to represent a valid persona session record.
     */
    public PersonaSession() {
    }

    /**
     * Constructs a {@link PersonaSession} associated with a given profile,
     * item ID, and timestamp.
     * The resulting session is initialized with a system scope metadata.
     *
     * @param itemId the unique identifier for the persona session item.
     * @param profile the {@link Profile} object representing the
     * context of the session.
     *
     * @param timeStamp the date and time when this session was
     * created or recorded.
     */
    public PersonaSession(String itemId, Profile profile, Date timeStamp) {
        super(itemId, profile, timeStamp, Metadata.SYSTEM_SCOPE);
    }
}
