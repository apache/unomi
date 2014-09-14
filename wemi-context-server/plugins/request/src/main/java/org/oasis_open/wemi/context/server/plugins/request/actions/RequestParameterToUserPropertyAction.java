package org.oasis_open.wemi.context.server.plugins.request.actions;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.Persona;
import org.oasis_open.wemi.context.server.api.actions.Action;
import org.oasis_open.wemi.context.server.api.actions.ActionExecutor;

import javax.servlet.http.HttpServletRequest;

/**
 * Copies a request parameter to a user property
 * @todo add support for multi-valued parameters or storing values as a list
 */
public class RequestParameterToUserPropertyAction implements ActionExecutor {
    public boolean execute(Action action, Event event) {
        boolean changed = false;
        if (event.getUser() instanceof Persona) {
            return false;
        }

        HttpServletRequest httpServletRequest = (HttpServletRequest) event.getAttributes().get("http_request");
        if (httpServletRequest == null) {
            return false;
        }
        String requestParameterName = (String) action.getParameterValues().get("requestParameterName");
        String userPropertyName = (String) action.getParameterValues().get("userPropertyName");
        String sessionPropertyName = (String) action.getParameterValues().get("sessionPropertyName");
        String requestParameterValue = httpServletRequest.getParameter(requestParameterName);
        if (requestParameterValue != null) {
            if (userPropertyName != null) {
                if (event.getUser().getProperty(userPropertyName) == null || !event.getUser().getProperty(userPropertyName).equals(requestParameterValue)) {
                    event.getUser().setProperty(userPropertyName, requestParameterValue);
                    changed = true;
                }
            } else if (sessionPropertyName != null) {
                if (event.getSession().getProperty(sessionPropertyName) == null || !event.getSession().getProperty(sessionPropertyName).equals(requestParameterValue)) {
                    event.getSession().setProperty(sessionPropertyName, requestParameterValue);
                    changed = true;
                }
            }
        }
        return changed;
    }
}
