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

package org.apache.unomi.graphql.servlet;

import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.graphql.servlet.auth.GraphQLServletSecurityValidator;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for GraphQLServlet WebSocket upgrade ordering: authenticate first,
 * acceptWebSocket second, never fall through to HTTP GraphQL, always clear thread-locals.
 */
@ExtendWith(MockitoExtension.class)
class GraphQLServletTest {

    @Mock
    private WebSocketServletFactory factory;
    @Mock
    private GraphQLServletSecurityValidator validator;
    @Mock
    private SecurityService securityService;
    @Mock
    private ExecutionContextManager executionContextManager;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private static final String BASIC_AUTH =
            "Basic " + java.util.Base64.getEncoder().encodeToString("user:pass".getBytes());

    private TrackingGraphQLServlet servlet;

    @BeforeEach
    void setUp() {
        servlet = new TrackingGraphQLServlet();
        servlet.bindForTests(factory, validator, securityService, executionContextManager);
    }

    @Test
    void service_nonUpgrade_usesHttpPath_andNeverAcceptsWebSocket() throws Exception {
        when(factory.isUpgradeRequest(request, response)).thenReturn(false);

        servlet.service(request, response);

        assertTrue(servlet.nonUpgradeCalled.get());
        verify(validator, never()).validateWebSocketUpgrade(any(), any());
        verify(factory, never()).acceptWebSocket(any(), any());
        verifyNoInteractions(securityService);
    }

    /** A WebSocket handshake bypasses CORS, so a foreign origin must be refused outright. */
    @Test
    void service_upgrade_crossOrigin_isRefused() throws Exception {
        when(factory.isUpgradeRequest(request, response)).thenReturn(true);
        when(request.getHeader("Origin")).thenReturn("https://evil.example.com");
        when(request.getServerName()).thenReturn("unomi.example.com");

        servlet.service(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(factory, never()).acceptWebSocket(any(), any());
        verify(validator, never()).validateWebSocketUpgrade(any(), any());
    }

    /** Without a credential the upgrade proceeds unauthenticated; the socket then gates on connection_init. */
    @Test
    void service_upgrade_withoutCredential_upgradesUnauthenticated() throws Exception {
        when(factory.isUpgradeRequest(request, response)).thenReturn(true);
        when(request.getHeader("Origin")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getHeaders("Sec-WebSocket-Protocol")).thenReturn(Collections.emptyEnumeration());
        when(factory.acceptWebSocket(request, response)).thenReturn(true);

        servlet.service(request, response);

        verify(validator, never()).validateWebSocketUpgrade(any(), any());
        verify(factory).acceptWebSocket(request, response);
        assertFalse(servlet.nonUpgradeCalled.get());
    }

    @Test
    void service_upgrade_authRejected_doesNotAccept_clearsContext() throws Exception {
        when(factory.isUpgradeRequest(request, response)).thenReturn(true);
        // No Origin: a non-browser client, which the upgrade accepts (it cannot be driven by a page).
        when(request.getHeader("Origin")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn(BASIC_AUTH);
        when(validator.validateWebSocketUpgrade(request, response)).thenReturn(false);

        servlet.service(request, response);

        assertFalse(servlet.nonUpgradeCalled.get());
        verify(factory, never()).acceptWebSocket(any(), any());
        verify(securityService).clearCurrentSubject();
        verify(executionContextManager).setCurrentContext(null);
        verify(response, never()).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
    }

    @Test
    void service_upgrade_authAccepted_acceptSucceeds_clearsContext() throws Exception {
        when(factory.isUpgradeRequest(request, response)).thenReturn(true);
        // No Origin: a non-browser client, which the upgrade accepts (it cannot be driven by a page).
        when(request.getHeader("Origin")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn(BASIC_AUTH);
        when(validator.validateWebSocketUpgrade(request, response)).thenReturn(true);
        when(request.getHeaders("Sec-WebSocket-Protocol")).thenReturn(Collections.emptyEnumeration());
        when(factory.acceptWebSocket(request, response)).thenReturn(true);

        servlet.service(request, response);

        assertFalse(servlet.nonUpgradeCalled.get());
        InOrder order = inOrder(factory, validator, securityService, executionContextManager);
        order.verify(factory).isUpgradeRequest(request, response);
        order.verify(validator).validateWebSocketUpgrade(request, response);
        order.verify(factory).acceptWebSocket(request, response);
        order.verify(securityService).clearCurrentSubject();
        order.verify(executionContextManager).setCurrentContext(null);
        verify(response, never()).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
    }

    @Test
    void service_upgrade_acceptFailsUncommitted_sends400_neverFallsThroughToHttp() throws Exception {
        when(factory.isUpgradeRequest(request, response)).thenReturn(true);
        // No Origin: a non-browser client, which the upgrade accepts (it cannot be driven by a page).
        when(request.getHeader("Origin")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn(BASIC_AUTH);
        when(validator.validateWebSocketUpgrade(request, response)).thenReturn(true);
        when(request.getHeaders("Sec-WebSocket-Protocol")).thenReturn(Collections.emptyEnumeration());
        when(factory.acceptWebSocket(request, response)).thenReturn(false);
        when(response.isCommitted()).thenReturn(false);

        servlet.service(request, response);

        assertFalse(servlet.nonUpgradeCalled.get(), "Must not fall through to HTTP GraphQL after failed accept");
        verify(response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid WebSocket upgrade");
        verify(securityService).clearCurrentSubject();
        verify(executionContextManager).setCurrentContext(null);
    }

    @Test
    void service_upgrade_acceptFailsCommitted_doesNotSendErrorAgain() throws Exception {
        when(factory.isUpgradeRequest(request, response)).thenReturn(true);
        // No Origin: a non-browser client, which the upgrade accepts (it cannot be driven by a page).
        when(request.getHeader("Origin")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn(BASIC_AUTH);
        when(validator.validateWebSocketUpgrade(request, response)).thenReturn(true);
        when(request.getHeaders("Sec-WebSocket-Protocol")).thenReturn(Collections.emptyEnumeration());
        when(factory.acceptWebSocket(request, response)).thenReturn(false);
        when(response.isCommitted()).thenReturn(true);

        servlet.service(request, response);

        assertFalse(servlet.nonUpgradeCalled.get());
        verify(response, never()).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
        verify(securityService).clearCurrentSubject();
    }

    @Test
    void service_upgrade_setsGraphqlSubprotocolHeader() throws Exception {
        when(request.getHeaders("Sec-WebSocket-Protocol"))
                .thenReturn(enumerationOf("graphql-ws, other"));
        when(factory.isUpgradeRequest(request, response)).thenReturn(true);
        // No Origin: a non-browser client, which the upgrade accepts (it cannot be driven by a page).
        when(request.getHeader("Origin")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn(BASIC_AUTH);
        when(validator.validateWebSocketUpgrade(request, response)).thenReturn(true);
        when(factory.acceptWebSocket(request, response)).thenReturn(true);

        servlet.service(request, response);

        verify(response).addHeader("Sec-WebSocket-Protocol", "graphql-ws");
    }

    @Test
    void negotiateGraphqlSubProtocol_selectsFirstGraphqlToken() {
        when(request.getHeaders("Sec-WebSocket-Protocol"))
                .thenReturn(enumerationOf("chat", "graphql-transport-ws, foo"));

        GraphQLServlet.negotiateGraphqlSubProtocol(request, response);

        verify(response).addHeader("Sec-WebSocket-Protocol", "graphql-transport-ws");
    }

    @Test
    void service_upgrade_clearsContextEvenWhenAcceptThrows() throws Exception {
        when(factory.isUpgradeRequest(request, response)).thenReturn(true);
        // No Origin: a non-browser client, which the upgrade accepts (it cannot be driven by a page).
        when(request.getHeader("Origin")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn(BASIC_AUTH);
        when(validator.validateWebSocketUpgrade(request, response)).thenReturn(true);
        when(request.getHeaders("Sec-WebSocket-Protocol")).thenReturn(Collections.emptyEnumeration());
        when(factory.acceptWebSocket(request, response)).thenThrow(new IOException("boom"));

        try {
            servlet.service(request, response);
            fail("Expected IOException");
        } catch (IOException expected) {
            // expected
        }

        assertFalse(servlet.nonUpgradeCalled.get());
        verify(securityService).clearCurrentSubject();
        verify(executionContextManager).setCurrentContext(null);
    }

    private static Enumeration<String> enumerationOf(String... values) {
        return Collections.enumeration(java.util.Arrays.asList(values));
    }

    /**
     * Overrides HTTP fall-through so tests can assert upgrade failures never reach it.
     */
    private static final class TrackingGraphQLServlet extends GraphQLServlet {
        private final AtomicBoolean nonUpgradeCalled = new AtomicBoolean(false);

        @Override
        void serviceNonUpgrade(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            nonUpgradeCalled.set(true);
        }
    }
}
