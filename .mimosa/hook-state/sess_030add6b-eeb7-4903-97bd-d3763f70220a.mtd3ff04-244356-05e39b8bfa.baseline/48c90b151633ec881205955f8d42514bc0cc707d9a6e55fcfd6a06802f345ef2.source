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
package org.apache.unomi.rest.endpoints;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.EventService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Set;

/**
 * JAX-RS endpoint for searching, loading, and deleting stored events.
 */
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
@Path("/events")
@Component(service=EventServiceEndpoint.class,property = "osgi.jaxrs.resource=true")
public class EventServiceEndpoint {

    @Reference
    private EventService eventService;

    /**
     * Sets the event service.
     *
     * @param eventService the event service
     */
    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * Searches events using the given query.
     * The query may include a condition tree ({@code type} + {@code parameterValues}), offset, and limit.
     *
     * @param query the search query, including optional condition tree, offset, and limit
     * @return a paged list of matching events
     * @api.status 200 org.apache.unomi.api.PartialList Events page (list items are Event).
     * @api.status 400 empty Invalid or unreadable query body.
     * @api.example {"list":[{"itemId":"evt-1","itemType":"event","eventType":"view","sessionId":"session-1","profileId":"profile-1","scope":"mysite","timeStamp":"2024-06-15T10:30:00.000Z","properties":{"pageUrl":"https://example.com/"},"persistent":true}],"offset":0,"pageSize":1,"totalSize":1,"totalSizeRelation":"EQUAL"}
     */
    @POST
    @Path("/search")
    public PartialList<Event> searchEvents(Query query) {
        return eventService.search(query);
    }

    /**
     * Returns the event with the given ID.
     *
     * @param id the event identifier
     * @return the event, or HTTP 404 when it does not exist
     * @api.status 200 org.apache.unomi.api.Event Event found.
     * @api.status 404 empty Event not found.
     * @api.example {"itemId":"evt-1","itemType":"event","eventType":"view","sessionId":"session-1","profileId":"profile-1","scope":"mysite","timeStamp":"2024-06-15T10:30:00.000Z","properties":{"pageUrl":"https://example.com/"},"persistent":true}
     */
    @GET
    @Path("/{id}")
    public Response getEvents(@PathParam("id") final String id) {
        Event event = eventService.getEvent(id);
        if (event == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(event).build();
    }

    /**
     * Deletes an event by id.
     *
     * @param id the identifier for the event to delete
     * @api.status 204 empty Event deleted.
     * @api.example {"itemId":"evt-1","itemType":"event","eventType":"view","sessionId":"session-1","profileId":"profile-42","scope":"mysite","timeStamp":"2024-06-15T10:30:00.000Z","properties":{"pageUrl":"https://example.com/"},"persistent":true}
     */
    @DELETE
    @Path("/{id}")
    public void deleteEvent(@PathParam("id") final String id) {
        eventService.deleteEvent(id);
    }

    /**
     * Returns event type identifiers known to the server.
     *
     * @return the processed event type identifiers
     * @api.status 200 array empty Event type ids known to the server (may be empty).
     * @api.example ["view","login","purchase"]
     */
    @GET
    @Path("types")
    public Set<String> getEventTypeNames() {
        return eventService.getEventTypeIds();
    }

}
