package org.oasis_open.wemi.context.server.plugins.request.consequences;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;
import org.oasis_open.wemi.context.server.api.consequences.ConsequenceExecutor;
import org.ops4j.pax.cdi.api.OsgiServiceProvider;
import org.ops4j.pax.cdi.api.Properties;
import org.ops4j.pax.cdi.api.Property;

import javax.enterprise.context.ApplicationScoped;
import javax.servlet.http.HttpServletRequest;

/**
 * Copies a request header value to a user property
 * @todo add support for multi-valued parameters or storing values as a list
 */
@ApplicationScoped
@OsgiServiceProvider
@Properties({
    @Property(name = "consequenceExecutorId", value = "requestHeaderToUserProperty")
})
public class RequestHeaderToUserPropertyConsequence implements ConsequenceExecutor {
    public boolean execute(Consequence consequence, User user, Object context) {
        boolean changed = false;
        Event event = (Event) context;
        HttpServletRequest httpServletRequest = (HttpServletRequest) event.getAttributes().get("http_request");
        if (httpServletRequest == null) {
            return false;
        }
        String requestHeaderName = (String) consequence.getParameterValues().get("requestHeaderName");
        String userPropertyName = (String) consequence.getParameterValues().get("userPropertyName");
        String requestHeaderValue = httpServletRequest.getHeader(requestHeaderName);
        if (requestHeaderValue != null) {
            if (user.getProperty(userPropertyName) == null || !user.getProperty(userPropertyName).equals(requestHeaderValue)) {
                user.setProperty(userPropertyName, requestHeaderValue);
                changed = true;
            }
        }
        return changed;
    }
}
