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
import org.apache.unomi.api.security.UnomiRoles;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
        if (isPublicOperation(query, operationName)) {
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

    /**
     * Decides whether a GraphQL document may run against a public API key. The check authorizes the
     * exact operation graphql-java will execute for the request (selected by {@code operationName}),
     * evaluates every selected root field against the public allow-list, detects introspection
     * structurally, and fails closed on anything it cannot prove public.
     */
    private boolean isPublicOperation(String query, String operationName) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }

        final Document queryDoc;
        try {
            queryDoc = parser.parseDocument(query);
        } catch (RuntimeException e) {
            // Unparseable input is never treated as public; execution will surface the syntax error.
            LOG.debug("Failed to parse GraphQL document; refusing public classification", e);
            return false;
        }

        // Separate the document into its operations and a fragment lookup table.
        final Map<String, FragmentDefinition> fragments = new HashMap<>();
        final List<OperationDefinition> operations = new ArrayList<>();
        for (Definition<?> def : queryDoc.getDefinitions()) {
            if (def instanceof OperationDefinition) {
                operations.add((OperationDefinition) def);
            } else if (def instanceof FragmentDefinition) {
                final FragmentDefinition fragment = (FragmentDefinition) def;
                fragments.put(fragment.getName(), fragment);
            }
        }
        if (operations.isEmpty()) {
            // No executable operation (e.g. a fragment-only document): fail closed.
            return false;
        }

        // Authorize exactly the operation graphql-java will execute, not simply the first definition.
        final OperationDefinition operation = resolveExecutedOperation(operations, operationName);
        if (operation == null) {
            // Ambiguous, duplicated or unknown operation name: fail closed.
            return false;
        }
        return isOperationPublic(operation, fragments);
    }

    /**
     * Resolves the operation graphql-java will execute: with an explicit {@code operationName} exactly
     * one operation must match; without a name the document must contain exactly one operation (per the
     * GraphQL spec). Anything else is invalid input and is refused.
     */
    private OperationDefinition resolveExecutedOperation(List<OperationDefinition> operations, String operationName) {
        if (operationName != null && !operationName.trim().isEmpty()) {
            OperationDefinition match = null;
            for (OperationDefinition operation : operations) {
                if (operationName.equals(operation.getName())) {
                    if (match != null) {
                        return null; // duplicate operation name: invalid document
                    }
                    match = operation;
                }
            }
            return match;
        }
        return operations.size() == 1 ? operations.get(0) : null;
    }

    private boolean isOperationPublic(OperationDefinition operation, Map<String, FragmentDefinition> fragments) {
        // Subscriptions are never public.
        if (SUBSCRIPTION.equals(operation.getOperation())) {
            return false;
        }

        final List<Field> topLevelFields = collectFields(operation.getSelectionSet(), fragments, new HashSet<>());
        if (topLevelFields.isEmpty()) {
            return false;
        }

        // Introspection is detected structurally, never by the operation's name: a query whose top-level
        // selections are all meta fields (__schema / __type / __typename) is public.
        if (QUERY.equals(operation.getOperation())
                && topLevelFields.stream().allMatch(field -> field.getName() != null && field.getName().startsWith("__"))) {
            return true;
        }

        // Every public operation lives entirely under a single "cdp" root; touching anything else is refused.
        final List<Field> cdpFields = topLevelFields.stream()
                .filter(field -> "cdp".equals(field.getName()))
                .collect(Collectors.toList());
        if (cdpFields.isEmpty() || cdpFields.size() != topLevelFields.size()) {
            return false;
        }

        final List<String> allowedNodeNames = new ArrayList<>();
        if (QUERY.equals(operation.getOperation())) {
            allowedNodeNames.add("getProfile");
        } else if (MUTATION.equals(operation.getOperation())) {
            allowedNodeNames.add("processEvents");
        } else {
            return false;
        }

        for (Field cdp : cdpFields) {
            final List<Field> cdpChildren = collectFields(cdp.getSelectionSet(), fragments, new HashSet<>());
            if (cdpChildren.isEmpty()) {
                // An empty cdp selection is not an affirmatively public request.
                return false;
            }
            for (Field child : cdpChildren) {
                if (!allowedNodeNames.contains(child.getName())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Flattens a selection set into concrete fields, resolving inline fragments and fragment spreads
     * (guarding against fragment cycles) so nothing can be hidden from the allow-list inside a fragment.
     */
    private List<Field> collectFields(SelectionSet selectionSet, Map<String, FragmentDefinition> fragments, Set<String> visitedFragments) {
        final List<Field> fields = new ArrayList<>();
        if (selectionSet == null) {
            return fields;
        }
        for (Selection selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                fields.add((Field) selection);
            } else if (selection instanceof InlineFragment) {
                fields.addAll(collectFields(((InlineFragment) selection).getSelectionSet(), fragments, visitedFragments));
            } else if (selection instanceof FragmentSpread) {
                final String name = ((FragmentSpread) selection).getName();
                if (visitedFragments.add(name)) {
                    final FragmentDefinition fragment = fragments.get(name);
                    if (fragment != null) {
                        fields.addAll(collectFields(fragment.getSelectionSet(), fragments, visitedFragments));
                    }
                }
            }
        }
        return fields;
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
            if (loginSubject == null) {
                return false;
            }

            // Set the security context for JAAS authentication
            securityService.setCurrentSubject(loginSubject);

            // A successful realm login is not by itself an authorization to use this API: the realm
            // can carry accounts that hold no Unomi role at all. Require the same administrator roles
            // the REST admin surface requires.
            if (!securityService.hasRole(UnomiRoles.ADMINISTRATOR)
                    && !securityService.hasRole(UnomiRoles.TENANT_ADMINISTRATOR)) {
                LOG.warn("Refusing GraphQL access to '{}': the account holds no Unomi administrator role", username);
                securityService.clearCurrentSubject();
                return false;
            }

            // Check for tenant ID header (only present when the credential arrived on a request;
            // the connection_init route carries none, so it can never select a tenant this way)
            String tenantId = req != null ? req.getHeader(UNOMI_TENANT_ID_HEADER) : null;
            if (tenantId != null && !tenantId.trim().isEmpty()) {
                // Validate tenant exists
                Tenant tenant = tenantService.getTenant(tenantId);
                if (tenant == null) {
                    LOG.warn("Invalid tenant ID provided in header: {}", tenantId);
                    securityService.clearCurrentSubject();
                    return false;
                }
                // Naming a tenant is not the same as having authority over it.
                if (!securityService.hasSystemAccess() && !securityService.hasTenantAccess(tenantId)) {
                    LOG.warn("Refusing GraphQL access to '{}': no authority over tenant {}", username, tenantId);
                    securityService.clearCurrentSubject();
                    return false;
                }
                executionContextManager.setCurrentContext(executionContextManager.createContext(tenantId));
            } else {
                // No tenant header. The system context is inherited by every tenant, so it is reserved
                // for subjects that actually hold system access rather than being the default.
                if (!securityService.hasSystemAccess()) {
                    LOG.warn("Refusing GraphQL access to '{}': no tenant specified and no system access", username);
                    securityService.clearCurrentSubject();
                    return false;
                }
                // The thread-local execution context must always be set explicitly here, otherwise a
                // stale context from a previous request on this pooled thread could leak in.
                executionContextManager.setCurrentContext(ExecutionContext.systemContext());
            }

            if (req != null) {
                req.setAttribute(REMOTE_USER, username);
            }
            return true;
        } catch (LoginException e) {
            LOG.debug("Login failed", e);
            return false;
        }
    }
}
