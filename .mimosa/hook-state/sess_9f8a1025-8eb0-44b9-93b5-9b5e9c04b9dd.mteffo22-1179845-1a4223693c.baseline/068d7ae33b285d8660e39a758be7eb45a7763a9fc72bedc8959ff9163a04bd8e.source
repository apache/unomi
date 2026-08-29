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

package org.apache.unomi.didvc.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.didvc.api.DidDocumentData;
import org.apache.unomi.didvc.api.services.UniversalDidResolverService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Universal DID resolution REST API (any supported method — did:web,
 * did:key, iAM Smart, RealDID, registry stubs). The DID is passed as a
 * URL-encoded path parameter ({@code :} percent-encoded).
 */
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
@Path("/didvc/resolver")
@Component(service = DidResolverEndPoint.class, property = "osgi.jaxrs.resource=true")
public class DidResolverEndPoint {

    @Reference
    private UniversalDidResolverService resolverService;

    public void setResolverService(UniversalDidResolverService resolverService) {
        this.resolverService = resolverService;
    }

    @GET
    @Path("/{did}")
    public Response resolve(@PathParam("did") String did) {
        DidDocumentData doc = resolverService.resolve(did);
        if (doc == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(doc).build();
    }
}
