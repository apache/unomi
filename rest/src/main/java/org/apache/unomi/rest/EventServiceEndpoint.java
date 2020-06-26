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
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.EventService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * A JAX-RS endpoint to access information about the context server's events.
 */
@WebService
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

    @WebMethod(exclude = true)
    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * Allows to search events using a query.
     *
     * @param query the query object to use to search for events. You can specify offset and limits along with a
     *              condition tree.
     * @return a partial list containing the events that match the query.
     */
    @POST
    @Path("/search")
    public PartialList<Event> searchEvents(Query query) {
        return eventService.search(query);
    }

    /**
     * Allows to retrieve event by id.
     *
     * @param id the event id.
     * @return {@link Event} with the provided id.
     */
    @GET
    @Path("/{id}")
    public Event getEvents(@PathParam("id") final String id) {
        return eventService.getEvent(id);
    }

}
