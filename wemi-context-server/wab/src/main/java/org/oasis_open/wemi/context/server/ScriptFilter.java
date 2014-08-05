package org.oasis_open.wemi.context.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.io.IOUtils;
import org.oasis_open.wemi.context.server.api.Session;
import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.services.EventListenerService;
import org.oasis_open.wemi.context.server.api.services.EventService;
import org.oasis_open.wemi.context.server.api.services.SegmentService;
import org.oasis_open.wemi.context.server.api.services.UserService;
import org.ops4j.pax.cdi.api.OsgiService;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * A servlet filter to serve a context-specific Javascript containing the current request context object.
 */
@WebFilter(urlPatterns = {"/context.js"})
public class ScriptFilter implements Filter {

    public static final String BASE_SCRIPT_LOCATION = "/WEB-INF/javascript/base.js";
    private static final int MAX_COOKIE_AGE_IN_SECONDS = 60 * 60 * 24 * 365 * 10; // 10-years

    FilterConfig filterConfig;

    @Inject
    @OsgiService
    UserService userService;

    @Inject
    @OsgiService
    SegmentService segmentService;

    @Inject
    @OsgiService
    private EventService eventService;

    @Inject
    @OsgiService(dynamic = true)
    private Instance<EventListenerService> eventListeners;

    public void init(FilterConfig filterConfig) throws ServletException {
        this.filterConfig = filterConfig;
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        // first we must retrieve the context for the current visitor, and build a Javascript object to attach to the
        // script output.
        String visitorID = null;

        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        String httpMethod = httpServletRequest.getMethod();
        HttpUtils.dumpBasicRequestInfo(httpServletRequest);
        HttpUtils.dumpRequestHeaders(httpServletRequest);


        User user = null;

        String cookieProfileId = null;
        Cookie[] cookies = httpServletRequest.getCookies();
        // HttpUtils.dumpRequestCookies(cookies);
        for (Cookie cookie : cookies) {
            if ("wemi-profileID".equals(cookie.getName())) {
                cookieProfileId = cookie.getValue();
                break;
            }
        }

        final String userSessionId = request.getParameter("userSession");
        if (userSessionId != null) {
            Session userSession = userService.loadSession(userSessionId);
            if (userSession != null) {
                visitorID = userSession.getUserId();
                user = userService.load(visitorID);
            }
        }
        if (user == null) {
            // user not stored in session
            if (cookieProfileId == null) {
                // no visitorID cookie was found, we generate a new one and create the user in the user service
                user = createNewUser(null, response);
            } else {
                user = userService.load(cookieProfileId);
                if (user == null) {
                    // this can happen if we have an old cookie but have reset the server.
                    user = createNewUser(cookieProfileId, response);
                }
            }
            // associate user with session
            if (userSessionId != null) {
                Session userSession = new Session(userSessionId,user.getItemId());
                userService.saveSession(userSession);
            }
        } else if (cookieProfileId == null || !cookieProfileId.equals(user.getItemId())) {
            // user if stored in session but not in cookie
            sendCookie(user, response);
        }

        if ("post".equals(httpMethod.toLowerCase())) {
            // we have received an update on the digitalData structure, we must store it.
            httpServletRequest = (HttpServletRequest) request;
            String contentType = httpServletRequest.getContentType();
            if (contentType != null && contentType.contains("application/json")) {
                InputStream jsonInputStream = httpServletRequest.getInputStream();
                ObjectMapper mapper = new ObjectMapper(); // create once, reuse
                JsonNode rootNode = mapper.readTree(jsonInputStream);
                if (rootNode != null) {
                    ObjectNode profileInfo = (ObjectNode) rootNode.get("user").get(0).get("profiles").get(0).get("profileInfo");
                    if (profileInfo != null && user == null && profileInfo.get("profileId") != null) {
                        user = userService.load(profileInfo.get("profileId").asText());
                    }
                    if (user != null) {
                        Iterator<String> fieldNameIter = profileInfo.fieldNames();
                        boolean modifiedProperties = false;
                        while (fieldNameIter.hasNext()) {
                            String fieldName = fieldNameIter.next();
                            JsonNode field = profileInfo.get(fieldName);
                            if (user.hasProperty(fieldName) && user.getProperty(fieldName).equals(field.asText())) {

                            } else {
                                if (fieldName != null && fieldName.length() > 0) {
                                    user.setProperty(fieldName, field.asText());
                                    modifiedProperties = true;
                                } else {
                                    // empty field name, won't set the property.
                                }
                            }
                        }
                        if (modifiedProperties) {
                            userService.save(user);
                        }
                    } else {
                        // couldn't resolve user !
                    }
                }
            }
        }

        HttpUtils.setupCORSHeaders(httpServletRequest, response);

        Writer responseWriter = response.getWriter();
        if ("post".equals(httpMethod.toLowerCase()) || "get".equals(httpMethod.toLowerCase())) {

            String baseRequestURL = HttpUtils.getBaseRequestURL(httpServletRequest);

            // we re-use the object naming convention from http://www.w3.org/community/custexpdata/, specifically in
            // http://www.w3.org/2013/12/ceddl-201312.pdf
            if (user != null) {
                if ("get".equals(httpMethod.toLowerCase())) {
                    responseWriter.append("window.digitalData = window.digitalData || {};\n");
                    responseWriter.append("var wemiDigitalData = \n");
                    responseWriter.append(HttpUtils.getJSONDigitalData(user, segmentService, baseRequestURL));
                    responseWriter.append("; \n");
                } else {
                    responseWriter.append(HttpUtils.getJSONDigitalData(user, segmentService, baseRequestURL));
                }
            }

            if ("get".equals(httpMethod.toLowerCase())) {
                // now we copy the base script source code
                if (userSessionId != null) {
                    responseWriter.append("var wemiUserSession = '" + userSessionId + "';\n");
                }
                InputStream baseScriptStream = filterConfig.getServletContext().getResourceAsStream(BASE_SCRIPT_LOCATION);
                IOUtils.copy(baseScriptStream, responseWriter);
            }

        } else {
            responseWriter.append("OK");
        }
        responseWriter.flush();

    }

    private User createNewUser(String existingVisitorID, ServletResponse response) {
        User user;
        String visitorID = existingVisitorID;
        if (visitorID == null) {
            visitorID = UUID.randomUUID().toString();
        }
        user = new User(visitorID);
        userService.save(user);
        sendCookie(user, response);
        return user;
    }

    private void sendCookie( User user, ServletResponse response) {
        if (response instanceof HttpServletResponse) {
            HttpServletResponse httpServletResponse = (HttpServletResponse) response;
            Cookie visitorIdCookie = new Cookie("wemi-profileID", user.getItemId());
            visitorIdCookie.setPath("/");
            visitorIdCookie.setMaxAge(MAX_COOKIE_AGE_IN_SECONDS);
            httpServletResponse.addCookie(visitorIdCookie);
        }
    }

    public void destroy() {
    }
}
