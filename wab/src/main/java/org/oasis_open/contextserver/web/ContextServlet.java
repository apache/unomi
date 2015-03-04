package org.oasis_open.contextserver.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.oasis_open.contextserver.api.ContextRequest;
import org.oasis_open.contextserver.api.ContextResponse;
import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.Persona;
import org.oasis_open.contextserver.api.PersonaWithSessions;
import org.oasis_open.contextserver.api.Profile;
import org.oasis_open.contextserver.api.Session;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.services.EventService;
import org.oasis_open.contextserver.api.services.ProfileService;
import org.oasis_open.contextserver.api.services.RulesService;
import org.oasis_open.contextserver.api.services.SegmentService;
import org.oasis_open.contextserver.persistence.spi.CustomObjectMapper;
import org.ops4j.pax.cdi.api.OsgiService;

/**
 * A servlet filter to serve a context-specific Javascript containing the current request context object.
 */
@WebServlet(urlPatterns = {"/context.js", "/context.json"})
public class ContextServlet extends HttpServlet {

    public static final String BASE_SCRIPT_LOCATION = "/WEB-INF/javascript/base.js";
    public static final String IMPERSONATE_BASE_SCRIPT_LOCATION = "/WEB-INF/javascript/impersonateBase.js";

    @Inject
    @OsgiService
    private ProfileService profileService;

    @Inject
    @OsgiService
    private SegmentService segmentService;

    @Inject
    @OsgiService
    private RulesService rulesService;

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
        String profileId;

        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        String httpMethod = httpServletRequest.getMethod();
//        log(HttpUtils.dumpRequestInfo(httpServletRequest));

        // set up CORS headers as soon as possible so that errors are not misconstrued on the client for CORS errors
        HttpUtils.setupCORSHeaders(httpServletRequest, response);

        if ("options".equals(httpMethod.toLowerCase())) {
            response.flushBuffer();
            return;
        }

        Profile profile = null;

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
            if ("currentProfile".equals(personaId) || personaId.equals(cookieProfileId)) {
                profile = null;
                HttpUtils.clearCookie(response, personaIdCookieName);
            } else {
                PersonaWithSessions personaWithSessions = profileService.loadPersonaWithSessions(personaId);
                profile = personaWithSessions.getPersona();
                session = personaWithSessions.getLastSession();
                if (profile != null) {
                    HttpUtils.sendProfileCookie(profile, response, profileIdCookieName, personaIdCookieName);
                }
            }
        } else if (cookiePersonaId != null) {
            PersonaWithSessions personaWithSessions = profileService.loadPersonaWithSessions(cookiePersonaId);
            profile = personaWithSessions.getPersona();
            session = personaWithSessions.getLastSession();
        }

        String sessionId = request.getParameter("sessionId");

        boolean profileCreated = false;

        ContextRequest contextRequest = null;
        String scope = null;
        String stringPayload = HttpUtils.getPayload(httpServletRequest);
        if (stringPayload != null) {
            ObjectMapper mapper = CustomObjectMapper.getObjectMapper();
            JsonFactory factory = mapper.getFactory();
            try {
                contextRequest = mapper.readValue(factory.createParser(stringPayload), ContextRequest.class);
            } catch (Exception e) {
                log("Cannot read payload " +stringPayload,e);
                return;
            }
            scope = contextRequest.getSource().getScope();
        }

        if (profile == null) {
            if (sessionId != null) {
                session = profileService.loadSession(sessionId, timestamp);
                if (session != null) {
                    profileId = session.getProfileId();
                    profile = profileService.load(profileId);
                    profile = checkMergedProfile(response, profile, session);
                }
            }
            if (profile == null) {
                // profile not stored in session
                if (cookieProfileId == null) {
                    // no profileId cookie was found, we generate a new one and create the profile in the profile service
                    profile = createNewProfile(null, response, timestamp);
                    profileCreated = true;
                } else {
                    profile = profileService.load(cookieProfileId);
                    if (profile == null) {
                        // this can happen if we have an old cookie but have reset the server,
                        // or if we merged the profiles and somehow this cookie didn't get updated.
                        profile = createNewProfile(null, response, timestamp);
                        profileCreated = true;
                        HttpUtils.sendProfileCookie(profile, response, profileIdCookieName, personaIdCookieName);
                    } else {
                        profile = checkMergedProfile(response, profile, session);
                    }
                }

            } else if (cookieProfileId == null || !cookieProfileId.equals(profile.getItemId())) {
                // profile if stored in session but not in cookie
                HttpUtils.sendProfileCookie(profile, response, profileIdCookieName, personaIdCookieName);
            }
            // associate profile with session
            if (sessionId != null && session == null) {
                session = new Session(sessionId, profile, timestamp, scope);
                profileService.saveSession(session);
                Event event = new Event("sessionCreated", session, profile, scope, null, session, timestamp);

                event.getAttributes().put(Event.HTTP_REQUEST_ATTRIBUTE, request);
                event.getAttributes().put(Event.HTTP_RESPONSE_ATTRIBUTE, response);
                log("Received event " + event.getEventType() + " for profile=" + profile.getItemId() + " session=" + session.getItemId() + " target=" + event.getTarget() + " timestamp=" + timestamp);
                eventService.send(event);
            }
        }

        if (profileCreated) {
            Event profileUpdated = new Event("profileUpdated", session, profile, scope, null, profile, timestamp);
            profileUpdated.setPersistent(false);
            profileUpdated.getAttributes().put(Event.HTTP_REQUEST_ATTRIBUTE, request);
            profileUpdated.getAttributes().put(Event.HTTP_RESPONSE_ATTRIBUTE, response);

            log("Received event " + profileUpdated.getEventType() + " for profile=" + profile.getItemId() + " session=" + session.getItemId() + " target=" + profileUpdated.getTarget() + " timestamp=" + timestamp);
            eventService.send(profileUpdated);
        }

        ContextResponse data = new ContextResponse();

        if(contextRequest != null){
            handleRequest(contextRequest, profile, session, data, request, response, timestamp);
        }

        String extension = httpServletRequest.getRequestURI().substring(httpServletRequest.getRequestURI().lastIndexOf(".") + 1);
        boolean noScript = "json".equals(extension);
        String contextAsJSONString = CustomObjectMapper.getObjectMapper().writeValueAsString(data);
        Writer responseWriter;
        if(noScript){
            response.setCharacterEncoding("UTF-8");
            responseWriter = response.getWriter();
            response.setContentType("application/json");
            IOUtils.write(contextAsJSONString, responseWriter);
        }else {
            responseWriter = response.getWriter();
            responseWriter.append("window.digitalData = window.digitalData || {};\n")
                    .append("var wemi = ")
                    .append(contextAsJSONString)
                    .append(";\n");

            // now we copy the base script source code
            InputStream baseScriptStream = getServletContext().getResourceAsStream(profile instanceof Persona ? IMPERSONATE_BASE_SCRIPT_LOCATION : BASE_SCRIPT_LOCATION);
            IOUtils.copy(baseScriptStream, responseWriter);
        }

        responseWriter.flush();
    }

    private Profile checkMergedProfile(ServletResponse response, Profile profile, Session session) {
        String profileId;
        if (profile.getMergedWith() != null) {
            profileId = profile.getMergedWith();
            Profile profileToDelete = profile;
            profile = profileService.load(profileId);
            if (profile != null) {
                log("Session profile was merged with profile " + profileId + ", replacing profile in session");
                if (session != null) {
                    session.setProfile(profile);
                    profileService.saveSession(session);
                    profileService.delete(profileToDelete.getItemId(), false);
                }
                HttpUtils.sendProfileCookie(profile, response, profileIdCookieName, personaIdCookieName);
            } else {
                log("Couldn't find merged profile" + profileId + ", falling back to profile " + profileToDelete.getItemId());
                profile = profileToDelete;
                profile.setMergedWith(null);
                profileService.save(profile);
            }
        }
        return profile;
    }

    private void handleRequest(ContextRequest contextRequest, Profile profile, Session session, ContextResponse data, ServletRequest request, ServletResponse response, Date timestamp)
            throws IOException {
        // execute provided events if any
        Event viewEvent = null;
        if(contextRequest.getEvents() != null) {
            for (Event event : contextRequest.getEvents()){
                if(event.getEventType() != null) {
                    Event eventToSend;
                    if(event.getProperties() != null){
                        eventToSend = new Event(event.getEventType(), session, profile, contextRequest.getSource().getScope(), event.getSource(), event.getTarget(), event.getProperties(), timestamp);
                    } else {
                        eventToSend = new Event(event.getEventType(), session, profile, contextRequest.getSource().getScope(), event.getSource(), event.getTarget(), timestamp);
                    }
                    event.getAttributes().put(Event.HTTP_REQUEST_ATTRIBUTE, request);
                    event.getAttributes().put(Event.HTTP_RESPONSE_ATTRIBUTE, response);
                    log("Received event " + event.getEventType() + " for profile=" + profile.getItemId() + " session=" + session.getItemId() + " target=" + event.getTarget() + " timestamp=" + timestamp);
                    eventService.send(eventToSend);
                    if("view".equals(eventToSend.getEventType())){
                        viewEvent = eventToSend;
                    }
                }
            }
        }

        data.setProfileId(profile.getItemId());

        if (contextRequest.isRequireSegments()) {
            data.setProfileSegments(profile.getSegments());
        }

        if (contextRequest.getRequiredProfileProperties() != null) {
            Map<String, Object> profileProperties = new HashMap<String, Object>(profile.getProperties());
            if (!contextRequest.getRequiredProfileProperties().contains("*")) {
                profileProperties.keySet().retainAll(contextRequest.getRequiredProfileProperties());
            }
            data.setProfileProperties(profileProperties);
        }
        if (session != null) {
            data.setSessionId(session.getItemId());
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
                    result &= profileService.matchCondition(condition, profile, session);
                }
                data.getFilteringResults().put(filteredContent.getFilterid(), result);
            }
        }

        data.setTrackedConditions(rulesService.getTrackedConditions(contextRequest.getSource()));
    }

    private Profile createNewProfile(String existingProfileId, ServletResponse response, Date timestamp) {
        Profile profile;
        String profileId = existingProfileId;
        if (profileId == null) {
            profileId = UUID.randomUUID().toString();
        }
        profile = new Profile(profileId);
        profile.setProperty("firstVisit", timestamp);
        profileService.save(profile);
        HttpUtils.sendProfileCookie(profile, response, profileIdCookieName, personaIdCookieName);
        return profile;
    }


    public void destroy() {
    }
}
