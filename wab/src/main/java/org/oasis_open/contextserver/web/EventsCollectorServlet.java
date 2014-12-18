package org.oasis_open.contextserver.web;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.Persona;
import org.oasis_open.contextserver.api.Session;
import org.oasis_open.contextserver.api.User;
import org.oasis_open.contextserver.api.services.EventService;
import org.oasis_open.contextserver.api.services.SegmentService;
import org.oasis_open.contextserver.api.services.UserService;
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
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Created by loom on 10.06.14.
 */
@WebServlet(urlPatterns = {"/eventcollector"})
public class EventsCollectorServlet extends HttpServlet {

    private static final List<String> reservedParameters = Arrays.asList("timestamp", "sessionId", "jsondata");

    @Inject
    @OsgiService
    private EventService eventService;

    @Inject
    @OsgiService
    private UserService userService;

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
        log(HttpUtils.dumpRequestInfo(request));
        HttpUtils.setupCORSHeaders(request, response);
        response.flushBuffer();
    }

    private void doEvent(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Date timestamp = new Date();
        if (request.getParameter("timestamp") != null) {
            timestamp.setTime(Long.parseLong(request.getParameter("timestamp")));
        }

        log(HttpUtils.dumpRequestInfo(request));

        HttpUtils.setupCORSHeaders(request, response);

        User user = null;

        String sessionId = request.getParameter("sessionId");
        if (sessionId == null) {
            return;
        }

        Session session = userService.loadSession(sessionId, timestamp);
        if (session == null) {
            return;
        }

        String userId = session.getUserId();
        if (userId == null) {
            return;
        }

        user = userService.load(userId);
        if (user == null || user instanceof Persona) {
            return;
        }

        String payload = HttpUtils.getPayload(request);
        if(payload == null){
            return;
        }

        ObjectMapper mapper = CustomObjectMapper.getObjectMapper();
        JsonFactory factory = mapper.getFactory();
        EventsCollectorRequest events = mapper.readValue(factory.createParser(payload), EventsCollectorRequest.class);
        if (events == null || events.getEvents() == null) {
            return;
        }

        boolean changed = false;
        for (Event event : events.getEvents()){
            if(event.getEventType() != null){
                Event eventToSend;
                if(event.getProperties() != null){
                    eventToSend = new Event(event.getEventType(), session, user, event.getSource(), event.getTarget(), event.getProperties(), timestamp);
                } else {
                    eventToSend = new Event(event.getEventType(), session, user, event.getSource(), event.getTarget(), timestamp);
                }
                event.getAttributes().put(Event.HTTP_REQUEST_ATTRIBUTE, request);
                event.getAttributes().put(Event.HTTP_RESPONSE_ATTRIBUTE, response);
                log("Received event " + event.getEventType() + " for user=" + user.getId() + " session=" + session.getId() + " target=" + event.getTarget() + " timestamp=" + timestamp);
                boolean eventChanged = eventService.send(eventToSend);
                changed = changed || eventChanged;
            }
        }

        PrintWriter responseWriter = response.getWriter();
        responseWriter.append("{\"updated\":" + changed + "}");
        responseWriter.flush();
    }

}
