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
import org.apache.cxf.validation.BeanValidationProvider;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.services.ConfigSharingService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.rest.validation.HibernateValidationProviderResolver;
import org.apache.unomi.rest.validation.JAXRSBeanValidationInInterceptorOverride;
import org.apache.unomi.rest.validation.cookies.CookieUtils;
import org.apache.unomi.rest.validation.cookies.CookieWrapper;
import org.hibernate.validator.HibernateValidator;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jws.WebService;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A servlet filter to serve a context-specific Javascript containing the current request context object.
 */
@WebService
@CrossOriginResourceSharing(allowAllOrigins = true, allowCredentials = true)
@Path("/")
@Component(service = ClientEndpoint.class, property = "osgi.jaxrs.resource=true")
public class ClientEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(ClientEndpoint.class.getName());
    private static final String CONTENT_DISPOSITION_HEADER_KEY = "Content-Disposition";

    private static final String FILE_NAME_WO_EXT = "my-profile";

    private static String getContentDispostionHeader(String extension) {
        return String.format("attachment; filename=\"%s.%s\"", FILE_NAME_WO_EXT, extension);
    }

    @Reference
    private ProfileService profileService;
    @Reference
    private ConfigSharingService configSharingService;

    @Context
    HttpServletRequest request;
    @Context
    HttpServletResponse response;

    @OPTIONS
    @Path("/client/{operation}/{param}")
    public Response options() {
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    @GET
    @Path("/client/{operation}/{param}")
    public Response getClient(@PathParam("operation") String operation, @PathParam("param") String param) throws JsonProcessingException {
        CookieUtils.validate(request.getCookies());
        if ("myprofile".equals(operation)) {
            if (((String) configSharingService.getProperty("allowedProfileDownloadFormats")).contains(param)) {
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
                if (configSharingService.getProperty("profileIdCookieName").equals(cookie.getName())) {
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
                    default:
                        throw new NotFoundException();
                }
            }
        }
        throw new NotFoundException();
    }

    private Response prepareCsvFileToDownload(Profile currentProfile, boolean vertical) {
        response.setContentType("text/csv");

        response.setHeader(CONTENT_DISPOSITION_HEADER_KEY, getContentDispostionHeader("csv"));
        StringWriter writer = new StringWriter();
        //using custom delimiter and quote character
        CSVWriter csvWriter = new CSVWriter(writer);
        if (vertical) {
            csvWriter.writeNext(new String[] { "name", "value" });
            for (Map.Entry<String, Object> entry : currentProfile.getProperties().entrySet()) {
                csvWriter.writeNext(new String[] { entry.getKey(), entry.getValue().toString().trim().replace("\n", "") });
            }
        } else {
            Set<String> keySet = currentProfile.getProperties().keySet();
            List<String> values = new ArrayList<>();
            for (Object value : currentProfile.getProperties().values()) {
                values.add(value.toString().trim().replace("\n", ""));
            }
            csvWriter.writeNext(keySet.toArray(new String[0]));
            csvWriter.writeNext(values.toArray(new String[0]));
        }
        Response.ResponseBuilder responseBuilder = Response.ok(writer.toString());
        return responseBuilder.build();
    }

    private Response prepareJsonFileToDownload(Profile currentProfile) throws JsonProcessingException {
        response.setContentType("text/json");
        response.setHeader(CONTENT_DISPOSITION_HEADER_KEY, getContentDispostionHeader("json"));

        ObjectMapper mapper = new ObjectMapper();
        String jsonContent = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(currentProfile.getProperties());
        return Response.ok(jsonContent).build();
    }

    private Response prepareYamlFileToDownload(Profile currentProfile, boolean asTextFile) throws JsonProcessingException {
        response.setContentType("text/" + (asTextFile ? "plain" : "yaml"));
        response.setHeader(CONTENT_DISPOSITION_HEADER_KEY, getContentDispostionHeader((asTextFile ? "txt" : "yml")));
        YAMLFactory yf = new YAMLFactory();
        ObjectMapper mapper = new ObjectMapper(yf);
        String yamlContent = mapper.writeValueAsString(currentProfile.getProperties());
        return Response.ok(yamlContent).build();
    }
}
