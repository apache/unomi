package org.oasis_open.wemi.context.server.plugins.request.actions;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.actions.Action;
import org.oasis_open.wemi.context.server.api.actions.ActionExecutor;

import javax.servlet.http.HttpServletRequest;

/**
 * Copies a request header value to a user property
 * @todo add support for multi-valued parameters or storing values as a list
 */
public class RequestHeaderToUserPropertyAction implements ActionExecutor {
    public boolean execute(Action action, Event event) {
        boolean changed = false;
        HttpServletRequest httpServletRequest = (HttpServletRequest) event.getAttributes().get("http_request");
        if (httpServletRequest == null) {
            return false;
        }
        String requestHeaderName = (String) action.getParameterValues().get("requestHeaderName");
        String userPropertyName = (String) action.getParameterValues().get("userPropertyName");
        String sessionPropertyName = (String) action.getParameterValues().get("sessionPropertyName");
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
