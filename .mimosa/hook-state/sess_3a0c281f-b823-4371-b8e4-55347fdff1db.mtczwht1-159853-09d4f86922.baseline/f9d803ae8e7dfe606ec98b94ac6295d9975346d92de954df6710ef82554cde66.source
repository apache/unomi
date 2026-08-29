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

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

/**
 * A JAX-RS endpoint to manage patches.
 */
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

    /**
     * Sets the patch service.
     *
     * @param patchService the patch service
     */
    public void setPatchService(PatchService patchService) {
        this.patchService = patchService;
    }

    /**
     * Applies a patch to an item.
     * <p>
     * When {@code force} is {@code false} or omitted and the patch was already applied, the call is a no-op.
     *
     * @param patch the patch to apply
     * @param force when {@code true}, re-applies even if previously applied
     * @api.status 204 empty Patch applied or skipped because it was already applied.
     * @api.example {"itemId":"profile-1","itemType":"patch","patches":[{"operation":"set","path":"properties.firstName","value":"Ada"}]}
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
