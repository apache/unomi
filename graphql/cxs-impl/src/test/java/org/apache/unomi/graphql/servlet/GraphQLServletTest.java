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
import graphql.GraphQL;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import org.apache.unomi.graphql.limits.GraphQLLimitsConfig;
import org.apache.unomi.graphql.servlet.auth.GraphQLServletSecurityValidator;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.apache.unomi.graphql.schema.GraphQLSchemaUpdater;
import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.contains;
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
    /** A listed admin UI origin gets credentialed CORS, named explicitly and varying on Origin. */
    @Test
    void cors_listedOrigin_getsCredentials() throws Exception {
        servlet.setAdminCorsService("https://admin.example"::equals);
        when(request.getHeader("Origin")).thenReturn("https://admin.example");

        servlet.doOptions(request, response);

        verify(response).setHeader("Access-Control-Allow-Origin", "https://admin.example");
        verify(response).setHeader("Access-Control-Allow-Credentials", "true");
        verify(response).setHeader(eq("Access-Control-Allow-Headers"), contains("Authorization"));
        verify(response).addHeader("Vary", "Origin");
    }

    /** An unlisted origin gets no CORS grant at all: the browser then refuses to expose the response. */
    @Test
    void cors_unlistedOrigin_getsNoCorsHeaders() throws Exception {
        servlet.setAdminCorsService("https://admin.example"::equals);
        when(request.getHeader("Origin")).thenReturn("https://evil.example.com");

        servlet.doOptions(request, response);

        verify(response, never()).setHeader(anyString(), anyString());
        verify(response, never()).addHeader(anyString(), anyString());
    }

    /** No Origin (same-origin GET or a non-browser client): no CORS headers are needed. */
    @Test
    void cors_noOrigin_getsNoCorsHeaders() throws Exception {
        servlet.setAdminCorsService(origin -> true);
        when(request.getHeader("Origin")).thenReturn(null);

        servlet.doOptions(request, response);

        verify(response, never()).setHeader(anyString(), anyString());
        verify(response, never()).addHeader(anyString(), anyString());
    }

    /** Before 4.0 any origin was echoed with credentials; the default empty list must no longer do that. */
    @Test
    void cors_emptyList_neverEchoesOrigin() throws Exception {
        servlet.setAdminCorsService(origin -> false);
        when(request.getHeader("Origin")).thenReturn("https://admin.example");

        servlet.doOptions(request, response);

        verify(response, never()).setHeader(eq("Access-Control-Allow-Origin"), anyString());
        verify(response, never()).setHeader(eq("Access-Control-Allow-Credentials"), anyString());
    }

    private GraphQLLimitsConfig bodyLimit(int maxBytes) {
        GraphQLLimitsConfig limits = mock(GraphQLLimitsConfig.class);
        when(limits.getMaxRequestBodyBytes()).thenReturn(maxBytes);
        return limits;
    }

    /** A declared Content-Length over the limit is refused before the body is read. */
    @Test
    void doPost_declaredOversizedBody_is413WithoutReading() throws Exception {
        servlet.setLimitsConfig(bodyLimit(100));
        when(request.getContentType()).thenReturn("application/json");
        when(request.getContentLength()).thenReturn(101);

        servlet.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE), anyString());
        verify(request, never()).getInputStream();
        verifyNoInteractions(validator);
    }

    /**
     * A chunked body declares no Content-Length; it is still refused once it goes over the limit, without
     * reading the rest of it and before authentication.
     */
    @Test
    void doPost_chunkedOversizedBody_is413AndStopsReading() throws Exception {
        servlet.setLimitsConfig(bodyLimit(1024));
        when(request.getContentType()).thenReturn("application/json");
        when(request.getContentLength()).thenReturn(-1);
        byte[] body = ("{\"query\":\"" + "a".repeat(1024 * 1024) + "\"}").getBytes(StandardCharsets.UTF_8);
        BodyStream stream = new BodyStream(body);
        when(request.getInputStream()).thenReturn(stream);

        servlet.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE), anyString());
        verifyNoInteractions(validator);
        assertTrue(stream.remaining() > body.length / 2, "the body must not be read to the end");
    }

    /** A chunked body within the limit is parsed and handed to authentication as before. */
    @Test
    void doPost_chunkedBodyWithinLimit_reachesValidator() throws Exception {
        servlet.setLimitsConfig(bodyLimit(1024));
        when(request.getContentType()).thenReturn("application/json");
        when(request.getContentLength()).thenReturn(-1);
        when(request.getInputStream()).thenReturn(
                new BodyStream("{\"query\":\"{ __typename }\"}".getBytes(StandardCharsets.UTF_8)));
        when(validator.validate(eq("{ __typename }"), isNull(), eq(request), eq(response))).thenReturn(false);

        servlet.doPost(request, response);

        verify(validator).validate(eq("{ __typename }"), isNull(), eq(request), eq(response));
        verify(response, never()).sendError(eq(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE), anyString());
    }

    // --- HTTP method and content type rules -------------------------------------------------------

    private static final String QUERY = "{ ping }";
    private static final String MUTATION = "mutation { touch }";

    /** A real graphql-java instance behind the schema updater: queries answer "pong", the mutation "touched". */
    private StringWriter givenExecutableSchema() throws IOException {
        GraphQL graphQL = GraphQL.newGraphQL(new SchemaGenerator().makeExecutableSchema(
                new SchemaParser().parse("type Query { ping: String, profile(createIfMissing: Boolean): String }"
                        + " type Mutation { touch: String }"),
                RuntimeWiring.newRuntimeWiring()
                        .type("Query", t -> t.dataFetcher("ping", env -> "pong").dataFetcher("profile", env -> "loaded"))
                        .type("Mutation", t -> t.dataFetcher("touch", env -> "touched"))
                        .build())).build();
        GraphQLSchemaUpdater updater = mock(GraphQLSchemaUpdater.class);
        when(updater.getGraphQL()).thenReturn(graphQL);
        servlet.setGraphQLSchemaUpdater(updater);
        StringWriter out = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(out));
        return out;
    }

    private void givenGet(String query) {
        when(request.getParameter("query")).thenReturn(query);
    }

    /** An authenticated GET query still runs. */
    @Test
    void doGet_query_runs() throws Exception {
        StringWriter out = givenExecutableSchema();
        givenGet(QUERY);
        when(validator.validate(eq(QUERY), isNull(), eq(request), eq(response))).thenReturn(true);

        servlet.doGet(request, response);

        assertTrue(out.toString().contains("pong"), out.toString());
        verify(response, never()).sendError(eq(HttpServletResponse.SC_METHOD_NOT_ALLOWED), anyString());
    }

    /** A mutation over GET is refused with 405 and Allow: POST, and never executed. */
    @Test
    void doGet_mutation_is405AndNeverRuns() throws Exception {
        GraphQLSchemaUpdater updater = mock(GraphQLSchemaUpdater.class);
        servlet.setGraphQLSchemaUpdater(updater);
        givenGet(MUTATION);
        when(validator.validate(eq(MUTATION), isNull(), eq(request), eq(response))).thenReturn(true);

        servlet.doGet(request, response);

        verify(response).setHeader("Allow", "POST");
        verify(response).sendError(eq(HttpServletResponse.SC_METHOD_NOT_ALLOWED), anyString());
        verifyNoInteractions(updater);
    }

    /** A query document whose selected operation is a mutation is refused too. */
    @Test
    void doGet_mutationSelectedByOperationName_is405() throws Exception {
        String document = "query readIt { ping } mutation writeIt { touch }";
        givenGet(document);
        when(request.getParameter("operationName")).thenReturn("writeIt");
        when(validator.validate(eq(document), eq("writeIt"), eq(request), eq(response))).thenReturn(true);

        servlet.doGet(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_METHOD_NOT_ALLOWED), anyString());
    }

    /**
     * Authentication (with its size limit) runs before the document is parsed for the method check:
     * an unauthenticated GET mutation gets the validator's answer, not 405.
     */
    @Test
    void doGet_unauthenticatedMutation_isRejectedByValidatorFirst() throws Exception {
        givenGet(MUTATION);
        when(validator.validate(eq(MUTATION), isNull(), eq(request), eq(response))).thenReturn(false);

        servlet.doGet(request, response);

        verify(validator).validate(eq(MUTATION), isNull(), eq(request), eq(response));
        verify(response, never()).sendError(eq(HttpServletResponse.SC_METHOD_NOT_ALLOWED), anyString());
        verify(response, never()).setHeader(eq("Allow"), anyString());
    }

    /** getProfile(createIfMissing: true) saves a profile: refused over GET like a mutation, never executed. */
    @Test
    void doGet_createIfMissingTrue_is405AndNeverRuns() throws Exception {
        GraphQLSchemaUpdater updater = mock(GraphQLSchemaUpdater.class);
        servlet.setGraphQLSchemaUpdater(updater);
        String query = "{ profile(createIfMissing: true) }";
        givenGet(query);
        when(validator.validate(eq(query), isNull(), eq(request), eq(response))).thenReturn(true);

        servlet.doGet(request, response);

        verify(response).setHeader("Allow", "POST");
        verify(response).sendError(eq(HttpServletResponse.SC_METHOD_NOT_ALLOWED), anyString());
        verifyNoInteractions(updater);
    }

    /** The same through a GET variables parameter. */
    @Test
    void doGet_createIfMissingTrueThroughVariables_is405() throws Exception {
        String query = "query q($c: Boolean) { profile(createIfMissing: $c) }";
        givenGet(query);
        when(request.getParameter("operationName")).thenReturn(null);
        when(request.getParameter("variables")).thenReturn("{\"c\": true}");
        when(validator.validate(eq(query), isNull(), eq(request), eq(response))).thenReturn(true);

        servlet.doGet(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_METHOD_NOT_ALLOWED), anyString());
    }

    /** Reading a profile without creating it still works over GET. */
    @Test
    void doGet_createIfMissingFalse_runs() throws Exception {
        StringWriter out = givenExecutableSchema();
        String query = "{ profile(createIfMissing: false) }";
        givenGet(query);
        when(validator.validate(eq(query), isNull(), eq(request), eq(response))).thenReturn(true);

        servlet.doGet(request, response);

        assertTrue(out.toString().contains("loaded"), out.toString());
        verify(response, never()).sendError(eq(HttpServletResponse.SC_METHOD_NOT_ALLOWED), anyString());
    }

    /** A cross-site form can post text/plain without a preflight: refused before the body is read. */
    @Test
    void doPost_textPlain_is415WithoutReading() throws Exception {
        when(request.getContentType()).thenReturn("text/plain");

        servlet.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE), anyString());
        verify(request, never()).getInputStream();
        verifyNoInteractions(validator);
    }

    @Test
    void doPost_formEncoded_is415() throws Exception {
        when(request.getContentType()).thenReturn("application/x-www-form-urlencoded");

        servlet.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE), anyString());
        verifyNoInteractions(validator);
    }

    @Test
    void doPost_missingContentType_is415() throws Exception {
        when(request.getContentType()).thenReturn(null);

        servlet.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE), anyString());
        verifyNoInteractions(validator);
    }

    /** Mutations still run over POST with JSON; a charset parameter and any letter case are accepted. */
    @Test
    void doPost_jsonWithCharset_runsMutation() throws Exception {
        StringWriter out = givenExecutableSchema();
        when(request.getContentType()).thenReturn("Application/JSON; charset=UTF-8");
        when(request.getContentLength()).thenReturn(-1);
        when(request.getInputStream()).thenReturn(
                new BodyStream(("{\"query\":\"" + MUTATION + "\"}").getBytes(StandardCharsets.UTF_8)));
        when(validator.validate(eq(MUTATION), isNull(), eq(request), eq(response))).thenReturn(true);

        servlet.doPost(request, response);

        assertTrue(out.toString().contains("touched"), out.toString());
        verify(response, never()).sendError(eq(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE), anyString());
    }

    /** A request body as the container hands it over. */
    private static final class BodyStream extends ServletInputStream {
        private final ByteArrayInputStream in;

        BodyStream(byte[] body) {
            this.in = new ByteArrayInputStream(body);
        }

        int remaining() {
            return in.available();
        }

        @Override
        public int read() {
            return in.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return in.read(b, off, len);
        }

        public boolean isFinished() {
            return in.available() == 0;
        }

        public boolean isReady() {
            return true;
        }

        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class TrackingGraphQLServlet extends GraphQLServlet {
        private final AtomicBoolean nonUpgradeCalled = new AtomicBoolean(false);

        @Override
        void serviceNonUpgrade(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            nonUpgradeCalled.set(true);
        }
    }

    /**
     * The creator is only ever Jetty's WebSocketCreator, never a started lifecycle, so its scheduler is
     * stopped from destroy(). This pins the wiring itself, not just that shutdown() works in isolation.
     */
    @Test
    void destroy_shutsDownTheSocketCreatorScheduler() {
        servlet.setGraphQLSchemaUpdater(mock(GraphQLSchemaUpdater.class));
        when(factory.getPolicy()).thenReturn(mock(WebSocketPolicy.class));
        servlet.configure(factory);
        assertNotNull(servlet.socketCreator());
        assertFalse(servlet.socketCreator().isShutdown());

        servlet.destroy();

        assertTrue(servlet.socketCreator().isShutdown());
    }
}
