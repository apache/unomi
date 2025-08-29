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
        super.init(config);
        this.validator = new GraphQLServletSecurityValidator();
    }

    private WebSocketServletFactory factory;

    @Override
    public void configure(WebSocketServletFactory factory) {
        LOGGER.debug("GraphQLServlet configured");
        this.factory = factory;
        factory.setCreator(new SubscriptionWebSocketFactory(graphQLSchemaUpdater.getGraphQL(), serviceManager));
        factory.getPolicy().setMaxTextMessageBufferSize(1024 * 1024);
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        LOGGER.debug("GraphQLServlet service called with request: {}", request.getRequestURI());
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
