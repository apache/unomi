package org.oasis_open.contextserver.rest;

/*
 * #%L
 * context-server-rest
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.apache.cxf.jaxrs.ext.MessageContext;
import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.oasis_open.contextserver.api.ClusterNode;
import org.oasis_open.contextserver.api.services.ClusterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * A JAX-RS endpoint to access information about the context server's cluster.
 */
@WebService
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
public class ClusterServiceEndPoint {
    private static final Logger logger = LoggerFactory.getLogger(ClusterServiceEndPoint.class.getName());

    @Context
    private MessageContext messageContext;

    private ClusterService clusterService;

    public ClusterServiceEndPoint() {
        System.out.println("Initializing cluster service endpoint...");
    }

    @WebMethod(exclude = true)
    public void setClusterService(ClusterService clusterService) {
        this.clusterService = clusterService;
    }

    @WebMethod(exclude = true)
    public void setMessageContext(MessageContext messageContext) {
        this.messageContext = messageContext;
    }

    /**
     * Retrieves the list of available nodes for this context server instance.
     *
     * @return a list of {@link ClusterNode}
     */
    @GET
    @Path("/")
    public List<ClusterNode> getClusterNodes() {
        return clusterService.getClusterNodes();
    }

    /**
     * Removes all data before the specified date from the context server.
     *
     * @param date the Date before which all data needs to be removed
     */
    @GET
    @Path("/purge/{date}")
    public void purge(@PathParam("date") String date) {
        try {
            clusterService.purge(new SimpleDateFormat("yyyy-MM-dd").parse(date));
        } catch (ParseException e) {
            logger.error("Cannot purge",e);
        }
    }

    /**
     * Removes all data associated with the provided scope.
     *
     * @param scope the scope for which we want to remove data
     */
    @DELETE
    @Path("{scope}")
    public void deleteScopedData(@PathParam("scope") String scope) {
        clusterService.purge(scope);
    }
}
