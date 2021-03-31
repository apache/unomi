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
package org.apache.unomi.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Optional;

/**
 * Http wrapper that force the content type to be "application/json"
 */
class HttpServletRequestForwardWrapper extends HttpServletRequestWrapper {

    private static final Logger logger = LoggerFactory.getLogger(HttpServletRequestForwardWrapper.class.getName());
    private static final String JSON_CONTENT_TYPE = "application/json";

    public HttpServletRequestForwardWrapper(HttpServletRequest request) {
        super(request);
    }

    /**
     * Forward servlet request to jax-rs endpoints. For a given path, forward to /cxs + path.
     *
     * @param request    initial request
     * @param response   initial response
     * @param forwardURI custom foward URI if the one from the request is not valid
     */
    public static void forward(HttpServletRequest request, HttpServletResponse response, String forwardURI) throws ServletException, IOException {
        try {
            HttpServletRequest requestWrapper = new HttpServletRequestForwardWrapper(request);
            requestWrapper.getServletContext()
                    .getContext("/cxs")
                    .getRequestDispatcher("/cxs" + Optional.ofNullable(forwardURI).orElse(request.getRequestURI()))
                    .forward(requestWrapper, response);
        } catch (Throwable t) { // Here in order to return generic message instead of the whole stack trace in case of not caught exception
            logger.error("HttpServletRequestForwardWrapper failed to forward the request", t);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    @Override
    public String getHeader(String name) {
        if ("Content-Type".equals(name) || "content-type".equals(name)) {
            return JSON_CONTENT_TYPE;
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if ("Content-Type".equals(name) || "content-type".equals(name)) {
            return Collections.enumeration(Collections.singleton(JSON_CONTENT_TYPE));
        }
        return super.getHeaders(name);
    }

    @Override
    public String getContentType() {
        return JSON_CONTENT_TYPE;
    }

    @Override
    public String getCharacterEncoding() {
        return "UTF-8";
    }
}
