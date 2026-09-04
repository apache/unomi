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

package org.apache.unomi.graphql.servlet.auth;

import graphql.language.*;
import graphql.parser.Parser;
import org.apache.unomi.api.ExecutionContext;
import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.tenants.ApiKey;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static graphql.language.OperationDefinition.Operation.*;
import static org.osgi.service.http.HttpContext.AUTHENTICATION_TYPE;
import static org.osgi.service.http.HttpContext.REMOTE_USER;

public class GraphQLServletSecurityValidator {

    private static final Logger LOG = LoggerFactory.getLogger(GraphQLServletSecurityValidator.class);
    private static final String UNOMI_TENANT_ID_HEADER = "X-Unomi-Tenant-Id";

    private final Parser parser;
    private final TenantService tenantService;
    private final SecurityService securityService;
    private final ExecutionContextManager executionContextManager;

    public GraphQLServletSecurityValidator(TenantService tenantService,
                                         SecurityService securityService,
                                         ExecutionContextManager executionContextManager) {
        this.parser = new Parser();
        this.tenantService = tenantService;
        this.securityService = securityService;
        this.executionContextManager = executionContextManager;
    }

    /**
     * Authenticates a WebSocket upgrade. Subscriptions are never public, so only Basic
     * (JAAS or tenant private key) is accepted.
     *
     * @return true when the caller is authenticated and a security context was established
     */
    public boolean validateWebSocketUpgrade(HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (req.getHeader("Authorization") == null) {
            res.addHeader("WWW-Authenticate", "Basic realm=\"karaf\"");
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        if (isAuthenticatedUser(req)) {
            return true;
        }
        res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return false;
    }

    public boolean validate(String query, String operationName, HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (isPublicOperation(query)) {
            // For public operations, check API key
            String apiKey = req.getHeader("X-Unomi-Api-Key");
            if (apiKey != null) {
                Tenant tenant = tenantService.getTenantByApiKey(apiKey, ApiKey.ApiKeyType.PUBLIC);
                if (tenant != null) {
                    // Set the security context for public API key
                    Subject subject = securityService.createSubject(tenant.getItemId(), false);
                    securityService.setCurrentSubject(subject);
                    executionContextManager.setCurrentContext(executionContextManager.createContext(tenant.getItemId()));
                    return true;
                }
            }
        }

        if (req.getHeader("Authorization") == null) {
            res.addHeader("WWW-Authenticate", "Basic realm=\"karaf\"");
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        if (isAuthenticatedUser(req)) {
            return true;
        } else {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
    }

    private boolean isPublicOperation(String query) {
        if (query == null) {
            return false;
        }

        final Document queryDoc = parser.parseDocument(query);
        if (queryDoc.getDefinitions().isEmpty()) {
            return false;
        }
        final Definition<?> def = queryDoc.getDefinitions().get(0);
        if (def instanceof OperationDefinition) {
            OperationDefinition opDef = (OperationDefinition) def;
            if (SUBSCRIPTION.equals(opDef.getOperation())) {
                // subscriptions are not public
                return false;
            } else if ("IntrospectionQuery".equals(opDef.getName())) {
                // allow introspection query
                return true;
            }

            List<Node> children = opDef.getSelectionSet().getChildren();
            final Field cdp = (Field) children.stream().filter((node) -> {
                return (node instanceof Field) && "cdp".equals(((Field) node).getName());
            }).findFirst().orElse(null);
            if (cdp == null) {
                // allow not a cdp namespace
                return true;
            }

            final List<String> allowedNodeNames = new ArrayList<>();
            if (QUERY.equals(opDef.getOperation())) {
                allowedNodeNames.add("getProfile");
            } else if (MUTATION.equals(opDef.getOperation())) {
                allowedNodeNames.add("processEvents");
            }

            return cdp.getSelectionSet().getChildren().stream().allMatch((node) -> {
                return (node instanceof Field) && allowedNodeNames.contains(((Field) node).getName());
            });
        }
        return true;
    }

    /**
     * Authenticates a Basic credential that did not arrive as a request header — used by the WebSocket
     * {@code connection_init} handshake, which is the only way a browser client can present credentials
     * (the browser WebSocket API cannot set request headers).
     * <p>
     * Deliberately the same credential format and the same verification path as the header route, so
     * there is one way to authenticate, not two. No request is involved, so no tenant header is honoured
     * here: the caller gets its own tenant's context, never a caller-selected one.
     *
     * @param authorizationValue a {@code Basic <base64>} credential
     * @return true when the credential authenticated and a security context was established
     */
    public boolean authenticateBasicCredential(String authorizationValue) {
        return authenticateBasic(authorizationValue, null);
    }

    private boolean isAuthenticatedUser(HttpServletRequest req) {
        req.setAttribute(AUTHENTICATION_TYPE, HttpServletRequest.BASIC_AUTH);
        return authenticateBasic(req.getHeader("Authorization"), req);
    }

    /**
     * @param req the originating request, or {@code null} when the credential did not arrive on one
     *            (WebSocket {@code connection_init}); when null, no tenant header is consulted.
     */
    private boolean authenticateBasic(String authHeader, HttpServletRequest req) {
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return false;
        }

        final String usernameAndPassword;
        try {
            usernameAndPassword = new String(
                    Base64.getDecoder().decode(authHeader.substring(6).getBytes(StandardCharsets.UTF_8)),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // Malformed Base64 must be treated as an authentication failure (401), not a 500.
            LOG.debug("Malformed Basic Authorization header", e);
            return false;
        }
        int userNameIndex = usernameAndPassword.indexOf(":");
        if (userNameIndex == -1) {
            return false;
        }

        String username = usernameAndPassword.substring(0, userNameIndex);
        String password = usernameAndPassword.substring(userNameIndex + 1);

        // An unset org.apache.unomi.security.root.password resolves to the empty string, which
        // PropertiesLoginModule then accepts as the shipped administrator's password (UNOMI-974).
        // This servlet authenticates against the karaf realm directly rather than through the REST
        // AuthenticationFilter, so it needs its own refusal: it stays reachable on launch paths the
        // startup guards in bin/setenv and the Docker entrypoint cannot cover, notably karaf.bat.
        // Checked ahead of the API key lookup too — an empty private key is never a valid one.
        if (password.isEmpty()) {
            LOG.warn("Rejecting Basic authentication with an empty password");
            return false;
        }

        // First try API key authentication
        if (username.length() > 0) {
            Tenant tenant = tenantService.getTenantByApiKey(password, ApiKey.ApiKeyType.PRIVATE);
            if (tenant != null && tenant.getItemId().equals(username)) {
                if (req != null) {
                    req.setAttribute(REMOTE_USER, username);
                }
                // Set the security context for private API key
                Subject subject = securityService.createSubject(tenant.getItemId(), true);
                securityService.setCurrentSubject(subject);
                executionContextManager.setCurrentContext(executionContextManager.createContext(tenant.getItemId()));
                return true;
            }
        }

        // Fall back to JAAS authentication
        try {
            Subject subject = new Subject();
            LoginContext loginContext = new LoginContext("karaf", subject, callbacks -> {
                for (Callback callback : callbacks) {
                    if (callback instanceof NameCallback) {
                        ((NameCallback) callback).setName(username);
                    } else if (callback instanceof PasswordCallback) {
                        ((PasswordCallback) callback).setPassword(password.toCharArray());
                    } else {
                        throw new UnsupportedCallbackException(callback);
                    }
                }
            });
            loginContext.login();
            Subject loginSubject = loginContext.getSubject();
            boolean success = loginSubject != null;
            if (success) {
                if (req != null) {
                    req.setAttribute(REMOTE_USER, username);
                }
                // Set the security context for JAAS authentication
                securityService.setCurrentSubject(loginSubject);

                // Check for tenant ID header (only meaningful when the credential arrived on a request)
                String tenantId = req != null ? req.getHeader(UNOMI_TENANT_ID_HEADER) : null;
                if (tenantId != null && !tenantId.trim().isEmpty()) {
                    // Validate tenant exists
                    Tenant tenant = tenantService.getTenant(tenantId);
                    if (tenant != null) {
                        executionContextManager.setCurrentContext(executionContextManager.createContext(tenantId));
                    } else {
                        LOG.warn("Invalid tenant ID provided in header: {}", tenantId);
                        // Same fallback as the "no tenant header" branch below: the thread-local
                        // execution context must always be set explicitly here, otherwise a stale
                        // context from a previous request on this pooled thread could leak in.
                        executionContextManager.setCurrentContext(ExecutionContext.systemContext());
                    }
                } else {
                    executionContextManager.setCurrentContext(ExecutionContext.systemContext());
                }
            }
            return success;
        } catch (LoginException e) {
            LOG.debug("Login failed", e);
            return false;
        }
    }
}
