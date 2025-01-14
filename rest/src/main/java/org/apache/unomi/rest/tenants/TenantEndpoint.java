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
package org.apache.unomi.rest.tenants;

import org.apache.unomi.api.tenants.ApiKey;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Component
@Path("/tenants")
public class TenantEndpoint {

    @Reference
    private TenantService tenantService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Tenant createTenant(TenantRequest request) {
        return tenantService.createTenant(request.getName(), request.getProperties());
    }

    @POST
    @Path("/{tenantId}/apikeys")
    @Produces(MediaType.APPLICATION_JSON)
    public ApiKey generateApiKey(@PathParam("tenantId") String tenantId,
                                 @QueryParam("validityPeriod") Long validityPeriod) {
        return tenantService.generateApiKey(tenantId, validityPeriod);
    }
}
