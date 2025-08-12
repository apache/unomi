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

import jakarta.annotation.Priority;
import javax.security.auth.Subject;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import java.io.IOException;
import java.util.*;

/**
 * A wrapper filter around JAASAuthenticationFilter so that we can deactivate JAAS login around some resources and make
 * them publicly accessible.
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

    public AuthenticationFilter(RestAuthenticationConfig restAuthenticationConfig) {
        this.restAuthenticationConfig = restAuthenticationConfig;

        // Build wrapped jaas filter
        jaasAuthenticationFilter = new JAASAuthenticationFilter();
        jaasAuthenticationFilter.setRoleClassifier(ROLE_CLASSIFIER);
        jaasAuthenticationFilter.setRoleClassifierType(ROLE_CLASSIFIER_TYPE);
        jaasAuthenticationFilter.setContextName(CONTEXT_NAME);
        jaasAuthenticationFilter.setRealmName(REALM_NAME);
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (isPublicPath(requestContext)) {
            JAXRSUtils.getCurrentMessage().put(SecurityContext.class,
                    new RolePrefixSecurityContextImpl(GUEST_SUBJECT, ROLE_CLASSIFIER, ROLE_CLASSIFIER_TYPE));
        } else{
            jaasAuthenticationFilter.filter(requestContext);
        }
    }

    private boolean isPublicPath(ContainerRequestContext requestContext) {
        // First we do some quick checks to protect against malformed requests
        // TODO should be handle by input validation ?
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
