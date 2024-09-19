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
package org.apache.unomi.rest.validation.request;

import org.apache.cxf.helpers.HttpHeaderHelper;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.unomi.api.services.ConfigSharingService;
import org.apache.unomi.rest.exception.InvalidRequestException;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * This interceptor is made to validate that the incoming post request on the public endpoints do not exceed the configured limit.
 */
public class RequestValidatorInterceptor extends AbstractPhaseInterceptor<Message> {

    private final ConfigSharingService configSharingService;

    private static final List<String> PROTECTED_URIS = new ArrayList<String>(){{
        add("/cxs/eventcollector");
        add("/cxs/context.js");
        add("/cxs/context.json");
    }};

    public RequestValidatorInterceptor(ConfigSharingService configSharingService) {
        super(Phase.RECEIVE);
        this.configSharingService = configSharingService;
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        int bytesLimit = (int) configSharingService.getProperty("publicPostRequestBytesLimit");
        if (bytesLimit > 0) {
            HttpServletRequest request = ((HttpServletRequest) message.get("HTTP.REQUEST"));
            if (request != null &&
                    request.getMethod().equalsIgnoreCase("POST") &&
                    PROTECTED_URIS.contains(request.getRequestURI())) {
                try {
                    int contentLength = Integer.parseInt(request.getHeader(HttpHeaderHelper.CONTENT_LENGTH));
                    if (contentLength > bytesLimit) {
                        throw new InvalidRequestException(
                                String.format("Incoming POST request blocked because exceeding maximum bytes size allowed on: %s (limit: %s, request size: %s)",
                                        request.getRequestURI(), bytesLimit, contentLength), "Request size exceed the limit");
                    }
                } catch (NumberFormatException nfe) {
                    // no content length, nothing will be deserialize by jackson, so nothing to check.
                }
            }
        }
    }
}
