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

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.util.*;

/**
 * A servlet filter to serve a context-specific Javascript containing the current request context object.
 */
public class ClientServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(ClientServlet.class.getName());
    private static final long serialVersionUID = 2928875960103325238L;
    private ProfileService profileService;

    private String profileIdCookieName = "context-profile-id";
    private String profileIdCookieDomain;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        logger.info("ClientServlet initialized.");
    }

    @Override
    public void destroy() {
        super.destroy();
        logger.info("Client servlet shutdown.");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String operation = req.getParameter("op");
        switch (operation){
            case "downloadMyProfile" :
                donwloadCurrentProfile(req, resp);
                break;
            default:
                return;

        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    }

    public void donwloadCurrentProfile(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String cookieProfileId = null;
        Cookie[] cookies = request.getCookies();
        for (Cookie cookie : cookies) {
            if (profileIdCookieName.equals(cookie.getName())) {
                cookieProfileId = cookie.getValue();
            }
        }
        if(cookieProfileId != null) {
            Profile currentProfile = profileService.load(cookieProfileId);
            if(currentProfile != null) {
                response.setContentType("text/csv");
                response.setHeader("Content-Disposition", "attachment; filename=\""+cookieProfileId+".csv\"");
                try {
                    OutputStream outputStream = response.getOutputStream();
                    String outputResult = "";

                    for (String prop : currentProfile.getProperties().keySet()) {
                        outputResult += prop + "," + currentProfile.getProperties().get(prop) + "\n";
                    }

                    outputStream.write(outputResult.getBytes());
                    outputStream.flush();
                    outputStream.close();
                }
                catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public void setProfileIdCookieDomain(String profileIdCookieDomain) {
        this.profileIdCookieDomain = profileIdCookieDomain;
    }

    public void setProfileIdCookieName(String profileIdCookieName) {
        this.profileIdCookieName = profileIdCookieName;
    }

}
