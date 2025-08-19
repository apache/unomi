/*
 * Copyright (C) 2002-2025 Jahia Solutions Group SA. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.unomi.rest.endpoints;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * @author Jerome Blanchard
 */
@Produces(MediaType.TEXT_PLAIN + ";charset=UTF-8")
@Path("/test")
@CrossOriginResourceSharing(allowAllOrigins = true, allowCredentials = true)
@Component(service = TestEndPoint.class, property = "osgi.jaxrs.resource=true")
public class TestEndPoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestEndPoint.class.getName());

    public TestEndPoint() {
        LOGGER.info("TestEndPoint initialized.");
    }

    @GET
    @Path("/ping")
    public String ping() {
        LOGGER.info("Ping received.");
        return "pong";
    }
}
