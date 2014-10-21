package org.oasis_open.wemi.context.server;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.Persona;
import org.oasis_open.wemi.context.server.api.Session;
import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.services.EventService;
import org.oasis_open.wemi.context.server.api.services.SegmentService;
import org.oasis_open.wemi.context.server.api.services.UserService;
import org.ops4j.pax.cdi.api.OsgiService;

import javax.inject.Inject;
import javax.json.*;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.*;

/**
 * Created by loom on 10.06.14.
 */
@WebServlet(urlPatterns = {"/eventcollector/*"})
public class EventCollectorServlet extends HttpServlet {

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
        log(HttpUtils.dumpBasicRequestInfo(request));
        HttpUtils.setupCORSHeaders(request, response);
        response.flushBuffer();
    }

    private void doEvent(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Date timestamp = new Date();
        if (request.getParameter("timestamp") != null) {
            timestamp.setTime(Long.parseLong(request.getParameter("timestamp")));
        }

        log(HttpUtils.dumpBasicRequestInfo(request));
        log(HttpUtils.dumpRequestHeaders(request));

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

        String eventType = request.getPathInfo();
        if (eventType.startsWith("/")) {
            eventType = eventType.substring(1);
        }
        if (eventType.endsWith("/")) {
            eventType = eventType.substring(eventType.length() - 1);
        }
        if (eventType.contains("/")) {
            eventType = eventType.substring(eventType.lastIndexOf("/"));
        }

        Event event = new Event(eventType, session, user, timestamp);

        if (request.getParameter("jsondata") != null) {
            JsonReader reader = Json.createReader(new StringReader(request.getParameter("jsondata")));
            JsonObject data = (JsonObject) reader.read();
            addJsonProperties(event, data, "");
        }

        Enumeration<String> parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String parameterName = parameterNames.nextElement();
            if (!reservedParameters.contains(parameterName)) {
                event.setProperty(parameterName, request.getParameter(parameterName));
            }
        }

        event.getAttributes().put(Event.HTTP_REQUEST_ATTRIBUTE, request);
        event.getAttributes().put(Event.HTTP_RESPONSE_ATTRIBUTE, response);

        boolean changed = eventService.send(event);

        PrintWriter responseWriter = response.getWriter();

        responseWriter.append("{\"updated\":" + changed + "}");
        responseWriter.flush();
    }

    private void addJsonProperties(Event event, JsonObject data, String name) {
        for (Map.Entry<String, JsonValue> entry : data.entrySet()) {
            switch (entry.getValue().getValueType()) {
                case STRING:
                    event.setProperty(name + entry.getKey(), ((JsonString) entry.getValue()).getString());
                    break;
                case NUMBER:
                    event.setProperty(name + entry.getKey(), ((JsonNumber) entry.getValue()).intValueExact());
                    break;
                case OBJECT:
                    addJsonProperties(event, ((JsonObject) entry.getValue()), name + entry.getKey() + ".");
                    break;
            }
        }
    }


}
