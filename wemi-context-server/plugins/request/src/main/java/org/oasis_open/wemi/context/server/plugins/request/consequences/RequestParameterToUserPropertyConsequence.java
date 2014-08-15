package org.oasis_open.wemi.context.server.plugins.request.consequences;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;
import org.oasis_open.wemi.context.server.api.consequences.ConsequenceExecutor;

import javax.servlet.http.HttpServletRequest;

/**
 * Copies a request parameter to a user property
 * @todo add support for multi-valued parameters or storing values as a list
 */
public class RequestParameterToUserPropertyConsequence implements ConsequenceExecutor {
    public boolean execute(Consequence consequence, Event event) {
        boolean changed = false;
        HttpServletRequest httpServletRequest = (HttpServletRequest) event.getAttributes().get("http_request");
        if (httpServletRequest == null) {
            return false;
        }
        String requestParameterName = (String) consequence.getParameterValues().get("requestParameterName");
        String userPropertyName = (String) consequence.getParameterValues().get("userPropertyName");
        String requestParameterValue = httpServletRequest.getParameter(requestParameterName);
        if (requestParameterValue != null) {
            if (event.getUser().getProperty(userPropertyName) == null || !event.getUser().getProperty(userPropertyName).equals(requestParameterValue)) {
                event.getUser().setProperty(userPropertyName, requestParameterValue);
                changed = true;
            }
        }
        return changed;
    }
}
