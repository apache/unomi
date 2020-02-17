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
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.introspection.IntrospectionQuery;
import org.apache.unomi.graphql.GraphQLSchemaUpdater;
import org.apache.unomi.graphql.services.ServiceManager;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component(
        service = {javax.servlet.http.HttpServlet.class, javax.servlet.Servlet.class},
        property = {"alias=/cdpgraphql"}
)
public class GraphQLServlet extends HttpServlet {

    private ObjectMapper objectMapper;

    private GraphQLSchemaUpdater graphQLSchemaUpdater;

    private ServiceManager serviceManager;

    @Reference
    public void setServiceManager(ServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }

    @Reference
    public void setGraphQLSchemaUpdater(GraphQLSchemaUpdater graphQLSchemaUpdater) {
        this.graphQLSchemaUpdater = graphQLSchemaUpdater;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String query = req.getParameter("query");
        if ("/schema.json".equals(req.getPathInfo())) {
            query = IntrospectionQuery.INTROSPECTION_QUERY;
        }
        String operationName = req.getParameter("operationName");
        String variableStr = req.getParameter("variables");
        Map<String, Object> variables = new HashMap<>();
        if ((variableStr != null) && (variableStr.trim().length() > 0)) {
            TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() {
            };
            variables = objectMapper.readValue(variableStr, typeRef);
        }

        setupCORSHeaders(req, resp);
        executeGraphQLRequest(resp, query, operationName, variables);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() {
        };
        Map<String, Object> body = objectMapper.readValue(req.getInputStream(), typeRef);


        String query = (String) body.get("query");
        String operationName = (String) body.get("operationName");
        Map<String, Object> variables = (Map<String, Object>) body.get("variables");

        if (variables == null) {
            variables = new HashMap<>();
        }

        setupCORSHeaders(req, resp);
        executeGraphQLRequest(resp, query, operationName, variables);
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setupCORSHeaders(req, resp);
        resp.flushBuffer();
    }

    private void executeGraphQLRequest(
            HttpServletResponse resp, String query, String operationName, Map<String, Object> variables) throws IOException {
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

        objectMapper.writeValue(resp.getWriter(), specificationResult);
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
