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

package org.apache.unomi.healthcheck.servlet;

import org.apache.karaf.jaas.boot.principal.RolePrincipal;
import org.apache.karaf.jaas.boot.principal.UserPrincipal;
import org.osgi.service.http.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.util.Base64;

/**
 * @author Jerome Blanchard
 */
public class HealthCheckHttpContext implements HttpContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckHttpContext.class.getName());

    private final String realm;

    public HealthCheckHttpContext(String realm) {
        this.realm = realm;
    }

    public boolean handleSecurity(HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (req.getHeader("Authorization") == null) {
            LOGGER.info("No Authorization header found, sending 401");
            res.addHeader("WWW-Authenticate", "Basic realm=\"" + realm + "\"");
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        if (authenticated(req)) {
            LOGGER.info("User authenticated, allowing access");
            return true;
        } else {
            LOGGER.info("User not authenticated, sending 401");
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
    }

    protected boolean authenticated(HttpServletRequest request) {
        request.setAttribute(AUTHENTICATION_TYPE, HttpServletRequest.BASIC_AUTH);

        String authzHeader = request.getHeader("Authorization");
        String usernameAndPassword = new String(Base64.getDecoder().decode(authzHeader.substring(6).getBytes()));
        String[] parts = usernameAndPassword.split(":");

        LOGGER.info("Authenticating user {}", parts[0]);
        try {
            //We use JAAS for authentication and authorization but it could be done using UserAdmin OSGI service
            LOGGER.info("Creating Login Context for realm {}", realm);
            LoginContext loginContext = new LoginContext(realm, callbacks -> {
                for (Callback callback : callbacks) {
                    if (callback instanceof NameCallback) {
                        ((NameCallback) callback).setName(parts[0]);
                    } else if (callback instanceof PasswordCallback) {
                        ((PasswordCallback) callback).setPassword(parts[1].toCharArray());
                    } else {
                        throw new UnsupportedCallbackException(callback);
                    }
                }
            });
            LOGGER.info("Login Context created");
            loginContext.login();
            LOGGER.info("Login Context called");
            if (loginContext.getSubject() != null) {
                LOGGER.info("User authenticated, subject is not null {}", loginContext.getSubject());
                String username = loginContext.getSubject().getPrincipals(UserPrincipal.class).stream()
                        .map(UserPrincipal::getName).findFirst().orElse("unknown");
                String[] roles = loginContext.getSubject().getPrincipals(RolePrincipal.class).stream().map(RolePrincipal::getName)
                                .toArray(String[]::new);
                LOGGER.info("User {} authenticated with roles {}", username, roles);
                request.setAttribute(REMOTE_USER, username);
                request.setAttribute(AUTHORIZATION, new HealthCheckAuthorization(username, roles));
                return true;
            }
        } catch (Exception e) {
            LOGGER.error("Error while authenticating user", e);
        }
        return false;
    }

    public URL getResource(String s) {
        return null;
    }

    public String getMimeType(String s) {
        return null;
    }

}
