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
 * CXF's CORS filter, with cookies allowed on public tracker responses only for origins the operator listed.
 * <p>
 * CXF answers preflights and grants the public tracker endpoints to any origin without credentials. This
 * subclass rewrites that grant right after CXF writes it, in the same response filter call, so the result
 * does not depend on filter ordering. That matters because CXF 3.x only reads {@code javax.annotation.Priority}:
 * a separate filter annotated with {@code jakarta.annotation.Priority} gets the default priority and runs
 * before CXF's, which then overwrites it. Request handling, {@code @PreMatching} and the priority are CXF's own.
 * A response CXF did not grant is left alone.
 */
public class PublicTrackerCorsFilter extends CrossOriginResourceSharingFilter {

    static final String ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    static final String ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";

    private final PublicTrackerSettings settings;

    /**
     * @param settings the resolved list of website origins that may use the visitor cookie
     */
    public PublicTrackerCorsFilter(PublicTrackerSettings settings) {
        this.settings = settings;
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        super.filter(requestContext, responseContext);
        restrictCredentials(requestContext, responseContext);
    }

    void restrictCredentials(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        if (!isPublic(requestContext)) {
            return;
        }
        String origin = requestContext.getHeaderString("Origin");
        MultivaluedMap<String, Object> headers = responseContext.getHeaders();
        if (origin == null || !headers.containsKey(ALLOW_ORIGIN)) {
            return;
        }
        if (settings.policy().allows(origin)) {
            headers.putSingle(ALLOW_ORIGIN, origin);
            headers.putSingle(ALLOW_CREDENTIALS, "true");
            if (!variesOnOrigin(headers)) {
                headers.add("Vary", "Origin");
            }
            return;
        }
        // Never echo an unlisted origin and never advertise cookies to it.
        headers.putSingle(ALLOW_ORIGIN, "*");
        headers.remove(ALLOW_CREDENTIALS);
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
