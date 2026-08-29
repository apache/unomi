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
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * A simple implementation of the {@link HttpContext} interface that provides basic authentication for health checks.
 */
public class HealthCheckHttpContext implements HttpContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckHttpContext.class.getName());

    private static final String BASIC_PREFIX = "Basic ";

    private final String realm;

    public HealthCheckHttpContext(String realm) {
        this.realm = realm;
    }

    public boolean handleSecurity(HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (req.getHeader("Authorization") == null) {
            LOGGER.debug("No Authorization header found, sending 401");
            res.addHeader("WWW-Authenticate", "Basic realm=\"" + realm + "\"");
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        if (authenticated(req)) {
            LOGGER.debug("User authenticated, allowing access");
            return true;
        } else {
            LOGGER.debug("User not authenticated, sending 401");
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
    }

    protected boolean authenticated(HttpServletRequest request) {
        request.setAttribute(AUTHENTICATION_TYPE, HttpServletRequest.BASIC_AUTH);

        String[] parts = extractBasicCredentials(request.getHeader("Authorization"));
        if (parts == null) {
            LOGGER.debug("Malformed Basic credentials, refusing access");
            return false;
        }
        final String user = parts[0];
        final String password = parts[1];

        // An unset org.apache.unomi.healthcheck.password resolves to the empty string, which
        // PropertiesLoginModule then accepts as this account's password (UNOMI-974). This endpoint
        // authenticates against the karaf realm directly rather than through the REST
        // AuthenticationFilter, so it needs its own refusal: it stays reachable on launch paths the
        // startup guards in bin/setenv and the Docker entrypoint cannot cover, notably karaf.bat.
        if (password.isEmpty()) {
            LOGGER.warn("Rejecting health check Basic authentication with an empty password");
            return false;
        }

        LOGGER.debug("Authenticating user {}", user);
        try {
            //We use JAAS for authentication and authorization but it could be done using UserAdmin OSGI service
            LOGGER.debug("Creating Login Context for realm {}", realm);
            LoginContext loginContext = new LoginContext(realm, callbacks -> {
                for (Callback callback : callbacks) {
                    if (callback instanceof NameCallback) {
                        ((NameCallback) callback).setName(user);
                    } else if (callback instanceof PasswordCallback) {
                        ((PasswordCallback) callback).setPassword(password.toCharArray());
                    } else {
                        throw new UnsupportedCallbackException(callback);
                    }
                }
            });
            LOGGER.debug("Login Context created");
            loginContext.login();
            LOGGER.debug("Login Context called");
            if (loginContext.getSubject() != null) {
                LOGGER.debug("User authenticated, subject is not null {}", loginContext.getSubject());
                String username = loginContext.getSubject().getPrincipals(UserPrincipal.class).stream()
                        .map(UserPrincipal::getName).findFirst().orElse("unknown");
                String[] roles = loginContext.getSubject().getPrincipals(RolePrincipal.class).stream().map(RolePrincipal::getName)
                                .toArray(String[]::new);
                LOGGER.debug("User {} authenticated with roles {}", username, roles);
                request.setAttribute(REMOTE_USER, username);
                request.setAttribute(AUTHORIZATION, new HealthCheckAuthorization(username, roles));
                return true;
            }
        } catch (Exception e) {
            LOGGER.error("Error while authenticating user", e);
        }
        return false;
    }

    /**
     * Decodes a Basic {@code Authorization} header into {user, password}, or {@code null} when it is
     * missing, not Basic, undecodable, or carries no {@code ':'} separator.
     * <p>
     * The split is bounded to two parts on purpose. {@code split(":")} discards trailing empty
     * strings, so {@code "health:"} yielded a single element and blew up on {@code parts[1]}, while
     * {@code "health::x"} yielded {@code ["health", "", "x"]} — an <em>empty</em> password that was
     * handed straight to JAAS. Bounding it keeps the RFC 7617 rule that the password is everything
     * after the first colon, and makes the emptiness check in {@link #authenticated} meaningful.
     * <p>
     * The scheme is matched case-insensitively per RFC 7235 §2.1. The previous implementation did a
     * blind {@code substring(6)} with no prefix check at all, so it accepted {@code "basic "}; a
     * case-sensitive check here would have quietly started rejecting those clients.
     * <p>
     * Neither returned element is ever {@code null}: {@link String#split(String, int)} only ever
     * produces non-null substrings, and a result that is not exactly two elements is rejected above.
     * Package-private for {@code HealthCheckHttpContextBlankPasswordTest}, which pins every one of
     * these cases.
     */
    String[] extractBasicCredentials(String authzHeader) {
        if (authzHeader == null
                || authzHeader.length() < BASIC_PREFIX.length()
                || !authzHeader.regionMatches(true, 0, BASIC_PREFIX, 0, BASIC_PREFIX.length())) {
            return null;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(authzHeader.substring(BASIC_PREFIX.length()).trim()),
                    StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", 2);
            return parts.length == 2 ? parts : null;
        } catch (IllegalArgumentException e) {
            // Undecodable base64. Deliberately not logged at error: this is attacker-controlled input
            // and a malformed header is a client error, not a server fault.
            LOGGER.debug("Could not decode Basic credentials");
            return null;
        }
    }

    public URL getResource(String s) {
        return null;
    }

    public String getMimeType(String s) {
        return null;
    }

}
