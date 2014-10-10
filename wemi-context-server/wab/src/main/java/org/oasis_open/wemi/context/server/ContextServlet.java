package org.oasis_open.wemi.context.server;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.io.IOUtils;
import org.oasis_open.wemi.context.server.api.*;
import org.oasis_open.wemi.context.server.api.services.EventService;
import org.oasis_open.wemi.context.server.api.services.SegmentService;
import org.oasis_open.wemi.context.server.api.services.UserService;
import org.ops4j.pax.cdi.api.OsgiService;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.*;

/**
 * A servlet filter to serve a context-specific Javascript containing the current request context object.
 */
@WebServlet(urlPatterns = {"/context.js"})
public class ContextServlet extends HttpServlet {

    public static final String BASE_SCRIPT_LOCATION = "/WEB-INF/javascript/base.js";
    public static final String IMPERSONATE_BASE_SCRIPT_LOCATION = "/WEB-INF/javascript/impersonateBase.js";
    public static final String SESSIONID_PERSONA_PREFIX = "persona-";
    public static final String SESSIONID_PERSONA_SEPARATOR = "___";

    @Inject
    @OsgiService
    UserService userService;

    @Inject
    @OsgiService
    SegmentService segmentService;

    @Inject
    @OsgiService
    private EventService eventService;

    @Override
    public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
        final Date timestamp = new Date();
        if (request.getParameter("timestamp") != null) {
            timestamp.setTime(Long.parseLong(request.getParameter("timestamp")));
        }
        // first we must retrieve the context for the current visitor, and build a Javascript object to attach to the
        // script output.
        String visitorId = null;

        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        String httpMethod = httpServletRequest.getMethod();
//        HttpUtils.dumpBasicRequestInfo(httpServletRequest);
//        HttpUtils.dumpRequestHeaders(httpServletRequest);

        if ("options".equals(httpMethod.toLowerCase())) {
            HttpUtils.setupCORSHeaders(httpServletRequest, response);
            return;
        }

        User user = null;

        String cookieProfileId = null;
        String cookiePersonaId = null;
        Cookie[] cookies = httpServletRequest.getCookies();
        // HttpUtils.dumpRequestCookies(cookies);
        for (Cookie cookie : cookies) {
            if ("wemi-profile-id".equals(cookie.getName())) {
                cookieProfileId = cookie.getValue();
            } else if ("wemi-persona-id".equals(cookie.getName())) {
                cookiePersonaId = cookie.getValue();
            }
        }

        Session session = null;

        String personaId = request.getParameter("personaId");
        if (personaId != null) {
            if ("currentUser".equals(personaId) || personaId.equals(cookieProfileId)) {
                user = null;
                HttpUtils.clearCookie(response, "wemi-persona-id");
            } else {
                PersonaWithSessions personaWithSessions = userService.loadPersonaWithSessions(personaId);
                user = personaWithSessions.getPersona();
                session = personaWithSessions.getLastSession();
                if (user != null) {
                    HttpUtils.sendCookie(user, response);
                }
            }
        } else if (cookiePersonaId != null) {
            PersonaWithSessions personaWithSessions = userService.loadPersonaWithSessions(cookiePersonaId);
            user = personaWithSessions.getPersona();
            session = personaWithSessions.getLastSession();
        }

        String sessionId = request.getParameter("sessionId");

        boolean userCreated = false;

        if (!(user instanceof Persona)) {
            if (sessionId != null) {
                session = userService.loadSession(sessionId, timestamp);
                if (session != null) {
                    visitorId = session.getUserId();
                    if (user == null) { // could be non null in case of persona
                        user = userService.load(visitorId);
                    }
                }
            }
            if (user == null) {
                // user not stored in session
                if (cookieProfileId == null) {
                    // no visitorId cookie was found, we generate a new one and create the user in the user service
                    user = createNewUser(null, response, timestamp);
                    userCreated = true;
                } else {
                    user = userService.load(cookieProfileId);
                    if (user == null) {
                        // this can happen if we have an old cookie but have reset the server.
                        user = createNewUser(cookieProfileId, response, timestamp);
                        userCreated = true;
                    }
                }

            } else if (cookieProfileId == null || !cookieProfileId.equals(user.getItemId())) {
                // user if stored in session but not in cookie
                HttpUtils.sendCookie(user, response);
            }
            // associate user with session
            if (sessionId != null && session == null) {
                session = new Session(sessionId, user, timestamp);
                userService.saveSession(session);
                Event event = new Event("sessionCreated", session, user, timestamp);

                event.getAttributes().put("http_request", request);
                event.getAttributes().put("http_response", response);
                eventService.save(event);
            }
        }

        if (userCreated) {
            Event userUpdated = new Event("userUpdated", session, user, timestamp);
            userUpdated.setPersistent(false);
            userUpdated.getAttributes().put("http_request", request);
            userUpdated.getAttributes().put("http_response", response);

            eventService.save(userUpdated);
        }

        HttpUtils.setupCORSHeaders(httpServletRequest, response);

        Writer responseWriter = response.getWriter();

        String baseRequestURL = HttpUtils.getBaseRequestURL(httpServletRequest);

        ObjectMapper mapper = new ObjectMapper();

        DigitalData data = new DigitalData();
        data.setUserId(user.getId());
        data.setSessionId(sessionId);
        data.setUserSegments(user.getSegments());
        data.setUserProperties(user.getProperties());
        data.setFilteringResults(new HashMap<String, Boolean>());
        if (session != null) {
            data.setSessionProperties(session.getProperties());
        }

        responseWriter.append("window.digitalData = window.digitalData || {};\n");
        responseWriter.append("var wemi = ");

        if ("post".equals(httpMethod.toLowerCase())) {
            StringBuilder buffer = new StringBuilder();
            String line;
            BufferedReader reader = request.getReader();
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
            }
            if (buffer.length() > 0) {
                JsonFactory factory = mapper.getFactory();
                ObjectNode payload = mapper.readTree(factory.createParser(buffer.toString()));
                ArrayNode filterNodes =  (ArrayNode) payload.get("filters");
                if (filterNodes != null) {
                    data.setFilteringResults(new HashMap<String, Boolean>());
                    for (JsonNode jsonNode : filterNodes) {

                        String id = jsonNode.get("filterid").asText();
                        ArrayNode filters = (ArrayNode) jsonNode.get("filters");
                        boolean result = true;
                        for (JsonNode filter : filters) {
                            JsonNode condition = filter.get("condition");
                            result &= userService.matchCondition(mapper.writeValueAsString(condition), user, session);
                        }
                        data.getFilteringResults().put(id, result);
                    }
                }
            }
        }
        responseWriter.append(mapper.writeValueAsString(data));

        // now we copy the base script source code
        InputStream baseScriptStream = getServletContext().getResourceAsStream(user instanceof Persona ? IMPERSONATE_BASE_SCRIPT_LOCATION : BASE_SCRIPT_LOCATION);

        IOUtils.copy(baseScriptStream, responseWriter);

        responseWriter.flush();

    }

    private User createNewUser(String existingVisitorId, ServletResponse response, Date timestamp) {
        User user;
        String visitorId = existingVisitorId;
        if (visitorId == null) {
            visitorId = UUID.randomUUID().toString();
        }
        user = new User(visitorId);
        user.setProperty("firstVisit", timestamp);
        userService.save(user);
        HttpUtils.sendCookie(user, response);
        return user;
    }


    public void destroy() {
    }
}
