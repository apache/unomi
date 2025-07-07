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
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

@Component(
        service = {javax.servlet.http.HttpServlet.class, javax.servlet.Servlet.class},
        property = {"alias=/graphql"}
)
public class GraphQLServlet extends WebSocketServlet {

    public static final String SCHEMA_URL = "/schema.json";

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphQLServlet.class);

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

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        this.validator = new GraphQLServletSecurityValidator(tenantService, securityService, executionContextManager);
    }

    private WebSocketServletFactory factory;

    @Override
    public void configure(WebSocketServletFactory factory) {
        this.factory = factory;
        // Wrap the WebSocket creator to handle security context for WebSocket connections
        SubscriptionWebSocketFactory originalCreator = new SubscriptionWebSocketFactory(graphQLSchemaUpdater.getGraphQL(), serviceManager);
        factory.setCreator((req, resp) -> {
            try {
                return originalCreator.createWebSocket(req, resp);
            } finally {
                cleanupSecurityContext();
            }
        });
        factory.getPolicy().setMaxTextMessageBufferSize(1024 * 1024);
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (factory.isUpgradeRequest(request, response)) {
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
        }
        super.service(request, response);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
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
        try {
            setupCORSHeaders(req, resp);
            resp.flushBuffer();
        } finally {
            cleanupSecurityContext();
        }
    }

    private void executeGraphQLRequest(
            HttpServletResponse resp, String query, String operationName, Map<String, Object> variables) throws IOException {
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
