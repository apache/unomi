package org.oasis_open.wemi.context.server;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.services.UserService;
import org.ops4j.pax.cdi.api.OsgiService;

import javax.inject.Inject;
import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.List;
import java.util.UUID;

/**
 * A servlet filter to serve a context-specific Javascript containing the current request context object.
 */
@WebFilter(urlPatterns={"/context.js"})
public class ScriptFilter implements Filter {

    public static final String BASE_SCRIPT_LOCATION = "/WEB-INF/javascript/base.js";
    private static final int MAX_COOKIE_AGE_IN_SECONDS = 60*60*24*365*10; // 10-years

    FilterConfig filterConfig;

    @Inject
    @OsgiService
    UserService userService;

    public void init(FilterConfig filterConfig) throws ServletException {
        this.filterConfig = filterConfig;
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        // first we must retrieve the context for the current visitor, and build a Javascript object to attach to the
        // script output.
        String visitorID = null;
        String httpMethod = null;
        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpServletRequest = (HttpServletRequest) request;
            httpMethod = httpServletRequest.getMethod();
            Cookie[] cookies = httpServletRequest.getCookies();
            for (Cookie cookie : cookies) {
                if ("visitorID".equals(cookie.getName())) {
                    visitorID = cookie.getValue();
                }
            }
        }

        User user = null;
        if (visitorID == null) {
            // no visitorID cookie was found, we generate a new one and create the user in the user service
            user = new User(UUID.randomUUID().toString());
            userService.save(user);
            if (response instanceof HttpServletResponse) {
                HttpServletResponse httpServletResponse = (HttpServletResponse) response;
                Cookie visitorIdCookie = new Cookie("visitorID", user.getItemId());
                visitorIdCookie.setPath("/");
                visitorIdCookie.setMaxAge(MAX_COOKIE_AGE_IN_SECONDS);
                httpServletResponse.addCookie(visitorIdCookie);
            }
        } else {
            user = userService.load(visitorID);
            if (user == null) {
                // this should not happen.
            }
        }

        if (httpMethod != null && "post".equals(httpMethod.toLowerCase())) {
            // we have received an update on the digitalData structure, we must store it.
            if (request instanceof HttpServletRequest) {
                HttpServletRequest httpServletRequest = (HttpServletRequest) request;
                String contentType = httpServletRequest.getContentType();
                if (contentType != null && contentType.contains("application/json")) {
                    InputStream jsonInputStream = httpServletRequest.getInputStream();
                    ObjectMapper mapper = new ObjectMapper(); // create once, reuse
                    JsonNode rootNode = mapper.readTree(jsonInputStream);
                    if (rootNode != null) {
                        rootNode.toString();
                    }
                }
            }
        }

        Writer responseWriter = response.getWriter();
        if (user != null) {
            // we re-use the object naming convention from http://www.w3.org/community/custexpdata/, specifically in
            // http://www.w3.org/2013/12/ceddl-201312.pdf
            responseWriter.append("var digitalData = {");
            responseWriter.append("  user: [ { ");
            responseWriter.append("    profiles: [ { ");
            responseWriter.append("      profileInfo: {");
            responseWriter.append("        profileId: \"" + user.getItemId() + "\", ");
            responseWriter.append("        userName: \"" + user.getProperty("userName") + "\", ");
            responseWriter.append("        email: \"" + user.getProperty("email") + "\",");
            responseWriter.append("        returningStatus: \"\", ");
            responseWriter.append("        type: \"main\", ");
            responseWriter.append("                   }");
            responseWriter.append("              } ]");
            responseWriter.append("        } ]");
            responseWriter.append("};");
        }

        // now we copy the base script source code
        InputStream baseScriptStream = filterConfig.getServletContext().getResourceAsStream(BASE_SCRIPT_LOCATION);
        IOUtils.copy(baseScriptStream, responseWriter);

        responseWriter.flush();
    }

    public void destroy() {
    }
}
