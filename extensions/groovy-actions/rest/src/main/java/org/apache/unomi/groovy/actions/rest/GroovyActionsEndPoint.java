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

package org.apache.unomi.groovy.actions.rest;

import org.apache.commons.io.IOUtils;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.Multipart;
import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.groovy.actions.services.GroovyActionsService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;

@WebService
@Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
@CrossOriginResourceSharing(allowAllOrigins = true, allowCredentials = true)
@Path("/groovyActions")
@Component(service = GroovyActionsEndPoint.class, property = "osgi.jaxrs.resource=true")
public class GroovyActionsEndPoint {

    private static final Logger logger = LoggerFactory.getLogger(GroovyActionsEndPoint.class.getName());

    @Reference
    private GroovyActionsService groovyActionsService;

    public GroovyActionsEndPoint() {
        logger.info("Initializing groovy actions service endpoint...");
    }

    @WebMethod(exclude = true)
    public void setGroovyActionsService(GroovyActionsService groovyActionsService) {
        this.groovyActionsService = groovyActionsService;
    }

    /**
     * Save a groovy action file and create an actionType entry to allow to call this action
     *
     * @param file the file to save
     * @return Response of the API call
     */
    @POST
    @Path("/")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response save(@Multipart(value = "file") Attachment file) {
        try {
            groovyActionsService
                    .save(file.getContentDisposition().getFilename().replace(".groovy", ""), IOUtils.toString(file.getDataHandler().getInputStream()));
        } catch (IOException e) {
            logger.error("Error while processing groovy file", e);
            return Response.serverError().build();
        }
        return Response.ok().build();
    }

    /**
     * Deletes the rule identified by the specified identifier.
     *
     * @param actionId the identifier of the groovy action that we want to delete
     */
    @DELETE
    @Path("/{actionId}")
    public void remove(@PathParam("actionId") String actionId) {
        groovyActionsService.remove(actionId);
    }
}
