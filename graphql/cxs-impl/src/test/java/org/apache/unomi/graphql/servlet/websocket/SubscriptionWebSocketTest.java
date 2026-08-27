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
import graphql.GraphQLError;
import io.reactivex.Flowable;
import org.apache.unomi.api.ExecutionContext;
import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.servlet.auth.GraphQLServletSecurityValidator;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Publisher;

import javax.security.auth.Subject;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for authenticated subscription execution:
 * subject/context must be bound for {@code graphQL.execute}, null/omitted
 * {@code variables} must not NPE inside GraphQL, and thread-locals must be cleared afterward.
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionWebSocketTest {

    @Mock
    private GraphQL graphQL;
    @Mock
    private ServiceManager serviceManager;
    @Mock
    private SecurityService securityService;
    @Mock
    private ExecutionContextManager executionContextManager;
    @Mock
    private Session session;
    @Mock
    private RemoteEndpoint remote;
    @Mock
    private ExecutionResult executionResult;
    @Mock
    private GraphQLError graphQLError;

    private final Subject subject = new Subject();
    private final ExecutionContext executionContext = new ExecutionContext("test-tenant", null, null);

    @Mock
    private GraphQLServletSecurityValidator validator;

    private SubscriptionWebSocket socket;

    @BeforeEach
    void setUp() {
        socket = new SubscriptionWebSocket(graphQL, serviceManager, subject, executionContext,
                securityService, executionContextManager, validator);
        when(session.getRemote()).thenReturn(remote);
        socket.onWebSocketConnect(session);
    }

    /** An unauthenticated socket (the browser path) must not execute anything before it authenticates. */
    @Test
    void unauthenticated_startIsRefusedAndSocketClosed() {
        SubscriptionWebSocket unauth = new SubscriptionWebSocket(graphQL, serviceManager, null, null,
                securityService, executionContextManager, validator);
        unauth.onWebSocketConnect(session);

        unauth.onWebSocketText(startMessage("\"variables\":null"));

        verifyNoInteractions(graphQL);
        verify(session).close(anyInt(), anyString());
    }

    @Test
    void unauthenticated_connectionInitWithoutCredential_isRefused() {
        SubscriptionWebSocket unauth = new SubscriptionWebSocket(graphQL, serviceManager, null, null,
                securityService, executionContextManager, validator);
        unauth.onWebSocketConnect(session);

        unauth.onWebSocketText("{\"type\":\"connection_init\",\"id\":\"1\"}");

        verify(validator, never()).authenticateBasicCredential(anyString());
        verify(session).close(anyInt(), anyString());
    }

    @Test
    void unauthenticated_connectionInitWithBadCredential_isRefused() {
        when(validator.authenticateBasicCredential(anyString())).thenReturn(false);
        SubscriptionWebSocket unauth = new SubscriptionWebSocket(graphQL, serviceManager, null, null,
                securityService, executionContextManager, validator);
        unauth.onWebSocketConnect(session);

        unauth.onWebSocketText("{\"type\":\"connection_init\",\"id\":\"1\","
                + "\"payload\":{\"Authorization\":\"Basic Ym9ndXM6Ym9ndXM=\"}}");

        verify(session).close(anyInt(), anyString());
    }

    /** A valid connection_init credential authenticates the socket and lifts the unauthenticated deadline. */
    @Test
    void unauthenticated_connectionInitWithValidCredential_authenticatesSocket() {
        when(validator.authenticateBasicCredential(anyString())).thenReturn(true);
        when(securityService.getCurrentSubject()).thenReturn(subject);
        when(executionContextManager.getCurrentContext()).thenReturn(executionContext);
        SubscriptionWebSocket unauth = new SubscriptionWebSocket(graphQL, serviceManager, null, null,
                securityService, executionContextManager, validator);
        unauth.onWebSocketConnect(session);

        unauth.onWebSocketText("{\"type\":\"connection_init\",\"id\":\"1\","
                + "\"payload\":{\"Authorization\":\"Basic dXNlcjpwYXNz\"}}");

        // Identity captured onto the socket, and not left bound to this shared IO thread.
        verify(securityService).clearCurrentSubject();
        verify(session, never()).close(anyInt(), anyString());
        verify(session).setIdleTimeout(0);
    }

    @Test
    void subscribe_bindsSubjectThenClearsThreadLocals() {
        stubSuccessfulPublisherExecute();

        socket.onWebSocketText(startMessage("\"variables\":null"));

        InOrder order = inOrder(securityService, executionContextManager);
        order.verify(securityService).setCurrentSubject(subject);
        order.verify(executionContextManager).setCurrentContext(executionContext);
        order.verify(securityService).clearCurrentSubject();
        order.verify(executionContextManager).setCurrentContext(null);
    }

    @Test
    void subscribe_nullVariables_passesEmptyMapToExecute() {
        // Reporter used omitted/null variables to prove unauthenticated traffic reached
        // graphQL.execute (NPE: "variables map can't be null"). Authenticated path must
        // normalize null → empty map before execute.
        stubSuccessfulPublisherExecute();

        socket.onWebSocketText(startMessage("\"variables\":null"));

        ArgumentCaptor<ExecutionInput> input = ArgumentCaptor.forClass(ExecutionInput.class);
        verify(graphQL).execute(input.capture());
        Map<String, Object> variables = input.getValue().getVariables();
        assertNotNull(variables);
        assertTrue(variables.isEmpty());
    }

    @Test
    void subscribe_omittedVariables_passesEmptyMapToExecute() {
        stubSuccessfulPublisherExecute();

        socket.onWebSocketText("{"
                + "\"id\":\"1\","
                + "\"type\":\"start\","
                + "\"payload\":{"
                + "\"query\":\"subscription { eventListener { id } }\""
                + "}}");

        ArgumentCaptor<ExecutionInput> input = ArgumentCaptor.forClass(ExecutionInput.class);
        verify(graphQL).execute(input.capture());
        Map<String, Object> variables = input.getValue().getVariables();
        assertNotNull(variables);
        assertTrue(variables.isEmpty());
    }

    @Test
    void subscribe_clearsThreadLocalsEvenWhenExecutionFails() {
        when(executionResult.getErrors()).thenReturn(Collections.singletonList(graphQLError));
        when(graphQL.execute(any(ExecutionInput.class))).thenReturn(executionResult);

        socket.onWebSocketText(startMessage(null));

        verify(securityService).setCurrentSubject(subject);
        verify(executionContextManager).setCurrentContext(executionContext);
        verify(securityService).clearCurrentSubject();
        verify(executionContextManager).setCurrentContext(null);
        verify(session).close(anyInt(), anyString());
    }

    private void stubSuccessfulPublisherExecute() {
        Publisher<ExecutionResult> publisher = Flowable.never();
        when(executionResult.getErrors()).thenReturn(Collections.emptyList());
        when(executionResult.getData()).thenReturn(publisher);
        when(graphQL.execute(any(ExecutionInput.class))).thenReturn(executionResult);
    }

    private static String startMessage(String variablesField) {
        StringBuilder payload = new StringBuilder();
        payload.append("{\"id\":\"1\",\"type\":\"start\",\"payload\":{");
        payload.append("\"query\":\"subscription { eventListener { id } }\"");
        if (variablesField != null) {
            payload.append(',').append(variablesField);
            payload.append(",\"operationName\":null");
        }
        payload.append("}}");
        return payload.toString();
    }
}
