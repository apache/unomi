package org.oasis_open.wemi.context.server;

import org.apache.commons.io.IOUtils;
import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.services.UserService;
import org.ops4j.pax.cdi.api.OsgiService;

import javax.inject.Inject;
import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.List;

/**
 * A servlet filter to serve a context-specific Javascript containing the current request context object.
 */
@WebFilter(urlPatterns={"/context.js"})
public class ScriptFilter implements Filter {

    public static final String BASE_SCRIPT_LOCATION = "/WEB-INF/javascript/base.js";

    FilterConfig filterConfig;

    @Inject
    @OsgiService
    UserService userService;

    public void init(FilterConfig filterConfig) throws ServletException {
        this.filterConfig = filterConfig;
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        // first we copy the base script source code
        InputStream baseScriptStream = filterConfig.getServletContext().getResourceAsStream(BASE_SCRIPT_LOCATION);
        Writer responseWriter = response.getWriter();
        IOUtils.copy(baseScriptStream, responseWriter);
        // now we must retrieve the context for the current visitor, and build a Javascript object to attach to the
        // script output.
        // @todo implement back-end call to load or create new visitor context
        String visitorID = null;
        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpServletRequest = (HttpServletRequest) request;
            Cookie[] cookies = httpServletRequest.getCookies();
            for (Cookie cookie : cookies) {
                if ("visitorID".equals(cookie.getName())) {
                    visitorID = cookie.getValue();
                }
            }
        }
        List<User> users = userService.findUsersByPropertyValue("visitorID", visitorID);

        // we re-use the object naming convention from http://www.w3.org/community/custexpdata/, specifically in
        // http://www.w3.org/2013/12/ceddl-201312.pdf
        responseWriter.append("digitalData = {");
        responseWriter.append("  user: [ { ");
        responseWriter.append("    profiles: [ { ");
        responseWriter.append("      profileInfo: {");
        responseWriter.append("        profileId: \"visitor-550e8400-e29b-41d4-a716-446655440000\", ");
        responseWriter.append("        userName: \"johndoe\", ");
        responseWriter.append("        email: \"a@b.c\",");
        responseWriter.append("        returningStatus: \"\", ");
        responseWriter.append("        type: \"main\", ");
        responseWriter.append("                   }");
        responseWriter.append("              } ]");
        responseWriter.append("        } ]");
        responseWriter.append("};");
        responseWriter.flush();
    }

    public void destroy() {
    }
}
