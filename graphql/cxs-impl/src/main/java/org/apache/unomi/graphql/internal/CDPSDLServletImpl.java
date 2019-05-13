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
package org.apache.unomi.graphql.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Charsets;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.TypeResolutionEnvironment;
import graphql.introspection.IntrospectionQuery;
import graphql.scalars.ExtendedScalars;
import graphql.schema.*;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

@Component(
        service={javax.servlet.http.HttpServlet.class,javax.servlet.Servlet.class},
        property = {"alias=/sdlgraphql", "jmx.objectname=graphql.servlet:type=graphql"}
)
public class CDPSDLServletImpl extends HttpServlet {

    private BundleContext bundleContext;
    private ObjectMapper objectMapper;
    private GraphQL graphQL;


    @Activate
    void activate(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    RuntimeWiring buildRuntimeWiring() {

        GraphQLScalarType emptyTypeWorkAroundScalarType = GraphQLScalarType.newScalar()
                .name("EmptyTypeWorkAround")
                .description("A marker type to get around the limitation of GraphQL that doesn't allow empty types. It should be always ignored.")
                .coercing(new Coercing() {
                    @Override
                    public Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
                        return null;
                    }

                    @Override
                    public Object parseValue(Object input) throws CoercingParseValueException {
                        return input;
                    }

                    @Override
                    public Object parseLiteral(Object input) throws CoercingParseLiteralException {
                        return input;
                    }
                })
                .build();

        GraphQLScalarType geopointScalarType = GraphQLScalarType.newScalar()
                .name("GeoPoint")
                .description("A type that represents a geographical location")
                .coercing(new Coercing() {
                    @Override
                    public Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
                        return null;
                    }

                    @Override
                    public Object parseValue(Object input) throws CoercingParseValueException {
                        return input;
                    }

                    @Override
                    public Object parseLiteral(Object input) throws CoercingParseLiteralException {
                        return input;
                    }
                })
                .build();

        return RuntimeWiring.newRuntimeWiring()
                .scalar(ExtendedScalars.DateTime)
                .scalar(ExtendedScalars.Date)
                .scalar(ExtendedScalars.Json)
                .scalar(ExtendedScalars.Time)
                .scalar(emptyTypeWorkAroundScalarType)
                .scalar(geopointScalarType)
                .type("CDP_EventInterface", typeWiring -> typeWiring
                        .typeResolver(new TypeResolver() {
                            @Override
                            public GraphQLObjectType getType(TypeResolutionEnvironment env) {
                                return null;
                            }
                        }))
                .type("CDP_ProfileInterface", typeWiring -> typeWiring
                        .typeResolver(new TypeResolver() {
                            @Override
                            public GraphQLObjectType getType(TypeResolutionEnvironment env) {
                                return null;
                            }
                        }))
                .type("CDP_PropertyInterface", typeWiring -> typeWiring
                        .typeResolver(new TypeResolver() {
                            @Override
                            public GraphQLObjectType getType(TypeResolutionEnvironment env) {
                                return null;
                            }
                        }))
                // .scalar(CustomScalar)
                // this uses builder function lambda syntax
                /*
                .type("QueryType", typeWiring -> typeWiring
                        .dataFetcher("hero", new StaticDataFetcher(StarWarsData.getArtoo()))
                        .dataFetcher("human", StarWarsData.getHumanDataFetcher())
                        .dataFetcher("droid", StarWarsData.getDroidDataFetcher())
                )
                .type("Human", typeWiring -> typeWiring
                        .dataFetcher("friends", StarWarsData.getFriendsDataFetcher())
                )
                // you can use builder syntax if you don't like the lambda syntax
                .type("Droid", typeWiring -> typeWiring
                        .dataFetcher("friends", StarWarsData.getFriendsDataFetcher())
                )
                // or full builder syntax if that takes your fancy
                .type(
                        newTypeWiring("Character")
                                .typeResolver(StarWarsData.getCharacterTypeResolver())
                                .build()
                )
                */
                .build();
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        SchemaParser schemaParser = new SchemaParser();
        SchemaGenerator schemaGenerator = new SchemaGenerator();

        Reader schemaReader = getSchemaReader("cdp-schema.graphqls");
        //File schemaFile2 = loadSchema("cdp-schema.graphqls");
        //File schemaFile3 = loadSchema("cdp-schema.graphqls");

        TypeDefinitionRegistry typeRegistry = new TypeDefinitionRegistry();

        // each registry is merged into the main registry
        typeRegistry.merge(schemaParser.parse(schemaReader));
        //typeRegistry.merge(schemaParser.parse(schemaFile2));
        //typeRegistry.merge(schemaParser.parse(schemaFile3));

        RuntimeWiring wiring = buildRuntimeWiring();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, wiring);
        graphQL = GraphQL.newGraphQL(graphQLSchema)
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
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
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        InputStream bodyStream = req.getInputStream();
        TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() {
        };
        Map<String, Object> body = objectMapper.readValue(bodyStream, typeRef);
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
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        setupCORSHeaders(req, resp);
        resp.flushBuffer();
    }

    private void executeGraphQLRequest(HttpServletResponse resp, String query, String operationName, Map<String, Object> variables) throws IOException {
        if (query == null || query.trim().length() == 0) {
            throw new RuntimeException("Query cannot be empty or null");
        }
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .variables(variables)
                .operationName(operationName)
                .build();

        ExecutionResult executionResult = graphQL.execute(executionInput);

        Map<String, Object> toSpecificationResult = executionResult.toSpecification();
        PrintWriter out = resp.getWriter();
        objectMapper.writeValue(out, toSpecificationResult);
    }

    private Reader getSchemaReader(String resourceUrl) {
        try {
            return new InputStreamReader(bundleContext.getBundle().getResource(resourceUrl).openConnection().getInputStream(), Charsets.UTF_8.name());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Setup CORS headers as soon as possible so that errors are not misconstrued on the client for CORS errors
     *
     * @param httpServletRequest
     * @param response
     * @throws IOException
     */
    public void setupCORSHeaders(HttpServletRequest httpServletRequest, ServletResponse response) throws IOException {
        if (response instanceof HttpServletResponse) {
            HttpServletResponse httpServletResponse = (HttpServletResponse) response;
            if (httpServletRequest != null && httpServletRequest.getHeader("Origin") != null) {
                httpServletResponse.setHeader("Access-Control-Allow-Origin", httpServletRequest.getHeader("Origin"));
            } else {
                httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");
            }
            httpServletResponse.setHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, X-Apollo-Tracing, test");
            httpServletResponse.setHeader("Access-Control-Allow-Credentials", "true");
            httpServletResponse.setHeader("Access-Control-Allow-Methods", "OPTIONS, POST, GET");
            // httpServletResponse.setHeader("Access-Control-Max-Age", "600");
            // httpServletResponse.setHeader("Access-Control-Expose-Headers","Access-Control-Allow-Origin");
            // httpServletResponse.flushBuffer();
        }
    }

}
