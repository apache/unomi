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

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @deprecated this servlet is now deprecated, because it have been migrated to REST endpoint.
 * A servlet filter to serve a context-specific Javascript containing the current request context object.
 */
@Deprecated
@WebServlet(name = "ContextServlet", urlPatterns = {"/context.json", "/context.js"})
public class ContextServlet extends HttpServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContextServlet.class.getName());

    public ContextServlet() {
        super();
        LOGGER.info("ContextServlet created.");
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        LOGGER.info("ContextServlet initialized.");
    }

    @Override
    public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpServletRequestForwardWrapper.forward(request, response);
    }

    @Override
    public void destroy() {
        LOGGER.info("Context servlet shutdown.");
    }

}
