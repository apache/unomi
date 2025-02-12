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
import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.api.security.UnomiRoles;
import org.apache.unomi.api.tenants.ApiKey;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);
    private static final String GUEST_USERNAME = "guest";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BASIC_AUTH_PREFIX = "Basic ";
    private static final String BEARER_AUTH_PREFIX = "Bearer ";
    private static final String GUEST_AUTH_PREFIX = "Guest ";
    private static final String GUEST_AUTH_HEADER = GUEST_AUTH_PREFIX + GUEST_USERNAME;

    // JAAS config
    private static final String ROLE_CLASSIFIER = "ROLE_UNOMI";
    private static final String ROLE_CLASSIFIER_TYPE = JAASLoginInterceptor.ROLE_CLASSIFIER_PREFIX;
    private static final String REALM_NAME = "cxs";
    private static final String CONTEXT_NAME = "karaf";

    private static final List<String> GUEST_ROLES = Collections.singletonList(UnomiRoles.USER);
    private static final Subject GUEST_SUBJECT = new Subject();
    static {
        GUEST_SUBJECT.getPrincipals().add(new UserPrincipal("guest"));
        GUEST_SUBJECT.getPrincipals().add(new RolePrincipal(UnomiRoles.USER));
    }

    private final JAASAuthenticationFilter jaasAuthenticationFilter;
    private final RestAuthenticationConfig restAuthenticationConfig;
    private final TenantService tenantService;
    private final SecurityService securityService;

    public AuthenticationFilter(RestAuthenticationConfig restAuthenticationConfig,
                              TenantService tenantService,
                              SecurityService securityService) {
        this.restAuthenticationConfig = restAuthenticationConfig;
        this.tenantService = tenantService;
        this.securityService = securityService;

        // Build wrapped jaas filter
        jaasAuthenticationFilter = new JAASAuthenticationFilter();
        jaasAuthenticationFilter.setRoleClassifier(ROLE_CLASSIFIER);
        jaasAuthenticationFilter.setRoleClassifierType(ROLE_CLASSIFIER_TYPE);
        jaasAuthenticationFilter.setContextName(CONTEXT_NAME);
        jaasAuthenticationFilter.setRealmName(REALM_NAME);
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        try {
            String path = requestContext.getUriInfo().getPath();

            // Tenant endpoints require JAAS authentication only
            if (path.startsWith("tenants")) {
                String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
                if (authHeader == null || !authHeader.startsWith("Basic ")) {
                    logger.debug("Tenant endpoint access denied: Missing or invalid Basic Auth header");
                    unauthorized(requestContext);
                    return;
                }

                try {
                    jaasAuthenticationFilter.filter(requestContext);
                    // Get the subject from the security context after successful JAAS auth
                    SecurityContext securityContext = JAXRSUtils.getCurrentMessage().get(SecurityContext.class);
                    if (securityContext != null) {
                        Subject subject = ((RolePrefixSecurityContextImpl) securityContext).getSubject();
                        // Set the authenticated subject in Unomi's security service
                        securityService.setCurrentSubject(subject);
                    }
                    return;
                } catch (Exception e) {
                    logger.debug("Tenant endpoint access denied: JAAS authentication failed");
                    unauthorized(requestContext);
                    return;
                }
            }

            // Check if this is a public path
            if (isPublicPath(requestContext)) {
                String apiKey = requestContext.getHeaderString("X-Unomi-API-Key");
                if (apiKey == null) {
                    logger.debug("Public endpoint access denied: Missing API key");
                    unauthorized(requestContext);
                    return;
                }

                // Find tenant by API key and validate it's a public key
                Tenant tenant = tenantService.getTenantByApiKey(apiKey, ApiKey.ApiKeyType.PUBLIC);
                if (tenant != null) {
                    // Create and set security context with tenant principal and public role
                    Subject subject = securityService.createSubject(tenant.getItemId(), false);

                    // Set CXF security context
                    JAXRSUtils.getCurrentMessage().put(SecurityContext.class,
                        new RolePrefixSecurityContextImpl(subject, ROLE_CLASSIFIER, ROLE_CLASSIFIER_TYPE));

                    // Set the security service subject
                    securityService.setCurrentSubject(subject);
                    return;
                }
                logger.debug("Public endpoint access denied: Invalid public API key");
                unauthorized(requestContext);
                return;
            }

            // For private endpoints, try tenant private key first, then fall back to JAAS
            String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith("Basic ")) {
                // Try tenant private key authentication first
                String[] credentials = extractBasicAuthCredentials(authHeader);
                if (credentials != null && credentials.length == 2) {
                    String tenantId = credentials[0];
                    String privateKey = credentials[1];

                    // Validate tenant credentials with private key type
                    if (tenantService.validateApiKeyWithType(tenantId, privateKey, ApiKey.ApiKeyType.PRIVATE)) {
                        Subject subject = securityService.createSubject(tenantId, true);

                        // Set CXF security context
                        JAXRSUtils.getCurrentMessage().put(SecurityContext.class,
                            new RolePrefixSecurityContextImpl(subject, ROLE_CLASSIFIER, ROLE_CLASSIFIER_TYPE));

                        // Set the security service subject
                        securityService.setCurrentSubject(subject);
                        return;
                    }
                    logger.debug("Private endpoint access denied: Invalid tenant private key");
                }

                // If tenant auth fails, try JAAS auth
                try {
                    jaasAuthenticationFilter.filter(requestContext);
                    // Get the subject from the security context after successful JAAS auth
                    SecurityContext securityContext = JAXRSUtils.getCurrentMessage().get(SecurityContext.class);
                    if (securityContext != null) {
                        Subject subject = ((RolePrefixSecurityContextImpl) securityContext).getSubject();
                        // Set the authenticated subject in Unomi's security service
                        securityService.setCurrentSubject(subject);
                    }
                    return;
                } catch (Exception e) {
                    logger.debug("Private endpoint access denied: Both tenant key and JAAS authentication failed");
                }
            } else {
                logger.debug("Private endpoint access denied: Missing Basic Auth header");
            }

            // If we get here, no valid authentication was provided
            unauthorized(requestContext);
        } catch (Exception e) {
            logger.error("Error during authentication", e);
            unauthorized(requestContext);
        }
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
