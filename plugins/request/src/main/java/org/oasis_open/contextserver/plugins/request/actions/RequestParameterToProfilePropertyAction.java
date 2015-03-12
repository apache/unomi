package org.oasis_open.contextserver.plugins.request.actions;

/*
 * #%L
 * Context Server Plugin - Provides request reading actions
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.actions.ActionExecutor;

import javax.servlet.http.HttpServletRequest;

/**
 * Copies a request parameter to a profile property
 *
 * @todo add support for multi-valued parameters or storing values as a list
 */
public class RequestParameterToProfilePropertyAction implements ActionExecutor {
    public boolean execute(Action action, Event event) {
        boolean changed = false;
        HttpServletRequest httpServletRequest = (HttpServletRequest) event.getAttributes().get(Event.HTTP_REQUEST_ATTRIBUTE);
        if (httpServletRequest == null) {
            return false;
        }
        String requestParameterName = (String) action.getParameterValues().get("requestParameterName");
        String profilePropertyName = (String) action.getParameterValues().get("profilePropertyName");
        String sessionPropertyName = (String) action.getParameterValues().get("sessionPropertyName");
        String requestParameterValue = httpServletRequest.getParameter(requestParameterName);
        if (requestParameterValue != null) {
            if (profilePropertyName != null) {
                if (event.getProfile().getProperty(profilePropertyName) == null || !event.getProfile().getProperty(profilePropertyName).equals(requestParameterValue)) {
                    event.getProfile().setProperty(profilePropertyName, requestParameterValue);
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
