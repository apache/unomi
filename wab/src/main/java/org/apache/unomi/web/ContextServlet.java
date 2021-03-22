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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.*;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.*;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.security.Principal;
import java.util.*;

/**
 * A servlet filter to serve a context-specific Javascript containing the current request context object.
 */
public class ContextServlet extends HttpServlet {
    private static final long serialVersionUID = 2928875830103325238L;
    private static final Logger logger = LoggerFactory.getLogger(ContextServlet.class.getName());

    private static final int MAX_COOKIE_AGE_IN_SECONDS = 60 * 60 * 24 * 365; // 1 year

    private String profileIdCookieName = "context-profile-id";
    private String profileIdCookieDomain;
    private int profileIdCookieMaxAgeInSeconds = MAX_COOKIE_AGE_IN_SECONDS;

    private ProfileService profileService;
    private EventService eventService;
    private RulesService rulesService;
    private PrivacyService privacyService;
    private PersonalizationService personalizationService;
    private ConfigSharingService configSharingService;

    private boolean sanitizeConditions = Boolean.parseBoolean(System.getProperty("org.apache.unomi.security.personalization.sanitizeConditions", "true"));

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        logger.info("ContextServlet initialized.");
    }

    @Override
    public void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        RequestDispatcher dispatcher = getServletContext().getContext("/cxs")
                .getRequestDispatcher("/cxs/context.json");
        try {

            dispatcher.forward(request, response);
            return;
        } catch (ServletException e) {
            logger.error(e.getMessage());
        }
    }
}
