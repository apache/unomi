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
package org.apache.unomi.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.EventType;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.EventService;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Set;

/**
 * A JAX-RS endpoint to access information about the context server's events.
 */
@WebService
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
public class EventServiceEndpoint {

    private EventService eventService;

    @WebMethod(exclude = true)
    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * Allows to search events using a query.
     * @param query the query object to use to search for events. You can specify offset and limits along with a
     *              condition tree.
     * @return a partial list containing the events that match the query.
     */
    @POST
    @Path("/search")
    public PartialList<Event> searchEvents(Query query) {
        return eventService.searchEvents(query.getCondition(), query.getOffset(), query.getLimit());
    }

    /**
     * Retrieves the list of event types identifiers that the server has processed.
     * @return a Set of strings that contain event type identifiers.
     */
    @GET
    @Path("types")
    public Set<String> getEventTypeNames() {
        return eventService.getEventTypeIds();
    }

    /**
     * Returns the list of event properties
     * @return a List of EventProperty objects that make up the properties that the server has seen.
     */
    @GET
    @Path("types/{typeName}")
    public EventType getEventType(@PathParam("typeName") String typeName) {
        return eventService.getEventType(typeName);
    }

}
