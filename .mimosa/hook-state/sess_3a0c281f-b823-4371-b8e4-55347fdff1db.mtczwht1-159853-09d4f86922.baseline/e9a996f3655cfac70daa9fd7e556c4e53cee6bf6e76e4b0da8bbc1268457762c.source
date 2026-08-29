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

import org.apache.unomi.didvc.api.DidDocumentData;
import org.apache.unomi.didvc.api.services.DidService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Well-known DID-document endpoint. Until Host-based did:web resolution
 * lands, the DID is supplied as a query parameter: {@code /.well-known/did.json?did=did:web:...}.
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/.well-known/did.json")
@Component(service = DidWebEndpoint.class, property = "osgi.jaxrs.resource=true")
public class DidWebEndpoint {

    @Reference
    private DidService didService;

    public void setDidService(DidService didService) {
        this.didService = didService;
    }

    @GET
    public Response didDocument(@QueryParam("did") String did) {
        if (did == null || did.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("did query parameter is required until Host-based did:web resolution lands")
                    .build();
        }
        DidDocumentData doc = didService.resolveDid(did);
        if (doc == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(doc).build();
    }
}
