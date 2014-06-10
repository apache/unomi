package org.oasis_open.wemi.context.server;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.services.EventListenerService;
import org.oasis_open.wemi.context.server.api.services.EventService;
import org.ops4j.pax.cdi.api.OsgiService;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.UUID;

/**
 * Created by loom on 10.06.14.
 */
@WebServlet(urlPatterns={"/eventcollector"})
public class EventCollectorServlet extends HttpServlet {

    @Inject
    @OsgiService
    private EventService eventService;

    @Inject
    @OsgiService(dynamic = true)
    private Instance<EventListenerService> eventListeners;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doGet(req, resp);

        doEvent(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPost(req, resp);

        doEvent(req, resp);
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpUtils.dumpBasicRequestInfo(req);
        super.doOptions(req, resp);

        HttpUtils.setupCORSHeaders(req, resp);
    }

    private void doEvent(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long eventTimeStamp = System.currentTimeMillis();
        HttpUtils.dumpBasicRequestInfo(req);
        HttpUtils.setupCORSHeaders(req, resp);

        String eventType = req.getServletPath();
        if (eventType.startsWith("/")) {
            eventType = eventType.substring(1);
        }
        if (eventType.endsWith("/")) {
            eventType = eventType.substring(eventType.length()-1);
        }
        if (eventType.contains("/")) {
            eventType = eventType.substring(eventType.lastIndexOf("/"));
        }

        Event event = new Event(UUID.randomUUID().toString());

        event.setProperty("eventTimeStamp", Long.toString(eventTimeStamp));
        event.setProperty("eventType", eventType);

        Enumeration<String> parameterNames = req.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String parameterName = parameterNames.nextElement();
            event.setProperty(parameterName, req.getParameter(parameterName));
        }

        eventService.save(event);
        for (EventListenerService eventListenerService : eventListeners) {
            eventListenerService.onEvent(event);
        }
    }


}
