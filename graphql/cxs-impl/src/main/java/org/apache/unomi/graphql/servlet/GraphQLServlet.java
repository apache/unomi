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

import com.fasterxml.jackson.core.type.TypeReference;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.introspection.IntrospectionQuery;
import org.apache.unomi.graphql.schema.GraphQLSchemaUpdater;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.servlet.auth.GraphQLServletSecurityValidator;
import org.apache.unomi.graphql.servlet.websocket.SubscriptionWebSocketFactory;
import org.apache.unomi.graphql.utils.GraphQLObjectMapper;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

@Component(service = GraphQLServlet.class)
public class GraphQLServlet extends WebSocketServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphQLServlet.class);

    public static final String SCHEMA_URL = "/schema.json";

    private GraphQLSchemaUpdater graphQLSchemaUpdater;
    private ServiceManager serviceManager;
    private GraphQLServletSecurityValidator validator;

    @Reference
    public void setServiceManager(ServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }

    @Reference
    public void setGraphQLSchemaUpdater(GraphQLSchemaUpdater graphQLSchemaUpdater) {
        this.graphQLSchemaUpdater = graphQLSchemaUpdater;
    }

    public GraphQLServlet() {
        LOGGER.info("GraphQLServlet created");
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        LOGGER.debug("GraphQLServlet initialized");
        // Must precede super.init(): WebSocketServlet.init() calls configure(), which captures this
        // validator into the SubscriptionWebSocketFactory.
        this.validator = new GraphQLServletSecurityValidator();
        super.init(config);
    }

    private WebSocketServletFactory factory;

    private SubscriptionWebSocketFactory socketCreator;

    @Override
    public void destroy() {
        try {
            if (socketCreator != null) {
                socketCreator.shutdown();
            }
        } finally {
            super.destroy();
        }
    }

    @Override
    public void configure(WebSocketServletFactory factory) {
        LOGGER.debug("GraphQLServlet configured");
        this.factory = factory;
        this.socketCreator = new SubscriptionWebSocketFactory(graphQLSchemaUpdater.getGraphQL(), serviceManager, validator);
        factory.setCreator(socketCreator);
        factory.getPolicy().setMaxTextMessageBufferSize(1024 * 1024);
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        LOGGER.debug("GraphQLServlet service called with request: {}", request.getRequestURI());
        // HTTP GraphQL (GET/POST/OPTIONS): auth is enforced in doGet/doPost via validator.validate(...).
        if (!factory.isUpgradeRequest(request, response)) {
            super.service(request, response);
            return;
        }
        serviceWebSocketUpgrade(request, response);
    }

    /**
     * WebSocket upgrade path. Order matters for security:
     * <ol>
     *   <li>Refuse foreign-origin handshakes, then authenticate any credential the handshake carries
     *       BEFORE {@code acceptWebSocket}. A handshake that carries no credential is upgraded
     *       unauthenticated - a browser cannot set headers on it - and must authenticate through
     *       {@code connection_init} before the socket will do anything.</li>
     *   <li>Call {@code acceptWebSocket} directly - do not call {@code WebSocketServlet.service()},
     *       which can fall through to {@code doGet}/{@code doPost} when accept fails and the response
     *       is not committed.</li>
     * </ol>
     */
    private void serviceWebSocketUpgrade(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // A WebSocket handshake is not subject to the same-origin policy and triggers no CORS
        // preflight, so any page on any origin can open one against this endpoint. Refuse handshakes
        // that declare a foreign origin before doing anything else.
        if (!isOriginAllowed(request)) {
            LOGGER.warn("Refusing cross-origin WebSocket upgrade from origin {}", request.getHeader("Origin"));
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Cross-origin WebSocket upgrade refused");
            return;
        }

        // Credentials on the upgrade request are the strongest path: the socket is never created
        // unauthenticated. They stay mandatory for any client that can set request headers.
        if (request.getHeader("Authorization") != null && !validator.validateWebSocketUpgrade(request, response)) {
            return;
        }

        try {
            final ServletUpgradeRequest upReq = new ServletUpgradeRequest(request);
            for (String subProtocol : upReq.getSubProtocols()) {
                if (subProtocol.startsWith("graphql")) {
                    response.addHeader("Sec-WebSocket-Protocol", subProtocol);
                    break;
                }
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        if (!factory.acceptWebSocket(request, response) && !response.isCommitted()) {
            // Upgrade was intended but rejected; never fall through to HTTP GraphQL.
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid WebSocket upgrade");
        }
    }

    /**
     * Accepts a handshake that declares no origin (a non-browser client, which cannot be driven into
     * making the request by a hostile page) or one whose origin is this same host. Anything else is a
     * page on another origin trying to open a socket here, which is refused.
     */
    static boolean isOriginAllowed(HttpServletRequest request) {
        final String origin = request.getHeader("Origin");
        if (origin == null || origin.trim().isEmpty()) {
            return true;
        }
        try {
            final URI originUri = new URI(origin);
            final String originHost = originUri.getHost();
            if (originHost == null) {
                return false;
            }
            if (!originHost.equalsIgnoreCase(request.getServerName())) {
                return false;
            }
            int originPort = originUri.getPort();
            if (originPort == -1) {
                originPort = "https".equalsIgnoreCase(originUri.getScheme()) ? 443 : 80;
            }
            return originPort == request.getServerPort();
        } catch (URISyntaxException e) {
            LOGGER.debug("Refusing WebSocket upgrade with unparseable Origin", e);
            return false;
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        LOGGER.debug("GraphQLServlet doGet called with request: {}", req.getRequestURI());
        String query = req.getParameter("query");
        if (SCHEMA_URL.equals(req.getPathInfo())) {
            query = IntrospectionQuery.INTROSPECTION_QUERY;
        }
        String operationName = req.getParameter("operationName");
        String variableStr = req.getParameter("variables");
        Map<String, Object> variables = new HashMap<>();
        if ((variableStr != null) && (variableStr.trim().length() > 0)) {
            TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() {
            };
            variables = GraphQLObjectMapper.getInstance().readValue(variableStr, typeRef);
        }

        if (!validator.validate(query, operationName, req, resp)) {
            return;
        }
        setupCORSHeaders(req, resp);
        executeGraphQLRequest(resp, query, operationName, variables);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        LOGGER.debug("GraphQLServlet doPost called with request: {}", req.getRequestURI());
        TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() {};
        Map<String, Object> body = GraphQLObjectMapper.getInstance().readValue(req.getInputStream(), typeRef);

        String query = (String) body.get("query");
        String operationName = (String) body.get("operationName");
        Map<String, Object> variables = (Map<String, Object>) body.get("variables");

        if (variables == null) {
            variables = new HashMap<>();
        }

        if (!validator.validate(query, operationName, req, resp)) {
            return;
        }
        setupCORSHeaders(req, resp);
        executeGraphQLRequest(resp, query, operationName, variables);
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        LOGGER.debug("GraphQLServlet doOptions called with request: {}", req.getRequestURI());
        setupCORSHeaders(req, resp);
        resp.flushBuffer();
    }

    private void executeGraphQLRequest(
            HttpServletResponse resp, String query, String operationName, Map<String, Object> variables) throws IOException {
        LOGGER.debug("Executing GraphQL request with query: {}, operationName: {}, variables: {}", query, operationName, variables);
        if (query == null || query.trim().length() == 0) {
            throw new IllegalArgumentException("Query cannot be empty or null");
        }

        final ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .variables(variables)
                .operationName(operationName)
                .context(serviceManager)
                .build();

        final ExecutionResult executionResult = graphQLSchemaUpdater.getGraphQL().execute(executionInput);

        final Map<String, Object> specificationResult = executionResult.toSpecification();

        GraphQLObjectMapper.getInstance().writeValue(resp.getWriter(), specificationResult);
    }

    private void setupCORSHeaders(HttpServletRequest httpServletRequest, ServletResponse response) {
        if (!(response instanceof HttpServletResponse)) {
            return;
        }

        HttpServletResponse httpServletResponse = (HttpServletResponse) response;

        httpServletResponse.setHeader("Access-Control-Allow-Origin", getOriginHeaderFromRequest(httpServletRequest));
        httpServletResponse.setHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, X-Apollo-Tracing");
        httpServletResponse.setHeader("Access-Control-Allow-Credentials", "true");
        httpServletResponse.setHeader("Access-Control-Allow-Methods", "OPTIONS, POST, GET");
    }

    private String getOriginHeaderFromRequest(final HttpServletRequest httpServletRequest) {
        return httpServletRequest != null && httpServletRequest.getHeader("Origin") != null
                ? httpServletRequest.getHeader("Origin")
                : "*";
    }

}
