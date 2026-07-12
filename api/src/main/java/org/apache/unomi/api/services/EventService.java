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
 * Publishes and retrieves {@link Event}s in the context server.
 * Client integrations send visitor actions here; rules and listeners
 * consume the same event stream.
 */
public interface EventService {

    /**
     * No change occurred following an event being handled.
     */
    int NO_CHANGE = 0;
    /**
     * An error occurred while processing the event.
     */
    int ERROR = 1;
    /**
     * The associated session was updated following an event being handled.
     */
    int SESSION_UPDATED = 2;
    /**
     * The associated profile was updated following an event being handled.
     */
    int PROFILE_UPDATED = 4;

    /**
     * Publishes an event, notifying listeners and persisting it when marked persistent.
     * Runs post-actions and updates the profile or session when rules require it.
     *
     * @param event event to propagate
     * @return bitmask of {@link #NO_CHANGE}, {@link #ERROR}, {@link #SESSION_UPDATED}, and {@link #PROFILE_UPDATED}
     */
    int send(Event event);

    /**
     * Checks whether the tenant may send the given event.
     * Restricted event types must be explicitly allowed per tenant.
     *
     * @param event event to test
     * @param tenantId tenant identifier
     * @param sourceIP client IP (not persisted for privacy)
     * @return {@code true} if the event is allowed
     */
    boolean isEventAllowedForTenant(Event event, String tenantId, String sourceIP);

    /**
     * Returns known event property definitions.
     *
     * @return available event properties
     * @deprecated use event types instead
     */
    List<EventProperty> getEventProperties();

    /**
     * Returns ids of all registered event types.
     *
     * @return known event type identifiers
     */
    Set<String> getEventTypeIds();

    /**
     * Searches events matching a condition, ordered by timestamp and paged.
     *
     * @param condition filter condition
     * @param offset zero-based index of the first result
     * @param size maximum results to return, or {@code -1} for all
     * @return matching events
     */
    PartialList<Event> searchEvents(Condition condition, int offset, int size);

    /**
     * Searches session events by type with optional full-text filtering, ordered and paged.
     *
     * @param sessionId session identifier
     * @param eventTypes event types to include (any match)
     * @param query optional full-text filter, or {@code null}
     * @param offset zero-based index of the first result
     * @param size maximum results to return, or {@code -1} for all
     * @param sortBy optional comma-separated property list with optional {@code :asc}/{@code :desc} suffixes
     * @return matching events
     */
    PartialList<Event> searchEvents(String sessionId, String[] eventTypes, String query, int offset, int size, String sortBy);

    /**
     * Searches events using a structured query.
     *
     * @param query query specifying which events to return
     * @return matching events
     */
    PartialList<Event> search(Query query);

    /**
     * Loads an event by id.
     *
     * @param id event identifier
     * @return matching event, or {@code null} if none exists
     */
    Event getEvent(final String id);

    /**
     * Checks whether an equivalent event was already raised for the session or profile.
     *
     * @param event event to check
     * @param session {@code true} to check the session history, {@code false} for the profile history
     * @return {@code true} if a matching event already exists
     */
    boolean hasEventAlreadyBeenRaised(Event event, boolean session);
    /**
     * Checks whether an event with the same item id was already raised.
     *
     * @param event event to check
     * @return {@code true} if a matching event already exists
     */
    boolean hasEventAlreadyBeenRaised(Event event);

    /**
     * Deletes all events belonging to the given profile.
     *
     * @param profileId profile whose events should be removed
     */
    void removeProfileEvents(String profileId);

    /**
     * Deletes a single event by id.
     *
     * @param eventIdentifier event identifier
     */
    void deleteEvent(String eventIdentifier);
}
