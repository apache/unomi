package org.oasis_open.contextserver.plugins.request.actions;

import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.actions.ActionExecutor;

import javax.servlet.http.HttpServletRequest;

/**
 * Copies a request header value to a profile property
 *
 * @todo add support for multi-valued parameters or storing values as a list
 */
public class RequestHeaderToProfilePropertyAction implements ActionExecutor {
    public boolean execute(Action action, Event event) {
        boolean changed = false;
        HttpServletRequest httpServletRequest = (HttpServletRequest) event.getAttributes().get(Event.HTTP_REQUEST_ATTRIBUTE);
        if (httpServletRequest == null) {
            return false;
        }
        String requestHeaderName = (String) action.getParameterValues().get("requestHeaderName");
        String profilePropertyName = (String) action.getParameterValues().get("profilePropertyName");
        String sessionPropertyName = (String) action.getParameterValues().get("sessionPropertyName");
        String requestHeaderValue = httpServletRequest.getHeader(requestHeaderName);
        if (requestHeaderValue != null) {
            if (profilePropertyName != null) {
                if (event.getProfile().getProperty(profilePropertyName) == null || !event.getProfile().getProperty(profilePropertyName).equals(requestHeaderValue)) {
                    event.getProfile().setProperty(profilePropertyName, requestHeaderValue);
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
