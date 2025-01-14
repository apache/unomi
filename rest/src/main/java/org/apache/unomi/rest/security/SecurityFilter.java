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
package org.apache.unomi.rest.security;

import org.apache.unomi.api.security.SecurityService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.lang.reflect.Method;

@Provider
@Component(service = ContainerRequestFilter.class)
@Priority(Priorities.AUTHORIZATION)
public class SecurityFilter implements ContainerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(SecurityFilter.class);

    @Reference
    private SecurityService securityService;

    @Context
    private ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        Method method = resourceInfo.getResourceMethod();
        RequiresRole roleAnnotation = method.getAnnotation(RequiresRole.class);
        RequiresTenant tenantAnnotation = method.getAnnotation(RequiresTenant.class);

        try {
            // Check role-based access
            if (roleAnnotation != null) {
                String[] roles = roleAnnotation.value();
                boolean hasAccess = false;
                for (String role : roles) {
                    if (securityService.hasRole(role)) {
                        hasAccess = true;
                        break;
                    }
                }
                if (!hasAccess) {
                    requestContext.abortWith(Response.status(Response.Status.FORBIDDEN)
                            .entity("User does not have required role")
                            .build());
                    return;
                }
            }

            // Check tenants-based access
            if (tenantAnnotation != null) {
                String tenantId = requestContext.getHeaderString("X-Unomi-Tenant");
                if (tenantId == null) {
                    requestContext.abortWith(Response.status(Response.Status.BAD_REQUEST)
                            .entity("Tenant ID is required")
                            .build());
                    return;
                }
                if (!securityService.hasTenantAccess(tenantId)) {
                    requestContext.abortWith(Response.status(Response.Status.FORBIDDEN)
                            .entity("User does not have access to tenants")
                            .build());
                    return;
                }
            }

        } catch (Exception e) {
            logger.error("Error during security check", e);
            requestContext.abortWith(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error during security check")
                    .build());
        }
    }
}
