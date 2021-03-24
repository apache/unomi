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
package org.apache.unomi.rest;

import org.apache.cxf.interceptor.security.DefaultSecurityContext;
import org.apache.cxf.interceptor.security.JAASLoginInterceptor;
import org.apache.cxf.interceptor.security.RolePrefixSecurityContextImpl;
import org.apache.cxf.jaxrs.security.JAASAuthenticationFilter;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.cxf.message.Message;
import org.apache.cxf.security.SecurityContext;
import org.apache.karaf.jaas.boot.principal.RolePrincipal;
import org.apache.karaf.jaas.boot.principal.UserPrincipal;

import javax.annotation.Priority;
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

    public static final String GUEST_USERNAME = "guest";
    public static final String GUEST_DEFAULT_ROLE = "ROLE_UNOMI_PUBLIC";

    private JAASAuthenticationFilter jaasAuthenticationFilter;
    private String roleClassifier;
    private String roleClassifierType = JAASLoginInterceptor.ROLE_CLASSIFIER_PREFIX;

    private String guestUsername = GUEST_USERNAME;
    private List<String> guestRoles = Arrays.asList(GUEST_DEFAULT_ROLE);

    private Set<String> publicPaths = new LinkedHashSet<>();

    public AuthenticationFilter() {
        jaasAuthenticationFilter = new JAASAuthenticationFilter();
    }

    public void setContextName(String contextName) {
        jaasAuthenticationFilter.setContextName(contextName);
    }

    public void setRoleClassifier(String roleClassifier) {
        this.roleClassifier = roleClassifier;
        jaasAuthenticationFilter.setRoleClassifier(roleClassifier);
    }

    public void setRoleClassifierType(String roleClassifierType) {
        this.roleClassifierType = roleClassifierType;
        jaasAuthenticationFilter.setRoleClassifierType(roleClassifierType);
    }

    public void setRealmName(String realmName) {
        jaasAuthenticationFilter.setRealmName(realmName);
    }

    public void setGuestUsername(String guestUsername) {
        this.guestUsername = guestUsername;
    }

    public void setGuestRoles(List<String> guestRoles) {
        this.guestRoles = guestRoles;
    }

    public void setPublicPaths(Set<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (isPublicPath(requestContext)) {
            Message message = JAXRSUtils.getCurrentMessage();
            Subject guestSubject = new Subject();
            guestSubject.getPrincipals().add(new UserPrincipal(guestUsername));
            for (String roleName : guestRoles) {
                guestSubject.getPrincipals().add(new RolePrincipal(roleName));
            }
            message.put(SecurityContext.class, createSecurityContext(guestUsername, guestSubject));
        } else{
            jaasAuthenticationFilter.filter(requestContext);
        }
    }

    private boolean isPublicPath(ContainerRequestContext requestContext) {
        // First we do some quick checks to protect against malformed requests
        if (requestContext.getMethod() == null) {
            return false;
        }
        if (requestContext.getMethod().length() > 10) {
            // this is a fishy request, we reject it
            return false;
        }
        if (requestContext.getUriInfo().getPath() == null) {
            return false;
        }
        if (requestContext.getUriInfo().getPath().length() > 1000) {
            return false;
        }
        if (publicPaths.contains(requestContext.getMethod() + " " + requestContext.getUriInfo().getPath())) {
            return true;
        }
        return false;
    }

    protected SecurityContext createSecurityContext(String name, Subject subject) {
        if (roleClassifier != null) {
            return new RolePrefixSecurityContextImpl(subject, roleClassifier,
                    roleClassifierType);
        }
        return new DefaultSecurityContext(name, subject);
    }

}
