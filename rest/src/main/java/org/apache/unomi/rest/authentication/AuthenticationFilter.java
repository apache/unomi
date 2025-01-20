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
package org.apache.unomi.rest.authentication;

import org.apache.cxf.interceptor.security.JAASLoginInterceptor;
import org.apache.cxf.interceptor.security.RolePrefixSecurityContextImpl;
import org.apache.cxf.jaxrs.security.JAASAuthenticationFilter;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.cxf.security.SecurityContext;
import org.apache.karaf.jaas.boot.principal.RolePrincipal;
import org.apache.karaf.jaas.boot.principal.UserPrincipal;
import org.apache.unomi.api.tenants.TenantService;

import javax.annotation.Priority;
import javax.security.auth.Subject;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * A filter that combines JAAS authentication with tenant API key authentication:
 * - JAAS authentication (if provided) grants full access
 * - Public API endpoints require a valid public API key
 * - Private API endpoints require both tenantId and private API key
 */
@PreMatching
@Priority(Priorities.AUTHENTICATION)
public class AuthenticationFilter implements ContainerRequestFilter {

    // Guest user config
    public static final String GUEST_USERNAME = "guest";
    public static final String GUEST_DEFAULT_ROLE = "ROLE_UNOMI_PUBLIC";
    private static final List<String> GUEST_ROLES = Collections.singletonList(GUEST_DEFAULT_ROLE);
    private static final Subject GUEST_SUBJECT = new Subject();
    static {
        GUEST_SUBJECT.getPrincipals().add(new UserPrincipal(GUEST_USERNAME));
        for (String roleName : GUEST_ROLES) {
            GUEST_SUBJECT.getPrincipals().add(new RolePrincipal(roleName));
        }
    }

    // JAAS config
    private static final String ROLE_CLASSIFIER = "ROLE_UNOMI";
    private static final String ROLE_CLASSIFIER_TYPE = JAASLoginInterceptor.ROLE_CLASSIFIER_PREFIX;
    private static final String REALM_NAME = "cxs";
    private static final String CONTEXT_NAME = "karaf";

    private final JAASAuthenticationFilter jaasAuthenticationFilter;
    private final RestAuthenticationConfig restAuthenticationConfig;
    private final TenantService tenantService;

    public AuthenticationFilter(RestAuthenticationConfig restAuthenticationConfig, TenantService tenantService) {
        this.restAuthenticationConfig = restAuthenticationConfig;
        this.tenantService = tenantService;

        // Build wrapped jaas filter
        jaasAuthenticationFilter = new JAASAuthenticationFilter();
        jaasAuthenticationFilter.setRoleClassifier(ROLE_CLASSIFIER);
        jaasAuthenticationFilter.setRoleClassifierType(ROLE_CLASSIFIER_TYPE);
        jaasAuthenticationFilter.setContextName(CONTEXT_NAME);
        jaasAuthenticationFilter.setRealmName(REALM_NAME);
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();

        // Tenant endpoints require JAAS authentication
        if (path.startsWith("tenants")) {
            String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Basic ")) {
                unauthorized(requestContext);
                return;
            }

            try {
                jaasAuthenticationFilter.filter(requestContext);
                return; // If JAAS auth succeeds, we're done
            } catch (Exception e) {
                unauthorized(requestContext);
                return;
            }
        }

        // For other endpoints, try JAAS authentication first if Basic Auth credentials are present
        String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            try {
                jaasAuthenticationFilter.filter(requestContext);
                return; // If JAAS auth succeeds, we're done - full access granted
            } catch (Exception e) {
                // JAAS auth failed, continue to API key checks
            }
        }

        // No valid JAAS auth, check for API key authentication
        String apiKey = requestContext.getHeaderString("X-Unomi-API-Key");
        if (apiKey == null) {
            // No API key provided, authentication failed
            unauthorized(requestContext);
            return;
        }

        // For public paths, accept public API key
        if (isPublicPath(requestContext)) {
            if (tenantService.validateApiKey(TenantService.SYSTEM_TENANT, apiKey)) {
                JAXRSUtils.getCurrentMessage().put(SecurityContext.class,
                        new RolePrefixSecurityContextImpl(GUEST_SUBJECT, ROLE_CLASSIFIER, ROLE_CLASSIFIER_TYPE));
                return;
            }
        } else {
            // For private paths, require both tenantId and private key
            if (authHeader == null || !authHeader.startsWith("Basic ")) {
                unauthorized(requestContext);
                return;
            }

            // Extract credentials
            String[] credentials = extractBasicAuthCredentials(authHeader);
            if (credentials == null || credentials.length != 2) {
                unauthorized(requestContext);
                return;
            }

            String tenantId = credentials[0];
            String privateKey = credentials[1];

            // Validate tenant credentials
            if (tenantService.validateApiKey(tenantId, privateKey)) {
                // Set the current tenant context
                tenantService.setCurrentTenant(tenantId);
                return;
            }
        }

        // If we get here, no valid authentication was provided
        unauthorized(requestContext);
    }

    private String[] extractBasicAuthCredentials(String authHeader) {
        try {
            String base64Credentials = authHeader.substring("Basic ".length()).trim();
            String credentials = new String(Base64.getDecoder().decode(base64Credentials));
            return credentials.split(":", 2);
        } catch (Exception e) {
            return null;
        }
    }

    private void unauthorized(ContainerRequestContext requestContext) {
        requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"" + REALM_NAME + "\"")
                .build());
    }

    private boolean isPublicPath(ContainerRequestContext requestContext) {
        // First we do some quick checks to protect against malformed requests
        if (requestContext.getMethod() == null ||
                requestContext.getMethod().length() > 10 ||
                requestContext.getUriInfo().getPath() == null) {
            return false;
        }

        // check if current path is matching any public path patterns
        String currentPath = requestContext.getMethod() + " " + requestContext.getUriInfo().getPath();
        return restAuthenticationConfig.getPublicPathPatterns().stream().anyMatch(pattern -> pattern.matcher(currentPath).matches());
    }
}
