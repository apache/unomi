package org.oasis_open.wemi.context.server.plugins.request.consequences;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;
import org.oasis_open.wemi.context.server.api.consequences.ConsequenceExecutor;

import javax.servlet.http.HttpServletRequest;

/**
 * Copies a request header value to a user property
 * @todo add support for multi-valued parameters or storing values as a list
 */
public class RequestHeaderToUserPropertyConsequence implements ConsequenceExecutor {
    public boolean execute(Consequence consequence, Event event) {
        boolean changed = false;
        HttpServletRequest httpServletRequest = (HttpServletRequest) event.getAttributes().get("http_request");
        if (httpServletRequest == null) {
            return false;
        }
        String requestHeaderName = (String) consequence.getParameterValues().get("requestHeaderName");
        String userPropertyName = (String) consequence.getParameterValues().get("userPropertyName");
        String sessionPropertyName = (String) consequence.getParameterValues().get("sessionPropertyName");
        String requestHeaderValue = httpServletRequest.getHeader(requestHeaderName);
        if (requestHeaderValue != null) {
            if (userPropertyName != null) {
                if (event.getUser().getProperty(userPropertyName) == null || !event.getUser().getProperty(userPropertyName).equals(requestHeaderValue)) {
                    event.getUser().setProperty(userPropertyName, requestHeaderValue);
                    changed = true;
                }
            } else if (sessionPropertyName != null) {
                if (event.getSession().getProperty(sessionPropertyName) == null || !event.getSession().getProperty(sessionPropertyName).equals(requestHeaderValue)) {
                    event.getSession().setProperty(sessionPropertyName, requestHeaderValue);
                    changed = true;
                }
            }
        }
        return changed;
    }
}
