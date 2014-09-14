package org.oasis_open.wemi.context.server;

import org.oasis_open.wemi.context.server.api.Persona;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.util.Enumeration;
import java.util.Map;

/**
 * Request wrapper to emulate a request from a Persona ("virtual user")
 */
public class PersonaRequestWrapper extends HttpServletRequestWrapper {

    Persona persona;

    public PersonaRequestWrapper(HttpServletRequest request, Persona persona) {
        super(request);
    }

    @Override
    public Cookie[] getCookies() {
        return super.getCookies();
    }

    @Override
    public long getDateHeader(String name) {
        return super.getDateHeader(name);
    }

    @Override
    public String getHeader(String name) {
        if (persona.getRequestHeaders().containsKey(name)) {
            return (String) persona.getRequestHeaders().get(name);
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return super.getHeaderNames();
    }

    @Override
    public int getIntHeader(String name) {
        return super.getIntHeader(name);
    }

    @Override
    public String getParameter(String name) {
        return super.getParameter(name);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return super.getParameterMap();
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return super.getParameterNames();
    }

    @Override
    public String[] getParameterValues(String name) {
        return super.getParameterValues(name);
    }

    @Override
    public String getRemoteAddr() {
        if (persona.getRequestHeaders().containsKey("Remote-Address")) {
            return (String) persona.getRequestHeaders().get("Remote-Address");
        }
        return super.getRemoteAddr();
    }

    @Override
    public String getRemoteHost() {
        if (persona.getRequestHeaders().containsKey("Remote-Host")) {
            return (String) persona.getRequestHeaders().get("Remote-Host");
        }
        return super.getRemoteHost();
    }
}
