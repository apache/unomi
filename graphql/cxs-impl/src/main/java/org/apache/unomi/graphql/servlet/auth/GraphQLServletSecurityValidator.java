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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static graphql.language.OperationDefinition.Operation.*;
import static org.osgi.service.http.HttpContext.AUTHENTICATION_TYPE;
import static org.osgi.service.http.HttpContext.REMOTE_USER;

public class GraphQLServletSecurityValidator {

    private static final Logger LOG = LoggerFactory.getLogger(GraphQLServletSecurityValidator.class);

    private final Parser parser;
    private final TenantService tenantService;

    public GraphQLServletSecurityValidator(TenantService tenantService) {
        this.parser = new Parser();
        this.tenantService = tenantService;
    }

    public boolean validate(String query, String operationName, HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (isPublicOperation(query)) {
            // For public operations, check API key
            String apiKey = req.getHeader("X-Unomi-Api-Key");
            if (apiKey != null) {
                Tenant tenant = tenantService.getTenantByApiKey(apiKey, ApiKey.ApiKeyType.PUBLIC);
                if (tenant != null) {
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

    private boolean isAuthenticatedUser(HttpServletRequest req) {
        req.setAttribute(AUTHENTICATION_TYPE, HttpServletRequest.BASIC_AUTH);

        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return false;
        }

        String usernameAndPassword = new String(Base64.getDecoder().decode(authHeader.substring(6).getBytes()));
        int userNameIndex = usernameAndPassword.indexOf(":");
        if (userNameIndex == -1) {
            return false;
        }

        String username = usernameAndPassword.substring(0, userNameIndex);
        String password = usernameAndPassword.substring(userNameIndex + 1);

        // First try API key authentication
        if (username.length() > 0) {
            Tenant tenant = tenantService.getTenantByApiKey(password, ApiKey.ApiKeyType.PRIVATE);
            if (tenant != null && tenant.getItemId().equals(username)) {
                req.setAttribute(REMOTE_USER, username);
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
                req.setAttribute(REMOTE_USER, loginSubject);
            }
            return success;
        } catch (LoginException e) {
            LOG.debug("Login failed", e);
            return false;
        }
    }
}
