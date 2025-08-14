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

import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.EventsCollectorRequest;
import org.apache.unomi.rest.exception.InvalidRequestException;
import org.apache.unomi.rest.models.EventCollectorResponse;
import org.apache.unomi.rest.service.RestServiceUtils;
import org.apache.unomi.utils.EventsRequestContext;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Date;
import java.util.List;

@Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
@Consumes(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(allowAllOrigins = true, allowCredentials = true)
@Path("/")
@Component(service = EventsCollectorEndpoint.class, property = "osgi.jaxrs.resource=true")
public class EventsCollectorEndpoint {

    public static final String SYSTEMSCOPE = "systemscope";
    @Reference
    private RestServiceUtils restServiceUtils;

    @Context
    HttpServletRequest request;
    @Context
    HttpServletResponse response;

    @OPTIONS
    @Path("/eventcollector")
    public Response options() {
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    @GET
    @Path("/eventcollector")
    public EventCollectorResponse collectAsGet(@QueryParam("payload") EventsCollectorRequest eventsCollectorRequest,
            @QueryParam("timestamp") Long timestampAsString) {
        return doEvent(eventsCollectorRequest, timestampAsString);
    }

    @POST
    @Path("/eventcollector")
    public EventCollectorResponse collectAsPost(EventsCollectorRequest eventsCollectorRequest,
            @QueryParam("timestamp") Long timestampAsLong) {
        return doEvent(eventsCollectorRequest, timestampAsLong);
    }

    private EventCollectorResponse doEvent(EventsCollectorRequest eventsCollectorRequest, Long timestampAsLong) {
        if (eventsCollectorRequest == null) {
            throw new InvalidRequestException("events collector cannot be empty", "Invalid received data");
        }
        Date timestamp = new Date();
        if (timestampAsLong != null) {
            timestamp = new Date(timestampAsLong);
        }

        String sessionId = eventsCollectorRequest.getSessionId();
        if (sessionId == null) {
            sessionId = request.getParameter("sessionId");
        }

        String profileId = eventsCollectorRequest.getProfileId();
        // Get the first available scope that is not equal to systemscope otherwise systemscope will be used
        String scope = SYSTEMSCOPE;
        List<Event> events = eventsCollectorRequest.getEvents();
        for (Event event : events) {
            if (StringUtils.isNotBlank(event.getEventType())) {
                if (StringUtils.isNotBlank(event.getScope()) && !event.getScope().equals(SYSTEMSCOPE)) {
                    scope = event.getScope();
                    break;
                } else if (event.getSource() != null && StringUtils.isNotBlank(event.getSource().getScope()) && !event.getSource()
                        .getScope().equals(SYSTEMSCOPE)) {
                    scope = event.getSource().getScope();
                    break;
                }
            }
        }

        // build public context, profile + session creation/anonymous etc ...
        EventsRequestContext eventsRequestContext = restServiceUtils.initEventsRequest(scope, sessionId, profileId, null, false, false,
                request, response, timestamp);

        // process events
        eventsRequestContext = restServiceUtils.performEventsRequest(eventsCollectorRequest.getEvents(), eventsRequestContext);

        // finalize request
        restServiceUtils.finalizeEventsRequest(eventsRequestContext, true);

        return new EventCollectorResponse(eventsRequestContext.getChanges());
    }
}
