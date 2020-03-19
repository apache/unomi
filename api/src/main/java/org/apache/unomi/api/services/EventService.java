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

package org.apache.unomi.api.services;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.EventProperty;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.actions.ActionPostExecutor;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;

import java.util.List;
import java.util.Set;

/**
 * A service to publish events, notably issued from user interactions with tracked entities, in the context server.
 */
public interface EventService {

    /**
     * No change occurred following an event being handled.
     */
    int NO_CHANGE = 0;
    /**
     * The associated session was updated following an event being handled.
     */
    int SESSION_UPDATED = 1;
    /**
     * The associated profile was updated following an event being handled.
     */
    int PROFILE_UPDATED = 2;

    /**
     * Propagates the specified event in the context server, notifying
     * {@link EventListenerService} instances if needed. If the event is persistent ({@link Event#isPersistent()}, it will be persisted appropriately. Once the event is
     * propagated, any {@link ActionPostExecutor} the event defined will be executed and the user profile updated if needed.
     *
     * @param event the Event to be propagated
     * @return the result of the event handling as combination of EventService flags, to be checked using bitwise AND (&amp;) operator
     */
    int send(Event event);

    /**
     * Check if the sender is allowed to sent the speecified event. Restricted event must be explicitely allowed for a sender.
     *
     * @param event        event to test
     * @param thirdPartyId third party id
     * @return true if the event is allowed
     */
    boolean isEventAllowed(Event event, String thirdPartyId);

    /**
     * Get the third party server name, if the request is originated from a known peer
     *
     * @param key the key
     * @param ip  the ip
     * @return server name
     */
    String authenticateThirdPartyServer(String key, String ip);

    /**
     * Retrieves the list of available event properties.
     *
     * @return a list of available event properties
     */
    List<EventProperty> getEventProperties();

    /**
     * Retrieves the set of known event type identifiers.
     *
     * @return the set of known event type identifiers.
     */
    Set<String> getEventTypeIds();

    /**
     * Retrieves {@link Event}s matching the specified {@link Condition}. Events are ordered according to their time stamp ({@link Event#getTimeStamp()} and paged: only
     * {@code size} of them are retrieved, starting with the {@code offset}-th one.
     *
     * @param condition the Condition we want the Events to match to be retrieved
     * @param offset    zero or a positive integer specifying the position of the first event in the total ordered collection of matching events
     * @param size      a positive integer specifying how many matching events should be retrieved or {@code -1} if all of them should be retrieved
     * @return a {@link PartialList} of matching events
     */
    PartialList<Event> searchEvents(Condition condition, int offset, int size);

    /**
     * Retrieves {@link Event}s for the {@link Session} identified by the provided session identifier, matching any of the provided event types,
     * ordered according to the specified {@code sortBy} String and paged: only {@code size} of them are retrieved, starting with the {@code offset}-th one.
     * If a {@code query} is provided, a full text search is performed on the matching events to further filter them.
     *
     * @param sessionId  the identifier of the user session we're considering
     * @param eventTypes an array of event type names; the events to retrieve should at least match one of these
     * @param query      a String to perform full text filtering on events matching the other conditions
     * @param offset     zero or a positive integer specifying the position of the first event in the total ordered collection of matching events
     * @param size       a positive integer specifying how many matching events should be retrieved or {@code -1} if all of them should be retrieved
     * @param sortBy     an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering elements according to the property order in
     *                   the String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally followed by
     *                   a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @return a {@link PartialList} of matching events
     */
    PartialList<Event> searchEvents(String sessionId, String[] eventTypes, String query, int offset, int size, String sortBy);

    /**
     * Retrieves {@link Event}s matching the specified {@link Query}.
     *
     * @param query a {@link Query} specifying which Events to retrieve
     * @return a {@link PartialList} of {@code Event} instances matching the specified query
     */
    PartialList<Event> search(Query query);

    /**
     * Checks whether the specified event has already been raised either for the associated session or profile depending on the specified {@code session} parameter.
     *
     * @param event   the event we want to check
     * @param session {@code true} if we want to check if the specified event has already been raised for the associated session, {@code false} if we want to check
     *                whether the event has already been raised for the associated profile
     * @return {@code true} if the event has already been raised, {@code false} otherwise
     */
    boolean hasEventAlreadyBeenRaised(Event event, boolean session);
}
