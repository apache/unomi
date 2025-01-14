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

import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.rest.security.RequiresRole;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/tenants")
@Component(service = TenantManagementEndpoint.class)
public class TenantManagementEndpoint {

    @Reference
    private TenantService tenantService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RequiresRole("unomi-admin")
    public List<Tenant> getTenants() {
        return tenantService.getAllTenants();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RequiresRole("unomi-admin")
    public Response createTenant(Tenant tenant) {
        tenantService.createTenant(tenant.getName(), tenant.getProperties());
        return Response.status(Response.Status.CREATED).entity(tenant).build();
    }

    @PUT
    @Path("/{tenantId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @RequiresRole("unomi-admin")
    public Response updateTenant(@PathParam("tenantId") String tenantId, Tenant tenant) {
        if (!tenantId.equals(tenant.getItemId())) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        tenantService.saveTenant(tenant);
        return Response.ok().build();
    }

    @DELETE
    @Path("/{tenantId}")
    @RequiresRole("unomi-admin")
    public Response deleteTenant(@PathParam("tenantId") String tenantId) {
        tenantService.deleteTenant(tenantId);
        return Response.ok().build();
    }
}
