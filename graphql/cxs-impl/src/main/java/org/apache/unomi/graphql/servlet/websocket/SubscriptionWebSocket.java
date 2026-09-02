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

package org.apache.unomi.graphql.servlet.websocket;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import org.apache.unomi.api.ExecutionContext;
import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.servlet.auth.GraphQLServletSecurityValidator;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.Subject;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SubscriptionWebSocket extends WebSocketAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionWebSocket.class);

    private final GraphQL graphQL;

    private final ServiceManager serviceManager;

    /** Closes a socket that has not authenticated within this window (milliseconds). */
    private static final long AUTHENTICATION_DEADLINE_MS = 10_000L;

    private final GraphQLServletSecurityValidator validator;

    /** Set at upgrade for header-authenticated clients, or at connection_init for browser clients. */
    private volatile Subject subject;

    private volatile ExecutionContext executionContext;

    /** No operation is executed on this socket until this is true. */
    private volatile boolean authenticated;

    private final SecurityService securityService;

    private final ExecutionContextManager executionContextManager;

    private Map<String, ExecutionResultSubscriber> subscriptions = new HashMap<String, ExecutionResultSubscriber>();

    public SubscriptionWebSocket(GraphQL graphQL, ServiceManager serviceManager,
                                 Subject subject, ExecutionContext executionContext,
                                 SecurityService securityService,
                                 ExecutionContextManager executionContextManager,
                                 GraphQLServletSecurityValidator validator) {
        this.graphQL = graphQL;
        this.serviceManager = serviceManager;
        this.subject = subject;
        this.executionContext = executionContext;
        this.securityService = securityService;
        this.executionContextManager = executionContextManager;
        this.validator = validator;
        // A subject supplied here came from an authenticated upgrade; otherwise the socket starts
        // unauthenticated and must present credentials through connection_init.
        this.authenticated = subject != null;
    }

    @Override
    public void onWebSocketConnect(Session sess) {
        LOGGER.info("Opening web socket");
        super.onWebSocketConnect(sess);
        if (!authenticated) {
            // Bound how long an unauthenticated socket may sit open, so sockets that never authenticate
            // cannot accumulate. Jetty closes the session when this idle window elapses.
            sess.setIdleTimeout(AUTHENTICATION_DEADLINE_MS);
        }
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        LOGGER.info("Closing web socket");
        super.onWebSocketClose(statusCode, reason);
    }

    @Override
    public void onWebSocketText(String textMessage) {
        // Deliberately not logging the message: connection_init carries the client's credentials.
        LOGGER.debug("Got web socket message of {} characters", textMessage == null ? 0 : textMessage.length());
        final GraphQLMessage message = GraphQLMessage.fromJson(textMessage);
        if (message == null) {
            return;
        }

        // Until the socket has authenticated, connection_init is the only message that is acted on.
        // Everything else - including any attempt to start an operation - closes the socket.
        if (!authenticated && !GraphQLMessage.TYPE_CONNECTION_INIT.equals(message.getType())) {
            LOGGER.warn("Refusing '{}' on an unauthenticated GraphQL WebSocket", message.getType());
            sendMessage(GraphQLMessage.create(message.getId())
                    .type(GraphQLMessage.TYPE_CONNECTION_ERROR)
                    .errors(Collections.singletonList("Not authenticated"))
                    .build());
            closeConnection(message, CLOSE_POLICY_VIOLATION, "Not authenticated");
            return;
        }

        switch (message.getType()) {
            case GraphQLMessage.TYPE_CONNECTION_INIT:
                if (!handleConnectionInit(message)) {
                    return;
                }
                sendMessage(GraphQLMessage.connectionAck(message.getId()));
                break;
            case GraphQLMessage.GQL_START:
                subscribe(message);
                break;
            case GraphQLMessage.GQL_STOP:
                unsubscribe(message);
                break;
            case GraphQLMessage.TYPE_CONNECTION_TERMINATE:
                closeConnection(message, "Client terminated connection");
                break;
        }
    }

    /** WebSocket close codes (RFC 6455). Code 0 is not valid and produces no client-visible close. */
    private static final int CLOSE_NORMAL = 1000;
    private static final int CLOSE_POLICY_VIOLATION = 1008;

    private void closeConnection(GraphQLMessage message, String reason) {
        closeConnection(message, CLOSE_NORMAL, reason);
    }

    private void closeConnection(GraphQLMessage message, int statusCode, String reason) {
        unsubscribe(message);
        getSession().close(statusCode, reason);
    }

    private void sendMessage(GraphQLMessage message) {
        try {
            getRemote().sendString(message.toString());
        } catch (IOException e) {
            LOGGER.error("Web socket error when sending a message", e);
        }
    }

    private void unsubscribe(GraphQLMessage message) {
        final ExecutionResultSubscriber sub = subscriptions.get(message.getId());
        if (sub != null) {
            sub.unsubscribe();
            subscriptions.remove(message.getId());
        }
    }

    /**
     * Authenticates the socket from the {@code connection_init} payload, which is how a browser client
     * presents credentials (it cannot set request headers on the handshake). A socket that already
     * authenticated on the upgrade is left as it is - the payload cannot replace an established identity.
     *
     * @return true when the socket may proceed, false when it has been closed
     */
    private boolean handleConnectionInit(GraphQLMessage message) {
        if (authenticated) {
            return true;
        }

        final String credential = basicCredentialFrom(message.getPayload());
        if (credential == null || validator == null || !validator.authenticateBasicCredential(credential)) {
            LOGGER.warn("Refusing GraphQL WebSocket connection_init without a valid credential");
            sendMessage(GraphQLMessage.create(message.getId())
                    .type(GraphQLMessage.TYPE_CONNECTION_ERROR)
                    .errors(Collections.singletonList("Not authenticated"))
                    .build());
            closeConnection(message, CLOSE_POLICY_VIOLATION, "Not authenticated");
            return false;
        }

        // The validator establishes the identity on this thread; capture it onto the socket and unbind,
        // since this is a shared Jetty IO thread that must not keep carrying it.
        try {
            this.subject = securityService != null ? securityService.getCurrentSubject() : null;
            this.executionContext = executionContextManager != null
                    ? executionContextManager.getCurrentContext() : null;
        } finally {
            clearThreadSecurityContext();
        }

        if (this.subject == null) {
            LOGGER.warn("Refusing GraphQL WebSocket connection_init that produced no subject");
            closeConnection(message, CLOSE_POLICY_VIOLATION, "Not authenticated");
            return false;
        }

        this.authenticated = true;
        final Session session = getSession();
        if (session != null) {
            // Authenticated: drop the short unauthenticated deadline.
            session.setIdleTimeout(0);
        }
        return true;
    }

    /**
     * Reads a {@code Basic} credential from a connection_init payload. The same credential format as the
     * HTTP path, so both routes are verified identically.
     */
    private static String basicCredentialFrom(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (entry.getKey() != null && "authorization".equalsIgnoreCase(entry.getKey().trim())
                    && entry.getValue() instanceof String) {
                return (String) entry.getValue();
            }
        }
        return null;
    }

    private void clearThreadSecurityContext() {
        try {
            if (securityService != null) {
                securityService.clearCurrentSubject();
            }
        } catch (Exception e) {
            LOGGER.error("Error clearing GraphQL WebSocket security context", e);
        }
        try {
            if (executionContextManager != null) {
                executionContextManager.setCurrentContext(null);
            }
        } catch (Exception e) {
            LOGGER.error("Error clearing GraphQL WebSocket execution context", e);
        }
    }

    private void subscribe(GraphQLMessage message) {
        final Map<String, Object> payload = message.getPayload();

        try {
            securityService.setCurrentSubject(subject);
            executionContextManager.setCurrentContext(executionContext);

            Map<String, Object> variables = (Map<String, Object>) payload.get("variables");
            if (variables == null) {
                variables = new HashMap<>();
            }

            ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                    .query((String) payload.get("query"))
                    .variables(variables)
                    .operationName((String) payload.get("operationName"))
                    .context(serviceManager)
                    .build();

            ExecutionResult executionResult = this.graphQL.execute(executionInput);
            if (executionResult.getErrors() != null && !executionResult.getErrors().isEmpty()) {
                sendMessage(GraphQLMessage.create(message.getId())
                        .errors(executionResult.getErrors())
                        .build());
                closeConnection(message, "Error executing graphQL query");
                return;
            } else if (!(executionResult.getData() instanceof Publisher)) {
                Object data = executionResult.getData();
                final String error = "Fetched value should be instance of Publisher, was: " + (data == null ? "null" : data.getClass().getName());
                sendMessage(GraphQLMessage.create(message.getId())
                        .errors(Collections.singletonList(error))
                        .build());
                closeConnection(message, error);
                return;
            }

            Publisher<ExecutionResult> publisher = executionResult.getData();
            ExecutionResultSubscriber subscriber = new ExecutionResultSubscriber(message.getId(), getRemote());
            publisher.subscribe(subscriber);

            subscriptions.put(message.getId(), subscriber);
        } finally {
            try {
                securityService.clearCurrentSubject();
                executionContextManager.setCurrentContext(null);
            } catch (Exception e) {
                LOGGER.error("Error clearing GraphQL WebSocket security context", e);
            }
        }
    }
}
