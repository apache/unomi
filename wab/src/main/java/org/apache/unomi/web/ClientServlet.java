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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.opencsv.CSVWriter;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.services.ProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A servlet filter to serve a context-specific Javascript containing the current request context object.
 */
public class ClientServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(ClientServlet.class.getName());
    private static final long serialVersionUID = 2928875960103325238L;
    private ProfileService profileService;

    private String profileIdCookieName = "context-profile-id";
    private String allowedProfileDownloadFormats;

    private final String FILE_NAME_WO_EXT = "my-profile";

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
        String[] pathInfo = req.getPathInfo().substring(1).split("\\.");
        if (pathInfo != null && pathInfo.length > 0) {
            String operation = pathInfo[0];
            String param = pathInfo[1];
            switch (operation) {
                case "myprofile":
                    if (allowedProfileDownloadFormats.contains(param)) {
                        donwloadCurrentProfile(req, resp, param);
                    } else {
                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    }
                    break;
                default:
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);

            }
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }

    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    }

    public void donwloadCurrentProfile(HttpServletRequest request, HttpServletResponse response, String downloadFileType) throws ServletException, IOException {
        String cookieProfileId = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (profileIdCookieName.equals(cookie.getName())) {
                    cookieProfileId = cookie.getValue();
                }
            }
        }
        if (cookieProfileId != null) {
            Profile currentProfile = profileService.load(cookieProfileId);
            if (currentProfile != null) {
                switch (downloadFileType) {
                    case "yaml":
                        prepareYamlFileToDownload(response, currentProfile, false);
                        break;
                    case "json":
                        prepareJsonFileToDownload(response, currentProfile);
                        break;
                    case "csv":
                        prepareCsvFileToDownload(response, currentProfile, request.getParameter("vertical") != null);
                        break;
                    case "text":
                        prepareYamlFileToDownload(response, currentProfile, true);
                        break;
                    default:
                        return;

                }

            }
        }
    }

    private void prepareCsvFileToDownload(HttpServletResponse response, Profile currentProfile, boolean vertical) {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + FILE_NAME_WO_EXT + ".csv\"");
        try {
            StringWriter writer = new StringWriter();

            //using custom delimiter and quote character
            CSVWriter csvWriter = new CSVWriter(writer);
            OutputStream outputStream = response.getOutputStream();
            if (vertical) {
                csvWriter.writeNext(new String[]{"name", "value"});
                for (Map.Entry<String, Object> entry : currentProfile.getProperties().entrySet()) {
                    csvWriter.writeNext(new String[]{entry.getKey(), entry.getValue().toString().trim().replace("\n", "")});
                }
            } else {
                Set<String> keySet = currentProfile.getProperties().keySet();
                List<String> values = new ArrayList();
                for (Object value : currentProfile.getProperties().values()) {
                    values.add(value.toString().trim().replace("\n", ""));
                }
                csvWriter.writeNext(keySet.toArray(new String[keySet.size()]));
                csvWriter.writeNext(values.toArray(new String[values.size()]));
            }
            outputStream.write(writer.toString().getBytes());
            outputStream.flush();
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void prepareJsonFileToDownload(HttpServletResponse response, Profile currentProfile) {
        response.setContentType("text/json");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + FILE_NAME_WO_EXT + ".json\"");
        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonContent = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(currentProfile.getProperties());
            OutputStream outputStream = response.getOutputStream();
            outputStream.write(jsonContent.getBytes());
            outputStream.flush();
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void prepareYamlFileToDownload(HttpServletResponse response, Profile currentProfile, boolean asTextFile) {
        response.setContentType("text/" + (asTextFile ? "plain" : "yaml"));
        response.setHeader("Content-Disposition", "attachment; filename=\"" + FILE_NAME_WO_EXT + (asTextFile ? ".txt" : ".yml") + "\"");
        try {
            YAMLFactory yf = new YAMLFactory();
            ObjectMapper mapper = new ObjectMapper(yf);
            String yamlContent = mapper.writeValueAsString(currentProfile.getProperties());
            OutputStream outputStream = response.getOutputStream();
            outputStream.write(yamlContent.getBytes());
            outputStream.flush();
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public void setAllowedProfileDownloadFormats(String allowedProfileDownloadFormats) {
        this.allowedProfileDownloadFormats = allowedProfileDownloadFormats;
    }

    public void setProfileIdCookieName(String profileIdCookieName) {
        this.profileIdCookieName = profileIdCookieName;
    }

}
