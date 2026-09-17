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
import graphql.GraphQL;
import graphql.introspection.IntrospectionQuery;
import org.apache.unomi.api.ExecutionContext;
import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.graphql.schema.GraphQLSchemaUpdater;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.servlet.auth.GraphQLServletSecurityValidator;
import org.apache.unomi.graphql.servlet.websocket.SubscriptionWebSocketFactory;
import org.apache.unomi.graphql.utils.GraphQLObjectMapper;
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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Component(service = GraphQLServlet.class)
public class GraphQLServlet extends WebSocketServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphQLServlet.class);

    public static final String SCHEMA_URL = "/schema.json";

    private GraphQLSchemaUpdater graphQLSchemaUpdater;
    private ServiceManager serviceManager;

    private TenantService tenantService;

    private ExecutionContextManager executionContextManager;

    private SecurityService securityService;

    private GraphQLServletSecurityValidator validator;

    @Reference
    public void setServiceManager(ServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }

    @Reference
    public void setGraphQLSchemaUpdater(GraphQLSchemaUpdater graphQLSchemaUpdater) {
        this.graphQLSchemaUpdater = graphQLSchemaUpdater;
    }

    @Reference
    public void setTenantService(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @Reference
    public void setExecutionContextManager(ExecutionContextManager executionContextManager) {
        this.executionContextManager = executionContextManager;
    }

    @Reference
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    public GraphQLServlet() {
        LOGGER.info("GraphQLServlet created");
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        LOGGER.debug("GraphQLServlet initialized");
        // Must precede super.init(): WebSocketServlet.init() calls configure(), which captures this
        // validator into the SubscriptionWebSocketFactory. Constructing it afterwards left the factory -
        // and therefore every socket - with a null validator, so connection_init could never authenticate.
        this.validator = new GraphQLServletSecurityValidator(tenantService, securityService, executionContextManager);
        super.init(config);
    }

    private WebSocketServletFactory factory;

    private SubscriptionWebSocketFactory socketCreator;

    /** For tests: the creator whose scheduler {@link #destroy()} must stop. */
    SubscriptionWebSocketFactory socketCreator() {
        return socketCreator;
    }

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
        // Wrap the WebSocket creator to bind the authenticated subject established during upgrade
        this.socketCreator = new SubscriptionWebSocketFactory(
                graphQLSchemaUpdater.getGraphQL(), serviceManager, securityService, executionContextManager, validator);
        factory.setCreator((req, resp) -> {
            try {
                return socketCreator.createWebSocket(req, resp);
            } finally {
                cleanupSecurityContext();
            }
        });
        factory.getPolicy().setMaxTextMessageBufferSize(1024 * 1024);
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        LOGGER.debug("GraphQLServlet service called with request: {}", request.getRequestURI());

        // HTTP GraphQL (GET/POST/OPTIONS): auth is enforced in doGet/doPost via validator.validate(...).
        if (!factory.isUpgradeRequest(request, response)) {
            serviceNonUpgrade(request, response);
            return;
        }

        serviceWebSocketUpgrade(request, response);
    }

    /**
     * HTTP GraphQL path. Separated so unit tests can assert upgrade handling never falls through here.
     */
    void serviceNonUpgrade(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        super.service(request, response);
    }

    /**
     * WebSocket upgrade path. Order matters for security:
     * <ol>
     *   <li>Refuse foreign-origin handshakes, then authenticate any credential the handshake carries
     *       BEFORE {@code acceptWebSocket} (creator reads the thread-local subject). A handshake that
     *       carries no credential is upgraded unauthenticated - a browser cannot set headers on it - and
     *       must authenticate through {@code connection_init} before the socket will do anything.</li>
     *   <li>Call {@code acceptWebSocket} directly — do not call {@code WebSocketServlet.service()},
     *       which can fall through to {@code doGet}/{@code doPost} when accept fails and the response
     *       is not committed.</li>
     *   <li>Always clear thread-locals in {@code finally} (covers accept failures before the creator runs).</li>
     * </ol>
     */
    void serviceWebSocketUpgrade(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
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
            //
            // A browser cannot set headers on a WebSocket handshake, so a request that carries none is
            // upgraded in an unauthenticated state instead of being refused. That socket can do nothing
            // until it authenticates through connection_init: SubscriptionWebSocket rejects every other
            // message until then, and closes the socket if credentials do not arrive promptly.
            if (request.getHeader("Authorization") != null && !validator.validateWebSocketUpgrade(request, response)) {
                return;
            }
            negotiateGraphqlSubProtocol(request, response);
            if (!factory.acceptWebSocket(request, response) && !response.isCommitted()) {
                // Upgrade was intended but rejected after auth; never fall through to HTTP GraphQL.
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid WebSocket upgrade");
            }
        } finally {
            cleanupSecurityContext();
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

    /**
     * Selects the first {@code graphql*} WebSocket subprotocol offered by the client.
     * Reads {@code Sec-WebSocket-Protocol} directly so upgrade auth tests do not need a full Jetty upgrade request.
     */
    static void negotiateGraphqlSubProtocol(HttpServletRequest request, HttpServletResponse response) {
        Enumeration<String> offered = request.getHeaders("Sec-WebSocket-Protocol");
        if (offered == null) {
            return;
        }
        while (offered.hasMoreElements()) {
            String headerValue = offered.nextElement();
            if (headerValue == null) {
                continue;
            }
            for (String part : headerValue.split(",")) {
                String subProtocol = part.trim();
                if (subProtocol.startsWith("graphql")) {
                    response.addHeader("Sec-WebSocket-Protocol", subProtocol);
                    return;
                }
            }
        }
    }

    /**
     * Package-private wiring for unit tests (avoids full Jetty/OSGi servlet init).
     */
    void bindForTests(WebSocketServletFactory factory,
                      GraphQLServletSecurityValidator validator,
                      SecurityService securityService,
                      ExecutionContextManager executionContextManager) {
        this.factory = factory;
        this.validator = validator;
        this.securityService = securityService;
        this.executionContextManager = executionContextManager;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        LOGGER.debug("GraphQLServlet doGet called with request: {}", req.getRequestURI());
        try {
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
        } finally {
            cleanupSecurityContext();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        LOGGER.debug("GraphQLServlet doPost called with request: {}", req.getRequestURI());
        try {
            TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() {
            };
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
        } finally {
            cleanupSecurityContext();
        }
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        LOGGER.debug("GraphQLServlet doOptions called with request: {}", req.getRequestURI());
        try {
            setupCORSHeaders(req, resp);
            resp.flushBuffer();
        } finally {
            cleanupSecurityContext();
        }
    }

    private void executeGraphQLRequest(
            HttpServletResponse resp, String query, String operationName, Map<String, Object> variables) throws IOException {
        LOGGER.debug("Executing GraphQL request with query: {}, operationName: {}, variables: {}", query, operationName, variables);
        if (query == null || query.trim().length() == 0) {
            throw new IllegalArgumentException("Query cannot be empty or null");
        }

        // Get the current tenant ID from the execution context
        String tenantId = executionContextManager.getCurrentContext() != null ?
            executionContextManager.getCurrentContext().getTenantId() : null;

        LOGGER.debug("Executing GraphQL request for tenant: {}", tenantId);

        // Get tenant-specific GraphQL instance or fall back to default
        final GraphQL graphQL = (tenantId != null)
                ? graphQLSchemaUpdater.getGraphQLForTenant(tenantId)
                : graphQLSchemaUpdater.getGraphQL();

        final ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .variables(variables)
                .operationName(operationName)
                .context(serviceManager)
                .build();

        final ExecutionResult executionResult = graphQL.execute(executionInput);

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

    private void cleanupSecurityContext() {
        try {
            securityService.clearCurrentSubject();
            executionContextManager.setCurrentContext(null);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Cleared security context after GraphQL request processing");
            }
        } catch (Exception e) {
            LOGGER.error("Error clearing GraphQL security context", e);
        }
    }

}
