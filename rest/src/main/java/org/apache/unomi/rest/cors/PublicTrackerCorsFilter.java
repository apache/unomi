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
package org.apache.unomi.rest.cors;

import org.apache.unomi.api.services.ConfigSharingService;

import jakarta.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.io.IOException;

/**
 * Allows cookies on public tracker responses only for origins the operator listed.
 * Priority 1 runs first on the request (so a listed preflight is answered here) and last on the
 * response (so it can replace the broad no-credentials CORS headers).
 */
@Priority(1)
public class PublicTrackerCorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private final ConfigSharingService configSharingService;

    public PublicTrackerCorsFilter(ConfigSharingService configSharingService) {
        this.configSharingService = configSharingService;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (!isPublic(requestContext) || !"OPTIONS".equalsIgnoreCase(requestContext.getMethod())) {
            return;
        }
        String origin = requestContext.getHeaderString("Origin");
        if (!policy().allows(origin)) {
            return;
        }
        Response response = Response.ok().build();
        applyCredentialedHeaders(response.getHeaders(), origin);
        response.getHeaders().putSingle("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.getHeaders().putSingle("Access-Control-Allow-Headers",
                "Origin, X-Requested-With, Content-Type, Accept, X-Unomi-Api-Key");
        requestContext.abortWith(response);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        if (!isPublic(requestContext)) {
            return;
        }
        String origin = requestContext.getHeaderString("Origin");
        MultivaluedMap<String, Object> headers = responseContext.getHeaders();
        if (policy().allows(origin)) {
            applyCredentialedHeaders(headers, origin);
            return;
        }
        // Never echo an unlisted origin and never advertise cookies with a wildcard origin.
        if (origin != null) {
            headers.putSingle("Access-Control-Allow-Origin", "*");
            headers.remove("Access-Control-Allow-Credentials");
        }
    }

    private PublicTrackerCorsPolicy policy() {
        Object configured = configSharingService == null ? null : configSharingService.getProperty(PublicTrackerCorsPolicy.ALLOWED_ORIGINS_PROPERTY);
        return PublicTrackerCorsPolicy.parse(configured == null ? null : configured.toString());
    }

    private static boolean isPublic(ContainerRequestContext requestContext) {
        if (requestContext.getUriInfo() == null || requestContext.getUriInfo().getPath() == null) {
            return false;
        }
        return PublicTrackerCorsPolicy.isPublicTrackerPath(requestContext.getUriInfo().getPath());
    }

    private static void applyCredentialedHeaders(MultivaluedMap<String, Object> headers, String origin) {
        headers.putSingle("Access-Control-Allow-Origin", origin);
        headers.putSingle("Access-Control-Allow-Credentials", "true");
        if (!headers.containsKey("Vary") || headers.get("Vary").stream().noneMatch(value -> String.valueOf(value).contains("Origin"))) {
            headers.add("Vary", "Origin");
        }
    }
}
