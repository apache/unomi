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

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharingFilter;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.MultivaluedMap;
import java.util.List;

/**
 * CXF's CORS filter, with credentials only for origins the operator listed.
 * <p>
 * On public tracker paths, CXF answers preflights and grants any origin without credentials; this
 * subclass upgrades listed origins to credentialed CORS (visitor cookie). On other (admin) paths,
 * annotations advertise no credentials by default; listed admin UI origins are upgraded the same way.
 * <p>
 * Rewrites run in the same response filter call as CXF's grant so the result does not depend on filter
 * ordering. That matters because CXF 3.x only reads {@code javax.annotation.Priority}: a separate filter
 * annotated with {@code jakarta.annotation.Priority} gets the default priority and runs before CXF's,
 * which then overwrites it. A response CXF did not grant is left alone.
 */
public class PublicTrackerCorsFilter extends CrossOriginResourceSharingFilter {

    static final String ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    static final String ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";

    private final PublicTrackerSettings settings;
    private final AdminCorsSettings adminCors;

    /**
     * @param settings  the resolved list of website origins that may use the visitor cookie
     * @param adminCors the resolved list of origins that may use credentialed admin CORS
     */
    public PublicTrackerCorsFilter(PublicTrackerSettings settings, AdminCorsSettings adminCors) {
        this.settings = settings;
        this.adminCors = adminCors;
    }

    /** For tests that only cover the public tracker path. */
    public PublicTrackerCorsFilter(PublicTrackerSettings settings) {
        this(settings, new AdminCorsSettings(null));
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        super.filter(requestContext, responseContext);
        restrictCredentials(requestContext, responseContext);
    }

    void restrictCredentials(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        String origin = requestContext.getHeaderString("Origin");
        MultivaluedMap<String, Object> headers = responseContext.getHeaders();
        if (origin == null || !headers.containsKey(ALLOW_ORIGIN)) {
            return;
        }
        if (isPublic(requestContext)) {
            if (allowsPublicCredentials(requestContext, origin)) {
                grantCredentials(headers, origin);
                return;
            }
            denyCredentials(headers);
            return;
        }
        if (adminCors.allows(origin)) {
            grantCredentials(headers, origin);
            return;
        }
        denyCredentials(headers);
    }

    /**
     * A preflight carries no API key, so any origin the operator or a tenant lists is accepted there; the
     * actual request, which carries the key, is checked against its own tenant.
     */
    private boolean allowsPublicCredentials(ContainerRequestContext requestContext, String origin) {
        boolean preflight = "OPTIONS".equals(requestContext.getMethod())
                && requestContext.getHeaderString("Access-Control-Request-Method") != null;
        return preflight ? settings.allowsPreflight(origin) : settings.policyForTenant(requestTenantId()).allows(origin);
    }

    private static void grantCredentials(MultivaluedMap<String, Object> headers, String origin) {
        headers.putSingle(ALLOW_ORIGIN, origin);
        headers.putSingle(ALLOW_CREDENTIALS, "true");
        if (!variesOnOrigin(headers)) {
            headers.add("Vary", "Origin");
        }
    }

    private static void denyCredentials(MultivaluedMap<String, Object> headers) {
        // Never echo an unlisted origin and never advertise cookies / Basic reuse to it.
        headers.putSingle(ALLOW_ORIGIN, "*");
        headers.remove(ALLOW_CREDENTIALS);
    }

    /** The tenant of the current request; replaced in tests. */
    String requestTenantId() {
        return RequestTenant.id();
    }

    private static boolean variesOnOrigin(MultivaluedMap<String, Object> headers) {
        List<Object> vary = headers.get("Vary");
        if (vary != null) {
            for (Object value : vary) {
                if (String.valueOf(value).contains("Origin")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isPublic(ContainerRequestContext requestContext) {
        return requestContext.getUriInfo() != null
                && PublicTrackerCorsPolicy.isPublicTrackerPath(requestContext.getUriInfo().getPath());
    }
}
