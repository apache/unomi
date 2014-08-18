package org.oasis_open.wemi.context.server.plugins.request.consequences;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.Session;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;
import org.oasis_open.wemi.context.server.api.consequences.ConsequenceExecutor;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by toto on 18/08/14.
 */
public class SetRemoteHostInfoConsequence implements ConsequenceExecutor {
    @Override
    public boolean execute(Consequence consequence, Event event) {
        HttpServletRequest httpServletRequest = (HttpServletRequest) event.getAttributes().get("http_request");
        if (httpServletRequest == null) {
            return false;
        }
        Session session = event.getSession();
        if (session == null) {
            return false;
        }

        session.setProperty("remoteAddr", httpServletRequest.getRemoteAddr());
        session.setProperty("remoteHost", httpServletRequest.getRemoteHost());

        return true;
    }
}
