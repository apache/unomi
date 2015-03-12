package org.oasis_open.contextserver.web;

/*
 * #%L
 * context-server-wab
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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.oasis_open.contextserver.api.*;
import org.oasis_open.contextserver.api.services.EventService;
import org.oasis_open.contextserver.api.services.SegmentService;
import org.oasis_open.contextserver.api.services.ProfileService;
import org.oasis_open.contextserver.persistence.spi.CustomObjectMapper;
import org.ops4j.pax.cdi.api.OsgiService;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;

@WebServlet(urlPatterns = {"/eventcollector"})
public class EventsCollectorServlet extends HttpServlet {

    private static final long serialVersionUID = 2008054804885122957L;

    @Inject
    @OsgiService
    private EventService eventService;

    @Inject
    @OsgiService
    private ProfileService profileService;

    @Inject
    @OsgiService
    private SegmentService segmentService;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        doEvent(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        doEvent(req, resp);
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//        log(HttpUtils.dumpRequestInfo(request));
        HttpUtils.setupCORSHeaders(request, response);
        response.flushBuffer();
    }

    private void doEvent(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Date timestamp = new Date();
        if (request.getParameter("timestamp") != null) {
            timestamp.setTime(Long.parseLong(request.getParameter("timestamp")));
        }

//        log(HttpUtils.dumpRequestInfo(request));

        HttpUtils.setupCORSHeaders(request, response);

        Profile profile = null;

        String sessionId = request.getParameter("sessionId");
        if (sessionId == null) {
            return;
        }

        Session session = profileService.loadSession(sessionId, timestamp);
        if (session == null) {
            return;
        }

        String profileId = session.getProfileId();
        if (profileId == null) {
            return;
        }

        profile = profileService.load(profileId);
        if (profile == null || profile instanceof Persona) {
            return;
        }

        String payload = HttpUtils.getPayload(request);
        if(payload == null){
            return;
        }

        ObjectMapper mapper = CustomObjectMapper.getObjectMapper();
        JsonFactory factory = mapper.getFactory();
        EventsCollectorRequest events = null;
        try {
            events = mapper.readValue(factory.createParser(payload), EventsCollectorRequest.class);
        } catch (Exception e) {
            log("Cannot read payload " + payload,e);
            return;
        }
        if (events == null || events.getEvents() == null) {
            return;
        }

        boolean changed = false;
        for (Event event : events.getEvents()){
            if(event.getEventType() != null){
                Event eventToSend;
                if(event.getProperties() != null){
                    eventToSend = new Event(event.getEventType(), session, profile, event.getScope(), event.getSource(), event.getTarget(), event.getProperties(), timestamp);
                } else {
                    eventToSend = new Event(event.getEventType(), session, profile, event.getScope(), event.getSource(), event.getTarget(), timestamp);
                }
                eventToSend.getAttributes().put(Event.HTTP_REQUEST_ATTRIBUTE, request);
                eventToSend.getAttributes().put(Event.HTTP_RESPONSE_ATTRIBUTE, response);
                log("Received event " + event.getEventType() + " for profile=" + profile.getItemId() + " session=" + session.getItemId() + " target=" + event.getTarget() + " timestamp=" + timestamp);
                boolean eventChanged = eventService.send(eventToSend);
                changed = changed || eventChanged;
            }
        }

        PrintWriter responseWriter = response.getWriter();
        responseWriter.append("{\"updated\":" + changed + "}");
        responseWriter.flush();
    }

}
