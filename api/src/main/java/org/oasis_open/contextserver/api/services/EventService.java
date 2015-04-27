package org.oasis_open.contextserver.api.services;

/*
 * #%L
 * context-server-api
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.EventProperty;
import org.oasis_open.contextserver.api.PartialList;
import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.List;
import java.util.Set;

/**
 * Event service.
 */
public interface EventService {

    int NO_CHANGE = 0;
    int SESSION_UPDATED = 1;
    int PROFILE_UPDATED = 2;

    int send(Event event);

    /**
     * Returns a list of available event properties.
     * 
     * @return a list of available event properties
     */
    List<EventProperty> getEventProperties();

    Set<String> getEventTypeIds();

    PartialList<Event> searchEvents(Condition condition, int offset, int size);

    PartialList<Event> searchEvents(String sessionId, String eventType, String query, int offset, int size, String sortBy);

    boolean hasEventAlreadyBeenRaised(Event event, boolean session);
}
