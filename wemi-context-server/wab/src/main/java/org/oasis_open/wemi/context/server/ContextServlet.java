package org.oasis_open.wemi.context.server;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.oasis_open.wemi.context.server.api.*;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.services.EventService;
import org.oasis_open.wemi.context.server.api.services.SegmentService;
import org.oasis_open.wemi.context.server.api.services.UserService;
import org.oasis_open.wemi.context.server.persistence.spi.CustomObjectMapper;
import org.ops4j.pax.cdi.api.OsgiService;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
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
    @Inject
    @OsgiService
    UserService userService;
    @Inject
    @OsgiService
    SegmentService segmentService;
    private String profileIdCookieName = "context-profile-id";
    private String personaIdCookieName = "context-persona-id";
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
        log(HttpUtils.dumpRequestInfo(httpServletRequest));

        if ("options".equals(httpMethod.toLowerCase())) {
            HttpUtils.setupCORSHeaders(httpServletRequest, response);
            response.flushBuffer();
            return;
        }

        User user = null;

        String cookieProfileId = null;
        String cookiePersonaId = null;
        Cookie[] cookies = httpServletRequest.getCookies();
        for (Cookie cookie : cookies) {
            if (profileIdCookieName.equals(cookie.getName())) {
                cookieProfileId = cookie.getValue();
            } else if (personaIdCookieName.equals(cookie.getName())) {
                cookiePersonaId = cookie.getValue();
            }
        }

        Session session = null;

        String personaId = request.getParameter("personaId");
        if (personaId != null) {
            if ("currentUser".equals(personaId) || personaId.equals(cookieProfileId)) {
                user = null;
                HttpUtils.clearCookie(response, personaIdCookieName);
            } else {
                PersonaWithSessions personaWithSessions = userService.loadPersonaWithSessions(personaId);
                user = personaWithSessions.getPersona();
                session = personaWithSessions.getLastSession();
                if (user != null) {
                    HttpUtils.sendProfileCookie(user, response, profileIdCookieName, personaIdCookieName);
                }
            }
        } else if (cookiePersonaId != null) {
            PersonaWithSessions personaWithSessions = userService.loadPersonaWithSessions(cookiePersonaId);
            user = personaWithSessions.getPersona();
            session = personaWithSessions.getLastSession();
        }

        String sessionId = request.getParameter("sessionId");

        boolean userCreated = false;

        if (user == null) {
            if (sessionId != null) {
                session = userService.loadSession(sessionId, timestamp);
                if (session != null) {
                    visitorId = session.getUserId();
                    user = userService.load(visitorId);
                    user = checkMergedUser(response, user, session);
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
                        // this can happen if we have an old cookie but have reset the server,
                        // or if we merged the profiles and somehow this cookie didn't get updated.
                        user = createNewUser(null, response, timestamp);
                        userCreated = true;
                        HttpUtils.sendProfileCookie(user, response, profileIdCookieName, personaIdCookieName);
                    } else {
                        user = checkMergedUser(response, user, session);
                    }
                }

            } else if (cookieProfileId == null || !cookieProfileId.equals(user.getItemId())) {
                // user if stored in session but not in cookie
                HttpUtils.sendProfileCookie(user, response, profileIdCookieName, personaIdCookieName);
            }
            // associate user with session
            if (sessionId != null && session == null) {
                session = new Session(sessionId, user, timestamp);
                userService.saveSession(session);
                Event event = new Event("sessionCreated", session, user, timestamp);

                event.getAttributes().put(Event.HTTP_REQUEST_ATTRIBUTE, request);
                event.getAttributes().put(Event.HTTP_RESPONSE_ATTRIBUTE, response);
                eventService.send(event);
            }
        }

        if (userCreated) {
            Event userUpdated = new Event("userUpdated", session, user, timestamp);
            userUpdated.setPersistent(false);
            userUpdated.getAttributes().put(Event.HTTP_REQUEST_ATTRIBUTE, request);
            userUpdated.getAttributes().put(Event.HTTP_RESPONSE_ATTRIBUTE, response);

            eventService.send(userUpdated);
        }

        HttpUtils.setupCORSHeaders(httpServletRequest, response);

        Writer responseWriter = response.getWriter();

        ContextResponse data = new ContextResponse();

        responseWriter.append("window.digitalData = window.digitalData || {};\n");
        responseWriter.append("var wemi = ");

        String payload = HttpUtils.getPayload(httpServletRequest);
        if(payload != null){
            handleRequest(payload, user, session, data, request, response, timestamp);
        }
        responseWriter.append(CustomObjectMapper.getObjectMapper().writeValueAsString(data));

        // now we copy the base script source code
        InputStream baseScriptStream = getServletContext().getResourceAsStream(user instanceof Persona ? IMPERSONATE_BASE_SCRIPT_LOCATION : BASE_SCRIPT_LOCATION);

        IOUtils.copy(baseScriptStream, responseWriter);

        responseWriter.flush();

    }

    private User checkMergedUser(ServletResponse response, User user, Session session) {
        String visitorId;
        if (user.getProperty("mergedWith") != null) {
            visitorId = (String) user.getProperty("mergedWith");
            User userToDelete = user;
            user = userService.load(visitorId);
            if (user != null) {
                log("Session user was merged with user " + visitorId + ", replacing user in session");
                if (session != null) {
                    session.setUser(user);
                    userService.saveSession(session);
                    userService.delete(userToDelete.getId(), false);
                }
                HttpUtils.sendProfileCookie(user, response, profileIdCookieName, personaIdCookieName);
            } else {
                log("Couldn't find merged user" + visitorId + ", falling back to user " + userToDelete.getId());
                user = userToDelete;
                user.getProperties().remove("mergedWith");
                userService.save(user);
            }
        }
        return user;
    }

    private void handleRequest(String stringPayload, User user, Session session, ContextResponse data, ServletRequest request, ServletResponse response, Date timestamp)
            throws IOException {
        ObjectMapper mapper = CustomObjectMapper.getObjectMapper();
        JsonFactory factory = mapper.getFactory();
        ContextRequest contextRequest = mapper.readValue(factory.createParser(stringPayload), ContextRequest.class);

        // execute provided events if any
        if(contextRequest.getEvents() != null) {
            for (Event event : contextRequest.getEvents()){
                if(event.getEventType() != null) {
                    Event eventToSend;
                    if(event.getProperties() != null){
                        eventToSend = new Event(event.getEventType(), session, user, timestamp, event.getProperties());
                    } else {
                        eventToSend = new Event(event.getEventType(), session, user, timestamp);
                    }
                    event.getAttributes().put(Event.HTTP_REQUEST_ATTRIBUTE, request);
                    event.getAttributes().put(Event.HTTP_RESPONSE_ATTRIBUTE, response);
                    eventService.send(eventToSend);
                }
            }
        }

        data.setUserId(user.getId());

        if (contextRequest.isRequireSegments()) {
            data.setUserSegments(user.getSegments());
        }

        if (contextRequest.getRequiredUserProperties() != null) {
            Map<String, Object> userProperties = new HashMap<String, Object>(user.getProperties());
            if (!contextRequest.getRequiredUserProperties().contains("*")) {
                userProperties.keySet().retainAll(contextRequest.getRequiredUserProperties());
            }
            data.setUserProperties(userProperties);
        }
        if (session != null) {
            data.setSessionId(session.getId());
            if (contextRequest.getRequiredSessionProperties() != null) {
                Map<String, Object> sessionProperties = new HashMap<String, Object>(session.getProperties());
                if (!contextRequest.getRequiredSessionProperties().contains("*")) {
                    sessionProperties.keySet().retainAll(contextRequest.getRequiredSessionProperties());
                }
                data.setSessionProperties(sessionProperties);
            }
        }

        List<ContextRequest.FilteredContent> filterNodes = contextRequest.getFilters();
        if (filterNodes != null) {
            data.setFilteringResults(new HashMap<String, Boolean>());
            for (ContextRequest.FilteredContent filteredContent : filterNodes) {
                boolean result = true;
                for (ContextRequest.Filter filter : filteredContent.getFilters()) {
                    Condition condition = filter.getCondition();
                    result &= userService.matchCondition(condition, user, session);
                }
                data.getFilteringResults().put(filteredContent.getFilterid(), result);
            }
        }
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
        HttpUtils.sendProfileCookie(user, response, profileIdCookieName, personaIdCookieName);
        return user;
    }


    public void destroy() {
    }
}
