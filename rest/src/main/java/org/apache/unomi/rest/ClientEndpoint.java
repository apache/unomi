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

package org.apache.unomi.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.opencsv.CSVWriter;
import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.services.ConfigSharingService;
import org.apache.unomi.api.services.ProfileService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jws.WebService;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * A servlet filter to serve a context-specific Javascript containing the current request context object.
 */
@WebService
@Produces
@Consumes(MediaType.TEXT_PLAIN)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
@Path("/")
@Component(service = ClientEndpoint.class, property = "osgi.jaxrs.resource=true")
public class ClientEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(ClientEndpoint.class.getName());
    private static final long serialVersionUID = 2928875960103325238L;

    @Reference
    private ProfileService profileService;

    private String profileIdCookieName = "context-profile-id";
    private String allowedProfileDownloadFormats;

    private final String FILE_NAME_WO_EXT = "my-profile";

    @Context
    HttpServletRequest request;
    @Context
    HttpServletResponse response;
    @Reference
    private ConfigSharingService configSharingService;

    @GET
    @Path("/client/{operation}/{param}")
    public Response getClient(@PathParam("operation") String operation, @PathParam("param") String param) throws JsonProcessingException {
        switch (operation) {
            case "myprofile":
                if (allowedProfileDownloadFormats.contains(param)) {
                    return donwloadCurrentProfile(param);
                } else {
                    throw new InternalServerErrorException(String.format("%s is not an allowed param", param));
                }
        }
        throw new NotFoundException();
    }

    private Response donwloadCurrentProfile(String downloadFileType) throws JsonProcessingException {
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
                        return prepareYamlFileToDownload(currentProfile, false);
                    case "json":
                        return prepareJsonFileToDownload(currentProfile);
                    case "csv":
                        return prepareCsvFileToDownload(currentProfile, request.getParameter("vertical") != null);
                    case "text":
                        return prepareYamlFileToDownload(currentProfile, true);
                }

            }
        }
        throw new NotFoundException();
    }

    private Response prepareCsvFileToDownload(Profile currentProfile, boolean vertical) {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + FILE_NAME_WO_EXT + ".csv\"");
        StringWriter writer = new StringWriter();
        //using custom delimiter and quote character
        CSVWriter csvWriter = new CSVWriter(writer);
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
        Response.ResponseBuilder responseBuilder = Response.ok(writer.toString());
        return responseBuilder.build();
    }

    private Response prepareJsonFileToDownload(Profile currentProfile) throws JsonProcessingException {
        response.setContentType("text/json");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + FILE_NAME_WO_EXT + ".json\"");
        ObjectMapper mapper = new ObjectMapper();
        String jsonContent = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(currentProfile.getProperties());
        return Response.ok(jsonContent).build();
    }

    private Response prepareYamlFileToDownload(Profile currentProfile, boolean asTextFile) throws JsonProcessingException {
        response.setContentType("text/" + (asTextFile ? "plain" : "yaml"));
        response.setHeader("Content-Disposition", "attachment; filename=\"" + FILE_NAME_WO_EXT + (asTextFile ? ".txt" : ".yml") + "\"");
        YAMLFactory yf = new YAMLFactory();
        ObjectMapper mapper = new ObjectMapper(yf);
        String yamlContent = mapper.writeValueAsString(currentProfile.getProperties());
        return Response.ok(yamlContent).build();
    }

    @Activate
    public void init() {
        // TODO DMF-4436 read values from the configuration file: org.apache.unomi.web.cfg
        profileIdCookieName = "context-profile-id";
        allowedProfileDownloadFormats = "csv,yaml,json,text";
    }
}
