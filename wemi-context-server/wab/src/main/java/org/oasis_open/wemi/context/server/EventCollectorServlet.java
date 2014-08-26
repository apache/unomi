package org.oasis_open.wemi.context.server;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.Session;
import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.services.EventService;
import org.oasis_open.wemi.context.server.api.services.SegmentService;
import org.oasis_open.wemi.context.server.api.services.UserService;
import org.ops4j.pax.cdi.api.OsgiService;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Created by loom on 10.06.14.
 */
@WebServlet(urlPatterns={"/eventcollector/*"})
public class EventCollectorServlet extends HttpServlet {

    private static final List<String> reservedParameters = Arrays.asList("timestamp", "sessionId");

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
//        HttpUtils.dumpBasicRequestInfo(request);
        HttpUtils.setupCORSHeaders(request, response);
    }

    private void doEvent(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Date timestamp = new Date();
        if (request.getParameter("timestamp") != null) {
            timestamp.setTime(Long.parseLong(request.getParameter("timestamp")));
        }

        HttpUtils.dumpBasicRequestInfo(request);

        HttpUtils.setupCORSHeaders(request, response);

        String sessionId = request.getParameter("sessionId");
        if (sessionId == null) {
            return;
        }

        Session session = userService.loadSession(sessionId);
        if (session == null) {
            return;
        }

        String userId = session.getUserId();
        if (userId == null) {
            return;
        }

        User user = userService.load(userId);
        if (user == null) {
            return;
        }

        String eventType = request.getPathInfo();
        if (eventType.startsWith("/")) {
            eventType = eventType.substring(1);
        }
        if (eventType.endsWith("/")) {
            eventType = eventType.substring(eventType.length()-1);
        }
        if (eventType.contains("/")) {
            eventType = eventType.substring(eventType.lastIndexOf("/"));
        }

        Event event = new Event(eventType, session, user, timestamp);

        Enumeration<String> parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String parameterName = parameterNames.nextElement();
            if (!reservedParameters.contains(parameterName)) {
                event.setProperty(parameterName, request.getParameter(parameterName));
            }
        }

        event.getAttributes().put("http_request", request);
        event.getAttributes().put("http_response", response);

        boolean changed = eventService.save(event);

        PrintWriter responseWriter = response.getWriter();

        if (changed) {
            responseWriter.append("{\"updated\":true, \"digitalData\":");
            responseWriter.append(HttpUtils.getJSONDigitalData(user, segmentService, HttpUtils.getBaseRequestURL(request)));
            responseWriter.append("}");
        } else {
            responseWriter.append("{\"updated\":false}");
        }
        responseWriter.flush();
    }


}
