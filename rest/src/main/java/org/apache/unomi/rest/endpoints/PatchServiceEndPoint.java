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

package org.apache.unomi.rest.endpoints;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.Patch;
import org.apache.unomi.api.services.PatchService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

/**
 * A JAX-RS endpoint to manage patches.
 */
@WebService
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
@Path("/patches")
@Component(service=PatchServiceEndPoint.class,property = "osgi.jaxrs.resource=true")
public class PatchServiceEndPoint {

    @Reference
    private PatchService patchService;

    public void setPatchService(PatchService patchService) {
        this.patchService = patchService;
    }

    /**
     * Apply a patch on an item
     * @param patch the patch to apply
     * @param force a boolean to force (or not) the application of the patch even if it was previously applied.
     */
    @POST
    @Path("/apply")
    public void setPatch(Patch patch, @QueryParam("force") Boolean force) {
        Patch previous = (force == null || !force) ? patchService.load(patch.getItemId()) : null;
        if (previous == null) {
            patchService.patch(patch);
        }
    }

}
