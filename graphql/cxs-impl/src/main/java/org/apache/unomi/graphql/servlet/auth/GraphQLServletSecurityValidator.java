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

import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.Node;
import graphql.language.OperationDefinition;
import graphql.parser.Parser;
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

import static graphql.language.OperationDefinition.Operation.MUTATION;
import static graphql.language.OperationDefinition.Operation.QUERY;
import static graphql.language.OperationDefinition.Operation.SUBSCRIPTION;
import static org.osgi.service.http.HttpContext.AUTHENTICATION_TYPE;
import static org.osgi.service.http.HttpContext.REMOTE_USER;

public class GraphQLServletSecurityValidator {

    private static final Logger LOG = LoggerFactory.getLogger(GraphQLServletSecurityValidator.class);

    private final Parser parser;

    public GraphQLServletSecurityValidator() {
        parser = new Parser();
    }

    public boolean validate(String query, String operationName, HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (isPublicOperation(query)) {
            return true;
        } else if (req.getHeader("Authorization") == null) {
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

        String usernameAndPassword = new String(Base64.getDecoder().decode(authHeader.substring(6).getBytes()));
        int userNameIndex = usernameAndPassword.indexOf(":");
        String username = usernameAndPassword.substring(0, userNameIndex);
        String password = usernameAndPassword.substring(userNameIndex + 1);

        LoginContext loginContext;
        try {
            loginContext = new LoginContext("karaf", callbacks -> {
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
            Subject subject = loginContext.getSubject();
            boolean success = subject != null;
            if (success) {
                req.setAttribute(REMOTE_USER, subject);
            }
            return success;
        } catch (LoginException e) {
            LOG.warn("Login failed", e);
            return false;
        }
    }
}
