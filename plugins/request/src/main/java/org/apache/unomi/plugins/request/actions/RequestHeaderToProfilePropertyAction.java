/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.unomi.plugins.request.actions;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;

import javax.servlet.http.HttpServletRequest;

/**
 * Copies a request header value to a profile property
 *
 * TODO add support for multi-valued parameters or storing values as a list
 */
public class RequestHeaderToProfilePropertyAction implements ActionExecutor {
    public int execute(Action action, Event event) {
        boolean changed = false;
        HttpServletRequest httpServletRequest = (HttpServletRequest) event.getAttributes().get(Event.HTTP_REQUEST_ATTRIBUTE);
        if (httpServletRequest == null) {
            return EventService.NO_CHANGE;
        }
        String requestHeaderName = (String) action.getParameterValues().get("requestHeaderName");
        String profilePropertyName = (String) action.getParameterValues().get("profilePropertyName");
        String sessionPropertyName = (String) action.getParameterValues().get("sessionPropertyName");
        String requestHeaderValue = httpServletRequest.getHeader(requestHeaderName);
        if (requestHeaderValue != null) {
            if (profilePropertyName != null) {
                if (event.getProfile().getProperty(profilePropertyName) == null || !event.getProfile().getProperty(profilePropertyName).equals(requestHeaderValue)) {
                    event.getProfile().setProperty(profilePropertyName, requestHeaderValue);
                    return EventService.PROFILE_UPDATED;
                }
            } else if (sessionPropertyName != null) {
                if (event.getSession().getProperty(sessionPropertyName) == null || !event.getSession().getProperty(sessionPropertyName).equals(requestHeaderValue)) {
                    event.getSession().setProperty(sessionPropertyName, requestHeaderValue);
                    return EventService.SESSION_UPDATED;
                }
            }
        }
        return EventService.NO_CHANGE;
    }
}
